package com.csu.carenest.careadmin.auth;

import com.csu.carenest.careadmin.common.ForbiddenException;
import com.csu.carenest.careadmin.common.UnauthorizedException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 统一鉴权服务：解析 Bearer Token，校验 login_session，并读取当前用户角色。
 */
@Service
public class AuthService {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JdbcTemplate jdbcTemplate;
    private final JwtTokenProvider jwtTokenProvider;

    public AuthService(JdbcTemplate jdbcTemplate, JwtTokenProvider jwtTokenProvider) {
        this.jdbcTemplate = jdbcTemplate;
        this.jwtTokenProvider = jwtTokenProvider;
    }

    public CurrentUser requireCurrentUser(String authorization) {
        // 所有受保护接口都必须携带 Authorization: Bearer <token>。
        if (authorization == null || !authorization.startsWith(BEARER_PREFIX)) {
            throw new UnauthorizedException();
        }
        String token = authorization.substring(BEARER_PREFIX.length()).trim();
        if (token.isEmpty()) {
            throw new UnauthorizedException();
        }
        JwtTokenProvider.ParsedToken parsedToken;
        try {
            parsedToken = jwtTokenProvider.parse(token);
        } catch (JwtTokenProvider.InvalidTokenException exception) {
            throw new UnauthorizedException();
        }
        Integer activeSessionCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM login_session
                WHERE session_id = ?
                  AND user_id = ?
                  AND token_hash = ?
                  AND expire_at > CURRENT_TIMESTAMP
                """, Integer.class, parsedToken.sessionId(), parsedToken.userId(), jwtTokenProvider.tokenHash(token));
        if (activeSessionCount == null || activeSessionCount == 0) {
            throw new UnauthorizedException();
        }
        List<RoleCode> roles = jdbcTemplate.query("""
                SELECT r.role_code
                FROM user_role ur
                JOIN sys_role r ON r.role_id = ur.role_id
                WHERE ur.user_id = ?
                  AND r.enabled = 1
                ORDER BY r.role_id
                """, (rs, rowNum) -> RoleCode.valueOf(rs.getString("role_code")), parsedToken.userId());
        if (roles.isEmpty()) {
            throw new ForbiddenException();
        }
        return new CurrentUser(parsedToken.userId(), roles);
    }

    public CurrentUser requireRole(String authorization, RoleCode roleCode) {
        // 单角色接口使用，例如家属下单、家属取消订单。
        CurrentUser currentUser = requireCurrentUser(authorization);
        if (!currentUser.hasRole(roleCode)) {
            throw new ForbiddenException();
        }
        return currentUser;
    }

    public CurrentUser requireAnyRole(String authorization, RoleCode... roleCodes) {
        // 多角色接口使用，例如 ADMIN 和 CUSTOMER_SERVICE 共享管理端权限。
        CurrentUser currentUser = requireCurrentUser(authorization);
        for (RoleCode roleCode : roleCodes) {
            if (currentUser.hasRole(roleCode)) {
                return currentUser;
            }
        }
        throw new ForbiddenException();
    }

    public void requirePermission(CurrentUser currentUser, String permissionCode) {
        Integer permissionCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM user_role ur
                JOIN sys_role r ON r.role_id = ur.role_id AND r.enabled = 1
                JOIN role_permission rp ON rp.role_id = r.role_id
                JOIN sys_permission p ON p.permission_id = rp.permission_id AND p.enabled = 1
                WHERE ur.user_id = ? AND p.permission_code = ?
                """, Integer.class, currentUser.userId(), permissionCode);
        if (permissionCount == null || permissionCount == 0) {
            throw new ForbiddenException();
        }
    }
}
