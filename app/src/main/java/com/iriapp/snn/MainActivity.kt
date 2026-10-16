package com.iriapp.snn

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var statusTextView: TextView
    private lateinit var runButton: Button
    private lateinit var telemetryTextView: TextView
    private lateinit var telemetryScrollView: ScrollView

    private val mainHandler = Handler(Looper.getMainLooper())
    private val avatarController = AvatarSNNController(256)

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

        // Initialize SNN core on launch
        initializeSnnCore()

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
