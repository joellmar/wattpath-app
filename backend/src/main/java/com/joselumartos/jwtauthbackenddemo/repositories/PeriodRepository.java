package com.joselumartos.jwtauthbackenddemo.repositories;

import com.joselumartos.jwtauthbackenddemo.entities.Period;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PeriodRepository extends JpaRepository<Period, Long> {

    // La búsqueda por franja horaria (startHour/endHour) se ha eliminado:
    // el mapping hora -> period_code ahora lo resuelve TariffCalendarSlotRepository.
    // Una vez conocido el periodCode, se usa este método para obtener el precio contractual.
    Optional<Period> findByTariffIdAndPeriodCode(Long tariffId, String periodCode);
}
