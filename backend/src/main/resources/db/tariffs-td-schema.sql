-- ============================================================
-- DDL de referencia: modelo relacional tarifas TD España
-- Ejecutar sobre base de datos de producción DESPUÉS de que
-- Hibernate haya creado las tablas con ddl-auto=update.
--
-- Cada sección corre en su propia transacción para que un
-- fallo puntual no aborte las demás.
-- ============================================================

-- ------------------------------------------------------------
-- tariffs
-- ------------------------------------------------------------
DO $$ BEGIN
  ALTER TABLE tariffs
    DROP COLUMN IF EXISTS type,
    DROP COLUMN IF EXISTS contracted_power_kw,
    ADD COLUMN IF NOT EXISTS access_tariff_code varchar(10) NOT NULL DEFAULT '2.0TD',
    ADD COLUMN IF NOT EXISTS geographic_zone varchar(20) NOT NULL DEFAULT 'PENINSULA';
EXCEPTION WHEN others THEN
  RAISE NOTICE 'tariffs ALTER skipped: %', SQLERRM;
END $$;

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
-- periods
-- ------------------------------------------------------------
DO $$ BEGIN
  ALTER TABLE periods
    DROP COLUMN IF EXISTS name,
    DROP COLUMN IF EXISTS start_hour,
    DROP COLUMN IF EXISTS end_hour,
    DROP COLUMN IF EXISTS day_type,
    DROP COLUMN IF EXISTS start_month,
    DROP COLUMN IF EXISTS end_month,
    ADD COLUMN IF NOT EXISTS period_code varchar(2) NOT NULL DEFAULT 'P1';
EXCEPTION WHEN others THEN
  RAISE NOTICE 'periods ALTER skipped: %', SQLERRM;
END $$;

DO $$ BEGIN
  ALTER TABLE periods ADD CONSTRAINT chk_periods_period_code
    CHECK (period_code IN ('P1', 'P2', 'P3', 'P4', 'P5', 'P6'));
EXCEPTION WHEN duplicate_object THEN NULL;
END $$;

CREATE UNIQUE INDEX IF NOT EXISTS ux_periods_tariff_period_code
  ON periods (tariff_id, period_code);

-- ------------------------------------------------------------
-- tariff_contracted_powers (solo si la tabla existe)
-- ------------------------------------------------------------
DO $$ BEGIN
  IF EXISTS (SELECT 1 FROM information_schema.tables
             WHERE table_schema = 'public'
               AND table_name   = 'tariff_contracted_powers') THEN

    ALTER TABLE tariff_contracted_powers ADD CONSTRAINT chk_tariff_contracted_powers_period_code
      CHECK (period_code IN ('P1', 'P2', 'P3', 'P4', 'P5', 'P6'));

  ELSE
    RAISE NOTICE 'tariff_contracted_powers no existe todavía, se omiten sus constraints.';
  END IF;
EXCEPTION WHEN duplicate_object THEN NULL;
END $$;

DO $$ BEGIN
  IF EXISTS (SELECT 1 FROM information_schema.tables
             WHERE table_schema = 'public'
               AND table_name   = 'tariff_contracted_powers') THEN

    ALTER TABLE tariff_contracted_powers ADD CONSTRAINT chk_tariff_contracted_powers_positive
      CHECK (contracted_power_kw > 0);

  END IF;
EXCEPTION WHEN duplicate_object THEN NULL;
END $$;

DO $$ BEGIN
  IF EXISTS (SELECT 1 FROM information_schema.tables
             WHERE table_schema = 'public'
               AND table_name   = 'tariff_contracted_powers') THEN

    CREATE INDEX IF NOT EXISTS ix_tariff_contracted_powers_tariff_id
      ON tariff_contracted_powers (tariff_id);

  END IF;
EXCEPTION WHEN others THEN NULL;
END $$;

-- ------------------------------------------------------------
-- tariff_calendar_slots (solo si la tabla existe)
-- ------------------------------------------------------------
DO $$ BEGIN
  IF EXISTS (SELECT 1 FROM information_schema.tables
             WHERE table_schema = 'public'
               AND table_name   = 'tariff_calendar_slots') THEN

    ALTER TABLE tariff_calendar_slots
      ADD COLUMN IF NOT EXISTS access_tariff_code VARCHAR(10);

    UPDATE tariff_calendar_slots
    SET access_tariff_code = '3.0TD'
    WHERE access_tariff_code IS NULL;

    ALTER TABLE tariff_calendar_slots
      ALTER COLUMN access_tariff_code SET NOT NULL;

  ELSE
    RAISE NOTICE 'tariff_calendar_slots no existe todavía, se omiten sus cambios.';
  END IF;
EXCEPTION WHEN others THEN
  RAISE NOTICE 'tariff_calendar_slots ALTER skipped: %', SQLERRM;
END $$;

DO $$ BEGIN
  IF EXISTS (SELECT 1 FROM information_schema.tables
             WHERE table_schema = 'public'
               AND table_name   = 'tariff_calendar_slots') THEN

    ALTER TABLE tariff_calendar_slots ADD CONSTRAINT chk_tariff_calendar_slots_access_tariff_code
      CHECK (access_tariff_code IN ('2.0TD', '3.0TD', '6.1TD', '6.2TD'));

  END IF;
EXCEPTION WHEN duplicate_object THEN NULL;
END $$;

DO $$ BEGIN
  IF EXISTS (SELECT 1 FROM information_schema.tables
             WHERE table_schema = 'public'
               AND table_name   = 'tariff_calendar_slots') THEN

    ALTER TABLE tariff_calendar_slots ADD CONSTRAINT chk_tariff_calendar_slots_geographic_zone
      CHECK (geographic_zone IN ('PENINSULA', 'CANARIAS', 'ISLAS_BALEARES', 'CEUTA', 'MELILLA'));

  END IF;
EXCEPTION WHEN duplicate_object THEN NULL;
END $$;

DO $$ BEGIN
  IF EXISTS (SELECT 1 FROM information_schema.tables
             WHERE table_schema = 'public'
               AND table_name   = 'tariff_calendar_slots') THEN

    ALTER TABLE tariff_calendar_slots ADD CONSTRAINT chk_tariff_calendar_slots_month_number
      CHECK (month_number BETWEEN 1 AND 12);

  END IF;
EXCEPTION WHEN duplicate_object THEN NULL;
END $$;

DO $$ BEGIN
  IF EXISTS (SELECT 1 FROM information_schema.tables
             WHERE table_schema = 'public'
               AND table_name   = 'tariff_calendar_slots') THEN

    ALTER TABLE tariff_calendar_slots ADD CONSTRAINT chk_tariff_calendar_slots_season_code
      CHECK (season_code IN ('HIGH', 'MID_HIGH', 'MID', 'LOW'));

  END IF;
EXCEPTION WHEN duplicate_object THEN NULL;
END $$;

DO $$ BEGIN
  IF EXISTS (SELECT 1 FROM information_schema.tables
             WHERE table_schema = 'public'
               AND table_name   = 'tariff_calendar_slots') THEN

    ALTER TABLE tariff_calendar_slots ADD CONSTRAINT chk_tariff_calendar_slots_day_type
      CHECK (day_type IN ('A', 'B', 'B1', 'C', 'D'));

  END IF;
EXCEPTION WHEN duplicate_object THEN NULL;
END $$;

DO $$ BEGIN
  IF EXISTS (SELECT 1 FROM information_schema.tables
             WHERE table_schema = 'public'
               AND table_name   = 'tariff_calendar_slots') THEN

    ALTER TABLE tariff_calendar_slots ADD CONSTRAINT chk_tariff_calendar_slots_period_code
      CHECK (period_code IN ('P1', 'P2', 'P3', 'P4', 'P5', 'P6'));

  END IF;
EXCEPTION WHEN duplicate_object THEN NULL;
END $$;

DO $$ BEGIN
  IF EXISTS (SELECT 1 FROM information_schema.tables
             WHERE table_schema = 'public'
               AND table_name   = 'tariff_calendar_slots') THEN

    CREATE UNIQUE INDEX IF NOT EXISTS ux_tariff_calendar_slots_lookup
      ON tariff_calendar_slots (
        access_tariff_code, geographic_zone, month_number, day_type, start_time, end_time
      );

    CREATE INDEX IF NOT EXISTS ix_tariff_calendar_slots_period_code
      ON tariff_calendar_slots (access_tariff_code, geographic_zone, month_number, day_type, period_code);

  END IF;
EXCEPTION WHEN others THEN
  RAISE NOTICE 'tariff_calendar_slots index skipped: %', SQLERRM;
END $$;
