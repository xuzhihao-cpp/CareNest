package com.csu.carenest.user.auth;

import com.csu.carenest.user.common.ForbiddenException;
import com.csu.carenest.user.common.NotFoundException;
import com.csu.carenest.user.common.UnauthorizedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AuthService {

    private static final String BEARER_PREFIX = "Bearer ";

    private final DemoAuthRepository authRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    public AuthService(
            DemoAuthRepository authRepository,
            PasswordEncoder passwordEncoder,
            JwtTokenProvider jwtTokenProvider) {
        this.authRepository = authRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenProvider = jwtTokenProvider;
    }

    public AuthResponse login(LoginRequest request) {
        SysUser user = authRepository.findUserByUsername(request.username())
                .filter(candidate -> passwordEncoder.matches(request.password(), candidate.getPasswordHash()))
                .orElseThrow(UnauthorizedException::new);
        JwtTokenProvider.IssuedToken issuedToken = jwtTokenProvider.issue(user.getUserId());
        LoginSession session = new LoginSession();
        session.setSessionId(issuedToken.sessionId());
        session.setUserId(user.getUserId());
        session.setTokenHash(jwtTokenProvider.tokenHash(issuedToken.token()));
        session.setExpireAt(issuedToken.expireAt());
        authRepository.saveSession(session);
        return responseFor(issuedToken.token(), user);
    }

    public AuthResponse currentUser(String authorization) {
        AuthenticatedSession session = authenticate(authorization);
        return responseFor(session.token(), session.user());
    }

    public AuthResponse logout(String authorization) {
        AuthenticatedSession session = authenticate(authorization);
        authRepository.deleteSession(session.session().getSessionId());
        return responseFor(session.token(), session.user());
    }

    public PermissionResponse currentPermissions(String authorization) {
        AuthenticatedSession session = authenticate(authorization);
        List<RoleCode> roles = authRepository.findRolesByUserId(session.user().getUserId());
        RoleCode primaryRole = primaryRole(roles);
        return new PermissionResponse(primaryRole.name(), authRepository.findPermissionsByRoles(roles));
    }

    public PermissionResponse updateRolePermissions(String authorization, String roleId, PermissionRequest request) {
        AuthenticatedSession session = authenticate(authorization);
        List<RoleCode> operatorRoles = authRepository.findRolesByUserId(session.user().getUserId());
        if (!canManageRolePermissions(operatorRoles)) {
            throw new ForbiddenException();
        }
        RoleCode targetRole = parseRoleId(roleId);
        authRepository.replaceRolePermissions(targetRole, request.permissionCodes());
        authRepository.saveOperationLog(new OperationLog(
                session.user().getUserId(),
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
        JwtTokenProvider.ParsedToken parsedToken = parseToken(token);
        LoginSession session = authRepository.findSession(parsedToken.sessionId())
                .filter(candidate -> jwtTokenProvider.tokenHash(token).equals(candidate.getTokenHash()))
                .filter(candidate -> parsedToken.userId().equals(candidate.getUserId()))
                .orElseThrow(UnauthorizedException::new);
        SysUser user = authRepository.findUserById(session.getUserId())
                .orElseThrow(UnauthorizedException::new);
        return new AuthenticatedSession(token, user, session);
    }

    private JwtTokenProvider.ParsedToken parseToken(String token) {
        try {
            return jwtTokenProvider.parse(token);
        } catch (JwtTokenProvider.InvalidTokenException exception) {
            throw new UnauthorizedException();
        }
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
        List<RoleCode> roleCodes = authRepository.findRolesByUserId(user.getUserId());
        List<String> roles = roleCodes.stream()
                .map(RoleCode::name)
                .toList();
        List<String> menus = authRepository.findMenusByRoles(roleCodes);
        return new AuthResponse(token, user.getUserId(), user.getDisplayName(), roles, menus);
    }

    record AuthenticatedSession(String token, SysUser user, LoginSession session) {
    }
}
