USE smart_nursing;
SET NAMES utf8mb4;

-- The phase 32-55 schema file is idempotent and can be rerun on an existing
-- Docker database. In the project Docker container, db/schema is mounted here.
SOURCE /docker-entrypoint-initdb.d/schema/phase-32-55-complete-schema.sql;
