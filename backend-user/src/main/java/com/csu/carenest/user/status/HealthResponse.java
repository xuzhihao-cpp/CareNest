package com.csu.carenest.user.status;

import java.time.OffsetDateTime;

public record HealthResponse(
        String status,
        String appName,
        String version,
        boolean ready,
        boolean dbConnected,
        OffsetDateTime serverTime
) {
}
