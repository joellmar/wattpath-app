-- ============================================================
-- Script 03: Seed de usuarios de desarrollo
--
-- CUÁNDO ejecutar:
--   Después de 01-hypertable.sql y de tariffs-td-schema.sql.
--   El backend puede estar parado o corriendo; si está corriendo
--   necesitarás reiniciarlo para que el SecurityContext cargue
--   los nuevos usuarios.
--
-- CREDENCIALES GENERADAS:
--   admin@wattimizer.dev  /  Admin_Wattimizer1!  → ROLE_ADMIN
--   user@wattimizer.dev   /  User_Wattimizer1!   → ROLE_USER
--
-- SEGURIDAD:
--   Los hashes se generan con bcrypt coste 10 mediante pgcrypto.
--   Son 100% compatibles con BCryptPasswordEncoder de Spring Security.
--   La función gen_salt('bf', 10) genera una sal aleatoria en cada
--   ejecución, por lo que los hashes resultantes varían entre ejecuciones
--   pero siguen siendo válidos para las mismas contraseñas.
--
-- IDEMPOTENCIA:
--   El INSERT usa ON CONFLICT DO NOTHING para que puedas re-ejecutar
--   el script sin duplicar usuarios ni obtener errores.
--   (La columna 'username' tiene un índice UNIQUE en la tabla.)
--
-- EXTENSIÓN REQUERIDA: pgcrypto (script 00-extensions.sql).
-- ============================================================

BEGIN;

-- ── Usuario administrador ────────────────────────────────────────────────────
-- Contraseña en texto plano: Admin_Wattimizer1!
-- Tiene acceso a CRUD del catálogo de tarifas y endpoint /register/admin.
INSERT INTO users (username, password, role, active, created_at, updated_at, created_by, updated_by)
VALUES (
    'admin@wattimizer.dev',
    crypt('Admin_Wattimizer1!', gen_salt('bf', 10)),
    'ROLE_ADMIN',
    true,
    NOW(),
    NOW(),
    'seed',
    'seed'
)
ON CONFLICT (username) DO NOTHING;

-- ── Usuario estándar ─────────────────────────────────────────────────────────
-- Contraseña en texto plano: User_Wattimizer1!
-- Solo puede ver y gestionar sus propios dispositivos y tarifa.
INSERT INTO users (username, password, role, active, created_at, updated_at, created_by, updated_by)
VALUES (
    'user@wattimizer.dev',
    crypt('User_Wattimizer1!', gen_salt('bf', 10)),
    'ROLE_USER',
    true,
    NOW(),
    NOW(),
    'seed',
    'seed'
)
ON CONFLICT (username) DO NOTHING;

COMMIT;

-- Verificación (ejecuta esta consulta por separado para confirmar):
-- SELECT id, username, role, active FROM users ORDER BY id;
