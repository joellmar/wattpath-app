package com.joselumartos.jwtauthbackenddemo.repositories;

import com.joselumartos.jwtauthbackenddemo.entities.Alert;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AlertRepository extends JpaRepository<Alert, Long> {
    List<Alert> findByUserUsername(String username);
    int deleteByIdAndUserUsername(Long id, String username);
}
