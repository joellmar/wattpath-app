package com.joselumartos.jwtauthbackenddemo.mappers;

import com.joselumartos.jwtauthbackenddemo.dtos.ReadingResponse;
import com.joselumartos.jwtauthbackenddemo.entities.Reading;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface ReadingResponseMapper {

    @Mapping(source = "device.macAddress", target = "macAddress")
    ReadingResponse toDto(Reading reading);

    List<ReadingResponse> toDtoList(List<Reading> readings);

}
