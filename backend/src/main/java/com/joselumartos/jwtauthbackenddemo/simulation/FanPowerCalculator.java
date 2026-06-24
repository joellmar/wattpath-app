package com.joselumartos.jwtauthbackenddemo.simulation;

import com.joselumartos.jwtauthbackenddemo.entities.Device;

import java.math.BigDecimal;
import java.time.Instant;

import static com.joselumartos.jwtauthbackenddemo.simulation.SimulationMath.oscillate;
import static com.joselumartos.jwtauthbackenddemo.simulation.SimulationMath.toPowerW;

final class FanPowerCalculator implements PowerProfileCalculator {

    @Override
    public BigDecimal calculatePowerW(Instant now, Device device) {
        return toPowerW(oscillate(now, device.getId(), 90.0, 10.0, 35.0));
    }
}
