package com.csu.carenest.careadmin.support;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** 阶段44-46回访、投诉和护理申诉冻结字段。 */
public final class SupportDtos {

    private SupportDtos() {
    }

    public record FollowUpRequest(
            @NotBlank String method,
            @NotBlank @Size(max = 800) String content,
            LocalDateTime nextFollowUpAt,
            @NotBlank @Size(max = 128) String result) {
    }

    public record FollowUpResponse(
            String followUpId,
            String ticketStatus,
            String method,
            String content,
            LocalDateTime nextFollowUpAt,
            String result,
            LocalDateTime createdAt) {
    }

    public record ReviewComplaintRequest(
            @Min(1) @Max(5) Integer rating,
            @NotNull @Size(max = 20) List<@NotBlank @Size(max = 64) String> tags,
            @Size(max = 500) String content,
            @Size(max = 64) String reasonType,
            @NotNull @Size(max = 10) List<@NotBlank @Size(max = 32) String> fileIds) {
    }

    public record ReviewComplaintResponse(
            String reviewId,
            String complaintId,
            String orderId,
            String serviceName,
            String complainantName,
            String status,
            Integer rating,
            List<String> tags,
            String reasonType,
            String content,
            List<String> fileIds,
            String handleResult,
            LocalDateTime createdAt) {
    }

    public record AppealRequest(
            @NotBlank @Size(max = 64) String targetType,
            @NotBlank @Size(max = 32) String targetId,
            @NotBlank @Size(max = 1000) String reason,
            @NotNull @Size(max = 10) List<@NotBlank @Size(max = 32) String> fileIds) {
    }

    public record AppealResponse(
            String appealId,
            String nurseId,
            String nurseName,
            String targetType,
            String targetId,
            String targetLabel,
            String reason,
            List<String> fileIds,
            String status,
            BigDecimal scoreAdjustment,
            String reviewComment,
            LocalDateTime createdAt) {
    }
}
