package com.csu.carenest.user.common;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.csu.carenest.user.auth.SysUser;
import com.csu.carenest.user.auth.SysUserMapper;
import com.csu.carenest.user.auth.UserRole;
import com.csu.carenest.user.auth.UserRoleMapper;
import com.csu.carenest.user.flow.HealthArchiveChangeLog;
import com.csu.carenest.user.flow.HealthArchiveChangeLogMapper;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/dev")
public class DevController {

    private static final String DEMO_HASH = "{bcrypt}$2b$10$CTEqZgvVGIL8gCprN4wywO4ricUhhHWQwWUY6iiX2pLZXEB2S13au";

    private final SysUserMapper sysUserMapper;
    private final UserRoleMapper userRoleMapper;
    private final HealthArchiveChangeLogMapper healthArchiveChangeLogMapper;

    public DevController(SysUserMapper sysUserMapper,
                         UserRoleMapper userRoleMapper,
                         HealthArchiveChangeLogMapper healthArchiveChangeLogMapper) {
        this.sysUserMapper = sysUserMapper;
        this.userRoleMapper = userRoleMapper;
        this.healthArchiveChangeLogMapper = healthArchiveChangeLogMapper;
    }

    @GetMapping("/ping")
    public ApiResponse<String> ping() {
        return ApiResponse.success("pong");
    }

    @GetMapping("/change-logs/{elderId}")
    public ApiResponse<List<Map<String, String>>> changeLogs(@PathVariable String elderId) {
        try {
            var logs = healthArchiveChangeLogMapper.selectList(
                    Wrappers.<HealthArchiveChangeLog>lambdaQuery()
                            .eq(HealthArchiveChangeLog::getElderId, elderId));
            List<Map<String, String>> result = new ArrayList<>();
            for (var log : logs) {
                Map<String, String> entry = new HashMap<>();
                entry.put("changeLogId", nvl(log.getChangeLogId()));
                entry.put("changedBy", nvl(log.getChangedBy()));
                entry.put("changeType", nvl(log.getChangeType()));
                String before = log.getBeforeValue();
                String after = log.getAfterValue();
                entry.put("beforePreview", before != null && before.length() > 80 ? before.substring(0, 80) + "..." : nvl(before));
                entry.put("afterPreview", after != null && after.length() > 80 ? after.substring(0, 80) + "..." : nvl(after));
                result.add(entry);
            }
            return ApiResponse.success(result);
        } catch (Exception e) {
            return ApiResponse.error(500, e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    @PostMapping("/create-family-user")
    public ApiResponse<Map<String, String>> createFamilyUser(@RequestBody CreateUserRequest request) {
        String username = request.username() != null ? request.username() : "family_zhang";
        String displayName = request.displayName() != null ? request.displayName() : "test";
        String phone = request.phone() != null ? request.phone() : "13800000000";

        try {
            SysUser existing = sysUserMapper.selectOne(
                    Wrappers.<SysUser>lambdaQuery().eq(SysUser::getUsername, username));
            if (existing != null) {
                return ApiResponse.success(Map.of(
                        "userId", existing.getUserId(),
                        "username", existing.getUsername(),
                        "displayName", existing.getDisplayName(),
                        "status", "EXISTING"
                ));
            }

            String userId = "family-" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
            SysUser user = new SysUser();
            user.setUserId(userId);
            user.setUsername(username);
            user.setPasswordHash(DEMO_HASH);
            user.setDisplayName(displayName);
            user.setPhone(phone);
            user.setAccountStatus("ENABLED");
            sysUserMapper.insert(user);

            UserRole userRole = new UserRole();
            userRole.setUserId(userId);
            userRole.setRoleId("role_family");
            userRoleMapper.insert(userRole);

            return ApiResponse.success(Map.of(
                    "userId", userId,
                    "username", username,
                    "displayName", displayName,
                    "status", "CREATED"
            ));
        } catch (Exception e) {
            return ApiResponse.error(500, e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    private static String nvl(String s) {
        return s != null ? s : "";
    }
}
