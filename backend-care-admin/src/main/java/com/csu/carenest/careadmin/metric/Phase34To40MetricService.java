package com.csu.carenest.careadmin.metric;

import com.csu.carenest.careadmin.auth.CurrentUser;
import com.csu.carenest.careadmin.auth.RoleCode;
import com.csu.carenest.careadmin.common.BusinessRuleException;
import com.csu.carenest.careadmin.common.ConflictException;
import com.csu.carenest.careadmin.common.ForbiddenException;
import com.csu.carenest.careadmin.common.NotFoundException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** 阶段34-40护理指标闭环业务服务。 */
@Service
public class Phase34To40MetricService {

    private static final String CONFIG_PERMISSION = "CARE_METRIC_CONFIG_MANAGE";
    private static final String REVIEW_PERMISSION = "CARE_EVIDENCE_REVIEW";
    private static final Set<String> EVIDENCE_ORDER_STATUSES = Set.of("SERVING", "WAIT_REPORT");
    private static final Set<String> METRIC_CHECK_ORDER_STATUSES =
            Set.of("WAIT_REPORT", "WAIT_CONFIRM", "COMPLETED");

    private final Phase34To40MetricRepository repository;
    private final ObjectMapper objectMapper;

    public Phase34To40MetricService(
            Phase34To40MetricRepository repository,
            ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public CareMetricDtos.ConfigVersionResponse metricConfig(CurrentUser user, String serviceId) {
        requirePermission(user, CONFIG_PERMISSION);
        requireService(serviceId);
        Phase34To40MetricRepository.ConfigContext config = repository.findActiveConfig(serviceId)
                .orElseThrow(NotFoundException::new);
        return new CareMetricDtos.ConfigVersionResponse(config.version());
    }

    @Transactional
    public CareMetricDtos.ConfigVersionResponse saveMetricConfig(
            CurrentUser user,
            String serviceId,
            CareMetricDtos.CareMetricConfigRequest request) {
        requirePermission(user, CONFIG_PERMISSION);
        requireService(serviceId);
        // 版本号由同一服务项目串行生成，避免并发保存得到相同 config_version。
        repository.lockService(serviceId);

        List<CareMetricDtos.CareMetricConfigItem> items = normalizeConfigItems(request.items());
        int version = repository.nextConfigVersion(serviceId);
        String configId = nextId("config");

        // 历史配置不覆盖，只将旧版本停用；订单快照仍可通过原 metric_item_id 读取。
        repository.deactivateConfigs(serviceId);
        repository.insertConfig(configId, serviceId, version, user.userId());
        for (int index = 0; index < items.size(); index++) {
            CareMetricDtos.CareMetricConfigItem item = items.get(index);
            String itemId = nextId("metric");
            repository.insertConfigItem(itemId, configId, serviceId, item, index + 1);
            repository.insertMissingScoreRule(
                    nextId("rule"), itemId, item.scoreWeight().negate());
        }
        log(user, "UPDATE_CARE_METRIC_CONFIG", "SERVICE_ITEM", serviceId,
                null, Map.of("configVersion", version));
        return new CareMetricDtos.ConfigVersionResponse(version);
    }

    @Transactional
    public CareMetricDtos.MetricChecklistResponse generateChecklist(CurrentUser user, String orderId) {
        requirePermission(user, CONFIG_PERMISSION);
        Phase34To40MetricRepository.OrderContext order = requireOrder(orderId);
        repository.lockOrder(orderId);
        order = requireOrder(orderId);
        if ("CANCELED".equals(order.orderStatus()) || "COMPLETED".equals(order.orderStatus())) {
            throw new ConflictException();
        }

        if (repository.findChecklistId(orderId).isPresent()) {
            return checklistResponse(orderId);
        }
        Phase34To40MetricRepository.ConfigContext config = repository.findActiveConfig(order.serviceId())
                .orElseThrow(BusinessRuleException::new);
        List<Phase34To40MetricRepository.ConfigMetricItem> configItems =
                repository.findEnabledConfigItems(config.configId());
        if (configItems.isEmpty()) {
            throw new BusinessRuleException();
        }

        String checklistId = nextId("checklist");
        repository.insertChecklist(checklistId, order, config, user.userId());
        for (Phase34To40MetricRepository.ConfigMetricItem item : configItems) {
            repository.insertOrderMetricItem(nextId("order_metric"), checklistId, orderId, item);
        }
        log(user, "GENERATE_METRIC_CHECKLIST", "NURSING_ORDER", orderId,
                null, Map.of("configVersion", config.version()));
        return checklistResponse(orderId);
    }

    @Transactional(readOnly = true)
    public CareMetricDtos.MetricChecklistResponse checklist(CurrentUser user, String orderId) {
        Phase34To40MetricRepository.OrderContext order = requireOrder(orderId);
        requireNurseOrAdminAccess(user, order, CONFIG_PERMISSION);
        if (repository.findChecklistId(orderId).isEmpty()) {
            throw new NotFoundException();
        }
        return checklistResponse(orderId);
    }

    @Transactional
    public CareMetricDtos.EvidenceResponse submitEvidence(
            CurrentUser user,
            String orderId,
            CareMetricDtos.EvidenceRequest request) {
        Phase34To40MetricRepository.OrderContext order = requireOrder(orderId);
        requireNurseOrAdminAccess(user, order, REVIEW_PERMISSION);
        repository.lockOrder(orderId);
        order = requireOrder(orderId);
        requireNurseOrAdminAccess(user, order, REVIEW_PERMISSION);
        if (!EVIDENCE_ORDER_STATUSES.contains(order.orderStatus())) {
            throw new ConflictException();
        }

        MetricEnums.EvidenceType evidenceType = MetricEnums.parse(
                MetricEnums.EvidenceType.class, request.evidenceType());
        if (evidenceType == MetricEnums.EvidenceType.NONE) {
            throw new BusinessRuleException();
        }
        String metricItemId = trimToNull(request.metricItemId());
        if (metricItemId != null) {
            Phase34To40MetricRepository.MetricItemContext metric = requireMetricItem(metricItemId);
            if (!orderId.equals(metric.orderId())) {
                throw new BusinessRuleException();
            }
        }

        String fileId = trimToNull(request.fileId());
        if (evidenceType == MetricEnums.EvidenceType.PHOTO
                || evidenceType == MetricEnums.EvidenceType.FILE) {
            if (fileId == null) {
                throw new BusinessRuleException();
            }
        }
        if (fileId != null) {
            requireOwnedFile(user, fileId);
        }

        String evidenceId = nextId("evidence");
        repository.insertEvidence(
                evidenceId, order, metricItemId, order.nurseId(), fileId,
                evidenceType.name(), trimToNull(request.description()));
        if (metricItemId != null) {
            repository.updateMetricStatus(metricItemId, MetricEnums.MetricStatus.SUBMITTED.name());
        }
        log(user, "SUBMIT_CARE_EVIDENCE", "CARE_SERVICE_EVIDENCE", evidenceId,
                null, Map.of("orderId", orderId, "auditStatus", "PENDING"));
        return new CareMetricDtos.EvidenceResponse(evidenceId, "PENDING", null, null, null, null, null);
    }

    @Transactional(readOnly = true)
    public List<CareMetricDtos.EvidenceResponse> evidences(CurrentUser user, String orderId) {
        Phase34To40MetricRepository.OrderContext order = requireOrder(orderId);
        requireOrderReadAccess(user, order);
        return repository.findEvidences(orderId);
    }

    @Transactional(readOnly = true)
    public List<CareMetricDtos.EvidenceResponse> pendingEvidences(CurrentUser user) {
        requirePermission(user, REVIEW_PERMISSION);
        return repository.findPendingEvidences();
    }

    @Transactional
    public CareMetricDtos.EvidenceResponse reviewEvidence(
            CurrentUser user,
            String evidenceId,
            CareMetricDtos.EvidenceReviewRequest request) {
        requirePermission(user, REVIEW_PERMISSION);
        Phase34To40MetricRepository.EvidenceContext evidence = repository.findEvidence(evidenceId)
                .orElseThrow(NotFoundException::new);
        if (!MetricEnums.EvidenceAuditStatus.PENDING.name().equals(evidence.auditStatus())) {
            throw new ConflictException();
        }
        MetricEnums.EvidenceAuditStatus target = MetricEnums.parse(
                MetricEnums.EvidenceAuditStatus.class, request.auditStatus());
        if (target == MetricEnums.EvidenceAuditStatus.PENDING) {
            throw new BusinessRuleException();
        }
        String comment = trimToNull(request.reviewComment());
        if ((target == MetricEnums.EvidenceAuditStatus.REJECTED
                || target == MetricEnums.EvidenceAuditStatus.NEED_MORE) && comment == null) {
            throw new BusinessRuleException();
        }

        if (repository.reviewEvidenceIfPending(
                evidenceId, target.name(), comment, user.userId()) == 0) {
            throw new ConflictException();
        }
        repository.insertEvidenceReview(
                nextId("evidence_review"), evidenceId, evidence.auditStatus(),
                target.name(), comment, user.userId());
        log(user, "REVIEW_CARE_EVIDENCE", "CARE_SERVICE_EVIDENCE", evidenceId,
                Map.of("auditStatus", evidence.auditStatus()),
                Map.of("auditStatus", target.name()));
        return new CareMetricDtos.EvidenceResponse(evidenceId, target.name(), null, null, null, null, null);
    }

    @Transactional
    public CareMetricDtos.MetricCheckResponse checkMetrics(CurrentUser user, String orderId) {
        Phase34To40MetricRepository.OrderContext order = requireOrder(orderId);
        requireOrderReadAccess(user, order);
        repository.lockOrder(orderId);
        order = requireOrder(orderId);
        requireOrderReadAccess(user, order);
        if (!METRIC_CHECK_ORDER_STATUSES.contains(order.orderStatus())) {
            throw new ConflictException();
        }
        List<Phase34To40MetricRepository.MetricItemContext> items =
                repository.findOrderMetricItems(orderId);
        if (items.isEmpty()) {
            throw new NotFoundException();
        }

        for (Phase34To40MetricRepository.MetricItemContext item : items) {
            MetricEnums.MetricStatus current = MetricEnums.parse(
                    MetricEnums.MetricStatus.class, item.metricStatus());
            if (current == MetricEnums.MetricStatus.PENDING_PROOF
                    || current == MetricEnums.MetricStatus.EXEMPT_APPROVED
                    || current == MetricEnums.MetricStatus.EXEMPT_REJECTED) {
                continue;
            }
            boolean requiresEvidence = item.required()
                    && !MetricEnums.EvidenceType.NONE.name().equals(item.evidenceType());
            boolean missing = requiresEvidence
                    && repository.countApprovedEvidence(item.metricItemId()) == 0;
            MetricEnums.MetricStatus result = missing
                    ? MetricEnums.MetricStatus.MISSING : MetricEnums.MetricStatus.PASS;
            BigDecimal scoreDelta = missing ? item.scoreWeight().negate() : BigDecimal.ZERO;
            repository.updateMetricStatus(item.metricItemId(), result.name());
            insertMetricRecordOnce(item, result.name(), scoreDelta, "METRIC_CHECK", orderId);
        }
        log(user, "CHECK_ORDER_METRICS", "NURSING_ORDER", orderId,
                null, Map.of("checked", true));
        return metricCheckResultInternal(orderId);
    }

    @Transactional(readOnly = true)
    public CareMetricDtos.MetricCheckResponse metricCheckResult(CurrentUser user, String orderId) {
        Phase34To40MetricRepository.OrderContext order = requireOrder(orderId);
        requireOrderReadAccess(user, order);
        if (repository.findChecklistId(orderId).isEmpty()) {
            throw new NotFoundException();
        }
        return metricCheckResultInternal(orderId);
    }

    @Transactional
    public CareMetricDtos.ExceptionProofResponse submitExceptionProof(
            CurrentUser user,
            String metricItemId,
            CareMetricDtos.ExceptionProofRequest request) {
        Phase34To40MetricRepository.MetricItemContext item = requireMetricItem(metricItemId);
        Phase34To40MetricRepository.OrderContext order = requireOrder(item.orderId());
        requireNurseOrAdminAccess(user, order, REVIEW_PERMISSION);
        repository.lockOrder(order.orderId());
        item = requireMetricItem(metricItemId);
        if (!MetricEnums.MetricStatus.MISSING.name().equals(item.metricStatus())) {
            throw new ConflictException();
        }
        if (repository.hasPendingProof(metricItemId)) {
            throw new ConflictException();
        }
        MetricEnums.ProofReasonType reasonType = MetricEnums.parse(
                MetricEnums.ProofReasonType.class, request.reasonType());

        List<String> fileIds = request.fileIds().stream()
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .collect(java.util.stream.Collectors.collectingAndThen(
                        java.util.stream.Collectors.toCollection(LinkedHashSet::new), ArrayList::new));
        if (fileIds.size() != request.fileIds().size()) {
            throw new BusinessRuleException();
        }

        String firstEvidenceId = null;
        for (String fileId : fileIds) {
            Phase34To40MetricRepository.FileAsset file = requireOwnedFile(user, fileId);
            String evidenceId = nextId("evidence");
            String evidenceType = file.mimeType() != null
                    && file.mimeType().toLowerCase(Locale.ROOT).startsWith("image/")
                    ? MetricEnums.EvidenceType.PHOTO.name() : MetricEnums.EvidenceType.FILE.name();
            repository.insertEvidence(
                    evidenceId, order, metricItemId, order.nurseId(), fileId,
                    evidenceType, request.reasonText().trim());
            if (firstEvidenceId == null) {
                firstEvidenceId = evidenceId;
            }
        }

        String proofId = nextId("proof");
        repository.insertProof(
                proofId, metricItemId, firstEvidenceId, order.nurseId(),
                reasonType.name(), request.reasonText().trim());
        repository.updateMetricStatus(metricItemId, MetricEnums.MetricStatus.PENDING_PROOF.name());
        insertMetricRecordOnce(
                item, MetricEnums.MetricStatus.PENDING_PROOF.name(), BigDecimal.ZERO,
                "EXCEPTION_PROOF", proofId);
        log(user, "SUBMIT_METRIC_EXCEPTION_PROOF", "METRIC_EXCEPTION_PROOF", proofId,
                null, Map.of("reviewStatus", "PENDING"));
        return new CareMetricDtos.ExceptionProofResponse(proofId, "PENDING");
    }

    @Transactional(readOnly = true)
    public List<CareMetricDtos.ExceptionProofResponse> exceptionProofs(
            CurrentUser user, String orderId) {
        Phase34To40MetricRepository.OrderContext order = requireOrder(orderId);
        requireNurseOrAdminAccess(user, order, REVIEW_PERMISSION);
        return repository.findExceptionProofs(orderId);
    }

    @Transactional(readOnly = true)
    public List<CareMetricDtos.ProofReviewResponse> pendingExceptionProofs(CurrentUser user) {
        requirePermission(user, REVIEW_PERMISSION);
        return repository.findPendingProofs();
    }

    @Transactional
    public CareMetricDtos.ProofReviewResponse reviewExceptionProof(
            CurrentUser user,
            String proofId,
            CareMetricDtos.ProofReviewRequest request) {
        requirePermission(user, REVIEW_PERMISSION);
        Phase34To40MetricRepository.ProofContext proof = repository.findProof(proofId)
                .orElseThrow(NotFoundException::new);
        if (!MetricEnums.ProofStatus.PENDING.name().equals(proof.proofStatus())) {
            throw new ConflictException();
        }
        MetricEnums.ProofStatus result = MetricEnums.parse(
                MetricEnums.ProofStatus.class, request.reviewResult());
        if (result == MetricEnums.ProofStatus.PENDING) {
            throw new BusinessRuleException();
        }
        MetricEnums.ScoreDecision decision = MetricEnums.parse(
                MetricEnums.ScoreDecision.class, request.scoreDecision());
        if ((result == MetricEnums.ProofStatus.APPROVED
                && decision != MetricEnums.ScoreDecision.NO_DEDUCTION)
                || (result == MetricEnums.ProofStatus.REJECTED
                && decision != MetricEnums.ScoreDecision.DEDUCT)) {
            throw new BusinessRuleException();
        }
        String comment = trimToNull(request.reviewComment());
        if (result == MetricEnums.ProofStatus.REJECTED && comment == null) {
            throw new BusinessRuleException();
        }

        MetricEnums.MetricStatus metricStatus = result == MetricEnums.ProofStatus.APPROVED
                ? MetricEnums.MetricStatus.EXEMPT_APPROVED
                : MetricEnums.MetricStatus.EXEMPT_REJECTED;
        BigDecimal scoreDelta = result == MetricEnums.ProofStatus.APPROVED
                ? BigDecimal.ZERO : proof.item().scoreWeight().negate();

        // 审核结论、指标状态和评分事实必须位于同一事务，避免出现“已豁免但仍扣分”。
        if (repository.reviewProofIfPending(
                proofId, result.name(), comment, user.userId()) == 0) {
            throw new ConflictException();
        }
        repository.updateMetricStatus(proof.item().metricItemId(), metricStatus.name());
        insertMetricRecordOnce(
                proof.item(), metricStatus.name(), scoreDelta,
                "EXCEPTION_PROOF_REVIEW", proofId);
        log(user, "REVIEW_METRIC_EXCEPTION_PROOF", "METRIC_EXCEPTION_PROOF", proofId,
                Map.of("reviewStatus", proof.proofStatus()),
                Map.of("reviewStatus", result.name(), "scoreDecision", decision.name()));
        return new CareMetricDtos.ProofReviewResponse(
                proofId, result.name(), decision.name());
    }

    private List<CareMetricDtos.CareMetricConfigItem> normalizeConfigItems(
            List<CareMetricDtos.CareMetricConfigItem> source) {
        Set<String> codes = new HashSet<>();
        List<CareMetricDtos.CareMetricConfigItem> result = new ArrayList<>();
        for (CareMetricDtos.CareMetricConfigItem item : source) {
            String code = item.metricCode().trim().toUpperCase(Locale.ROOT);
            if (!codes.add(code)) {
                throw new BusinessRuleException();
            }
            MetricEnums.MetricType metricType = MetricEnums.parse(
                    MetricEnums.MetricType.class, item.metricType());
            MetricEnums.EvidenceType evidenceType = MetricEnums.parse(
                    MetricEnums.EvidenceType.class, item.evidenceType());
            result.add(new CareMetricDtos.CareMetricConfigItem(
                    code, item.metricName().trim(), metricType.name(), item.required(),
                    evidenceType.name(), item.scoreWeight(), trimToNull(item.description())));
        }
        return result;
    }

    private CareMetricDtos.MetricChecklistResponse checklistResponse(String orderId) {
        return new CareMetricDtos.MetricChecklistResponse(repository.findChecklistItems(orderId));
    }

    private CareMetricDtos.MetricCheckResponse metricCheckResultInternal(String orderId) {
        List<CareMetricDtos.MetricCheckItem> result = repository.findOrderMetricItems(orderId).stream()
                .map(item -> {
                    boolean missingEvidence = item.required()
                            && !MetricEnums.EvidenceType.NONE.name().equals(item.evidenceType())
                            && repository.countApprovedEvidence(item.metricItemId()) == 0;
                    BigDecimal impact = switch (MetricEnums.parse(
                            MetricEnums.MetricStatus.class, item.metricStatus())) {
                        case MISSING, EXEMPT_REJECTED -> item.scoreWeight().negate();
                        default -> BigDecimal.ZERO;
                    };
                    return new CareMetricDtos.MetricCheckItem(
                            item.metricItemId(), item.metricName(), item.metricStatus(),
                            impact, missingEvidence);
                })
                .toList();
        return new CareMetricDtos.MetricCheckResponse(result);
    }

    private void requireService(String serviceId) {
        if (serviceId == null || serviceId.isBlank() || !repository.serviceExists(serviceId.trim())) {
            throw new NotFoundException();
        }
    }

    private Phase34To40MetricRepository.OrderContext requireOrder(String orderId) {
        if (orderId == null || orderId.isBlank()) {
            throw new BusinessRuleException();
        }
        return repository.findOrderContext(orderId.trim()).orElseThrow(NotFoundException::new);
    }

    private Phase34To40MetricRepository.MetricItemContext requireMetricItem(String metricItemId) {
        if (metricItemId == null || metricItemId.isBlank()) {
            throw new BusinessRuleException();
        }
        return repository.findMetricItem(metricItemId.trim()).orElseThrow(NotFoundException::new);
    }

    private Phase34To40MetricRepository.FileAsset requireOwnedFile(CurrentUser user, String fileId) {
        Phase34To40MetricRepository.FileAsset file = repository.findFile(fileId)
                .orElseThrow(NotFoundException::new);
        if (!user.userId().equals(file.uploadedBy())) {
            throw new ForbiddenException();
        }
        return file;
    }

    private void requirePermission(CurrentUser user, String permissionCode) {
        boolean managementRole = user.hasRole(RoleCode.ADMIN)
                || user.hasRole(RoleCode.CUSTOMER_SERVICE);
        if (!managementRole || !repository.hasPermission(user.userId(), permissionCode)) {
            throw new ForbiddenException();
        }
    }

    private void requireNurseOrAdminAccess(
            CurrentUser user,
            Phase34To40MetricRepository.OrderContext order,
            String adminPermission) {
        if (user.hasRole(RoleCode.NURSE) && user.userId().equals(order.nurseId())) {
            return;
        }
        requirePermission(user, adminPermission);
    }

    private void requireOrderReadAccess(
            CurrentUser user, Phase34To40MetricRepository.OrderContext order) {
        if (user.hasRole(RoleCode.NURSE) && user.userId().equals(order.nurseId())) {
            return;
        }
        if (user.hasRole(RoleCode.FAMILY)
                && repository.familyCanView(user.userId(), order.elderId())) {
            return;
        }
        if ((user.hasRole(RoleCode.ADMIN) || user.hasRole(RoleCode.CUSTOMER_SERVICE))
                && repository.hasPermission(user.userId(), REVIEW_PERMISSION)) {
            return;
        }
        throw new ForbiddenException();
    }

    private void insertMetricRecordOnce(
            Phase34To40MetricRepository.MetricItemContext item,
            String status,
            BigDecimal scoreDelta,
            String sourceType,
            String sourceId) {
        if (!repository.hasMetricRecord(
                item.metricItemId(), status, scoreDelta, sourceType, sourceId)) {
            repository.insertMetricRecord(
                    nextId("metric_record"), item, status, scoreDelta, sourceType, sourceId);
        }
    }

    private void log(
            CurrentUser user,
            String operationType,
            String bizType,
            String bizId,
            Object before,
            Object after) {
        repository.insertOperationLog(
                nextId("op"), user.userId(), user.primaryRole(), operationType,
                bizType, bizId, writeJson(before), writeJson(after), nextId("trace"));
    }

    private String writeJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to serialize metric operation log", exception);
        }
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private String nextId(String prefix) {
        String value = prefix + "_" + UUID.randomUUID().toString().replace("-", "");
        return value.substring(0, Math.min(32, value.length()));
    }
}
