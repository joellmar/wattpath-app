package com.joselumartos.jwtauthbackenddemo.simulation;

import com.joselumartos.jwtauthbackenddemo.entities.Device;

import java.math.BigDecimal;
import java.time.Instant;

@FunctionalInterface
public interface PowerProfileCalculator {

    BigDecimal calculatePowerW(Instant now, Device device);
}
