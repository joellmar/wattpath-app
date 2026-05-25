package com.joselumartos.jwtauthbackenddemo.mappers;

import com.joselumartos.jwtauthbackenddemo.dtos.DeviceDto;
import com.joselumartos.jwtauthbackenddemo.entities.Device;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface DeviceDtoMapper {

    @Mapping(source = "user.username", target = "username", defaultExpression = "java(\"\")")
    DeviceDto toDto(Device device);

    List<DeviceDto> toDtoList(List<Device> devices);

    @Mapping(source = "username", target = "user.username")
    Device toEntity(DeviceDto dto);

    List<Device> toEntityList(List<DeviceDto> dtos);
}
