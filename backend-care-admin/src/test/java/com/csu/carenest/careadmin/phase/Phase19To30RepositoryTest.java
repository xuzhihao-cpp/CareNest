package com.csu.carenest.careadmin.phase;

import com.csu.carenest.careadmin.phase.entity.HealthReviewTaskEntity;
import com.csu.carenest.careadmin.phase.repository.Phase19To30Repository;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 使用 MySQL 兼容模式校验阶段21-25仓储 SQL 的真实列名，防止接口测试只验证到 Mock。
 */
class Phase19To30RepositoryTest {

    private JdbcTemplate jdbcTemplate;
    private Phase19To30Repository repository;

    @BeforeEach
    void setUp() {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:" + UUID.randomUUID()
                + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        jdbcTemplate = new JdbcTemplate(dataSource);
        repository = new Phase19To30Repository(jdbcTemplate);
        createSchema();
    }

    @Test
    void medicalReviewUsesReviewerIdAndSynchronizesFileAuditStatus() {
        jdbcTemplate.update("INSERT INTO file_asset VALUES ('file_1', 'PENDING')");
        jdbcTemplate.update("""
                INSERT INTO medical_file
                  (medical_file_id, file_id, elder_id, file_type, title, occurred_at, audit_status)
                VALUES ('medical_1', 'file_1', 'elder_1', 'CHECK_REPORT', '检查报告', CURRENT_DATE, 'PENDING')
                """);

        repository.updateMedicalFileReview("medical_1", "APPROVED", "资料清晰", "admin_1");

        Map<String, Object> medical = jdbcTemplate.queryForMap(
                "SELECT audit_status, reviewer_id FROM medical_file WHERE medical_file_id = 'medical_1'");
        assertEquals("APPROVED", medical.get("audit_status"));
        assertEquals("admin_1", medical.get("reviewer_id"));
        assertEquals("APPROVED", jdbcTemplate.queryForObject(
                "SELECT audit_status FROM file_asset WHERE file_id = 'file_1'", String.class));
    }

    @Test
    void healthSuggestionAndReviewTaskUseLatestStatusAndVersionColumns() {
        jdbcTemplate.update("INSERT INTO health_archive VALUES ('archive_1', 'elder_1', 2, NULL, NULL, CURRENT_TIMESTAMP)");
        repository.insertHealthReviewTask(
                "review_1", "suggestion_1", "HEALTH_UPDATE", "order_1", "elder_1",
                "riskTags", null, "跌倒风险", "SUGGESTION", "suggestion_1", "nurse_1");
        repository.insertHealthSuggestion(
                "suggestion_1", "review_1", "order_1", "elder_1", "riskTags", "跌倒风险",
                "SERVICE_RECORD", "record_1", "服务中发现", "nurse_1");

        HealthReviewTaskEntity task = repository.findHealthReviewTaskForUpdate("review_1").orElseThrow();
        assertEquals("2", task.archiveVersion());
        assertEquals("PENDING", repository.findSuggestionsByTask("review_1").get(0).get("status"));

        repository.updateArchiveVersion("elder_1", 3, "admin_1");
        repository.finishHealthReviewTask("review_1", "APPROVED", "admin_1", "确认归档");

        assertEquals(3, repository.currentArchiveVersion("elder_1"));
        assertEquals("APPROVED", jdbcTemplate.queryForObject(
                "SELECT suggestion_status FROM health_update_suggestion WHERE suggestion_id = 'suggestion_1'",
                String.class));
    }

    private void createSchema() {
        jdbcTemplate.execute("CREATE TABLE file_asset (file_id VARCHAR(32) PRIMARY KEY, audit_status VARCHAR(32))");
        jdbcTemplate.execute("""
                CREATE TABLE medical_file (
                  medical_file_id VARCHAR(32) PRIMARY KEY, file_id VARCHAR(32), elder_id VARCHAR(32),
                  file_type VARCHAR(32), title VARCHAR(128), occurred_at DATE, audit_status VARCHAR(32),
                  review_comment VARCHAR(255), reviewer_id VARCHAR(32), reviewed_at TIMESTAMP
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE health_archive (
                  archive_id VARCHAR(32) PRIMARY KEY, elder_id VARCHAR(32) UNIQUE, archive_version INT,
                  care_summary VARCHAR(512), updated_by VARCHAR(32), updated_at TIMESTAMP
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE health_info_review_task (
                  review_task_id VARCHAR(32) PRIMARY KEY, suggestion_id VARCHAR(32), task_type VARCHAR(32),
                  order_id VARCHAR(32), elder_id VARCHAR(32), field_name VARCHAR(64), old_value VARCHAR(512),
                  new_value VARCHAR(512), source_type VARCHAR(32), source_id VARCHAR(32),
                  review_status VARCHAR(32), created_by VARCHAR(32), reviewer_id VARCHAR(32),
                  reviewed_at TIMESTAMP, review_remark VARCHAR(255), created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE health_update_suggestion (
                  suggestion_id VARCHAR(32) PRIMARY KEY, review_task_id VARCHAR(32), order_id VARCHAR(32),
                  elder_id VARCHAR(32), field_name VARCHAR(64), old_value CLOB, new_value CLOB,
                  source_type VARCHAR(64), source_id VARCHAR(32), reason VARCHAR(255),
                  suggestion_status VARCHAR(32), created_by VARCHAR(32),
                  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
                """);
    }
}
