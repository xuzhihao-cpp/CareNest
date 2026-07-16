# syntax=docker/dockerfile:1.7
FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /workspace

COPY .mvn/ .mvn/
COPY pom.xml ./
COPY backend-user/pom.xml backend-user/pom.xml
COPY backend-care-admin/pom.xml backend-care-admin/pom.xml
RUN --mount=type=cache,id=carenest-maven,target=/root/.m2 \
    mvn -B -ntp -pl backend-care-admin -am dependency:go-offline

COPY backend-care-admin/src backend-care-admin/src
RUN --mount=type=cache,id=carenest-maven,target=/root/.m2 \
    mvn -B -ntp -pl backend-care-admin -am -DskipTests package

FROM eclipse-temurin:17-jre-jammy AS runtime
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system --gid 10001 carenest \
    && useradd --system --uid 10001 --gid carenest --home-dir /app --shell /usr/sbin/nologin carenest
WORKDIR /app
COPY --from=build --chown=carenest:carenest /workspace/backend-care-admin/target/backend-care-admin-*.jar /app/app.jar
COPY --chown=carenest:carenest db/seed /app/db/seed
USER carenest
EXPOSE 8082
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75.0 -Dfile.encoding=UTF-8 -Duser.timezone=Asia/Shanghai"
HEALTHCHECK --interval=10s --timeout=5s --start-period=30s --retries=12 \
  CMD curl -fsS http://127.0.0.1:8082/api/v1/health | grep -q '"dbConnected":true' || exit 1
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
