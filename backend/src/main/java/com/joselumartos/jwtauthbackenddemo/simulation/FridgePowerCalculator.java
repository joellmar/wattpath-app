package com.joselumartos.jwtauthbackenddemo.simulation;

import com.joselumartos.jwtauthbackenddemo.entities.Device;

import java.math.BigDecimal;
import java.time.Instant;

import static com.joselumartos.jwtauthbackenddemo.simulation.SimulationMath.cyclePosition;
import static com.joselumartos.jwtauthbackenddemo.simulation.SimulationMath.toPowerW;

final class FridgePowerCalculator implements PowerProfileCalculator {

    private static final long CYCLE_SECONDS = 3600;

    @Override
    public BigDecimal calculatePowerW(Instant now, Device device) {
        double position = cyclePosition(now, device.getId(), CYCLE_SECONDS);
        if (position < 0.15) {
            return toPowerW(180.0);
        }
        return toPowerW(25.0);
    }
}
