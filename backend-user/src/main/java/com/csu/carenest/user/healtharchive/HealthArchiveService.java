package com.csu.carenest.user.healtharchive;

import com.csu.carenest.user.auth.AuthService;
import com.csu.carenest.user.auth.RoleCode;
import com.csu.carenest.user.common.ForbiddenException;
import com.csu.carenest.user.common.NotFoundException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class HealthArchiveService {

    private static final Set<String> DISEASE_STATUSES = Set.of("ACTIVE", "MONITORING", "STABLE", "RESOLVED");
    private static final Set<String> MEDICATION_FREQUENCIES = Set.of(
            "ONCE_DAILY", "TWICE_DAILY", "THREE_TIMES_DAILY", "EVERY_OTHER_DAY", "WEEKLY", "AS_NEEDED");
    private static final Map<String, String> LEGACY_FREQUENCIES = Map.of(
            "每日一次", "ONCE_DAILY",
            "每日两次", "TWICE_DAILY",
            "每日三次", "THREE_TIMES_DAILY",
            "隔日一次", "EVERY_OTHER_DAY",
            "每周一次", "WEEKLY",
            "按需使用", "AS_NEEDED");
    private static final Map<String, String> RISK_CODES = Map.of(
            "跌倒风险", "FALL_RISK",
            "压疮风险", "PRESSURE_INJURY_RISK",
            "吞咽风险", "SWALLOWING_RISK",
            "用药风险", "MEDICATION_RISK",
            "走失风险", "WANDERING_RISK",
            "过敏风险", "ALLERGY_RISK");

    private final AuthService authService;
    private final HealthArchiveRepository repository;
    private final ObjectMapper objectMapper;

    public HealthArchiveService(
            AuthService authService,
            HealthArchiveRepository repository,
            ObjectMapper objectMapper) {
        this.authService = authService;
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    public HealthArchiveDtos.ArchiveResponse getArchive(String authorization, String elderId) {
        AuthService.CurrentUser currentUser = authService.requireCurrentUser(authorization);
        HealthArchiveRepository.ElderRow elder = repository.findElder(elderId).orElseThrow(NotFoundException::new);
        requireReadAccess(currentUser, elder);
        HealthArchiveRepository.ArchiveRow archive = repository.findArchive(elderId)
                .orElseThrow(NotFoundException::new);

        List<HealthArchiveDtos.DiseaseItem> diseases = repository.findDiseases(elderId).stream()
                .map(row -> new HealthArchiveDtos.DiseaseItem(
                        row.name(), row.diagnosedAt(), normalizeDiseaseStatus(row.status()), row.remark()))
                .toList();
        List<HealthArchiveDtos.MedicationItem> medications = repository.findMedications(elderId).stream()
                .map(row -> new HealthArchiveDtos.MedicationItem(
                        row.name(), row.dosage(), normalizeFrequency(row.frequency()), readTimePoints(row.timePoints()),
                        row.startDate(), row.endDate(), row.remark()))
                .toList();
        List<HealthArchiveDtos.AllergyItem> allergies = repository.findAllergies(elderId).stream()
                .map(row -> new HealthArchiveDtos.AllergyItem(
                        row.allergen(), row.reaction(), normalizeSeverity(row.severity()), row.remark()))
                .toList();
        List<HealthArchiveDtos.RiskTagItem> riskTags = repository.findRiskTags(elderId).stream()
                .map(row -> new HealthArchiveDtos.RiskTagItem(
                        row.code() == null || row.code().isBlank() ? legacyRiskCode(row.name()) : row.code(), row.name()))
                .toList();
        HealthArchiveDtos.CarePlanContent carePlan = repository.findActiveCarePlanContent(elderId)
                .map(this::readCarePlan)
                .orElseGet(HealthArchiveDtos.CarePlanContent::empty);

        return new HealthArchiveDtos.ArchiveResponse(
                elderId,
                archive.archiveVersion(),
                diseases,
                medications,
                allergies,
                riskTags,
                carePlan,
                archive.updatedAt());
    }

    private void requireReadAccess(AuthService.CurrentUser currentUser, HealthArchiveRepository.ElderRow elder) {
        if (currentUser.roles().contains(RoleCode.ELDER) && currentUser.userId().equals(elder.userId())) {
            return;
        }
        if (currentUser.roles().contains(RoleCode.FAMILY)
                && hasScope(currentUser.userId(), elder.elderId(), "HEALTH_VIEW")) {
            return;
        }
        throw new ForbiddenException();
    }

    private boolean hasScope(String familyId, String elderId, String scope) {
        return repository.findActiveBindingScopes(familyId, elderId).stream()
                .map(this::readScopes)
                .anyMatch(scopes -> scopes.contains(scope));
    }

    private List<String> readScopes(String value) {
        try {
            return objectMapper.readValue(value, new TypeReference<>() {
            });
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Invalid binding scope JSON", exception);
        }
    }

    private List<String> readTimePoints(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(value, new TypeReference<>() {
            });
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Invalid medication time JSON", exception);
        }
    }

    private HealthArchiveDtos.CarePlanContent readCarePlan(String value) {
        if (value == null || value.isBlank()) {
            return HealthArchiveDtos.CarePlanContent.empty();
        }
        try {
            return objectMapper.readValue(value, HealthArchiveDtos.CarePlanContent.class);
        } catch (JsonProcessingException exception) {
            return new HealthArchiveDtos.CarePlanContent("", value, "");
        }
    }

    private String normalizeDiseaseStatus(String value) {
        return DISEASE_STATUSES.contains(value) ? value : "ACTIVE";
    }

    private String normalizeFrequency(String value) {
        if (MEDICATION_FREQUENCIES.contains(value)) {
            return value;
        }
        return LEGACY_FREQUENCIES.getOrDefault(value, "AS_NEEDED");
    }

    private String normalizeSeverity(String value) {
        return switch (value == null ? "" : value) {
            case "SEVERE", "HIGH" -> "SEVERE";
            case "MODERATE", "MEDIUM" -> "MODERATE";
            default -> "MILD";
        };
    }

    private String legacyRiskCode(String name) {
        return RISK_CODES.getOrDefault(name, "CUSTOM_RISK");
    }
}
