package com.csu.carenest.user.auth;

import com.csu.carenest.user.common.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/roles")
public class AdminRolePermissionController {

    private final AuthService authService;

    public AdminRolePermissionController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/{roleId}/permissions")
    public ApiResponse<PermissionResponse> updatePermissions(
            @PathVariable("roleId") String roleId,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @Valid @RequestBody PermissionRequest request) {
        return ApiResponse.success(authService.updateRolePermissions(authorization, roleId, request));
    }
}
