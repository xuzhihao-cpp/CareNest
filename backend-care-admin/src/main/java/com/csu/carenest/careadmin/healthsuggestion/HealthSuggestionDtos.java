package com.csu.carenest.careadmin.healthsuggestion;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.LocalDateTime;

public final class HealthSuggestionDtos {
    private HealthSuggestionDtos() {}
    public record CreateRequest(String fieldName, JsonNode newValue, String sourceType, String sourceId, String reason) {}
    public record CreateResult(String suggestionId, String status) {}
    public record ReviewTaskItem(String taskId, String suggestionId, String status, String elderName,
                                 String serviceName, String sourceType, String sourceSummary,
                                 String fieldName, JsonNode currentValue, JsonNode suggestedValue,
                                 String reason, LocalDateTime submittedAt) {}
}
