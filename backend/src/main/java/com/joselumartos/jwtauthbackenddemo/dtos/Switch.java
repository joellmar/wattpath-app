package com.joselumartos.jwtauthbackenddemo.dtos;


import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record Switch(
    @JsonProperty("aenergy")
    ActiveEnergy activeEnergy,
    @JsonProperty("apower")
    Double activePower
) {}
