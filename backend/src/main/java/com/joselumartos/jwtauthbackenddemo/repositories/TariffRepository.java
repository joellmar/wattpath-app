package com.joselumartos.jwtauthbackenddemo.repositories;

import com.joselumartos.jwtauthbackenddemo.entities.Tariff;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TariffRepository extends JpaRepository<Tariff, Long> {

    /**
     * Devuelve solo las tarifas del catálogo maestro:
     * excluye los clones privados que están asignados a algún usuario.
     */
    @Query("SELECT t FROM Tariff t WHERE t.id NOT IN " +
           "(SELECT u.tariff.id FROM UserEntity u WHERE u.tariff IS NOT NULL)")
    List<Tariff> findAllCatalog();
}
