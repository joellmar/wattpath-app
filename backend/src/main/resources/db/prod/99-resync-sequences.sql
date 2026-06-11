-- ============================================================
-- Script 99: Resincronizar secuencias de identidad
--
-- CUÁNDO ejecutar:
--   Después de cualquier seed o importación manual que inserte
--   filas sin especificar id (ON CONFLICT DO NOTHING).
--   Si el id máximo en tabla supera el último valor de la
--   secuencia, el próximo INSERT del backend fallará por
--   duplicate key. Este script previene esa colisión.
--
-- CÓMO ejecutar desde el VPS:
--   docker compose exec -T timescaledb psql -U postgres -d wattimizer_db \
--     < backend/src/main/resources/db/prod/99-resync-sequences.sql
--
-- IDEMPOTENCIA: setval nunca es destructivo; solo mueve el puntero.
-- ============================================================

-- Función auxiliar: avanza la secuencia hasta MAX(id)+1 si ya hay filas;
-- si la tabla está vacía, deja la secuencia en 1 (siguiente INSERT = 1).
DO $$
DECLARE
  seq_name text;
  max_id   bigint;
BEGIN

  -- users
  seq_name := pg_get_serial_sequence('users', 'id');
  IF seq_name IS NOT NULL THEN
    SELECT COALESCE(MAX(id), 0) INTO max_id FROM users;
    PERFORM setval(seq_name, GREATEST(max_id, 1));
  END IF;

  -- devices
  seq_name := pg_get_serial_sequence('devices', 'id');
  IF seq_name IS NOT NULL THEN
    SELECT COALESCE(MAX(id), 0) INTO max_id FROM devices;
    PERFORM setval(seq_name, GREATEST(max_id, 1));
  END IF;

  -- tariffs
  seq_name := pg_get_serial_sequence('tariffs', 'id');
  IF seq_name IS NOT NULL THEN
    SELECT COALESCE(MAX(id), 0) INTO max_id FROM tariffs;
    PERFORM setval(seq_name, GREATEST(max_id, 1));
  END IF;

  -- periods
  seq_name := pg_get_serial_sequence('periods', 'id');
  IF seq_name IS NOT NULL THEN
    SELECT COALESCE(MAX(id), 0) INTO max_id FROM periods;
    PERFORM setval(seq_name, GREATEST(max_id, 1));
  END IF;

  -- tariff_contracted_powers
  seq_name := pg_get_serial_sequence('tariff_contracted_powers', 'id');
  IF seq_name IS NOT NULL THEN
    SELECT COALESCE(MAX(id), 0) INTO max_id FROM tariff_contracted_powers;
    PERFORM setval(seq_name, GREATEST(max_id, 1));
  END IF;

  -- tariff_calendar_slots
  seq_name := pg_get_serial_sequence('tariff_calendar_slots', 'id');
  IF seq_name IS NOT NULL THEN
    SELECT COALESCE(MAX(id), 0) INTO max_id FROM tariff_calendar_slots;
    PERFORM setval(seq_name, GREATEST(max_id, 1));
  END IF;

  -- alerts
  seq_name := pg_get_serial_sequence('alerts', 'id');
  IF seq_name IS NOT NULL THEN
    SELECT COALESCE(MAX(id), 0) INTO max_id FROM alerts;
    PERFORM setval(seq_name, GREATEST(max_id, 1));
  END IF;

  -- federated_identities
  seq_name := pg_get_serial_sequence('federated_identities', 'id');
  IF seq_name IS NOT NULL THEN
    SELECT COALESCE(MAX(id), 0) INTO max_id FROM federated_identities;
    PERFORM setval(seq_name, GREATEST(max_id, 1));
  END IF;

END $$;
