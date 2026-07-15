package com.csu.carenest.careadmin.attention;

import com.csu.carenest.careadmin.auth.CurrentUser;
import com.csu.carenest.careadmin.auth.RoleCode;
import com.csu.carenest.careadmin.common.BusinessRuleException;
import com.csu.carenest.careadmin.common.ConflictException;
import com.csu.carenest.careadmin.common.ForbiddenException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 使用 MySQL 兼容模式验证阶段31真实 SQL、权限和幂等规则。 */
class Phase31AttentionServiceTest {

    private static final CurrentUser NURSE = new CurrentUser("nurse_1", List.of(RoleCode.NURSE));
    private static final CurrentUser OTHER_NURSE = new CurrentUser("nurse_2", List.of(RoleCode.NURSE));
    private static final CurrentUser ADMIN = new CurrentUser("admin_1", List.of(RoleCode.ADMIN));

    private JdbcTemplate jdbcTemplate;
    private Phase31AttentionService service;

    @BeforeEach
    void setUp() {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:" + UUID.randomUUID()
                + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        jdbcTemplate = new JdbcTemplate(dataSource);
        createSchema();
        seedData();
        service = new Phase31AttentionService(
                new Phase31AttentionRepository(jdbcTemplate), new ObjectMapper());
    }

    @Test
    void repeatedReadGeneratesOneActiveSnapshotPerSource() {
        AttentionNoticeDtos.AttentionNoticeResponse first = service.attentionNotices(NURSE, "order_1");
        AttentionNoticeDtos.AttentionNoticeResponse second = service.attentionNotices(NURSE, "order_1");

        assertEquals(5, first.items().size());
        assertEquals(first.items().stream().map(AttentionNoticeDtos.AttentionNoticeItem::noticeId).toList(),
                second.items().stream().map(AttentionNoticeDtos.AttentionNoticeItem::noticeId).toList());
        assertEquals(5, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM care_attention_notice", Integer.class));
        assertThrows(ForbiddenException.class,
                () -> service.attentionNotices(OTHER_NURSE, "order_1"));
    }

    @Test
    void acknowledgementIsIdempotentAndUnlocksServiceStart() {
        AttentionNoticeDtos.AttentionNoticeResponse generated = service.attentionNotices(NURSE, "order_1");
        List<String> requiredIds = generated.items().stream()
                .filter(AttentionNoticeDtos.AttentionNoticeItem::requiredAck)
                .map(AttentionNoticeDtos.AttentionNoticeItem::noticeId)
                .toList();

        assertEquals(3, requiredIds.size());
        assertThrows(BusinessRuleException.class,
                () -> service.requireAllRequiredAcknowledged("order_1", "task_1", "nurse_1"));

        AttentionNoticeDtos.AckRequest request = new AttentionNoticeDtos.AckRequest(requiredIds);
        service.acknowledge(NURSE, "order_1", request);
        AttentionNoticeDtos.AttentionNoticeResponse repeated = service.acknowledge(NURSE, "order_1", request);

        assertEquals(3, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM care_attention_ack", Integer.class));
        assertTrue(repeated.items().stream().filter(AttentionNoticeDtos.AttentionNoticeItem::requiredAck)
                .allMatch(AttentionNoticeDtos.AttentionNoticeItem::acknowledged));
        service.requireAllRequiredAcknowledged("order_1", "task_1", "nurse_1");
    }

    @Test
    void acknowledgementRequiresFrozenPermissionCode() {
        AttentionNoticeDtos.AttentionNoticeResponse generated = service.attentionNotices(NURSE, "order_1");
        jdbcTemplate.update("DELETE FROM role_permission WHERE role_id = 'role_nurse'");

        assertThrows(ForbiddenException.class, () -> service.acknowledge(
                NURSE, "order_1", new AttentionNoticeDtos.AckRequest(
                        List.of(generated.items().get(0).noticeId()))));
    }

    @Test
    void adminReadRequiresReviewPermission() {
        assertEquals(5, service.attentionNotices(ADMIN, "order_1").items().size());
        jdbcTemplate.update("DELETE FROM role_permission WHERE role_id = 'role_admin'");

        assertThrows(ForbiddenException.class,
                () -> service.attentionNotices(ADMIN, "order_1"));
    }

    @Test
    void changedArchiveSourceCreatesNewSnapshotAndCancelsOldOne() {
        service.attentionNotices(NURSE, "order_1");
        jdbcTemplate.update("UPDATE risk_tag SET remark = '夜间起身时必须搀扶' WHERE risk_tag_id = 'risk_1'");
        service.attentionNotices(NURSE, "order_1");

        assertEquals(5, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM care_attention_notice WHERE notice_status = 'ACTIVE'", Integer.class));
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM care_attention_notice WHERE notice_status = 'CANCELED'", Integer.class));
    }

    @Test
    void riskLevelChangeCreatesNewUnacknowledgedSnapshot() {
        AttentionNoticeDtos.AttentionNoticeResponse generated = service.attentionNotices(NURSE, "order_1");
        List<String> requiredIds = generated.items().stream()
                .filter(AttentionNoticeDtos.AttentionNoticeItem::requiredAck)
                .map(AttentionNoticeDtos.AttentionNoticeItem::noticeId)
                .toList();
        service.acknowledge(NURSE, "order_1", new AttentionNoticeDtos.AckRequest(requiredIds));

        jdbcTemplate.update("UPDATE risk_tag SET risk_level = 'HIGH' WHERE risk_tag_id = 'risk_1'");
        AttentionNoticeDtos.AttentionNoticeResponse refreshed = service.attentionNotices(NURSE, "order_1");

        AttentionNoticeDtos.AttentionNoticeItem risk = refreshed.items().stream()
                .filter(item -> item.content().contains("跌倒风险"))
                .findFirst().orElseThrow();
        assertEquals("CRITICAL", risk.level());
        assertFalse(risk.acknowledged());
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM care_attention_notice WHERE notice_status = 'CANCELED'", Integer.class));
        assertThrows(BusinessRuleException.class,
                () -> service.requireAllRequiredAcknowledged("order_1", "task_1", "nurse_1"));
    }

    @Test
    void deletedRiskSourceIsCanceledAndNoLongerBlocksStart() {
        AttentionNoticeDtos.AttentionNoticeResponse generated = service.attentionNotices(NURSE, "order_1");
        List<String> remainingRequiredIds = generated.items().stream()
                .filter(AttentionNoticeDtos.AttentionNoticeItem::requiredAck)
                .filter(item -> !item.content().contains("跌倒风险"))
                .map(AttentionNoticeDtos.AttentionNoticeItem::noticeId)
                .toList();
        service.acknowledge(NURSE, "order_1", new AttentionNoticeDtos.AckRequest(remainingRequiredIds));

        jdbcTemplate.update("DELETE FROM risk_tag WHERE risk_tag_id = 'risk_1'");
        AttentionNoticeDtos.AttentionNoticeResponse refreshed = service.attentionNotices(NURSE, "order_1");

        assertTrue(refreshed.items().stream().noneMatch(item -> item.content().contains("跌倒风险")));
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM care_attention_notice WHERE notice_status = 'CANCELED'", Integer.class));
        service.requireAllRequiredAcknowledged("order_1", "task_1", "nurse_1");
    }

    @Test
    void adminCanReviewExistingNoticesAfterServiceCompletion() {
        service.attentionNotices(ADMIN, "order_1");
        jdbcTemplate.update("UPDATE nursing_order SET order_status = 'COMPLETED' WHERE order_id = 'order_1'");
        jdbcTemplate.update("UPDATE nurse_task SET task_status = 'COMPLETED' WHERE task_id = 'task_1'");

        assertEquals(5, service.attentionNotices(ADMIN, "order_1").items().size());
        assertThrows(ConflictException.class,
                () -> service.attentionNotices(NURSE, "order_1"));
    }

    @Test
    void canceledOrderCannotAcknowledgeNotices() {
        AttentionNoticeDtos.AttentionNoticeResponse generated = service.attentionNotices(NURSE, "order_1");
        jdbcTemplate.update("UPDATE nursing_order SET order_status = 'CANCELED' WHERE order_id = 'order_1'");

        assertThrows(ConflictException.class, () -> service.acknowledge(
                NURSE, "order_1", new AttentionNoticeDtos.AckRequest(
                        List.of(generated.items().get(0).noticeId()))));
        assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM care_attention_ack", Integer.class));
    }

    @Test
    void reassignedNurseMustAcknowledgeRequiredNoticesAgain() {
        AttentionNoticeDtos.AttentionNoticeResponse generated = service.attentionNotices(NURSE, "order_1");
        List<String> requiredIds = generated.items().stream()
                .filter(AttentionNoticeDtos.AttentionNoticeItem::requiredAck)
                .map(AttentionNoticeDtos.AttentionNoticeItem::noticeId)
                .toList();
        service.acknowledge(NURSE, "order_1", new AttentionNoticeDtos.AckRequest(requiredIds));

        jdbcTemplate.update("UPDATE nurse_task SET nurse_id = 'nurse_2' WHERE task_id = 'task_1'");
        AttentionNoticeDtos.AttentionNoticeResponse reassigned = service.attentionNotices(OTHER_NURSE, "order_1");

        assertTrue(reassigned.items().stream()
                .filter(AttentionNoticeDtos.AttentionNoticeItem::requiredAck)
                .noneMatch(AttentionNoticeDtos.AttentionNoticeItem::acknowledged));
        assertThrows(BusinessRuleException.class,
                () -> service.requireAllRequiredAcknowledged("order_1", "task_1", "nurse_2"));
    }

    private void createSchema() {
        jdbcTemplate.execute("CREATE TABLE nursing_order (order_id VARCHAR(32) PRIMARY KEY, elder_id VARCHAR(32), service_id VARCHAR(32), order_status VARCHAR(32))");
        jdbcTemplate.execute("CREATE TABLE nurse_task (task_id VARCHAR(32) PRIMARY KEY, order_id VARCHAR(32), nurse_id VARCHAR(32), task_status VARCHAR(32), dispatch_remark VARCHAR(255))");
        jdbcTemplate.execute("CREATE TABLE service_item (service_id VARCHAR(32) PRIMARY KEY, service_name VARCHAR(128), service_desc VARCHAR(512))");
        jdbcTemplate.execute("CREATE TABLE health_archive (archive_id VARCHAR(32) PRIMARY KEY, elder_id VARCHAR(32), archive_version INT, care_summary VARCHAR(512))");
        jdbcTemplate.execute("CREATE TABLE allergy_record (allergy_id VARCHAR(32) PRIMARY KEY, elder_id VARCHAR(32), allergen VARCHAR(64), reaction VARCHAR(128), severity VARCHAR(32), remark VARCHAR(255))");
        jdbcTemplate.execute("CREATE TABLE risk_tag (risk_tag_id VARCHAR(32) PRIMARY KEY, elder_id VARCHAR(32), tag_name VARCHAR(64), risk_level VARCHAR(32), remark VARCHAR(255))");
        jdbcTemplate.execute("CREATE TABLE medical_file (medical_file_id VARCHAR(32) PRIMARY KEY, elder_id VARCHAR(32), file_type VARCHAR(32), title VARCHAR(128), audit_status VARCHAR(32), reviewed_at TIMESTAMP)");
        jdbcTemplate.execute("CREATE TABLE sys_role (role_id VARCHAR(32) PRIMARY KEY, role_code VARCHAR(64), enabled TINYINT)");
        jdbcTemplate.execute("CREATE TABLE user_role (user_id VARCHAR(32), role_id VARCHAR(32), PRIMARY KEY (user_id, role_id))");
        jdbcTemplate.execute("CREATE TABLE sys_permission (permission_id VARCHAR(32) PRIMARY KEY, permission_code VARCHAR(128), enabled TINYINT)");
        jdbcTemplate.execute("CREATE TABLE role_permission (role_id VARCHAR(32), permission_id VARCHAR(32), PRIMARY KEY (role_id, permission_id))");
        jdbcTemplate.execute("""
                CREATE TABLE care_attention_notice (
                  notice_id VARCHAR(32) PRIMARY KEY, order_id VARCHAR(32), task_id VARCHAR(32),
                  nurse_id VARCHAR(32), notice_level VARCHAR(32), content VARCHAR(500),
                  source_type VARCHAR(32), source_id VARCHAR(64), source_version VARCHAR(64),
                  required_ack TINYINT, notice_hash CHAR(64), notice_status VARCHAR(32),
                  generated_by VARCHAR(32), generated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                  UNIQUE (order_id, source_type, source_id, notice_hash))
                """);
        jdbcTemplate.execute("""
                CREATE TABLE care_attention_ack (
                  ack_id VARCHAR(32) PRIMARY KEY, notice_id VARCHAR(32), order_id VARCHAR(32),
                  task_id VARCHAR(32), nurse_id VARCHAR(32), acked_by VARCHAR(32),
                  acked_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                  UNIQUE (notice_id, nurse_id))
                """);
        jdbcTemplate.execute("""
                CREATE TABLE operation_log (
                  log_id VARCHAR(32) PRIMARY KEY, operator_id VARCHAR(32), role_code VARCHAR(64),
                  operation_type VARCHAR(64), biz_type VARCHAR(64), biz_id VARCHAR(64),
                  after_value VARCHAR(2000), trace_id VARCHAR(64))
                """);
    }

    private void seedData() {
        jdbcTemplate.update("INSERT INTO nursing_order VALUES ('order_1','elder_1','service_1','ACCEPTED')");
        jdbcTemplate.update("INSERT INTO nurse_task VALUES ('task_1','order_1','nurse_1','ACCEPTED','上门前联系家属确认门牌')");
        jdbcTemplate.update("INSERT INTO service_item VALUES ('service_1','基础照护','服务前准备血压计')");
        jdbcTemplate.update("INSERT INTO health_archive VALUES ('archive_1','elder_1',2,'移动时需有人陪同')");
        jdbcTemplate.update("INSERT INTO allergy_record VALUES ('allergy_1','elder_1','青霉素','皮疹','HIGH',NULL)");
        jdbcTemplate.update("INSERT INTO risk_tag VALUES ('risk_1','elder_1','跌倒风险','MEDIUM','上下楼重点观察')");
        jdbcTemplate.update("INSERT INTO medical_file VALUES ('medical_1','elder_1','CHECK_REPORT','近期检查报告','APPROVED',CURRENT_TIMESTAMP)");
        jdbcTemplate.update("INSERT INTO sys_role VALUES ('role_nurse','NURSE',1),('role_admin','ADMIN',1)");
        jdbcTemplate.update("INSERT INTO user_role VALUES ('nurse_1','role_nurse'),('nurse_2','role_nurse'),('admin_1','role_admin')");
        jdbcTemplate.update("INSERT INTO sys_permission VALUES ('perm_ack','NURSE_ATTENTION_ACK',1),('perm_review','CARE_ATTENTION_REVIEW',1)");
        jdbcTemplate.update("INSERT INTO role_permission VALUES ('role_nurse','perm_ack'),('role_admin','perm_review')");
    }
}
