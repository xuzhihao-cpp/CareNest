package com.csu.carenest.careadmin.metric;

import com.csu.carenest.careadmin.auth.AuthService;
import com.csu.carenest.careadmin.auth.CurrentUser;
import com.csu.carenest.careadmin.auth.RoleCode;
import com.csu.carenest.careadmin.common.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.nio.charset.StandardCharsets;
import java.util.List;

/** 阶段34-40护理指标、留档和豁免审核接口入口。 */
@RestController
@RequestMapping("/api/v1")
public class Phase34To40MetricController {

    private final AuthService authService;
    private final Phase34To40MetricService metricService;

    public Phase34To40MetricController(AuthService authService, Phase34To40MetricService metricService) {
        this.authService = authService;
        this.metricService = metricService;
    }

    @GetMapping("/admin/service-items/{serviceId}/care-metric-config")
    public ApiResponse<CareMetricDtos.ConfigVersionResponse> metricConfig(
            @RequestHeader("Authorization") String authorization,
            @PathVariable("serviceId") String serviceId) {
        CurrentUser user = adminUser(authorization);
        return ApiResponse.success(metricService.metricConfig(user, serviceId));
    }

    @PutMapping("/admin/service-items/{serviceId}/care-metric-config")
    public ApiResponse<CareMetricDtos.ConfigVersionResponse> saveMetricConfig(
            @RequestHeader("Authorization") String authorization,
            @PathVariable("serviceId") String serviceId,
            @Valid @RequestBody CareMetricDtos.CareMetricConfigRequest request) {
        CurrentUser user = adminUser(authorization);
        return ApiResponse.success(metricService.saveMetricConfig(user, serviceId, request));
    }

    @PostMapping("/admin/orders/{orderId}/metric-checklist/generate")
    public ApiResponse<CareMetricDtos.MetricChecklistResponse> generateChecklist(
            @RequestHeader("Authorization") String authorization,
            @PathVariable("orderId") String orderId) {
        CurrentUser user = adminUser(authorization);
        return ApiResponse.success(metricService.generateChecklist(user, orderId));
    }

    @GetMapping("/nurse/orders/{orderId}/metric-checklist")
    public ApiResponse<CareMetricDtos.MetricChecklistResponse> checklist(
            @RequestHeader("Authorization") String authorization,
            @PathVariable("orderId") String orderId) {
        CurrentUser user = authService.requireAnyRole(authorization, RoleCode.NURSE, RoleCode.ADMIN);
        return ApiResponse.success(metricService.checklist(user, orderId));
    }

    @PostMapping("/nurse/orders/{orderId}/evidences")
    public ApiResponse<CareMetricDtos.EvidenceResponse> submitEvidence(
            @RequestHeader("Authorization") String authorization,
            @PathVariable("orderId") String orderId,
            @Valid @RequestBody CareMetricDtos.EvidenceRequest request) {
        CurrentUser user = authService.requireAnyRole(authorization, RoleCode.NURSE, RoleCode.ADMIN);
        return ApiResponse.success(metricService.submitEvidence(user, orderId, request));
    }

    @GetMapping("/orders/{orderId}/evidences")
    public ApiResponse<List<CareMetricDtos.EvidenceResponse>> evidences(
            @RequestHeader("Authorization") String authorization,
            @PathVariable("orderId") String orderId) {
        CurrentUser user = authService.requireCurrentUser(authorization);
        return ApiResponse.success(metricService.evidences(user, orderId));
    }

    @GetMapping("/admin/evidences")
    public ApiResponse<List<CareMetricDtos.EvidenceResponse>> pendingEvidences(
            @RequestHeader("Authorization") String authorization) {
        CurrentUser user = adminUser(authorization);
        return ApiResponse.success(metricService.pendingEvidences(user));
    }

    @GetMapping("/admin/evidences/{evidenceId}/preview")
    public ResponseEntity<byte[]> evidencePreview(
            @RequestHeader("Authorization") String authorization,
            @PathVariable("evidenceId") String evidenceId) {
        CurrentUser user = adminUser(authorization);
        CareMetricDtos.EvidenceFilePreview preview = metricService.evidenceFilePreview(user, evidenceId);
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

    @PostMapping("/admin/evidences/{evidenceId}/review")
    public ApiResponse<CareMetricDtos.EvidenceResponse> reviewEvidence(
            @RequestHeader("Authorization") String authorization,
            @PathVariable("evidenceId") String evidenceId,
            @Valid @RequestBody CareMetricDtos.EvidenceReviewRequest request) {
        CurrentUser user = adminUser(authorization);
        return ApiResponse.success(metricService.reviewEvidence(user, evidenceId, request));
    }

    @PostMapping("/orders/{orderId}/metric-check")
    public ApiResponse<CareMetricDtos.MetricCheckResponse> checkMetrics(
            @RequestHeader("Authorization") String authorization,
            @PathVariable("orderId") String orderId) {
        CurrentUser user = authService.requireCurrentUser(authorization);
        return ApiResponse.success(metricService.checkMetrics(user, orderId));
    }

    @GetMapping("/orders/{orderId}/metric-check-result")
    public ApiResponse<CareMetricDtos.MetricCheckResponse> metricCheckResult(
            @RequestHeader("Authorization") String authorization,
            @PathVariable("orderId") String orderId) {
        CurrentUser user = authService.requireCurrentUser(authorization);
        return ApiResponse.success(metricService.metricCheckResult(user, orderId));
    }

    @PostMapping("/nurse/metric-items/{metricItemId}/exception-proofs")
    public ApiResponse<CareMetricDtos.ExceptionProofResponse> submitExceptionProof(
            @RequestHeader("Authorization") String authorization,
            @PathVariable("metricItemId") String metricItemId,
            @Valid @RequestBody CareMetricDtos.ExceptionProofRequest request) {
        CurrentUser user = authService.requireAnyRole(authorization, RoleCode.NURSE, RoleCode.ADMIN);
        return ApiResponse.success(metricService.submitExceptionProof(user, metricItemId, request));
    }

    @GetMapping("/nurse/orders/{orderId}/exception-proofs")
    public ApiResponse<List<CareMetricDtos.ExceptionProofResponse>> exceptionProofs(
            @RequestHeader("Authorization") String authorization,
            @PathVariable("orderId") String orderId) {
        CurrentUser user = authService.requireAnyRole(authorization, RoleCode.NURSE, RoleCode.ADMIN);
        return ApiResponse.success(metricService.exceptionProofs(user, orderId));
    }

    @GetMapping("/admin/metric-exception-proofs")
    public ApiResponse<List<CareMetricDtos.ProofReviewResponse>> pendingExceptionProofs(
            @RequestHeader("Authorization") String authorization) {
        CurrentUser user = adminUser(authorization);
        return ApiResponse.success(metricService.pendingExceptionProofs(user));
    }

    @PostMapping("/admin/metric-exception-proofs/{proofId}/review")
    public ApiResponse<CareMetricDtos.ProofReviewResponse> reviewExceptionProof(
            @RequestHeader("Authorization") String authorization,
            @PathVariable("proofId") String proofId,
            @Valid @RequestBody CareMetricDtos.ProofReviewRequest request) {
        CurrentUser user = adminUser(authorization);
        return ApiResponse.success(metricService.reviewExceptionProof(user, proofId, request));
    }

    private CurrentUser adminUser(String authorization) {
        return authService.requireAnyRole(authorization, RoleCode.ADMIN, RoleCode.CUSTOMER_SERVICE);
    }
}
