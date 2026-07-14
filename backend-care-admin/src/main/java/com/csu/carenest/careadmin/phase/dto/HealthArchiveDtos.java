package com.csu.carenest.careadmin.phase.dto;

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

    public record PreServiceHealthSummary(
            Map<String, Object> elderProfile,
            List<String> riskTags,
            List<Map<String, Object>> medications,
            List<Map<String, Object>> diseases,
            List<Map<String, Object>> allergies,
            List<Map<String, Object>> approvedMedicalFiles,
            List<Map<String, Object>> recentReports) {
    }
}
