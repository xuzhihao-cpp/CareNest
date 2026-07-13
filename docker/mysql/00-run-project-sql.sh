#!/bin/sh
set -eu

export MYSQL_PWD="${MYSQL_ROOT_PASSWORD}"

run_sql_directory() {
  directory="$1"
  for sql_file in "${directory}"/*.sql; do
    [ -f "${sql_file}" ] || continue
    echo "CareNest init: ${sql_file}"
    mysql --protocol=socket -uroot "${MYSQL_DATABASE}" < "${sql_file}"
  done
}

run_sql_directory /docker-entrypoint-initdb.d/schema
run_sql_directory /docker-entrypoint-initdb.d/seed
