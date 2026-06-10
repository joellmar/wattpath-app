-- ============================================================
-- Script 01: Convertir la tabla 'readings' en hypertable
--
-- CUÁNDO ejecutar:
--   Después de 00-extensions.sql y ANTES de que lleguen datos MQTT.
--   Hibernate (ddl-auto=update) crea 'readings' como tabla PostgreSQL
--   normal. Este script la convierte en una hypertable de TimescaleDB
--   particionada por la columna 'time'.
--
-- REQUISITO: la tabla debe estar VACÍA. En una BD recién creada por
--   Hibernate esto siempre se cumple (no hay datos MQTT aún).
--   Si ya hay filas, añade la opción: migrate_data => true
--
-- VERIFICACIÓN POST-EJECUCIÓN:
--   SELECT hypertable_name, num_chunks
--   FROM timescaledb_information.hypertables;
--   → Debe aparecer: readings | 0
-- ============================================================

-- Convertir la tabla vacía en hypertable particionada por 'time'.
-- TimescaleDB gestionará automáticamente los chunks semanales.
SELECT create_hypertable('readings', 'time');

-- Si por algún motivo la tabla ya tiene datos (por ej. el backend estuvo
-- corriendo y recibió mensajes MQTT antes de este script), usa esto:
-- SELECT create_hypertable('readings', 'time', migrate_data => true);
