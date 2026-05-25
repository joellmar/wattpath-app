package com.joselumartos.jwtauthbackenddemo.dtos;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record Status(
        Boolean output,
        @JsonProperty("apower")
        Double activePower,
        @JsonProperty("aenergy")
        ActiveEnergy activeEnergy
) {}
