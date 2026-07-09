package com.csu.carenest.user.auth;

import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Repository
public class DemoAuthRepository {

    private static final String DEMO_PASSWORD = "Demo@123456";

    private final Map<String, SysUser> usersById = new HashMap<>();
    private final Map<String, SysUser> usersByUsername = new HashMap<>();
    private final Map<String, List<RoleCode>> userRoles = new HashMap<>();
    private final Map<RoleCode, List<String>> roleMenus = new EnumMap<>(RoleCode.class);
    private final Map<RoleCode, List<String>> rolePermissions = new EnumMap<>(RoleCode.class);
    private final Map<String, LoginSession> sessionsByToken = new ConcurrentHashMap<>();
    private final List<OperationLog> operationLogs = new ArrayList<>();

    public DemoAuthRepository() {
        seedRolesAndMenus();
        seedRolePermissions();
        seedUser(new SysUser("elder-001", "elder_demo", DEMO_PASSWORD, "长辈演示账号"), RoleCode.ELDER);
        seedUser(new SysUser("family-001", "family_demo", DEMO_PASSWORD, "家属演示账号"), RoleCode.FAMILY);
        seedUser(new SysUser("nurse-001", "nurse_demo", DEMO_PASSWORD, "护理演示账号"), RoleCode.NURSE);
        seedUser(new SysUser("admin-001", "admin_demo", DEMO_PASSWORD, "管理员演示账号"), RoleCode.ADMIN);
        seedUser(new SysUser("cs-001", "cs_demo", DEMO_PASSWORD, "客服演示账号"), RoleCode.CUSTOMER_SERVICE);
    }

    public Optional<SysUser> findUserByUsername(String username) {
        return Optional.ofNullable(usersByUsername.get(username));
    }

    public Optional<SysUser> findUserById(String userId) {
        return Optional.ofNullable(usersById.get(userId));
    }

    public List<RoleCode> findRolesByUserId(String userId) {
        return userRoles.getOrDefault(userId, List.of());
    }

    public List<String> findMenusByRoles(List<RoleCode> roles) {
        List<String> menus = new ArrayList<>();
        for (RoleCode role : roles) {
            menus.addAll(roleMenus.getOrDefault(role, List.of()));
        }
        return List.copyOf(menus);
    }

    public List<String> findPermissionsByRoles(List<RoleCode> roles) {
        List<String> permissions = new ArrayList<>();
        for (RoleCode role : roles) {
            permissions.addAll(rolePermissions.getOrDefault(role, List.of()));
        }
        return List.copyOf(permissions);
    }

    public List<String> findPermissionsByRole(RoleCode roleCode) {
        return rolePermissions.getOrDefault(roleCode, List.of());
    }

    public void replaceRolePermissions(RoleCode roleCode, List<String> permissionCodes) {
        rolePermissions.put(roleCode, List.copyOf(permissionCodes));
    }

    public void saveOperationLog(OperationLog operationLog) {
        operationLogs.add(operationLog);
    }

    public void saveSession(LoginSession session) {
        sessionsByToken.put(session.token(), session);
    }

    public Optional<LoginSession> findSession(String token) {
        return Optional.ofNullable(sessionsByToken.get(token));
    }

    public void deleteSession(String token) {
        sessionsByToken.remove(token);
    }

    private void seedUser(SysUser user, RoleCode roleCode) {
        usersById.put(user.userId(), user);
        usersByUsername.put(user.username(), user);
        userRoles.put(user.userId(), List.of(roleCode));
    }

    private void seedRolesAndMenus() {
        roleMenus.put(RoleCode.ELDER, List.of("/elder/home", "/elder/reminders", "/elder/ai"));
        roleMenus.put(RoleCode.FAMILY, List.of("/family/home", "/family/elders", "/family/orders"));
        roleMenus.put(RoleCode.NURSE, List.of("/nurse/home", "/nurse/orders", "/nurse/reports"));
        roleMenus.put(RoleCode.ADMIN, List.of("/admin/home", "/admin/users", "/admin/dashboard"));
        roleMenus.put(RoleCode.CUSTOMER_SERVICE, List.of("/customer-service/home", "/customer-service/tickets"));
    }

    private void seedRolePermissions() {
        rolePermissions.put(RoleCode.ELDER, List.of("ELDER_REMINDER_VIEW", "ELDER_AI_CHAT"));
        rolePermissions.put(RoleCode.FAMILY, List.of("FAMILY_ELDER_VIEW", "FAMILY_ORDER_CREATE"));
        rolePermissions.put(RoleCode.NURSE, List.of("NURSE_ORDER_VIEW", "NURSE_REPORT_CREATE"));
        rolePermissions.put(RoleCode.ADMIN, List.of("ADMIN_DASHBOARD_VIEW", "ROLE_PERMISSION_MANAGE"));
        rolePermissions.put(RoleCode.CUSTOMER_SERVICE, List.of("CUSTOMER_SERVICE_TICKET_HANDLE", "ROLE_PERMISSION_MANAGE"));
    }
}
