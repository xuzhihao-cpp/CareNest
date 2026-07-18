package com.csu.carenest.careadmin.reminder;

import com.csu.carenest.careadmin.auth.AuthService;
import com.csu.carenest.careadmin.auth.CurrentUser;
import com.csu.carenest.careadmin.auth.RoleCode;
import com.csu.carenest.careadmin.common.ApiException;
import com.csu.carenest.careadmin.redis.HomeCacheInvalidator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Set;

@Service
public class ReminderService {

    private static final Set<String> REMINDER_TYPES = Set.of(
            "MEDICATION", "MEASUREMENT", "REHAB", "REVISIT", "FOLLOW_UP", "CUSTOM");
    private static final Set<String> STATUSES = Set.of("PENDING", "DONE", "SNOOZED", "MISSED", "NEED_HELP");

    private final AuthService authService;
    private final ReminderRepository repository;
    private final HomeCacheInvalidator cacheInvalidator;

    public ReminderService(AuthService authService, ReminderRepository repository, HomeCacheInvalidator cacheInvalidator) {
        this.authService = authService;
        this.repository = repository;
        this.cacheInvalidator = cacheInvalidator;
    }

    public ReminderDtos.PageResult<ReminderDtos.ReminderItem> list(CurrentUser currentUser, String elderId, String status,
                                                                   int page, int size) {
        validateAccess(currentUser, elderId);
        authService.requirePermission(currentUser, "REMINDER_VIEW");
        validatePage(page, size);
        String normalizedStatus = normalizeStatus(status);
        return new ReminderDtos.PageResult<>(
                repository.list(elderId, normalizedStatus, size, (page - 1) * size).stream().map(this::toItem).toList(),
                repository.count(elderId, normalizedStatus),
                page,
                size);
    }

    @Transactional
    public ReminderDtos.ReminderItem create(CurrentUser currentUser, String elderId, ReminderDtos.ReminderUpsertRequest request) {
        validateAccess(currentUser, elderId);
        authService.requirePermission(currentUser, "REMINDER_UPDATE");
        String reminderType = normalizeReminderType(request.reminderType());
        String title = normalizeText(request.title(), "提醒标题");
        String content = normalizeNullableText(request.content());
        LocalDateTime scheduledAt = parseDateTime(request.scheduledAt());
        String reminderStatus = normalizeStatusOrDefault(request.reminderStatus());
        ReminderRepository.ReminderRow row = repository.insert(elderId, reminderType, title, content, scheduledAt, reminderStatus, currentUser.userId());
        logMutation(currentUser, "NURSE_REMINDER_CREATE", null, row);
        evictElderHome(elderId);
        return toItem(row);
    }

    @Transactional
    public ReminderDtos.ReminderItem update(CurrentUser currentUser, String elderId, String reminderId, ReminderDtos.ReminderUpsertRequest request) {
        validateAccess(currentUser, elderId);
        authService.requirePermission(currentUser, "REMINDER_UPDATE");
        ReminderRepository.ReminderRow before = repository.find(elderId, reminderId)
                .orElseThrow(() -> new ApiException(404, "提醒不存在"));
        String reminderType = normalizeReminderType(request.reminderType());
        String title = normalizeText(request.title(), "提醒标题");
        String content = normalizeNullableText(request.content());
        LocalDateTime scheduledAt = parseDateTime(request.scheduledAt());
        String reminderStatus = normalizeStatusOrDefault(request.reminderStatus(), before.reminderStatus());
        ReminderRepository.ReminderRow after = repository.update(elderId, reminderId, reminderType, title, content, scheduledAt, reminderStatus);
        logMutation(currentUser, "NURSE_REMINDER_UPDATE", before, after);
        evictElderHome(elderId);
        return toItem(after);
    }

    @Transactional
    public ReminderDtos.ReminderDeleteResponse delete(CurrentUser currentUser, String elderId, String reminderId) {
        validateAccess(currentUser, elderId);
        authService.requirePermission(currentUser, "REMINDER_UPDATE");
        ReminderRepository.ReminderRow current = repository.find(elderId, reminderId)
                .orElseThrow(() -> new ApiException(404, "提醒不存在"));
        if (!"NURSE_MANUAL".equals(current.sourceType())) {
            throw new ApiException(422, "系统生成的提醒不能删除，可直接修改提醒内容和时间");
        }
        ReminderRepository.ReminderRow before = repository.delete(elderId, reminderId);
        repository.insertOperationLog(currentUser.userId(), "NURSE_REMINDER_DELETE", reminderId,
                summary(before), null);
        evictElderHome(elderId);
        return new ReminderDtos.ReminderDeleteResponse(reminderId);
    }

    private void validateAccess(CurrentUser currentUser, String elderId) {
        if (elderId == null || elderId.isBlank()) {
            throw new ApiException(422, "必须指定长辈");
        }
        if (currentUser.hasRole(RoleCode.NURSE) && !repository.hasNurseAssignment(currentUser.userId(), elderId)) {
            throw new ApiException(403, "当前护理员不能管理该长辈提醒");
        }
    }

    private void validatePage(int page, int size) {
        if (page < 1 || size < 1 || size > 50) {
            throw new ApiException(422, "分页参数不合法");
        }
    }

    private ReminderDtos.ReminderItem toItem(ReminderRepository.ReminderRow row) {
        return new ReminderDtos.ReminderItem(
                row.reminderId(), row.elderId(), row.elderName(), row.reminderType(),
                row.title(), row.content(), row.scheduledAt(), row.reminderStatus(),
                row.sourceType(), row.sourceId(), row.createdBy(), row.createdByName());
    }

    private String normalizeText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new ApiException(422, fieldName + "不能为空");
        }
        return value.trim();
    }

    private String normalizeNullableText(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String normalizeReminderType(String value) {
        String normalized = normalizeText(value, "提醒类型").toUpperCase();
        if (!REMINDER_TYPES.contains(normalized)) {
            throw new ApiException(422, "提醒类型不合法");
        }
        return normalized;
    }

    private String normalizeStatus(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim().toUpperCase();
        if (!STATUSES.contains(normalized)) {
            throw new ApiException(422, "提醒状态不合法");
        }
        return normalized;
    }

    private String normalizeStatusOrDefault(String value) {
        return normalizeStatusOrDefault(value, "PENDING");
    }

    private String normalizeStatusOrDefault(String value, String defaultValue) {
        String normalized = normalizeStatus(value);
        return normalized == null ? defaultValue : normalized;
    }

    private LocalDateTime parseDateTime(String value) {
        try {
            return LocalDateTime.parse(normalizeText(value, "提醒时间").replace(' ', 'T'));
        } catch (Exception exception) {
            throw new ApiException(422, "提醒时间格式不合法");
        }
    }

    private void evictElderHome(String elderId) {
        repository.elderUserId(elderId).ifPresent(userId -> cacheInvalidator.evictAfterCommit(RoleCode.ELDER.name(), userId));
    }

    private void logMutation(CurrentUser currentUser, String operationType,
                             ReminderRepository.ReminderRow before, ReminderRepository.ReminderRow after) {
        repository.insertOperationLog(
                currentUser.userId(),
                operationType,
                after.reminderId(),
                before == null ? null : summary(before),
                summary(after));
    }

    private String summary(ReminderRepository.ReminderRow row) {
        return row.reminderType() + "|" + row.title() + "|" + row.scheduledAt() + "|" + row.reminderStatus();
    }
}
