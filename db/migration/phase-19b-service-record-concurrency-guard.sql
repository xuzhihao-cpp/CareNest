USE smart_nursing;
SET NAMES utf8mb4;

DELIMITER $$

DROP PROCEDURE IF EXISTS add_service_record_order_guard$$
CREATE PROCEDURE add_service_record_order_guard()
BEGIN
  DECLARE duplicate_orders INT DEFAULT 0;
  DECLARE guard_exists INT DEFAULT 0;
  DECLARE legacy_index_exists INT DEFAULT 0;

  SELECT COUNT(*) INTO duplicate_orders
  FROM (
    SELECT order_id
    FROM care_service_record
    GROUP BY order_id
    HAVING COUNT(*) > 1
  ) duplicate_rows;

  IF duplicate_orders > 0 THEN
    SIGNAL SQLSTATE '45000'
      SET MESSAGE_TEXT = 'Duplicate care_service_record rows must be resolved before phase 19-B migration';
  END IF;

  SELECT COUNT(*) INTO guard_exists
  FROM information_schema.statistics
  WHERE table_schema = DATABASE()
    AND table_name = 'care_service_record'
    AND index_name = 'uk_care_service_record_order';

  IF guard_exists = 0 THEN
    ALTER TABLE care_service_record
      ADD UNIQUE KEY uk_care_service_record_order (order_id);
  END IF;

  SELECT COUNT(*) INTO legacy_index_exists
  FROM information_schema.statistics
  WHERE table_schema = DATABASE()
    AND table_name = 'care_service_record'
    AND index_name = 'idx_care_service_record_order';

  IF legacy_index_exists > 0 THEN
    ALTER TABLE care_service_record DROP INDEX idx_care_service_record_order;
  END IF;
END$$

CALL add_service_record_order_guard()$$
DROP PROCEDURE add_service_record_order_guard$$

DELIMITER ;
