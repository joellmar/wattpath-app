package com.joselumartos.jwtauthbackenddemo.repositories;

import com.joselumartos.jwtauthbackenddemo.entities.Alert;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Repository
public interface AlertRepository extends JpaRepository<Alert, Long> {
    List<Alert> findByUserUsername(String username);
    int deleteByIdAndUserUsername(Long id, String username);

    @Modifying
    @Transactional
    @Query("DELETE FROM Alert a WHERE a.device.id = :deviceId")
    int deleteByDeviceId(@Param("deviceId") Long deviceId);
}
