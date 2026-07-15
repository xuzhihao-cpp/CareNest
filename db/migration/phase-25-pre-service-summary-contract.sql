USE smart_nursing;
SET NAMES utf8mb4;

-- Normalize the legacy Phase 20 value to the frozen frontend/API contract.
UPDATE medical_file
SET file_type = 'EXAMINATION_REPORT'
WHERE file_type = 'CHECK_REPORT';

-- Historical local demo volumes used this bucket name before Docker standardized it.
-- Scope the repair to bundled demo objects so user-uploaded bucket assignments remain authoritative.
UPDATE file_asset
SET storage_bucket = 'smart-nursing'
WHERE storage_bucket = 'carenest-medical'
  AND object_key LIKE 'demo/%';
