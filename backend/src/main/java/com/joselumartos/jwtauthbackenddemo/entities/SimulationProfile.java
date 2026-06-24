package com.joselumartos.jwtauthbackenddemo.entities;

/**
 * Perfiles sintéticos de consumo eléctrico para dispositivos simulados.
 * Los nombres se persisten como texto en BD y deben mantenerse estables.
 */
public enum SimulationProfile {
    SINE_WAVE,
    OVEN,
    WASHING_MACHINE,
    TELEVISION,
    FAN,
    DESKTOP_PC,
    FRIDGE,
    STANDBY,
    CONSTANT_HIGH_LOAD
}
