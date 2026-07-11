USE smart_nursing;
SET NAMES utf8mb4;

ALTER TABLE elder_family_binding
  ADD COLUMN pending_scope_codes JSON NULL COMMENT '待长辈确认的授权范围',
  ADD COLUMN scope_update_status VARCHAR(32) NULL COMMENT '授权变更状态';
