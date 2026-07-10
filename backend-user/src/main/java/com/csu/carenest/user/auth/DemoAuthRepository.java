package com.csu.carenest.user.auth;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.csu.carenest.user.common.NotFoundException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Repository
public class DemoAuthRepository {

    private final SysUserMapper sysUserMapper;
    private final SysRoleMapper sysRoleMapper;
    private final UserRoleMapper userRoleMapper;
    private final SysPermissionMapper sysPermissionMapper;
    private final RolePermissionMapper rolePermissionMapper;
    private final LoginSessionMapper loginSessionMapper;
    private final OperationLogMapper operationLogMapper;

    public DemoAuthRepository(
            SysUserMapper sysUserMapper,
            SysRoleMapper sysRoleMapper,
            UserRoleMapper userRoleMapper,
            SysPermissionMapper sysPermissionMapper,
            RolePermissionMapper rolePermissionMapper,
            LoginSessionMapper loginSessionMapper,
            OperationLogMapper operationLogMapper) {
        this.sysUserMapper = sysUserMapper;
        this.sysRoleMapper = sysRoleMapper;
        this.userRoleMapper = userRoleMapper;
        this.sysPermissionMapper = sysPermissionMapper;
        this.rolePermissionMapper = rolePermissionMapper;
        this.loginSessionMapper = loginSessionMapper;
        this.operationLogMapper = operationLogMapper;
    }

    public Optional<SysUser> findUserByUsername(String username) {
        return Optional.ofNullable(sysUserMapper.selectOne(Wrappers.<SysUser>lambdaQuery()
                .eq(SysUser::getUsername, username)
                .eq(SysUser::getAccountStatus, "ENABLED")));
    }

    public Optional<SysUser> findUserById(String userId) {
        return Optional.ofNullable(sysUserMapper.selectOne(Wrappers.<SysUser>lambdaQuery()
                .eq(SysUser::getUserId, userId)
                .eq(SysUser::getAccountStatus, "ENABLED")));
    }

    public List<RoleCode> findRolesByUserId(String userId) {
        List<String> roleIds = userRoleMapper.selectList(Wrappers.<UserRole>lambdaQuery()
                        .eq(UserRole::getUserId, userId))
                .stream()
                .map(UserRole::getRoleId)
                .toList();
        if (roleIds.isEmpty()) {
            return List.of();
        }
        return sysRoleMapper.selectList(Wrappers.<SysRole>lambdaQuery()
                        .in(SysRole::getRoleId, roleIds)
                        .eq(SysRole::getEnabled, true))
                .stream()
                .map(SysRole::getRoleCode)
                .map(RoleCode::valueOf)
                .toList();
    }

    public List<String> findMenusByRoles(List<RoleCode> roles) {
        List<String> menus = new ArrayList<>();
        for (RoleCode role : roles) {
            menus.addAll(menusForRole(role));
        }
        return List.copyOf(menus);
    }

    public List<String> findPermissionsByRoles(List<RoleCode> roles) {
        Set<String> permissions = new LinkedHashSet<>();
        for (RoleCode role : roles) {
            permissions.addAll(findPermissionsByRole(role));
        }
        return List.copyOf(permissions);
    }

    public List<String> findPermissionsByRole(RoleCode roleCode) {
        SysRole role = findRoleByCode(roleCode).orElseThrow(NotFoundException::new);
        List<RolePermission> rolePermissions = rolePermissionMapper.selectList(Wrappers.<RolePermission>lambdaQuery()
                .eq(RolePermission::getRoleId, role.getRoleId())
                .orderByAsc(RolePermission::getSort));
        List<String> permissionCodes = new ArrayList<>();
        for (RolePermission rolePermission : rolePermissions) {
            SysPermission permission = sysPermissionMapper.selectById(rolePermission.getPermissionId());
            if (permission != null && Boolean.TRUE.equals(permission.getEnabled())) {
                permissionCodes.add(permission.getPermissionCode());
            }
        }
        return List.copyOf(permissionCodes);
    }

    @Transactional
    public void replaceRolePermissions(RoleCode roleCode, List<String> permissionCodes) {
        SysRole role = findRoleByCode(roleCode).orElseThrow(NotFoundException::new);
        rolePermissionMapper.delete(Wrappers.<RolePermission>lambdaQuery()
                .eq(RolePermission::getRoleId, role.getRoleId()));
        for (int index = 0; index < permissionCodes.size(); index++) {
            SysPermission permission = findPermissionByCode(permissionCodes.get(index))
                    .orElseThrow(NotFoundException::new);
            RolePermission rolePermission = new RolePermission();
            rolePermission.setRoleId(role.getRoleId());
            rolePermission.setPermissionId(permission.getPermissionId());
            rolePermission.setSort(index + 1);
            rolePermissionMapper.insert(rolePermission);
        }
    }

    public void saveOperationLog(OperationLog operationLog) {
        operationLogMapper.insert(operationLog);
    }

    public void saveSession(LoginSession session) {
        loginSessionMapper.insert(session);
    }

    public Optional<LoginSession> findSession(String sessionId) {
        return Optional.ofNullable(loginSessionMapper.selectOne(Wrappers.<LoginSession>lambdaQuery()
                .eq(LoginSession::getSessionId, sessionId)
                .isNull(LoginSession::getRevokedAt)
                .gt(LoginSession::getExpireAt, LocalDateTime.now())));
    }

    public void deleteSession(String sessionId) {
        LoginSession session = new LoginSession();
        session.setSessionId(sessionId);
        session.setRevokedAt(LocalDateTime.now());
        loginSessionMapper.updateById(session);
    }

    private Optional<SysRole> findRoleByCode(RoleCode roleCode) {
        return Optional.ofNullable(sysRoleMapper.selectOne(Wrappers.<SysRole>lambdaQuery()
                .eq(SysRole::getRoleCode, roleCode.name())
                .eq(SysRole::getEnabled, true)));
    }

    private Optional<SysPermission> findPermissionByCode(String permissionCode) {
        return Optional.ofNullable(sysPermissionMapper.selectOne(Wrappers.<SysPermission>lambdaQuery()
                .eq(SysPermission::getPermissionCode, permissionCode)
                .eq(SysPermission::getEnabled, true)));
    }

    private List<String> menusForRole(RoleCode role) {
        return switch (role) {
            case ELDER -> List.of("/elder/home", "/elder/reminders", "/elder/ai");
            case FAMILY -> List.of("/family/home", "/family/elders", "/family/orders");
            case NURSE -> List.of("/nurse/home", "/nurse/orders", "/nurse/reports");
            case ADMIN -> List.of("/admin/home", "/admin/users", "/admin/dashboard");
            case CUSTOMER_SERVICE -> List.of("/customer-service/home", "/customer-service/tickets");
        };
    }
}
