package com.csu.carenest.user.auth;

import java.util.List;

public record PermissionResponse(
        String roleCode,
        List<String> permissions
) {
}
