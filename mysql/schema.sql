SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci;
SET character_set_client = utf8mb4;
SET character_set_connection = utf8mb4;
SET character_set_results = utf8mb4;

-- ---------------------------------------------------------------------
-- 综合管廊（地下城市管廊）· 廊体舱段 / 入廊管线 / 巡检点 / 附属设备
-- 库名 utility_tunnel；application.yml 里 ddl-auto: none，表结构全部由本文件在 MySQL 镜像构建期导入。
-- 注意：mysql 容器是 UTC，种子数据里的时间一律写 UTC_TIMESTAMP() + INTERVAL 8 HOUR。

CREATE DATABASE IF NOT EXISTS utility_tunnel
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_unicode_ci;

USE utility_tunnel;

SET FOREIGN_KEY_CHECKS = 0;
DROP TABLE IF EXISTS facility;
DROP TABLE IF EXISTS patrol_point;
DROP TABLE IF EXISTS pipeline;
DROP TABLE IF EXISTS tunnel_segment;
SET FOREIGN_KEY_CHECKS = 1;

-- ---------------- 廊体舱段 ----------------
-- shape ∈ {圆形, 矩形, 马蹄形}；status ∈ {运营, 维护, 停用}
CREATE TABLE tunnel_segment (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    seg_code VARCHAR(32) NOT NULL UNIQUE,
    seg_name VARCHAR(64),
    shape VARCHAR(16),
    length_m DECIMAL(10,2) NOT NULL,
    status VARCHAR(16) NOT NULL,
    created_at DATETIME
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------------- 入廊管线 ----------------
-- line_type ∈ {电力, 通信, 给水, 排水, 热力, 燃气}；pressure_level ∈ {高压, 中压, 低压}；status ∈ {在用, 检修}
CREATE TABLE pipeline (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    segment_id BIGINT NOT NULL,
    line_type VARCHAR(16) NOT NULL,
    diameter_mm INT,
    pressure_level VARCHAR(16),
    status VARCHAR(16) NOT NULL,
    created_at DATETIME,
    KEY idx_pipeline_segment (segment_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------------- 巡检点 ----------------
CREATE TABLE patrol_point (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    segment_id BIGINT NOT NULL,
    point_code VARCHAR(32) NOT NULL,
    location VARCHAR(128),
    last_patrol_at DATETIME,
    created_at DATETIME,
    KEY idx_patrol_segment (segment_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------------- 附属设备 ----------------
-- facility_type ∈ {风机, 排水泵, 照明, 监控}；status ∈ {正常, 故障}
CREATE TABLE facility (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    segment_id BIGINT NOT NULL,
    facility_type VARCHAR(16) NOT NULL,
    status VARCHAR(16) NOT NULL,
    created_at DATETIME,
    KEY idx_facility_segment (segment_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- =====================================================================
-- 种子数据（14 行，覆盖全部四个模块；演示「电力 + 燃气 防爆隔离」规则场景）
-- =====================================================================

INSERT INTO tunnel_segment (id, seg_code, seg_name, shape, length_m, status, created_at) VALUES
(1, 'SEG-01', '中央综合舱·北段', '矩形', 1200.00, '运营', UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 90 DAY),
(2, 'SEG-02', '中央综合舱·南段', '圆形',  980.00,  '运营', UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 88 DAY),
(3, 'SEG-03', '电力舱',          '矩形',  760.00,  '维护', UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 80 DAY),
(4, 'SEG-04', '燃气舱',          '马蹄形', 540.00,  '停用', UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 60 DAY);

-- SEG-01 含电力管线（用于校验：同舱禁止再敷设燃气）；SEG-04 含燃气但无电力（允许）
INSERT INTO pipeline (id, segment_id, line_type, diameter_mm, pressure_level, status, created_at) VALUES
(1, 1, '电力', 300, '高压', '在用', UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 70 DAY),
(2, 1, '通信', 100, '低压', '在用', UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 68 DAY),
(3, 2, '排水', 500, '低压', '在用', UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 66 DAY),
(4, 4, '燃气', 200, '中压', '在用', UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 55 DAY);

INSERT INTO patrol_point (id, segment_id, point_code, location, last_patrol_at, created_at) VALUES
(1, 1, 'P-01', '桩号 K0+100', UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 2 DAY, UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 70 DAY),
(2, 2, 'P-02', '桩号 K1+200', UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 5 DAY, UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 66 DAY),
(3, 3, 'P-03', '桩号 K2+000', UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 9 DAY, UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 80 DAY);

INSERT INTO facility (id, segment_id, facility_type, status, created_at) VALUES
(1, 1, '风机',   '正常', UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 70 DAY),
(2, 1, '排水泵', '故障', UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 70 DAY),
(3, 4, '监控',   '故障', UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 55 DAY);
