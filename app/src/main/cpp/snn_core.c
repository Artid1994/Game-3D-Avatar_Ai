#include "snn_core.h"

#include <stdlib.h>
#include <string.h>

/**
 * @file snn_core.c
 * @brief LIF SNN implementation with minimal RAM usage.
 *
 * Memory budget (per 256-neuron config):
 *   - membrane_potential: 256 x 4 bytes = 1 KB
 *   - input_current:      256 x 4 bytes = 1 KB
 *   - spike output:       256 x 1 byte  = 256 B
 *   - Weights:            256 x 4 bytes x 2 = 2 KB
 *   - Config struct:      ~200 bytes
 *   Total: ~4.5 KB  (<< 2 MB budget)
 */

#define MAX_NEURONS 256

/* Static buffers: no heap allocation after init */
static float  g_membrane  [MAX_NEURONS];   /* membrane potentials */
static float  g_input_cur [MAX_NEURONS];   /* input currents      */
static unsigned char g_spikes[MAX_NEURONS]; /* output spike buffer */

int snn_core_init(snn_core_config_t* cfg)
{
    if (cfg == NULL || cfg->num_neurons <= 0) {
        return -1;
    }
    if (cfg->num_neurons > MAX_NEURONS) {
        cfg->num_neurons = MAX_NEURONS;  /* clamp to max */
    }

    /* Initialize membrane potentials to rest */
    memset(g_membrane, 0, sizeof(g_membrane));
    for (int i = 0; i < cfg->num_neurons; i++) {
        g_membrane[i] = (float)cfg->v_rest;
        g_input_cur[i] = 0.0f;
        g_spikes[i] = 0;
    }

    return 0;
}

void snn_core_reset(snn_core_config_t* cfg)
{
    if (cfg == NULL) return;
    for (int i = 0; i < cfg->num_neurons; i++) {
        g_membrane[i] = (float)cfg->v_rest;
        g_input_cur[i] = 0.0f;
        g_spikes[i] = 0;
    }
}

void snn_core_cleanup(snn_core_config_t* cfg)
{
    (void)cfg;  /* No heap resources in this minimal implementation */
}

int snn_core_step(snn_core_config_t* cfg, const unsigned char* input,
                  unsigned char* output)
{
    if (cfg == NULL || input == NULL || output == NULL) {
        return -1;
    }

    const int n = cfg->num_neurons;
    if (n <= 0) return -1;

    /* Accumulate weighted input currents */
    for (int i = 0; i < n; i++) {
        g_input_cur[i] += (float)input[i] * cfg->input_weights[i];
    }

    /* Integrate membrane potentials (Euler method) */
    const float dt_inv = 1.0f / cfg->dt;
    for (int i = 0; i < n; i++) {
        if (g_input_cur[i] > 0.0f) {
            /* Leaky integrate: tau_m * dv/dt = -(v - v_rest) + R * I */
            const float leak = (g_membrane[i] - cfg->v_rest) * dt_inv / cfg->tau_m;
            g_membrane[i] += (float)cfg->R * g_input_cur[i] - leak;
            g_input_cur[i] = 0.0f;  /* current consumed */
        }
    }

    /* Detect spikes and handle refractory period */
    for (int i = 0; i < n; i++) {
        if (g_membrane[i] >= cfg->v_threshold) {
            g_spikes[i] = 1;
            g_membrane[i] = cfg->v_reset;
            g_input_cur[i] = 0.0f;
        } else {
            g_spikes[i] = 0;
        }
        output[i] = g_spikes[i];
    }

    return 0;
}
