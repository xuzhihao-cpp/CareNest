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
import com.csu.carenest.careadmin.redis.RedisCacheService;
import com.csu.carenest.careadmin.redis.RedisKeyFactory;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.Period;
import java.time.format.DateTimeParseException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashMap;
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
            "DISPATCHED", "ACCEPTED", "ON_THE_WAY");

    private final Phase19To30Repository repository;
    private final ObjectMapper objectMapper;
    private final Phase25MedicalFileStorage medicalFileStorage;
    private final RedisCacheService cacheService;

    @Autowired
    public Phase19To30Service(
            Phase19To30Repository repository,
            ObjectMapper objectMapper,
            Phase25MedicalFileStorage medicalFileStorage,
            RedisCacheService cacheService) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.medicalFileStorage = medicalFileStorage;
        this.cacheService = cacheService;
    }

    public Phase19To30Service(
            Phase19To30Repository repository,
            ObjectMapper objectMapper,
            Phase25MedicalFileStorage medicalFileStorage) {
        this(repository, objectMapper, medicalFileStorage, null);
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

    @Transactional(readOnly = true)
    public MedicalFilePreview adminMedicalFilePreview(String fileId) {
        MedicalFileEntity medicalFile = requireMedicalFile(fileId);
        Phase19To30Repository.MedicalFileAssetRow file = repository
                .findMedicalFileAsset(medicalFile.medicalFileId())
                .orElseThrow(NotFoundException::new);
        return new MedicalFilePreview(
                medicalFileStorage.read(file.bucket(), file.objectKey()), file.mimeType(), file.originalName());
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

    public List<HealthArchiveDtos.ArchiveChangeLogResponse> archiveChangeLogs(String elderId) {
        if (!hasText(elderId)) {
            throw new BusinessRuleException();
        }
        return repository.findArchiveChangeLogs(elderId.trim(), 20).stream()
                .map(row -> new HealthArchiveDtos.ArchiveChangeLogResponse(
                        string(row, "changeLogId"),
                        nullableString(row, "fieldName"),
                        string(row, "changeType"),
                        nullableString(row, "beforeValue"),
                        nullableString(row, "afterValue"),
                        nullableString(row, "comment"),
                        nullableString(row, "archiveVersion"),
                        toLocalDateTime(row.get("changedAt")).toString()))
                .toList();
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

    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public HealthArchiveDtos.PreServiceHealthSummary preServiceHealthSummary(
            CurrentUser currentUser,
            String orderId) {
        Map<String, Object> order = requireOrder(orderId);
        requireOrderAccess(currentUser, order, true);
        if (currentUser.hasRole(RoleCode.NURSE)
                && !PRE_SERVICE_ORDER_STATUSES.contains(string(order, "order_status"))) {
            throw new ConflictException();
        }
        if (currentUser.hasRole(RoleCode.NURSE)
                && !repository.nurseHasPreServiceTask(currentUser.userId(), orderId)) {
            throw new ConflictException();
        }
        String elderId = string(order, "elder_id");
        Phase19To30Repository.PreServiceArchiveSnapshot archive = repository.findPreServiceArchive(elderId)
                .orElseThrow(NotFoundException::new);
        HealthArchiveDtos.PreServiceHealthSummary summary = new HealthArchiveDtos.PreServiceHealthSummary(
                toPreServiceElderProfile(archive),
                repository.findRiskTags(elderId),
                repository.findMedications(elderId).stream().map(this::toMedication).toList(),
                repository.findDiseases(elderId),
                repository.findAllergies(elderId),
                repository.findApprovedMedicalFiles(elderId).stream()
                        .map(file -> new HealthArchiveDtos.ApprovedMedicalFile(
                                file.title(), file.fileType(), file.occurredAt(), null,
                                "/api/v1/nurse/orders/"
                                        + UriUtils.encodePathSegment(orderId, StandardCharsets.UTF_8)
                                        + "/medical-files/"
                                        + UriUtils.encodePathSegment(file.medicalFileId(), StandardCharsets.UTF_8)
                                        + "/preview"))
                        .toList(),
                repository.findRecentReports(elderId).stream()
                        .map(report -> new HealthArchiveDtos.RecentReport(
                                report.serviceName(), report.occurredAt(), report.generatedAt(), report.summary(),
                                report.nursingAdvice(), repository.findReportVitalSigns(report.reportId())))
                        .toList());
        saveOperationLog(currentUser, "VIEW_PRE_SERVICE_HEALTH_SUMMARY",
                "NURSING_ORDER", orderId, null, Map.of("archiveVersion", archive.archiveVersion()));
        return summary;
    }

    @Transactional(readOnly = true)
    public MedicalFilePreview preServiceMedicalFilePreview(
            CurrentUser currentUser, String orderId, String medicalFileId) {
        Map<String, Object> order = requireOrder(orderId);
        requireOrderAccess(currentUser, order, true);
        if (currentUser.hasRole(RoleCode.NURSE)
                && !PRE_SERVICE_ORDER_STATUSES.contains(string(order, "order_status"))) {
            throw new ConflictException();
        }
        if (currentUser.hasRole(RoleCode.NURSE)
                && !repository.nurseHasPreServiceTask(currentUser.userId(), orderId)) {
            throw new ConflictException();
        }
        Phase19To30Repository.MedicalFileRow file = repository
                .findApprovedMedicalFile(string(order, "elder_id"), medicalFileId)
                .orElseThrow(NotFoundException::new);
        return new MedicalFilePreview(
                medicalFileStorage.read(file.bucket(), file.objectKey()), file.mimeType(), file.originalName());
    }

    public record MedicalFilePreview(byte[] content, String mimeType, String originalName) {
    }

    @Transactional(readOnly = true)
    public List<QualificationDtos.SkillOption> qualificationSkillOptions() {
        return repository.findQualificationSkillOptions();
    }

    @Transactional(readOnly = true)
    public QualificationFilePreview qualificationFilePreview(String applicationId, String fileId) {
        Phase19To30Repository.QualificationFileRow file = repository
                .findQualificationFile(applicationId, fileId)
                .orElseThrow(NotFoundException::new);
        return new QualificationFilePreview(
                medicalFileStorage.read(file.bucket(), file.objectKey()), file.mimeType(), file.originalName());
    }

    public record QualificationFilePreview(byte[] content, String mimeType, String originalName) {
    }

    @Transactional
    public QualificationDtos.ApplicationResponse submitQualification(
            CurrentUser currentUser,
            QualificationDtos.ApplicationRequest request) {
        QualificationApplicationEntity current = repository.findCurrentQualification(currentUser.userId()).orElse(null);
        if (current != null && (PENDING.equals(current.auditStatus()) || APPROVED.equals(current.auditStatus()))) {
            throw new ConflictException();
        }

        List<String> skillCodes = request.serviceSkillCodes().stream()
                .map(String::trim).filter(this::hasText).distinct().toList();
        List<String> fileIds = request.certificateFileIds().stream()
                .map(String::trim).filter(this::hasText).distinct().toList();
        if (skillCodes.isEmpty() || skillCodes.size() != request.serviceSkillCodes().size()
                || fileIds.isEmpty() || fileIds.size() != request.certificateFileIds().size()
                || fileIds.size() > 5 || !repository.qualificationSkillsExist(skillCodes)) {
            throw new BusinessRuleException();
        }
        for (String fileId : fileIds) {
            Phase19To30Repository.QualificationFileRow file = repository
                    .findOwnedQualificationFile(currentUser.userId(), fileId)
                    .orElseThrow(ForbiddenException::new);
            if (!qualificationFileTypeAllowed(file.mimeType(), file.originalName())
                    || file.size() <= 0 || file.size() > 20L * 1024 * 1024) {
                throw new BusinessRuleException();
            }
        }

        String applicationId = nextId("qualification");
        repository.updateNurseProfileForApplication(
                currentUser.userId(), request.realName(), request.idNoMasked(), PENDING);
        String skillsJson = writeJson(skillCodes);
        for (String fileId : fileIds) {
            repository.insertNurseCertificate(
                    nextId("certificate"),
                    applicationId,
                    currentUser.userId(),
                    request.realName(),
                    request.idNoMasked(),
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
        return toQualificationApplication(application);
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
                .map(this::toQualificationApplication)
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
        if (!PENDING.equals(application.auditStatus())) {
            throw new ConflictException();
        }
        AuditStatus targetStatus = AuditStatus.parse(request.auditStatus());
        requireReviewTarget(targetStatus, request.reviewComment());
        repository.reviewQualification(
                applicationId,
                application.nurseId(),
                targetStatus.name(),
                request.reviewComment(),
                reviewer.userId());
        evictRecommendationCache();
        saveOperationLog(reviewer, "REVIEW_NURSE_QUALIFICATION", "NURSE_QUALIFICATION",
                applicationId, application, request);
        return new QualificationDtos.QualificationReviewResponse(
                application.nurseId(), targetStatus.name());
    }

    public QualificationDtos.TrainingResponse trainingStatus(CurrentUser currentUser) {
        return trainingStatusForNurse(currentUser.userId());
    }

    public QualificationDtos.TrainingResponse trainingStatusForNurse(String nurseId) {
        TrainingRecordEntity record = repository.findTrainingRecord(nurseId)
                .orElseThrow(NotFoundException::new);
        return toTrainingResponse(record);
    }

    @Transactional
    public QualificationDtos.TrainingResponse reviewTraining(
            CurrentUser reviewer,
            String nurseId,
            QualificationDtos.TrainingReviewRequest request) {
        if (!APPROVED.equals(repository.findNurseQualificationStatus(nurseId))) {
            throw new BusinessRuleException();
        }
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
        evictRecommendationCache();
        saveOperationLog(reviewer, "REVIEW_NURSE_TRAINING", "NURSE_TRAINING",
                nurseId, null, request);
        return trainingStatusForNurse(nurseId);
    }

    @Transactional
    public RecommendationDtos.RecommendResponse recommendNurses(
            CurrentUser currentUser,
            RecommendationDtos.RecommendRequest request) {
        requireElderAccess(currentUser, request.elderId(), ORDER_CREATE_SCOPE);
        LocalDateTime scheduledStart = parseDateTime(request.scheduledStart());
        if (!scheduledStart.isAfter(LocalDateTime.now())) {
            throw new BusinessRuleException();
        }
        int durationMinutes = repository.findOnShelfServiceDuration(request.serviceId())
                .orElseThrow(BusinessRuleException::new);
        if (!repository.addressBelongsToElder(request.addressId(), request.elderId())) {
            throw new BusinessRuleException();
        }
        LocalDateTime scheduledEnd = scheduledStart.plusMinutes(durationMinutes);
        String requestHash = recommendationHash(request, scheduledStart);
        RecommendationDtos.RecommendResponse cached = cacheService == null
                ? null
                : cacheService.get(
                        RedisKeyFactory.nurseRecommendationKey(requestHash),
                        RecommendationDtos.RecommendResponse.class).orElse(null);
        List<NurseRecommendationEntity> recommendations = cached == null
                ? repository.findRecommendableNurses(
                        request.serviceId(), request.elderId(), scheduledStart, scheduledEnd)
                : cached.nurses().stream().map(this::toRecommendationEntity).toList();
        if (cached == null && cacheService != null) {
            cacheService.put(
                    RedisKeyFactory.nurseRecommendationKey(requestHash),
                    toRecommendResponse(recommendations), Duration.ofMinutes(5));
        }
        String requestKey = nextId("recommend");
        for (int index = 0; index < recommendations.size(); index++) {
            NurseRecommendationEntity recommendation = recommendations.get(index);
            // 推荐理由必须落库，后续选择偏好、申诉和评分都可追溯到同一批推荐结果。
            repository.insertRecommendationLog(
                    nextId("recommend_log"), requestKey, requestHash, null, request.elderId(),
                    request.serviceId(), request.addressId(), scheduledStart, recommendation,
                    index + 1, currentUser.userId());
        }
        return toRecommendResponse(recommendations);
    }

    public RecommendationDtos.RecommendResponse orderRecommendations(
            CurrentUser currentUser,
            String orderId) {
        Map<String, Object> order = requireOrder(orderId);
        requireOrderAccess(currentUser, order, false);
        return toRecommendResponse(repository.findOrderRecommendations(orderId));
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
        Phase19To30Repository.RecommendationLogRow recommendationLog = repository
                .findOrderRecommendationLog(orderId, request.preferredNurseId())
                .filter(row -> row.recommendation().available())
                .orElseThrow(BusinessRuleException::new);
        LocalDateTime scheduledStart = toLocalDateTime(order.get("scheduled_start_at"));
        LocalDateTime scheduledEnd = toLocalDateTime(order.get("scheduled_end_at"));
        if (!repository.nurseEligibleForService(
                request.preferredNurseId(), string(order, "service_id"),
                scheduledStart, scheduledEnd, orderId)) {
            throw new BusinessRuleException();
        }
        NurseRecommendationEntity recommendation = recommendationLog.recommendation();
        repository.updatePreferredNurse(
                orderId, request.preferredNurseId(), recommendation.recommendReason(),
                recommendationLog.logId(), currentUser.userId());
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
        String recommendReason = nullableString(order, "preferred_nurse_reason");
        if (!hasText(recommendReason)) {
            recommendReason = repository.findOrderRecommendation(orderId, nurseId)
                    .map(NurseRecommendationEntity::recommendReason)
                    .orElseThrow(NotFoundException::new);
        }
        return new RecommendationDtos.PreferredNurseResponse(
                orderId, nurseId, recommendReason);
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
                task.taskId(), task.elderId(), task.status(), task.archiveVersion(), task.submittedAt(), suggestions);
    }

    private MedicalFileDtos.MedicalFileItem toMedicalFileItem(MedicalFileEntity entity) {
        Phase19To30Repository.MedicalFileAssetRow asset = repository
                .findMedicalFileAsset(entity.medicalFileId())
                .orElse(null);
        String encodedId = UriUtils.encodePathSegment(entity.medicalFileId(), StandardCharsets.UTF_8);
        String previewUrl = asset == null ? null : "/api/v1/admin/medical-files/" + encodedId + "/preview";
        String downloadUrl = asset == null ? null : previewUrl + "?download=true";
        return new MedicalFileDtos.MedicalFileItem(
                entity.medicalFileId(), entity.fileId(), entity.elderId(), entity.fileType(),
                entity.title(), entity.occurredAt(), entity.auditStatus(), entity.reviewComment(),
                asset == null ? null : asset.originalName(), asset == null ? null : asset.mimeType(),
                asset == null ? null : asset.fileSize(), previewUrl, downloadUrl);
    }

    private RecommendationDtos.RecommendResponse toRecommendResponse(
            List<NurseRecommendationEntity> recommendations) {
        return new RecommendationDtos.RecommendResponse(recommendations.stream()
                .map(item -> new RecommendationDtos.NurseItem(
                        item.nurseId(), item.nurseName(), item.score(), item.matchedSkills(),
                        item.recommendReason(), item.available()))
                .toList());
    }

    private NurseRecommendationEntity toRecommendationEntity(RecommendationDtos.NurseItem item) {
        return new NurseRecommendationEntity(
                item.nurseId(), item.nurseName(), item.score(), item.matchedSkills(),
                item.recommendReason(), item.available());
    }

    private String recommendationHash(
            RecommendationDtos.RecommendRequest request,
            LocalDateTime scheduledStart) {
        String value = String.join("|", request.elderId(), request.serviceId(), request.addressId(),
                scheduledStart.withSecond(0).withNano(0).toString());
        try {
            return java.util.HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256")
                            .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private void evictRecommendationCache() {
        if (cacheService != null) {
            cacheService.evictByPrefix(RedisKeyFactory.nurseRecommendationPrefix());
        }
    }

    private QualificationDtos.ApplicationResponse toQualificationApplication(
            QualificationApplicationEntity application) {
        List<QualificationDtos.CertificateFile> files = repository
                .findQualificationFiles(application.applicationId())
                .stream()
                .map(file -> new QualificationDtos.CertificateFile(
                        file.fileId(), file.originalName(), file.mimeType(), file.size(),
                        qualificationFileTypeAllowed(file.mimeType(), file.originalName())))
                .toList();
        return new QualificationDtos.ApplicationResponse(
                application.applicationId(), application.nurseId(), application.nurseName(),
                application.auditStatus(), application.realName(), application.idNoMasked(),
                maskCertificateNumber(application.certificateNo()), files,
                readStringList(application.serviceSkillCodes()), application.reviewComment(),
                application.submittedAt(), application.reviewedAt());
    }

    private QualificationDtos.TrainingResponse toTrainingResponse(TrainingRecordEntity record) {
        return new QualificationDtos.TrainingResponse(
                record.nurseId(), record.nurseName(), record.qualificationStatus(),
                effectiveTrainingStatus(record), record.trainingBatch(), record.passedAt(),
                record.expiredAt(), record.remark());
    }

    private List<String> readStringList(String raw) {
        if (!hasText(raw)) {
            return List.of();
        }
        try {
            return objectMapper.readValue(raw, new TypeReference<>() {
            });
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Invalid qualification skill data", exception);
        }
    }

    private String maskCertificateNumber(String certificateNo) {
        if (!hasText(certificateNo)) {
            return "";
        }
        String value = certificateNo.trim();
        if (value.length() <= 4) {
            return "****";
        }
        int prefixLength = Math.min(4, value.length() - 4);
        return value.substring(0, prefixLength) + "****" + value.substring(value.length() - 4);
    }

    private boolean qualificationFileTypeAllowed(String mimeType, String originalName) {
        String mime = hasText(mimeType) ? mimeType.trim().toLowerCase() : "";
        String name = hasText(originalName) ? originalName.trim().toLowerCase() : "";
        return ("application/pdf".equals(mime) && name.endsWith(".pdf"))
                || (("image/jpeg".equals(mime) || "image/jpg".equals(mime))
                && (name.endsWith(".jpg") || name.endsWith(".jpeg")))
                || ("image/png".equals(mime) && name.endsWith(".png"));
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
            case "riskTags" -> {
                Map<String, Object> riskTag = normalizedRiskTag(value);
                String tagName = requiredMapText(riskTag, "tagName", "name", "value");
                String tagCode = mapText(riskTag, "tagCode", "code");
                if (!hasText(tagCode)) {
                    tagCode = generatedRiskTagCode(tagName);
                }
                repository.upsertRiskTag(
                        nextId("risk"), elderId, tagCode, tagName,
                        enumMapText(riskTag, "MEDIUM", Set.of("LOW", "MEDIUM", "HIGH"), "riskLevel"),
                        mapText(riskTag, "remark"));
            }
            case "diseases" -> repository.upsertDisease(
                    nextId("disease"), elderId,
                    requiredMapText(value, "diseaseName", "name", "value"),
                    enumMapText(value, "ACTIVE",
                            Set.of("ACTIVE", "MONITORING", "STABLE", "RESOLVED"),
                            "diseaseStatus", "status"),
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

    /** 兼容旧任务保存的“风险名称：等级”文本，同时保留新接口的结构化风险对象。 */
    private Map<String, Object> normalizedRiskTag(Map<String, Object> value) {
        if (hasText(mapText(value, "tagName", "name")) || hasText(mapText(value, "riskLevel"))) {
            return value;
        }
        String raw = mapText(value, "value");
        if (!hasText(raw)) {
            return value;
        }
        String trimmed = raw.trim();
        for (String level : List.of("HIGH", "MEDIUM", "LOW")) {
            for (String separator : List.of("：", ":")) {
                String suffix = separator + level;
                if (trimmed.toUpperCase().endsWith(suffix)) {
                    Map<String, Object> parsed = new HashMap<>(value);
                    parsed.put("tagName", trimmed.substring(0, trimmed.length() - suffix.length()).trim());
                    parsed.put("riskLevel", level);
                    return parsed;
                }
            }
        }
        return value;
    }

    private String generatedRiskTagCode(String tagName) {
        return switch (tagName.trim()) {
            case "跌倒风险" -> "FALL_RISK";
            case "压疮风险" -> "PRESSURE_INJURY_RISK";
            case "吞咽风险" -> "SWALLOWING_RISK";
            case "用药风险" -> "MEDICATION_RISK";
            case "走失风险" -> "WANDERING_RISK";
            case "过敏风险" -> "ALLERGY_RISK";
            default -> "CUSTOM_" + Integer.toUnsignedString(tagName.trim().hashCode(), 16).toUpperCase();
        };
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
        return parseDateTime(record.expiredAt()).isAfter(LocalDateTime.now()) ? APPROVED : "EXPIRED";
    }

    private HealthArchiveDtos.PreServiceElderProfile toPreServiceElderProfile(
            Phase19To30Repository.PreServiceArchiveSnapshot archive) {
        HealthArchiveDtos.CarePlan carePlan = parseCarePlan(archive.carePlanJson());
        List<String> carePoints = hasText(archive.careSummary())
                ? java.util.Arrays.stream(archive.careSummary().split("[;；\\n]"))
                .map(String::trim).filter(this::hasText).distinct().toList()
                : List.of();
        Integer age = null;
        if (hasText(archive.birthDate())) {
            try {
                age = Math.max(0, Period.between(LocalDate.parse(archive.birthDate()), LocalDate.now()).getYears());
            } catch (DateTimeParseException ignored) {
                // Birth date remains available even if legacy data cannot be used for age calculation.
            }
        }
        return new HealthArchiveDtos.PreServiceElderProfile(
                archive.elderName(), archive.gender(), archive.birthDate(), age,
                archive.careLevel(), carePlan, carePoints);
    }

    private HealthArchiveDtos.CarePlan parseCarePlan(String raw) {
        if (!hasText(raw)) {
            return new HealthArchiveDtos.CarePlan("", "", "");
        }
        try {
            Map<String, Object> values = objectMapper.readValue(raw, new TypeReference<>() {
            });
            return new HealthArchiveDtos.CarePlan(
                    Optional.ofNullable(mapText(values, "careGoals")).orElse(""),
                    Optional.ofNullable(mapText(values, "dailyCare")).orElse(""),
                    Optional.ofNullable(mapText(values, "precautions")).orElse(""));
        } catch (JsonProcessingException exception) {
            return new HealthArchiveDtos.CarePlan("", raw, "");
        }
    }

    private HealthArchiveDtos.Medication toMedication(Phase19To30Repository.MedicationRow row) {
        List<String> timePoints = List.of();
        if (hasText(row.timePointsJson())) {
            try {
                timePoints = objectMapper.readValue(row.timePointsJson(), new TypeReference<>() {
                });
            } catch (JsonProcessingException exception) {
                throw new IllegalStateException("Invalid archived medication time points", exception);
            }
        }
        return new HealthArchiveDtos.Medication(
                row.medicationName(), row.dosage(), row.frequency(), timePoints,
                row.startDate(), row.endDate(), row.remark());
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
