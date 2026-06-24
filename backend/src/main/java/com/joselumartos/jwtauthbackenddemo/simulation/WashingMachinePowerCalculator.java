package com.joselumartos.jwtauthbackenddemo.simulation;

import com.joselumartos.jwtauthbackenddemo.entities.Device;

import java.math.BigDecimal;
import java.time.Instant;

import static com.joselumartos.jwtauthbackenddemo.simulation.SimulationMath.cyclePosition;
import static com.joselumartos.jwtauthbackenddemo.simulation.SimulationMath.toPowerW;

final class WashingMachinePowerCalculator implements PowerProfileCalculator {

    private static final long CYCLE_SECONDS = 3600;

    @Override
    public BigDecimal calculatePowerW(Instant now, Device device) {
        double position = cyclePosition(now, device.getId(), CYCLE_SECONDS);
        if (position < 0.10) {
            return toPowerW(100.0);
        }
        if (position < 0.30) {
            return toPowerW(2000.0);
        }
        if (position < 0.60) {
            return toPowerW(400.0);
        }
        if (position < 0.80) {
            double spinPhase = (position - 0.60) / 0.20;
            double spinWave = Math.sin(spinPhase * Math.PI * 6);
            return toPowerW(400.0 + 1400.0 * Math.abs(spinWave));
        }
        return toPowerW(10.0);
    }
}
