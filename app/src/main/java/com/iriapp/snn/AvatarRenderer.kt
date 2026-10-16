package com.iriapp.snn

import android.content.Context
import android.util.Log
import androidx.annotation.WorkerThread
import io.github.sceneview.SceneView
import io.github.sceneview.model.ModelInstance
import io.github.sceneview.model.ModelLoader
import io.github.sceneview.node.ModelNode

/**
 * AvatarRenderer — 3D Avatar rendering manager for the Game 3D Avatar AI app.
 *
 * Uses the SceneView SDK (Filament + glTF/VRM loader) to render an animated
 * 3D avatar (GLB/VRM).  The renderer is driven by [AvatarSNNController.AvatarState]
 * which is derived from SNN spike patterns in real time.
 *
 * Blendshape / morph target mapping:
 *   - happy        ← emotion channel (EQ 0-15) → ModelNode blendshape
 *   - blink        ← blink channel (16-31)   → eye blendshape
 *   - nod          ← head channel (32-47)    → head pose blendshape
 *
 * @param context Application context (required for SceneView)
 */
class AvatarRenderer(private val context: Context) {

    private val tag = "AvatarRenderer"

    // View reference
    var sceneView: SceneView? = null

    // Model instance
    @Volatile
    private var modelInstance: ModelInstance? = null

    // Model nodes for blendshape animation
    private var modelNode: ModelNode? = null

    // SNN state sink
    private var avatarState: AvatarState? = null

    // Load placeholder placeholder logic
    fun loadAvatar(glTFPath: String = "models/avatar.glb"): Boolean {
        try {
            val loader = ModelLoader(context)
            val instance = loader.createModelFromAsset(glTFPath)
            modelInstance = instance
            modelNode = ModelNode(modelInstance = instance, scaleToUnits = 1.0f, autoAnimate = true)
            Log.d(tag, "Model loaded: $glTFPath")
            return true
        } catch (e: Exception) {
            Log.e(tag, "Failed to load model: ${e.message}", e)
            return false
        }
    }

    /**
     * Initialize the renderer with a SceneView and load the 3D model.
     * Call this from the Activity after setContentView.
     */
    fun attachToSceneView(sceneView: SceneView) {
        this.sceneView = sceneView
        // Add the model node to the scene
        modelNode?.let {
            sceneView.scene?.addChild(it)
        }
    }

    /**
     * Update the 3D avatar based on the current SNN state.
     * This is called on every SNN step to drive blendshapes and morph targets.
     */
    fun update(avatarState: AvatarState) {
        this.avatarState = avatarState

        // Drive expression blendshapes from emotion flags
        driveExpressionBlendshapes(avatarState)

        // Drive blink blendshapes from blink flags
        driveBlinkBlendshapes(avatarState)

        // Drive head pose blendshapes from head gesture flags
        driveHeadBlendshapes(avatarState)
    }

    // ---- Blendshape drivers ----

    private fun driveExpressionBlendshapes(state: AvatarState) {
        val model = modelNode ?: return
        val names = state.expressionNames

        for (i in names.indices) {
            if (state.expressionFlags.isNotEmpty() && i < state.expressionFlags.size) {
                val active = state.expressionFlags[i]
                // Blendshape index = expression name index (approximate mapping)
                if (active) {
                    model.blendshapeSetBlendShapeIndex(i, 1.0f)
                } else {
                    model.blendshapeSetBlendShapeIndex(i, 0.0f)
                }
            }
        }
    }

    private fun driveBlinkBlendshapes(state: AvatarState) {
        val model = modelNode ?: return
        val names = state.blinkFlags

        for (i in names.indices) {
            if (i < state.blinkFlags.size) {
                val active = state.blinkFlags[i]
                // Map blinks to eye blendshape indices
                when (i) {
                    0 -> model.blendshapeSetBlendShapeIndex(17, if (active) 1.0f else 0.0f) // left eye
                    1 -> model.blendshapeSetBlendShapeIndex(18, if (active) 1.0f else 0.0f) // right eye
                    2 -> model.blendshapeSetBlendShapeIndex(19, if (active) 1.0f else 0.0f) // bilateral
                }
            }
        }
    }

    private fun driveHeadBlendshapes(state: AvatarState) {
        val model = modelNode ?: return
        val names = state.headGestureNames

        for (i in names.indices) {
            if (i < state.headFlags.size) {
                val active = state.headFlags[i]
                // Map head gestures to head pose blendshape indices
                when (i) {
                    1 -> model.blendshapeSetBlendShapeIndex(20, if (active) 1.0f else 0.0f) // nod
                    2 -> model.blendshapeSetBlendShapeIndex(21, if (active) 1.0f else 0.0f) // shake
                    3 -> model.blendshapeSetBlendShapeIndex(22, if (active) 1.0f else 0.0f) // tilt
                }
            }
        }
    }

    /**
     * Update the transform (position/rotation/scale) of the 3D model
     * based on SNN locomotion and body pose state.
     */
    fun updateTransform(avatarState: AvatarState) {
        val model = modelNode ?: return
        val loc = avatarState.activeLocomotions
        val body = avatarState.activeBodyPoses

        // Simple example: scale based on locomotion
        var scaleFactor = 1.0f
        if (loc.contains("Walk")) scaleFactor = 1.05f
        if (loc.contains("Run")) scaleFactor = 1.10f
        if (loc.contains("Jump")) scaleFactor = 1.08f

        model.setScale(scaleFactor)
    }

    /**
     * Reset the renderer (call before a new scene or sequence).
     */
    fun reset() {
        modelInstance = null
        modelNode = null
        avatarState = null
        sceneView = null
    }

    /** Get the current 3D model instance (for advanced rendering control). */
    fun getModelInstance(): ModelInstance? = modelInstance
}
