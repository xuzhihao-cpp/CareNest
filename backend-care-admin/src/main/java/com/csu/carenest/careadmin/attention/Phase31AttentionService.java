package com.csu.carenest.careadmin.attention;

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

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/** 阶段31服务前注意事项生成、确认与开始服务门禁。 */
@Service
public class Phase31AttentionService {

    private static final String NURSE_ACK_PERMISSION = "NURSE_ATTENTION_ACK";
    private static final String ADMIN_REVIEW_PERMISSION = "CARE_ATTENTION_REVIEW";
    private static final Set<String> READABLE_ORDER_STATUSES = Set.of(
            "DISPATCHED", "ACCEPTED", "ON_THE_WAY", "SERVING");
    private static final Set<String> ACKNOWLEDGEABLE_ORDER_STATUSES = Set.of(
            "DISPATCHED", "ACCEPTED", "ON_THE_WAY");

    private final Phase31AttentionRepository repository;
    private final ObjectMapper objectMapper;

    public Phase31AttentionService(Phase31AttentionRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public AttentionNoticeDtos.AttentionNoticeResponse attentionNotices(
            CurrentUser currentUser, String orderId) {
        Phase31AttentionRepository.OrderContext context = requireContext(orderId);
        boolean adminReview = requireReadAccess(currentUser, context);
        if (!adminReview && !READABLE_ORDER_STATUSES.contains(context.orderStatus())) {
            throw new ConflictException();
        }
        if (ACKNOWLEDGEABLE_ORDER_STATUSES.contains(context.orderStatus())) {
            ensureGenerated(context);
        }
        return response(context);
    }

    @Transactional
    public AttentionNoticeDtos.AttentionNoticeResponse acknowledge(
            CurrentUser currentUser,
            String orderId,
            AttentionNoticeDtos.AckRequest request) {
        if (!currentUser.hasRole(RoleCode.NURSE)
                || !repository.hasPermission(currentUser.userId(), NURSE_ACK_PERMISSION)) {
            throw new ForbiddenException();
        }
        Phase31AttentionRepository.OrderContext context = requireContext(orderId);
        requireAssignedNurse(currentUser, context);
        if (!ACKNOWLEDGEABLE_ORDER_STATUSES.contains(context.orderStatus())) {
            throw new ConflictException();
        }

        ensureGenerated(context);
        List<String> noticeIds = request.noticeIds().stream()
                .map(String::trim)
                .filter(this::hasText)
                .collect(java.util.stream.Collectors.collectingAndThen(
                        java.util.stream.Collectors.toCollection(LinkedHashSet::new), ArrayList::new));
        if (noticeIds.isEmpty()) {
            throw new BusinessRuleException();
        }
        List<String> visibleIds = repository.findVisibleNoticeIds(
                orderId, context.taskId(), context.nurseId(), noticeIds);
        if (visibleIds.size() != noticeIds.size()) {
            // 对越权或其他订单的 noticeId 统一返回业务校验失败，不泄露资源是否存在。
            throw new BusinessRuleException();
        }
        for (String noticeId : noticeIds) {
            repository.acknowledge(nextId("ack"), noticeId, context, currentUser.userId());
        }
        repository.insertOperationLog(
                nextId("op"), currentUser.userId(), currentUser.primaryRole(), orderId,
                writeJson(java.util.Map.of("noticeIds", noticeIds)), nextId("trace"));
        return response(context);
    }

    /**
     * 开始服务前重新生成当前来源快照并检查数据库确认记录。
     * 该检查位于任务状态事务内，不能被前端按钮状态绕过。
     */
    @Transactional
    public void requireAllRequiredAcknowledged(String orderId, String taskId, String nurseId) {
        Phase31AttentionRepository.OrderContext context = requireContext(orderId);
        if (!taskId.equals(context.taskId()) || !nurseId.equals(context.nurseId())) {
            throw new ConflictException();
        }
        ensureGenerated(context);
        if (repository.countUnacknowledgedRequired(orderId, taskId, nurseId) > 0) {
            throw new BusinessRuleException();
        }
    }

    private boolean requireReadAccess(
            CurrentUser currentUser, Phase31AttentionRepository.OrderContext context) {
        if (currentUser.hasRole(RoleCode.NURSE) && currentUser.userId().equals(context.nurseId())) {
            return false;
        }
        boolean adminRole = currentUser.hasRole(RoleCode.ADMIN)
                || currentUser.hasRole(RoleCode.CUSTOMER_SERVICE);
        if (!adminRole || !repository.hasPermission(currentUser.userId(), ADMIN_REVIEW_PERMISSION)) {
            throw new ForbiddenException();
        }
        return true;
    }

    private void requireAssignedNurse(
            CurrentUser currentUser, Phase31AttentionRepository.OrderContext context) {
        if (!currentUser.userId().equals(context.nurseId())) {
            throw new ForbiddenException();
        }
    }

    private Phase31AttentionRepository.OrderContext requireContext(String orderId) {
        if (!hasText(orderId)) {
            throw new BusinessRuleException();
        }
        return repository.findOrderContext(orderId.trim()).orElseThrow(NotFoundException::new);
    }

    private void ensureGenerated(Phase31AttentionRepository.OrderContext context) {
        List<Candidate> candidates = new ArrayList<>();
        String archiveVersion = hasText(context.archiveId())
                ? context.archiveId() + ":v" + (context.archiveVersion() == null ? 0 : context.archiveVersion())
                : null;

        if (hasText(context.careSummary()) && hasText(context.archiveId())) {
            candidates.add(new Candidate(
                    "INFO", "照护重点：" + context.careSummary(), "HEALTH_ARCHIVE",
                    context.archiveId() + ":summary", archiveVersion, false));
        }
        for (Phase31AttentionRepository.AllergySource allergy : repository.findAllergies(context.elderId())) {
            String detail = joinDetail(allergy.reaction(), allergy.remark());
            candidates.add(new Candidate(
                    allergyLevel(allergy.severity()),
                    "已归档过敏信息：" + allergy.allergen()
                            + (hasText(detail) ? "（" + detail + "）" : "")
                            + "。护理过程中不得自行建议用药调整。",
                    "HEALTH_ARCHIVE", allergy.id(), archiveVersion, true));
        }
        for (Phase31AttentionRepository.RiskSource risk : repository.findRisks(context.elderId())) {
            String detail = hasText(risk.remark()) ? "，" + risk.remark() : "";
            candidates.add(new Candidate(
                    riskLevel(risk.level()),
                    "已归档护理风险：" + risk.name() + detail + "。服务中请重点观察并按护理规范处理。",
                    "HEALTH_ARCHIVE", risk.id(), archiveVersion, true));
        }
        if (hasText(context.serviceDescription())) {
            candidates.add(new Candidate(
                    "INFO", "服务项目“" + context.serviceName() + "”注意事项：" + context.serviceDescription(),
                    "SERVICE_ITEM", context.serviceId(), context.serviceId() + ":current", false));
        }
        if (hasText(context.dispatchRemark())) {
            candidates.add(new Candidate(
                    "WARNING", "本单派单注意事项：" + context.dispatchRemark(),
                    "ORDER_CONTEXT", context.taskId(), context.taskId() + ":current", true));
        }

        for (Candidate candidate : candidates) {
            String content = limit(normalizeContent(candidate.content()), 500);
            String hash = sha256(context.orderId() + "|" + candidate.source() + "|"
                    + candidate.sourceId() + "|" + candidate.level() + "|"
                    + candidate.requiredAck() + "|" + content);
            repository.saveNotice(new Phase31AttentionRepository.NoticeSnapshot(
                    nextId("notice"), context.orderId(), context.taskId(), context.nurseId(),
                    candidate.level(), content, candidate.source(), candidate.sourceId(),
                    candidate.sourceVersion(), candidate.requiredAck(), hash));
        }

        Set<SourceKey> currentSources = candidates.stream()
                .map(candidate -> new SourceKey(candidate.source(), candidate.sourceId()))
                .collect(java.util.stream.Collectors.toSet());
        for (Phase31AttentionRepository.ActiveNoticeKey active :
                repository.findActiveNoticeKeys(context.orderId())) {
            if (!currentSources.contains(new SourceKey(active.source(), active.sourceId()))) {
                // 来源被删除时保留历史记录并失效，避免旧风险继续阻塞服务。
                repository.cancelNotice(active.noticeId());
            }
        }
    }

    private AttentionNoticeDtos.AttentionNoticeResponse response(
            Phase31AttentionRepository.OrderContext context) {
        return new AttentionNoticeDtos.AttentionNoticeResponse(
                repository.findActiveNotices(context.orderId(), context.nurseId()));
    }

    private String allergyLevel(String severity) {
        if (!hasText(severity)) {
            return "CRITICAL";
        }
        String normalized = severity.trim().toUpperCase(Locale.ROOT);
        return Set.of("LOW", "MILD", "轻微").contains(normalized) ? "WARNING" : "CRITICAL";
    }

    private String riskLevel(String level) {
        if (!hasText(level)) {
            return "WARNING";
        }
        return switch (level.trim().toUpperCase(Locale.ROOT)) {
            case "HIGH", "CRITICAL", "高", "严重" -> "CRITICAL";
            case "LOW", "INFO", "低", "轻微" -> "INFO";
            default -> "WARNING";
        };
    }

    private String joinDetail(String first, String second) {
        if (hasText(first) && hasText(second)) {
            return first.trim() + "；" + second.trim();
        }
        return hasText(first) ? first.trim() : hasText(second) ? second.trim() : null;
    }

    private String normalizeContent(String value) {
        return value.trim().replaceAll("\\s+", " ");
    }

    private String limit(String value, int maxLength) {
        return value.length() <= maxLength ? value : value.substring(0, maxLength - 1) + "…";
    }

    private String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to serialize attention operation log", exception);
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String nextId(String prefix) {
        return (prefix + "_" + UUID.randomUUID().toString().replace("-", ""))
                .substring(0, Math.min(32, prefix.length() + 33));
    }

    private record Candidate(
            String level, String content, String source, String sourceId,
            String sourceVersion, boolean requiredAck) {
    }

    private record SourceKey(String source, String sourceId) {
    }
}
