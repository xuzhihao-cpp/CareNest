package com.csu.carenest.user.healtharchive;

import com.csu.carenest.user.auth.AuthService;
import com.csu.carenest.user.auth.RoleCode;
import com.csu.carenest.user.common.ApiException;
import com.csu.carenest.user.common.ForbiddenException;
import com.csu.carenest.user.common.NotFoundException;
import com.csu.carenest.user.redis.HomeCacheInvalidator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
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
    private static final Map<String, String> RISK_LABELS = Map.of(
            "FALL_RISK", "跌倒风险",
            "PRESSURE_INJURY_RISK", "压疮风险",
            "SWALLOWING_RISK", "吞咽风险",
            "MEDICATION_RISK", "用药风险",
            "WANDERING_RISK", "走失风险",
            "ALLERGY_RISK", "过敏风险");

    private final AuthService authService;
    private final HealthArchiveRepository repository;
    private final ObjectMapper objectMapper;
    private final HomeCacheInvalidator homeCacheInvalidator;

    public HealthArchiveService(
            AuthService authService,
            HealthArchiveRepository repository,
            ObjectMapper objectMapper,
            HomeCacheInvalidator homeCacheInvalidator) {
        this.authService = authService;
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.homeCacheInvalidator = homeCacheInvalidator;
    }

    public HealthArchiveDtos.ArchiveResponse getArchive(String authorization, String elderId) {
        AuthService.CurrentUser currentUser = authService.requireCurrentUser(authorization);
        HealthArchiveRepository.ElderRow elder = repository.findElder(elderId).orElseThrow(NotFoundException::new);
        requireReadAccess(currentUser, elder);
        return assembleArchive(elderId);
    }

    public List<HealthArchiveDtos.ArchiveChangeLogResponse> getArchiveChangeLogs(
            String authorization,
            String elderId) {
        AuthService.CurrentUser currentUser = authService.requireCurrentUser(authorization);
        HealthArchiveRepository.ElderRow elder = repository.findElder(elderId).orElseThrow(NotFoundException::new);
        requireReadAccess(currentUser, elder);
        return repository.findArchiveChangeLogs(elderId, 20).stream()
                .map(this::toArchiveChangeLogResponse)
                .toList();
    }

    private HealthArchiveDtos.ArchiveChangeLogResponse toArchiveChangeLogResponse(
            HealthArchiveRepository.ArchiveChangeLogRow row) {
        JsonNode after = readJsonObject(row.afterValue());
        String normalizedValue = jsonValue(after, "normalizedValue");
        return new HealthArchiveDtos.ArchiveChangeLogResponse(
                row.changeLogId(),
                jsonValue(after, "targetField"),
                row.changeType(),
                row.beforeValue(),
                "REVIEW_ARCHIVE".equals(row.changeType()) && normalizedValue != null
                        ? normalizedValue
                        : row.afterValue(),
                jsonValue(after, "comment"),
                jsonValue(after, "archiveVersion"),
                row.changedAt());
    }

    private JsonNode readJsonObject(String value) {
        if (value == null || value.isBlank()) {
            return objectMapper.createObjectNode();
        }
        try {
            JsonNode node = objectMapper.readTree(value);
            return node != null && node.isObject() ? node : objectMapper.createObjectNode();
        } catch (JsonProcessingException exception) {
            return objectMapper.createObjectNode();
        }
    }

    private String jsonValue(JsonNode object, String fieldName) {
        JsonNode value = object.path(fieldName);
        if (value.isMissingNode() || value.isNull()) {
            return null;
        }
        return value.isValueNode() ? value.asText() : value.toString();
    }

    @Transactional
    public HealthArchiveDtos.ArchiveUpdateResult updateArchive(
            String authorization,
            String elderId,
            HealthArchiveDtos.ArchiveUpdateRequest request) {
        AuthService.CurrentUser currentUser = authService.requireCurrentUser(authorization);
        HealthArchiveRepository.ElderRow elder = repository.findElder(elderId).orElseThrow(NotFoundException::new);
        requireWriteAccess(currentUser, elderId);
        validateUpdate(request);
        HealthArchiveDtos.ArchiveResponse before = assembleArchive(elderId);

        if (repository.advanceArchiveVersion(elderId, request.archiveVersion(), currentUser.userId()) != 1) {
            throw new ApiException(409, "档案已被更新，请刷新后再保存");
        }

        repository.replaceDiseases(elderId, request.diseases());
        repository.replaceMedications(elderId, request.medications().stream()
                .map(input -> new HealthArchiveRepository.SerializedMedication(input, writeJson(input.timePoints())))
                .toList());
        repository.replaceAllergies(elderId, request.allergies());
        repository.replaceRiskTags(elderId, request.riskTags(), RISK_LABELS);
        repository.replaceCarePlan(elderId, writeJson(request.carePlan()));

        HealthArchiveDtos.ArchiveResponse after = assembleArchive(elderId);
        String beforeJson = writeJson(before);
        String afterJson = writeJson(after);
        repository.insertChangeLog(elderId, currentUser.userId(), "FAMILY_EDIT", beforeJson, afterJson);
        repository.insertOperationLog(
                currentUser.userId(), "UPDATE_HEALTH_ARCHIVE", elderId, beforeJson, afterJson);
        evictHomesAfterCommit(elder);
        return new HealthArchiveDtos.ArchiveUpdateResult(after.archiveVersion(), after.updatedAt());
    }

    @Transactional
    public HealthArchiveDtos.MedicationCreateResult addMedication(
            String authorization,
            String elderId,
            HealthArchiveDtos.MedicationCreateRequest request) {
        AuthService.CurrentUser currentUser = authService.requireCurrentUser(authorization);
        HealthArchiveRepository.ElderRow elder = repository.findElder(elderId).orElseThrow(NotFoundException::new);
        requireWriteAccess(currentUser, elderId);
        if (request.endDate() != null && request.endDate().isBefore(request.startDate())) {
            throw new ApiException(422, "用药结束日期不能早于开始日期");
        }
        String normalizedName = request.medicationName().trim().toLowerCase(Locale.ROOT);
        if (repository.medicationNameExists(elderId, normalizedName)) {
            throw new ApiException(422, "当前用药存在重复药物");
        }
        HealthArchiveDtos.ArchiveResponse before = assembleArchive(elderId);
        if (repository.advanceArchiveVersion(elderId, request.archiveVersion(), currentUser.userId()) != 1) {
            throw new ApiException(409, "档案已被更新，请刷新后再保存");
        }

        HealthArchiveDtos.MedicationInput input = new HealthArchiveDtos.MedicationInput(
                request.medicationName(), request.dosage(), request.frequency(), request.timePoints(),
                request.startDate(), request.endDate(), request.remark());
        repository.insertMedication(
                elderId,
                new HealthArchiveRepository.SerializedMedication(input, writeJson(input.timePoints())));

        HealthArchiveDtos.ArchiveResponse after = assembleArchive(elderId);
        String beforeJson = writeJson(before);
        String afterJson = writeJson(after);
        repository.insertChangeLog(elderId, currentUser.userId(), "FAMILY_EDIT", beforeJson, afterJson);
        repository.insertOperationLog(
                currentUser.userId(), "ADD_HEALTH_ARCHIVE_MEDICATION", elderId, beforeJson, afterJson);
        evictHomesAfterCommit(elder);

        HealthArchiveDtos.MedicationItem medication = after.medications().stream()
                .filter(item -> item.medicationName().equals(request.medicationName().trim()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Inserted medication was not readable"));
        return new HealthArchiveDtos.MedicationCreateResult(after.archiveVersion(), medication);
    }

    private HealthArchiveDtos.ArchiveResponse assembleArchive(String elderId) {
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

    private void requireWriteAccess(AuthService.CurrentUser currentUser, String elderId) {
        if (currentUser.roles().contains(RoleCode.FAMILY)
                && hasScope(currentUser.userId(), elderId, "HEALTH_EDIT")) {
            return;
        }
        throw new ForbiddenException();
    }

    private void validateUpdate(HealthArchiveDtos.ArchiveUpdateRequest request) {
        requireUnique(
                request.diseases().stream().map(HealthArchiveDtos.DiseaseInput::diseaseName).toList(),
                "慢病记录存在重复项目");
        requireUnique(
                request.medications().stream().map(HealthArchiveDtos.MedicationInput::medicationName).toList(),
                "当前用药存在重复药物");
        requireUnique(
                request.allergies().stream().map(HealthArchiveDtos.AllergyInput::allergenName).toList(),
                "过敏记录存在重复项目");
        requireUnique(request.riskTags(), "风险标签存在重复项目");
        if (request.riskTags().stream().anyMatch(tag -> !RISK_LABELS.containsKey(tag))) {
            throw new ApiException(422, "风险标签不支持");
        }
        if (request.medications().stream()
                .anyMatch(item -> item.endDate() != null && item.endDate().isBefore(item.startDate()))) {
            throw new ApiException(422, "用药结束日期不能早于开始日期");
        }
    }

    private void requireUnique(List<String> values, String message) {
        Set<String> normalized = new HashSet<>();
        boolean duplicate = values.stream()
                .map(value -> value.trim().toLowerCase(Locale.ROOT))
                .anyMatch(value -> !normalized.add(value));
        if (duplicate) {
            throw new ApiException(422, message);
        }
    }

    private void evictHomesAfterCommit(HealthArchiveRepository.ElderRow elder) {
        if (elder.userId() != null) {
            homeCacheInvalidator.evictAfterCommit(RoleCode.ELDER.name(), elder.userId());
        }
        for (String familyId : repository.findActiveFamilyIds(elder.elderId())) {
            homeCacheInvalidator.evictAfterCommit(RoleCode.FAMILY.name(), familyId);
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Health archive JSON serialization failed", exception);
        }
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
