package com.csu.carenest.user.healtharchive;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
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
}
