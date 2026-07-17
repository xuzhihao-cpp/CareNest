package com.csu.carenest.careadmin.support;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/** 阶段44-46数据访问层。 */
@Repository
public class Phase44To46SupportRepository {

    private final JdbcTemplate jdbcTemplate;

    public Phase44To46SupportRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public boolean hasPermission(String userId, String permissionCode) {
        return count("""
                SELECT COUNT(*) FROM user_role ur
                JOIN sys_role r ON r.role_id=ur.role_id AND r.enabled=1
                JOIN role_permission rp ON rp.role_id=r.role_id
                JOIN sys_permission p ON p.permission_id=rp.permission_id AND p.enabled=1
                WHERE ur.user_id=? AND p.permission_code=?
                """, userId, permissionCode) > 0;
    }

    public Optional<TicketContext> findTicket(String ticketId) {
        List<TicketContext> rows = jdbcTemplate.query("""
                SELECT ticket_id, elder_id, ticket_status
                FROM customer_service_ticket WHERE ticket_id=?
                """, (rs, rowNum) -> new TicketContext(
                rs.getString("ticket_id"), rs.getString("elder_id"),
                rs.getString("ticket_status")), ticketId);
        return rows.stream().findFirst();
    }

    public void updateTicketStatus(String ticketId, String status, String assignedTo) {
        jdbcTemplate.update("""
                UPDATE customer_service_ticket
                SET ticket_status=?,assigned_to=COALESCE(assigned_to,?),
                    resolved_at=CASE WHEN ?='RESOLVED' THEN CURRENT_TIMESTAMP ELSE resolved_at END
                WHERE ticket_id=?
                """, status, assignedTo, status, ticketId);
    }

    public void insertTicketFollowUp(
            String followUpId, TicketContext ticket, String type, String content,
            LocalDateTime nextFollowUpAt, String userId) {
        jdbcTemplate.update("""
                INSERT INTO follow_up_record
                  (follow_up_id,elder_id,ticket_id,follow_up_type,content,
                   next_follow_up_at,need_reminder,created_by)
                VALUES (?,?,?,?,?,?,0,?)
                """, followUpId, ticket.elderId(), ticket.ticketId(), type,
                content, nextFollowUpAt, userId);
    }

    public List<FollowUpRecord> findTicketFollowUps(String ticketId) {
        return jdbcTemplate.query("""
                SELECT f.follow_up_id,t.ticket_status,f.follow_up_type,f.content,
                       f.next_follow_up_at,f.created_at
                FROM follow_up_record f
                JOIN customer_service_ticket t ON t.ticket_id=f.ticket_id
                WHERE f.ticket_id=? ORDER BY f.created_at,f.follow_up_id
                """, (rs, rowNum) -> new FollowUpRecord(
                rs.getString("follow_up_id"), rs.getString("ticket_status"),
                rs.getString("follow_up_type"), rs.getString("content"),
                rs.getTimestamp("next_follow_up_at") == null ? null
                        : rs.getTimestamp("next_follow_up_at").toLocalDateTime(),
                rs.getTimestamp("created_at").toLocalDateTime()), ticketId);
    }

    public Optional<OrderReviewContext> findOrderForReview(String orderId) {
        List<OrderReviewContext> rows = jdbcTemplate.query("""
                SELECT o.order_id,o.elder_id,o.family_id,o.order_status,
                       sr.report_id,nt.nurse_id,si.service_name
                FROM nursing_order o
                JOIN service_report sr ON sr.order_id=o.order_id
                JOIN nurse_task nt ON nt.order_id=o.order_id
                JOIN service_item si ON si.service_id=o.service_id
                WHERE o.order_id=?
                """, (rs, rowNum) -> new OrderReviewContext(
                rs.getString("order_id"), rs.getString("elder_id"),
                rs.getString("family_id"), rs.getString("order_status"),
                rs.getString("report_id"), rs.getString("nurse_id"),
                rs.getString("service_name")), orderId);
        return rows.stream().findFirst();
    }

    public boolean familyCanReview(String familyId, OrderReviewContext order) {
        return count("""
                SELECT COUNT(*) FROM elder_family_binding
                WHERE elder_id=? AND family_id=? AND binding_status='ACTIVE'
                  AND scope_codes LIKE '%\"REPORT_VIEW\"%'
                """, order.elderId(), familyId) > 0;
    }

    public boolean fileOwnedBy(String fileId, String userId) {
        return count("SELECT COUNT(*) FROM file_asset WHERE file_id=? AND uploaded_by=?", fileId, userId) > 0;
    }

    public boolean reviewExists(String orderId, String reviewerId) {
        return count("SELECT COUNT(*) FROM review WHERE order_id=? AND reviewer_id=?", orderId, reviewerId) > 0;
    }

    public void insertReview(
            String reviewId, OrderReviewContext order, String reviewerId,
            int rating, String content) {
        jdbcTemplate.update("""
                INSERT INTO review
                  (review_id,order_id,report_id,reviewer_id,reviewer_role,rating,satisfaction,content)
                VALUES (?,?,?,?, 'FAMILY',?,?,?)
                """, reviewId, order.orderId(), order.reportId(), reviewerId, rating, rating, content);
    }

    public void insertComplaint(
            String complaintId, String orderId, String complainantId, String content) {
        jdbcTemplate.update("""
                INSERT INTO complaint
                  (complaint_id,order_id,complainant_id,complaint_status,content)
                VALUES (?,?,?,'PENDING',?)
                """, complaintId, orderId, complainantId, content);
    }

    public List<ReviewRecord> findReviews() {
        return jdbcTemplate.query("""
                SELECT r.review_id,r.order_id,si.service_name,
                       ep.elder_name,u.display_name AS reviewer_name,
                       r.reviewer_role,r.rating,r.satisfaction,r.content,r.created_at
                FROM review r
                JOIN nursing_order o ON o.order_id=r.order_id
                JOIN service_item si ON si.service_id=o.service_id
                JOIN elder_profile ep ON ep.elder_id=o.elder_id
                JOIN sys_user u ON u.user_id=r.reviewer_id
                ORDER BY r.created_at DESC,r.review_id DESC
                """, (rs, rowNum) -> new ReviewRecord(
                rs.getString("review_id"), rs.getString("order_id"),
                rs.getString("service_name"), rs.getString("elder_name"),
                rs.getString("reviewer_name"), rs.getString("reviewer_role"),
                rs.getInt("rating"), (Integer) rs.getObject("satisfaction"),
                rs.getString("content"), rs.getTimestamp("created_at").toLocalDateTime()));
    }

    public List<ComplaintRecord> findComplaints() {
        return jdbcTemplate.query("""
                SELECT c.review_id,c.complaint_id,c.order_id,c.complaint_status,
                       c.content,c.handle_result,c.created_at,
                       si.service_name,u.display_name AS complainant_name
                FROM complaint c
                JOIN nursing_order o ON o.order_id=c.order_id
                JOIN service_item si ON si.service_id=o.service_id
                JOIN sys_user u ON u.user_id=c.complainant_id
                ORDER BY c.created_at DESC,c.complaint_id DESC
                """, (rs, rowNum) -> new ComplaintRecord(
                rs.getString("review_id"), rs.getString("complaint_id"),
                rs.getString("order_id"), rs.getString("service_name"),
                rs.getString("complainant_name"), rs.getString("complaint_status"),
                rs.getString("content"), rs.getString("handle_result"),
                rs.getTimestamp("created_at").toLocalDateTime()));
    }

    public Optional<ComplaintContext> findComplaint(String complaintId) {
        List<ComplaintContext> rows = jdbcTemplate.query("""
                SELECT c.complaint_id,c.order_id,c.complaint_status,nt.nurse_id
                FROM complaint c JOIN nurse_task nt ON nt.order_id=c.order_id
                WHERE c.complaint_id=?
                """, (rs, rowNum) -> new ComplaintContext(
                rs.getString("complaint_id"), rs.getString("order_id"),
                rs.getString("complaint_status"), rs.getString("nurse_id")), complaintId);
        return rows.stream().findFirst();
    }

    public int handleComplaint(
            String complaintId, String status, String result, String handlerId) {
        return jdbcTemplate.update("""
                UPDATE complaint SET complaint_status=?,handle_result=?,handled_by=?,handled_at=CURRENT_TIMESTAMP
                WHERE complaint_id=? AND complaint_status IN ('PENDING','PROCESSING')
                """, status, result, handlerId, complaintId);
    }

    public boolean appealTargetBelongsToNurse(String targetType, String targetId, String nurseId) {
        return switch (targetType) {
            case "COMPLAINT" -> count("""
                    SELECT COUNT(*) FROM complaint c JOIN nurse_task nt ON nt.order_id=c.order_id
                    WHERE c.complaint_id=? AND nt.nurse_id=?
                    """, targetId, nurseId) > 0;
            case "METRIC" -> count("""
                    SELECT COUNT(*) FROM order_metric_item m JOIN nurse_task nt ON nt.order_id=m.order_id
                    WHERE m.order_metric_item_id=? AND nt.nurse_id=?
                    """, targetId, nurseId) > 0;
            case "EXCEPTION_PROOF" -> count("""
                    SELECT COUNT(*) FROM metric_exception_proof p
                    JOIN order_metric_item m ON m.order_metric_item_id=p.order_metric_item_id
                    JOIN nurse_task nt ON nt.order_id=m.order_id
                    WHERE p.proof_id=? AND nt.nurse_id=?
                    """, targetId, nurseId) > 0;
            default -> false;
        };
    }

    public Optional<String> findTargetNurse(String targetType, String targetId) {
        String sql = switch (targetType) {
            case "COMPLAINT" -> """
                    SELECT nt.nurse_id FROM complaint c JOIN nurse_task nt ON nt.order_id=c.order_id
                    WHERE c.complaint_id=?
                    """;
            case "METRIC" -> """
                    SELECT nt.nurse_id FROM order_metric_item m JOIN nurse_task nt ON nt.order_id=m.order_id
                    WHERE m.order_metric_item_id=?
                    """;
            case "EXCEPTION_PROOF" -> """
                    SELECT nt.nurse_id FROM metric_exception_proof p
                    JOIN order_metric_item m ON m.order_metric_item_id=p.order_metric_item_id
                    JOIN nurse_task nt ON nt.order_id=m.order_id WHERE p.proof_id=?
                    """;
            default -> null;
        };
        if (sql == null) {
            return Optional.empty();
        }
        List<String> rows = jdbcTemplate.query(sql, (rs, rowNum) -> rs.getString("nurse_id"), targetId);
        return rows.stream().findFirst();
    }

    public boolean pendingAppealExists(String nurseId, String targetType, String targetId) {
        return count("""
                SELECT COUNT(*) FROM nurse_appeal
                WHERE nurse_id=? AND target_type=? AND target_id=? AND appeal_status='PENDING'
                """, nurseId, targetType, targetId) > 0;
    }

    public void insertAppeal(
            String appealId, String nurseId, String targetType, String targetId,
            String reason, String fileIdsJson) {
        jdbcTemplate.update("""
                INSERT INTO nurse_appeal
                  (appeal_id,nurse_id,target_type,target_id,reason,file_ids,appeal_status,score_adjustment)
                VALUES (?,?,?,?,?,?,'PENDING',0)
                """, appealId, nurseId, targetType, targetId, reason, fileIdsJson);
    }

    public List<AppealRecord> findAppeals(String nurseId) {
        return jdbcTemplate.query("""
                SELECT a.appeal_id,a.nurse_id,u.display_name AS nurse_name,
                       a.target_type,a.target_id,a.reason,a.file_ids,a.appeal_status,
                       a.score_adjustment,a.review_comment,a.created_at
                FROM nurse_appeal a JOIN sys_user u ON u.user_id=a.nurse_id
                WHERE a.nurse_id=? ORDER BY a.created_at DESC,a.appeal_id DESC
                """, appealMapper(), nurseId);
    }

    public List<AppealRecord> findAllAppeals() {
        return jdbcTemplate.query("""
                SELECT a.appeal_id,a.nurse_id,u.display_name AS nurse_name,
                       a.target_type,a.target_id,a.reason,a.file_ids,a.appeal_status,
                       a.score_adjustment,a.review_comment,a.created_at
                FROM nurse_appeal a JOIN sys_user u ON u.user_id=a.nurse_id
                ORDER BY a.created_at DESC,a.appeal_id DESC
                """, appealMapper());
    }

    private org.springframework.jdbc.core.RowMapper<AppealRecord> appealMapper() {
        return (rs, rowNum) -> new AppealRecord(
                rs.getString("appeal_id"), rs.getString("nurse_id"),
                rs.getString("nurse_name"), rs.getString("target_type"),
                rs.getString("target_id"), rs.getString("reason"),
                rs.getString("file_ids"), rs.getString("appeal_status"),
                rs.getBigDecimal("score_adjustment"), rs.getString("review_comment"),
                rs.getTimestamp("created_at").toLocalDateTime());
    }

    public Optional<AppealContext> findAppeal(String appealId) {
        List<AppealContext> rows = jdbcTemplate.query("""
                SELECT appeal_id,nurse_id,target_type,target_id,appeal_status
                FROM nurse_appeal WHERE appeal_id=?
                """, (rs, rowNum) -> new AppealContext(
                rs.getString("appeal_id"), rs.getString("nurse_id"),
                rs.getString("target_type"), rs.getString("target_id"),
                rs.getString("appeal_status")), appealId);
        return rows.stream().findFirst();
    }

    public BigDecimal targetDeduction(AppealContext appeal) {
        BigDecimal value = switch (appeal.targetType()) {
            case "METRIC" -> jdbcTemplate.queryForObject("""
                    SELECT score_weight FROM order_metric_item WHERE order_metric_item_id=?
                    """, BigDecimal.class, appeal.targetId());
            case "EXCEPTION_PROOF" -> jdbcTemplate.queryForObject("""
                    SELECT m.score_weight FROM metric_exception_proof p
                    JOIN order_metric_item m ON m.order_metric_item_id=p.order_metric_item_id
                    WHERE p.proof_id=?
                    """, BigDecimal.class, appeal.targetId());
            case "COMPLAINT" -> jdbcTemplate.queryForObject("""
                    SELECT ABS(COALESCE(MIN(score_delta),-5)) FROM metric_score_rule
                    WHERE rule_type='COMPLAINT' AND enabled=1
                    """, BigDecimal.class);
            default -> BigDecimal.ZERO;
        };
        return value == null ? BigDecimal.ZERO : value.abs();
    }

    public int reviewAppeal(
            String appealId, String status, BigDecimal adjustment,
            String comment, String reviewerId) {
        return jdbcTemplate.update("""
                UPDATE nurse_appeal SET appeal_status=?,score_adjustment=?,review_comment=?,
                    reviewed_by=?,reviewed_at=CURRENT_TIMESTAMP
                WHERE appeal_id=? AND appeal_status='PENDING'
                """, status, adjustment, comment, reviewerId, appealId);
    }

    public void insertOperationLog(
            String logId, String operatorId, String roleCode, String operationType,
            String bizType, String bizId, String beforeJson, String afterJson, String traceId) {
        jdbcTemplate.update("""
                INSERT INTO operation_log
                  (log_id,operator_id,role_code,operation_type,biz_type,biz_id,
                   before_value,after_value,trace_id)
                VALUES (?,?,?,?,?,?,?,?,?)
                """, logId, operatorId, roleCode, operationType, bizType, bizId,
                beforeJson, afterJson, traceId);
    }

    private int count(String sql, Object... args) {
        Integer result = jdbcTemplate.queryForObject(sql, Integer.class, args);
        return result == null ? 0 : result;
    }

    public record TicketContext(String ticketId, String elderId, String status) {
    }

    public record OrderReviewContext(
            String orderId, String elderId, String familyId, String orderStatus,
            String reportId, String nurseId, String serviceName) {
    }

    public record ComplaintContext(
            String complaintId, String orderId, String status, String nurseId) {
    }

    public record AppealContext(
            String appealId, String nurseId, String targetType,
            String targetId, String status) {
    }

    public record FollowUpRecord(
            String followUpId, String ticketStatus, String method,
            String storedContent, LocalDateTime nextFollowUpAt, LocalDateTime createdAt) {
    }

    public record ComplaintRecord(
            String reviewId, String complaintId, String orderId, String serviceName,
            String complainantName, String status, String storedContent,
            String handleResult, LocalDateTime createdAt) {
    }

    public record ReviewRecord(
            String reviewId, String orderId, String serviceName, String elderName,
            String reviewerName, String reviewerRole, Integer rating, Integer satisfaction,
            String storedContent, LocalDateTime createdAt) {
    }

    public record AppealRecord(
            String appealId, String nurseId, String nurseName, String targetType,
            String targetId, String reason, String fileIdsJson, String status,
            BigDecimal scoreAdjustment, String reviewComment, LocalDateTime createdAt) {
    }
}
