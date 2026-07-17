package com.csu.carenest.careadmin.delivery;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** 阶段51-55随访、真实统计和演示就绪数据访问。 */
@Repository
public class Phase51To55DeliveryRepository {

    private final JdbcTemplate jdbcTemplate;

    public Phase51To55DeliveryRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public boolean hasPermission(String userId, String permission) {
        return count("""
                SELECT COUNT(*) FROM user_role ur
                JOIN sys_role r ON r.role_id=ur.role_id AND r.enabled=1
                JOIN role_permission rp ON rp.role_id=r.role_id
                JOIN sys_permission p ON p.permission_id=rp.permission_id AND p.enabled=1
                WHERE ur.user_id=? AND p.permission_code=?
                """, userId, permission) > 0;
    }

    public boolean elderExists(String elderId) {
        return count("SELECT COUNT(*) FROM elder_profile WHERE elder_id=?", elderId) > 0;
    }

    public boolean orderBelongsToElder(String orderId, String elderId) {
        return count("SELECT COUNT(*) FROM nursing_order WHERE order_id=? AND elder_id=?", orderId, elderId) > 0;
    }

    public Optional<String> findActiveBindingScopes(String familyId, String elderId) {
        List<String> rows = jdbcTemplate.query("""
                SELECT scope_codes FROM elder_family_binding
                WHERE family_id=? AND elder_id=? AND binding_status='ACTIVE'
                ORDER BY binding_id DESC LIMIT 1
                """, (rs, rowNum) -> rs.getString("scope_codes"), familyId, elderId);
        return rows.stream().findFirst();
    }

    public List<DeliveryDtos.FollowUpRecordResponse> findFollowUps(String elderId) {
        return jdbcTemplate.query("""
                SELECT follow_up_id,elder_id,order_id,follow_up_type,content,next_follow_up_at,
                       need_reminder,created_reminder_task_id,created_at
                FROM follow_up_record WHERE elder_id=?
                ORDER BY created_at DESC,follow_up_id DESC
                """, (rs, rowNum) -> new DeliveryDtos.FollowUpRecordResponse(
                rs.getString("follow_up_id"), rs.getString("elder_id"), rs.getString("order_id"),
                rs.getString("follow_up_type"), rs.getString("content"),
                rs.getTimestamp("next_follow_up_at") == null ? null
                        : rs.getTimestamp("next_follow_up_at").toLocalDateTime(),
                rs.getBoolean("need_reminder"), rs.getString("created_reminder_task_id"),
                rs.getTimestamp("created_at").toLocalDateTime()), elderId);
    }

    public void insertFollowUp(
            String followUpId, DeliveryDtos.FollowUpRequest request, String userId) {
        jdbcTemplate.update("""
                INSERT INTO follow_up_record
                  (follow_up_id,elder_id,order_id,follow_up_type,content,next_follow_up_at,
                   need_reminder,created_by)
                VALUES (?,?,?,?,?,?,?,?)
                """, followUpId, request.elderId(), trim(request.orderId()),
                request.followUpType(), request.content().trim(), request.nextFollowUpAt(),
                request.needReminder(), userId);
    }

    public void insertReminder(
            String reminderId, String followUpId, DeliveryDtos.FollowUpRequest request, String userId) {
        jdbcTemplate.update("""
                INSERT INTO reminder_task
                  (task_id,elder_id,reminder_type,title,content,scheduled_at,
                   reminder_status,source_type,source_id,created_by)
                VALUES (?,?,'FOLLOW_UP','随访提醒',?,?,'PENDING','FOLLOW_UP',?,?)
                """, reminderId, request.elderId(), request.content().trim(),
                request.nextFollowUpAt(), followUpId, userId);
        jdbcTemplate.update("""
                UPDATE follow_up_record SET created_reminder_task_id=? WHERE follow_up_id=?
                """, reminderId, followUpId);
    }

    public DeliveryDtos.BasicStatisticsResponse basicStatistics(LocalDateTime from, LocalDateTime to) {
        long orders = countLong("SELECT COUNT(*) FROM nursing_order WHERE created_at>=? AND created_at<?", from, to);
        long completed = countLong("""
                SELECT COUNT(*) FROM nursing_order
                WHERE created_at>=? AND created_at<? AND order_status='COMPLETED'
                """, from, to);
        long reminderRecords = countLong("""
                SELECT COUNT(*) FROM reminder_record WHERE operated_at>=? AND operated_at<?
                """, from, to);
        long remindersDone = countLong("""
                SELECT COUNT(*) FROM reminder_record
                WHERE operated_at>=? AND operated_at<? AND result='DONE'
                """, from, to);
        long tickets = countLong("""
                SELECT COUNT(*) FROM customer_service_ticket WHERE created_at>=? AND created_at<?
                """, from, to);
        long handledTickets = countLong("""
                SELECT COUNT(*) FROM customer_service_ticket
                WHERE created_at>=? AND created_at<? AND ticket_status IN ('RESOLVED','CLOSED')
                """, from, to);
        BigDecimal satisfaction = decimal("""
                SELECT COALESCE(AVG(COALESCE(satisfaction,rating)),0) FROM review
                WHERE created_at>=? AND created_at<?
                """, from, to).setScale(2, RoundingMode.HALF_UP);
        Map<String, Object> cards = new LinkedHashMap<>();
        cards.put("orderCount", orders);
        cards.put("completedOrderCount", completed);
        cards.put("customerServiceTicketCount", tickets);
        cards.put("customerServiceHandleRate", rate(handledTickets, tickets));
        return new DeliveryDtos.BasicStatisticsResponse(
                cards, trend("""
                        SELECT DATE(created_at) metric_date,COUNT(*) metric_value
                        FROM nursing_order WHERE created_at>=? AND created_at<?
                        GROUP BY DATE(created_at) ORDER BY metric_date
                        """, from, to),
                rate(completed, orders), rate(remindersDone, reminderRecords), satisfaction);
    }

    public DeliveryDtos.QualityStatisticsResponse qualityStatistics(LocalDateTime from, LocalDateTime to) {
        long evidence = countLong("""
                SELECT COUNT(*) FROM care_service_evidence WHERE created_at>=? AND created_at<?
                """, from, to);
        long approvedEvidence = countLong("""
                SELECT COUNT(*) FROM care_service_evidence
                WHERE created_at>=? AND created_at<? AND audit_status='APPROVED'
                """, from, to);
        long metrics = countLong("""
                SELECT COUNT(*) FROM order_metric_item WHERE created_at>=? AND created_at<?
                """, from, to);
        long passedMetrics = countLong("""
                SELECT COUNT(*) FROM order_metric_item
                WHERE created_at>=? AND created_at<? AND metric_status IN ('PASS','EXEMPT_APPROVED')
                """, from, to);
        long proofs = countLong("""
                SELECT COUNT(*) FROM metric_exception_proof WHERE created_at>=? AND created_at<?
                """, from, to);
        long approvedProofs = countLong("""
                SELECT COUNT(*) FROM metric_exception_proof
                WHERE created_at>=? AND created_at<? AND proof_status='APPROVED'
                """, from, to);
        Map<String, Long> distribution = new LinkedHashMap<>();
        List<ScoreLevelCount> scoreLevels = jdbcTemplate.query("""
                SELECT CASE WHEN total_score>=90 THEN 'EXCELLENT'
                            WHEN total_score>=80 THEN 'GOOD' ELSE 'NEEDS_IMPROVEMENT' END score_level,
                       COUNT(*) score_count FROM nurse_score GROUP BY score_level ORDER BY score_level
                """, (rs, rowNum) -> new ScoreLevelCount(
                rs.getString("score_level"), rs.getLong("score_count")));
        scoreLevels.forEach(item -> distribution.put(item.level(), item.count()));
        return new DeliveryDtos.QualityStatisticsResponse(
                rate(approvedEvidence, evidence), rate(passedMetrics, metrics),
                rate(approvedProofs, proofs), distribution,
                trend("""
                        SELECT DATE(created_at) metric_date,
                               ROUND(SUM(CASE WHEN metric_status IN ('PASS','EXEMPT_APPROVED')
                                         THEN 1 ELSE 0 END)*100.0/COUNT(*),2) metric_value
                        FROM order_metric_item WHERE created_at>=? AND created_at<?
                        GROUP BY DATE(created_at) ORDER BY metric_date
                        """, from, to));
    }

    public DeliveryDtos.DemoDataStatusResponse demoStatus() {
        List<String> accounts = jdbcTemplate.query("""
                SELECT DISTINCT u.username FROM sys_user u
                JOIN user_role ur ON ur.user_id=u.user_id
                JOIN sys_role r ON r.role_id=ur.role_id
                WHERE u.account_status='ENABLED' AND
                  ((u.username='elder_demo' AND r.role_code='ELDER')
                   OR (u.username='family_demo' AND r.role_code='FAMILY')
                   OR (u.username='nurse_demo' AND r.role_code='NURSE')
                   OR (u.username='admin_demo' AND r.role_code='ADMIN')
                   OR (u.username='cs_demo' AND r.role_code='CUSTOMER_SERVICE'))
                ORDER BY u.username
                """, (rs, rowNum) -> rs.getString("username"));
        int scenarios = 0;
        scenarios += present("elder_family_binding");
        scenarios += present("nursing_order");
        scenarios += present("order_metric_item");
        scenarios += present("customer_service_ticket");
        scenarios += present("complaint");
        scenarios += present("nurse_appeal");
        scenarios += present("training_article");
        scenarios += present("follow_up_record");
        boolean ready = accounts.size() == 5 && scenarios == 8
                && count("""
                        SELECT COUNT(*) FROM bug_list
                        WHERE severity IN ('HIGH','CRITICAL') AND bug_status IN ('OPEN','PROCESSING')
                        """) == 0;
        return new DeliveryDtos.DemoDataStatusResponse(
                ready, accounts, scenarios, lastResetAt().orElse(null));
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

    private Optional<LocalDateTime> lastResetAt() {
        List<LocalDateTime> rows = jdbcTemplate.query("""
                SELECT created_at FROM operation_log WHERE operation_type='RESET_DEMO_DATA'
                ORDER BY created_at DESC LIMIT 1
                """, (rs, rowNum) -> rs.getTimestamp("created_at").toLocalDateTime());
        return rows.stream().findFirst();
    }

    private int present(String table) {
        return count("SELECT COUNT(*) FROM " + table) > 0 ? 1 : 0;
    }

    private List<DeliveryDtos.TrendPoint> trend(
            String sql, LocalDateTime from, LocalDateTime to) {
        return jdbcTemplate.query(sql, (rs, rowNum) -> new DeliveryDtos.TrendPoint(
                rs.getDate("metric_date").toLocalDate().toString(),
                rs.getBigDecimal("metric_value")), from, to);
    }

    private BigDecimal rate(long numerator, long denominator) {
        if (denominator == 0) {
            return BigDecimal.ZERO.setScale(2);
        }
        return BigDecimal.valueOf(numerator).multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(denominator), 2, RoundingMode.HALF_UP);
    }

    private int count(String sql, Object... args) {
        Integer value = jdbcTemplate.queryForObject(sql, Integer.class, args);
        return value == null ? 0 : value;
    }

    private long countLong(String sql, Object... args) {
        Long value = jdbcTemplate.queryForObject(sql, Long.class, args);
        return value == null ? 0 : value;
    }

    private BigDecimal decimal(String sql, Object... args) {
        BigDecimal value = jdbcTemplate.queryForObject(sql, BigDecimal.class, args);
        return value == null ? BigDecimal.ZERO : value;
    }

    private String trim(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private record ScoreLevelCount(String level, long count) {
    }
}
