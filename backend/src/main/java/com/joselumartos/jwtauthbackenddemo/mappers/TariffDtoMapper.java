package com.joselumartos.jwtauthbackenddemo.mappers;

import com.joselumartos.jwtauthbackenddemo.dtos.TariffDto;
import com.joselumartos.jwtauthbackenddemo.entities.Tariff;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper(componentModel = "spring")
public interface TariffDtoMapper {

    TariffDto toDto(Tariff tariff);

    List<TariffDto> toDtoList(List<Tariff> tariffs);

    Tariff toEntity(TariffDto dto);

    List<Tariff> toEntityList (List<TariffDto> dtos);


}
