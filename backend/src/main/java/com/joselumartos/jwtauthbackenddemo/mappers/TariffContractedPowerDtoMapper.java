package com.joselumartos.jwtauthbackenddemo.mappers;

import com.joselumartos.jwtauthbackenddemo.dtos.TariffContractedPowerDto;
import com.joselumartos.jwtauthbackenddemo.entities.TariffContractedPower;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper(componentModel = "spring")
public interface TariffContractedPowerDtoMapper {

    TariffContractedPowerDto toDto(TariffContractedPower entity);

    TariffContractedPower toEntity(TariffContractedPowerDto dto);

    List<TariffContractedPowerDto> toDtoList(List<TariffContractedPower> entities);

    List<TariffContractedPower> toEntityList(List<TariffContractedPowerDto> dtos);
}
