package com.joselumartos.jwtauthbackenddemo.repositories;

import com.joselumartos.jwtauthbackenddemo.entities.Reading;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface ReadingRepository extends JpaRepository<Reading, Long> {
    Optional<Reading> findFirstByDeviceMacAddressOrderByTimeDesc(String macAddress);

    Optional<Reading> findByTimeAndDeviceMacAddress(Instant time, String macAddress);

    @Query("SELECT r FROM Reading r WHERE r.device.macAddress = :macAddress " +
            "AND r.time >= :start AND r.time <= :end ORDER BY r.time ASC")
    List<Reading> findReadingsInInterval(
            @Param("macAddress") String macAddress,
            @Param("start") Instant start,
            @Param("end") Instant end);

    @Modifying
    @Transactional
    @Query("DELETE FROM Reading r WHERE r.time = :time AND r.device.macAddress = :macAddress")
    Long deleteByTimeAndDeviceMacAddress(@Param("time") Instant time, @Param("macAddress") String macAddress);
}
