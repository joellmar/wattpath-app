-- ============================================================
-- DDL de referencia: modelo relacional tarifas TD España
-- Ejecutar sobre base de datos de producción DESPUÉS de que
-- Hibernate haya creado las tablas con ddl-auto=update.
--
-- NOTA: PostgreSQL no admite ADD CONSTRAINT IF NOT EXISTS.
-- Los constraints se añaden dentro de bloques DO $$ BEGIN ...
-- EXCEPTION WHEN duplicate_object THEN NULL; END $$
-- para que el script sea idempotente.
-- ============================================================

BEGIN;

-- ------------------------------------------------------------
-- tariffs: eliminar columnas legacy y añadir peaje + zona.
-- Con base limpia, Hibernate ya habrá creado la tabla nueva.
-- Estas ALTER solo son necesarias si se migra desde un esquema anterior.
-- ------------------------------------------------------------
ALTER TABLE tariffs
  DROP COLUMN IF EXISTS type,
  DROP COLUMN IF EXISTS contracted_power_kw,
  ADD COLUMN IF NOT EXISTS access_tariff_code varchar(10) NOT NULL DEFAULT '2.0TD',
  ADD COLUMN IF NOT EXISTS geographic_zone varchar(20) NOT NULL DEFAULT 'PENINSULA';

DO $$ BEGIN
  ALTER TABLE tariffs ADD CONSTRAINT chk_tariffs_access_tariff_code
    CHECK (access_tariff_code IN ('2.0TD', '3.0TD', '6.1TD', '6.2TD'));
EXCEPTION WHEN duplicate_object THEN NULL;
END $$;

DO $$ BEGIN
  ALTER TABLE tariffs ADD CONSTRAINT chk_tariffs_geographic_zone
    CHECK (geographic_zone IN ('PENINSULA', 'CANARIAS', 'ISLAS_BALEARES', 'CEUTA', 'MELILLA'));
EXCEPTION WHEN duplicate_object THEN NULL;
END $$;

-- ------------------------------------------------------------
-- periods: eliminar campos de calendario legacy y añadir period_code.
-- ------------------------------------------------------------
ALTER TABLE periods
  DROP COLUMN IF EXISTS name,
  DROP COLUMN IF EXISTS start_hour,
  DROP COLUMN IF EXISTS end_hour,
  DROP COLUMN IF EXISTS day_type,
  DROP COLUMN IF EXISTS start_month,
  DROP COLUMN IF EXISTS end_month,
  ADD COLUMN IF NOT EXISTS period_code varchar(2) NOT NULL DEFAULT 'P1';

DO $$ BEGIN
  ALTER TABLE periods ADD CONSTRAINT chk_periods_period_code
    CHECK (period_code IN ('P1', 'P2', 'P3', 'P4', 'P5', 'P6'));
EXCEPTION WHEN duplicate_object THEN NULL;
END $$;

CREATE UNIQUE INDEX IF NOT EXISTS ux_periods_tariff_period_code
  ON periods (tariff_id, period_code);

-- ------------------------------------------------------------
-- tariff_contracted_powers: potencia contratada por periodo.
-- Hibernate crea la tabla; este bloque añade los CHECK que Hibernate omite.
-- ------------------------------------------------------------
DO $$ BEGIN
  ALTER TABLE tariff_contracted_powers ADD CONSTRAINT chk_tariff_contracted_powers_period_code
    CHECK (period_code IN ('P1', 'P2', 'P3', 'P4', 'P5', 'P6'));
EXCEPTION WHEN duplicate_object THEN NULL;
END $$;

DO $$ BEGIN
  ALTER TABLE tariff_contracted_powers ADD CONSTRAINT chk_tariff_contracted_powers_positive
    CHECK (contracted_power_kw > 0);
EXCEPTION WHEN duplicate_object THEN NULL;
END $$;

CREATE INDEX IF NOT EXISTS ix_tariff_contracted_powers_tariff_id
  ON tariff_contracted_powers (tariff_id);

-- ------------------------------------------------------------
-- tariff_calendar_slots: calendario regulatorio global.
-- Añadir el discriminador de peaje si aún no existe y aplicar
-- constraints e índices para la resolución peaje+zona+mes+tipo_día+hora→Pn.
-- ------------------------------------------------------------

-- Añadir el discriminador de peaje. Backfill a '3.0TD' para filas legacy si las hubiera.
ALTER TABLE tariff_calendar_slots
  ADD COLUMN IF NOT EXISTS access_tariff_code VARCHAR(10);

UPDATE tariff_calendar_slots
SET access_tariff_code = '3.0TD'
WHERE access_tariff_code IS NULL;

ALTER TABLE tariff_calendar_slots
  ALTER COLUMN access_tariff_code SET NOT NULL;

DO $$ BEGIN
  ALTER TABLE tariff_calendar_slots ADD CONSTRAINT chk_tariff_calendar_slots_access_tariff_code
    CHECK (access_tariff_code IN ('2.0TD', '3.0TD', '6.1TD', '6.2TD'));
EXCEPTION WHEN duplicate_object THEN NULL;
END $$;

DO $$ BEGIN
  ALTER TABLE tariff_calendar_slots ADD CONSTRAINT chk_tariff_calendar_slots_geographic_zone
    CHECK (geographic_zone IN ('PENINSULA', 'CANARIAS', 'ISLAS_BALEARES', 'CEUTA', 'MELILLA'));
EXCEPTION WHEN duplicate_object THEN NULL;
END $$;

DO $$ BEGIN
  ALTER TABLE tariff_calendar_slots ADD CONSTRAINT chk_tariff_calendar_slots_month_number
    CHECK (month_number BETWEEN 1 AND 12);
EXCEPTION WHEN duplicate_object THEN NULL;
END $$;

DO $$ BEGIN
  ALTER TABLE tariff_calendar_slots ADD CONSTRAINT chk_tariff_calendar_slots_season_code
    CHECK (season_code IN ('HIGH', 'MID_HIGH', 'MID', 'LOW'));
EXCEPTION WHEN duplicate_object THEN NULL;
END $$;

DO $$ BEGIN
  ALTER TABLE tariff_calendar_slots ADD CONSTRAINT chk_tariff_calendar_slots_day_type
    CHECK (day_type IN ('A', 'B', 'B1', 'C', 'D'));
EXCEPTION WHEN duplicate_object THEN NULL;
END $$;

DO $$ BEGIN
  ALTER TABLE tariff_calendar_slots ADD CONSTRAINT chk_tariff_calendar_slots_period_code
    CHECK (period_code IN ('P1', 'P2', 'P3', 'P4', 'P5', 'P6'));
EXCEPTION WHEN duplicate_object THEN NULL;
END $$;

DO $$ BEGIN
  ALTER TABLE tariff_calendar_slots ADD CONSTRAINT chk_tariff_calendar_slots_time_range
    CHECK (
      start_time <> end_time
      OR (day_type = 'D' AND period_code = 'P6')
    );
EXCEPTION WHEN duplicate_object THEN NULL;
END $$;

-- Índice único de búsqueda: la PK lógica del calendario es peaje+zona+mes+tipo+inicio+fin.
DROP INDEX IF EXISTS ix_tariff_calendar_slots_lookup;

CREATE UNIQUE INDEX IF NOT EXISTS ux_tariff_calendar_slots_lookup
  ON tariff_calendar_slots (
    access_tariff_code,
    geographic_zone,
    month_number,
    day_type,
    start_time,
    end_time
  );

-- Índice auxiliar para las consultas de period_code por peaje+zona+mes+tipo_día.
DROP INDEX IF EXISTS ix_tariff_calendar_slots_period_code;

CREATE INDEX IF NOT EXISTS ix_tariff_calendar_slots_period_code
  ON tariff_calendar_slots (access_tariff_code, geographic_zone, month_number, day_type, period_code);

COMMIT;
