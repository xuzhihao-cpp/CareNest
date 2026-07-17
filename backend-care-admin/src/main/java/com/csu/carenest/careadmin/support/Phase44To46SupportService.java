package com.csu.carenest.careadmin.support;

import com.csu.carenest.careadmin.auth.CurrentUser;
import com.csu.carenest.careadmin.auth.RoleCode;
import com.csu.carenest.careadmin.common.BusinessRuleException;
import com.csu.carenest.careadmin.common.ConflictException;
import com.csu.carenest.careadmin.common.ForbiddenException;
import com.csu.carenest.careadmin.common.NotFoundException;
import com.csu.carenest.careadmin.score.Phase47To48ScoreService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** 阶段44-46回访、投诉和申诉事务服务。 */
@Service
public class Phase44To46SupportService {

    private static final String FOLLOW_UP_PERMISSION = "FOLLOW_UP_MANAGE";
    private static final String COMPLAINT_PERMISSION = "COMPLAINT_HANDLE";
    private static final String APPEAL_PERMISSION = "NURSE_APPEAL_REVIEW";
    private static final String APPEAL_CREATE_PERMISSION = "NURSE_APPEAL_CREATE";
    private static final Set<String> REVIEWABLE_ORDER_STATUSES = Set.of("WAIT_CONFIRM", "COMPLETED");

    private final Phase44To46SupportRepository repository;
    private final ObjectMapper objectMapper;
    private final Phase47To48ScoreService scoreService;

    public Phase44To46SupportService(
            Phase44To46SupportRepository repository,
            ObjectMapper objectMapper,
            Phase47To48ScoreService scoreService) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.scoreService = scoreService;
    }

    @Transactional
    public SupportDtos.FollowUpResponse addFollowUp(
            CurrentUser user, String ticketId, SupportDtos.FollowUpRequest request) {
        requirePermission(user, FOLLOW_UP_PERMISSION);
        Phase44To46SupportRepository.TicketContext ticket = requireTicket(ticketId);
        if ("CLOSED".equals(ticket.status())) {
            throw new ConflictException();
        }
        SupportEnums.FollowUpType type = SupportEnums.parse(
                SupportEnums.FollowUpType.class, request.method());
        String resultText = request.result().trim().toUpperCase(java.util.Locale.ROOT);
        String targetStatus = Set.of("PROCESSING", "RESOLVED").contains(resultText)
                ? resultText : "PROCESSING";
        Map<String, Object> followUp = new LinkedHashMap<>();
        followUp.put("content", request.content().trim());
        followUp.put("result", request.result().trim());
        String content = limit(writeJson(followUp), 1000);
        String followUpId = nextId("followup");
        repository.insertTicketFollowUp(
                followUpId, ticket, type.name(), content, request.nextFollowUpAt(), user.userId());
        repository.updateTicketStatus(ticketId, targetStatus, user.userId());
        log(user, "CREATE_TICKET_FOLLOW_UP", "CUSTOMER_SERVICE_TICKET", ticketId,
                Map.of("status", ticket.status()), Map.of("status", targetStatus, "followUpId", followUpId));
        return new SupportDtos.FollowUpResponse(
                followUpId, targetStatus, type.name(), request.content().trim(),
                request.nextFollowUpAt(), request.result().trim(), java.time.LocalDateTime.now());
    }

    @Transactional(readOnly = true)
    public List<SupportDtos.FollowUpResponse> followUps(CurrentUser user, String ticketId) {
        requirePermission(user, FOLLOW_UP_PERMISSION);
        requireTicket(ticketId);
        return repository.findTicketFollowUps(ticketId).stream().map(this::followUpResponse).toList();
    }

    @Transactional
    public SupportDtos.ReviewComplaintResponse submitReview(
            CurrentUser user, String orderId, SupportDtos.ReviewComplaintRequest request) {
        Phase44To46SupportRepository.OrderReviewContext order = requireReviewOrder(user, orderId);
        if (request.rating() == null) {
            throw new BusinessRuleException();
        }
        if (repository.reviewExists(orderId, user.userId())) {
            throw new ConflictException();
        }
        validateFiles(user.userId(), request.fileIds());
        String reviewId = nextId("review");
        String content = reviewContent(request);
        repository.insertReview(reviewId, order, user.userId(), request.rating(), content);
        log(user, "SUBMIT_SERVICE_REVIEW", "SERVICE_REVIEW", reviewId,
                null, Map.of("orderId", orderId, "rating", request.rating()));
        return new SupportDtos.ReviewComplaintResponse(
                reviewId, null, order.orderId(), order.serviceName(), null,
                order.orderStatus(), request.rating(), List.copyOf(request.tags()), null,
                blankToNull(request.content()), List.copyOf(request.fileIds()), null,
                java.time.LocalDateTime.now());
    }

    @Transactional
    public SupportDtos.ReviewComplaintResponse submitComplaint(
            CurrentUser user, String orderId, SupportDtos.ReviewComplaintRequest request) {
        requireReviewOrder(user, orderId);
        if (request.content() == null || request.content().isBlank()
                || request.reasonType() == null || request.reasonType().isBlank()) {
            throw new BusinessRuleException();
        }
        validateFiles(user.userId(), request.fileIds());
        String complaintId = nextId("complaint");
        String content = complaintContent(request);
        repository.insertComplaint(complaintId, orderId, user.userId(), content);
        log(user, "SUBMIT_COMPLAINT", "COMPLAINT", complaintId,
                null, Map.of("status", "PENDING", "orderId", orderId));
        return new SupportDtos.ReviewComplaintResponse(
                null, complaintId, orderId, null, null, "PENDING", null, List.of(),
                request.reasonType().trim(), request.content().trim(),
                List.copyOf(request.fileIds()), null, java.time.LocalDateTime.now());
    }

    @Transactional(readOnly = true)
    public List<SupportDtos.ReviewComplaintResponse> complaints(CurrentUser user) {
        requirePermission(user, COMPLAINT_PERMISSION);
        return repository.findComplaints().stream().map(this::complaintResponse).toList();
    }

    @Transactional
    public SupportDtos.ReviewComplaintResponse handleComplaint(
            CurrentUser user, String complaintId, SupportDtos.ReviewComplaintRequest request) {
        requirePermission(user, COMPLAINT_PERMISSION);
        Phase44To46SupportRepository.ComplaintContext complaint = repository.findComplaint(complaintId)
                .orElseThrow(NotFoundException::new);
        String status = "RESOLVED";
        if (request.reasonType() != null && !request.reasonType().isBlank()) {
            SupportEnums.ComplaintStatus parsed = SupportEnums.parse(
                    SupportEnums.ComplaintStatus.class, request.reasonType());
            if (parsed != SupportEnums.ComplaintStatus.RESOLVED
                    && parsed != SupportEnums.ComplaintStatus.REJECTED) {
                throw new BusinessRuleException();
            }
            status = parsed.name();
        }
        if (request.content() == null || request.content().isBlank()) {
            throw new BusinessRuleException();
        }
        if (repository.handleComplaint(
                complaintId, status, request.content().trim(), user.userId()) == 0) {
            throw new ConflictException();
        }
        log(user, "HANDLE_COMPLAINT", "COMPLAINT", complaintId,
                Map.of("status", complaint.status()), Map.of("status", status));
        return new SupportDtos.ReviewComplaintResponse(
                null, complaintId, complaint.orderId(), null, null, status,
                null, List.of(), null, null, List.of(), request.content().trim(),
                java.time.LocalDateTime.now());
    }

    @Transactional
    public SupportDtos.AppealResponse submitAppeal(
            CurrentUser user, SupportDtos.AppealRequest request) {
        SupportEnums.AppealTarget target = SupportEnums.parse(
                SupportEnums.AppealTarget.class, request.targetType());
        if (!user.hasRole(RoleCode.NURSE)
                || !repository.hasPermission(user.userId(), APPEAL_CREATE_PERMISSION)) {
            throw new ForbiddenException();
        }
        String nurseId = requireNurseIdentity(user, target.name(), request.targetId());
        if (!repository.appealTargetBelongsToNurse(target.name(), request.targetId(), nurseId)) {
            throw new ForbiddenException();
        }
        if (repository.pendingAppealExists(nurseId, target.name(), request.targetId())) {
            throw new ConflictException();
        }
        validateFiles(user.userId(), request.fileIds());
        String appealId = nextId("appeal");
        repository.insertAppeal(
                appealId, nurseId, target.name(), request.targetId().trim(),
                request.reason().trim(), writeJson(normalizeIds(request.fileIds())));
        log(user, "SUBMIT_NURSE_APPEAL", "NURSE_APPEAL", appealId,
                null, Map.of("status", "PENDING", "targetType", target.name()));
        return new SupportDtos.AppealResponse(
                appealId, nurseId, null, target.name(), request.targetId().trim(),
                targetLabel(target.name()), request.reason().trim(),
                List.copyOf(request.fileIds()), "PENDING", BigDecimal.ZERO,
                null, java.time.LocalDateTime.now());
    }

    @Transactional(readOnly = true)
    public List<SupportDtos.AppealResponse> appeals(CurrentUser user) {
        if (user.hasRole(RoleCode.NURSE)) {
            return repository.findAppeals(user.userId()).stream().map(this::appealResponse).toList();
        }
        requirePermission(user, APPEAL_PERMISSION);
        return repository.findAllAppeals().stream().map(this::appealResponse).toList();
    }

    @Transactional
    public SupportDtos.AppealResponse reviewAppeal(
            CurrentUser user, String appealId, SupportDtos.AppealRequest request) {
        requirePermission(user, APPEAL_PERMISSION);
        Phase44To46SupportRepository.AppealContext appeal = repository.findAppeal(appealId)
                .orElseThrow(NotFoundException::new);
        if (!"PENDING".equals(appeal.status())) {
            throw new ConflictException();
        }
        if (!appeal.targetId().equals(request.targetId().trim())) {
            throw new BusinessRuleException();
        }
        // 冻结请求没有新增 reviewResult 字段，审核接口按文档中的 targetType 承载 APPROVED/REJECTED。
        SupportEnums.AppealStatus decision = SupportEnums.parse(
                SupportEnums.AppealStatus.class, request.targetType());
        if (decision == SupportEnums.AppealStatus.PENDING) {
            throw new BusinessRuleException();
        }
        BigDecimal adjustment = decision == SupportEnums.AppealStatus.APPROVED
                ? repository.targetDeduction(appeal) : BigDecimal.ZERO;
        if (repository.reviewAppeal(
                appealId, decision.name(), adjustment,
                request.reason().trim(), user.userId()) == 0) {
            throw new ConflictException();
        }
        scoreService.recalculateAfterAppeal(user, appeal.nurseId(), appealId);
        log(user, "REVIEW_NURSE_APPEAL", "NURSE_APPEAL", appealId,
                Map.of("status", appeal.status()),
                Map.of("status", decision.name(), "scoreAdjustment", adjustment));
        return new SupportDtos.AppealResponse(
                appealId, appeal.nurseId(), null, appeal.targetType(), appeal.targetId(),
                targetLabel(appeal.targetType()), null, List.of(), decision.name(),
                adjustment, request.reason().trim(), java.time.LocalDateTime.now());
    }

    private SupportDtos.FollowUpResponse followUpResponse(
            Phase44To46SupportRepository.FollowUpRecord record) {
        JsonNode data = readObject(record.storedContent());
        return new SupportDtos.FollowUpResponse(
                record.followUpId(), record.ticketStatus(), record.method(),
                data.path("content").asText(record.storedContent()), record.nextFollowUpAt(),
                data.path("result").asText("已记录"), record.createdAt());
    }

    private SupportDtos.ReviewComplaintResponse complaintResponse(
            Phase44To46SupportRepository.ComplaintRecord record) {
        JsonNode data = readObject(record.storedContent());
        return new SupportDtos.ReviewComplaintResponse(
                record.reviewId(), record.complaintId(), record.orderId(), record.serviceName(),
                record.complainantName(), record.status(), null, List.of(),
                data.path("reasonType").asText(null), data.path("content").asText(record.storedContent()),
                readStringList(data.path("fileIds")), record.handleResult(), record.createdAt());
    }

    private SupportDtos.AppealResponse appealResponse(
            Phase44To46SupportRepository.AppealRecord record) {
        return new SupportDtos.AppealResponse(
                record.appealId(), record.nurseId(), record.nurseName(), record.targetType(),
                record.targetId(), targetLabel(record.targetType()), record.reason(),
                readStringList(readArray(record.fileIdsJson())), record.status(),
                record.scoreAdjustment(), record.reviewComment(), record.createdAt());
    }

    private String targetLabel(String targetType) {
        return switch (targetType) {
            case "COMPLAINT" -> "投诉处理";
            case "METRIC" -> "服务质量指标";
            case "EXCEPTION_PROOF" -> "异常情况证明";
            default -> "评分变更";
        };
    }

    private JsonNode readObject(String value) {
        try {
            JsonNode node = objectMapper.readTree(value);
            return node != null && node.isObject() ? node : objectMapper.createObjectNode();
        } catch (Exception ignored) {
            return objectMapper.createObjectNode();
        }
    }

    private JsonNode readArray(String value) {
        try {
            JsonNode node = objectMapper.readTree(value == null ? "[]" : value);
            return node != null && node.isArray() ? node : objectMapper.createArrayNode();
        } catch (Exception ignored) {
            return objectMapper.createArrayNode();
        }
    }

    private List<String> readStringList(JsonNode node) {
        List<String> values = new ArrayList<>();
        if (node != null && node.isArray()) {
            node.forEach(item -> {
                if (item.isTextual() && !item.asText().isBlank()) values.add(item.asText());
            });
        }
        return values;
    }

    private Phase44To46SupportRepository.TicketContext requireTicket(String ticketId) {
        if (ticketId == null || ticketId.isBlank()) {
            throw new BusinessRuleException();
        }
        return repository.findTicket(ticketId.trim()).orElseThrow(NotFoundException::new);
    }

    private Phase44To46SupportRepository.OrderReviewContext requireReviewOrder(
            CurrentUser user, String orderId) {
        Phase44To46SupportRepository.OrderReviewContext order = repository.findOrderForReview(orderId)
                .orElseThrow(NotFoundException::new);
        if (!REVIEWABLE_ORDER_STATUSES.contains(order.orderStatus())) {
            throw new ConflictException();
        }
        if (!repository.familyCanReview(user.userId(), order)) {
            throw new ForbiddenException();
        }
        return order;
    }

    private String requireNurseIdentity(CurrentUser user, String targetType, String targetId) {
        if (user.hasRole(RoleCode.NURSE)) {
            return user.userId();
        }
        requirePermission(user, APPEAL_PERMISSION);
        return repository.findTargetNurse(targetType, targetId).orElseThrow(NotFoundException::new);
    }

    private void requirePermission(CurrentUser user, String permission) {
        boolean role = user.hasRole(RoleCode.ADMIN) || user.hasRole(RoleCode.CUSTOMER_SERVICE);
        if (!role || !repository.hasPermission(user.userId(), permission)) {
            throw new ForbiddenException();
        }
    }

    private void validateFiles(String userId, List<String> fileIds) {
        List<String> ids = normalizeIds(fileIds);
        if (ids.size() != fileIds.size()) {
            throw new BusinessRuleException();
        }
        for (String fileId : ids) {
            if (!repository.fileOwnedBy(fileId, userId)) {
                throw new ForbiddenException();
            }
        }
    }

    private List<String> normalizeIds(List<String> values) {
        return values.stream().map(String::trim).filter(value -> !value.isEmpty())
                .collect(java.util.stream.Collectors.collectingAndThen(
                        java.util.stream.Collectors.toCollection(LinkedHashSet::new), ArrayList::new));
    }

    private String reviewContent(SupportDtos.ReviewComplaintRequest request) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("content", blankToNull(request.content()));
        value.put("tags", request.tags());
        value.put("fileIds", request.fileIds());
        return limit(writeJson(value), 500);
    }

    private String complaintContent(SupportDtos.ReviewComplaintRequest request) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("reasonType", request.reasonType().trim());
        value.put("content", request.content().trim());
        value.put("fileIds", request.fileIds());
        String json = writeJson(value);
        if (json.length() > 1000) {
            throw new BusinessRuleException();
        }
        return json;
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String limit(String value, int max) {
        if (value.length() > max) {
            throw new BusinessRuleException();
        }
        return value;
    }

    private void log(
            CurrentUser user, String operationType, String bizType,
            String bizId, Object before, Object after) {
        repository.insertOperationLog(
                nextId("op"), user.userId(), user.primaryRole(), operationType,
                bizType, bizId, writeJsonNullable(before), writeJsonNullable(after), nextId("trace"));
    }

    private String writeJsonNullable(Object value) {
        return value == null ? null : writeJson(value);
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to serialize support workflow data", exception);
        }
    }

    private String nextId(String prefix) {
        String value = prefix + "_" + UUID.randomUUID().toString().replace("-", "");
        return value.substring(0, Math.min(32, value.length()));
    }
}
