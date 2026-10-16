package com.iriapp.snn

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import io.github.sceneview.SceneView

class MainActivity : AppCompatActivity() {

    private lateinit var statusTextView: TextView
    private lateinit var runButton: Button
    private lateinit var telemetryTextView: TextView
    private lateinit var telemetryScrollView: ScrollView
    private lateinit var sceneViewContainer: LinearLayout

    private val mainHandler = Handler(Looper.getMainLooper())
    private val avatarController = AvatarSNNController(256)
    private val avatarRenderer = AvatarRenderer(this)

    // Pre-allocated buffers (numNeurons = 256)
    private val inputBytes = ByteArray(256) { 0 }
    private val outputBytes = ByteArray(256)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusTextView = findViewById(R.id.status_text_view)
        runButton = findViewById(R.id.run_button)
        telemetryTextView = findViewById(R.id.telemetry_text_view)
        telemetryScrollView = findViewById(R.id.telemetry_scroll_view)
        sceneViewContainer = findViewById(R.id.scene_view_container)

        // Initialize SNN core on launch
        initializeSnnCore()

        // Attach 3D avatar renderer
        attachAvatarRenderer()

        // Connect button click listener
        runButton.setOnClickListener {
            // Run native step on a background thread to avoid blocking UI
            Thread {
                val spikes = SNNCore.nativeStep(inputBytes, outputBytes)
                // Update UI on main thread
                mainHandler.post {
                    if (spikes != null) {
                        avatarController.pushSpikes(inputBytes, spikes)
                        updateTelemetry()
                        updateAvatar()
                    } else {
                        statusTextView.text = "SNN step failed"
                    }
                }
            }.start()
        }
    }

    /** Initialize SNN core with default LIF parameters on launch. */
    private fun initializeSnnCore() {
        val success = SNNCore.nativeInit(
            256,
            vRest = -65.0,
            vThreshold = -50.0,
            vReset = -65.0,
            tauM = 20.0,
            r = 10.0,
            dt = 1.0
        )
        statusTextView.text = if (success) {
            "Status: SNN ready (256 neurons)"
        } else {
            "Status: SNN init failed"
        }
    }

    /** Attach the 3D avatar renderer to the scene view container. */
    private fun attachAvatarRenderer() {
        try {
            // Create a SceneView dynamically
            val sceneView = SceneView(this)
            sceneView.layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            sceneViewContainer.addView(sceneView)

            // Load the 3D avatar model
            avatarRenderer.attachToSceneView(sceneView)
            val loaded = avatarRenderer.loadAvatar("models/avatar.glb")
            statusTextView.text = if (loaded) {
                "Status: SNN ready (256 neurons) + 3D Avatar loaded"
            } else {
                "Status: SNN ready (256 neurons) + 3D Avatar load failed"
            }
        } catch (e: Exception) {
            statusTextView.text = "Status: SceneView init failed: ${e.message}"
        }
    }

    /** Update the 3D avatar based on current SNN state. */
    private fun updateAvatar() {
        val state = avatarController.currentState
        avatarRenderer.update(state)
        avatarRenderer.updateTransform(state)
    }

    /** Update telemetry display with current SNN state. */
    private fun updateTelemetry() {
        val state = avatarController.currentState
        telemetryTextView.text = StringBuilder()
            .append("totalSpikes: ").append(state.totalSpikes).append("\n")
            .append("spikeCount: ").append(state.spikeCount.contentToString()).append("\n")
            .append("activeExpressions: ").append(state.activeExpressions.joinToString(", ")).append("\n")
            .append("activeBlinks: ").append(state.activeBlinks.joinToString(", ")).append("\n")
            .append("activeHeadGestures: ").append(state.activeHeadGestures.joinToString(", ")).append("\n")
            .append("activeHandGestures: ").append(state.activeHandGestures.joinToString(", ")).append("\n")
            .append("activeBodyPoses: ").append(state.activeBodyPoses.joinToString(", ")).append("\n")
            .append("activeLocomotions: ").append(state.activeLocomotions.joinToString(", ")).append("\n")
            .toString()
    }
}
