package com.csu.carenest.careadmin.phase.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.List;
import java.util.Map;

/**
 * 阶段21病历审核接口使用的 DTO 集合。
 */
public final class MedicalFileDtos {

    private MedicalFileDtos() {
    }

    public record MedicalFileItem(
            String medicalFileId,
            String fileId,
            String elderId,
            String fileType,
            String title,
            String occurredAt,
            String auditStatus,
            String reviewComment) {
    }

    public record ReviewRequest(
            @NotBlank String auditStatus,
            String reviewComment,
            Boolean extractToArchive,
            List<Map<String, Object>> extractedItems) {
    }

    public record ReviewResponse(String fileId, String auditStatus, String reviewedAt) {
    }
}
