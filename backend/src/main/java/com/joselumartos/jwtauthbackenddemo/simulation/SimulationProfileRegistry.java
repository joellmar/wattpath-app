package com.joselumartos.jwtauthbackenddemo.simulation;

import com.joselumartos.jwtauthbackenddemo.entities.Device;
import com.joselumartos.jwtauthbackenddemo.entities.SimulationProfile;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;

@Component
public class SimulationProfileRegistry {

    private final Map<SimulationProfile, PowerProfileCalculator> calculators;

    public SimulationProfileRegistry() {
        calculators = new EnumMap<>(SimulationProfile.class);
        calculators.put(SimulationProfile.SINE_WAVE, new SineWavePowerCalculator());
        calculators.put(SimulationProfile.OVEN, new OvenPowerCalculator());
        calculators.put(SimulationProfile.WASHING_MACHINE, new WashingMachinePowerCalculator());
        calculators.put(SimulationProfile.TELEVISION, new TelevisionPowerCalculator());
        calculators.put(SimulationProfile.FAN, new FanPowerCalculator());
        calculators.put(SimulationProfile.DESKTOP_PC, new DesktopPcPowerCalculator());
        calculators.put(SimulationProfile.FRIDGE, new FridgePowerCalculator());
        calculators.put(SimulationProfile.STANDBY, new StandbyPowerCalculator());
        calculators.put(SimulationProfile.CONSTANT_HIGH_LOAD, new ConstantHighLoadPowerCalculator());
    }

    public BigDecimal calculatePowerW(SimulationProfile profile, Instant now, Device device) {
        SimulationProfile resolvedProfile = profile != null ? profile : SimulationProfile.SINE_WAVE;
        PowerProfileCalculator calculator = calculators.get(resolvedProfile);
        if (calculator == null) {
            calculator = calculators.get(SimulationProfile.SINE_WAVE);
        }
        return calculator.calculatePowerW(now, device);
    }
}
