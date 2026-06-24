package com.joselumartos.jwtauthbackenddemo.simulation;

import com.joselumartos.jwtauthbackenddemo.entities.Device;

import java.math.BigDecimal;
import java.time.Instant;

import static com.joselumartos.jwtauthbackenddemo.simulation.SimulationMath.devicePhaseOffset;
import static com.joselumartos.jwtauthbackenddemo.simulation.SimulationMath.oscillate;
import static com.joselumartos.jwtauthbackenddemo.simulation.SimulationMath.toPowerW;

final class DesktopPcPowerCalculator implements PowerProfileCalculator {

    @Override
    public BigDecimal calculatePowerW(Instant now, Device device) {
        double base = oscillate(now, device.getId(), 45.0, 80.0, 120.0);
        double seconds = now.getEpochSecond() + devicePhaseOffset(device.getId());
        double loadSpike = 50.0 * Math.max(0.0, Math.sin(seconds * 2 * Math.PI / 300.0));
        return toPowerW(base + loadSpike);
    }
}
