#include "snn_core.h"
#include <jni.h>

/**
 * @file snn_jni.c
 * @brief JNI wrapper exposing nativeInit and nativeStep.
 *
 * Method signatures (as seen by JNI):
 *   jboolean nativeInit(JNIEnv* env, jobject obj,
 *                       jint numNeurons, jdouble vRest,
 *                       jdouble vThreshold, jdouble vReset,
 *                       jdouble tauM, jdouble r, jdouble dt);
 *
 *   jbyteArray nativeStep(JNIEnv* env, jobject obj,
 *                         jbyteArray input, jbyteArray output);
 */

/* Native SNN core instance (globally accessible) */
static snn_core_config_t g_snn_cfg;
static snn_core_config_t* g_cfg_ptr = &g_snn_cfg;

/*
 * ---------------------------------------------------------------------------
 * nativeInit
 * ---------------------------------------------------------------------------
 * Initializes the LIF SNN with the supplied parameters.
 * Returns JNI_TRUE (1) on success, JNI_FALSE (0) on failure.
 */
JNIEXPORT jboolean JNICALL
Java_com_iriapp_snn_SnnCore_nativeInit(JNIEnv* env, jobject obj,
                                       jint numNeurons, jdouble vRest,
                                       jdouble vThreshold, jdouble vReset,
                                       jdouble tauM, jdouble r, jdouble dt)
{
    if (g_cfg_ptr == NULL) {
        return JNI_FALSE;
    }

    g_cfg_ptr->num_neurons   = (int)numNeurons;
    g_cfg_ptr->v_rest        = (float)vRest;
    g_cfg_ptr->v_threshold   = (float)vThreshold;
    g_cfg_ptr->v_reset       = (float)vReset;
    g_cfg_ptr->tau_m         = (float)tauM;
    g_cfg_ptr->R             = (float)r;
    g_cfg_ptr->dt            = (float)dt;
    g_cfg_ptr->refractory    = 0.0;  /* not used in this minimal core */

    /* Allocate weight buffers (static, no heap after init) */
    static float in_w[MAX_NEURONS];
    static float out_w[MAX_NEURONS];

    if (numNeurons > MAX_NEURONS) {
        return JNI_FALSE;  /* cannot support >MAX_NEURONS */
    }

    g_cfg_ptr->input_weights  = in_w;
    g_cfg_ptr->output_weights = out_w;

    /* Initialize weights to a small positive value (default) */
    for (int i = 0; i < numNeurons; i++) {
        g_cfg_ptr->input_weights[i]  = 0.1f;
        g_cfg_ptr->output_weights[i] = 0.1f;
    }

    return (jboolean)snn_core_init(g_cfg_ptr);
}

/*
 * ---------------------------------------------------------------------------
 * nativeStep
 * ---------------------------------------------------------------------------
 * Advances the SNN one time step and returns the spike output byte array.
 *
 * The caller must allocate output with:
 *   env->AllocateByteArray(env, numNeurons)
 * and pass it as the output parameter (in/out).
 */
JNIEXPORT jbyteArray JNICALL
Java_com_iriapp_snn_SnnCore_nativeStep(JNIEnv* env, jobject obj,
                                       jbyteArray input, jbyteArray output)
{
    jbyteArray result = NULL;

    if (g_cfg_ptr == NULL || input == NULL || output == NULL) {
        return NULL;
    }

    jsize n = g_cfg_ptr->num_neurons;
    if (n <= 0) {
        return NULL;
    }

    /* Copy input byte array to native buffer */
    jbyte* input_buf = (jbyte*)env->GetByteArrayElements(input, NULL);
    if (input_buf == NULL) {
        return NULL;
    }

    /* Copy output byte array (or allocate if null) */
    jbyte* output_buf = (jbyte*)env->GetByteArrayElements(output, NULL);
    if (output_buf == NULL) {
        env->ReleaseByteArrayElements(input, input_buf, JNI_ABORT);
        return NULL;
    }

    /* Step the SNN */
    int ret = snn_core_step(g_cfg_ptr, (const unsigned char*)input_buf,
                            (unsigned char*)output_buf);
    env->ReleaseByteArrayElements(input, input_buf, JNI_ABORT);

    if (ret != 0) {
        env->ReleaseByteArrayElements(output, output_buf, JNI_ABORT);
        return NULL;
    }

    /* Return a new byte array copy to the caller (or return the buffer) */
    result = env->NewByteArray(env, n);
    if (result != NULL) {
        env->SetByteArrayRegion(result, 0, n, (const jbyte*)output_buf);
    }

    env->ReleaseByteArrayElements(output, output_buf, JNI_ABORT);
    return result;
}
