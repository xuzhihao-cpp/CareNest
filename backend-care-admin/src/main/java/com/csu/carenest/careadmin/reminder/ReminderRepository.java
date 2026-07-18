package com.csu.carenest.careadmin.reminder;

import com.csu.carenest.careadmin.common.ApiException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class ReminderRepository {

    private final JdbcTemplate jdbcTemplate;

    public ReminderRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public boolean hasNurseAssignment(String nurseId, String elderId) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM nurse_task nt
                JOIN nursing_order o ON o.order_id = nt.order_id
                WHERE nt.nurse_id = ? AND o.elder_id = ?
                """, Integer.class, nurseId, elderId);
        return count != null && count > 0;
    }

    public Optional<String> elderUserId(String elderId) {
        return jdbcTemplate.query("""
                SELECT user_id FROM elder_profile WHERE elder_id = ?
                """, (rs, rowNum) -> rs.getString("user_id"), elderId).stream().findFirst();
    }

    public long count(String elderId, String status) {
        if (status == null || status.isBlank()) {
            Long count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM reminder_task WHERE elder_id = ?",
                    Long.class, elderId);
            return count == null ? 0L : count;
        }
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM reminder_task WHERE elder_id = ? AND reminder_status = ?",
                Long.class, elderId, status);
        return count == null ? 0L : count;
    }

    public List<ReminderRow> list(String elderId, String status, int limit, int offset) {
        StringBuilder sql = new StringBuilder("""
                SELECT t.task_id, t.elder_id, COALESCE(e.elder_name, '长辈') AS elder_name,
                       t.reminder_type, t.title, t.content, t.scheduled_at, t.reminder_status,
                       t.source_type, t.source_id, t.created_by, COALESCE(u.username, '系统') AS created_by_name
                FROM reminder_task t
                LEFT JOIN elder_profile e ON e.elder_id = t.elder_id
                LEFT JOIN sys_user u ON u.user_id = t.created_by
                WHERE t.elder_id = ?
                """);
        Object[] args;
        if (status == null || status.isBlank()) {
            sql.append("""
                    ORDER BY CASE t.reminder_status
                        WHEN 'PENDING' THEN 1
                        WHEN 'NEED_HELP' THEN 2
                        WHEN 'SNOOZED' THEN 3
                        WHEN 'MISSED' THEN 4
                        ELSE 5 END,
                        t.scheduled_at ASC,
                        t.task_id DESC
                    LIMIT ? OFFSET ?
                    """);
            args = new Object[]{elderId, limit, offset};
        } else {
            sql.append("""
                    AND t.reminder_status = ?
                    ORDER BY CASE t.reminder_status
                        WHEN 'PENDING' THEN 1
                        WHEN 'NEED_HELP' THEN 2
                        WHEN 'SNOOZED' THEN 3
                        WHEN 'MISSED' THEN 4
                        ELSE 5 END,
                        t.scheduled_at ASC,
                        t.task_id DESC
                    LIMIT ? OFFSET ?
                    """);
            args = new Object[]{elderId, status, limit, offset};
        }
        return jdbcTemplate.query(sql.toString(), (rs, rowNum) -> new ReminderRow(
                rs.getString("task_id"),
                rs.getString("elder_id"),
                rs.getString("elder_name"),
                rs.getString("reminder_type"),
                rs.getString("title"),
                rs.getString("content"),
                toLocalDateTime(rs.getTimestamp("scheduled_at")),
                rs.getString("reminder_status"),
                rs.getString("source_type"),
                rs.getString("source_id"),
                rs.getString("created_by"),
                rs.getString("created_by_name")
        ), args);
    }

    public Optional<ReminderRow> find(String elderId, String reminderId) {
        return jdbcTemplate.query("""
                SELECT t.task_id, t.elder_id, COALESCE(e.elder_name, '长辈') AS elder_name,
                       t.reminder_type, t.title, t.content, t.scheduled_at, t.reminder_status,
                       t.source_type, t.source_id, t.created_by, COALESCE(u.username, '系统') AS created_by_name
                FROM reminder_task t
                LEFT JOIN elder_profile e ON e.elder_id = t.elder_id
                LEFT JOIN sys_user u ON u.user_id = t.created_by
                WHERE t.elder_id = ? AND t.task_id = ?
                """, (rs, rowNum) -> new ReminderRow(
                rs.getString("task_id"),
                rs.getString("elder_id"),
                rs.getString("elder_name"),
                rs.getString("reminder_type"),
                rs.getString("title"),
                rs.getString("content"),
                toLocalDateTime(rs.getTimestamp("scheduled_at")),
                rs.getString("reminder_status"),
                rs.getString("source_type"),
                rs.getString("source_id"),
                rs.getString("created_by"),
                rs.getString("created_by_name")
        ), elderId, reminderId).stream().findFirst();
    }

    public ReminderRow insert(String elderId, String reminderType, String title, String content,
                              LocalDateTime scheduledAt, String reminderStatus, String createdBy) {
        String reminderId = id();
        jdbcTemplate.update("""
                INSERT INTO reminder_task (
                    task_id, elder_id, reminder_type, title, content,
                    scheduled_at, reminder_status, source_type, source_id, created_by
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, reminderId, elderId, reminderType, title, content,
                Timestamp.valueOf(scheduledAt), reminderStatus, "NURSE_MANUAL", null, createdBy);
        return find(elderId, reminderId).orElseThrow(() -> new ApiException(404, "提醒不存在"));
    }

    public ReminderRow update(String elderId, String reminderId, String reminderType, String title,
                              String content, LocalDateTime scheduledAt, String reminderStatus) {
        int updated = jdbcTemplate.update("""
                UPDATE reminder_task
                SET reminder_type = ?, title = ?, content = ?, scheduled_at = ?,
                    reminder_status = ?
                WHERE elder_id = ? AND task_id = ?
                """, reminderType, title, content, Timestamp.valueOf(scheduledAt),
                reminderStatus, elderId, reminderId);
        if (updated != 1) {
            throw new ApiException(409, "提醒已被其他人修改，请刷新后重试");
        }
        return find(elderId, reminderId).orElseThrow(() -> new ApiException(404, "提醒不存在"));
    }

    public ReminderRow delete(String elderId, String reminderId) {
        ReminderRow current = find(elderId, reminderId).orElseThrow(() -> new ApiException(404, "提醒不存在"));
        jdbcTemplate.update("DELETE FROM reminder_record WHERE task_id = ?", reminderId);
        int removed = jdbcTemplate.update("DELETE FROM reminder_task WHERE elder_id = ? AND task_id = ?", elderId, reminderId);
        if (removed != 1) {
            throw new ApiException(409, "提醒已被其他人修改，请刷新后重试");
        }
        return current;
    }

    public void insertOperationLog(String operatorId, String operationType, String reminderId,
                                   String beforeValue, String afterValue) {
        jdbcTemplate.update("""
                INSERT INTO operation_log(
                    log_id, operator_id, role_code, operation_type, biz_type, biz_id,
                    before_value, after_value, trace_id
                ) VALUES (?, ?, 'NURSE', ?, 'REMINDER_TASK', ?, ?, ?, ?)
                """, id(), operatorId, operationType, reminderId, beforeValue, afterValue, "reminder-" + id());
    }

    private static LocalDateTime toLocalDateTime(Timestamp value) {
        return value == null ? null : value.toLocalDateTime();
    }

    private static String id() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    public record ReminderRow(
            String reminderId,
            String elderId,
            String elderName,
            String reminderType,
            String title,
            String content,
            LocalDateTime scheduledAt,
            String reminderStatus,
            String sourceType,
            String sourceId,
            String createdBy,
            String createdByName) {
    }
}
