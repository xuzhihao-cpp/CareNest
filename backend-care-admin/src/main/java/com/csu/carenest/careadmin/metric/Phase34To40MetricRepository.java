package com.csu.carenest.careadmin.metric;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/** 阶段34-40数据访问层，只使用成员1冻结的表和字段。 */
@Repository
public class Phase34To40MetricRepository {

    private final JdbcTemplate jdbcTemplate;

    public Phase34To40MetricRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
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

    public boolean serviceExists(String serviceId) {
        return count("SELECT COUNT(*) FROM service_item WHERE service_id = ?", serviceId) > 0;
    }

    public void lockService(String serviceId) {
        jdbcTemplate.queryForObject(
                "SELECT service_id FROM service_item WHERE service_id = ? FOR UPDATE",
                String.class, serviceId);
    }

    public Optional<ConfigContext> findActiveConfig(String serviceId) {
        List<ConfigContext> rows = jdbcTemplate.query("""
                SELECT config_id, service_id, config_version
                FROM care_metric_config
                WHERE service_id = ? AND config_status = 'ACTIVE'
                ORDER BY config_version DESC
                """, (rs, rowNum) -> new ConfigContext(
                rs.getString("config_id"), rs.getString("service_id"),
                rs.getInt("config_version")), serviceId);
        return rows.stream().findFirst();
    }

    public int nextConfigVersion(String serviceId) {
        Integer version = jdbcTemplate.queryForObject("""
                SELECT COALESCE(MAX(config_version), 0) + 1
                FROM care_metric_config
                WHERE service_id = ?
                """, Integer.class, serviceId);
        return version == null ? 1 : version;
    }

    public void deactivateConfigs(String serviceId) {
        jdbcTemplate.update("""
                UPDATE care_metric_config SET config_status = 'INACTIVE'
                WHERE service_id = ? AND config_status = 'ACTIVE'
                """, serviceId);
    }

    public void insertConfig(String configId, String serviceId, int version, String userId) {
        jdbcTemplate.update("""
                INSERT INTO care_metric_config
                  (config_id, service_id, config_version, config_status, created_by)
                VALUES (?, ?, ?, 'ACTIVE', ?)
                """, configId, serviceId, version, userId);
    }

    public void insertConfigItem(
            String itemId, String configId, String serviceId,
            CareMetricDtos.CareMetricConfigItem item, int sort) {
        jdbcTemplate.update("""
                INSERT INTO care_metric_item
                  (metric_item_id, config_id, service_id, metric_code, metric_name,
                   metric_type, required, evidence_type, expected_action, score_weight,
                   description, sort, enabled)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, NULL, ?, ?, ?, 1)
                """, itemId, configId, serviceId, item.metricCode(), item.metricName(),
                item.metricType(), item.required(), item.evidenceType(), item.scoreWeight(),
                item.description(), sort);
    }

    public void insertMissingScoreRule(String ruleId, String metricItemId, BigDecimal scoreDelta) {
        jdbcTemplate.update("""
                INSERT INTO metric_score_rule
                  (score_rule_id, metric_item_id, rule_type, score_delta, description, enabled)
                VALUES (?, ?, 'MISSING', ?, 'Required metric missing', 1)
                """, ruleId, metricItemId, scoreDelta);
    }

    public Optional<OrderContext> findOrderContext(String orderId) {
        List<OrderContext> rows = jdbcTemplate.query("""
                SELECT o.order_id, o.service_id, o.elder_id, o.family_id, o.order_status,
                       nt.task_id, nt.nurse_id, nt.task_status
                FROM nursing_order o
                JOIN nurse_task nt ON nt.order_id = o.order_id
                WHERE o.order_id = ?
                """, (rs, rowNum) -> new OrderContext(
                rs.getString("order_id"), rs.getString("service_id"),
                rs.getString("elder_id"), rs.getString("family_id"),
                rs.getString("order_status"), rs.getString("task_id"),
                rs.getString("nurse_id"), rs.getString("task_status")), orderId);
        return rows.stream().findFirst();
    }

    public void lockOrder(String orderId) {
        jdbcTemplate.queryForObject(
                "SELECT order_id FROM nursing_order WHERE order_id = ? FOR UPDATE",
                String.class, orderId);
    }

    public boolean familyCanView(String familyId, String elderId) {
        return count("""
                SELECT COUNT(*) FROM elder_family_binding
                WHERE family_id = ? AND elder_id = ? AND binding_status = 'ACTIVE'
                  AND scope_codes LIKE '%\"REPORT_VIEW\"%'
                """, familyId, elderId) > 0;
    }

    public Optional<String> findChecklistId(String orderId) {
        List<String> rows = jdbcTemplate.query(
                "SELECT checklist_id FROM order_metric_checklist WHERE order_id = ?",
                (rs, rowNum) -> rs.getString("checklist_id"), orderId);
        return rows.stream().findFirst();
    }

    public void insertChecklist(
            String checklistId, OrderContext order, ConfigContext config, String userId) {
        jdbcTemplate.update("""
                INSERT INTO order_metric_checklist
                  (checklist_id, order_id, service_id, config_id, config_version, generated_by)
                VALUES (?, ?, ?, ?, ?, ?)
                """, checklistId, order.orderId(), order.serviceId(), config.configId(),
                config.version(), userId);
    }

    public List<ConfigMetricItem> findEnabledConfigItems(String configId) {
        return jdbcTemplate.query("""
                SELECT metric_item_id, metric_code, metric_name, required, evidence_type,
                       expected_action, score_weight
                FROM care_metric_item
                WHERE config_id = ? AND enabled = 1
                ORDER BY sort, metric_item_id
                """, (rs, rowNum) -> new ConfigMetricItem(
                rs.getString("metric_item_id"), rs.getString("metric_code"),
                rs.getString("metric_name"), rs.getBoolean("required"),
                rs.getString("evidence_type"), rs.getString("expected_action"),
                rs.getBigDecimal("score_weight")), configId);
    }

    public void insertOrderMetricItem(
            String orderMetricItemId, String checklistId, String orderId, ConfigMetricItem item) {
        jdbcTemplate.update("""
                INSERT INTO order_metric_item
                  (order_metric_item_id, checklist_id, order_id, metric_item_id,
                   metric_code, metric_name, required, evidence_type, score_weight, metric_status)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 'PENDING')
                """, orderMetricItemId, checklistId, orderId, item.metricItemId(),
                item.metricCode(), item.metricName(), item.required(), item.evidenceType(),
                item.scoreWeight());
    }

    public List<CareMetricDtos.MetricChecklistItem> findChecklistItems(String orderId) {
        return jdbcTemplate.query("""
                SELECT omi.order_metric_item_id, omi.metric_code, omi.required,
                       omi.evidence_type, cmi.expected_action, omi.metric_status, omi.score_weight
                FROM order_metric_item omi
                JOIN care_metric_item cmi ON cmi.metric_item_id = omi.metric_item_id
                WHERE omi.order_id = ?
                ORDER BY omi.created_at, omi.order_metric_item_id
                """, (rs, rowNum) -> new CareMetricDtos.MetricChecklistItem(
                rs.getString("order_metric_item_id"), rs.getString("metric_code"),
                rs.getBoolean("required"), rs.getString("evidence_type"),
                rs.getString("expected_action"), rs.getString("metric_status"),
                rs.getBigDecimal("score_weight")), orderId);
    }

    public Optional<MetricItemContext> findMetricItem(String metricItemId) {
        List<MetricItemContext> rows = jdbcTemplate.query("""
                SELECT omi.order_metric_item_id, omi.order_id, omi.metric_name,
                       omi.required, omi.evidence_type, omi.score_weight, omi.metric_status,
                       nt.nurse_id, nt.task_id, nt.task_status,
                       o.elder_id, o.family_id, o.order_status
                FROM order_metric_item omi
                JOIN nursing_order o ON o.order_id = omi.order_id
                JOIN nurse_task nt ON nt.order_id = omi.order_id
                WHERE omi.order_metric_item_id = ?
                """, (rs, rowNum) -> new MetricItemContext(
                rs.getString("order_metric_item_id"), rs.getString("order_id"),
                rs.getString("metric_name"), rs.getBoolean("required"),
                rs.getString("evidence_type"), rs.getBigDecimal("score_weight"),
                rs.getString("metric_status"), rs.getString("nurse_id"),
                rs.getString("task_id"), rs.getString("task_status"),
                rs.getString("elder_id"), rs.getString("family_id"),
                rs.getString("order_status")), metricItemId);
        return rows.stream().findFirst();
    }

    public Optional<FileAsset> findFile(String fileId) {
        List<FileAsset> rows = jdbcTemplate.query("""
                SELECT file_id, uploaded_by, mime_type FROM file_asset WHERE file_id = ?
                """, (rs, rowNum) -> new FileAsset(
                rs.getString("file_id"), rs.getString("uploaded_by"),
                rs.getString("mime_type")), fileId);
        return rows.stream().findFirst();
    }

    public void insertEvidence(
            String evidenceId, OrderContext order, String metricItemId,
            String nurseId, String fileId, String evidenceType, String description) {
        jdbcTemplate.update("""
                INSERT INTO care_service_evidence
                  (evidence_id, order_id, task_id, order_metric_item_id, nurse_id,
                   file_id, evidence_type, description, audit_status)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'PENDING')
                """, evidenceId, order.orderId(), order.taskId(), metricItemId,
                nurseId, fileId, evidenceType, description);
    }

    public List<CareMetricDtos.EvidenceResponse> findEvidences(String orderId) {
        return jdbcTemplate.query("""
                SELECT evidence_id, audit_status FROM care_service_evidence
                WHERE order_id = ? ORDER BY submitted_at, evidence_id
                """, (rs, rowNum) -> new CareMetricDtos.EvidenceResponse(
                rs.getString("evidence_id"), rs.getString("audit_status")), orderId);
    }

    public List<CareMetricDtos.EvidenceResponse> findPendingEvidences() {
        return jdbcTemplate.query("""
                SELECT evidence_id, audit_status FROM care_service_evidence
                WHERE audit_status = 'PENDING' ORDER BY submitted_at, evidence_id
                """, (rs, rowNum) -> new CareMetricDtos.EvidenceResponse(
                rs.getString("evidence_id"), rs.getString("audit_status")));
    }

    public Optional<EvidenceContext> findEvidence(String evidenceId) {
        List<EvidenceContext> rows = jdbcTemplate.query("""
                SELECT evidence_id, order_id, order_metric_item_id, nurse_id, audit_status
                FROM care_service_evidence WHERE evidence_id = ?
                """, (rs, rowNum) -> new EvidenceContext(
                rs.getString("evidence_id"), rs.getString("order_id"),
                rs.getString("order_metric_item_id"), rs.getString("nurse_id"),
                rs.getString("audit_status")), evidenceId);
        return rows.stream().findFirst();
    }

    public int reviewEvidenceIfPending(
            String evidenceId, String status, String comment, String reviewerId) {
        return jdbcTemplate.update("""
                UPDATE care_service_evidence
                SET audit_status = ?, reviewed_by = ?, reviewed_at = CURRENT_TIMESTAMP,
                    review_comment = ?
                WHERE evidence_id = ? AND audit_status = 'PENDING'
                """, status, reviewerId, comment, evidenceId);
    }

    public void insertEvidenceReview(
            String recordId, String evidenceId, String fromStatus,
            String toStatus, String comment, String reviewerId) {
        jdbcTemplate.update("""
                INSERT INTO evidence_review_record
                  (review_record_id, evidence_id, from_status, to_status, review_comment, reviewer_id)
                VALUES (?, ?, ?, ?, ?, ?)
                """, recordId, evidenceId, fromStatus, toStatus, comment, reviewerId);
    }

    public int countApprovedEvidence(String metricItemId) {
        return count("""
                SELECT COUNT(*) FROM care_service_evidence
                WHERE order_metric_item_id = ? AND audit_status = 'APPROVED'
                """, metricItemId);
    }

    public void updateMetricStatus(String metricItemId, String status) {
        jdbcTemplate.update("""
                UPDATE order_metric_item
                SET metric_status = ?,
                    submitted_at = CASE WHEN ? = 'SUBMITTED' THEN CURRENT_TIMESTAMP ELSE submitted_at END,
                    reviewed_at = CASE WHEN ? IN ('PASS','MISSING','EXEMPT_APPROVED','EXEMPT_REJECTED')
                                       THEN CURRENT_TIMESTAMP ELSE reviewed_at END
                WHERE order_metric_item_id = ?
                """, status, status, status, metricItemId);
    }

    public List<MetricItemContext> findOrderMetricItems(String orderId) {
        return jdbcTemplate.query("""
                SELECT omi.order_metric_item_id, omi.order_id, omi.metric_name,
                       omi.required, omi.evidence_type, omi.score_weight, omi.metric_status,
                       nt.nurse_id, nt.task_id, nt.task_status,
                       o.elder_id, o.family_id, o.order_status
                FROM order_metric_item omi
                JOIN nursing_order o ON o.order_id = omi.order_id
                JOIN nurse_task nt ON nt.order_id = omi.order_id
                WHERE omi.order_id = ?
                ORDER BY omi.created_at, omi.order_metric_item_id
                """, (rs, rowNum) -> new MetricItemContext(
                rs.getString("order_metric_item_id"), rs.getString("order_id"),
                rs.getString("metric_name"), rs.getBoolean("required"),
                rs.getString("evidence_type"), rs.getBigDecimal("score_weight"),
                rs.getString("metric_status"), rs.getString("nurse_id"),
                rs.getString("task_id"), rs.getString("task_status"),
                rs.getString("elder_id"), rs.getString("family_id"),
                rs.getString("order_status")), orderId);
    }

    public void insertMetricRecord(
            String recordId, MetricItemContext item, String status,
            BigDecimal scoreDelta, String sourceType, String sourceId) {
        jdbcTemplate.update("""
                INSERT INTO nurse_metric_record
                  (metric_record_id, nurse_id, order_id, order_metric_item_id,
                   metric_status, score_delta, source_type, source_id)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """, recordId, item.nurseId(), item.orderId(), item.metricItemId(),
                status, scoreDelta, sourceType, sourceId);
    }

    public boolean hasMetricRecord(
            String metricItemId, String status, BigDecimal scoreDelta,
            String sourceType, String sourceId) {
        return count("""
                SELECT COUNT(*) FROM nurse_metric_record
                WHERE order_metric_item_id = ? AND metric_status = ? AND score_delta = ?
                  AND source_type = ? AND source_id = ?
                """, metricItemId, status, scoreDelta, sourceType, sourceId) > 0;
    }

    public boolean hasPendingProof(String metricItemId) {
        return count("""
                SELECT COUNT(*) FROM metric_exception_proof
                WHERE order_metric_item_id = ? AND proof_status = 'PENDING'
                """, metricItemId) > 0;
    }

    public void insertProof(
            String proofId, String metricItemId, String evidenceId,
            String nurseId, String reasonType, String reason) {
        jdbcTemplate.update("""
                INSERT INTO metric_exception_proof
                  (proof_id, order_metric_item_id, evidence_id, nurse_id,
                   reason_type, reason, proof_status)
                VALUES (?, ?, ?, ?, ?, ?, 'PENDING')
                """, proofId, metricItemId, evidenceId, nurseId, reasonType, reason);
    }

    public List<CareMetricDtos.ExceptionProofResponse> findExceptionProofs(String orderId) {
        return jdbcTemplate.query("""
                SELECT p.proof_id, p.proof_status
                FROM metric_exception_proof p
                JOIN order_metric_item omi ON omi.order_metric_item_id = p.order_metric_item_id
                WHERE omi.order_id = ?
                ORDER BY p.created_at, p.proof_id
                """, (rs, rowNum) -> new CareMetricDtos.ExceptionProofResponse(
                rs.getString("proof_id"), rs.getString("proof_status")), orderId);
    }

    public List<CareMetricDtos.ProofReviewResponse> findPendingProofs() {
        return jdbcTemplate.query("""
                SELECT proof_id, proof_status FROM metric_exception_proof
                WHERE proof_status = 'PENDING' ORDER BY created_at, proof_id
                """, (rs, rowNum) -> new CareMetricDtos.ProofReviewResponse(
                rs.getString("proof_id"), rs.getString("proof_status"), null));
    }

    public Optional<ProofContext> findProof(String proofId) {
        List<ProofContext> rows = jdbcTemplate.query("""
                SELECT p.proof_id, p.proof_status, p.order_metric_item_id,
                       omi.order_id, omi.metric_name, omi.required, omi.evidence_type,
                       omi.score_weight, omi.metric_status, nt.nurse_id, nt.task_id,
                       nt.task_status, o.elder_id, o.family_id, o.order_status
                FROM metric_exception_proof p
                JOIN order_metric_item omi ON omi.order_metric_item_id = p.order_metric_item_id
                JOIN nursing_order o ON o.order_id = omi.order_id
                JOIN nurse_task nt ON nt.order_id = omi.order_id
                WHERE p.proof_id = ?
                """, (rs, rowNum) -> new ProofContext(
                rs.getString("proof_id"), rs.getString("proof_status"),
                new MetricItemContext(
                        rs.getString("order_metric_item_id"), rs.getString("order_id"),
                        rs.getString("metric_name"), rs.getBoolean("required"),
                        rs.getString("evidence_type"), rs.getBigDecimal("score_weight"),
                        rs.getString("metric_status"), rs.getString("nurse_id"),
                        rs.getString("task_id"), rs.getString("task_status"),
                        rs.getString("elder_id"), rs.getString("family_id"),
                        rs.getString("order_status"))), proofId);
        return rows.stream().findFirst();
    }

    public int reviewProofIfPending(String proofId, String status, String comment, String reviewerId) {
        return jdbcTemplate.update("""
                UPDATE metric_exception_proof
                SET proof_status = ?, review_comment = ?, reviewed_by = ?,
                    reviewed_at = CURRENT_TIMESTAMP
                WHERE proof_id = ? AND proof_status = 'PENDING'
                """, status, comment, reviewerId, proofId);
    }

    public void insertOperationLog(
            String logId, String operatorId, String roleCode,
            String operationType, String bizType, String bizId,
            String beforeValue, String afterValue, String traceId) {
        jdbcTemplate.update("""
                INSERT INTO operation_log
                  (log_id, operator_id, role_code, operation_type, biz_type, biz_id,
                   before_value, after_value, trace_id)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, logId, operatorId, roleCode, operationType, bizType, bizId,
                beforeValue, afterValue, traceId);
    }

    private int count(String sql, Object... args) {
        Integer value = jdbcTemplate.queryForObject(sql, Integer.class, args);
        return value == null ? 0 : value;
    }

    public record ConfigContext(String configId, String serviceId, int version) {
    }

    public record ConfigMetricItem(
            String metricItemId, String metricCode, String metricName,
            boolean required, String evidenceType, String expectedAction,
            BigDecimal scoreWeight) {
    }

    public record OrderContext(
            String orderId, String serviceId, String elderId, String familyId,
            String orderStatus, String taskId, String nurseId, String taskStatus) {
    }

    public record MetricItemContext(
            String metricItemId, String orderId, String metricName,
            boolean required, String evidenceType, BigDecimal scoreWeight,
            String metricStatus, String nurseId, String taskId, String taskStatus,
            String elderId, String familyId, String orderStatus) {
    }

    public record FileAsset(String fileId, String uploadedBy, String mimeType) {
    }

    public record EvidenceContext(
            String evidenceId, String orderId, String metricItemId,
            String nurseId, String auditStatus) {
    }

    public record ProofContext(String proofId, String proofStatus, MetricItemContext item) {
    }
}
