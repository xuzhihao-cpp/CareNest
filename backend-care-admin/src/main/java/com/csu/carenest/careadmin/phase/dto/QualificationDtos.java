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

    public record CertificateFile(
            String fileId,
            String originalName,
            String mimeType,
            long size,
            boolean previewable) {
    }

    public record ApplicationResponse(
            String applicationId,
            String nurseId,
            String nurseName,
            String auditStatus,
            String realName,
            String idNoMasked,
            String certificateNoMasked,
            List<CertificateFile> certificateFiles,
            List<String> serviceSkillCodes,
            String reviewComment,
            String submittedAt,
            String reviewedAt) {

        public ApplicationResponse(String applicationId, String auditStatus) {
            this(applicationId, null, null, auditStatus, null, null, null,
                    List.of(), List.of(), null, null, null);
        }
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

    public record TrainingResponse(
            String nurseId,
            String nurseName,
            String qualificationStatus,
            String trainingStatus,
            String trainingBatch,
            String passedAt,
            String expiredAt,
            String remark) {

        public TrainingResponse(String nurseId, String trainingStatus, String expiredAt) {
            this(nurseId, null, null, trainingStatus, null, null, expiredAt, null);
        }
    }

    public record SkillOption(String value, String label, int sort) {
    }
}
