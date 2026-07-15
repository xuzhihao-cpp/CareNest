USE smart_nursing;
SET NAMES utf8mb4;

INSERT INTO reminder_task
  (reminder_id, elder_id, source_type, source_id, title, content, reminder_at, reminder_status, created_by)
SELECT 'phase32_reminder_001', 'elder_001', 'MEDICATION_PLAN', 'medication_001',
       '早晨用药', '请按照照护计划服用今日早晨药物。', DATE_ADD(NOW(), INTERVAL 20 MINUTE), 'PENDING', 'system'
WHERE EXISTS (SELECT 1 FROM elder_profile WHERE elder_id = 'elder_001')
  AND NOT EXISTS (SELECT 1 FROM reminder_task WHERE reminder_id = 'phase32_reminder_001');

INSERT INTO reminder_task
  (reminder_id, elder_id, source_type, source_id, title, content, reminder_at, reminder_status, created_by)
SELECT 'phase32_reminder_002', 'elder_001', 'CARE_PLAN', 'care_plan_001',
       '护理准备', '护理员上门前请准备好常用药品和近期检查资料。', DATE_ADD(NOW(), INTERVAL 2 HOUR), 'PENDING', 'system'
WHERE EXISTS (SELECT 1 FROM elder_profile WHERE elder_id = 'elder_001')
  AND NOT EXISTS (SELECT 1 FROM reminder_task WHERE reminder_id = 'phase32_reminder_002');
