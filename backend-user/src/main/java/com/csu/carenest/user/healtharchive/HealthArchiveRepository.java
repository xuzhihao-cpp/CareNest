package com.csu.carenest.user.healtharchive;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
class HealthArchiveRepository {

    private final JdbcTemplate jdbcTemplate;

    HealthArchiveRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    Optional<ElderRow> findElder(String elderId) {
        return jdbcTemplate.query(
                        "SELECT elder_id, user_id FROM elder_profile WHERE elder_id = ?",
                        (resultSet, rowNum) -> new ElderRow(
                                resultSet.getString("elder_id"),
                                resultSet.getString("user_id")),
                        elderId)
                .stream()
                .findFirst();
    }

    List<String> findActiveBindingScopes(String familyId, String elderId) {
        return jdbcTemplate.queryForList(
                """
                SELECT scope_codes
                FROM elder_family_binding
                WHERE family_id = ? AND elder_id = ? AND binding_status = 'ACTIVE'
                """,
                String.class,
                familyId,
                elderId);
    }

    List<String> findActiveFamilyIds(String elderId) {
        return jdbcTemplate.queryForList(
                """
                SELECT DISTINCT family_id
                FROM elder_family_binding
                WHERE elder_id = ? AND binding_status = 'ACTIVE'
                """,
                String.class,
                elderId);
    }

    Optional<ArchiveRow> findArchive(String elderId) {
        return jdbcTemplate.query(
                        """
                        SELECT archive_id, elder_id, archive_version, care_summary, updated_by, updated_at
                        FROM health_archive
                        WHERE elder_id = ?
                        """,
                        (resultSet, rowNum) -> new ArchiveRow(
                                resultSet.getString("archive_id"),
                                resultSet.getString("elder_id"),
                                resultSet.getInt("archive_version"),
                                resultSet.getString("care_summary"),
                                resultSet.getString("updated_by"),
                                resultSet.getTimestamp("updated_at").toLocalDateTime()),
                        elderId)
                .stream()
                .findFirst();
    }

    List<DiseaseRow> findDiseases(String elderId) {
        return jdbcTemplate.query(
                """
                SELECT disease_name, disease_status, diagnosed_at, remark
                FROM chronic_disease
                WHERE elder_id = ?
                ORDER BY created_at, disease_id
                """,
                (resultSet, rowNum) -> new DiseaseRow(
                        resultSet.getString("disease_name"),
                        resultSet.getString("disease_status"),
                        resultSet.getObject("diagnosed_at", LocalDate.class),
                        resultSet.getString("remark")),
                elderId);
    }

    List<MedicationRow> findMedications(String elderId) {
        return jdbcTemplate.query(
                """
                SELECT medication_name, dosage, frequency, time_points, start_date, end_date, remark
                FROM medication_plan
                WHERE elder_id = ? AND medication_status = 'ACTIVE'
                ORDER BY created_at, medication_id
                """,
                (resultSet, rowNum) -> new MedicationRow(
                        resultSet.getString("medication_name"),
                        resultSet.getString("dosage"),
                        resultSet.getString("frequency"),
                        resultSet.getString("time_points"),
                        resultSet.getObject("start_date", LocalDate.class),
                        resultSet.getObject("end_date", LocalDate.class),
                        resultSet.getString("remark")),
                elderId);
    }

    List<AllergyRow> findAllergies(String elderId) {
        return jdbcTemplate.query(
                """
                SELECT allergen, reaction, severity, remark
                FROM allergy_record
                WHERE elder_id = ?
                ORDER BY created_at, allergy_id
                """,
                (resultSet, rowNum) -> new AllergyRow(
                        resultSet.getString("allergen"),
                        resultSet.getString("reaction"),
                        resultSet.getString("severity"),
                        resultSet.getString("remark")),
                elderId);
    }

    List<RiskTagRow> findRiskTags(String elderId) {
        return jdbcTemplate.query(
                """
                SELECT tag_code, tag_name
                FROM risk_tag
                WHERE elder_id = ?
                ORDER BY created_at, risk_tag_id
                """,
                (resultSet, rowNum) -> new RiskTagRow(
                        resultSet.getString("tag_code"),
                        resultSet.getString("tag_name")),
                elderId);
    }

    Optional<String> findActiveCarePlanContent(String elderId) {
        return jdbcTemplate.query(
                        """
                        SELECT plan_content
                        FROM care_plan
                        WHERE elder_id = ? AND plan_status = 'ACTIVE'
                        ORDER BY updated_at DESC, care_plan_id
                        """,
                        (resultSet, rowNum) -> resultSet.getString("plan_content"),
                        elderId)
                .stream()
                .findFirst();
    }

    int advanceArchiveVersion(String elderId, int expectedVersion, String updatedBy) {
        return jdbcTemplate.update(
                """
                UPDATE health_archive
                SET archive_version = archive_version + 1, updated_by = ?, updated_at = CURRENT_TIMESTAMP
                WHERE elder_id = ? AND archive_version = ?
                """,
                updatedBy,
                elderId,
                expectedVersion);
    }

    void replaceDiseases(String elderId, List<HealthArchiveDtos.DiseaseInput> values) {
        jdbcTemplate.update("DELETE FROM chronic_disease WHERE elder_id = ?", elderId);
        for (HealthArchiveDtos.DiseaseInput value : values) {
            jdbcTemplate.update(
                    """
                    INSERT INTO chronic_disease
                      (disease_id, elder_id, disease_name, disease_status, diagnosed_at, remark)
                    VALUES (?, ?, ?, ?, ?, ?)
                    """,
                    nextId("disease"), elderId, value.diseaseName().trim(), value.status(),
                    value.diagnosedAt(), trimToNull(value.remark()));
        }
    }

    void replaceMedications(String elderId, List<SerializedMedication> values) {
        jdbcTemplate.update("DELETE FROM medication_plan WHERE elder_id = ?", elderId);
        for (SerializedMedication value : values) {
            HealthArchiveDtos.MedicationInput input = value.input();
            jdbcTemplate.update(
                    """
                    INSERT INTO medication_plan
                      (medication_id, elder_id, medication_name, dosage, frequency, time_points,
                       start_date, end_date, medication_status, remark)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'ACTIVE', ?)
                    """,
                    nextId("med"), elderId, input.medicationName().trim(), trimToNull(input.dosage()),
                    input.frequency(), value.timePointsJson(), input.startDate(), input.endDate(),
                    trimToNull(input.remark()));
        }
    }

    void replaceAllergies(String elderId, List<HealthArchiveDtos.AllergyInput> values) {
        jdbcTemplate.update("DELETE FROM allergy_record WHERE elder_id = ?", elderId);
        for (HealthArchiveDtos.AllergyInput value : values) {
            jdbcTemplate.update(
                    """
                    INSERT INTO allergy_record
                      (allergy_id, elder_id, allergen, reaction, severity, remark)
                    VALUES (?, ?, ?, ?, ?, ?)
                    """,
                    nextId("allergy"), elderId, value.allergenName().trim(), trimToNull(value.reaction()),
                    value.severity(), trimToNull(value.remark()));
        }
    }

    void replaceRiskTags(String elderId, List<String> codes, Map<String, String> labels) {
        jdbcTemplate.update("DELETE FROM risk_tag WHERE elder_id = ?", elderId);
        for (String rawCode : codes) {
            String code = rawCode.trim();
            jdbcTemplate.update(
                    """
                    INSERT INTO risk_tag
                      (risk_tag_id, elder_id, tag_code, tag_name, risk_level)
                    VALUES (?, ?, ?, ?, 'MEDIUM')
                    """,
                    nextId("risk"), elderId, code, labels.getOrDefault(code, code));
        }
    }

    void replaceCarePlan(String elderId, String contentJson) {
        jdbcTemplate.update("DELETE FROM care_plan WHERE elder_id = ?", elderId);
        jdbcTemplate.update(
                """
                INSERT INTO care_plan (care_plan_id, elder_id, plan_content, plan_status)
                VALUES (?, ?, ?, 'ACTIVE')
                """,
                nextId("plan"), elderId, contentJson);
    }

    boolean medicationNameExists(String elderId, String normalizedName) {
        Integer count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM medication_plan
                WHERE elder_id = ? AND LOWER(TRIM(medication_name)) = ?
                """,
                Integer.class,
                elderId,
                normalizedName);
        return count != null && count > 0;
    }

    void insertMedication(String elderId, SerializedMedication value) {
        HealthArchiveDtos.MedicationInput input = value.input();
        jdbcTemplate.update(
                """
                INSERT INTO medication_plan
                  (medication_id, elder_id, medication_name, dosage, frequency, time_points,
                   start_date, end_date, medication_status, remark)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'ACTIVE', ?)
                """,
                nextId("med"), elderId, input.medicationName().trim(), trimToNull(input.dosage()),
                input.frequency(), value.timePointsJson(), input.startDate(), input.endDate(),
                trimToNull(input.remark()));
    }

    void insertChangeLog(
            String elderId,
            String changedBy,
            String changeType,
            String beforeJson,
            String afterJson) {
        jdbcTemplate.update(
                """
                INSERT INTO health_archive_change_log
                  (change_log_id, elder_id, changed_by, change_type, before_value, after_value)
                VALUES (?, ?, ?, ?, ?, ?)
                """,
                nextId("archive_log"), elderId, changedBy, changeType, beforeJson, afterJson);
    }

    List<ArchiveChangeLogRow> findArchiveChangeLogs(String elderId, int limit) {
        return jdbcTemplate.query(
                """
                SELECT change_log_id AS changeLogId,
                       change_type AS changeType,
                       CAST(before_value AS CHAR) AS beforeValue,
                       CAST(after_value AS CHAR) AS afterValue,
                       created_at AS changedAt
                FROM health_archive_change_log
                WHERE elder_id = ?
                ORDER BY created_at DESC, change_log_id DESC
                LIMIT ?
                """,
                (resultSet, rowNum) -> new ArchiveChangeLogRow(
                        resultSet.getString("changeLogId"),
                        resultSet.getString("changeType"),
                        resultSet.getString("beforeValue"),
                        resultSet.getString("afterValue"),
                        resultSet.getTimestamp("changedAt").toLocalDateTime()),
                elderId,
                limit);
    }

    void insertOperationLog(
            String operatorId,
            String operationType,
            String bizId,
            String beforeJson,
            String afterJson) {
        jdbcTemplate.update(
                """
                INSERT INTO operation_log
                  (log_id, operator_id, role_code, operation_type, biz_type, biz_id,
                   before_value, after_value, trace_id)
                VALUES (?, ?, 'FAMILY', ?, 'HEALTH_ARCHIVE', ?, ?, ?, ?)
                """,
                nextId("op"), operatorId, operationType, bizId, beforeJson, afterJson,
                "phase19-" + java.util.UUID.randomUUID().toString().replace("-", ""));
    }

    private String nextId(String prefix) {
        return prefix + "_" + java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    record ElderRow(String elderId, String userId) {
    }

    record ArchiveRow(
            String archiveId,
            String elderId,
            int archiveVersion,
            String careSummary,
            String updatedBy,
            LocalDateTime updatedAt) {
    }

    record DiseaseRow(String name, String status, LocalDate diagnosedAt, String remark) {
    }

    record MedicationRow(
            String name,
            String dosage,
            String frequency,
            String timePoints,
            LocalDate startDate,
            LocalDate endDate,
            String remark) {
    }

    record AllergyRow(String allergen, String reaction, String severity, String remark) {
    }

    record RiskTagRow(String code, String name) {
    }

    record ArchiveChangeLogRow(
            String changeLogId,
            String changeType,
            String beforeValue,
            String afterValue,
            LocalDateTime changedAt) {
    }

    record SerializedMedication(HealthArchiveDtos.MedicationInput input, String timePointsJson) {
    }
}
