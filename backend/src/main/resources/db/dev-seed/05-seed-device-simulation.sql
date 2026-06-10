-- ============================================================
-- Script 05: Seed del dispositivo de simulación (sin hardware físico)
--
-- CUÁNDO ejecutar:
--   Después de 03-seed-users-dev.sql.
--   Opcional: solo si quieres trabajar sin el Shelly físico conectado.
--
-- QUÉ HACE:
--   Inserta un dispositivo virtual con is_simulated=true.
--   El backend tiene un job de simulación (saveSimulatedReading) que
--   puede generar lecturas artificiales para este dispositivo, lo que
--   permite probar el dashboard, las gráficas y los cálculos de coste
--   sin necesidad de tener el enchufe Shelly encendido y publicando.
--
-- DISPOSITIVO:
--   MAC sintética:  SIM000000001  (no es una MAC de hardware real)
--   Nombre:         Simulador IoT
--   is_simulated:   true
--
-- IDEMPOTENCIA:
--   ON CONFLICT (mac_address) DO NOTHING
-- ============================================================

BEGIN;

INSERT INTO devices (
    user_id,
    name,
    mac_address,
    is_on,
    is_simulated,
    created_at,
    updated_at,
    created_by,
    updated_by
)
SELECT
    u.id,
    'Simulador IoT',
    'SIM000000001',
    true,            -- is_on=true: el simulador siempre está "encendido"
    true,            -- is_simulated=true: es un dispositivo virtual
    NOW(),
    NOW(),
    'seed',
    'seed'
FROM users u
WHERE u.username = 'admin@wattimizer.dev'
ON CONFLICT (mac_address) DO NOTHING;

COMMIT;

-- Verificación:
-- SELECT id, name, mac_address, is_on, is_simulated, user_id FROM devices WHERE mac_address = 'SIM000000001';
