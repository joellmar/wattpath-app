package com.joselumartos.jwtauthbackenddemo.repositories;

import com.joselumartos.jwtauthbackenddemo.entities.Period;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalTime;
import java.util.Optional;

@Repository
public interface PeriodRepository extends JpaRepository<Period, Long> {

    @Query("SELECT p FROM Period p WHERE p.tariff.id = :tariffId AND " +
            "((p.startHour <= :currentTime AND p.endHour >= :currentTime) OR " +
            "(p.startHour > p.endHour AND (:currentTime >= p.startHour OR :currentTime <= p.endHour)))")
    Optional<Period> findApplicablePeriod(@Param("tariffId") Long tariffId, @Param("currentTime")LocalTime currentTime);
}
