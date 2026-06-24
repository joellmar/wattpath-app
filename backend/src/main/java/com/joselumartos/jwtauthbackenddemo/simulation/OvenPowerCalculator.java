package com.joselumartos.jwtauthbackenddemo.simulation;

import com.joselumartos.jwtauthbackenddemo.entities.Device;

import java.math.BigDecimal;
import java.time.Instant;

import static com.joselumartos.jwtauthbackenddemo.simulation.SimulationMath.cyclePosition;
import static com.joselumartos.jwtauthbackenddemo.simulation.SimulationMath.oscillate;
import static com.joselumartos.jwtauthbackenddemo.simulation.SimulationMath.toPowerW;

final class OvenPowerCalculator implements PowerProfileCalculator {

    private static final long CYCLE_SECONDS = 3600;

    @Override
    public BigDecimal calculatePowerW(Instant now, Device device) {
        double position = cyclePosition(now, device.getId(), CYCLE_SECONDS);
        if (position < 0.20) {
            return toPowerW(1800.0);
        }
        if (position < 0.60) {
            return toPowerW(oscillate(now, device.getId(), 45.0, 200.0, 1000.0));
        }
        return toPowerW(5.0);
    }
}
