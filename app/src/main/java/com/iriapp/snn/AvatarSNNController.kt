package com.iriapp.snn

import android.util.Log

/**
 * AvatarSNNController — Maps raw SNN spike byte arrays into 3D Avatar / VRM
 * gesture and emotion state flags.
 *
 * The C SNN core (snn_engine) outputs one spike per neuron per time step
 * (a byte array of 0s and 1s).  Each output "channel" is mapped to a
 * semantic avatar state.  This controller collects those spikes across
 * successive time steps, maintains a short running history, and derives
 * stable states and gesture counts that are easier for the rendering
 * layer to consume.
 *
 * Channel layout (numNeurons = 256):
 *   0-15     : eyebrow / face expression (Happy, Angry, Surprised, ...)
 *   16-31    : eye blink (left, right, bilateral)
 *   32-47    : head pose (nod, shake, tilt)
 *   48-63    : hand gesture (wave, point, thumbs up, ...)
 *   64-79    : body pose / upper body
 *   80-95    : full body locomotion (walk, run, jump, ...)
 *   96-255   : spare / custom
 *
 * Usage:
 *   val controller = AvatarSNNController(256)
 *   controller.pushSpikes(input, spikes)   // call per simulation step
 *   val state = controller.currentState    // snapshot of derived avatar state
 */
class AvatarSNNController(private val numNeurons: Int) {

    companion object {
        private const val TAG = "AvatarSNNController"
        private const val DEFAULT_HISTORY_STEPS = 8
        private const val DEFAULT_NEURONS = 256

        // Channel index constants
        const val CHANNEL_EQ   = 0    // 0-15  : facial expression (EQ)
        const val CHANNEL_BLINK = 16   // 16-31 : eye blink
        const val CHANNEL_HEAD = 32   // 32-47 : head gesture
        const val CHANNEL_HAND = 48   // 48-63 : hand gesture
        const val CHANNEL_BODY = 64   // 64-79 : body pose
        const val CHANNEL_LOCOMOTION = 80  // 80-95: full body / locomotion

        // Convolutional kernel (simple peak detector across history)
        private val SPIKE_KERNEL = floatArrayOf(
            0.0f, 1.0f, 0.0f,   // central detection
            0.5f, 1.0f, 1.0f,   // rising edge
            1.0f, 1.0f, 0.5f    // falling edge preserved
        )
    }

    private val history = Array(DEFAULT_HISTORY_STEPS) { BooleanArray(numNeurons) }
    private var historyIndex = 0
    private var spikeCount = IntArray(numNeurons)   // per-neuron spike tallies
    private var totalSpikes = 0

    private val emotionFlags = BooleanArray(16)     // facial expression bits
    private val blinkFlags   = BooleanArray(16)     // eye blink flags
    private val headFlags    = BooleanArray(16)     // head gesture flags
    private val handFlags    = BooleanArray(16)     // hand gesture flags
    private val bodyFlags    = BooleanArray(16)     // body pose flags
    private val locomotionFlags = BooleanArray(16)  // locomotion flags

    private val expressionNames = listOf(
        "Neutral", "Happy", "Angry", "Surprised",
        "Fearful", "Disgusted", "Sad", "Contempt",
        "Confused", "Thinking", "Bored", "Excited",
        "Sleepy", "Dizzy", "Pensive", "Calm"
    )

    // Blink detection thresholds (per-eye channels)
    private val blinkThreshold = 0.3f

    init {
        require(numNeurons <= DEFAULT_NEURONS) {
            "numNeurons must not exceed $DEFAULT_NEURONS"
        }
    }

    /**
     * Push a spike output vector from the native SNN step into the controller.
     *
     * @param input   the input byte array (passed through for reference only)
     * @param spikes  the spike output byte array (0 or 1 per neuron)
     */
    fun pushSpikes(input: ByteArray, spikes: ByteArray) {
        require(input.size == spikes.size) {
            "input and spikes must have same length"
        }

        // Store current spike pattern in circular history
        history[historyIndex] = spikes.clone()
        historyIndex = (historyIndex + 1) % history.size

        // Increment spike counters for all neurons that spiked
        for (i in spikes.indices) {
            if (spikes[i] == 1.toByte()) {
                spikeCount[i]++
                totalSpikes++
            }
        }

        // Derive expression flags from the EQ channel (0-15)
        detectExpressionPattern()

        // Detect blinks from blink channel (16-31)
        detectBlinks()

        // Detect head gestures from head channel (32-47)
        detectHeadGestures()

        // Detect hand gestures (48-63)
        detectHandGestures()

        // Detect body poses (64-79)
        detectBodyPoses()

        // Detect locomotion (80-95)
        detectLocomotion()
    }

    /**
     * Get the current derived avatar state.
     */
    val currentState: AvatarState
        get() = AvatarState(
            expressionFlags = emotionFlags.clone(),
            expressionNames = expressionNames,
            blinkFlags = blinkFlags.clone(),
            headFlags = headFlags.clone(),
            headGestureNames = listOf("Neutral", "Nod", "Shake", "Tilt", "Lean", "LookUp", "LookDown", "LookLeft", "LookRight", "Blink", "WidenEyes", "Squint", "BlinkLeft", "BlinkRight", "BlinkBoth", "BlinkOne"),
            handFlags = handFlags.clone(),
            handGestureNames = listOf("Neutral", "Wave", "Point", "ThumbsUp", "ThumbsDown", "OK", "Peace", "OpenHand", "ClosedFist", "Grab", "Pinch", "Reach", "Block", "Stop", "Go", "Hover"),
            bodyFlags = bodyFlags.clone(),
            bodyPoseNames = listOf("Neutral", "Relaxed", "Lean", "ArmsUp", "ArmsOut", "KneesBent", "Hunched", "Stretch", "CrossedArms", "HandsOnHips", "Bend", "Turn", "Crouch", "ReachUp", "ReachOut", "Sit"),
            locomotionFlags = locomotionFlags.clone(),
            locomotionNames = listOf("Stationary", "Walk", "Run", "Jump", "Crouch", "Turn", "Sneak", "Roll", "JumpLeft", "JumpRight", "Punch", "Kick", "Dance", "Clap", "Spin", "Shuffle"),
            spikeCount = spikeCount.clone(),
            totalSpikes = totalSpikes,
            historySize = history.size
        )

    // ---- Detection helpers ----------

    private fun detectExpressionPattern() {
        // Use peak detection on EQ channel with the kernel
        val eqChannel = 0..15
        var bestChannel = -1
        var bestScore = 0f

        for (ch in eqChannel) {
            var score = 0f
            // Look at the last few history frames
            for (h in 0 until history.size) {
                val idx = (historyIndex - h + history.size) % history.size
                if (idx < spikeCount.size && ch < spikeCount.size) {
                    score += spikeCount[ch + h * 16] * SIDES[h]
                }
            }
            if (score > bestScore) {
                bestScore = score
                bestChannel = ch
            }
        }

        // Map best channel to expression
        if (bestChannel >= 0) {
            emotionFlags[bestChannel] = true
            // Clear stale flags
            emotionFlags.fill(false, bestChannel + 1, emotionFlags.size)
        }
    }

    private fun detectBlinks() {
        for (ch in 0..15) {
            val blinkIdx = CHANNEL_BLINK + ch
            if (blinkIdx < spikeCount.size) {
                // A blink is detected if the neuron fires significantly more than average
                val avg = if (history.size > 0) history.sumOf { frame -> frame[blinkIdx] } / history.size.toFloat() else 0f
                if (spikeCount[blinkIdx] > history.size * blinkThreshold) {
                    blinkFlags[ch] = true
                    // Reset after detecting for cooldown
                    if (blinkFlags[ch]) {
                        // Cooldown logic — keep it simple
                    }
                }
            }
        }
    }

    private fun detectHeadGestures() {
        for (ch in 0..15) {
            val headIdx = CHANNEL_HEAD + ch
            if (headIdx < spikeCount.size) {
                if (spikeCount[headIdx] > history.size * 0.2f) {
                    headFlags[ch] = true
                }
            }
        }
    }

    private fun detectHandGestures() {
        for (ch in 0..15) {
            val handIdx = CHANNEL_HAND + ch
            if (handIdx < spikeCount.size) {
                if (spikeCount[handIdx] > history.size * 0.2f) {
                    handFlags[ch] = true
                }
            }
        }
    }

    private fun detectBodyPoses() {
        for (ch in 0..15) {
            val bodyIdx = CHANNEL_BODY + ch
            if (bodyIdx < spikeCount.size) {
                if (spikeCount[bodyIdx] > history.size * 0.2f) {
                    bodyFlags[ch] = true
                }
            }
        }
    }

    private fun detectLocomotion() {
        for (ch in 0..15) {
            val locIdx = CHANNEL_LOCOMOTION + ch
            if (locIdx < spikeCount.size) {
                if (spikeCount[locIdx] > history.size * 0.2f) {
                    locomotionFlags[ch] = true
                }
            }
        }
    }

    // Simple moving average kernel
    private val SIDES = floatArrayOf(0.2f, 0.4f, 0.6f, 0.8f, 1.0f, 0.8f, 0.6f, 0.4f, 0.2f)

    /**
     * Reset all internal counters and flags (call before a new scene or sequence).
     */
    fun reset() {
        for (i in spikeCount.indices) {
            spikeCount[i] = 0
        }
        totalSpikes = 0
        for (frame in history) {
            frame.fill(false)
        }
        historyIndex = 0
        emotionFlags.fill(false)
        blinkFlags.fill(false)
        headFlags.fill(false)
        handFlags.fill(false)
        bodyFlags.fill(false)
        locomotionFlags.fill(false)
    }

    /**
     * Get the spike frequency (spikes per frame) for a specific neuron index.
     */
    fun spikeFrequency(neuronIdx: Int): Float {
        if (neuronIdx >= spikeCount.size) return 0f
        return (spikeCount[neuronIdx] / history.size).toFloat()
    }

    /**
     * Check if a given expression name is currently active.
     */
    fun isExpressionActive(expressionIndex: Int): Boolean {
        if (expressionIndex < 0 || expressionIndex >= emotionFlags.size) return false
        return emotionFlags[expressionIndex]
    }

    /**
     * Check if a given blink is currently active.
     */
    fun isBlinkActive(eyeIndex: Int): Boolean {
        if (eyeIndex < 0 || eyeIndex >= blinkFlags.size) return false
        return blinkFlags[eyeIndex]
    }

    /**
     * Check if a given head gesture is currently active.
     */
    fun isHeadGestureActive(gestureIndex: Int): Boolean {
        if (gestureIndex < 0 || gestureIndex >= headFlags.size) return false
        return headFlags[gestureIndex]
    }

    /**
     * Check if a given hand gesture is currently active.
     */
    fun isHandGestureActive(gestureIndex: Int): Boolean {
        if (gestureIndex < 0 || gestureIndex >= handFlags.size) return false
        return handFlags[gestureIndex]
    }

    /**
     * Check if full-body locomotion state is active.
     */
    fun isLocomotionActive(locIndex: Int): Boolean {
        if (locIndex < 0 || locIndex >= locomotionFlags.size) return false
        return locomotionFlags[locIndex]
    }
}

/**
 * Snapshot of the derived avatar state from SNN spikes.
 */
data class AvatarState(
    val expressionFlags: BooleanArray,
    val expressionNames: List<String>,
    val blinkFlags: BooleanArray,
    val headGestureNames: List<String>,
    val headFlags: BooleanArray,
    val handGestureNames: List<String>,
    val handFlags: BooleanArray,
    val bodyPoseNames: List<String>,
    val bodyFlags: BooleanArray,
    val locomotionNames: List<String>,
    val locomotionFlags: BooleanArray,
    val spikeCount: IntArray,
    val totalSpikes: Int,
    val historySize: Int
) {
    /**
     * Get the set of currently active expression names.
     */
    val activeExpressions: Set<String>
        get() = expressionNames.indices.filter { expressionFlags[it] }.map { expressionNames[it] }.toSet()

    /**
     * Get the set of currently detected blink names.
     */
    val activeBlinks: Set<String>
        get() = blinkFlags.indices.filter { blinkFlags[it] }.map { "Blink$it" }.toSet()

    /**
     * Get the set of currently detected head gesture names.
     */
    val activeHeadGestures: Set<String>
        get() = headGestureNames.indices.filter { headFlags[it] }.map { headGestureNames[it] }.toSet()

    /**
     * Get the set of currently detected hand gesture names.
     */
    val activeHandGestures: Set<String>
        get() = handGestureNames.indices.filter { handFlags[it] }.map { handGestureNames[it] }.toSet()

    /**
     * Get the set of currently detected body pose names.
     */
    val activeBodyPoses: Set<String>
        get() = bodyPoseNames.indices.filter { bodyFlags[it] }.map { bodyPoseNames[it] }.toSet()

    /**
     * Get the set of currently active locomotion names.
     */
    val activeLocomotions: Set<String>
        get() = locomotionNames.indices.filter { locomotionFlags[it] }.map { locomotionNames[it] }.toSet()

    /**
     * Get a human-readable summary of all active states.
     */
    override fun toString(): String {
        val sb = StringBuilder()
        sb.append("AvatarState{\n")
        sb.append("  expressions: ").append(activeExpressions).append("\n")
        sb.append("  blinks: ").append(activeBlinks).append("\n")
        sb.append("  head: ").append(activeHeadGestures).append("\n")
        sb.append("  hands: ").append(activeHandGestures).append("\n")
        sb.append("  body: ").append(activeBodyPoses).append("\n")
        sb.append("  locomotion: ").append(activeLocomotions).append("\n")
        sb.append("  totalSpikes: $totalSpikes\n")
        sb.append("}")
        return sb.toString()
    }
}
