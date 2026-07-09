USE smart_nursing;
SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS service_address (
  address_id VARCHAR(32) NOT NULL COMMENT '服务地址ID',
  elder_id VARCHAR(32) NOT NULL COMMENT '长辈ID',
  family_id VARCHAR(32) NOT NULL COMMENT '家属ID',
  contact_name VARCHAR(64) NOT NULL COMMENT '联系人姓名',
  contact_phone VARCHAR(32) NOT NULL COMMENT '联系人电话',
  province_code VARCHAR(32) NOT NULL COMMENT '省份编码',
  city_code VARCHAR(32) NOT NULL COMMENT '城市编码',
  region_code VARCHAR(32) NOT NULL COMMENT '区县编码',
  detail_address VARCHAR(255) NOT NULL COMMENT '详细地址',
  is_default TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否默认地址',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (address_id),
  KEY idx_service_address_elder (elder_id),
  KEY idx_service_address_family (family_id),
  KEY idx_service_address_default (elder_id, family_id, is_default),
  CONSTRAINT fk_service_address_elder FOREIGN KEY (elder_id) REFERENCES elder_profile (elder_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='服务地址';
