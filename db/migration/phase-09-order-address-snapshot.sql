USE smart_nursing;
SET NAMES utf8mb4;

ALTER TABLE nursing_order
  ADD COLUMN service_address_snapshot VARCHAR(300) NULL COMMENT '下单时的服务地址快照' AFTER address_id;

UPDATE nursing_order o
JOIN service_address a ON a.address_id = o.address_id
SET o.service_address_snapshot = CONCAT(a.region_code, ' ', a.detail_address)
WHERE o.service_address_snapshot IS NULL OR o.service_address_snapshot = '';

ALTER TABLE nursing_order DROP FOREIGN KEY fk_nursing_order_address;

ALTER TABLE nursing_order
  MODIFY address_id VARCHAR(32) NULL COMMENT '下单时的地址来源ID，可在地址删除后置空',
  MODIFY service_address_snapshot VARCHAR(300) NOT NULL COMMENT '下单时的服务地址快照';

ALTER TABLE nursing_order
  ADD CONSTRAINT fk_nursing_order_address
    FOREIGN KEY (address_id) REFERENCES service_address (address_id) ON DELETE SET NULL;
