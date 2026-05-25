package com.joselumartos.jwtauthbackenddemo.dtos;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record EventsRpc(
        @JsonProperty("src")
        String source,
        Params params
) {}

