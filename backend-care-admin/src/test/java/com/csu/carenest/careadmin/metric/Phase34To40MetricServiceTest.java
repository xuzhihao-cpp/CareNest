package com.csu.carenest.careadmin.metric;

import com.csu.carenest.careadmin.auth.CurrentUser;
import com.csu.carenest.careadmin.auth.RoleCode;
import com.csu.carenest.careadmin.common.BusinessRuleException;
import com.csu.carenest.careadmin.common.ForbiddenException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 使用 H2 MySQL 兼容模式验证阶段34-40真实 SQL 与完整状态链。 */
class Phase34To40MetricServiceTest {

    private static final CurrentUser ADMIN = new CurrentUser("admin_1", List.of(RoleCode.ADMIN));
    private static final CurrentUser NURSE = new CurrentUser("nurse_1", List.of(RoleCode.NURSE));
    private static final CurrentUser OTHER_NURSE = new CurrentUser("nurse_2", List.of(RoleCode.NURSE));
    private static final CurrentUser FAMILY = new CurrentUser("family_1", List.of(RoleCode.FAMILY));

    private JdbcTemplate jdbcTemplate;
    private Phase34To40MetricService service;

    @BeforeEach
    void setUp() {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:" + UUID.randomUUID()
                + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1;NON_KEYWORDS=REQUIRED");
        ResourceDatabasePopulator populator = new ResourceDatabasePopulator(
                new ClassPathResource("phase34-40-schema.sql"),
                new ClassPathResource("phase34-40-data.sql"));
        populator.execute(dataSource);
        jdbcTemplate = new JdbcTemplate(dataSource);
        service = new Phase34To40MetricService(
                new Phase34To40MetricRepository(jdbcTemplate), new ObjectMapper());
    }

    @Test
    void newConfigVersionDoesNotRewriteGeneratedChecklistSnapshot() {
        saveDefaultConfig(new BigDecimal("10.00"));
        CareMetricDtos.MetricChecklistResponse first = service.generateChecklist(ADMIN, "order_1");
        service.generateChecklist(ADMIN, "order_1");
        service.saveMetricConfig(ADMIN, "service_1", configRequest(new BigDecimal("25.00")));

        assertEquals(2, service.metricConfig(ADMIN, "service_1").configVersion());
        assertEquals(2, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM care_metric_config", Integer.class));
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM order_metric_checklist", Integer.class));
        assertEquals(first.items().stream().map(CareMetricDtos.MetricChecklistItem::itemId).toList(),
                service.checklist(NURSE, "order_1").items().stream()
                        .map(CareMetricDtos.MetricChecklistItem::itemId).toList());
        assertEquals(new BigDecimal("10.00"), jdbcTemplate.queryForObject(
                "SELECT score_weight FROM order_metric_item WHERE metric_code = 'SERVICE_PHOTO'",
                BigDecimal.class));
    }

    @Test
    void approvedEvidencePassesMetricCheckAndWritesReviewHistory() {
        saveDefaultConfig(new BigDecimal("10.00"));
        service.generateChecklist(ADMIN, "order_1");
        String metricItemId = photoMetricItemId();
        jdbcTemplate.update("UPDATE nursing_order SET order_status = 'SERVING' WHERE order_id = 'order_1'");
        jdbcTemplate.update("UPDATE nurse_task SET task_status = 'SERVING' WHERE order_id = 'order_1'");

        CareMetricDtos.EvidenceResponse evidence = service.submitEvidence(
                NURSE, "order_1",
                new CareMetricDtos.EvidenceRequest(metricItemId, "file_1", "PHOTO", "服务照片"));
        service.reviewEvidence(ADMIN, evidence.evidenceId(),
                new CareMetricDtos.EvidenceReviewRequest("APPROVED", "材料有效"));
        jdbcTemplate.update("UPDATE nursing_order SET order_status = 'WAIT_REPORT' WHERE order_id = 'order_1'");
        jdbcTemplate.update("UPDATE nurse_task SET task_status = 'COMPLETED' WHERE order_id = 'order_1'");

        CareMetricDtos.MetricCheckResponse response = service.checkMetrics(NURSE, "order_1");

        assertTrue(response.items().stream().allMatch(item -> "PASS".equals(item.checkResult())));
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM evidence_review_record WHERE to_status = 'APPROVED'", Integer.class));
        assertEquals(2, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM nurse_metric_record WHERE metric_status = 'PASS'", Integer.class));
    }

    @Test
    void approvedExceptionProofRemovesDeductionInSameWorkflow() {
        String metricItemId = prepareMissingMetric();
        CareMetricDtos.ExceptionProofResponse proof = service.submitExceptionProof(
                NURSE, metricItemId,
                new CareMetricDtos.ExceptionProofRequest(
                        "ELDER_REFUSED", "长辈当次拒绝拍照", List.of("file_1")));

        CareMetricDtos.ProofReviewResponse reviewed = service.reviewExceptionProof(
                ADMIN, proof.proofId(),
                new CareMetricDtos.ProofReviewRequest(
                        "APPROVED", "证明有效", "NO_DEDUCTION"));

        assertEquals("APPROVED", reviewed.reviewStatus());
        assertEquals("EXEMPT_APPROVED", jdbcTemplate.queryForObject(
                "SELECT metric_status FROM order_metric_item WHERE order_metric_item_id = ?",
                String.class, metricItemId));
        assertEquals(new BigDecimal("0.00"), jdbcTemplate.queryForObject(
                "SELECT score_delta FROM nurse_metric_record WHERE source_type = 'EXCEPTION_PROOF_REVIEW'",
                BigDecimal.class));
    }

    @Test
    void rejectedExceptionProofKeepsConfiguredDeduction() {
        String metricItemId = prepareMissingMetric();
        CareMetricDtos.ExceptionProofResponse proof = service.submitExceptionProof(
                NURSE, metricItemId,
                new CareMetricDtos.ExceptionProofRequest(
                        "FORGOT", "护理人员忘记拍照", List.of()));

        service.reviewExceptionProof(ADMIN, proof.proofId(),
                new CareMetricDtos.ProofReviewRequest("REJECTED", "无有效材料", "DEDUCT"));

        assertEquals("EXEMPT_REJECTED", jdbcTemplate.queryForObject(
                "SELECT metric_status FROM order_metric_item WHERE order_metric_item_id = ?",
                String.class, metricItemId));
        assertEquals(new BigDecimal("-10.00"), jdbcTemplate.queryForObject(
                "SELECT score_delta FROM nurse_metric_record WHERE source_type = 'EXCEPTION_PROOF_REVIEW'",
                BigDecimal.class));
    }

    @Test
    void accessAndFrozenDecisionRulesAreEnforced() {
        saveDefaultConfig(new BigDecimal("10.00"));
        service.generateChecklist(ADMIN, "order_1");

        assertThrows(ForbiddenException.class, () -> service.checklist(OTHER_NURSE, "order_1"));
        assertEquals(2, service.metricCheckResult(FAMILY, "order_1").items().size());
        jdbcTemplate.update("DELETE FROM role_permission WHERE permission_id = 'perm_review'");
        assertThrows(ForbiddenException.class, () -> service.pendingEvidences(ADMIN));
    }

    @Test
    void inconsistentProofReviewDecisionIsRejected() {
        String metricItemId = prepareMissingMetric();
        CareMetricDtos.ExceptionProofResponse proof = service.submitExceptionProof(
                NURSE, metricItemId,
                new CareMetricDtos.ExceptionProofRequest("OTHER", "客观原因", List.of()));

        assertThrows(BusinessRuleException.class, () -> service.reviewExceptionProof(
                ADMIN, proof.proofId(),
                new CareMetricDtos.ProofReviewRequest("APPROVED", "", "DEDUCT")));
    }

    private String prepareMissingMetric() {
        saveDefaultConfig(new BigDecimal("10.00"));
        service.generateChecklist(ADMIN, "order_1");
        jdbcTemplate.update("UPDATE nursing_order SET order_status = 'WAIT_REPORT' WHERE order_id = 'order_1'");
        jdbcTemplate.update("UPDATE nurse_task SET task_status = 'COMPLETED' WHERE order_id = 'order_1'");
        service.checkMetrics(NURSE, "order_1");
        return photoMetricItemId();
    }

    private String photoMetricItemId() {
        return jdbcTemplate.queryForObject(
                "SELECT order_metric_item_id FROM order_metric_item WHERE metric_code = 'SERVICE_PHOTO'",
                String.class);
    }

    private void saveDefaultConfig(BigDecimal photoWeight) {
        service.saveMetricConfig(ADMIN, "service_1", configRequest(photoWeight));
    }

    private CareMetricDtos.CareMetricConfigRequest configRequest(BigDecimal photoWeight) {
        return new CareMetricDtos.CareMetricConfigRequest(List.of(
                new CareMetricDtos.CareMetricConfigItem(
                        "SERVICE_PHOTO", "服务照片", "SERVICE_PROCESS", true,
                        "PHOTO", photoWeight, "服务中必须拍照"),
                new CareMetricDtos.CareMetricConfigItem(
                        "POST_CONFIRM", "服务后确认", "POST_SERVICE", true,
                        "NONE", new BigDecimal("5.00"), "服务后确认")));
    }
}
