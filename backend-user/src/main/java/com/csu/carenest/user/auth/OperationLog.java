package com.csu.carenest.user.auth;

import java.util.List;

public record OperationLog(
        String operatorId,
        String action,
        String targetId,
        List<String> permissionCodes
) {
}
