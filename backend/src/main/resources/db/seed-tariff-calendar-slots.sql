-- ============================================================
-- Seed regulatorio: tariff_calendar_slots
-- Fuente normativa: Circular CNMC 3/2020 (BOE 7-oct-2020)
-- Zonas cubiertas: PENINSULA, ISLAS_BALEARES
-- Peajes cubiertos: 2.0TD, 3.0TD
-- Total filas: 336 (84 por combinación peaje x zona)
--
-- Estructura del bloque diario:
--   Península     → 00-08 / 08-09 / 09-14 / 14-18 / 18-22 / 22-23:59
--   Illes Balears → 00-08 / 08-10 / 10-15 / 15-18 / 18-22 / 22-23:59
--
-- Mapeo temporada → tipo de día laborable:
--   HIGH     → A  |  MID_HIGH → B  |  MID → B1  |  LOW → C
--
-- Nota sobre end_time '23:59':
--   PostgreSQL TIME no admite 24:00. El último slot diario (22:00-00:00)
--   se almacena con end_time='23:59'. La query findPeriodCode cubre de
--   forma práctica todas las lecturas del tramo 22:00-23:58:xx.
--   Para cubrir el minuto 23:59, añadir a la query la cláusula:
--     OR (cs.endTime = '23:59' AND :localTime >= cs.startTime)
--
-- Nota sobre tipo D (fines de semana + festivos):
--   3.0TD → period_code='P6', start=end='00:00' (semántica "día completo").
--           Permitido por chk_tariff_calendar_slots_time_range.
--   2.0TD → period_code='P3', start='00:00', end='23:59' (start ≠ end
--           porque el constraint solo admite start=end para P6+D).
-- ============================================================

BEGIN;

INSERT INTO tariff_calendar_slots
    (access_tariff_code, geographic_zone, month_number, season_code, day_type, period_code, start_time, end_time)
VALUES

-- ============================================================
-- 2.0TD | PENÍNSULA
-- P1=punta (09-14 y 18-22), P2=llano (intermedio), P3=valle (00-08)
-- Asignación de periodo idéntica para todos los tipos A/B/B1/C.
-- Solo varía el day_type letter para que findWorkdayType devuelva la
-- temporada correcta a partir de la cual el resolver calcula P1/P2/P3.
-- ============================================================

-- Enero — HIGH, tipo A
('2.0TD','PENINSULA',1,'HIGH','A','P3','00:00','08:00'),
('2.0TD','PENINSULA',1,'HIGH','A','P2','08:00','09:00'),
('2.0TD','PENINSULA',1,'HIGH','A','P1','09:00','14:00'),
('2.0TD','PENINSULA',1,'HIGH','A','P2','14:00','18:00'),
('2.0TD','PENINSULA',1,'HIGH','A','P1','18:00','22:00'),
('2.0TD','PENINSULA',1,'HIGH','A','P2','22:00','23:59'),
('2.0TD','PENINSULA',1,'HIGH','D','P3','00:00','23:59'),

-- Febrero — HIGH, tipo A
('2.0TD','PENINSULA',2,'HIGH','A','P3','00:00','08:00'),
('2.0TD','PENINSULA',2,'HIGH','A','P2','08:00','09:00'),
('2.0TD','PENINSULA',2,'HIGH','A','P1','09:00','14:00'),
('2.0TD','PENINSULA',2,'HIGH','A','P2','14:00','18:00'),
('2.0TD','PENINSULA',2,'HIGH','A','P1','18:00','22:00'),
('2.0TD','PENINSULA',2,'HIGH','A','P2','22:00','23:59'),
('2.0TD','PENINSULA',2,'HIGH','D','P3','00:00','23:59'),

-- Marzo — MID_HIGH, tipo B
('2.0TD','PENINSULA',3,'MID_HIGH','B','P3','00:00','08:00'),
('2.0TD','PENINSULA',3,'MID_HIGH','B','P2','08:00','09:00'),
('2.0TD','PENINSULA',3,'MID_HIGH','B','P1','09:00','14:00'),
('2.0TD','PENINSULA',3,'MID_HIGH','B','P2','14:00','18:00'),
('2.0TD','PENINSULA',3,'MID_HIGH','B','P1','18:00','22:00'),
('2.0TD','PENINSULA',3,'MID_HIGH','B','P2','22:00','23:59'),
('2.0TD','PENINSULA',3,'MID_HIGH','D','P3','00:00','23:59'),

-- Abril — LOW, tipo C
('2.0TD','PENINSULA',4,'LOW','C','P3','00:00','08:00'),
('2.0TD','PENINSULA',4,'LOW','C','P2','08:00','09:00'),
('2.0TD','PENINSULA',4,'LOW','C','P1','09:00','14:00'),
('2.0TD','PENINSULA',4,'LOW','C','P2','14:00','18:00'),
('2.0TD','PENINSULA',4,'LOW','C','P1','18:00','22:00'),
('2.0TD','PENINSULA',4,'LOW','C','P2','22:00','23:59'),
('2.0TD','PENINSULA',4,'LOW','D','P3','00:00','23:59'),

-- Mayo — LOW, tipo C
('2.0TD','PENINSULA',5,'LOW','C','P3','00:00','08:00'),
('2.0TD','PENINSULA',5,'LOW','C','P2','08:00','09:00'),
('2.0TD','PENINSULA',5,'LOW','C','P1','09:00','14:00'),
('2.0TD','PENINSULA',5,'LOW','C','P2','14:00','18:00'),
('2.0TD','PENINSULA',5,'LOW','C','P1','18:00','22:00'),
('2.0TD','PENINSULA',5,'LOW','C','P2','22:00','23:59'),
('2.0TD','PENINSULA',5,'LOW','D','P3','00:00','23:59'),

-- Junio — MID, tipo B1
('2.0TD','PENINSULA',6,'MID','B1','P3','00:00','08:00'),
('2.0TD','PENINSULA',6,'MID','B1','P2','08:00','09:00'),
('2.0TD','PENINSULA',6,'MID','B1','P1','09:00','14:00'),
('2.0TD','PENINSULA',6,'MID','B1','P2','14:00','18:00'),
('2.0TD','PENINSULA',6,'MID','B1','P1','18:00','22:00'),
('2.0TD','PENINSULA',6,'MID','B1','P2','22:00','23:59'),
('2.0TD','PENINSULA',6,'MID','D','P3','00:00','23:59'),

-- Julio — HIGH, tipo A
('2.0TD','PENINSULA',7,'HIGH','A','P3','00:00','08:00'),
('2.0TD','PENINSULA',7,'HIGH','A','P2','08:00','09:00'),
('2.0TD','PENINSULA',7,'HIGH','A','P1','09:00','14:00'),
('2.0TD','PENINSULA',7,'HIGH','A','P2','14:00','18:00'),
('2.0TD','PENINSULA',7,'HIGH','A','P1','18:00','22:00'),
('2.0TD','PENINSULA',7,'HIGH','A','P2','22:00','23:59'),
('2.0TD','PENINSULA',7,'HIGH','D','P3','00:00','23:59'),

-- Agosto — MID, tipo B1
('2.0TD','PENINSULA',8,'MID','B1','P3','00:00','08:00'),
('2.0TD','PENINSULA',8,'MID','B1','P2','08:00','09:00'),
('2.0TD','PENINSULA',8,'MID','B1','P1','09:00','14:00'),
('2.0TD','PENINSULA',8,'MID','B1','P2','14:00','18:00'),
('2.0TD','PENINSULA',8,'MID','B1','P1','18:00','22:00'),
('2.0TD','PENINSULA',8,'MID','B1','P2','22:00','23:59'),
('2.0TD','PENINSULA',8,'MID','D','P3','00:00','23:59'),

-- Septiembre — MID, tipo B1
('2.0TD','PENINSULA',9,'MID','B1','P3','00:00','08:00'),
('2.0TD','PENINSULA',9,'MID','B1','P2','08:00','09:00'),
('2.0TD','PENINSULA',9,'MID','B1','P1','09:00','14:00'),
('2.0TD','PENINSULA',9,'MID','B1','P2','14:00','18:00'),
('2.0TD','PENINSULA',9,'MID','B1','P1','18:00','22:00'),
('2.0TD','PENINSULA',9,'MID','B1','P2','22:00','23:59'),
('2.0TD','PENINSULA',9,'MID','D','P3','00:00','23:59'),

-- Octubre — LOW, tipo C
('2.0TD','PENINSULA',10,'LOW','C','P3','00:00','08:00'),
('2.0TD','PENINSULA',10,'LOW','C','P2','08:00','09:00'),
('2.0TD','PENINSULA',10,'LOW','C','P1','09:00','14:00'),
('2.0TD','PENINSULA',10,'LOW','C','P2','14:00','18:00'),
('2.0TD','PENINSULA',10,'LOW','C','P1','18:00','22:00'),
('2.0TD','PENINSULA',10,'LOW','C','P2','22:00','23:59'),
('2.0TD','PENINSULA',10,'LOW','D','P3','00:00','23:59'),

-- Noviembre — MID_HIGH, tipo B
('2.0TD','PENINSULA',11,'MID_HIGH','B','P3','00:00','08:00'),
('2.0TD','PENINSULA',11,'MID_HIGH','B','P2','08:00','09:00'),
('2.0TD','PENINSULA',11,'MID_HIGH','B','P1','09:00','14:00'),
('2.0TD','PENINSULA',11,'MID_HIGH','B','P2','14:00','18:00'),
('2.0TD','PENINSULA',11,'MID_HIGH','B','P1','18:00','22:00'),
('2.0TD','PENINSULA',11,'MID_HIGH','B','P2','22:00','23:59'),
('2.0TD','PENINSULA',11,'MID_HIGH','D','P3','00:00','23:59'),

-- Diciembre — HIGH, tipo A
('2.0TD','PENINSULA',12,'HIGH','A','P3','00:00','08:00'),
('2.0TD','PENINSULA',12,'HIGH','A','P2','08:00','09:00'),
('2.0TD','PENINSULA',12,'HIGH','A','P1','09:00','14:00'),
('2.0TD','PENINSULA',12,'HIGH','A','P2','14:00','18:00'),
('2.0TD','PENINSULA',12,'HIGH','A','P1','18:00','22:00'),
('2.0TD','PENINSULA',12,'HIGH','A','P2','22:00','23:59'),
('2.0TD','PENINSULA',12,'HIGH','D','P3','00:00','23:59'),

-- ============================================================
-- 3.0TD | PENÍNSULA
-- Valle P6 (00-08), 6 periodos según temporada:
--   HIGH  (A)  → P6 / P2 / P1 / P2 / P1 / P2
--   MID_HIGH(B)→ P6 / P3 / P2 / P3 / P2 / P3
--   MID  (B1)  → P6 / P4 / P3 / P4 / P3 / P4
--   LOW  (C)   → P6 / P5 / P4 / P5 / P4 / P5
-- Tipo D: P6 todo el día con start=end='00:00' (convenio tabla).
-- ============================================================

-- Enero — HIGH, tipo A
('3.0TD','PENINSULA',1,'HIGH','A','P6','00:00','08:00'),
('3.0TD','PENINSULA',1,'HIGH','A','P2','08:00','09:00'),
('3.0TD','PENINSULA',1,'HIGH','A','P1','09:00','14:00'),
('3.0TD','PENINSULA',1,'HIGH','A','P2','14:00','18:00'),
('3.0TD','PENINSULA',1,'HIGH','A','P1','18:00','22:00'),
('3.0TD','PENINSULA',1,'HIGH','A','P2','22:00','23:59'),
('3.0TD','PENINSULA',1,'HIGH','D','P6','00:00','00:00'),

-- Febrero — HIGH, tipo A
('3.0TD','PENINSULA',2,'HIGH','A','P6','00:00','08:00'),
('3.0TD','PENINSULA',2,'HIGH','A','P2','08:00','09:00'),
('3.0TD','PENINSULA',2,'HIGH','A','P1','09:00','14:00'),
('3.0TD','PENINSULA',2,'HIGH','A','P2','14:00','18:00'),
('3.0TD','PENINSULA',2,'HIGH','A','P1','18:00','22:00'),
('3.0TD','PENINSULA',2,'HIGH','A','P2','22:00','23:59'),
('3.0TD','PENINSULA',2,'HIGH','D','P6','00:00','00:00'),

-- Marzo — MID_HIGH, tipo B
('3.0TD','PENINSULA',3,'MID_HIGH','B','P6','00:00','08:00'),
('3.0TD','PENINSULA',3,'MID_HIGH','B','P3','08:00','09:00'),
('3.0TD','PENINSULA',3,'MID_HIGH','B','P2','09:00','14:00'),
('3.0TD','PENINSULA',3,'MID_HIGH','B','P3','14:00','18:00'),
('3.0TD','PENINSULA',3,'MID_HIGH','B','P2','18:00','22:00'),
('3.0TD','PENINSULA',3,'MID_HIGH','B','P3','22:00','23:59'),
('3.0TD','PENINSULA',3,'MID_HIGH','D','P6','00:00','00:00'),

-- Abril — LOW, tipo C
('3.0TD','PENINSULA',4,'LOW','C','P6','00:00','08:00'),
('3.0TD','PENINSULA',4,'LOW','C','P5','08:00','09:00'),
('3.0TD','PENINSULA',4,'LOW','C','P4','09:00','14:00'),
('3.0TD','PENINSULA',4,'LOW','C','P5','14:00','18:00'),
('3.0TD','PENINSULA',4,'LOW','C','P4','18:00','22:00'),
('3.0TD','PENINSULA',4,'LOW','C','P5','22:00','23:59'),
('3.0TD','PENINSULA',4,'LOW','D','P6','00:00','00:00'),

-- Mayo — LOW, tipo C
('3.0TD','PENINSULA',5,'LOW','C','P6','00:00','08:00'),
('3.0TD','PENINSULA',5,'LOW','C','P5','08:00','09:00'),
('3.0TD','PENINSULA',5,'LOW','C','P4','09:00','14:00'),
('3.0TD','PENINSULA',5,'LOW','C','P5','14:00','18:00'),
('3.0TD','PENINSULA',5,'LOW','C','P4','18:00','22:00'),
('3.0TD','PENINSULA',5,'LOW','C','P5','22:00','23:59'),
('3.0TD','PENINSULA',5,'LOW','D','P6','00:00','00:00'),

-- Junio — MID, tipo B1
('3.0TD','PENINSULA',6,'MID','B1','P6','00:00','08:00'),
('3.0TD','PENINSULA',6,'MID','B1','P4','08:00','09:00'),
('3.0TD','PENINSULA',6,'MID','B1','P3','09:00','14:00'),
('3.0TD','PENINSULA',6,'MID','B1','P4','14:00','18:00'),
('3.0TD','PENINSULA',6,'MID','B1','P3','18:00','22:00'),
('3.0TD','PENINSULA',6,'MID','B1','P4','22:00','23:59'),
('3.0TD','PENINSULA',6,'MID','D','P6','00:00','00:00'),

-- Julio — HIGH, tipo A
('3.0TD','PENINSULA',7,'HIGH','A','P6','00:00','08:00'),
('3.0TD','PENINSULA',7,'HIGH','A','P2','08:00','09:00'),
('3.0TD','PENINSULA',7,'HIGH','A','P1','09:00','14:00'),
('3.0TD','PENINSULA',7,'HIGH','A','P2','14:00','18:00'),
('3.0TD','PENINSULA',7,'HIGH','A','P1','18:00','22:00'),
('3.0TD','PENINSULA',7,'HIGH','A','P2','22:00','23:59'),
('3.0TD','PENINSULA',7,'HIGH','D','P6','00:00','00:00'),

-- Agosto — MID, tipo B1
('3.0TD','PENINSULA',8,'MID','B1','P6','00:00','08:00'),
('3.0TD','PENINSULA',8,'MID','B1','P4','08:00','09:00'),
('3.0TD','PENINSULA',8,'MID','B1','P3','09:00','14:00'),
('3.0TD','PENINSULA',8,'MID','B1','P4','14:00','18:00'),
('3.0TD','PENINSULA',8,'MID','B1','P3','18:00','22:00'),
('3.0TD','PENINSULA',8,'MID','B1','P4','22:00','23:59'),
('3.0TD','PENINSULA',8,'MID','D','P6','00:00','00:00'),

-- Septiembre — MID, tipo B1
('3.0TD','PENINSULA',9,'MID','B1','P6','00:00','08:00'),
('3.0TD','PENINSULA',9,'MID','B1','P4','08:00','09:00'),
('3.0TD','PENINSULA',9,'MID','B1','P3','09:00','14:00'),
('3.0TD','PENINSULA',9,'MID','B1','P4','14:00','18:00'),
('3.0TD','PENINSULA',9,'MID','B1','P3','18:00','22:00'),
('3.0TD','PENINSULA',9,'MID','B1','P4','22:00','23:59'),
('3.0TD','PENINSULA',9,'MID','D','P6','00:00','00:00'),

-- Octubre — LOW, tipo C
('3.0TD','PENINSULA',10,'LOW','C','P6','00:00','08:00'),
('3.0TD','PENINSULA',10,'LOW','C','P5','08:00','09:00'),
('3.0TD','PENINSULA',10,'LOW','C','P4','09:00','14:00'),
('3.0TD','PENINSULA',10,'LOW','C','P5','14:00','18:00'),
('3.0TD','PENINSULA',10,'LOW','C','P4','18:00','22:00'),
('3.0TD','PENINSULA',10,'LOW','C','P5','22:00','23:59'),
('3.0TD','PENINSULA',10,'LOW','D','P6','00:00','00:00'),

-- Noviembre — MID_HIGH, tipo B
('3.0TD','PENINSULA',11,'MID_HIGH','B','P6','00:00','08:00'),
('3.0TD','PENINSULA',11,'MID_HIGH','B','P3','08:00','09:00'),
('3.0TD','PENINSULA',11,'MID_HIGH','B','P2','09:00','14:00'),
('3.0TD','PENINSULA',11,'MID_HIGH','B','P3','14:00','18:00'),
('3.0TD','PENINSULA',11,'MID_HIGH','B','P2','18:00','22:00'),
('3.0TD','PENINSULA',11,'MID_HIGH','B','P3','22:00','23:59'),
('3.0TD','PENINSULA',11,'MID_HIGH','D','P6','00:00','00:00'),

-- Diciembre — HIGH, tipo A
('3.0TD','PENINSULA',12,'HIGH','A','P6','00:00','08:00'),
('3.0TD','PENINSULA',12,'HIGH','A','P2','08:00','09:00'),
('3.0TD','PENINSULA',12,'HIGH','A','P1','09:00','14:00'),
('3.0TD','PENINSULA',12,'HIGH','A','P2','14:00','18:00'),
('3.0TD','PENINSULA',12,'HIGH','A','P1','18:00','22:00'),
('3.0TD','PENINSULA',12,'HIGH','A','P2','22:00','23:59'),
('3.0TD','PENINSULA',12,'HIGH','D','P6','00:00','00:00'),

-- ============================================================
-- 2.0TD | ISLAS_BALEARES
-- Bloques: 00-08 / 08-10 / 10-15 / 15-18 / 18-22 / 22-23:59
-- P1=punta (10-15 y 18-22), P2=llano (intermedios), P3=valle (00-08)
-- Temporadas Baleares: HIGH=jun-sep / MID_HIGH=may,oct / MID=ene,feb,dic / LOW=mar,abr,nov
-- ============================================================

-- Enero — MID, tipo B1
('2.0TD','ISLAS_BALEARES',1,'MID','B1','P3','00:00','08:00'),
('2.0TD','ISLAS_BALEARES',1,'MID','B1','P2','08:00','10:00'),
('2.0TD','ISLAS_BALEARES',1,'MID','B1','P1','10:00','15:00'),
('2.0TD','ISLAS_BALEARES',1,'MID','B1','P2','15:00','18:00'),
('2.0TD','ISLAS_BALEARES',1,'MID','B1','P1','18:00','22:00'),
('2.0TD','ISLAS_BALEARES',1,'MID','B1','P2','22:00','23:59'),
('2.0TD','ISLAS_BALEARES',1,'MID','D','P3','00:00','23:59'),

-- Febrero — MID, tipo B1
('2.0TD','ISLAS_BALEARES',2,'MID','B1','P3','00:00','08:00'),
('2.0TD','ISLAS_BALEARES',2,'MID','B1','P2','08:00','10:00'),
('2.0TD','ISLAS_BALEARES',2,'MID','B1','P1','10:00','15:00'),
('2.0TD','ISLAS_BALEARES',2,'MID','B1','P2','15:00','18:00'),
('2.0TD','ISLAS_BALEARES',2,'MID','B1','P1','18:00','22:00'),
('2.0TD','ISLAS_BALEARES',2,'MID','B1','P2','22:00','23:59'),
('2.0TD','ISLAS_BALEARES',2,'MID','D','P3','00:00','23:59'),

-- Marzo — LOW, tipo C
('2.0TD','ISLAS_BALEARES',3,'LOW','C','P3','00:00','08:00'),
('2.0TD','ISLAS_BALEARES',3,'LOW','C','P2','08:00','10:00'),
('2.0TD','ISLAS_BALEARES',3,'LOW','C','P1','10:00','15:00'),
('2.0TD','ISLAS_BALEARES',3,'LOW','C','P2','15:00','18:00'),
('2.0TD','ISLAS_BALEARES',3,'LOW','C','P1','18:00','22:00'),
('2.0TD','ISLAS_BALEARES',3,'LOW','C','P2','22:00','23:59'),
('2.0TD','ISLAS_BALEARES',3,'LOW','D','P3','00:00','23:59'),

-- Abril — LOW, tipo C
('2.0TD','ISLAS_BALEARES',4,'LOW','C','P3','00:00','08:00'),
('2.0TD','ISLAS_BALEARES',4,'LOW','C','P2','08:00','10:00'),
('2.0TD','ISLAS_BALEARES',4,'LOW','C','P1','10:00','15:00'),
('2.0TD','ISLAS_BALEARES',4,'LOW','C','P2','15:00','18:00'),
('2.0TD','ISLAS_BALEARES',4,'LOW','C','P1','18:00','22:00'),
('2.0TD','ISLAS_BALEARES',4,'LOW','C','P2','22:00','23:59'),
('2.0TD','ISLAS_BALEARES',4,'LOW','D','P3','00:00','23:59'),

-- Mayo — MID_HIGH, tipo B
('2.0TD','ISLAS_BALEARES',5,'MID_HIGH','B','P3','00:00','08:00'),
('2.0TD','ISLAS_BALEARES',5,'MID_HIGH','B','P2','08:00','10:00'),
('2.0TD','ISLAS_BALEARES',5,'MID_HIGH','B','P1','10:00','15:00'),
('2.0TD','ISLAS_BALEARES',5,'MID_HIGH','B','P2','15:00','18:00'),
('2.0TD','ISLAS_BALEARES',5,'MID_HIGH','B','P1','18:00','22:00'),
('2.0TD','ISLAS_BALEARES',5,'MID_HIGH','B','P2','22:00','23:59'),
('2.0TD','ISLAS_BALEARES',5,'MID_HIGH','D','P3','00:00','23:59'),

-- Junio — HIGH, tipo A
('2.0TD','ISLAS_BALEARES',6,'HIGH','A','P3','00:00','08:00'),
('2.0TD','ISLAS_BALEARES',6,'HIGH','A','P2','08:00','10:00'),
('2.0TD','ISLAS_BALEARES',6,'HIGH','A','P1','10:00','15:00'),
('2.0TD','ISLAS_BALEARES',6,'HIGH','A','P2','15:00','18:00'),
('2.0TD','ISLAS_BALEARES',6,'HIGH','A','P1','18:00','22:00'),
('2.0TD','ISLAS_BALEARES',6,'HIGH','A','P2','22:00','23:59'),
('2.0TD','ISLAS_BALEARES',6,'HIGH','D','P3','00:00','23:59'),

-- Julio — HIGH, tipo A
('2.0TD','ISLAS_BALEARES',7,'HIGH','A','P3','00:00','08:00'),
('2.0TD','ISLAS_BALEARES',7,'HIGH','A','P2','08:00','10:00'),
('2.0TD','ISLAS_BALEARES',7,'HIGH','A','P1','10:00','15:00'),
('2.0TD','ISLAS_BALEARES',7,'HIGH','A','P2','15:00','18:00'),
('2.0TD','ISLAS_BALEARES',7,'HIGH','A','P1','18:00','22:00'),
('2.0TD','ISLAS_BALEARES',7,'HIGH','A','P2','22:00','23:59'),
('2.0TD','ISLAS_BALEARES',7,'HIGH','D','P3','00:00','23:59'),

-- Agosto — HIGH, tipo A
('2.0TD','ISLAS_BALEARES',8,'HIGH','A','P3','00:00','08:00'),
('2.0TD','ISLAS_BALEARES',8,'HIGH','A','P2','08:00','10:00'),
('2.0TD','ISLAS_BALEARES',8,'HIGH','A','P1','10:00','15:00'),
('2.0TD','ISLAS_BALEARES',8,'HIGH','A','P2','15:00','18:00'),
('2.0TD','ISLAS_BALEARES',8,'HIGH','A','P1','18:00','22:00'),
('2.0TD','ISLAS_BALEARES',8,'HIGH','A','P2','22:00','23:59'),
('2.0TD','ISLAS_BALEARES',8,'HIGH','D','P3','00:00','23:59'),

-- Septiembre — HIGH, tipo A
('2.0TD','ISLAS_BALEARES',9,'HIGH','A','P3','00:00','08:00'),
('2.0TD','ISLAS_BALEARES',9,'HIGH','A','P2','08:00','10:00'),
('2.0TD','ISLAS_BALEARES',9,'HIGH','A','P1','10:00','15:00'),
('2.0TD','ISLAS_BALEARES',9,'HIGH','A','P2','15:00','18:00'),
('2.0TD','ISLAS_BALEARES',9,'HIGH','A','P1','18:00','22:00'),
('2.0TD','ISLAS_BALEARES',9,'HIGH','A','P2','22:00','23:59'),
('2.0TD','ISLAS_BALEARES',9,'HIGH','D','P3','00:00','23:59'),

-- Octubre — MID_HIGH, tipo B
('2.0TD','ISLAS_BALEARES',10,'MID_HIGH','B','P3','00:00','08:00'),
('2.0TD','ISLAS_BALEARES',10,'MID_HIGH','B','P2','08:00','10:00'),
('2.0TD','ISLAS_BALEARES',10,'MID_HIGH','B','P1','10:00','15:00'),
('2.0TD','ISLAS_BALEARES',10,'MID_HIGH','B','P2','15:00','18:00'),
('2.0TD','ISLAS_BALEARES',10,'MID_HIGH','B','P1','18:00','22:00'),
('2.0TD','ISLAS_BALEARES',10,'MID_HIGH','B','P2','22:00','23:59'),
('2.0TD','ISLAS_BALEARES',10,'MID_HIGH','D','P3','00:00','23:59'),

-- Noviembre — LOW, tipo C
('2.0TD','ISLAS_BALEARES',11,'LOW','C','P3','00:00','08:00'),
('2.0TD','ISLAS_BALEARES',11,'LOW','C','P2','08:00','10:00'),
('2.0TD','ISLAS_BALEARES',11,'LOW','C','P1','10:00','15:00'),
('2.0TD','ISLAS_BALEARES',11,'LOW','C','P2','15:00','18:00'),
('2.0TD','ISLAS_BALEARES',11,'LOW','C','P1','18:00','22:00'),
('2.0TD','ISLAS_BALEARES',11,'LOW','C','P2','22:00','23:59'),
('2.0TD','ISLAS_BALEARES',11,'LOW','D','P3','00:00','23:59'),

-- Diciembre — MID, tipo B1
('2.0TD','ISLAS_BALEARES',12,'MID','B1','P3','00:00','08:00'),
('2.0TD','ISLAS_BALEARES',12,'MID','B1','P2','08:00','10:00'),
('2.0TD','ISLAS_BALEARES',12,'MID','B1','P1','10:00','15:00'),
('2.0TD','ISLAS_BALEARES',12,'MID','B1','P2','15:00','18:00'),
('2.0TD','ISLAS_BALEARES',12,'MID','B1','P1','18:00','22:00'),
('2.0TD','ISLAS_BALEARES',12,'MID','B1','P2','22:00','23:59'),
('2.0TD','ISLAS_BALEARES',12,'MID','D','P3','00:00','23:59'),

-- ============================================================
-- 3.0TD | ISLAS_BALEARES
-- Bloques: 00-08 / 08-10 / 10-15 / 15-18 / 18-22 / 22-23:59
--   HIGH  (A)  → P6 / P2 / P1 / P2 / P1 / P2
--   MID_HIGH(B)→ P6 / P3 / P2 / P3 / P2 / P3
--   MID  (B1)  → P6 / P4 / P3 / P4 / P3 / P4
--   LOW  (C)   → P6 / P5 / P4 / P5 / P4 / P5
-- Tipo D: P6 todo el día con start=end='00:00'.
-- ============================================================

-- Enero — MID, tipo B1
('3.0TD','ISLAS_BALEARES',1,'MID','B1','P6','00:00','08:00'),
('3.0TD','ISLAS_BALEARES',1,'MID','B1','P4','08:00','10:00'),
('3.0TD','ISLAS_BALEARES',1,'MID','B1','P3','10:00','15:00'),
('3.0TD','ISLAS_BALEARES',1,'MID','B1','P4','15:00','18:00'),
('3.0TD','ISLAS_BALEARES',1,'MID','B1','P3','18:00','22:00'),
('3.0TD','ISLAS_BALEARES',1,'MID','B1','P4','22:00','23:59'),
('3.0TD','ISLAS_BALEARES',1,'MID','D','P6','00:00','00:00'),

-- Febrero — MID, tipo B1
('3.0TD','ISLAS_BALEARES',2,'MID','B1','P6','00:00','08:00'),
('3.0TD','ISLAS_BALEARES',2,'MID','B1','P4','08:00','10:00'),
('3.0TD','ISLAS_BALEARES',2,'MID','B1','P3','10:00','15:00'),
('3.0TD','ISLAS_BALEARES',2,'MID','B1','P4','15:00','18:00'),
('3.0TD','ISLAS_BALEARES',2,'MID','B1','P3','18:00','22:00'),
('3.0TD','ISLAS_BALEARES',2,'MID','B1','P4','22:00','23:59'),
('3.0TD','ISLAS_BALEARES',2,'MID','D','P6','00:00','00:00'),

-- Marzo — LOW, tipo C
('3.0TD','ISLAS_BALEARES',3,'LOW','C','P6','00:00','08:00'),
('3.0TD','ISLAS_BALEARES',3,'LOW','C','P5','08:00','10:00'),
('3.0TD','ISLAS_BALEARES',3,'LOW','C','P4','10:00','15:00'),
('3.0TD','ISLAS_BALEARES',3,'LOW','C','P5','15:00','18:00'),
('3.0TD','ISLAS_BALEARES',3,'LOW','C','P4','18:00','22:00'),
('3.0TD','ISLAS_BALEARES',3,'LOW','C','P5','22:00','23:59'),
('3.0TD','ISLAS_BALEARES',3,'LOW','D','P6','00:00','00:00'),

-- Abril — LOW, tipo C
('3.0TD','ISLAS_BALEARES',4,'LOW','C','P6','00:00','08:00'),
('3.0TD','ISLAS_BALEARES',4,'LOW','C','P5','08:00','10:00'),
('3.0TD','ISLAS_BALEARES',4,'LOW','C','P4','10:00','15:00'),
('3.0TD','ISLAS_BALEARES',4,'LOW','C','P5','15:00','18:00'),
('3.0TD','ISLAS_BALEARES',4,'LOW','C','P4','18:00','22:00'),
('3.0TD','ISLAS_BALEARES',4,'LOW','C','P5','22:00','23:59'),
('3.0TD','ISLAS_BALEARES',4,'LOW','D','P6','00:00','00:00'),

-- Mayo — MID_HIGH, tipo B
('3.0TD','ISLAS_BALEARES',5,'MID_HIGH','B','P6','00:00','08:00'),
('3.0TD','ISLAS_BALEARES',5,'MID_HIGH','B','P3','08:00','10:00'),
('3.0TD','ISLAS_BALEARES',5,'MID_HIGH','B','P2','10:00','15:00'),
('3.0TD','ISLAS_BALEARES',5,'MID_HIGH','B','P3','15:00','18:00'),
('3.0TD','ISLAS_BALEARES',5,'MID_HIGH','B','P2','18:00','22:00'),
('3.0TD','ISLAS_BALEARES',5,'MID_HIGH','B','P3','22:00','23:59'),
('3.0TD','ISLAS_BALEARES',5,'MID_HIGH','D','P6','00:00','00:00'),

-- Junio — HIGH, tipo A
('3.0TD','ISLAS_BALEARES',6,'HIGH','A','P6','00:00','08:00'),
('3.0TD','ISLAS_BALEARES',6,'HIGH','A','P2','08:00','10:00'),
('3.0TD','ISLAS_BALEARES',6,'HIGH','A','P1','10:00','15:00'),
('3.0TD','ISLAS_BALEARES',6,'HIGH','A','P2','15:00','18:00'),
('3.0TD','ISLAS_BALEARES',6,'HIGH','A','P1','18:00','22:00'),
('3.0TD','ISLAS_BALEARES',6,'HIGH','A','P2','22:00','23:59'),
('3.0TD','ISLAS_BALEARES',6,'HIGH','D','P6','00:00','00:00'),

-- Julio — HIGH, tipo A
('3.0TD','ISLAS_BALEARES',7,'HIGH','A','P6','00:00','08:00'),
('3.0TD','ISLAS_BALEARES',7,'HIGH','A','P2','08:00','10:00'),
('3.0TD','ISLAS_BALEARES',7,'HIGH','A','P1','10:00','15:00'),
('3.0TD','ISLAS_BALEARES',7,'HIGH','A','P2','15:00','18:00'),
('3.0TD','ISLAS_BALEARES',7,'HIGH','A','P1','18:00','22:00'),
('3.0TD','ISLAS_BALEARES',7,'HIGH','A','P2','22:00','23:59'),
('3.0TD','ISLAS_BALEARES',7,'HIGH','D','P6','00:00','00:00'),

-- Agosto — HIGH, tipo A
('3.0TD','ISLAS_BALEARES',8,'HIGH','A','P6','00:00','08:00'),
('3.0TD','ISLAS_BALEARES',8,'HIGH','A','P2','08:00','10:00'),
('3.0TD','ISLAS_BALEARES',8,'HIGH','A','P1','10:00','15:00'),
('3.0TD','ISLAS_BALEARES',8,'HIGH','A','P2','15:00','18:00'),
('3.0TD','ISLAS_BALEARES',8,'HIGH','A','P1','18:00','22:00'),
('3.0TD','ISLAS_BALEARES',8,'HIGH','A','P2','22:00','23:59'),
('3.0TD','ISLAS_BALEARES',8,'HIGH','D','P6','00:00','00:00'),

-- Septiembre — HIGH, tipo A
('3.0TD','ISLAS_BALEARES',9,'HIGH','A','P6','00:00','08:00'),
('3.0TD','ISLAS_BALEARES',9,'HIGH','A','P2','08:00','10:00'),
('3.0TD','ISLAS_BALEARES',9,'HIGH','A','P1','10:00','15:00'),
('3.0TD','ISLAS_BALEARES',9,'HIGH','A','P2','15:00','18:00'),
('3.0TD','ISLAS_BALEARES',9,'HIGH','A','P1','18:00','22:00'),
('3.0TD','ISLAS_BALEARES',9,'HIGH','A','P2','22:00','23:59'),
('3.0TD','ISLAS_BALEARES',9,'HIGH','D','P6','00:00','00:00'),

-- Octubre — MID_HIGH, tipo B
('3.0TD','ISLAS_BALEARES',10,'MID_HIGH','B','P6','00:00','08:00'),
('3.0TD','ISLAS_BALEARES',10,'MID_HIGH','B','P3','08:00','10:00'),
('3.0TD','ISLAS_BALEARES',10,'MID_HIGH','B','P2','10:00','15:00'),
('3.0TD','ISLAS_BALEARES',10,'MID_HIGH','B','P3','15:00','18:00'),
('3.0TD','ISLAS_BALEARES',10,'MID_HIGH','B','P2','18:00','22:00'),
('3.0TD','ISLAS_BALEARES',10,'MID_HIGH','B','P3','22:00','23:59'),
('3.0TD','ISLAS_BALEARES',10,'MID_HIGH','D','P6','00:00','00:00'),

-- Noviembre — LOW, tipo C
('3.0TD','ISLAS_BALEARES',11,'LOW','C','P6','00:00','08:00'),
('3.0TD','ISLAS_BALEARES',11,'LOW','C','P5','08:00','10:00'),
('3.0TD','ISLAS_BALEARES',11,'LOW','C','P4','10:00','15:00'),
('3.0TD','ISLAS_BALEARES',11,'LOW','C','P5','15:00','18:00'),
('3.0TD','ISLAS_BALEARES',11,'LOW','C','P4','18:00','22:00'),
('3.0TD','ISLAS_BALEARES',11,'LOW','C','P5','22:00','23:59'),
('3.0TD','ISLAS_BALEARES',11,'LOW','D','P6','00:00','00:00'),

-- Diciembre — MID, tipo B1
('3.0TD','ISLAS_BALEARES',12,'MID','B1','P6','00:00','08:00'),
('3.0TD','ISLAS_BALEARES',12,'MID','B1','P4','08:00','10:00'),
('3.0TD','ISLAS_BALEARES',12,'MID','B1','P3','10:00','15:00'),
('3.0TD','ISLAS_BALEARES',12,'MID','B1','P4','15:00','18:00'),
('3.0TD','ISLAS_BALEARES',12,'MID','B1','P3','18:00','22:00'),
('3.0TD','ISLAS_BALEARES',12,'MID','B1','P4','22:00','23:59'),
('3.0TD','ISLAS_BALEARES',12,'MID','D','P6','00:00','00:00')

ON CONFLICT (access_tariff_code, geographic_zone, month_number, day_type, start_time, end_time)
DO NOTHING;

COMMIT;
