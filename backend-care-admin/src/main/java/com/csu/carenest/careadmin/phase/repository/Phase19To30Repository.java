package com.csu.carenest.careadmin.phase.repository;

import com.csu.carenest.careadmin.phase.entity.HealthReviewTaskEntity;
import com.csu.carenest.careadmin.phase.entity.MedicalFileEntity;
import com.csu.carenest.careadmin.phase.entity.NurseRecommendationEntity;
import com.csu.carenest.careadmin.phase.entity.QualificationApplicationEntity;
import com.csu.carenest.careadmin.phase.entity.TrainingRecordEntity;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 阶段21、23至30的数据访问层。
 *
 * <p>这里只消费成员1约定的表和 snake_case 字段，不创建、迁移或修改数据库结构。</p>
 */
@Repository
public class Phase19To30Repository {

    private final JdbcTemplate jdbcTemplate;

    public Phase19To30Repository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public long countMedicalFiles(String auditStatus) {
        String sql = "SELECT COUNT(*) FROM medical_file WHERE (? IS NULL OR audit_status = ?)";
        Long total = jdbcTemplate.queryForObject(sql, Long.class, auditStatus, auditStatus);
        return total == null ? 0 : total;
    }

    public List<MedicalFileEntity> findMedicalFiles(String auditStatus, int size, int offset) {
        return jdbcTemplate.query("""
                SELECT medical_file_id, file_id, elder_id, file_type, title, occurred_at,
                       audit_status, review_comment
                FROM medical_file
                WHERE (? IS NULL OR audit_status = ?)
                ORDER BY occurred_at DESC, medical_file_id DESC
                LIMIT ? OFFSET ?
                """, (rs, rowNum) -> new MedicalFileEntity(
                rs.getString("medical_file_id"),
                rs.getString("file_id"),
                rs.getString("elder_id"),
                rs.getString("file_type"),
                rs.getString("title"),
                text(rs.getObject("occurred_at")),
                rs.getString("audit_status"),
                rs.getString("review_comment")
        ), auditStatus, auditStatus, size, offset);
    }

    public Optional<MedicalFileEntity> findMedicalFile(String fileId) {
        try {
            MedicalFileEntity entity = jdbcTemplate.queryForObject("""
                    SELECT medical_file_id, file_id, elder_id, file_type, title, occurred_at,
                           audit_status, review_comment
                    FROM medical_file
                    WHERE medical_file_id = ? OR file_id = ?
                    """, (rs, rowNum) -> new MedicalFileEntity(
                    rs.getString("medical_file_id"),
                    rs.getString("file_id"),
                    rs.getString("elder_id"),
                    rs.getString("file_type"),
                    rs.getString("title"),
                    text(rs.getObject("occurred_at")),
                    rs.getString("audit_status"),
                    rs.getString("review_comment")
            ), fileId, fileId);
            return Optional.ofNullable(entity);
        } catch (EmptyResultDataAccessException exception) {
            return Optional.empty();
        }
    }

    public void updateMedicalFileReview(
            String medicalFileId,
            String auditStatus,
            String reviewComment,
            String reviewerId) {
        jdbcTemplate.update("""
                UPDATE medical_file
                SET audit_status = ?, review_comment = ?, reviewer_id = ?, reviewed_at = CURRENT_TIMESTAMP
                WHERE medical_file_id = ?
                """, auditStatus, reviewComment, reviewerId, medicalFileId);
        jdbcTemplate.update("""
                UPDATE file_asset
                SET audit_status = ?
                WHERE file_id = (
                    SELECT file_id FROM medical_file WHERE medical_file_id = ?
                )
                """, auditStatus, medicalFileId);
    }

    public void insertHealthReviewTask(
            String taskId,
            String suggestionId,
            String taskType,
            String orderId,
            String elderId,
            String fieldName,
            String oldValue,
            String newValue,
            String sourceType,
            String sourceId,
            String createdBy) {
        jdbcTemplate.update("""
                INSERT INTO health_info_review_task
                  (review_task_id, suggestion_id, task_type, order_id, elder_id,
                   field_name, old_value, new_value, source_type, source_id,
                   review_status, created_by)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'PENDING', ?)
                """, taskId, suggestionId, taskType, orderId, elderId,
                fieldName, oldValue, newValue, sourceType, sourceId, createdBy);
    }

    public Map<String, Object> findOrder(String orderId) {
        return queryForMap("SELECT * FROM nursing_order WHERE order_id = ?", orderId);
    }

    public boolean nurseOwnsOrder(String nurseId, String orderId) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM nurse_task WHERE nurse_id = ? AND order_id = ?
                """, Integer.class, nurseId, orderId);
        return count != null && count > 0;
    }

    public Optional<Map<String, Object>> findActiveBinding(String familyId, String elderId) {
        try {
            return Optional.of(jdbcTemplate.queryForMap("""
                    SELECT binding_id, scope_codes
                    FROM elder_family_binding
                    WHERE family_id = ? AND elder_id = ? AND binding_status = 'ACTIVE'
                    ORDER BY updated_at DESC
                    LIMIT 1
                    """, familyId, elderId));
        } catch (EmptyResultDataAccessException exception) {
            return Optional.empty();
        }
    }

    public void insertHealthSuggestion(
            String suggestionId,
            String taskId,
            String orderId,
            String elderId,
            String fieldName,
            String newValue,
            String sourceType,
            String sourceId,
            String reason,
            String createdBy) {
        jdbcTemplate.update("""
                INSERT INTO health_update_suggestion
                  (suggestion_id, review_task_id, order_id, elder_id, field_name, new_value,
                   source_type, source_id, reason, suggestion_status, created_by)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 'PENDING', ?)
                """, suggestionId, taskId, orderId, elderId, fieldName, newValue,
                sourceType, sourceId, reason, createdBy);
    }

    public Optional<String> findPendingSuggestion(
            String orderId,
            String fieldName,
            String newValue,
            String sourceType,
            String sourceId) {
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject("""
                    SELECT suggestion_id
                    FROM health_update_suggestion
                    WHERE order_id = ? AND field_name = ? AND new_value = ?
                      AND source_type = ? AND source_id = ?
                      AND suggestion_status = 'PENDING'
                    ORDER BY created_at DESC
                    LIMIT 1
                    """, String.class, orderId, fieldName, newValue, sourceType, sourceId));
        } catch (EmptyResultDataAccessException exception) {
            return Optional.empty();
        }
    }

    public boolean sourceBelongsToOrder(String sourceType, String sourceId, String orderId) {
        String sql = switch (sourceType) {
            case "SERVICE_RECORD" -> "SELECT COUNT(*) FROM care_service_record WHERE record_id = ? AND order_id = ?";
            case "SERVICE_REPORT" -> "SELECT COUNT(*) FROM service_report WHERE report_id = ? AND order_id = ?";
            default -> null;
        };
        if (sql == null) {
            return false;
        }
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, sourceId, orderId);
        return count != null && count > 0;
    }

    public long countHealthReviewTasks(String status) {
        Long total = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM health_info_review_task
                WHERE (? IS NULL OR review_status = ?)
                """, Long.class, status, status);
        return total == null ? 0 : total;
    }

    public List<HealthReviewTaskEntity> findHealthReviewTasks(String status, int size, int offset) {
        return jdbcTemplate.query("""
                SELECT t.review_task_id, t.elder_id, t.review_status,
                       COALESCE(ha.archive_version, 0) AS archive_version,
                       t.field_name, t.old_value, t.new_value, t.source_type, t.source_id,
                       t.review_remark
                FROM health_info_review_task t
                LEFT JOIN health_archive ha ON ha.elder_id = t.elder_id
                WHERE (? IS NULL OR t.review_status = ?)
                ORDER BY t.created_at DESC, t.review_task_id DESC
                LIMIT ? OFFSET ?
                """, (rs, rowNum) -> new HealthReviewTaskEntity(
                rs.getString("review_task_id"),
                rs.getString("elder_id"),
                rs.getString("review_status"),
                rs.getString("archive_version"),
                rs.getString("field_name"),
                rs.getString("old_value"),
                rs.getString("new_value"),
                rs.getString("source_type"),
                rs.getString("source_id"),
                rs.getString("review_remark")
        ), status, status, size, offset);
    }

    public Optional<HealthReviewTaskEntity> findHealthReviewTask(String taskId) {
        try {
            HealthReviewTaskEntity entity = jdbcTemplate.queryForObject("""
                    SELECT t.review_task_id, t.elder_id, t.review_status,
                           COALESCE(ha.archive_version, 0) AS archive_version,
                           t.field_name, t.old_value, t.new_value, t.source_type, t.source_id,
                           t.review_remark
                    FROM health_info_review_task t
                    LEFT JOIN health_archive ha ON ha.elder_id = t.elder_id
                    WHERE t.review_task_id = ?
                    """, (rs, rowNum) -> new HealthReviewTaskEntity(
                    rs.getString("review_task_id"),
                    rs.getString("elder_id"),
                    rs.getString("review_status"),
                    rs.getString("archive_version"),
                    rs.getString("field_name"),
                    rs.getString("old_value"),
                    rs.getString("new_value"),
                    rs.getString("source_type"),
                    rs.getString("source_id"),
                    rs.getString("review_remark")
            ), taskId);
            return Optional.ofNullable(entity);
        } catch (EmptyResultDataAccessException exception) {
            return Optional.empty();
        }
    }

    public Optional<HealthReviewTaskEntity> findHealthReviewTaskForUpdate(String taskId) {
        try {
            HealthReviewTaskEntity entity = jdbcTemplate.queryForObject("""
                    SELECT t.review_task_id, t.elder_id, t.review_status,
                           COALESCE((SELECT ha.archive_version
                                     FROM health_archive ha
                                     WHERE ha.elder_id = t.elder_id), 0) AS archive_version,
                           t.field_name, t.old_value, t.new_value, t.source_type, t.source_id,
                           t.review_remark
                    FROM health_info_review_task t
                    WHERE t.review_task_id = ?
                    FOR UPDATE
                    """, (rs, rowNum) -> new HealthReviewTaskEntity(
                    rs.getString("review_task_id"), rs.getString("elder_id"),
                    rs.getString("review_status"), rs.getString("archive_version"),
                    rs.getString("field_name"), rs.getString("old_value"),
                    rs.getString("new_value"), rs.getString("source_type"),
                    rs.getString("source_id"), rs.getString("review_remark")
            ), taskId);
            return Optional.ofNullable(entity);
        } catch (EmptyResultDataAccessException exception) {
            return Optional.empty();
        }
    }

    public List<Map<String, Object>> findSuggestionsByTask(String taskId) {
        return jdbcTemplate.queryForList("""
                SELECT suggestion_id, field_name, old_value, new_value, source_type, source_id, reason,
                       suggestion_status AS status
                FROM health_update_suggestion
                WHERE review_task_id = ?
                ORDER BY created_at, suggestion_id
                """, taskId);
    }

    public int currentArchiveVersion(String elderId) {
        try {
            Integer version = jdbcTemplate.queryForObject("""
                    SELECT archive_version FROM health_archive WHERE elder_id = ?
                    """, Integer.class, elderId);
            return version == null ? 0 : version;
        } catch (EmptyResultDataAccessException exception) {
            return 0;
        }
    }

    public void updateArchiveVersion(String elderId, int archiveVersion, String updatedBy) {
        int changed = jdbcTemplate.update("""
                UPDATE health_archive
                SET archive_version = ?, updated_by = ?, updated_at = CURRENT_TIMESTAMP
                WHERE elder_id = ?
                """, archiveVersion, updatedBy, elderId);
        if (changed == 0) {
            jdbcTemplate.update("""
                    INSERT INTO health_archive (archive_id, elder_id, archive_version, updated_by)
                    VALUES (?, ?, ?, ?)
                    """, "archive_" + elderId, elderId, archiveVersion, updatedBy);
        }
    }

    public void insertArchiveChangeLog(
            String changeLogId,
            String elderId,
            String reviewerId,
            String beforeValue,
            String afterValue) {
        jdbcTemplate.update("""
                INSERT INTO health_archive_change_log
                  (change_log_id, elder_id, changed_by, change_type, before_value, after_value)
                VALUES (?, ?, ?, 'REVIEW_ARCHIVE', ?, ?)
                """, changeLogId, elderId, reviewerId, beforeValue, afterValue);
    }

    public List<Map<String, Object>> findArchiveChangeLogs(String elderId, int limit) {
        return jdbcTemplate.queryForList("""
                SELECT change_log_id AS changeLogId,
                       change_type AS changeType,
                       JSON_UNQUOTE(JSON_EXTRACT(after_value, '$.targetField')) AS fieldName,
                       CAST(before_value AS CHAR) AS beforeValue,
                       CASE
                         WHEN change_type = 'REVIEW_ARCHIVE'
                           THEN COALESCE(JSON_UNQUOTE(JSON_EXTRACT(after_value, '$.normalizedValue')),
                                         CAST(after_value AS CHAR))
                         ELSE CAST(after_value AS CHAR)
                       END AS afterValue,
                       JSON_UNQUOTE(JSON_EXTRACT(after_value, '$.comment')) AS comment,
                       JSON_UNQUOTE(JSON_EXTRACT(after_value, '$.archiveVersion')) AS archiveVersion,
                       created_at AS changedAt
                FROM health_archive_change_log
                WHERE elder_id = ?
                ORDER BY created_at DESC, change_log_id DESC
                LIMIT ?
                """, elderId, limit);
    }

    public void finishHealthReviewTask(
            String taskId,
            String reviewStatus,
            String reviewerId,
            String reviewRemark) {
        jdbcTemplate.update("""
                UPDATE health_info_review_task
                SET review_status = ?, reviewer_id = ?, reviewed_at = CURRENT_TIMESTAMP,
                    review_remark = ?
                WHERE review_task_id = ?
                """, reviewStatus, reviewerId, reviewRemark, taskId);
        jdbcTemplate.update("""
                UPDATE health_update_suggestion SET suggestion_status = ?
                WHERE review_task_id = ? AND suggestion_status = 'PENDING'
                """, reviewStatus, taskId);
    }

    /** 更新健康档案主表中的照护摘要；版本号由归档事务统一递增。 */
    public void updateCareSummary(String elderId, String careSummary, String updatedBy) {
        jdbcTemplate.update("""
                UPDATE health_archive
                SET care_summary = ?, updated_by = ?, updated_at = CURRENT_TIMESTAMP
                WHERE elder_id = ?
                """, careSummary, updatedBy, elderId);
    }

    /** 同一长辈同名风险标签采用替换语义，避免审核重试产生重复记录。 */
    public void upsertRiskTag(
            String riskTagId,
            String elderId,
            String tagCode,
            String tagName,
            String riskLevel,
            String remark) {
        jdbcTemplate.update("DELETE FROM risk_tag WHERE elder_id = ? AND tag_name = ?", elderId, tagName);
        jdbcTemplate.update("""
                INSERT INTO risk_tag (risk_tag_id, elder_id, tag_code, tag_name, risk_level, remark)
                VALUES (?, ?, ?, ?, ?, ?)
                """, riskTagId, elderId, tagCode, tagName, riskLevel, remark);
    }

    public void upsertDisease(
            String diseaseId, String elderId, String diseaseName, String diseaseStatus, String remark) {
        jdbcTemplate.update(
                "DELETE FROM chronic_disease WHERE elder_id = ? AND disease_name = ?", elderId, diseaseName);
        jdbcTemplate.update("""
                INSERT INTO chronic_disease
                  (disease_id, elder_id, disease_name, disease_status, remark)
                VALUES (?, ?, ?, ?, ?)
                """, diseaseId, elderId, diseaseName, diseaseStatus, remark);
    }

    public void upsertMedication(
            String medicationId,
            String elderId,
            String medicationName,
            String dosage,
            String frequency,
            String medicationStatus,
            String remark) {
        jdbcTemplate.update(
                "DELETE FROM medication_plan WHERE elder_id = ? AND medication_name = ?",
                elderId, medicationName);
        jdbcTemplate.update("""
                INSERT INTO medication_plan
                  (medication_id, elder_id, medication_name, dosage, frequency, medication_status, remark)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """, medicationId, elderId, medicationName, dosage, frequency, medicationStatus, remark);
    }

    public void upsertAllergy(
            String allergyId,
            String elderId,
            String allergen,
            String reaction,
            String severity,
            String remark) {
        jdbcTemplate.update(
                "DELETE FROM allergy_record WHERE elder_id = ? AND allergen = ?", elderId, allergen);
        jdbcTemplate.update("""
                INSERT INTO allergy_record
                  (allergy_id, elder_id, allergen, reaction, severity, remark)
                VALUES (?, ?, ?, ?, ?, ?)
                """, allergyId, elderId, allergen, reaction, severity, remark);
    }

    public void replaceActiveCarePlan(String carePlanId, String elderId, String planContent) {
        jdbcTemplate.update("""
                UPDATE care_plan SET plan_status = 'INACTIVE', updated_at = CURRENT_TIMESTAMP
                WHERE elder_id = ? AND plan_status = 'ACTIVE'
                """, elderId);
        jdbcTemplate.update("""
                INSERT INTO care_plan (care_plan_id, elder_id, plan_content, plan_status)
                VALUES (?, ?, ?, 'ACTIVE')
                """, carePlanId, elderId, planContent);
    }

    public Map<String, Object> findElderProfile(String elderId) {
        return queryForMap("""
                SELECT ep.elder_id AS elderId, ep.elder_name AS elderName, ep.gender,
                       ep.birth_date AS birthDate, ep.care_level AS careLevel,
                       ha.care_summary AS careSummary, ha.archive_version AS archiveVersion
                FROM elder_profile ep
                LEFT JOIN health_archive ha ON ha.elder_id = ep.elder_id
                WHERE ep.elder_id = ?
                """, elderId);
    }

    public List<String> findRiskTags(String elderId) {
        return jdbcTemplate.query("""
                SELECT tag_name FROM risk_tag WHERE elder_id = ? ORDER BY risk_level DESC, tag_name
                """, (rs, rowNum) -> rs.getString("tag_name"), elderId);
    }

    public List<Map<String, Object>> findMedications(String elderId) {
        return jdbcTemplate.queryForList("""
                SELECT medication_id AS medicationId, medication_name AS medicationName,
                       dosage, frequency, start_date AS startDate, end_date AS endDate
                FROM medication_plan
                WHERE elder_id = ? AND medication_status = 'ACTIVE'
                ORDER BY start_date DESC
                """, elderId);
    }

    public List<Map<String, Object>> findDiseases(String elderId) {
        return jdbcTemplate.queryForList("""
                SELECT disease_id AS diseaseId, disease_name AS diseaseName,
                       disease_status AS diseaseStatus, diagnosed_at AS diagnosedAt, remark
                FROM chronic_disease
                WHERE elder_id = ? AND disease_status = 'ACTIVE'
                ORDER BY disease_name
                """, elderId);
    }

    public List<Map<String, Object>> findAllergies(String elderId) {
        return jdbcTemplate.queryForList("""
                SELECT allergy_id AS allergyId, allergen, reaction
                FROM allergy_record WHERE elder_id = ? ORDER BY allergen
                """, elderId);
    }

    public List<Map<String, Object>> findApprovedMedicalFiles(String elderId) {
        return jdbcTemplate.queryForList("""
                SELECT medical_file_id AS medicalFileId, file_id AS fileId, file_type AS fileType,
                       title, occurred_at AS occurredAt, audit_status AS auditStatus
                FROM medical_file
                WHERE elder_id = ? AND audit_status = 'APPROVED'
                ORDER BY occurred_at DESC
                """, elderId);
    }

    public List<Map<String, Object>> findRecentReports(String elderId) {
        return jdbcTemplate.queryForList("""
                SELECT sr.report_id AS reportId, sr.order_id AS orderId, sr.summary,
                       sr.nursing_advice AS nursingAdvice, sr.generated_at AS generatedAt
                FROM service_report sr
                JOIN nursing_order o ON o.order_id = sr.order_id
                WHERE o.elder_id = ? AND sr.report_status IN ('WAIT_CONFIRM', 'CONFIRMED')
                ORDER BY sr.generated_at DESC
                LIMIT 10
                """, elderId);
    }

    public void updateNurseProfileForApplication(
            String nurseId,
            String realName,
            String idNoMasked,
            String qualificationStatus) {
        jdbcTemplate.update("""
                UPDATE nurse_profile
                SET real_name = ?, id_no_masked = ?, qualification_status = ?, updated_at = CURRENT_TIMESTAMP
                WHERE nurse_id = ?
                """, realName, idNoMasked, qualificationStatus, nurseId);
    }

    public void insertNurseCertificate(
            String certificateId,
            String applicationId,
            String nurseId,
            String certificateNo,
            String fileId,
            String skillCodesJson) {
        jdbcTemplate.update("""
                INSERT INTO nurse_certificate
                  (certificate_id, application_id, nurse_id, certificate_no, file_id,
                   service_skill_codes, audit_status)
                VALUES (?, ?, ?, ?, ?, ?, 'PENDING')
                """, certificateId, applicationId, nurseId, certificateNo, fileId, skillCodesJson);
    }

    public Optional<QualificationApplicationEntity> findCurrentQualification(String nurseId) {
        try {
            QualificationApplicationEntity entity = jdbcTemplate.queryForObject("""
                    SELECT application_id, nurse_id, audit_status, review_comment
                    FROM nurse_certificate
                    WHERE nurse_id = ?
                    ORDER BY created_at DESC
                    LIMIT 1
                    """, (rs, rowNum) -> new QualificationApplicationEntity(
                    rs.getString("application_id"),
                    rs.getString("nurse_id"),
                    rs.getString("audit_status"),
                    rs.getString("review_comment")
            ), nurseId);
            return Optional.ofNullable(entity);
        } catch (EmptyResultDataAccessException exception) {
            return Optional.empty();
        }
    }

    public long countQualificationApplications(String auditStatus) {
        Long total = jdbcTemplate.queryForObject("""
                SELECT COUNT(DISTINCT application_id) FROM nurse_certificate
                WHERE (? IS NULL OR audit_status = ?)
                """, Long.class, auditStatus, auditStatus);
        return total == null ? 0 : total;
    }

    public List<QualificationApplicationEntity> findQualificationApplications(
            String auditStatus,
            int size,
            int offset) {
        return jdbcTemplate.query("""
                SELECT application_id, nurse_id, MAX(audit_status) AS audit_status,
                       MAX(review_comment) AS review_comment
                FROM nurse_certificate
                WHERE (? IS NULL OR audit_status = ?)
                GROUP BY application_id, nurse_id
                ORDER BY MAX(created_at) DESC
                LIMIT ? OFFSET ?
                """, (rs, rowNum) -> new QualificationApplicationEntity(
                rs.getString("application_id"),
                rs.getString("nurse_id"),
                rs.getString("audit_status"),
                rs.getString("review_comment")
        ), auditStatus, auditStatus, size, offset);
    }

    public Optional<QualificationApplicationEntity> findQualificationApplication(String applicationId) {
        try {
            QualificationApplicationEntity entity = jdbcTemplate.queryForObject("""
                    SELECT application_id, nurse_id, MAX(audit_status) AS audit_status,
                           MAX(review_comment) AS review_comment
                    FROM nurse_certificate
                    WHERE application_id = ?
                    GROUP BY application_id, nurse_id
                    """, (rs, rowNum) -> new QualificationApplicationEntity(
                    rs.getString("application_id"),
                    rs.getString("nurse_id"),
                    rs.getString("audit_status"),
                    rs.getString("review_comment")
            ), applicationId);
            return Optional.ofNullable(entity);
        } catch (EmptyResultDataAccessException exception) {
            return Optional.empty();
        }
    }

    public void reviewQualification(
            String applicationId,
            String nurseId,
            String auditStatus,
            String reviewComment,
            String reviewerId) {
        jdbcTemplate.update("""
                UPDATE nurse_certificate
                SET audit_status = ?, review_comment = ?, reviewed_by = ?, reviewed_at = CURRENT_TIMESTAMP
                WHERE application_id = ?
                """, auditStatus, reviewComment, reviewerId, applicationId);
        jdbcTemplate.update("""
                UPDATE nurse_profile SET qualification_status = ?, updated_at = CURRENT_TIMESTAMP
                WHERE nurse_id = ?
                """, auditStatus, nurseId);
    }

    public Optional<TrainingRecordEntity> findTrainingRecord(String nurseId) {
        try {
            TrainingRecordEntity entity = jdbcTemplate.queryForObject("""
                    SELECT nurse_id, training_status, expired_at
                    FROM nurse_training_record
                    WHERE nurse_id = ?
                    ORDER BY created_at DESC
                    LIMIT 1
                    """, (rs, rowNum) -> new TrainingRecordEntity(
                    rs.getString("nurse_id"),
                    rs.getString("training_status"),
                    text(rs.getObject("expired_at"))
            ), nurseId);
            return Optional.ofNullable(entity);
        } catch (EmptyResultDataAccessException exception) {
            return Optional.empty();
        }
    }

    public void saveTrainingReview(
            String trainingId,
            String nurseId,
            String status,
            String trainingBatch,
            LocalDateTime expiredAt,
            String remark,
            String reviewerId) {
        jdbcTemplate.update("""
                INSERT INTO nurse_training_record
                  (training_id, nurse_id, training_status, training_batch, expired_at,
                   remark, reviewed_by, reviewed_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
                """, trainingId, nurseId, status, trainingBatch, expiredAt, remark, reviewerId);
    }

    public List<NurseRecommendationEntity> findRecommendableNurses(String serviceId) {
        return jdbcTemplate.query("""
                SELECT np.nurse_id, COALESCE(np.real_name, u.display_name) AS nurse_name,
                       COALESCE(ns.total_score, 0) AS total_score,
                       GROUP_CONCAT(DISTINCT nss.skill_code ORDER BY nss.skill_code) AS matched_skills
                FROM nurse_profile np
                JOIN sys_user u ON u.user_id = np.nurse_id
                JOIN nurse_training_record ntr ON ntr.nurse_id = np.nurse_id
                LEFT JOIN nurse_score ns ON ns.nurse_id = np.nurse_id
                LEFT JOIN nurse_service_skill nss ON nss.nurse_id = np.nurse_id
                WHERE np.qualification_status = 'APPROVED'
                  AND ntr.training_status = 'APPROVED'
                  AND (ntr.expired_at IS NULL OR ntr.expired_at > CURRENT_TIMESTAMP)
                  AND (nss.service_id = ? OR nss.service_id IS NULL)
                GROUP BY np.nurse_id, np.real_name, u.display_name, ns.total_score
                ORDER BY total_score DESC, np.nurse_id
                """, (rs, rowNum) -> {
            String skills = rs.getString("matched_skills");
            List<String> skillList = skills == null || skills.isBlank()
                    ? List.of()
                    : Arrays.stream(skills.split(",")).map(String::trim).toList();
            BigDecimal score = rs.getBigDecimal("total_score");
            String reason = skillList.isEmpty()
                    ? "资质和培训有效，按综合评分推荐"
                    : "资质和培训有效，匹配技能：" + String.join("、", skillList);
            return new NurseRecommendationEntity(
                    rs.getString("nurse_id"),
                    rs.getString("nurse_name"),
                    score == null ? BigDecimal.ZERO : score,
                    skillList,
                    reason,
                    true
            );
        }, serviceId);
    }

    public void insertRecommendationLog(
            String logId,
            String requestKey,
            String orderId,
            String elderId,
            String serviceId,
            String addressId,
            LocalDateTime scheduledStart,
            NurseRecommendationEntity recommendation,
            String createdBy) {
        jdbcTemplate.update("""
                INSERT INTO nurse_recommendation_log
                  (recommendation_log_id, request_key, order_id, elder_id, service_id, address_id,
                   scheduled_start_at, nurse_id, score, matched_skills, recommend_reason,
                   available, created_by)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, logId, requestKey, orderId, elderId, serviceId, addressId, scheduledStart,
                recommendation.nurseId(), recommendation.score(),
                String.join(",", recommendation.matchedSkills()), recommendation.recommendReason(),
                recommendation.available() ? 1 : 0, createdBy);
    }

    public List<NurseRecommendationEntity> findOrderRecommendations(String orderId) {
        return jdbcTemplate.query("""
                SELECT l.nurse_id, COALESCE(np.real_name, u.display_name) AS nurse_name,
                       l.score, l.matched_skills, l.recommend_reason, l.available
                FROM nurse_recommendation_log l
                JOIN sys_user u ON u.user_id = l.nurse_id
                LEFT JOIN nurse_profile np ON np.nurse_id = l.nurse_id
                WHERE l.order_id = ?
                ORDER BY l.score DESC, l.created_at
                """, (rs, rowNum) -> new NurseRecommendationEntity(
                rs.getString("nurse_id"),
                rs.getString("nurse_name"),
                rs.getBigDecimal("score"),
                splitCsv(rs.getString("matched_skills")),
                rs.getString("recommend_reason"),
                rs.getBoolean("available")
        ), orderId);
    }

    public Optional<NurseRecommendationEntity> findOrderRecommendation(String orderId, String nurseId) {
        return findOrderRecommendations(orderId).stream()
                .filter(item -> item.nurseId().equals(nurseId))
                .findFirst();
    }

    public void updatePreferredNurse(String orderId, String nurseId) {
        jdbcTemplate.update("""
                UPDATE nursing_order SET preferred_nurse_id = ?, updated_at = CURRENT_TIMESTAMP
                WHERE order_id = ?
                """, nurseId, orderId);
    }

    public void insertOperationLog(
            String logId,
            String operatorId,
            String roleCode,
            String operationType,
            String bizType,
            String bizId,
            String beforeValue,
            String afterValue,
            String traceId) {
        jdbcTemplate.update("""
                INSERT INTO operation_log
                  (log_id, operator_id, role_code, operation_type, biz_type, biz_id,
                   before_value, after_value, trace_id)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, logId, operatorId, roleCode, operationType, bizType, bizId,
                beforeValue, afterValue, traceId);
    }

    private Map<String, Object> queryForMap(String sql, Object... args) {
        return jdbcTemplate.queryForMap(sql, args);
    }

    private static List<String> splitCsv(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return Arrays.stream(value.split(",")).map(String::trim).toList();
    }

    private static String text(Object value) {
        if (value instanceof Timestamp timestamp) {
            return timestamp.toLocalDateTime().toString();
        }
        return value == null ? null : value.toString();
    }
}
