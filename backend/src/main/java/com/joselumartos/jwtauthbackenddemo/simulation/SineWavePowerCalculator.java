package com.joselumartos.jwtauthbackenddemo.simulation;

import com.joselumartos.jwtauthbackenddemo.entities.Device;

import java.math.BigDecimal;
import java.time.Instant;

import static com.joselumartos.jwtauthbackenddemo.simulation.SimulationMath.devicePhaseOffset;
import static com.joselumartos.jwtauthbackenddemo.simulation.SimulationMath.toPowerW;

final class SineWavePowerCalculator implements PowerProfileCalculator {

    @Override
    public BigDecimal calculatePowerW(Instant now, Device device) {
        double seconds = now.getEpochSecond();
        double phase = devicePhaseOffset(device.getId());
        double sineValue = Math.sin(seconds * 2 * Math.PI / 60.0 + phase);
        return toPowerW(500.0 + 300.0 * sineValue);
    }
}
