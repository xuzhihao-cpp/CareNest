package com.csu.carenest.user.reminder;

import com.csu.carenest.user.auth.AuthService;
import com.csu.carenest.user.auth.RoleCode;
import com.csu.carenest.user.common.ApiException;
import com.csu.carenest.user.redis.HomeCacheInvalidator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Set;

@Service
public class ReminderService {
    private static final Set<String> STATUSES = Set.of("PENDING", "DONE", "SNOOZED", "MISSED", "NEED_HELP");
    private static final Set<String> ACTIONS = Set.of("DONE", "SNOOZE", "NEED_HELP");
    private final AuthService auth;
    private final ReminderRepository repository;
    private final HomeCacheInvalidator cache;

    public ReminderService(AuthService auth, ReminderRepository repository, HomeCacheInvalidator cache) {
        this.auth = auth; this.repository = repository; this.cache = cache;
    }

    public ReminderDtos.PageResult<ReminderDtos.Item> list(String authorization, String status, int page, int size) {
        String elderId = elder(authorization);
        requirePermission(authorization, "REMINDER_VIEW", "ELDER_REMINDER_VIEW");
        validatePage(page, size);
        if (status != null && !STATUSES.contains(status)) throw new ApiException(422, "提醒状态不合法");
        return new ReminderDtos.PageResult<>(repository.list(elderId, status, size, (page - 1) * size).stream()
                .map(this::item).toList(), repository.count(elderId, status), page, size);
    }

    @Transactional
    public ReminderDtos.ActionResult act(String authorization, String reminderId, ReminderDtos.ActionRequest request) {
        AuthService.CurrentUser user = auth.requireCurrentUser(authorization);
        requireElder(user);
        requirePermission(authorization, "REMINDER_UPDATE", "ELDER_REMINDER_VIEW");
        String elderId = repository.elderIdForUser(user.userId()).orElseThrow(() -> new ApiException(404, "未找到当前长辈档案"));
        if (request == null || request.action() == null || !ACTIONS.contains(request.action())) throw new ApiException(422, "提醒操作不合法");
        ReminderRepository.ReminderRow current = repository.find(elderId, reminderId).orElseThrow(() -> new ApiException(404, "提醒不存在"));
        String target = switch (request.action()) {
            case "DONE" -> "DONE";
            case "NEED_HELP" -> "NEED_HELP";
            case "SNOOZE" -> "SNOOZED";
            default -> throw new ApiException(422, "提醒操作不合法");
        };
        if (!Set.of("PENDING", "SNOOZED", "MISSED", "NEED_HELP").contains(current.status())) throw new ApiException(409, "当前提醒状态不能执行该操作");
        if ("SNOOZE".equals(request.action()) && (request.snoozeMinutes() == null || request.snoozeMinutes() < 5 || request.snoozeMinutes() > 24 * 60)) {
            throw new ApiException(422, "稍后提醒时间须为5分钟至24小时");
        }
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime snoozedUntil = "SNOOZE".equals(request.action()) ? now.plusMinutes(request.snoozeMinutes()) : null;
        ReminderRepository.ReminderRow updated = repository.updateStatus(elderId, reminderId, current.status(), target,
                snoozedUntil, "DONE".equals(target) ? now : null, "NEED_HELP".equals(target) ? now : null);
        String recordId = repository.insertRecord(reminderId, elderId, current.status(), target, request.action(), request.snoozeMinutes(), user.userId(), request.note());
        repository.insertOperationLog(user.userId(), reminderId, current.status(), target);
        cache.evictAfterCommit(RoleCode.ELDER.name(), user.userId());
        return new ReminderDtos.ActionResult(item(updated), new ReminderDtos.RecordItem(updated.reminderId(), updated.title(), request.action(), current.status(), target, request.note(), now));
    }

    public ReminderDtos.PageResult<ReminderDtos.RecordItem> records(String authorization, String requestedElderId, int page, int size) {
        AuthService.CurrentUser user = auth.requireCurrentUser(authorization);
        String elderId = repository.elderIdForUser(user.userId()).orElse(null);
        if (user.roles().contains(RoleCode.ELDER)) {
            requirePermission(authorization, "REMINDER_RECORD_VIEW", "ELDER_REMINDER_VIEW");
            if (elderId == null) throw new ApiException(404, "未找到当前长辈档案");
        } else if (user.roles().contains(RoleCode.FAMILY)) {
            requirePermission(authorization, "REMINDER_RECORD_VIEW");
            elderId = requestedElderId;
            if (elderId == null || !repository.hasActiveBinding(user.userId(), elderId)) throw new ApiException(403, "当前家属无权查看该提醒记录");
        } else if (user.roles().contains(RoleCode.ADMIN) || user.roles().contains(RoleCode.CUSTOMER_SERVICE)) {
            requirePermission(authorization, "REMINDER_RECORD_VIEW");
            if (requestedElderId == null || requestedElderId.isBlank()) throw new ApiException(422, "查询提醒记录必须指定长辈");
            elderId = requestedElderId;
        } else {
            throw new ApiException(403, "当前角色无权查看提醒记录");
        }
        validatePage(page, size);
        return new ReminderDtos.PageResult<>(repository.records(elderId, size, (page - 1) * size).stream().map(this::record).toList(), repository.recordCount(elderId), page, size);
    }

    private String elder(String authorization) {
        AuthService.CurrentUser user = auth.requireCurrentUser(authorization); requireElder(user);
        return repository.elderIdForUser(user.userId()).orElseThrow(() -> new ApiException(404, "未找到当前长辈档案"));
    }
    private void requireElder(AuthService.CurrentUser user) { if (!user.roles().contains(RoleCode.ELDER)) throw new ApiException(403, "仅长辈本人可操作提醒"); }
    private void requirePermission(String authorization, String... accepted) {
        var permissions = auth.currentPermissions(authorization).permissions();
        for (String permission : accepted) if (permissions.contains(permission)) return;
        throw new ApiException(403, "当前账号无权执行提醒操作");
    }
    private void validatePage(int page, int size) { if (page < 1 || size < 1 || size > 50) throw new ApiException(422, "分页参数不合法"); }
    private ReminderDtos.Item item(ReminderRepository.ReminderRow r) { return new ReminderDtos.Item(r.reminderId(), r.title(), r.content(), r.reminderAt(), r.status(), r.snoozedUntil(), r.completedAt(), r.needsHelpAt(), r.reminderType()); }
    private ReminderDtos.RecordItem record(ReminderRepository.RecordRow r) { return new ReminderDtos.RecordItem(r.reminderId(), r.title(), r.action(), r.fromStatus(), r.toStatus(), r.note(), r.actedAt()); }
}
