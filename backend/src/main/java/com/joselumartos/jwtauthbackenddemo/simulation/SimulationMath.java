package com.joselumartos.jwtauthbackenddemo.simulation;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;

final class SimulationMath {

    private SimulationMath() {
    }

    static double devicePhaseOffset(long deviceId) {
        return (deviceId % 10) * Math.PI / 5.0;
    }

    static double cyclePosition(Instant now, long deviceId, long cycleSeconds) {
        double seconds = now.getEpochSecond() + devicePhaseOffset(deviceId);
        return (seconds % cycleSeconds) / cycleSeconds;
    }

    static BigDecimal toPowerW(double watts) {
        return BigDecimal.valueOf(Math.max(0.0, watts)).setScale(2, RoundingMode.HALF_UP);
    }

    static double oscillate(Instant now, long deviceId, double periodSeconds, double amplitude, double center) {
        double seconds = now.getEpochSecond() + devicePhaseOffset(deviceId);
        return center + amplitude * Math.sin(seconds * 2 * Math.PI / periodSeconds);
    }
}
