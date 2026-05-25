package com.joselumartos.jwtauthbackenddemo.mappers;

import com.joselumartos.jwtauthbackenddemo.dtos.AlertDto;
import com.joselumartos.jwtauthbackenddemo.entities.Alert;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface AlertDtoMapper {

    @Mapping(source = "device.macAddress", target = "macAddress")
    @Mapping(source = "user.username", target = "username")
    AlertDto toDto(Alert alert);

    List<AlertDto> toDtoList(List<Alert> alerts);
}
