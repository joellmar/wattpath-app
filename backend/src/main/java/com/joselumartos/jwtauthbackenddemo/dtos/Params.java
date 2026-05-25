package com.joselumartos.jwtauthbackenddemo.dtos;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record Params(
        @JsonProperty("ts")
        Double timestamp,
        @JsonProperty("switch:0")
        Switch switchData
) {}
