package com.csu.carenest.careadmin.status;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Connection;
import java.time.OffsetDateTime;
import java.time.ZoneId;

@Service
public class CareAdminStatusService {

    private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");

    private final DataSource dataSource;
    private final String version;

    public CareAdminStatusService(
            DataSource dataSource,
            @Value("${carenest.app.version:0.1.0}") String version) {
        this.dataSource = dataSource;
        this.version = version;
    }

    public CareAdminHealthResponse health() {
        boolean dbConnected;
        try (Connection connection = dataSource.getConnection()) {
            dbConnected = connection.isValid(2);
        } catch (Exception ignored) {
            dbConnected = false;
        }
        return new CareAdminHealthResponse(
                dbConnected ? "UP" : "DOWN",
                dbConnected,
                "CareNest Care Admin",
                version,
                dbConnected,
                OffsetDateTime.now(SHANGHAI));
    }
}
