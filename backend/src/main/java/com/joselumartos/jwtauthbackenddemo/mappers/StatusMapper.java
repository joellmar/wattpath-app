package com.joselumartos.jwtauthbackenddemo.mappers;

import com.joselumartos.jwtauthbackenddemo.dtos.Status;
import com.joselumartos.jwtauthbackenddemo.entities.Reading;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

import java.math.BigDecimal;
import java.util.List;

@Mapper(componentModel = "spring")
public interface StatusMapper {

    @Mapping(source = "isOn", target = "output")
    @Mapping(source = "powerW", target = "activePower")
    @Mapping(source = "energyTotalKwh", target = "activeEnergy.total", qualifiedByName = "toWh")
    Status toDto(Reading reading);

    List<Status> toDtoList(List<Reading> readings);

    @Mapping(target = "time", ignore = true)
    @Mapping(target = "device", ignore = true)
    @Mapping(source = "activePower", target = "powerW")
    @Mapping(source = "activeEnergy.total", target = "energyTotalKwh", qualifiedByName = "toKwh")
    @Mapping(source = "output", target = "isOn")
    Reading toEntity(Status dto);

    List<Reading> toEntityList(List<Status> dtos);

    @Named("toWh")
    default Double toWh(BigDecimal energyKwh) {
        return (energyKwh != null)
                ? energyKwh.doubleValue() * 1000
                : null;
    }

    @Named("toKwh")
    default BigDecimal toKwh(Double energyWh) {
        return (energyWh != null)
                ? BigDecimal.valueOf(energyWh).divide(BigDecimal.valueOf(1000))
                : null;
    }
}
