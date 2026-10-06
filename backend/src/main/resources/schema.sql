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
DROP TABLE IF EXISTS risk_event;
DROP TABLE IF EXISTS facility;
DROP TABLE IF EXISTS patrol_point;
DROP TABLE IF EXISTS pipeline;
DROP TABLE IF EXISTS tunnel_segment;
SET FOREIGN_KEY_CHECKS = 1;

-- ---------------- 廊体舱段 ----------------
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
CREATE TABLE facility (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    segment_id BIGINT NOT NULL,
    facility_type VARCHAR(16) NOT NULL,
    status VARCHAR(16) NOT NULL,
    created_at DATETIME,
    KEY idx_facility_segment (segment_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------------- 巡检异常 / 风险事件 ----------------
-- severity ∈ {一般, 重要, 紧急}；status ∈ {待处置, 处理中, 已解除}；source_type ∈ {PATROL, FACILITY}
-- status 非「已解除」即视为未解除风险：该舱段在最后一条未解除事件解除前不得从「维护/停用」恢复「运营」
CREATE TABLE risk_event (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    segment_id BIGINT NOT NULL,
    source_type VARCHAR(16) NOT NULL,
    source_id BIGINT NOT NULL,
    severity VARCHAR(16) NOT NULL,
    description VARCHAR(512) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT '待处置',
    handle_note VARCHAR(512),
    found_at DATETIME NOT NULL,
    resolver VARCHAR(32),
    resolved_at DATETIME,
    created_at DATETIME,
    KEY idx_risk_segment (segment_id),
    KEY idx_risk_status (status),
    KEY idx_risk_source (source_type, source_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- =====================================================================
-- 种子数据（18 行，覆盖全部五个模块；演示「电力 + 燃气 防爆隔离」规则场景）
-- 风险事件场景：SEG-03 同时挂 2 条未解除事件（一条可先解除、舱段仍封控）；SEG-04 事件已解除但舱段仍停用待人工恢复
-- =====================================================================

INSERT INTO tunnel_segment (id, seg_code, seg_name, shape, length_m, status, created_at) VALUES
(1, 'SEG-01', '中央综合舱·北段', '矩形', 1200.00, '运营', UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 90 DAY),
(2, 'SEG-02', '中央综合舱·南段', '圆形',  980.00,  '运营', UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 88 DAY),
(3, 'SEG-03', '电力舱',          '矩形',  760.00,  '维护', UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 80 DAY),
(4, 'SEG-04', '燃气舱',          '马蹄形', 540.00,  '停用', UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 60 DAY);

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
(3, 4, '监控',   '故障', UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 55 DAY),
(4, 3, '照明',   '故障', UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 10 DAY);

INSERT INTO risk_event
    (id, segment_id, source_type, source_id, severity, description, status, handle_note, found_at, resolver, resolved_at, created_at)
VALUES
(1, 3, 'PATROL',   3, '重要', 'K2+000 巡检点发现舱壁渗漏水迹，疑似伸缩缝密封老化',
    '待处置', NULL, UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 3 DAY,
    NULL, NULL, UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 3 DAY),
(2, 3, 'FACILITY', 4, '紧急', '照明回路跳闸，K1+800~K2+100 段照度不足，夜间巡检受阻',
    '处理中', '已联系机电班更换漏电保护开关，备件到货后复测',
    UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 1 DAY,
    NULL, NULL, UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 1 DAY),
(3, 4, 'FACILITY', 3, '一般', '燃气舱监控摄像头离线，重启后恢复在线',
    '已解除', '现场重启设备并核对信号链路，连续观察 24 小时无异常',
    UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 6 DAY,
    '周明远', UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 5 DAY,
    UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 6 DAY);
