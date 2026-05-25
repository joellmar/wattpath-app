package com.joselumartos.jwtauthbackenddemo.repositories;

import com.joselumartos.jwtauthbackenddemo.entities.Tariff;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TariffRepository extends JpaRepository<Tariff, Long> {
}
