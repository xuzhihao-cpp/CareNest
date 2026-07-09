package com.csu.carenest.user.auth;

import com.csu.carenest.user.common.UnauthorizedException;
import com.csu.carenest.user.common.ForbiddenException;
import com.csu.carenest.user.common.NotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class AuthService {

    private static final String BEARER_PREFIX = "Bearer ";

    private final DemoAuthRepository authRepository;

    public AuthService(DemoAuthRepository authRepository) {
        this.authRepository = authRepository;
    }

    public AuthResponse login(LoginRequest request) {
        SysUser user = authRepository.findUserByUsername(request.username())
                .filter(candidate -> candidate.password().equals(request.password()))
                .orElseThrow(UnauthorizedException::new);
        String token = "cn-" + UUID.randomUUID();
        authRepository.saveSession(new LoginSession(token, user.userId()));
        return responseFor(token, user);
    }

    public AuthResponse currentUser(String authorization) {
        AuthenticatedSession session = authenticate(authorization);
        return responseFor(session.token(), session.user());
    }

    public AuthResponse logout(String authorization) {
        AuthenticatedSession session = authenticate(authorization);
        authRepository.deleteSession(session.token());
        return responseFor(session.token(), session.user());
    }

    public PermissionResponse currentPermissions(String authorization) {
        AuthenticatedSession session = authenticate(authorization);
        List<RoleCode> roles = authRepository.findRolesByUserId(session.user().userId());
        RoleCode primaryRole = primaryRole(roles);
        return new PermissionResponse(primaryRole.name(), authRepository.findPermissionsByRoles(roles));
    }

    public PermissionResponse updateRolePermissions(String authorization, String roleId, PermissionRequest request) {
        AuthenticatedSession session = authenticate(authorization);
        List<RoleCode> operatorRoles = authRepository.findRolesByUserId(session.user().userId());
        if (!canManageRolePermissions(operatorRoles)) {
            throw new ForbiddenException();
        }
        RoleCode targetRole = parseRoleId(roleId);
        authRepository.replaceRolePermissions(targetRole, request.permissionCodes());
        authRepository.saveOperationLog(new OperationLog(
                session.user().userId(),
                "UPDATE_ROLE_PERMISSIONS",
                targetRole.name(),
                request.permissionCodes()
        ));
        return new PermissionResponse(targetRole.name(), authRepository.findPermissionsByRole(targetRole));
    }

    AuthenticatedSession authenticate(String authorization) {
        if (authorization == null || !authorization.startsWith(BEARER_PREFIX)) {
            throw new UnauthorizedException();
        }
        String token = authorization.substring(BEARER_PREFIX.length()).trim();
        if (token.isEmpty()) {
            throw new UnauthorizedException();
        }
        LoginSession session = authRepository.findSession(token)
                .orElseThrow(UnauthorizedException::new);
        SysUser user = authRepository.findUserById(session.userId())
                .orElseThrow(UnauthorizedException::new);
        return new AuthenticatedSession(token, user);
    }

    private boolean canManageRolePermissions(List<RoleCode> roles) {
        boolean allowedRole = roles.stream()
                .anyMatch(role -> role == RoleCode.ADMIN || role == RoleCode.CUSTOMER_SERVICE);
        return allowedRole && authRepository.findPermissionsByRoles(roles).contains("ROLE_PERMISSION_MANAGE");
    }

    private RoleCode parseRoleId(String roleId) {
        try {
            return RoleCode.valueOf(roleId.toUpperCase());
        } catch (IllegalArgumentException exception) {
            throw new NotFoundException();
        }
    }

    private RoleCode primaryRole(List<RoleCode> roles) {
        if (roles.isEmpty()) {
            throw new ForbiddenException();
        }
        return roles.get(0);
    }

    private AuthResponse responseFor(String token, SysUser user) {
        List<RoleCode> roleCodes = authRepository.findRolesByUserId(user.userId());
        List<String> roles = roleCodes.stream()
                .map(RoleCode::name)
                .toList();
        List<String> menus = authRepository.findMenusByRoles(roleCodes);
        return new AuthResponse(token, user.userId(), user.displayName(), roles, menus);
    }

    record AuthenticatedSession(String token, SysUser user) {
    }
}
