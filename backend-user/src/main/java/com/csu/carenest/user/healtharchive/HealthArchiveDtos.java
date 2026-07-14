package com.csu.carenest.user.healtharchive;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public final class HealthArchiveDtos {

    private HealthArchiveDtos() {
    }

    public record ArchiveResponse(
            String elderId,
            int archiveVersion,
            List<DiseaseItem> diseases,
            List<MedicationItem> medications,
            List<AllergyItem> allergies,
            List<RiskTagItem> riskTags,
            CarePlanContent carePlan,
            LocalDateTime updatedAt) {
    }

    public record DiseaseItem(
            String diseaseName,
            LocalDate diagnosedAt,
            String status,
            String remark) {
    }

    public record MedicationItem(
            String medicationName,
            String dosage,
            String frequency,
            List<String> timePoints,
            LocalDate startDate,
            LocalDate endDate,
            String remark) {
    }

    public record AllergyItem(
            String allergenName,
            String reaction,
            String severity,
            String remark) {
    }

    public record RiskTagItem(String tagCode, String tagName) {
    }

    public record CarePlanContent(String careGoals, String dailyCare, String precautions) {
        public static CarePlanContent empty() {
            return new CarePlanContent("", "", "");
        }
    }

    public record ArchiveUpdateRequest(
            @Min(1) int archiveVersion,
            @NotNull @Size(max = 50) List<@Valid DiseaseInput> diseases,
            @NotNull @Size(max = 50) List<@Valid MedicationInput> medications,
            @NotNull @Size(max = 50) List<@Valid AllergyInput> allergies,
            @NotNull @Size(max = 20) List<@NotBlank @Size(max = 64) String> riskTags,
            @NotNull @Valid CarePlanInput carePlan) {
    }

    public record DiseaseInput(
            @NotBlank @Size(max = 64) String diseaseName,
            LocalDate diagnosedAt,
            @NotBlank @Pattern(regexp = "ACTIVE|MONITORING|STABLE|RESOLVED") String status,
            @Size(max = 255) String remark) {
    }

    public record MedicationInput(
            @NotBlank @Size(max = 64) String medicationName,
            @Size(max = 64) String dosage,
            @NotBlank @Pattern(regexp = "ONCE_DAILY|TWICE_DAILY|THREE_TIMES_DAILY|EVERY_OTHER_DAY|WEEKLY|AS_NEEDED")
            String frequency,
            @NotNull @Size(min = 1, max = 3)
            List<@Pattern(regexp = "(?:[01]\\d|2[0-3]):[0-5]\\d") String> timePoints,
            @NotNull LocalDate startDate,
            LocalDate endDate,
            @Size(max = 255) String remark) {
    }

    public record AllergyInput(
            @NotBlank @Size(max = 64) String allergenName,
            @Size(max = 128) String reaction,
            @NotBlank @Pattern(regexp = "MILD|MODERATE|SEVERE") String severity,
            @Size(max = 255) String remark) {
    }

    public record CarePlanInput(
            @NotNull @Size(max = 300) String careGoals,
            @NotNull @Size(max = 500) String dailyCare,
            @NotNull @Size(max = 500) String precautions) {
    }

    public record ArchiveUpdateResult(int archiveVersion, LocalDateTime updatedAt) {
    }

    public record MedicationCreateRequest(
            @Min(1) int archiveVersion,
            @NotBlank @Size(max = 64) String medicationName,
            @Size(max = 64) String dosage,
            @NotBlank @Pattern(regexp = "ONCE_DAILY|TWICE_DAILY|THREE_TIMES_DAILY|EVERY_OTHER_DAY|WEEKLY|AS_NEEDED")
            String frequency,
            @NotNull @Size(min = 1, max = 3)
            List<@Pattern(regexp = "(?:[01]\\d|2[0-3]):[0-5]\\d") String> timePoints,
            @NotNull LocalDate startDate,
            LocalDate endDate,
            @Size(max = 255) String remark) {
    }

    public record MedicationCreateResult(int archiveVersion, MedicationItem medication) {
    }
}
