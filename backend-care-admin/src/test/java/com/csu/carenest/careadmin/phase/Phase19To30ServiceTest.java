package com.csu.carenest.careadmin.phase;

import com.csu.carenest.careadmin.auth.CurrentUser;
import com.csu.carenest.careadmin.auth.RoleCode;
import com.csu.carenest.careadmin.common.BusinessRuleException;
import com.csu.carenest.careadmin.common.ConflictException;
import com.csu.carenest.careadmin.common.ForbiddenException;
import com.csu.carenest.careadmin.phase.dto.HealthArchiveDtos;
import com.csu.carenest.careadmin.phase.dto.MedicalFileDtos;
import com.csu.carenest.careadmin.phase.dto.QualificationDtos;
import com.csu.carenest.careadmin.phase.dto.RecommendationDtos;
import com.csu.carenest.careadmin.phase.entity.HealthReviewTaskEntity;
import com.csu.carenest.careadmin.phase.entity.MedicalFileEntity;
import com.csu.carenest.careadmin.phase.entity.NurseRecommendationEntity;
import com.csu.carenest.careadmin.phase.entity.QualificationApplicationEntity;
import com.csu.carenest.careadmin.phase.repository.Phase19To30Repository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 阶段21、23至30关键业务规则测试。
 */
@ExtendWith(MockitoExtension.class)
class Phase19To30ServiceTest {

    private static final CurrentUser ADMIN = new CurrentUser("admin_demo", List.of(RoleCode.ADMIN));
    private static final CurrentUser NURSE = new CurrentUser("nurse_demo", List.of(RoleCode.NURSE));
    private static final CurrentUser FAMILY = new CurrentUser("family_demo", List.of(RoleCode.FAMILY));

    @Mock
    private Phase19To30Repository repository;
    @Mock
    private Phase25MedicalFileStorage medicalFileStorage;

    private Phase19To30Service service;

    @BeforeEach
    void setUp() {
        service = new Phase19To30Service(repository, new ObjectMapper(), medicalFileStorage);
    }

    @Test
    void rejectedMedicalFileMustContainReason() {
        when(repository.findMedicalFile("medical_1")).thenReturn(Optional.of(medicalFile()));

        assertThrows(BusinessRuleException.class, () -> service.reviewMedicalFile(
                ADMIN,
                "medical_1",
                new MedicalFileDtos.ReviewRequest("REJECTED", "", false, List.of())));

        verify(repository, never()).updateMedicalFileReview(anyString(), anyString(), any(), anyString());
    }

    @Test
    void approvedMedicalFileCanCreateArchiveReviewTask() {
        when(repository.findMedicalFile("medical_1")).thenReturn(Optional.of(medicalFile()));

        MedicalFileDtos.ReviewResponse response = service.reviewMedicalFile(
                ADMIN,
                "medical_1",
                new MedicalFileDtos.ReviewRequest(
                        "APPROVED",
                        "资料清晰",
                        true,
                        List.of(Map.of("fieldName", "allergies", "newValue", "青霉素"))));

        assertEquals("APPROVED", response.auditStatus());
        verify(repository).insertHealthReviewTask(
                anyString(), isNull(), eq("MEDICAL_FILE"), isNull(), eq("elder_demo"),
                eq("allergies"), isNull(), eq("青霉素"), eq("MEDICAL_FILE"),
                eq("medical_1"), eq("admin_demo"));
        verify(repository).insertOperationLog(
                anyString(), eq("admin_demo"), eq("ADMIN"), eq("REVIEW_MEDICAL_FILE"),
                eq("MEDICAL_FILE"), eq("medical_1"), anyString(), anyString(), anyString());
    }

    @Test
    void archiveExtractionRequiresAtLeastOneStructuredItem() {
        when(repository.findMedicalFile("medical_1")).thenReturn(Optional.of(medicalFile()));

        assertThrows(BusinessRuleException.class, () -> service.reviewMedicalFile(
                ADMIN, "medical_1",
                new MedicalFileDtos.ReviewRequest("APPROVED", "资料清晰", true, List.of())));

        verify(repository, never()).updateMedicalFileReview(anyString(), anyString(), any(), anyString());
    }

    @Test
    void nurseSuggestionChecksOrderSourceAndCreatesPendingReviewTask() {
        Map<String, Object> order = Map.of(
                "order_id", "order_1", "elder_id", "elder_demo", "order_status", "SERVING");
        when(repository.findOrder("order_1")).thenReturn(order);
        when(repository.nurseOwnsOrder("nurse_demo", "order_1")).thenReturn(true);
        when(repository.sourceBelongsToOrder("SERVICE_RECORD", "record_1", "order_1")).thenReturn(true);
        when(repository.findPendingSuggestion(
                "order_1", "riskTags", "跌倒风险", "SERVICE_RECORD", "record_1"))
                .thenReturn(Optional.empty());

        HealthArchiveDtos.SuggestionResponse response = service.createHealthSuggestion(
                NURSE, "order_1", new HealthArchiveDtos.SuggestionRequest(
                        "riskTags", "跌倒风险", "service_record", "record_1", "服务中发现"));

        assertEquals("PENDING", response.status());
        verify(repository).insertHealthReviewTask(
                anyString(), anyString(), eq("HEALTH_UPDATE"), eq("order_1"), eq("elder_demo"),
                eq("riskTags"), isNull(), eq("跌倒风险"), eq("SUGGESTION"),
                anyString(), eq("nurse_demo"));
        verify(repository).insertHealthSuggestion(
                anyString(), anyString(), eq("order_1"), eq("elder_demo"), eq("riskTags"),
                eq("跌倒风险"), eq("SERVICE_RECORD"), eq("record_1"),
                eq("服务中发现"), eq("nurse_demo"));
    }

    @Test
    void familyCannotSubmitHealthSuggestion() {
        when(repository.findOrder("order_1")).thenReturn(Map.of(
                "order_id", "order_1", "elder_id", "elder_demo",
                "family_id", "family_demo", "order_status", "SERVING"));

        assertThrows(ForbiddenException.class, () -> service.createHealthSuggestion(
                FAMILY, "order_1", new HealthArchiveDtos.SuggestionRequest(
                        "riskTags", "跌倒风险", "SERVICE_RECORD", "record_1", "观察所得")));
    }

    @Test
    void archiveReviewWritesAuditLogAndIncrementsVersion() {
        when(repository.findHealthReviewTaskForUpdate("review_1"))
                .thenReturn(Optional.of(new HealthReviewTaskEntity(
                        "review_1", "elder_demo", "PENDING", "2", "allergies",
                        null, "青霉素", "SUGGESTION", "suggestion_1", null)));
        when(repository.currentArchiveVersion("elder_demo")).thenReturn(2);

        HealthArchiveDtos.ArchiveResponse response = service.archiveHealthReviewTask(
                ADMIN,
                "review_1",
                new HealthArchiveDtos.ArchiveRequest(List.of(
                        new HealthArchiveDtos.ArchiveDecision(
                                "allergies", "allergies", "青霉素", "APPROVED", "确认入档"))));

        assertEquals("3", response.archiveVersion());
        verify(repository).updateArchiveVersion("elder_demo", 3, "admin_demo");
        verify(repository).upsertAllergy(
                anyString(), eq("elder_demo"), eq("青霉素"), isNull(), isNull(), isNull());
        verify(repository).insertArchiveChangeLog(
                anyString(), eq("elder_demo"), eq("admin_demo"), anyString(), anyString());
        verify(repository).finishHealthReviewTask(
                "review_1", "APPROVED", "admin_demo", "ARCHIVE_REVIEW_APPROVED");
    }

    @Test
    void rejectedArchiveDecisionDoesNotModifyArchiveOrVersion() {
        when(repository.findHealthReviewTaskForUpdate("review_2"))
                .thenReturn(Optional.of(new HealthReviewTaskEntity(
                        "review_2", "elder_demo", "PENDING", "4", "careSummary",
                        "原摘要", "新摘要", "SUGGESTION", "suggestion_2", null)));
        when(repository.currentArchiveVersion("elder_demo")).thenReturn(4);

        HealthArchiveDtos.ArchiveResponse response = service.archiveHealthReviewTask(
                ADMIN, "review_2", new HealthArchiveDtos.ArchiveRequest(List.of(
                        new HealthArchiveDtos.ArchiveDecision(
                                "careSummary", "careSummary", "新摘要", "REJECTED", "证据不足"))));

        assertEquals("REJECTED", response.status());
        assertEquals("4", response.archiveVersion());
        verify(repository, never()).updateArchiveVersion(anyString(), anyInt(), anyString());
        verify(repository, never()).updateCareSummary(anyString(), anyString(), anyString());
        verify(repository).finishHealthReviewTask(
                "review_2", "REJECTED", "admin_demo", "ARCHIVE_REVIEW_REJECTED");
    }

    @Test
    void nurseCannotReadPreServiceSummaryAfterAllowedWindow() {
        when(repository.findOrder("order_1")).thenReturn(Map.of(
                "order_id", "order_1", "elder_id", "elder_demo", "order_status", "WAIT_REPORT"));
        when(repository.nurseOwnsOrder("nurse_demo", "order_1")).thenReturn(true);

        assertThrows(ConflictException.class,
                () -> service.preServiceHealthSummary(NURSE, "order_1"));
    }

    @Test
    void pendingQualificationCannotBeSubmittedAgain() {
        when(repository.findCurrentQualification("nurse_demo"))
                .thenReturn(Optional.of(new QualificationApplicationEntity(
                        "qualification_1", "nurse_demo", "PENDING", null)));

        QualificationDtos.ApplicationRequest request = new QualificationDtos.ApplicationRequest(
                "护理甲", "430***********1234", "CERT-001", List.of("file_1"), List.of("SKILL_BASIC"));

        assertThrows(ConflictException.class, () -> service.submitQualification(NURSE, request));
        verify(repository, never()).insertNurseCertificate(
                anyString(), anyString(), anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void approvedTrainingMustHaveFutureExpiration() {
        QualificationDtos.TrainingReviewRequest request = new QualificationDtos.TrainingReviewRequest(
                "APPROVED", "2026-A", LocalDateTime.now().minusDays(1).toString(), "通过");

        assertThrows(BusinessRuleException.class,
                () -> service.reviewTraining(ADMIN, "nurse_demo", request));
        verify(repository, never()).saveTrainingReview(
                anyString(), anyString(), anyString(), anyString(), any(), any(), anyString());
    }

    @Test
    void recommendationRequiresActiveOrderCreateScopeAndPersistsReason() {
        when(repository.findActiveBinding("family_demo", "elder_demo"))
                .thenReturn(Optional.of(Map.of("scope_codes", "[\"ORDER_CREATE\"]")));
        when(repository.findRecommendableNurses("service_1")).thenReturn(List.of(
                new NurseRecommendationEntity(
                        "nurse_demo", "护理甲", new BigDecimal("95.5"),
                        List.of("SKILL_BASIC"), "资质和培训有效，匹配技能：SKILL_BASIC", true)));

        RecommendationDtos.RecommendResponse response = service.recommendNurses(
                FAMILY,
                new RecommendationDtos.RecommendRequest(
                        "elder_demo", "service_1", "2026-07-12T09:00:00", "address_1"));

        assertEquals(1, response.nurses().size());
        ArgumentCaptor<NurseRecommendationEntity> recommendationCaptor =
                ArgumentCaptor.forClass(NurseRecommendationEntity.class);
        verify(repository).insertRecommendationLog(
                anyString(), anyString(), eq(null), eq("elder_demo"), eq("service_1"),
                eq("address_1"), any(), recommendationCaptor.capture(), eq("family_demo"));
        assertTrue(recommendationCaptor.getValue().recommendReason().contains("匹配技能"));
    }

    @Test
    void preferredNurseSelectionDoesNotDispatchOrder() {
        Map<String, Object> order = Map.of(
                "order_id", "order_1",
                "elder_id", "elder_demo",
                "family_id", "family_demo",
                "order_status", "WAIT_DISPATCH");
        when(repository.findOrder("order_1")).thenReturn(order);
        when(repository.findActiveBinding("family_demo", "elder_demo"))
                .thenReturn(Optional.of(Map.of("scope_codes", "[\"ORDER_CREATE\"]")));
        when(repository.findOrderRecommendation("order_1", "nurse_demo"))
                .thenReturn(Optional.of(new NurseRecommendationEntity(
                        "nurse_demo", "护理甲", BigDecimal.TEN, List.of(), "综合评分推荐", true)));

        RecommendationDtos.PreferredNurseResponse response = service.choosePreferredNurse(
                FAMILY, "order_1", new RecommendationDtos.PreferredNurseRequest("nurse_demo"));

        assertEquals("nurse_demo", response.preferredNurseId());
        verify(repository).updatePreferredNurse("order_1", "nurse_demo");
        verify(repository, never()).insertHealthReviewTask(
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    private MedicalFileEntity medicalFile() {
        return new MedicalFileEntity(
                "medical_1", "file_1", "elder_demo", "REPORT", "检查报告",
                "2026-07-10T08:00:00", "PENDING", null);
    }
}
