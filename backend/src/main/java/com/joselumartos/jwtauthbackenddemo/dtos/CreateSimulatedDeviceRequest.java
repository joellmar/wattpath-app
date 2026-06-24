package com.joselumartos.jwtauthbackenddemo.dtos;

import com.joselumartos.jwtauthbackenddemo.entities.SimulationProfile;

public record CreateSimulatedDeviceRequest(
        String name,
        SimulationProfile simulationProfile
) {}
