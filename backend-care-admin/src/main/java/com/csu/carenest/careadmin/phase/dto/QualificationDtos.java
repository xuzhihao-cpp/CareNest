package com.csu.carenest.careadmin.phase.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/**
 * 阶段26至28护理资质和培训审核 DTO。
 */
public final class QualificationDtos {

    private QualificationDtos() {
    }

    public record ApplicationRequest(
            @NotBlank String realName,
            @NotBlank String idNoMasked,
            @NotBlank String certificateNo,
            @NotEmpty List<@NotBlank String> certificateFileIds,
            @NotEmpty List<@NotBlank String> serviceSkillCodes) {
    }

    public record ApplicationResponse(String applicationId, String auditStatus) {
    }

    public record QualificationReviewRequest(@NotBlank String auditStatus, String reviewComment) {
    }

    public record QualificationReviewResponse(String nurseId, String qualificationStatus) {
    }

    public record TrainingReviewRequest(
            @NotBlank String status,
            @NotBlank String trainingBatch,
            String expiredAt,
            String remark) {
    }

    public record TrainingResponse(String nurseId, String trainingStatus, String expiredAt) {
    }
}
