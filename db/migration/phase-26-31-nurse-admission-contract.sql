USE smart_nursing;
SET NAMES utf8mb4;

-- The phase 26-31 schema file is written as an idempotent baseline:
-- it creates missing tables, adds missing nursing_order preference columns,
-- recreates database triggers, and can be rerun on an existing Docker database.
--
-- In the project Docker container, db/schema is mounted at this path.
SOURCE /docker-entrypoint-initdb.d/schema/phase-26-31-nurse-admission-schema.sql;
