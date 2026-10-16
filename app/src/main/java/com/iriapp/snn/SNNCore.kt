package com.iriapp.snn

/**
 * SNNCore — Kotlin binding for the native snn_engine C library (LIF Spiking Neural Network).
 *
 * Loads the native library via System.loadLibrary("snn_engine").
 * Exposes two native entry points to Kotlin:
 *   - nativeInit(numNeurons, vRest, vThreshold, vReset, tauM, r, dt)
 *   - nativeStep(input: ByteArray, output: ByteArray): ByteArray
 *
 * The C implementation (snn_jni.c) maps these to:
 *   Java_com_iriapp_snn_SnnCore_nativeInit
 *   Java_com_iriapp_snn_SnnCore_nativeStep
 *
 * Usage:
 *   val core = SNNCore()
 *   core.init(256, vRest = -65.0, vThreshold = -50.0, vReset = -65.0, tauM = 20.0, r = 10.0, dt = 1.0)
 *   val spikes = core.step(inputBytes)
 */
object SNNCore {
    /**
     * Static initializer: loads the native 'snn_engine' shared library.
     * The NDK build system places the library at libs/<abi>/libsnn_engine.so
     * and the Android runtime makes it available on the application's
     * native library search path.
     */
    init {
        System.loadLibrary("snn_engine")
    }

    /**
     * Initialize the SNN core with LIF neuron parameters.
     *
     * @param numNeurons   Number of neurons (max 256, defined by MAX_NEURONS in snn_core.c)
     * @param vRest        Resting membrane potential (mV)
     * @param vThreshold   Firing threshold (mV)
     * @param vReset       Reset potential after spike (mV)
     * @param tauM         Membrane time constant (ms)
     * @param r            Membrane resistance (MOhm)
     * @param dt           Simulation time step (ms)
     * @return             true on success, false on failure (e.g., invalid config or >256 neurons)
     */
    external fun nativeInit(
        numNeurons: Int,
        vRest: Double,
        vThreshold: Double,
        vReset: Double,
        tauM: Double,
        r: Double,
        dt: Double
    ): Boolean

    /**
     * Advance the SNN one simulation time step.
     *
     * The caller allocates the output buffer with exactly [numNeurons] bytes.
     * The C function snn_core_step() writes a single byte (0 or 1) per neuron
     * into the output buffer and returns the same array to Kotlin.
     *
     * @param input   Input current vector (1 byte per input neuron)
     * @param output  Output spike vector (1 byte per neuron, 0=quiet, 1=spiked)
     * @return        The populated spike output byte array, or null on error
     */
    external fun nativeStep(
        input: ByteArray,
        output: ByteArray
    ): ByteArray?

    /**
     * Convenience wrapper: allocates the output buffer and calls nativeStep.
     */
    fun step(inputs: ByteArray): ByteArray? {
        val output = ByteArray(inputs.size)
        return nativeStep(inputs, output)
    }

    /**
     * Convenience wrapper: resets membrane potentials to rest.
     */
    external fun reset(): Unit
}
