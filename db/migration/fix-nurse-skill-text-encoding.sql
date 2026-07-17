USE smart_nursing;
SET NAMES utf8mb4;

-- Repair the historical UTF-8/GBK mojibake for "基础护理" without relying on
-- a terminal's active code page.
SET @bad_skill_name = CONVERT(UNHEX('C3A5C5B8C2BAC3A7C2A1E282ACC3A6C5A0C2A4C3A7C290E280A0') USING utf8mb4);
SET @correct_skill_name = CONVERT(UNHEX('E59FBAE7A180E68AA4E79086') USING utf8mb4);

UPDATE nurse_service_skill
SET skill_name = @correct_skill_name
WHERE skill_name = @bad_skill_name;

UPDATE nurse_recommendation_log
SET recommend_reason = REPLACE(recommend_reason, @bad_skill_name, @correct_skill_name)
WHERE recommend_reason LIKE CONCAT('%', @bad_skill_name, '%');

UPDATE nursing_order
SET preferred_nurse_reason = REPLACE(preferred_nurse_reason, @bad_skill_name, @correct_skill_name)
WHERE preferred_nurse_reason LIKE CONCAT('%', @bad_skill_name, '%');
