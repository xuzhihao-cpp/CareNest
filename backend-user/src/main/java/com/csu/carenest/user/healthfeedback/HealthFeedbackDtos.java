package com.csu.carenest.user.healthfeedback;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.LocalDateTime;
import java.util.List;

public final class HealthFeedbackDtos {
    private HealthFeedbackDtos() {}

    @JsonIgnoreProperties(ignoreUnknown = false)
    public record CreateRequest(String feedbackType, String severity, String content,
                                String inputType, String fileId, String elderId) {}
    public record CreateResult(String feedbackId, LocalDateTime createdAt, String aiAdvice) {}
    public record Item(String feedbackId, String elderId, String elderName, String feedbackType,
                       String severity, String content, String inputType, String fileId,
                       String voiceUrl, LocalDateTime createdAt) {}
    public record PageResult<T>(List<T> records, long total, int page, int size) {}
}
