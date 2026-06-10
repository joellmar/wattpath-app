-- ============================================================
-- Script 00: Activar extensiones PostgreSQL necesarias
--
-- CUÁNDO ejecutar:
--   Una sola vez, justo después del primer arranque del backend
--   (Hibernate crea las tablas vacías, luego PARAS el backend
--   y ejecutas este script antes de cualquier otro).
--
-- ORDEN: este script va PRIMERO de todos los de dev-seed/.
-- ============================================================

-- timescaledb: habilita el motor de series temporales.
-- La imagen Docker ya la tiene instalada; este comando la activa
-- en la base de datos 'wattimizer_db' si aún no lo está.
CREATE EXTENSION IF NOT EXISTS timescaledb;

-- pgcrypto: necesario para generar hashes bcrypt desde SQL
-- (lo usan los scripts de seed de usuarios).
-- Compatible con los hashes que produce BCryptPasswordEncoder de Spring Security.
CREATE EXTENSION IF NOT EXISTS pgcrypto;
