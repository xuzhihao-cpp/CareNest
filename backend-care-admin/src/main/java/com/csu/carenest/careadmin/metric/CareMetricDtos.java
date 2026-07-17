package com.csu.carenest.careadmin.metric;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** 阶段34-40请求与响应字段，字段名严格按开工文档冻结。 */
public final class CareMetricDtos {

    private CareMetricDtos() {
    }

    public record CareMetricConfigRequest(
            @NotEmpty @Size(max = 100) List<@Valid CareMetricConfigItem> items) {
    }

    public record CareMetricConfigItem(
            @NotBlank @Size(max = 64) String metricCode,
            @NotBlank @Size(max = 128) String metricName,
            @NotBlank String metricType,
            @NotNull Boolean required,
            @NotBlank String evidenceType,
            @NotNull @DecimalMin("0.00") @DecimalMax("100.00") BigDecimal scoreWeight,
            @Size(max = 500) String description) {
    }

    public record ConfigVersionResponse(
            Integer configVersion,
            List<CareMetricConfigItem> items) {
    }

    public record MetricChecklistResponse(List<MetricChecklistItem> items) {
    }

    public record MetricChecklistItem(
            String itemId,
            String metricCode,
            Boolean required,
            String evidenceType,
            String expectedAction,
            String status,
            BigDecimal scoreWeight) {
    }

    public record EvidenceRequest(
            @Size(max = 32) String metricItemId,
            @Size(max = 32) String fileId,
            @NotBlank String evidenceType,
            @Size(max = 500) String description) {
    }

    public record EvidenceResponse(
            String evidenceId,
            String auditStatus,
            String metricName,
            String evidenceType,
            String description,
            String fileId,
            LocalDateTime submittedAt) {
    }

    public record EvidenceFilePreview(byte[] content, String mimeType, String originalName) {
    }

    public record EvidenceReviewRequest(
            @NotBlank String auditStatus,
            @Size(max = 500) String reviewComment) {
    }

    public record MetricCheckResponse(List<MetricCheckItem> items) {
    }

    public record MetricCheckItem(
            String metricItemId,
            String metricName,
            String checkResult,
            BigDecimal scoreImpact,
            Boolean missingEvidence) {
    }

    public record ExceptionProofRequest(
            @NotBlank String reasonType,
            @NotBlank @Size(max = 500) String reasonText,
            @NotNull @Size(max = 10) List<@NotBlank @Size(max = 32) String> fileIds) {
    }

    public record ExceptionProofResponse(String proofId, String reviewStatus) {
    }

    public record ProofReviewRequest(
            @NotBlank String reviewResult,
            @Size(max = 500) String reviewComment,
            @NotBlank String scoreDecision) {
    }

    public record ProofReviewResponse(
            String proofId,
            String reviewStatus,
            String scoreDecision) {
    }
}
