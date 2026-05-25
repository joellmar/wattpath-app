package com.joselumartos.jwtauthbackenddemo.mappers;

import com.joselumartos.jwtauthbackenddemo.dtos.PeriodDto;
import com.joselumartos.jwtauthbackenddemo.entities.Period;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper(componentModel = "spring")
public interface PeriodDtoMapper {

    PeriodDto toDto(Period period);

    Period toEntity(PeriodDto dto);

    List<PeriodDto> toDtoList(List<Period> periods);

    List<Period> toEntityList(List<PeriodDto> dtos);
}
