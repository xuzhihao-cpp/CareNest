package com.csu.carenest.careadmin.attention;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;

/** 阶段31数据访问层，只消费成员1已冻结的表和字段。 */
@Repository
public class Phase31AttentionRepository {

    private final JdbcTemplate jdbcTemplate;

    public Phase31AttentionRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<OrderContext> findOrderContext(String orderId) {
        List<OrderContext> rows = jdbcTemplate.query("""
                SELECT o.order_id, o.elder_id, o.order_status,
                       nt.task_id, nt.nurse_id, nt.task_status, nt.dispatch_remark,
                       si.service_id, si.service_name, si.service_desc,
                       ha.archive_id, ha.archive_version, ha.care_summary
                FROM nursing_order o
                JOIN nurse_task nt ON nt.order_id = o.order_id
                JOIN service_item si ON si.service_id = o.service_id
                LEFT JOIN health_archive ha ON ha.elder_id = o.elder_id
                WHERE o.order_id = ?
                """, (rs, rowNum) -> new OrderContext(
                rs.getString("order_id"),
                rs.getString("elder_id"),
                rs.getString("order_status"),
                rs.getString("task_id"),
                rs.getString("nurse_id"),
                rs.getString("task_status"),
                rs.getString("dispatch_remark"),
                rs.getString("service_id"),
                rs.getString("service_name"),
                rs.getString("service_desc"),
                rs.getString("archive_id"),
                (Integer) rs.getObject("archive_version"),
                rs.getString("care_summary")), orderId);
        return rows.stream().findFirst();
    }

    public List<AllergySource> findAllergies(String elderId) {
        return jdbcTemplate.query("""
                SELECT allergy_id, allergen, reaction, severity, remark
                FROM allergy_record
                WHERE elder_id = ?
                ORDER BY allergy_id
                """, (rs, rowNum) -> new AllergySource(
                rs.getString("allergy_id"), rs.getString("allergen"),
                rs.getString("reaction"), rs.getString("severity"), rs.getString("remark")), elderId);
    }

    public List<RiskSource> findRisks(String elderId) {
        return jdbcTemplate.query("""
                SELECT risk_tag_id, tag_name, risk_level, remark
                FROM risk_tag
                WHERE elder_id = ?
                ORDER BY risk_tag_id
                """, (rs, rowNum) -> new RiskSource(
                rs.getString("risk_tag_id"), rs.getString("tag_name"),
                rs.getString("risk_level"), rs.getString("remark")), elderId);
    }

    public boolean hasPermission(String userId, String permissionCode) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM user_role ur
                JOIN sys_role r ON r.role_id = ur.role_id AND r.enabled = 1
                JOIN role_permission rp ON rp.role_id = r.role_id
                JOIN sys_permission p ON p.permission_id = rp.permission_id AND p.enabled = 1
                WHERE ur.user_id = ? AND p.permission_code = ?
                """, Integer.class, userId, permissionCode);
        return count != null && count > 0;
    }

    public void saveNotice(NoticeSnapshot notice) {
        // 同一来源内容变化时保留旧快照但将其标记为失效，避免静默覆盖已确认记录。
        jdbcTemplate.update("""
                UPDATE care_attention_notice
                SET notice_status = 'CANCELED'
                WHERE order_id = ? AND source_type = ? AND source_id = ?
                  AND notice_hash <> ? AND notice_status = 'ACTIVE'
                """, notice.orderId(), notice.source(), notice.sourceId(), notice.noticeHash());
        try {
            jdbcTemplate.update("""
                    INSERT INTO care_attention_notice
                      (notice_id, order_id, task_id, nurse_id, notice_level, content,
                       source_type, source_id, source_version, required_ack, notice_hash,
                       notice_status, generated_by)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'ACTIVE', NULL)
                    """, notice.noticeId(), notice.orderId(), notice.taskId(), notice.nurseId(),
                    notice.level(), notice.content(), notice.source(), notice.sourceId(),
                    notice.sourceVersion(), notice.requiredAck(), notice.noticeHash());
        } catch (DuplicateKeyException duplicate) {
            // 重新派单复用业务快照，但确认记录仍按 nurse_id 隔离，新护理必须重新确认。
            jdbcTemplate.update("""
                    UPDATE care_attention_notice
                    SET task_id = ?, nurse_id = ?, notice_status = 'ACTIVE'
                    WHERE order_id = ? AND source_type = ? AND source_id = ? AND notice_hash = ?
                    """, notice.taskId(), notice.nurseId(), notice.orderId(),
                    notice.source(), notice.sourceId(), notice.noticeHash());
        }
    }

    public List<ActiveNoticeKey> findActiveNoticeKeys(String orderId) {
        return jdbcTemplate.query("""
                SELECT notice_id, source_type, source_id
                FROM care_attention_notice
                WHERE order_id = ? AND notice_status = 'ACTIVE'
                """, (rs, rowNum) -> new ActiveNoticeKey(
                rs.getString("notice_id"), rs.getString("source_type"), rs.getString("source_id")), orderId);
    }

    public void cancelNotice(String noticeId) {
        jdbcTemplate.update("""
                UPDATE care_attention_notice
                SET notice_status = 'CANCELED'
                WHERE notice_id = ? AND notice_status = 'ACTIVE'
                """, noticeId);
    }

    public List<AttentionNoticeDtos.AttentionNoticeItem> findActiveNotices(String orderId, String nurseId) {
        return jdbcTemplate.query("""
                SELECT n.notice_id, n.notice_level, n.content, n.source_type, n.required_ack,
                       a.acked_at
                FROM care_attention_notice n
                LEFT JOIN care_attention_ack a
                  ON a.notice_id = n.notice_id AND a.nurse_id = ?
                WHERE n.order_id = ? AND n.nurse_id = ? AND n.notice_status = 'ACTIVE'
                ORDER BY CASE n.notice_level
                           WHEN 'CRITICAL' THEN 1 WHEN 'WARNING' THEN 2 ELSE 3 END,
                         n.generated_at, n.notice_id
                """, (rs, rowNum) -> {
            Timestamp ackedAt = rs.getTimestamp("acked_at");
            return new AttentionNoticeDtos.AttentionNoticeItem(
                    rs.getString("notice_id"), rs.getString("notice_level"),
                    rs.getString("content"), rs.getString("source_type"),
                    rs.getBoolean("required_ack"), ackedAt != null, timestampText(ackedAt));
        }, nurseId, orderId, nurseId);
    }

    public List<String> findVisibleNoticeIds(String orderId, String taskId, String nurseId, List<String> noticeIds) {
        String placeholders = String.join(",", java.util.Collections.nCopies(noticeIds.size(), "?"));
        String sql = "SELECT notice_id FROM care_attention_notice "
                + "WHERE order_id = ? AND task_id = ? AND nurse_id = ? AND notice_status = 'ACTIVE' "
                + "AND notice_id IN (" + placeholders + ")";
        java.util.ArrayList<Object> args = new java.util.ArrayList<>();
        args.add(orderId);
        args.add(taskId);
        args.add(nurseId);
        args.addAll(noticeIds);
        return jdbcTemplate.query(sql, (rs, rowNum) -> rs.getString("notice_id"), args.toArray());
    }

    public void acknowledge(String ackId, String noticeId, OrderContext context, String userId) {
        try {
            jdbcTemplate.update("""
                    INSERT INTO care_attention_ack
                      (ack_id, notice_id, order_id, task_id, nurse_id, acked_by)
                    VALUES (?, ?, ?, ?, ?, ?)
                    """, ackId, noticeId, context.orderId(), context.taskId(),
                    context.nurseId(), userId);
        } catch (DuplicateKeyException ignored) {
            // 唯一键 notice_id + nurse_id 使重复确认天然幂等，保留首次确认时间。
        }
    }

    public long countUnacknowledgedRequired(String orderId, String taskId, String nurseId) {
        Long count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM care_attention_notice n
                LEFT JOIN care_attention_ack a
                  ON a.notice_id = n.notice_id AND a.nurse_id = ?
                WHERE n.order_id = ? AND n.task_id = ? AND n.nurse_id = ?
                  AND n.notice_status = 'ACTIVE' AND n.required_ack = 1
                  AND a.ack_id IS NULL
                """, Long.class, nurseId, orderId, taskId, nurseId);
        return count == null ? 0 : count;
    }

    public void insertOperationLog(
            String logId, String operatorId, String roleCode, String orderId, String afterValue, String traceId) {
        jdbcTemplate.update("""
                INSERT INTO operation_log
                  (log_id, operator_id, role_code, operation_type, biz_type, biz_id,
                   after_value, trace_id)
                VALUES (?, ?, ?, 'ACK_CARE_ATTENTION', 'NURSING_ORDER', ?, ?, ?)
                """, logId, operatorId, roleCode, orderId, afterValue, traceId);
    }

    private static String timestampText(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime().toString();
    }

    public record OrderContext(
            String orderId, String elderId, String orderStatus,
            String taskId, String nurseId, String taskStatus, String dispatchRemark,
            String serviceId, String serviceName, String serviceDescription,
            String archiveId, Integer archiveVersion, String careSummary) {
    }

    public record AllergySource(String id, String allergen, String reaction, String severity, String remark) {
    }

    public record RiskSource(String id, String name, String level, String remark) {
    }

    public record ActiveNoticeKey(String noticeId, String source, String sourceId) {
    }

    public record NoticeSnapshot(
            String noticeId, String orderId, String taskId, String nurseId,
            String level, String content, String source, String sourceId,
            String sourceVersion, boolean requiredAck, String noticeHash) {
    }
}
