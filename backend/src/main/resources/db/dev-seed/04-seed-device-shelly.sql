-- ============================================================
-- Script 04: Seed del dispositivo Shelly Plug S Gen 3 (hardware real)
--
-- CUÁNDO ejecutar:
--   Después de 03-seed-users-dev.sql (necesita que exista el usuario admin).
--
-- QUÉ HACE:
--   Inserta el enchufe físico con su MAC real en la tabla 'devices',
--   asignándolo directamente al usuario admin de desarrollo.
--
--   Esto evita que el primer mensaje MQTT del Shelly lo auto-provisione
--   con user_id=NULL (modo SYSTEM), obligándote a reclamarlo manualmente
--   desde la UI. En un entorno de desarrollo es más práctico tenerlo
--   pre-asignado al admin desde el principio.
--
-- DISPOSITIVO:
--   Modelo:      Shelly Plug S Gen 3
--   ID Shelly:   shellyplugsg3-9070694d3590
--   MAC address: 9070694d3590  (formato sin separadores, 12 hex)
--   Tópico MQTT: shellyplugsg3-9070694d3590/events/rpc
--               shellyplugsg3-9070694d3590/status/switch:0
--
-- IDEMPOTENCIA:
--   ON CONFLICT (mac_address) DO NOTHING — no duplica el dispositivo
--   si ya existe con esa MAC (por ej. si el backend lo auto-provisionó
--   antes de ejecutar este script).
--
-- NOTA SOBRE AUTO-PROVISIONING:
--   Si el backend recibe un mensaje MQTT de esta MAC antes de que este
--   script se ejecute, creará el dispositivo con user_id=NULL y
--   created_by='SYSTEM'. En ese caso este script no sobreescribe nada
--   (el ON CONFLICT lo impide). Para asignarlo al admin en ese caso,
--   usa la función "Reclamar dispositivo" en la UI de gestión de
--   dispositivos, o ejecuta este UPDATE manual:
--
--   UPDATE devices
--   SET user_id = (SELECT id FROM users WHERE username = 'admin@wattimizer.dev'),
--       name = 'Shelly Plug S (DEV)',
--       updated_at = NOW(),
--       updated_by = 'seed'
--   WHERE mac_address = '9070694d3590';
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
    'Shelly Plug S (DEV)',
    '9070694d3590',
    false,           -- is_on=false: el estado real llegará del primer mensaje MQTT
    false,           -- is_simulated=false: es hardware físico
    NOW(),
    NOW(),
    'seed',
    'seed'
FROM users u
WHERE u.username = 'admin@wattimizer.dev'
ON CONFLICT (mac_address) DO NOTHING;

COMMIT;

-- Verificación:
-- SELECT id, name, mac_address, is_on, is_simulated, user_id FROM devices WHERE mac_address = '9070694d3590';
