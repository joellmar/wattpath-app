package com.joselumartos.jwtauthbackenddemo.mappers;

import com.joselumartos.jwtauthbackenddemo.dtos.EventsRpc;
import com.joselumartos.jwtauthbackenddemo.entities.Device;
import com.joselumartos.jwtauthbackenddemo.entities.Reading;
import com.joselumartos.jwtauthbackenddemo.repositories.DeviceRepository;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Mapper(componentModel = "spring")
public abstract class EventsRpcMapper {

    @Autowired
    protected DeviceRepository deviceRepository;

    @Mapping(expression = "java(\"shellyplugsg3-\" + reading.getDevice().getMacAddress())", target = "source")
    @Mapping(source = "time", target = "params.timestamp", qualifiedByName = "instantToDouble")
    @Mapping(source = "energyTotalKwh", target = "params.switchData.activeEnergy.total", qualifiedByName = "toWh")
    @Mapping(source = "powerW", target = "params.switchData.activePower")
    public abstract EventsRpc toDto(Reading reading);

    public abstract List<EventsRpc> toDtoList(List<Reading> readings);

    @Mapping(source = "params.timestamp", target = "time", qualifiedByName = "doubleToInstant")
    @Mapping(source = "source", target = "device", qualifiedByName = "mapSourceToDevice")
    @Mapping(source = "params.switchData.activePower", target = "powerW")
    @Mapping(source = "params.switchData.activeEnergy.total", target = "energyTotalKwh", qualifiedByName = "toKwh")
    @Mapping(target = "isOn", ignore = true)
    public abstract Reading toEntity(EventsRpc dto);

    public abstract List<Reading> toEntityList(List<EventsRpc> dto);

    @Named("toWh")
    protected Double toWh(BigDecimal energyKwh) {
        return (energyKwh != null)
                ? energyKwh.doubleValue() * 1000
                : null;
    }

    @Named("toKwh")
    protected BigDecimal toKwh(Double energyWh) {
        return (energyWh != null)
                ? BigDecimal.valueOf(energyWh).divide(BigDecimal.valueOf(1000))
                : null;
    }

    @Named("mapSourceToDevice")
    protected Device mapSourceToDevice(String source) {
        if (source == null || !source.contains("-")) return null;

        String mac = source.substring(source.lastIndexOf("-") + 1);
        return deviceRepository.findByMacAddress(mac).orElse(null);
    }

    @Named("instantToDouble")
    protected Double instantToDouble(Instant instant) {
        return (instant != null)
                ? (double) instant.getEpochSecond()
                : null;
    }

    @Named("doubleToInstant")
    protected Instant doubleToInstant(Double timestamp) {
        return (timestamp != null)
                ? Instant.ofEpochSecond(timestamp.longValue())
                : null;
    }
}
