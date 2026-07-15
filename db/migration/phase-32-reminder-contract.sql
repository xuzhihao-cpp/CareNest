USE smart_nursing;
SET NAMES utf8mb4;

ALTER TABLE reminder_task
  ADD KEY idx_reminder_task_pending_due (reminder_status, reminder_at);
