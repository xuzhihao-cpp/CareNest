package com.csu.carenest.user.auth;

import com.csu.carenest.user.common.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public ApiResponse<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.success(authService.login(request));
    }

    @PostMapping("/logout")
    public ApiResponse<AuthResponse> logout(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        return ApiResponse.success(authService.logout(authorization));
    }

    @GetMapping("/me")
    public ApiResponse<AuthResponse> me(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        return ApiResponse.success(authService.currentUser(authorization));
    }

    @GetMapping("/menus")
    public ApiResponse<AuthResponse> menus(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        return ApiResponse.success(authService.currentUser(authorization));
    }

    @GetMapping("/permissions")
    public ApiResponse<PermissionResponse> permissions(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        return ApiResponse.success(authService.currentPermissions(authorization));
    }
}
