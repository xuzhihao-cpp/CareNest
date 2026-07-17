package com.csu.carenest.careadmin.score;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/** 阶段47-48评分事实查询和可审计写入。 */
@Repository
public class Phase47To48ScoreRepository {

    private final JdbcTemplate jdbcTemplate;

    public Phase47To48ScoreRepository(JdbcTemplate jdbcTemplate) {
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

    public boolean nurseExists(String nurseId) {
        return count("SELECT COUNT(*) FROM nurse_profile WHERE nurse_id=?", nurseId) > 0;
    }

    public void lockNurse(String nurseId) {
        jdbcTemplate.queryForObject(
                "SELECT nurse_id FROM nurse_profile WHERE nurse_id=? FOR UPDATE",
                String.class, nurseId);
    }

    public Optional<BigDecimal> currentScore(String nurseId) {
        List<BigDecimal> rows = jdbcTemplate.query(
                "SELECT total_score FROM nurse_score WHERE nurse_id=?",
                (rs, rowNum) -> rs.getBigDecimal("total_score"), nurseId);
        return rows.stream().findFirst();
    }

    public ScoreFacts calculateFacts(String nurseId) {
        BigDecimal metricDeduction = decimal("""
                SELECT COALESCE(SUM(CASE
                  WHEN omi.metric_status IN ('MISSING','EXEMPT_REJECTED') THEN omi.score_weight
                  ELSE 0 END),0)
                FROM order_metric_item omi
                JOIN nurse_task nt ON nt.order_id=omi.order_id
                WHERE nt.nurse_id=?
                """, nurseId);
        BigDecimal complaintRule = decimal("""
                SELECT ABS(COALESCE(MIN(score_delta),-5)) FROM metric_score_rule
                WHERE rule_type='COMPLAINT' AND enabled=1
                """);
        int complaintCount = count("""
                SELECT COUNT(*) FROM complaint c
                JOIN nurse_task nt ON nt.order_id=c.order_id
                WHERE nt.nurse_id=? AND c.complaint_status='RESOLVED'
                """, nurseId);
        BigDecimal appealAdjustment = decimal("""
                SELECT COALESCE(SUM(score_adjustment),0) FROM nurse_appeal
                WHERE nurse_id=? AND appeal_status='APPROVED'
                """, nurseId);
        int serviceCount = count("""
                SELECT COUNT(*) FROM nurse_task nt JOIN nursing_order o ON o.order_id=nt.order_id
                WHERE nt.nurse_id=? AND o.order_status='COMPLETED'
                """, nurseId);
        BigDecimal positiveRate = decimal("""
                SELECT CASE WHEN COUNT(r.review_id)=0 THEN NULL
                  ELSE ROUND(SUM(CASE WHEN r.rating>=4 THEN 1 ELSE 0 END)*100.0/COUNT(r.review_id),2) END
                FROM review r JOIN nurse_task nt ON nt.order_id=r.order_id
                WHERE nt.nurse_id=?
                """, nurseId);
        LocalDateTime lastServiceAt = jdbcTemplate.queryForObject("""
                SELECT MAX(nt.completed_at) FROM nurse_task nt WHERE nt.nurse_id=?
                """, (rs, rowNum) -> {
            Timestamp value = rs.getTimestamp(1);
            return value == null ? null : value.toLocalDateTime();
        }, nurseId);
        BigDecimal score = BigDecimal.valueOf(100)
                .subtract(metricDeduction)
                .subtract(complaintRule.multiply(BigDecimal.valueOf(complaintCount)))
                .add(appealAdjustment)
                .max(BigDecimal.ZERO)
                .min(BigDecimal.valueOf(100))
                .setScale(2, java.math.RoundingMode.HALF_UP);
        return new ScoreFacts(
                score, serviceCount, positiveRate, complaintCount, lastServiceAt,
                metricDeduction, complaintRule, appealAdjustment);
    }

    public void saveScore(String nurseId, ScoreFacts facts, String userId) {
        if (currentScore(nurseId).isPresent()) {
            jdbcTemplate.update("""
                    UPDATE nurse_score SET total_score=?,service_count=?,positive_rate=?,
                      complaint_count=?,last_service_at=?,updated_by=? WHERE nurse_id=?
                    """, facts.totalScore(), facts.serviceCount(), facts.positiveRate(),
                    facts.complaintCount(), facts.lastServiceAt(), userId, nurseId);
        } else {
            jdbcTemplate.update("""
                    INSERT INTO nurse_score
                      (nurse_id,total_score,service_count,positive_rate,complaint_count,last_service_at,updated_by)
                    VALUES (?,?,?,?,?,?,?)
                    """, nurseId, facts.totalScore(), facts.serviceCount(), facts.positiveRate(),
                    facts.complaintCount(), facts.lastServiceAt(), userId);
        }
    }

    public void insertChangeLog(
            String logId, String nurseId, String sourceType, String sourceId,
            BigDecimal before, BigDecimal after, String reason, String userId) {
        jdbcTemplate.update("""
                INSERT INTO nurse_score_change_log
                  (change_log_id,nurse_id,source_event_type,source_event_id,before_score,
                   after_score,score_delta,reason,changed_by)
                VALUES (?,?,?,?,?,?,?,?,?)
                """, logId, nurseId, sourceType, sourceId, before, after,
                after.subtract(before), reason, userId);
    }

    public List<ScoreDtos.ScoreChangeItem> findLogs(String nurseId, int offset, int size) {
        return jdbcTemplate.query("""
                SELECT change_log_id,source_event_type,source_event_id,before_score,
                       after_score,score_delta,reason,created_at
                FROM nurse_score_change_log WHERE nurse_id=?
                ORDER BY created_at DESC,change_log_id DESC LIMIT ? OFFSET ?
                """, (rs, rowNum) -> {
            String sourceType = rs.getString("source_event_type");
            String sourceId = rs.getString("source_event_id");
            String targetType = switch (sourceType) {
                case "COMPLAINT" -> "COMPLAINT";
                case "METRIC", "METRIC_CHECK", "EXCEPTION_PROOF_REVIEW" -> "METRIC";
                default -> null;
            };
            return new ScoreDtos.ScoreChangeItem(
                    rs.getString("change_log_id"), sourceType, sourceId,
                    rs.getBigDecimal("before_score"), rs.getBigDecimal("after_score"),
                    rs.getBigDecimal("score_delta"), rs.getString("reason"),
                    rs.getTimestamp("created_at").toLocalDateTime(), targetType,
                    targetType == null ? null : sourceId);
        }, nurseId, size, offset);
    }

    public BigDecimal monthDelta(String nurseId, LocalDateTime monthStart) {
        return decimal("""
                SELECT COALESCE(SUM(score_delta),0) FROM nurse_score_change_log
                WHERE nurse_id=? AND created_at>=?
                """, nurseId, monthStart);
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
        Integer value = jdbcTemplate.queryForObject(sql, Integer.class, args);
        return value == null ? 0 : value;
    }

    private BigDecimal decimal(String sql, Object... args) {
        BigDecimal value = jdbcTemplate.queryForObject(sql, BigDecimal.class, args);
        return value == null ? BigDecimal.ZERO : value;
    }

    public record ScoreFacts(
            BigDecimal totalScore,
            int serviceCount,
            BigDecimal positiveRate,
            int complaintCount,
            LocalDateTime lastServiceAt,
            BigDecimal metricDeduction,
            BigDecimal complaintRule,
            BigDecimal appealAdjustment) {
    }
}
