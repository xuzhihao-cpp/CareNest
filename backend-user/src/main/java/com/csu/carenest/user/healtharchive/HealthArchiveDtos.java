package com.csu.carenest.user.healtharchive;

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
}
