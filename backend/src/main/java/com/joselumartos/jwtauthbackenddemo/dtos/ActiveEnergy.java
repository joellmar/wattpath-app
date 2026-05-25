package com.joselumartos.jwtauthbackenddemo.dtos;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ActiveEnergy(
        Double total
) { }
