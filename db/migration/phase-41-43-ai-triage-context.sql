USE smart_nursing;
SET NAMES utf8mb4;

-- Explicit migration for existing data volumes. Do not replay this file during
-- empty-database initialization; the consolidated schema contains the columns.
ALTER TABLE ai_assistant_session
  ADD COLUMN IF NOT EXISTS triage_level VARCHAR(32) DEFAULT NULL,
  ADD COLUMN IF NOT EXISTS triage_category VARCHAR(64) DEFAULT NULL,
  ADD COLUMN IF NOT EXISTS triage_question VARCHAR(500) DEFAULT NULL,
  ADD COLUMN IF NOT EXISTS triage_context VARCHAR(1000) DEFAULT NULL,
  ADD COLUMN IF NOT EXISTS triage_fingerprint VARCHAR(128) DEFAULT NULL,
  ADD COLUMN IF NOT EXISTS triage_awaiting_answer TINYINT(1) NOT NULL DEFAULT 0;

CREATE INDEX idx_ai_session_triage
  ON ai_assistant_session (triage_awaiting_answer, triage_level);
