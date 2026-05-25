package com.joselumartos.jwtauthbackenddemo.services;

import com.joselumartos.jwtauthbackenddemo.entities.Device;
import com.joselumartos.jwtauthbackenddemo.entities.Period;
import com.joselumartos.jwtauthbackenddemo.entities.Reading;
import com.joselumartos.jwtauthbackenddemo.entities.Tariff;
import com.joselumartos.jwtauthbackenddemo.repositories.DeviceRepository;
import com.joselumartos.jwtauthbackenddemo.repositories.PeriodRepository;
import com.joselumartos.jwtauthbackenddemo.repositories.ReadingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class ConsumptionService {

    private final ReadingRepository readingRepository;
    private final DeviceRepository deviceRepository;
    private final PeriodRepository periodRepository;

    @Transactional(readOnly = true)
    public BigDecimal calculateCostInPeriod(String macAddress, Instant start, Instant end) {
        List<Reading> readings = readingRepository.findReadingsInInterval(macAddress, start, end);
        if(readings.size() < 2) return BigDecimal.ZERO;

        Optional<Device> deviceOpt = deviceRepository.findByMacAddress(macAddress);
        if (deviceOpt.isEmpty() || deviceOpt.get().getUser() == null || deviceOpt.get().getUser().getTariff() == null) {
            log.warn("No se puede calcular coste: Dispositivo {} sin configuración tarifaria completa.", macAddress);
            return BigDecimal.ZERO;
        }

        Tariff tariff = deviceOpt.get().getUser().getTariff();

        BigDecimal totalCost = BigDecimal.ZERO;

        for (int i = 1; i < readings.size(); i++) {
            Reading current = readings.get(i);
            Reading previous = readings.get(i - 1);

            if (current.getEnergyTotalKwh() == null || previous.getEnergyTotalKwh() == null) continue;

            BigDecimal deltaKwh = current.getEnergyTotalKwh().subtract(previous.getEnergyTotalKwh());

            if (deltaKwh.compareTo(BigDecimal.ZERO) <= 0) continue;

            ZonedDateTime zonedDateTime = current.getTime().atZone(ZoneId.of("Europe/Madrid"));
            LocalTime localTime = zonedDateTime.toLocalTime();

            Optional<Period> periodOpt = periodRepository.findApplicablePeriod(tariff.getId(), localTime);
            if (periodOpt.isEmpty()) continue;

            BigDecimal priceKwh = periodOpt.get().getPriceKwh();

            BigDecimal stepCost = deltaKwh.multiply(priceKwh);
            totalCost = totalCost.add(stepCost);
        }

        return totalCost.setScale(2, RoundingMode.HALF_UP);
    }

    @Transactional(readOnly = true)
    public BigDecimal calculateGhostCost(String macAddress, Instant start, Instant end) {
        List<Reading> readings = readingRepository.findReadingsInInterval(macAddress, start, end);
        if(readings.size() < 2) return BigDecimal.ZERO;

        Optional<Device> deviceOpt = deviceRepository.findByMacAddress(macAddress);
        if (deviceOpt.isEmpty() || deviceOpt.get().getUser() == null || deviceOpt.get().getUser().getTariff() == null) {
            return BigDecimal.ZERO;
        }

        Tariff tariff = deviceOpt.get().getUser().getTariff();

        BigDecimal ghostCost = BigDecimal.ZERO;

        for (int i = 1; i < readings.size(); i++) {
            Reading current = readings.get(i);
            Reading previous = readings.get(i - 1);

            if (current.getEnergyTotalKwh() == null || previous.getEnergyTotalKwh() == null) continue;

            ZonedDateTime zonedDateTime = current.getTime().atZone(ZoneId.of("Europe/Madrid"));
            int hour = zonedDateTime.getHour();

            if (hour >= 0 && hour < 6) {
                BigDecimal deltaKwh = current.getEnergyTotalKwh().subtract(previous.getEnergyTotalKwh());
                if (deltaKwh.compareTo(BigDecimal.ZERO) <= 0) continue;

                Optional<Period> periodOpt = periodRepository.findApplicablePeriod(tariff.getId(), zonedDateTime.toLocalTime());
                if (periodOpt.isEmpty()) continue;

                BigDecimal stepCost = deltaKwh.multiply(periodOpt.get().getPriceKwh());
                ghostCost = ghostCost.add(stepCost);

            }
        }

        return ghostCost.setScale(2, RoundingMode.HALF_UP);
    }
}
