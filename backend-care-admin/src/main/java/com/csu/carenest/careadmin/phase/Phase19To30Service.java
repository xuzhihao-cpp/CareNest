package com.csu.carenest.careadmin.phase;

import com.csu.carenest.careadmin.auth.CurrentUser;
import com.csu.carenest.careadmin.auth.RoleCode;
import com.csu.carenest.careadmin.common.BusinessRuleException;
import com.csu.carenest.careadmin.common.ConflictException;
import com.csu.carenest.careadmin.common.ForbiddenException;
import com.csu.carenest.careadmin.common.NotFoundException;
import com.csu.carenest.careadmin.common.PageData;
import com.csu.carenest.careadmin.phase.dto.HealthArchiveDtos;
import com.csu.carenest.careadmin.phase.dto.MedicalFileDtos;
import com.csu.carenest.careadmin.phase.dto.QualificationDtos;
import com.csu.carenest.careadmin.phase.dto.RecommendationDtos;
import com.csu.carenest.careadmin.phase.entity.HealthReviewTaskEntity;
import com.csu.carenest.careadmin.phase.entity.MedicalFileEntity;
import com.csu.carenest.careadmin.phase.entity.NurseRecommendationEntity;
import com.csu.carenest.careadmin.phase.entity.QualificationApplicationEntity;
import com.csu.carenest.careadmin.phase.entity.TrainingRecordEntity;
import com.csu.carenest.careadmin.phase.enums.AuditStatus;
import com.csu.carenest.careadmin.phase.enums.TrainingStatus;
import com.csu.carenest.careadmin.phase.repository.Phase19To30Repository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * 成员3阶段21、23至30的核心业务服务。
 *
 * <p>阶段19、20、22属于其他成员，本服务只读取其产出的数据，不提供同义接口。</p>
 */
@Service
public class Phase19To30Service {

    private static final String PENDING = "PENDING";
    private static final String APPROVED = "APPROVED";
    private static final String REJECTED = "REJECTED";
    private static final String NEED_MORE = "NEED_MORE";
    private static final String ORDER_CREATE_SCOPE = "ORDER_CREATE";
    private static final Set<String> HEALTH_TARGET_FIELDS = Set.of(
            "careSummary", "riskTags", "diseases", "medications", "allergies", "carePlan");
    private static final Set<String> SUGGESTION_SOURCE_TYPES = Set.of(
            "SERVICE_RECORD", "SERVICE_REPORT");
    private static final Set<String> SUGGESTION_ORDER_STATUSES = Set.of(
            "SERVING", "WAIT_REPORT", "WAIT_CONFIRM", "COMPLETED");
    private static final Set<String> PRE_SERVICE_ORDER_STATUSES = Set.of(
            "DISPATCHED", "ACCEPTED", "ON_THE_WAY", "SERVING");

    private final Phase19To30Repository repository;
    private final ObjectMapper objectMapper;

    public Phase19To30Service(Phase19To30Repository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    public PageData<MedicalFileDtos.MedicalFileItem> medicalFiles(
            String auditStatus,
            int page,
            int size) {
        String normalizedStatus = nullableAuditStatus(auditStatus);
        int safePage = safePage(page);
        int safeSize = safeSize(size);
        List<MedicalFileDtos.MedicalFileItem> records = repository
                .findMedicalFiles(normalizedStatus, safeSize, offset(safePage, safeSize))
                .stream()
                .map(this::toMedicalFileItem)
                .toList();
        return new PageData<>(records, repository.countMedicalFiles(normalizedStatus), safePage, safeSize);
    }

    public MedicalFileDtos.MedicalFileItem medicalFile(String fileId) {
        return toMedicalFileItem(requireMedicalFile(fileId));
    }

    @Transactional
    public MedicalFileDtos.ReviewResponse reviewMedicalFile(
            CurrentUser reviewer,
            String fileId,
            MedicalFileDtos.ReviewRequest request) {
        MedicalFileEntity medicalFile = requireMedicalFile(fileId);
        AuditStatus targetStatus = AuditStatus.parse(request.auditStatus());
        requireReviewTarget(targetStatus, request.reviewComment());
        if (Boolean.TRUE.equals(request.extractToArchive()) && targetStatus != AuditStatus.APPROVED) {
            throw new BusinessRuleException();
        }
        if (Boolean.TRUE.equals(request.extractToArchive())
                && (request.extractedItems() == null || request.extractedItems().isEmpty())) {
            throw new BusinessRuleException();
        }
        if (Boolean.TRUE.equals(request.extractToArchive())) {
            for (Map<String, Object> item : request.extractedItems()) {
                canonicalHealthField(requiredMapText(item, "fieldName"));
                requiredMapText(item, "newValue", "value");
            }
        }

        repository.updateMedicalFileReview(
                medicalFile.medicalFileId(),
                targetStatus.name(),
                request.reviewComment(),
                reviewer.userId());

        // 只有审核通过且明确要求提取时才创建归档审核任务，病历内容绝不直接覆盖健康档案。
        if (Boolean.TRUE.equals(request.extractToArchive())) {
            for (Map<String, Object> item : request.extractedItems()) {
                String fieldName = canonicalHealthField(requiredMapText(item, "fieldName"));
                String newValue = requiredMapText(item, "newValue", "value");
                repository.insertHealthReviewTask(
                        nextId("review"), null, "MEDICAL_FILE", null,
                        medicalFile.elderId(), fieldName, mapText(item, "oldValue"), newValue,
                        "MEDICAL_FILE", medicalFile.medicalFileId(), reviewer.userId());
            }
        }
        saveOperationLog(reviewer, "REVIEW_MEDICAL_FILE", "MEDICAL_FILE", medicalFile.medicalFileId(),
                medicalFile, request);
        return new MedicalFileDtos.ReviewResponse(
                fileId,
                targetStatus.name(),
                LocalDateTime.now().toString());
    }

    @Transactional
    public HealthArchiveDtos.SuggestionResponse createHealthSuggestion(
            CurrentUser currentUser,
            String orderId,
            HealthArchiveDtos.SuggestionRequest request) {
        Map<String, Object> order = requireOrder(orderId);
        requireOrderAccess(currentUser, order, true);
        if (!SUGGESTION_ORDER_STATUSES.contains(string(order, "order_status"))) {
            throw new ConflictException();
        }
        String fieldName = canonicalHealthField(request.fieldName());
        String sourceType = request.sourceType().trim().toUpperCase();
        if (!SUGGESTION_SOURCE_TYPES.contains(sourceType)
                || !repository.sourceBelongsToOrder(sourceType, request.sourceId(), orderId)) {
            throw new BusinessRuleException();
        }
        Optional<String> existingSuggestion = repository.findPendingSuggestion(
                orderId, fieldName, request.newValue(), sourceType, request.sourceId());
        if (existingSuggestion.isPresent()) {
            return new HealthArchiveDtos.SuggestionResponse(existingSuggestion.get(), PENDING);
        }
        String suggestionId = nextId("suggestion");
        String taskId = nextId("review");
        String elderId = string(order, "elder_id");

        repository.insertHealthReviewTask(
                taskId, suggestionId, "HEALTH_UPDATE", orderId, elderId,
                fieldName, null, request.newValue(), "SUGGESTION", suggestionId,
                currentUser.userId());
        repository.insertHealthSuggestion(
                suggestionId,
                taskId,
                orderId,
                elderId,
                fieldName,
                request.newValue(),
                sourceType,
                request.sourceId(),
                request.reason(),
                currentUser.userId());
        saveOperationLog(currentUser, "CREATE_HEALTH_UPDATE_SUGGESTION",
                "HEALTH_UPDATE_SUGGESTION", suggestionId, null, request);
        return new HealthArchiveDtos.SuggestionResponse(suggestionId, PENDING);
    }

    public PageData<HealthArchiveDtos.ReviewTaskResponse> healthReviewTasks(
            String status,
            int page,
            int size) {
        String normalizedStatus = nullableAuditStatus(status);
        int safePage = safePage(page);
        int safeSize = safeSize(size);
        List<HealthArchiveDtos.ReviewTaskResponse> records = repository
                .findHealthReviewTasks(normalizedStatus, safeSize, offset(safePage, safeSize))
                .stream()
                .map(this::toReviewTaskResponse)
                .toList();
        return new PageData<>(records, repository.countHealthReviewTasks(normalizedStatus), safePage, safeSize);
    }

    public HealthArchiveDtos.ReviewTaskResponse healthReviewTask(String taskId) {
        return toReviewTaskResponse(requireReviewTask(taskId));
    }

    @Transactional
    public HealthArchiveDtos.ArchiveResponse archiveHealthReviewTask(
            CurrentUser reviewer,
            String taskId,
            HealthArchiveDtos.ArchiveRequest request) {
        HealthReviewTaskEntity task = repository.findHealthReviewTaskForUpdate(taskId)
                .orElseThrow(NotFoundException::new);
        if (!PENDING.equals(task.status()) && !NEED_MORE.equals(task.status())) {
            throw new ConflictException();
        }
        validateArchiveDecisions(request.decisions());

        int currentVersion = repository.currentArchiveVersion(task.elderId());
        boolean hasApproved = request.decisions().stream()
                .anyMatch(item -> APPROVED.equalsIgnoreCase(item.decision()));
        boolean hasNeedMore = request.decisions().stream()
                .anyMatch(item -> NEED_MORE.equalsIgnoreCase(item.decision()));
        int nextVersion = hasApproved ? currentVersion + 1 : currentVersion;
        if (hasApproved) {
            repository.updateArchiveVersion(task.elderId(), nextVersion, reviewer.userId());
        }

        // 每一项归档决定都写入不可省略的审计日志，保留来源字段、目标字段和审核意见。
        for (HealthArchiveDtos.ArchiveDecision decision : request.decisions()) {
            String targetField = canonicalHealthField(decision.targetField());
            if (hasText(task.fieldName()) && !task.fieldName().equals(targetField)) {
                throw new BusinessRuleException();
            }
            if (APPROVED.equalsIgnoreCase(decision.decision())) {
                applyArchiveDecision(task.elderId(), targetField, decision.normalizedValue(), reviewer.userId());
            }
            repository.insertArchiveChangeLog(
                    nextId("archive_log"),
                    task.elderId(),
                    reviewer.userId(),
                    writeJson(Map.of(
                            "archiveVersion", currentVersion,
                            "sourceField", decision.sourceField())),
                    writeJson(Map.of(
                            "archiveVersion", nextVersion,
                            "targetField", targetField,
                            "normalizedValue", decision.normalizedValue(),
                            "decision", decision.decision(),
                            "comment", decision.comment() == null ? "" : decision.comment())));
        }
        String finalStatus = hasNeedMore ? NEED_MORE : hasApproved ? APPROVED : REJECTED;
        repository.finishHealthReviewTask(
                taskId, finalStatus, reviewer.userId(), "ARCHIVE_REVIEW_" + finalStatus);
        saveOperationLog(reviewer, "ARCHIVE_HEALTH_REVIEW", "HEALTH_REVIEW_TASK", taskId, task, request);
        return new HealthArchiveDtos.ArchiveResponse(taskId, finalStatus, String.valueOf(nextVersion));
    }

    @Transactional
    public HealthArchiveDtos.PreServiceHealthSummary preServiceHealthSummary(
            CurrentUser currentUser,
            String orderId) {
        Map<String, Object> order = requireOrder(orderId);
        requireOrderAccess(currentUser, order, true);
        if (currentUser.hasRole(RoleCode.NURSE)
                && !PRE_SERVICE_ORDER_STATUSES.contains(string(order, "order_status"))) {
            throw new ConflictException();
        }
        String elderId = string(order, "elder_id");
        HealthArchiveDtos.PreServiceHealthSummary summary = new HealthArchiveDtos.PreServiceHealthSummary(
                repository.findElderProfile(elderId),
                repository.findRiskTags(elderId),
                repository.findMedications(elderId),
                repository.findDiseases(elderId),
                repository.findAllergies(elderId),
                repository.findApprovedMedicalFiles(elderId),
                repository.findRecentReports(elderId));
        saveOperationLog(currentUser, "VIEW_PRE_SERVICE_HEALTH_SUMMARY",
                "NURSING_ORDER", orderId, null, Map.of("elderId", elderId));
        return summary;
    }

    @Transactional
    public QualificationDtos.ApplicationResponse submitQualification(
            CurrentUser currentUser,
            QualificationDtos.ApplicationRequest request) {
        QualificationApplicationEntity current = repository.findCurrentQualification(currentUser.userId()).orElse(null);
        if (current != null && (PENDING.equals(current.auditStatus()) || APPROVED.equals(current.auditStatus()))) {
            throw new ConflictException();
        }

        String applicationId = nextId("qualification");
        repository.updateNurseProfileForApplication(
                currentUser.userId(), request.realName(), request.idNoMasked(), PENDING);
        String skillsJson = writeJson(request.serviceSkillCodes());
        for (String fileId : request.certificateFileIds()) {
            repository.insertNurseCertificate(
                    nextId("certificate"),
                    applicationId,
                    currentUser.userId(),
                    request.certificateNo(),
                    fileId,
                    skillsJson);
        }
        saveOperationLog(currentUser, "SUBMIT_NURSE_QUALIFICATION", "NURSE_QUALIFICATION",
                applicationId, null, request);
        return new QualificationDtos.ApplicationResponse(applicationId, PENDING);
    }

    public QualificationDtos.ApplicationResponse currentQualification(CurrentUser currentUser) {
        QualificationApplicationEntity application = repository.findCurrentQualification(currentUser.userId())
                .orElseThrow(NotFoundException::new);
        return new QualificationDtos.ApplicationResponse(application.applicationId(), application.auditStatus());
    }

    public PageData<QualificationDtos.ApplicationResponse> qualificationApplications(
            String auditStatus,
            int page,
            int size) {
        String normalizedStatus = nullableAuditStatus(auditStatus);
        int safePage = safePage(page);
        int safeSize = safeSize(size);
        List<QualificationDtos.ApplicationResponse> records = repository
                .findQualificationApplications(normalizedStatus, safeSize, offset(safePage, safeSize))
                .stream()
                .map(item -> new QualificationDtos.ApplicationResponse(item.applicationId(), item.auditStatus()))
                .toList();
        return new PageData<>(records,
                repository.countQualificationApplications(normalizedStatus), safePage, safeSize);
    }

    @Transactional
    public QualificationDtos.QualificationReviewResponse reviewQualification(
            CurrentUser reviewer,
            String applicationId,
            QualificationDtos.QualificationReviewRequest request) {
        QualificationApplicationEntity application = repository.findQualificationApplication(applicationId)
                .orElseThrow(NotFoundException::new);
        AuditStatus targetStatus = AuditStatus.parse(request.auditStatus());
        requireReviewTarget(targetStatus, request.reviewComment());
        repository.reviewQualification(
                applicationId,
                application.nurseId(),
                targetStatus.name(),
                request.reviewComment(),
                reviewer.userId());
        saveOperationLog(reviewer, "REVIEW_NURSE_QUALIFICATION", "NURSE_QUALIFICATION",
                applicationId, application, request);
        return new QualificationDtos.QualificationReviewResponse(
                application.nurseId(), targetStatus.name());
    }

    public QualificationDtos.TrainingResponse trainingStatus(CurrentUser currentUser) {
        TrainingRecordEntity record = repository.findTrainingRecord(currentUser.userId())
                .orElseThrow(NotFoundException::new);
        return new QualificationDtos.TrainingResponse(
                record.nurseId(), effectiveTrainingStatus(record), record.expiredAt());
    }

    @Transactional
    public QualificationDtos.TrainingResponse reviewTraining(
            CurrentUser reviewer,
            String nurseId,
            QualificationDtos.TrainingReviewRequest request) {
        TrainingStatus status = TrainingStatus.parse(request.status());
        LocalDateTime expiredAt = parseNullableDateTime(request.expiredAt());
        if (status == TrainingStatus.APPROVED
                && (expiredAt == null || !expiredAt.isAfter(LocalDateTime.now()))) {
            throw new BusinessRuleException();
        }
        if ((status == TrainingStatus.REJECTED || status == TrainingStatus.NEED_MORE)
                && !hasText(request.remark())) {
            throw new BusinessRuleException();
        }
        repository.saveTrainingReview(
                nextId("training"), nurseId, status.name(), request.trainingBatch(), expiredAt,
                request.remark(), reviewer.userId());
        saveOperationLog(reviewer, "REVIEW_NURSE_TRAINING", "NURSE_TRAINING",
                nurseId, null, request);
        return new QualificationDtos.TrainingResponse(
                nurseId, status.name(), expiredAt == null ? null : expiredAt.toString());
    }

    @Transactional
    public RecommendationDtos.RecommendResponse recommendNurses(
            CurrentUser currentUser,
            RecommendationDtos.RecommendRequest request) {
        requireElderAccess(currentUser, request.elderId(), ORDER_CREATE_SCOPE);
        LocalDateTime scheduledStart = parseDateTime(request.scheduledStart());
        List<NurseRecommendationEntity> recommendations = repository.findRecommendableNurses(request.serviceId());
        String requestKey = nextId("recommend");
        for (NurseRecommendationEntity recommendation : recommendations) {
            // 推荐理由必须落库，后续选择偏好、申诉和评分都可追溯到同一批推荐结果。
            repository.insertRecommendationLog(
                    nextId("recommend_log"), requestKey, null, request.elderId(), request.serviceId(),
                    request.addressId(), scheduledStart, recommendation, currentUser.userId());
        }
        return toRecommendResponse(recommendations);
    }

    public RecommendationDtos.RecommendResponse orderRecommendations(
            CurrentUser currentUser,
            String orderId) {
        Map<String, Object> order = requireOrder(orderId);
        requireOrderAccess(currentUser, order, false);
        List<NurseRecommendationEntity> recommendations = repository.findOrderRecommendations(orderId);
        if (recommendations.isEmpty()) {
            recommendations = repository.findRecommendableNurses(string(order, "service_id"));
            LocalDateTime scheduledStart = toLocalDateTime(order.get("scheduled_start_at"));
            String requestKey = nextId("recommend");
            for (NurseRecommendationEntity recommendation : recommendations) {
                repository.insertRecommendationLog(
                        nextId("recommend_log"), requestKey, orderId, string(order, "elder_id"),
                        string(order, "service_id"), string(order, "address_id"), scheduledStart,
                        recommendation, currentUser.userId());
            }
        }
        return toRecommendResponse(recommendations);
    }

    @Transactional
    public RecommendationDtos.PreferredNurseResponse choosePreferredNurse(
            CurrentUser currentUser,
            String orderId,
            RecommendationDtos.PreferredNurseRequest request) {
        Map<String, Object> order = requireOrder(orderId);
        requireFamilyOrderScope(currentUser, order, ORDER_CREATE_SCOPE);
        if (!"WAIT_DISPATCH".equals(string(order, "order_status"))) {
            throw new ConflictException();
        }
        NurseRecommendationEntity recommendation = repository
                .findOrderRecommendation(orderId, request.preferredNurseId())
                .filter(NurseRecommendationEntity::available)
                .orElseThrow(BusinessRuleException::new);
        repository.updatePreferredNurse(orderId, request.preferredNurseId());
        saveOperationLog(currentUser, "CHOOSE_PREFERRED_NURSE", "NURSING_ORDER", orderId,
                nullablePreference(nullableString(order, "preferred_nurse_id")), request);

        // 偏好护理只写入订单偏好字段，绝不创建 nurse_task，也不改变 WAIT_DISPATCH 状态。
        return new RecommendationDtos.PreferredNurseResponse(
                orderId, request.preferredNurseId(), recommendation.recommendReason());
    }

    public RecommendationDtos.PreferredNurseResponse preferredNurseView(
            CurrentUser currentUser,
            String orderId) {
        Map<String, Object> order = requireOrder(orderId);
        requireFamilyOrderScope(currentUser, order, ORDER_CREATE_SCOPE);
        String nurseId = nullableString(order, "preferred_nurse_id");
        if (!hasText(nurseId)) {
            throw new NotFoundException();
        }
        NurseRecommendationEntity recommendation = repository.findOrderRecommendation(orderId, nurseId)
                .orElseThrow(NotFoundException::new);
        return new RecommendationDtos.PreferredNurseResponse(
                orderId, nurseId, recommendation.recommendReason());
    }

    private MedicalFileEntity requireMedicalFile(String fileId) {
        return repository.findMedicalFile(fileId).orElseThrow(NotFoundException::new);
    }

    private HealthReviewTaskEntity requireReviewTask(String taskId) {
        return repository.findHealthReviewTask(taskId).orElseThrow(NotFoundException::new);
    }

    private Map<String, Object> requireOrder(String orderId) {
        try {
            return repository.findOrder(orderId);
        } catch (EmptyResultDataAccessException exception) {
            throw new NotFoundException();
        }
    }

    private void requireOrderAccess(CurrentUser currentUser, Map<String, Object> order, boolean nurseOnly) {
        if (currentUser.hasRole(RoleCode.ADMIN) || currentUser.hasRole(RoleCode.CUSTOMER_SERVICE)) {
            return;
        }
        String orderId = string(order, "order_id");
        if (currentUser.hasRole(RoleCode.NURSE)
                && repository.nurseOwnsOrder(currentUser.userId(), orderId)) {
            return;
        }
        if (!nurseOnly && currentUser.hasRole(RoleCode.FAMILY)
                && currentUser.userId().equals(string(order, "family_id"))) {
            return;
        }
        throw new ForbiddenException();
    }

    private void requireElderAccess(CurrentUser currentUser, String elderId, String requiredScope) {
        if (currentUser.hasRole(RoleCode.ADMIN)
                || currentUser.hasRole(RoleCode.CUSTOMER_SERVICE)
                || currentUser.hasRole(RoleCode.NURSE)) {
            return;
        }
        if (currentUser.hasRole(RoleCode.FAMILY)) {
            Map<String, Object> binding = repository.findActiveBinding(currentUser.userId(), elderId)
                    .orElseThrow(ForbiddenException::new);
            if (hasScope(binding.get("scope_codes"), requiredScope)) {
                return;
            }
        }
        throw new ForbiddenException();
    }

    private void requireFamilyOrderScope(
            CurrentUser currentUser,
            Map<String, Object> order,
            String requiredScope) {
        if (!currentUser.hasRole(RoleCode.FAMILY)
                || !currentUser.userId().equals(string(order, "family_id"))) {
            throw new ForbiddenException();
        }
        requireElderAccess(currentUser, string(order, "elder_id"), requiredScope);
    }

    private boolean hasScope(Object rawScopes, String requiredScope) {
        if (rawScopes == null) {
            return false;
        }
        try {
            List<String> scopes = objectMapper.readValue(rawScopes.toString(), new TypeReference<>() {
            });
            return scopes.contains(requiredScope);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("授权范围 JSON 格式错误", exception);
        }
    }

    private HealthArchiveDtos.ReviewTaskResponse toReviewTaskResponse(HealthReviewTaskEntity task) {
        List<HealthArchiveDtos.SuggestionItem> suggestions = repository.findSuggestionsByTask(task.taskId())
                .stream()
                .map(row -> new HealthArchiveDtos.SuggestionItem(
                        string(row, "suggestion_id"),
                        string(row, "field_name"),
                        string(row, "old_value"),
                        string(row, "new_value"),
                        string(row, "source_type"),
                        string(row, "source_id"),
                        string(row, "reason"),
                        string(row, "status")))
                .toList();
        if (suggestions.isEmpty() && hasText(task.fieldName())) {
            suggestions = List.of(new HealthArchiveDtos.SuggestionItem(
                    null, task.fieldName(), task.oldValue(), task.newValue(), task.sourceType(),
                    task.sourceId(), null, task.status()));
        }
        return new HealthArchiveDtos.ReviewTaskResponse(
                task.taskId(), task.elderId(), task.status(), task.archiveVersion(), suggestions);
    }

    private MedicalFileDtos.MedicalFileItem toMedicalFileItem(MedicalFileEntity entity) {
        return new MedicalFileDtos.MedicalFileItem(
                entity.medicalFileId(), entity.fileId(), entity.elderId(), entity.fileType(),
                entity.title(), entity.occurredAt(), entity.auditStatus(), entity.reviewComment());
    }

    private RecommendationDtos.RecommendResponse toRecommendResponse(
            List<NurseRecommendationEntity> recommendations) {
        return new RecommendationDtos.RecommendResponse(recommendations.stream()
                .map(item -> new RecommendationDtos.NurseItem(
                        item.nurseId(), item.nurseName(), item.score(), item.matchedSkills(),
                        item.recommendReason(), item.available()))
                .toList());
    }

    private void validateArchiveDecisions(List<HealthArchiveDtos.ArchiveDecision> decisions) {
        for (HealthArchiveDtos.ArchiveDecision decision : decisions) {
            if (!List.of(APPROVED, REJECTED, NEED_MORE).contains(decision.decision().toUpperCase())) {
                throw new BusinessRuleException();
            }
            canonicalHealthField(decision.targetField());
            if ((REJECTED.equalsIgnoreCase(decision.decision())
                    || NEED_MORE.equalsIgnoreCase(decision.decision()))
                    && !hasText(decision.comment())) {
                throw new BusinessRuleException();
            }
        }
    }

    /**
     * 将审核通过的规范化值写入阶段19已经冻结的真实健康档案表。
     * 这里仅选择既有字段，不拼接客户端传入的表名或列名。
     */
    private void applyArchiveDecision(
            String elderId,
            String targetField,
            String normalizedValue,
            String reviewerId) {
        Map<String, Object> value = normalizedObject(normalizedValue);
        switch (targetField) {
            case "careSummary" -> repository.updateCareSummary(elderId, normalizedValue, reviewerId);
            case "riskTags" -> repository.upsertRiskTag(
                    nextId("risk"), elderId,
                    requiredMapText(value, "tagName", "name", "value"),
                    enumMapText(value, "MEDIUM", Set.of("LOW", "MEDIUM", "HIGH"), "riskLevel"),
                    mapText(value, "remark"));
            case "diseases" -> repository.upsertDisease(
                    nextId("disease"), elderId,
                    requiredMapText(value, "diseaseName", "name", "value"),
                    enumMapText(value, "ACTIVE", Set.of("ACTIVE", "INACTIVE"), "diseaseStatus", "status"),
                    mapText(value, "remark"));
            case "medications" -> repository.upsertMedication(
                    nextId("medication"), elderId,
                    requiredMapText(value, "medicationName", "name", "value"),
                    mapText(value, "dosage"), mapText(value, "frequency"),
                    enumMapText(value, "ACTIVE", Set.of("ACTIVE", "INACTIVE"), "medicationStatus", "status"),
                    mapText(value, "remark"));
            case "allergies" -> repository.upsertAllergy(
                    nextId("allergy"), elderId,
                    requiredMapText(value, "allergen", "name", "value"),
                    mapText(value, "reaction"), mapText(value, "severity"), mapText(value, "remark"));
            case "carePlan" -> repository.replaceActiveCarePlan(
                    nextId("care_plan"), elderId,
                    requiredMapText(value, "planContent", "content", "value"));
            default -> throw new BusinessRuleException();
        }
    }

    private String canonicalHealthField(String fieldName) {
        if (!hasText(fieldName)) {
            throw new BusinessRuleException();
        }
        String canonical = switch (fieldName.trim().toLowerCase()) {
            case "caresummary" -> "careSummary";
            case "risktags" -> "riskTags";
            case "diseases" -> "diseases";
            case "medications" -> "medications";
            case "allergies" -> "allergies";
            case "careplan" -> "carePlan";
            default -> null;
        };
        if (canonical == null || !HEALTH_TARGET_FIELDS.contains(canonical)) {
            throw new BusinessRuleException();
        }
        return canonical;
    }

    private Map<String, Object> normalizedObject(String rawValue) {
        if (!hasText(rawValue)) {
            throw new BusinessRuleException();
        }
        String trimmed = rawValue.trim();
        if (!trimmed.startsWith("{")) {
            return Map.of("value", trimmed);
        }
        try {
            return objectMapper.readValue(trimmed, new TypeReference<>() {
            });
        } catch (JsonProcessingException exception) {
            throw new BusinessRuleException();
        }
    }

    private String enumMapText(
            Map<String, Object> values,
            String defaultValue,
            Set<String> allowed,
            String... keys) {
        String value = mapText(values, keys);
        String normalized = hasText(value) ? value.trim().toUpperCase() : defaultValue;
        if (!allowed.contains(normalized)) {
            throw new BusinessRuleException();
        }
        return normalized;
    }

    private String requiredMapText(Map<String, Object> values, String... keys) {
        String value = mapText(values, keys);
        if (!hasText(value)) {
            throw new BusinessRuleException();
        }
        return value;
    }

    private String mapText(Map<String, Object> values, String... keys) {
        for (String key : keys) {
            Object value = values.get(key);
            if (value != null) {
                return value instanceof String text ? text : writeJson(value);
            }
        }
        return null;
    }

    private void requireReviewTarget(AuditStatus status, String reviewComment) {
        if (status == AuditStatus.PENDING) {
            throw new BusinessRuleException();
        }
        if ((status == AuditStatus.REJECTED || status == AuditStatus.NEED_MORE)
                && !hasText(reviewComment)) {
            throw new BusinessRuleException();
        }
    }

    private String effectiveTrainingStatus(TrainingRecordEntity record) {
        if (!APPROVED.equals(record.trainingStatus()) || !hasText(record.expiredAt())) {
            return record.trainingStatus();
        }
        return parseDateTime(record.expiredAt()).isAfter(LocalDateTime.now()) ? APPROVED : REJECTED;
    }

    private void saveOperationLog(
            CurrentUser currentUser,
            String operationType,
            String bizType,
            String bizId,
            Object beforeValue,
            Object afterValue) {
        repository.insertOperationLog(
                nextId("op"),
                currentUser.userId(),
                currentUser.primaryRole(),
                operationType,
                bizType,
                bizId,
                writeJson(beforeValue),
                writeJson(afterValue),
                nextId("trace"));
    }

    private String nullableAuditStatus(String status) {
        return hasText(status) ? AuditStatus.parse(status).name() : null;
    }

    private LocalDateTime parseDateTime(String value) {
        try {
            return OffsetDateTime.parse(value).toLocalDateTime();
        } catch (DateTimeParseException ignored) {
            try {
                return LocalDateTime.parse(value);
            } catch (DateTimeParseException exception) {
                throw new BusinessRuleException();
            }
        }
    }

    private LocalDateTime parseNullableDateTime(String value) {
        return hasText(value) ? parseDateTime(value) : null;
    }

    private LocalDateTime toLocalDateTime(Object value) {
        if (value instanceof Timestamp timestamp) {
            return timestamp.toLocalDateTime();
        }
        if (value instanceof LocalDateTime localDateTime) {
            return localDateTime;
        }
        return parseDateTime(value == null ? "" : value.toString());
    }

    private String writeJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("JSON 序列化失败", exception);
        }
    }

    private String nextId(String prefix) {
        return prefix + "_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }

    private int safePage(int page) {
        return Math.max(page, 1);
    }

    private int safeSize(int size) {
        return Math.min(Math.max(size, 1), 100);
    }

    private int offset(int page, int size) {
        return (page - 1) * size;
    }

    private String string(Map<String, Object> row, String key) {
        Object value = row.get(key);
        return value == null ? null : value.toString();
    }

    private String nullableString(Map<String, Object> row, String key) {
        return row.containsKey(key) ? string(row, key) : null;
    }

    private Map<String, Object> nullablePreference(String nurseId) {
        Map<String, Object> value = new java.util.HashMap<>();
        value.put("preferredNurseId", nurseId);
        return value;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
