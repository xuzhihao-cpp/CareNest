package com.csu.carenest.user.auth;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record PermissionRequest(
        @NotEmpty List<String> permissionCodes
) {
}
