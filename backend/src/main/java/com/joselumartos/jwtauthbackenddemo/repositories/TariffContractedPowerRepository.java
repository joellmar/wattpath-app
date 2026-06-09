package com.joselumartos.jwtauthbackenddemo.repositories;

import com.joselumartos.jwtauthbackenddemo.entities.TariffContractedPower;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface TariffContractedPowerRepository extends JpaRepository<TariffContractedPower, Long> {

    Optional<TariffContractedPower> findByTariffIdAndPeriodCode(Long tariffId, String periodCode);
}
