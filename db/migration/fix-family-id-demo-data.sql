USE smart_nursing;
SET NAMES utf8mb4;

UPDATE elder_family_binding
SET family_id = 'family-001'
WHERE family_id = 'family_001';

UPDATE service_address
SET family_id = 'family-001'
WHERE family_id = 'family_001';
