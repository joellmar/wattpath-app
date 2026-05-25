package com.joselumartos.jwtauthbackenddemo.entities;

import lombok.*;

import java.io.Serializable;
import java.time.Instant;

@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@EqualsAndHashCode
public class ReadingId implements Serializable {
    private Long device;
    private Instant time;
}
