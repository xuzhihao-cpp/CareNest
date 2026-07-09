USE smart_nursing;
SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS service_item (
  service_id VARCHAR(32) NOT NULL COMMENT '服务项目ID',
  service_name VARCHAR(128) NOT NULL COMMENT '服务项目名称',
  service_desc VARCHAR(512) DEFAULT NULL COMMENT '服务说明',
  price_cent INT NOT NULL DEFAULT 0 COMMENT '价格，单位分',
  duration_minutes INT NOT NULL DEFAULT 0 COMMENT '服务时长，单位分钟',
  service_status VARCHAR(32) NOT NULL DEFAULT 'ON_SHELF' COMMENT '服务状态',
  sort INT NOT NULL DEFAULT 1 COMMENT '排序',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (service_id),
  KEY idx_service_item_status (service_status),
  CONSTRAINT ck_service_item_status CHECK (service_status IN ('ON_SHELF','OFF_SHELF'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='服务项目';
