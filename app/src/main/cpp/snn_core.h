#ifndef SNN_CORE_H
#define SNN_CORE_H

#define MAX_NEURONS 256

#ifdef __cplusplus
extern "C" {
#endif

/**
 * @file snn_core.h
 * @brief Lightweight Leaky Integrate-and-Fire (LIF) Spiking Neural Network core
 *
 * Design constraints:
 *  - Minimal RAM footprint: static buffers, no heap after init
 *  - Max 256 neurons (configurable at init)
 *  - Single-precision accumulator for memory efficiency
 */

/* LIF neuron model parameters */
typedef struct {
    int      num_neurons;   /* number of neurons in the network      */
    double   v_rest;        /* resting membrane potential (mV)       */
    double   v_threshold;   /* firing threshold (mV)                 */
    double   v_reset;       /* reset potential after spike (mV)      */
    double   tau_m;         /* membrane time constant (ms)           */
    double   R;             /* membrane resistance (MOhm)            */
    double   dt;            /* simulation time step (ms)             */
    double   refractory;    /* refractory period (ms)                */
    double*  input_weights; /* input weight vector                   */
    double*  output_weights;/* output weight vector                  */
} snn_core_config_t;

/**
 * @brief Initialize the SNN core.
 *
 * Allocates the spike output buffer and initializes membrane potentials
 * to resting potential. No dynamic allocation occurs after this call.
 *
 * @param cfg  Pointer to configuration; must be non-NULL and valid for
 *             the lifetime of the SNN core.
 * @return 0 on success, -1 on invalid config or allocation failure.
 */
int snn_core_init(snn_core_config_t* cfg);

/**
 * @brief Advance the network by one time step.
 *
 * Applies input currents, integrates membrane potential with leak,
 * handles refractory period, and detects spikes. The output buffer
 * receives a byte (0 or 1) per neuron.
 *
 * @param cfg     Pointer to the SNN configuration (must be initialized).
 * @param input   Input current vector (one byte per input neuron).
 * @param output  Output spike vector (1 byte per neuron, 0=quiet, 1=spiked).
 * @return 0 on success, -1 on invalid config.
 */
int snn_core_step(snn_core_config_t* cfg, const unsigned char* input,
                  unsigned char* output);

/**
 * @brief Reset all membrane potentials to resting potential.
 *
 * Call this before processing a new sequence.
 */
void snn_core_reset(snn_core_config_t* cfg);

/**
 * @brief Cleanup: free any heap-allocated resources.
 *
 * @note After this call, the SNN core must be re-initialized before use.
 */
void snn_core_cleanup(snn_core_config_t* cfg);

#ifdef __cplusplus
}
#endif

#endif /* SNN_CORE_H */
