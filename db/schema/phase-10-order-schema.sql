USE smart_nursing;
SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS nursing_order (
  order_id VARCHAR(32) NOT NULL COMMENT '订单ID',
  elder_id VARCHAR(32) NOT NULL COMMENT '长辈ID',
  family_id VARCHAR(32) NOT NULL COMMENT '家属ID',
  service_id VARCHAR(32) NOT NULL COMMENT '服务项目ID',
  address_id VARCHAR(32) NOT NULL COMMENT '服务地址ID',
  order_status VARCHAR(32) NOT NULL COMMENT '订单状态',
  scheduled_start_at DATETIME NOT NULL COMMENT '预约开始时间',
  scheduled_end_at DATETIME DEFAULT NULL COMMENT '预约结束时间',
  service_price_cent INT NOT NULL DEFAULT 0 COMMENT '服务价格，单位分',
  contact_name VARCHAR(64) NOT NULL COMMENT '联系人姓名',
  contact_phone VARCHAR(32) NOT NULL COMMENT '联系人电话',
  remark VARCHAR(255) DEFAULT NULL COMMENT '备注',
  created_by VARCHAR(32) DEFAULT NULL COMMENT '创建人用户ID',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (order_id),
  KEY idx_nursing_order_elder (elder_id),
  KEY idx_nursing_order_family (family_id),
  KEY idx_nursing_order_service (service_id),
  KEY idx_nursing_order_status (order_status),
  KEY idx_nursing_order_scheduled_start (scheduled_start_at),
  CONSTRAINT fk_nursing_order_elder FOREIGN KEY (elder_id) REFERENCES elder_profile (elder_id),
  CONSTRAINT fk_nursing_order_service FOREIGN KEY (service_id) REFERENCES service_item (service_id),
  CONSTRAINT fk_nursing_order_address FOREIGN KEY (address_id) REFERENCES service_address (address_id),
  CONSTRAINT ck_nursing_order_status CHECK (order_status IN (
    'WAIT_DISPATCH','DISPATCHED','ACCEPTED','ON_THE_WAY','SERVING',
    'WAIT_REPORT','WAIT_CONFIRM','COMPLETED','CANCELED'
  ))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='护理订单';

CREATE TABLE IF NOT EXISTS order_status_log (
  status_log_id VARCHAR(32) NOT NULL COMMENT '订单状态日志ID',
  order_id VARCHAR(32) NOT NULL COMMENT '订单ID',
  from_status VARCHAR(32) DEFAULT NULL COMMENT '变更前状态',
  to_status VARCHAR(32) NOT NULL COMMENT '变更后状态',
  changed_by VARCHAR(32) DEFAULT NULL COMMENT '变更人用户ID',
  change_reason VARCHAR(255) DEFAULT NULL COMMENT '变更原因',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (status_log_id),
  KEY idx_order_status_log_order (order_id),
  KEY idx_order_status_log_to_status (to_status),
  CONSTRAINT fk_order_status_log_order FOREIGN KEY (order_id) REFERENCES nursing_order (order_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='订单状态日志';
