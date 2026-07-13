package com.csu.carenest.careadmin.phase.dto;

import jakarta.validation.constraints.NotBlank;

import java.math.BigDecimal;
import java.util.List;

/**
 * 阶段29至30护理推荐和偏好选择 DTO。
 */
public final class RecommendationDtos {

    private RecommendationDtos() {
    }

    public record RecommendRequest(
            @NotBlank String elderId,
            @NotBlank String serviceId,
            @NotBlank String scheduledStart,
            @NotBlank String addressId) {
    }

    public record NurseItem(
            String nurseId,
            String nurseName,
            BigDecimal score,
            List<String> matchedSkills,
            String recommendReason,
            boolean available) {
    }

    public record RecommendResponse(List<NurseItem> nurses) {
    }

    public record PreferredNurseRequest(@NotBlank String preferredNurseId) {
    }

    public record PreferredNurseResponse(
            String orderId,
            String preferredNurseId,
            String recommendReason) {
    }
}
