package com.csu.carenest.careadmin.delivery;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** 阶段51-55成员3随访、看板和演示交付字段。 */
public final class DeliveryDtos {

    private DeliveryDtos() {
    }

    public record FollowUpRequest(
            @NotBlank @Size(max = 32) String elderId,
            @Size(max = 32) String orderId,
            @NotBlank String followUpType,
            @NotBlank @Size(max = 1000) String content,
            LocalDateTime nextFollowUpAt,
            @NotNull Boolean needReminder) {
    }

    public record FollowUpResponse(String followUpId, String createdReminderTaskId) {
    }

    public record FollowUpRecordResponse(
            String followUpId,
            String elderId,
            String orderId,
            String followUpType,
            String content,
            LocalDateTime nextFollowUpAt,
            boolean needReminder,
            String createdReminderTaskId,
            LocalDateTime createdAt) {
    }

    public record TrendPoint(String date, BigDecimal value) {
    }

    public record BasicStatisticsResponse(
            Map<String, Object> cards,
            List<TrendPoint> orderTrend,
            BigDecimal serviceCompletionRate,
            BigDecimal reminderDoneRate,
            BigDecimal satisfactionAvg) {
    }

    public record QualityStatisticsResponse(
            BigDecimal archiveCompleteRate,
            BigDecimal metricPassRate,
            BigDecimal exceptionApproveRate,
            Map<String, Long> scoreDistribution,
            List<TrendPoint> qualityTrend) {
    }

    public record DemoDataStatusResponse(
            Boolean ready,
            List<String> accounts,
            Integer scenarioCount,
            LocalDateTime lastResetAt) {
    }
}
