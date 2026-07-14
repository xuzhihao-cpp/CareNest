package com.csu.carenest.user.healthfeedback;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

@Repository
public class HealthFeedbackRepository {
    private final JdbcTemplate jdbc;
    public HealthFeedbackRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public Optional<ElderRow> findElderByUser(String userId) { return findElder("user_id", userId); }
    public Optional<ElderRow> findElder(String elderId) { return findElder("elder_id", elderId); }
    private Optional<ElderRow> findElder(String column, String value) {
        return jdbc.query("SELECT elder_id,elder_name,user_id FROM elder_profile WHERE " + column + "=?",
                (rs, n) -> new ElderRow(rs.getString(1), rs.getString(2), rs.getString(3)), value)
                .stream().findFirst();
    }

    public boolean hasActiveScope(String familyId, String elderId, String scope) {
        return jdbc.queryForObject("""
                SELECT COUNT(*) FROM elder_family_binding
                WHERE family_id=? AND elder_id=? AND binding_status='ACTIVE' AND scope_codes LIKE ?
                """, Integer.class, familyId, elderId, "%\"" + scope + "\"%") > 0;
    }

    public Optional<AssetRow> findAsset(String fileId) {
        return jdbc.query("SELECT file_id,mime_type,object_key,uploaded_by,file_size FROM file_asset WHERE file_id=?",
                (rs, n) -> new AssetRow(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getLong(5)), fileId)
                .stream().findFirst();
    }

    public void insert(String id, String elderId, HealthFeedbackDtos.CreateRequest request, String userId) {
        jdbc.update("""
                INSERT INTO elder_health_feedback
                  (feedback_id,elder_id,feedback_type,severity,content,input_type,file_id,created_by)
                VALUES (?,?,?,?,?,?,?,?)
                """, id, elderId, request.feedbackType(), request.severity(), clean(request.content()),
                request.inputType(), clean(request.fileId()), userId);
    }

    public void insertVoiceLog(String id, String userId, String fileId, String feedbackId) {
        jdbc.update("""
                INSERT INTO voice_command_log
                  (voice_log_id,user_id,file_id,intent_type,source_biz_type,source_biz_id)
                VALUES (?,?,?,'HEALTH_FEEDBACK','ELDER_HEALTH_FEEDBACK',?)
                """, id, userId, fileId, feedbackId);
    }

    public void insertHighSeverityLog(String id, String userId, String feedbackId) {
        jdbc.update("""
                INSERT INTO operation_log
                  (log_id,operator_id,role_code,operation_type,biz_type,biz_id,after_value,trace_id)
                VALUES (?,?,'ELDER','HIGH_HEALTH_FEEDBACK','ELDER_HEALTH_FEEDBACK',?,?,?)
                """, id, userId, feedbackId, "{\"severity\":\"HIGH\"}", id);
    }

    public LocalDateTime createdAt(String id) {
        return jdbc.queryForObject("SELECT created_at FROM elder_health_feedback WHERE feedback_id=?",
                LocalDateTime.class, id);
    }

    public long count(String elderId, String type, String severity, LocalDate from, LocalDate to) {
        Query query = filters(elderId, type, severity, from, to);
        return jdbc.queryForObject("SELECT COUNT(*) FROM elder_health_feedback f" + query.where,
                Long.class, query.args.toArray());
    }

    public List<FeedbackRow> list(String elderId, String type, String severity, LocalDate from, LocalDate to,
                                  int offset, int size) {
        Query query = filters(elderId, type, severity, from, to);
        query.args.add(size); query.args.add(offset);
        return jdbc.query("""
                SELECT f.feedback_id,f.elder_id,e.elder_name,f.feedback_type,f.severity,f.content,
                       f.input_type,f.file_id,f.created_at,a.object_key
                FROM elder_health_feedback f JOIN elder_profile e ON e.elder_id=f.elder_id
                LEFT JOIN file_asset a ON a.file_id=f.file_id
                """ + query.where + " ORDER BY CASE f.severity WHEN 'HIGH' THEN 3 WHEN 'MEDIUM' THEN 2 ELSE 1 END DESC,"
                + " f.created_at DESC,f.feedback_id DESC LIMIT ? OFFSET ?",
                (rs, n) -> new FeedbackRow(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4),
                        rs.getString(5), rs.getString(6), rs.getString(7), rs.getString(8),
                        rs.getTimestamp(9).toLocalDateTime(), rs.getString(10)), query.args.toArray());
    }

    public List<String> activeFamilyIds(String elderId) {
        return jdbc.queryForList("SELECT DISTINCT family_id FROM elder_family_binding WHERE elder_id=? AND binding_status='ACTIVE'",
                String.class, elderId);
    }

    private Query filters(String elderId, String type, String severity, LocalDate from, LocalDate to) {
        StringBuilder sql = new StringBuilder(" WHERE f.elder_id=?");
        List<Object> args = new ArrayList<>(List.of(elderId));
        if (type != null) { sql.append(" AND f.feedback_type=?"); args.add(type); }
        if (severity != null) { sql.append(" AND f.severity=?"); args.add(severity); }
        if (from != null) { sql.append(" AND f.created_at>=?"); args.add(Timestamp.valueOf(from.atStartOfDay())); }
        if (to != null) { sql.append(" AND f.created_at<?"); args.add(Timestamp.valueOf(to.plusDays(1).atStartOfDay())); }
        return new Query(sql.toString(), args);
    }
    private static String clean(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private record Query(String where, List<Object> args) {}
    public record ElderRow(String elderId, String elderName, String userId) {}
    public record AssetRow(String fileId, String mimeType, String objectKey, String uploadedBy, long fileSize) {}
    public record FeedbackRow(String feedbackId, String elderId, String elderName, String feedbackType,
                              String severity, String content, String inputType, String fileId,
                              LocalDateTime createdAt, String objectKey) {}
}
