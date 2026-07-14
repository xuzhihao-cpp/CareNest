USE smart_nursing;
SET NAMES utf8mb4;

SET @has_tag_code := (
  SELECT COUNT(*)
  FROM information_schema.columns
  WHERE table_schema = DATABASE()
    AND table_name = 'risk_tag'
    AND column_name = 'tag_code'
);
SET @sql := IF(
  @has_tag_code = 0,
  'ALTER TABLE risk_tag ADD COLUMN tag_code VARCHAR(64) NULL COMMENT ''风险标签稳定代码'' AFTER elder_id',
  'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

UPDATE risk_tag
SET tag_code = CASE tag_name
  WHEN '跌倒风险' THEN 'FALL_RISK'
  WHEN '压疮风险' THEN 'PRESSURE_INJURY_RISK'
  WHEN '吞咽风险' THEN 'SWALLOWING_RISK'
  WHEN '用药风险' THEN 'MEDICATION_RISK'
  WHEN '走失风险' THEN 'WANDERING_RISK'
  WHEN '过敏风险' THEN 'ALLERGY_RISK'
  ELSE CONCAT('CUSTOM_', UPPER(REPLACE(risk_tag_id, '-', '_')))
END
WHERE tag_code IS NULL OR tag_code = '';

ALTER TABLE risk_tag MODIFY COLUMN tag_code VARCHAR(64) NOT NULL COMMENT '风险标签稳定代码';

SET @has_disease_unique := (
  SELECT COUNT(*) FROM information_schema.statistics
  WHERE table_schema = DATABASE() AND table_name = 'chronic_disease'
    AND index_name = 'uk_chronic_disease_elder_name'
);
SET @sql := IF(@has_disease_unique = 0,
  'ALTER TABLE chronic_disease ADD UNIQUE KEY uk_chronic_disease_elder_name (elder_id, disease_name)',
  'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has_medication_unique := (
  SELECT COUNT(*) FROM information_schema.statistics
  WHERE table_schema = DATABASE() AND table_name = 'medication_plan'
    AND index_name = 'uk_medication_plan_elder_name'
);
SET @sql := IF(@has_medication_unique = 0,
  'ALTER TABLE medication_plan ADD UNIQUE KEY uk_medication_plan_elder_name (elder_id, medication_name)',
  'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has_allergy_unique := (
  SELECT COUNT(*) FROM information_schema.statistics
  WHERE table_schema = DATABASE() AND table_name = 'allergy_record'
    AND index_name = 'uk_allergy_record_elder_name'
);
SET @sql := IF(@has_allergy_unique = 0,
  'ALTER TABLE allergy_record ADD UNIQUE KEY uk_allergy_record_elder_name (elder_id, allergen)',
  'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has_risk_unique := (
  SELECT COUNT(*) FROM information_schema.statistics
  WHERE table_schema = DATABASE() AND table_name = 'risk_tag'
    AND index_name = 'uk_risk_tag_elder_code'
);
SET @sql := IF(@has_risk_unique = 0,
  'ALTER TABLE risk_tag ADD UNIQUE KEY uk_risk_tag_elder_code (elder_id, tag_code)',
  'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has_care_plan_unique := (
  SELECT COUNT(*) FROM information_schema.statistics
  WHERE table_schema = DATABASE() AND table_name = 'care_plan'
    AND index_name = 'uk_care_plan_elder'
);
SET @sql := IF(@has_care_plan_unique = 0,
  'ALTER TABLE care_plan ADD UNIQUE KEY uk_care_plan_elder (elder_id)',
  'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

UPDATE medication_plan
SET frequency = CASE frequency
  WHEN '每日一次' THEN 'ONCE_DAILY'
  WHEN '每日两次' THEN 'TWICE_DAILY'
  WHEN '每日三次' THEN 'THREE_TIMES_DAILY'
  WHEN '隔日一次' THEN 'EVERY_OTHER_DAY'
  WHEN '每周一次' THEN 'WEEKLY'
  WHEN '按需使用' THEN 'AS_NEEDED'
  ELSE frequency
END;

UPDATE allergy_record
SET severity = CASE severity
  WHEN 'HIGH' THEN 'SEVERE'
  WHEN 'MEDIUM' THEN 'MODERATE'
  WHEN 'LOW' THEN 'MILD'
  ELSE severity
END;
