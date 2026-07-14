package com.csu.carenest.user.medicalfile;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.LocalDateTime;

public final class MedicalFileDtos {
    private MedicalFileDtos() {
    }

    public record UploadResult(
            String fileId,
            String url,
            String originalName,
            String mimeType,
            long size,
            String auditStatus) {
    }

    public record RegisterRequest(
            @NotBlank String fileId,
            @NotBlank String fileType,
            @NotBlank @Size(max = 128) String title,
            @NotNull LocalDate occurredAt) {
    }

    public record RegisterResult(String medicalFileId, String fileId, String auditStatus) {
    }

    public record MedicalFileItem(
            String medicalFileId,
            String fileId,
            String auditStatus,
            String fileType,
            String title,
            LocalDate occurredAt,
            LocalDateTime uploadedAt,
            String originalFileName,
            long fileSize,
            String auditOpinion,
            String previewUrl,
            String downloadUrl) {
    }
}
