package com.csu.carenest.user.reminder;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class ReminderRepository {
    private final JdbcTemplate jdbc;

    public ReminderRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public Optional<String> elderIdForUser(String userId) {
        return jdbc.queryForList("SELECT elder_id FROM elder_profile WHERE user_id=?", String.class, userId)
                .stream().findFirst();
    }

    public long count(String elderId, String status) {
        if (status == null) return jdbc.queryForObject("SELECT COUNT(*) FROM reminder_task WHERE elder_id=?", Long.class, elderId);
        return jdbc.queryForObject("SELECT COUNT(*) FROM reminder_task WHERE elder_id=? AND reminder_status=?", Long.class, elderId, status);
    }

    public List<ReminderRow> list(String elderId, String status, int limit, int offset) {
        String filter = status == null ? "" : " AND reminder_status=?";
        Object[] args = status == null ? new Object[]{elderId, limit, offset} : new Object[]{elderId, status, limit, offset};
        return jdbc.query("SELECT reminder_id,title,content,reminder_at,reminder_status,snoozed_until,completed_at,needs_help_at,source_type "
                + "FROM reminder_task WHERE elder_id=?" + filter
                + " ORDER BY CASE reminder_status WHEN 'NEED_HELP' THEN 1 WHEN 'PENDING' THEN 2 WHEN 'SNOOZED' THEN 3 ELSE 4 END, "
                + "reminder_at DESC, reminder_id DESC LIMIT ? OFFSET ?",
                (rs, n) -> new ReminderRow(rs.getString(1), rs.getString(2), rs.getString(3),
                        rs.getTimestamp(4).toLocalDateTime(), rs.getString(5), toLocal(rs.getTimestamp(6)),
                        toLocal(rs.getTimestamp(7)), toLocal(rs.getTimestamp(8)), rs.getString(9)), args);
    }

    public Optional<ReminderRow> find(String elderId, String reminderId) {
        return jdbc.query("""
                SELECT reminder_id,title,content,reminder_at,reminder_status,snoozed_until,completed_at,needs_help_at,source_type
                FROM reminder_task WHERE elder_id=? AND reminder_id=?
                """, (rs, n) -> new ReminderRow(rs.getString(1), rs.getString(2), rs.getString(3),
                rs.getTimestamp(4).toLocalDateTime(), rs.getString(5), toLocal(rs.getTimestamp(6)),
                toLocal(rs.getTimestamp(7)), toLocal(rs.getTimestamp(8)), rs.getString(9)), elderId, reminderId)
                .stream().findFirst();
    }

    public ReminderRow updateStatus(String elderId, String reminderId, String fromStatus, String toStatus,
                                    LocalDateTime snoozedUntil, LocalDateTime completedAt, LocalDateTime needsHelpAt) {
        int updated = jdbc.update("""
                UPDATE reminder_task SET reminder_status=?,snoozed_until=?,completed_at=?,needs_help_at=?
                WHERE elder_id=? AND reminder_id=? AND reminder_status=?
                """, toStatus, ts(snoozedUntil), ts(completedAt), ts(needsHelpAt), elderId, reminderId, fromStatus);
        if (updated != 1) throw new IllegalStateException("Reminder changed concurrently");
        return find(elderId, reminderId).orElseThrow();
    }

    public String insertRecord(String reminderId, String elderId, String from, String to,
                               String action, String actor, String note) {
        String id = id();
        jdbc.update("""
                INSERT INTO reminder_record(record_id,reminder_id,elder_id,from_status,to_status,action_type,acted_by,note)
                VALUES (?,?,?,?,?,?,?,?)
                """, id, reminderId, elderId, from, to, action, actor, clean(note));
        return id;
    }

    public void insertOperationLog(String actor, String reminderId, String from, String to) {
        jdbc.update("""
                INSERT INTO operation_log(log_id,operator_id,role_code,operation_type,biz_type,biz_id,before_value,after_value,trace_id)
                VALUES (?,?, 'ELDER', 'REMINDER_ACTION', 'REMINDER_TASK', ?, ?, ?, ?)
                """, id(), actor, reminderId, "{\"status\":\"" + from + "\"}",
                "{\"status\":\"" + to + "\"}", "reminder-" + id());
    }

    public RecordRow record(String recordId) {
        return jdbc.queryForObject("""
                SELECT r.reminder_id,t.title,r.action_type,r.from_status,r.to_status,r.note,r.created_at
                FROM reminder_record r JOIN reminder_task t ON t.reminder_id=r.reminder_id
                WHERE r.record_id=?""", (rs, n) -> new RecordRow(rs.getString(1), rs.getString(2), rs.getString(3),
                rs.getString(4), rs.getString(5), rs.getString(6), rs.getTimestamp(7).toLocalDateTime()), recordId);
    }

    public List<RecordRow> records(String elderId, int limit, int offset) {
        return jdbc.query("""
                SELECT r.reminder_id,t.title,r.action_type,r.from_status,r.to_status,r.note,r.created_at
                FROM reminder_record r JOIN reminder_task t ON t.reminder_id=r.reminder_id
                WHERE r.elder_id=? ORDER BY r.created_at DESC,r.record_id DESC LIMIT ? OFFSET ?
                """, (rs, n) -> new RecordRow(rs.getString(1), rs.getString(2), rs.getString(3),
                rs.getString(4), rs.getString(5), rs.getString(6), rs.getTimestamp(7).toLocalDateTime()), elderId, limit, offset);
    }

    public long recordCount(String elderId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM reminder_record WHERE elder_id=?", Long.class, elderId);
    }

    private static Timestamp ts(LocalDateTime value) { return value == null ? null : Timestamp.valueOf(value); }
    private static LocalDateTime toLocal(Timestamp value) { return value == null ? null : value.toLocalDateTime(); }
    private static String clean(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private static String id() { return UUID.randomUUID().toString().replace("-", ""); }
    public record ReminderRow(String reminderId, String title, String content, LocalDateTime reminderAt,
                              String status, LocalDateTime snoozedUntil, LocalDateTime completedAt,
                              LocalDateTime needsHelpAt, String sourceType) {}
    public record RecordRow(String reminderId, String title, String action, String fromStatus,
                            String toStatus, String note, LocalDateTime actedAt) {}
}
