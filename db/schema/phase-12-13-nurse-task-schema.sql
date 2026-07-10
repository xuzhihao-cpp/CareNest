USE smart_nursing;
SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS nurse_task (
  task_id VARCHAR(32) NOT NULL COMMENT '护理任务ID',
  order_id VARCHAR(32) NOT NULL COMMENT '订单ID',
  nurse_id VARCHAR(32) NOT NULL COMMENT '护理人员用户ID',
  task_status VARCHAR(32) NOT NULL COMMENT '任务状态',
  dispatch_remark VARCHAR(255) DEFAULT NULL COMMENT '派单备注',
  accepted_at DATETIME DEFAULT NULL COMMENT '接单时间',
  started_at DATETIME DEFAULT NULL COMMENT '开始服务时间',
  completed_at DATETIME DEFAULT NULL COMMENT '完成服务时间',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (task_id),
  UNIQUE KEY uk_nurse_task_order (order_id),
  KEY idx_nurse_task_nurse_status (nurse_id, task_status),
  KEY idx_nurse_task_order (order_id),
  CONSTRAINT fk_nurse_task_order FOREIGN KEY (order_id) REFERENCES nursing_order (order_id),
  CONSTRAINT fk_nurse_task_nurse FOREIGN KEY (nurse_id) REFERENCES sys_user (user_id),
  CONSTRAINT ck_nurse_task_status CHECK (task_status IN (
    'DISPATCHED','ACCEPTED','ON_THE_WAY','SERVING','COMPLETED','CANCELED'
  ))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='护理任务';
