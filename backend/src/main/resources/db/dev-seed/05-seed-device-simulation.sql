-- ============================================================
-- Script 05: Seed de dispositivos simulados (sin hardware físico)
--
-- CUÁNDO ejecutar:
--   Después de 03-seed-users-dev.sql y tras arrancar el backend
--   con la entidad Device actualizada (columna simulation_profile).
--
-- QUÉ HACE:
--   Inserta un dispositivo virtual por perfil de simulación.
--   El job IotTelemetrySimulationJob genera lecturas artificiales
--   para probar dashboard, gráficas, costes y alertas sin Shelly.
--
-- IDEMPOTENCIA:
--   ON CONFLICT (mac_address) DO NOTHING
--   Backfill de simulation_profile en simuladores existentes.
-- ============================================================

BEGIN;

ALTER TABLE devices
    ADD COLUMN IF NOT EXISTS simulation_profile varchar(32);

UPDATE devices
SET simulation_profile = 'SINE_WAVE'
WHERE is_simulated = true
  AND (simulation_profile IS NULL OR simulation_profile = '');

INSERT INTO devices (
    user_id,
    name,
    mac_address,
    is_on,
    is_simulated,
    simulation_profile,
    created_at,
    updated_at,
    created_by,
    updated_by
)
SELECT
    u.id,
    seed.name,
    seed.mac_address,
    true,
    true,
    seed.simulation_profile,
    NOW(),
    NOW(),
    'seed',
    'seed'
FROM users u
CROSS JOIN (
    VALUES
        ('Simulador onda senoidal', 'SIM000000001', 'SINE_WAVE'),
        ('Simulador horno',         'SIM000000002', 'OVEN'),
        ('Simulador lavadora',      'SIM000000003', 'WASHING_MACHINE'),
        ('Simulador televisor',     'SIM000000004', 'TELEVISION'),
        ('Simulador ventilador',    'SIM000000005', 'FAN'),
        ('Simulador PC',            'SIM000000006', 'DESKTOP_PC'),
        ('Simulador nevera',        'SIM000000007', 'FRIDGE'),
        ('Simulador consumo fantasma', 'SIM000000008', 'STANDBY'),
        ('Simulador carga alta',    'SIM000000009', 'CONSTANT_HIGH_LOAD')
) AS seed(name, mac_address, simulation_profile)
WHERE u.username = 'admin@wattimizer.dev'
ON CONFLICT (mac_address) DO UPDATE
SET
    name = EXCLUDED.name,
    is_simulated = true,
    simulation_profile = EXCLUDED.simulation_profile,
    updated_at = NOW(),
    updated_by = 'seed';

COMMIT;

-- Verificación:
-- SELECT name, mac_address, is_on, is_simulated, simulation_profile, user_id
-- FROM devices
-- WHERE mac_address LIKE 'SIM%'
-- ORDER BY mac_address;
