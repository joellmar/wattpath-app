package com.joselumartos.jwtauthbackenddemo.mappers;

import com.joselumartos.jwtauthbackenddemo.dtos.TariffDto;
import com.joselumartos.jwtauthbackenddemo.entities.Tariff;
import org.mapstruct.Mapper;

import java.util.List;

// uses declara explícitamente los mappers delegados para las colecciones anidadas
// periods (Period -> PeriodDto) y contractedPowers (TariffContractedPower -> TariffContractedPowerDto).
// Sin esto MapStruct no puede generar el impl y el compilador falla en la fase de APT.
@Mapper(
        componentModel = "spring",
        uses = {
                PeriodDtoMapper.class,
                TariffContractedPowerDtoMapper.class
        }
)
public interface TariffDtoMapper {

    TariffDto toDto(Tariff tariff);

    List<TariffDto> toDtoList(List<Tariff> tariffs);

    Tariff toEntity(TariffDto dto);

    List<Tariff> toEntityList (List<TariffDto> dtos);


}
