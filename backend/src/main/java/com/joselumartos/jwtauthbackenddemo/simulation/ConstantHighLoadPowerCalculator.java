package com.joselumartos.jwtauthbackenddemo.simulation;

import com.joselumartos.jwtauthbackenddemo.entities.Device;

import java.math.BigDecimal;
import java.time.Instant;

import static com.joselumartos.jwtauthbackenddemo.simulation.SimulationMath.toPowerW;

final class ConstantHighLoadPowerCalculator implements PowerProfileCalculator {

    @Override
    public BigDecimal calculatePowerW(Instant now, Device device) {
        return toPowerW(3500.0);
    }
}
