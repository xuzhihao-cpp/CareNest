package com.csu.carenest.careadmin.phase.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;
import java.util.Map;

/**
 * 阶段23至25健康建议、审核归档和服务前摘要 DTO。
 */
public final class HealthArchiveDtos {

    private HealthArchiveDtos() {
    }

    public record SuggestionRequest(
            @NotBlank String fieldName,
            @NotBlank String newValue,
            @NotBlank String sourceType,
            @NotBlank String sourceId,
            @NotBlank String reason) {
    }

    public record SuggestionResponse(String suggestionId, String status) {
    }

    public record SuggestionItem(
            String suggestionId,
            String fieldName,
            String oldValue,
            String newValue,
            String sourceType,
            String sourceId,
            String reason,
            String status) {
    }

    public record ReviewTaskResponse(
            String taskId,
            String elderId,
            String status,
            String archiveVersion,
            List<SuggestionItem> suggestions) {
    }

    public record ArchiveDecision(
            @NotBlank String sourceField,
            @NotBlank String targetField,
            @NotBlank String normalizedValue,
            @NotBlank String decision,
            String comment) {
    }

    public record ArchiveRequest(@NotEmpty List<@Valid ArchiveDecision> decisions) {
    }

    public record ArchiveResponse(String taskId, String status, String archiveVersion) {
    }

    public record ArchiveChangeLogResponse(
            String changeLogId,
            String fieldName,
            String changeType,
            String beforeValue,
            String afterValue,
            String comment,
            String archiveVersion,
            String changedAt) {
    }

    public record PreServiceHealthSummary(
            PreServiceElderProfile elderProfile,
            List<RiskTag> riskTags,
            List<Medication> medications,
            List<Disease> diseases,
            List<Allergy> allergies,
            List<ApprovedMedicalFile> approvedMedicalFiles,
            List<RecentReport> recentReports) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record PreServiceElderProfile(
            String elderName,
            String gender,
            String birthDate,
            Integer age,
            String careLevel,
            CarePlan carePlan,
            List<String> carePoints) {
    }

    public record CarePlan(String careGoals, String dailyCare, String precautions) {
    }

    public record RiskTag(String tagCode, String tagName) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Medication(
            String medicationName,
            String dosage,
            String frequency,
            List<String> timePoints,
            String startDate,
            String endDate,
            String remark) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Disease(
            String diseaseName,
            String status,
            String diagnosedAt,
            String remark) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Allergy(
            String allergenName,
            String reaction,
            String severity,
            String remark) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ApprovedMedicalFile(
            String title,
            String fileType,
            String occurredAt,
            String summary,
            String previewUrl) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record RecentReport(
            String serviceName,
            String occurredAt,
            String generatedAt,
            String summary,
            String nursingAdvice,
            List<String> vitalSigns) {
    }
}
