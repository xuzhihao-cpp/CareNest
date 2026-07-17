package com.csu.carenest.careadmin.phase;

import com.csu.carenest.careadmin.auth.AuthService;
import com.csu.carenest.careadmin.auth.CurrentUser;
import com.csu.carenest.careadmin.auth.RoleCode;
import com.csu.carenest.careadmin.common.ApiResponse;
import com.csu.carenest.careadmin.common.PageData;
import com.csu.carenest.careadmin.common.NotFoundException;
import com.csu.carenest.careadmin.phase.dto.HealthArchiveDtos;
import com.csu.carenest.careadmin.phase.dto.MedicalFileDtos;
import com.csu.carenest.careadmin.phase.dto.QualificationDtos;
import com.csu.carenest.careadmin.phase.dto.RecommendationDtos;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import java.nio.charset.StandardCharsets;

import java.util.List;
import java.util.Map;

/**
 * 成员3阶段21、23至30接口入口。
 *
 * <p>控制器只做认证、角色校验和参数接收，数据归属与状态流转由业务服务二次校验。</p>
 */
@RestController
@RequestMapping("/api/v1")
public class Phase19To30Controller {

    private final AuthService authService;
    private final Phase19To30Service phaseService;

    public Phase19To30Controller(AuthService authService, Phase19To30Service phaseService) {
        this.authService = authService;
        this.phaseService = phaseService;
    }

    @GetMapping("/dictionaries/{dictCode}")
    public ApiResponse<Map<String, Object>> dictionary(
            @RequestHeader("Authorization") String authorization,
            @PathVariable("dictCode") String dictCode) {
        authService.requireCurrentUser(authorization);
        if (!"nurseServiceSkill".equals(dictCode)) {
            throw new NotFoundException();
        }
        return ApiResponse.success(Map.of(
                "dictCode", dictCode,
                "items", phaseService.qualificationSkillOptions()));
    }

    @GetMapping("/admin/medical-files")
    public ApiResponse<PageData<MedicalFileDtos.MedicalFileItem>> medicalFiles(
            @RequestHeader("Authorization") String authorization,
            @RequestParam(name = "auditStatus", required = false) String auditStatus,
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "size", defaultValue = "10") int size) {
        authService.requireAnyRole(authorization, RoleCode.ADMIN, RoleCode.CUSTOMER_SERVICE);
        return ApiResponse.success(phaseService.medicalFiles(auditStatus, page, size));
    }

    @GetMapping("/admin/medical-files/{fileId}")
    public ApiResponse<MedicalFileDtos.MedicalFileItem> medicalFile(
            @RequestHeader("Authorization") String authorization,
            @PathVariable("fileId") String fileId) {
        authService.requireAnyRole(authorization, RoleCode.ADMIN, RoleCode.CUSTOMER_SERVICE);
        return ApiResponse.success(phaseService.medicalFile(fileId));
    }

    @GetMapping("/admin/medical-files/{fileId}/preview")
    public ResponseEntity<byte[]> medicalFilePreview(
            @RequestHeader("Authorization") String authorization,
            @PathVariable("fileId") String fileId,
            @RequestParam(name = "download", defaultValue = "false") boolean download) {
        authService.requireAnyRole(authorization, RoleCode.ADMIN, RoleCode.CUSTOMER_SERVICE);
        Phase19To30Service.MedicalFilePreview preview = phaseService.adminMedicalFilePreview(fileId);
        MediaType mediaType;
        try {
            mediaType = MediaType.parseMediaType(preview.mimeType());
        } catch (Exception ignored) {
            mediaType = MediaType.APPLICATION_OCTET_STREAM;
        }
        ContentDisposition disposition = (download ? ContentDisposition.attachment() : ContentDisposition.inline())
                .filename(preview.originalName(), StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .contentType(mediaType)
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .body(preview.content());
    }

    @PostMapping("/admin/medical-files/{fileId}/review")
    public ApiResponse<MedicalFileDtos.ReviewResponse> reviewMedicalFile(
            @RequestHeader("Authorization") String authorization,
            @PathVariable("fileId") String fileId,
            @Valid @RequestBody MedicalFileDtos.ReviewRequest request) {
        CurrentUser currentUser = authService.requireAnyRole(
                authorization, RoleCode.ADMIN, RoleCode.CUSTOMER_SERVICE);
        return ApiResponse.success(phaseService.reviewMedicalFile(currentUser, fileId, request));
    }

    @GetMapping("/admin/health-review-tasks/{taskId}")
    public ApiResponse<HealthArchiveDtos.ReviewTaskResponse> healthReviewTask(
            @RequestHeader("Authorization") String authorization,
            @PathVariable("taskId") String taskId) {
        authService.requireAnyRole(authorization, RoleCode.ADMIN, RoleCode.CUSTOMER_SERVICE);
        return ApiResponse.success(phaseService.healthReviewTask(taskId));
    }

    @GetMapping("/admin/elders/{elderId}/health-archive/change-logs")
    public ApiResponse<List<HealthArchiveDtos.ArchiveChangeLogResponse>> archiveChangeLogs(
            @RequestHeader("Authorization") String authorization,
            @PathVariable("elderId") String elderId) {
        authService.requireAnyRole(authorization, RoleCode.ADMIN, RoleCode.CUSTOMER_SERVICE);
        return ApiResponse.success(phaseService.archiveChangeLogs(elderId));
    }

    @PostMapping("/admin/health-review-tasks/{taskId}/archive")
    public ApiResponse<HealthArchiveDtos.ArchiveResponse> archiveHealthReviewTask(
            @RequestHeader("Authorization") String authorization,
            @PathVariable("taskId") String taskId,
            @Valid @RequestBody HealthArchiveDtos.ArchiveRequest request) {
        CurrentUser currentUser = authService.requireAnyRole(
                authorization, RoleCode.ADMIN, RoleCode.CUSTOMER_SERVICE);
        return ApiResponse.success(phaseService.archiveHealthReviewTask(currentUser, taskId, request));
    }

    @GetMapping("/nurse/orders/{orderId}/pre-service-health-summary")
    public ApiResponse<HealthArchiveDtos.PreServiceHealthSummary> preServiceHealthSummary(
            @RequestHeader("Authorization") String authorization,
            @PathVariable("orderId") String orderId) {
        CurrentUser currentUser = authService.requireAnyRole(
                authorization, RoleCode.NURSE, RoleCode.ADMIN);
        return ApiResponse.success(phaseService.preServiceHealthSummary(currentUser, orderId));
    }

    @GetMapping("/nurse/orders/{orderId}/medical-files/{medicalFileId}/preview")
    public ResponseEntity<byte[]> preServiceMedicalFilePreview(
            @RequestHeader("Authorization") String authorization,
            @PathVariable("orderId") String orderId,
            @PathVariable("medicalFileId") String medicalFileId) {
        CurrentUser currentUser = authService.requireAnyRole(authorization, RoleCode.NURSE, RoleCode.ADMIN);
        Phase19To30Service.MedicalFilePreview preview =
                phaseService.preServiceMedicalFilePreview(currentUser, orderId, medicalFileId);
        MediaType mediaType;
        try {
            mediaType = MediaType.parseMediaType(preview.mimeType());
        } catch (Exception ignored) {
            mediaType = MediaType.APPLICATION_OCTET_STREAM;
        }
        return ResponseEntity.ok()
                .contentType(mediaType)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline()
                        .filename(preview.originalName(), StandardCharsets.UTF_8).build().toString())
                .body(preview.content());
    }

    @PostMapping("/nurse/qualification-applications")
    public ApiResponse<QualificationDtos.ApplicationResponse> submitQualification(
            @RequestHeader("Authorization") String authorization,
            @Valid @RequestBody QualificationDtos.ApplicationRequest request) {
        CurrentUser currentUser = authService.requireRole(authorization, RoleCode.NURSE);
        authService.requirePermission(currentUser, "NURSE_QUALIFICATION_SUBMIT");
        return ApiResponse.success(phaseService.submitQualification(currentUser, request));
    }

    @GetMapping("/nurse/qualification-applications/current")
    public ApiResponse<QualificationDtos.ApplicationResponse> currentQualification(
            @RequestHeader("Authorization") String authorization) {
        CurrentUser currentUser = authService.requireRole(authorization, RoleCode.NURSE);
        authService.requirePermission(currentUser, "NURSE_QUALIFICATION_SUBMIT");
        return ApiResponse.success(phaseService.currentQualification(currentUser));
    }

    @GetMapping("/admin/nurse-qualification-applications")
    public ApiResponse<PageData<QualificationDtos.ApplicationResponse>> qualificationApplications(
            @RequestHeader("Authorization") String authorization,
            @RequestParam(name = "auditStatus", required = false) String auditStatus,
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "size", defaultValue = "10") int size) {
        CurrentUser currentUser = authService.requireAnyRole(
                authorization, RoleCode.ADMIN, RoleCode.CUSTOMER_SERVICE);
        authService.requirePermission(currentUser, "NURSE_QUALIFICATION_REVIEW");
        return ApiResponse.success(phaseService.qualificationApplications(auditStatus, page, size));
    }

    @GetMapping("/admin/nurse-qualification-applications/{applicationId}/files/{fileId}/preview")
    public ResponseEntity<byte[]> qualificationFilePreview(
            @RequestHeader("Authorization") String authorization,
            @PathVariable("applicationId") String applicationId,
            @PathVariable("fileId") String fileId) {
        CurrentUser currentUser = authService.requireAnyRole(
                authorization, RoleCode.ADMIN, RoleCode.CUSTOMER_SERVICE);
        authService.requirePermission(currentUser, "NURSE_QUALIFICATION_REVIEW");
        Phase19To30Service.QualificationFilePreview preview =
                phaseService.qualificationFilePreview(applicationId, fileId);
        MediaType mediaType;
        try {
            mediaType = MediaType.parseMediaType(preview.mimeType());
        } catch (Exception ignored) {
            mediaType = MediaType.APPLICATION_OCTET_STREAM;
        }
        return ResponseEntity.ok()
                .contentType(mediaType)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline()
                        .filename(preview.originalName(), StandardCharsets.UTF_8).build().toString())
                .body(preview.content());
    }

    @PostMapping("/admin/nurse-qualification-applications/{applicationId}/review")
    public ApiResponse<QualificationDtos.QualificationReviewResponse> reviewQualification(
            @RequestHeader("Authorization") String authorization,
            @PathVariable("applicationId") String applicationId,
            @Valid @RequestBody QualificationDtos.QualificationReviewRequest request) {
        CurrentUser currentUser = authService.requireAnyRole(
                authorization, RoleCode.ADMIN, RoleCode.CUSTOMER_SERVICE);
        authService.requirePermission(currentUser, "NURSE_QUALIFICATION_REVIEW");
        return ApiResponse.success(phaseService.reviewQualification(currentUser, applicationId, request));
    }

    @GetMapping("/nurse/training-status")
    public ApiResponse<QualificationDtos.TrainingResponse> trainingStatus(
            @RequestHeader("Authorization") String authorization) {
        CurrentUser currentUser = authService.requireRole(authorization, RoleCode.NURSE);
        return ApiResponse.success(phaseService.trainingStatus(currentUser));
    }

    @GetMapping("/admin/nurses/{nurseId}/training-status")
    public ApiResponse<QualificationDtos.TrainingResponse> adminTrainingStatus(
            @RequestHeader("Authorization") String authorization,
            @PathVariable("nurseId") String nurseId) {
        CurrentUser currentUser = authService.requireAnyRole(
                authorization, RoleCode.ADMIN, RoleCode.CUSTOMER_SERVICE);
        authService.requirePermission(currentUser, "NURSE_TRAINING_REVIEW");
        return ApiResponse.success(phaseService.trainingStatusForNurse(nurseId));
    }

    @PostMapping("/admin/nurses/{nurseId}/training-review")
    public ApiResponse<QualificationDtos.TrainingResponse> reviewTraining(
            @RequestHeader("Authorization") String authorization,
            @PathVariable("nurseId") String nurseId,
            @Valid @RequestBody QualificationDtos.TrainingReviewRequest request) {
        CurrentUser currentUser = authService.requireAnyRole(
                authorization, RoleCode.ADMIN, RoleCode.CUSTOMER_SERVICE);
        authService.requirePermission(currentUser, "NURSE_TRAINING_REVIEW");
        return ApiResponse.success(phaseService.reviewTraining(currentUser, nurseId, request));
    }

    @PostMapping("/orders/recommend-nurses")
    public ApiResponse<RecommendationDtos.RecommendResponse> recommendNurses(
            @RequestHeader("Authorization") String authorization,
            @Valid @RequestBody RecommendationDtos.RecommendRequest request) {
        CurrentUser currentUser = authService.requireAnyRole(
                authorization, RoleCode.FAMILY, RoleCode.ADMIN, RoleCode.NURSE);
        authService.requirePermission(currentUser, "NURSE_RECOMMEND_VIEW");
        return ApiResponse.success(phaseService.recommendNurses(currentUser, request));
    }

    @GetMapping("/orders/{orderId}/recommendations")
    public ApiResponse<RecommendationDtos.RecommendResponse> orderRecommendations(
            @RequestHeader("Authorization") String authorization,
            @PathVariable("orderId") String orderId) {
        CurrentUser currentUser = authService.requireAnyRole(
                authorization, RoleCode.FAMILY, RoleCode.ADMIN, RoleCode.NURSE);
        authService.requirePermission(currentUser, "NURSE_RECOMMEND_VIEW");
        return ApiResponse.success(phaseService.orderRecommendations(currentUser, orderId));
    }

    @PutMapping("/family/orders/{orderId}/preferred-nurse")
    public ApiResponse<RecommendationDtos.PreferredNurseResponse> choosePreferredNurse(
            @RequestHeader("Authorization") String authorization,
            @PathVariable("orderId") String orderId,
            @Valid @RequestBody RecommendationDtos.PreferredNurseRequest request) {
        CurrentUser currentUser = authService.requireRole(authorization, RoleCode.FAMILY);
        authService.requirePermission(currentUser, "NURSE_PREFERENCE_SELECT");
        return ApiResponse.success(phaseService.choosePreferredNurse(currentUser, orderId, request));
    }

    @GetMapping("/family/orders/{orderId}/recommendation-view")
    public ApiResponse<RecommendationDtos.PreferredNurseResponse> preferredNurseView(
            @RequestHeader("Authorization") String authorization,
            @PathVariable("orderId") String orderId) {
        CurrentUser currentUser = authService.requireRole(authorization, RoleCode.FAMILY);
        authService.requirePermission(currentUser, "NURSE_PREFERENCE_SELECT");
        return ApiResponse.success(phaseService.preferredNurseView(currentUser, orderId));
    }
}
