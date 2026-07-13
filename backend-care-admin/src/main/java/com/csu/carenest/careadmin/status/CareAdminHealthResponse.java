package com.csu.carenest.careadmin.status;

import java.time.OffsetDateTime;

public record CareAdminHealthResponse(
        String status,
        String appName,
        String version,
        boolean dbConnected,
        OffsetDateTime serverTime
) {
}
