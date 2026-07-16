package com.csu.carenest.careadmin.support;

import com.csu.carenest.careadmin.auth.CurrentUser;
import com.csu.carenest.careadmin.auth.RoleCode;
import com.csu.carenest.careadmin.common.BusinessRuleException;
import com.csu.carenest.careadmin.common.ConflictException;
import com.csu.carenest.careadmin.common.ForbiddenException;
import com.csu.carenest.careadmin.common.NotFoundException;
import com.csu.carenest.careadmin.score.Phase47To48ScoreService;
import com.fasterxml.jackson.core.JsonProcessingException;
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

/** 阶段43-46客服工单、回访、投诉和申诉事务服务。 */
@Service
public class Phase43To46SupportService {

    private static final String TICKET_PERMISSION = "CUSTOMER_SERVICE_TICKET_HANDLE";
    private static final String FOLLOW_UP_PERMISSION = "FOLLOW_UP_MANAGE";
    private static final String COMPLAINT_PERMISSION = "COMPLAINT_HANDLE";
    private static final String APPEAL_PERMISSION = "NURSE_APPEAL_REVIEW";
    private static final Set<String> REVIEWABLE_ORDER_STATUSES = Set.of("WAIT_CONFIRM", "COMPLETED");

    private final Phase43To46SupportRepository repository;
    private final ObjectMapper objectMapper;
    private final Phase47To48ScoreService scoreService;

    public Phase43To46SupportService(
            Phase43To46SupportRepository repository,
            ObjectMapper objectMapper,
            Phase47To48ScoreService scoreService) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.scoreService = scoreService;
    }

    @Transactional
    public SupportDtos.TicketResponse createTicket(CurrentUser user, SupportDtos.TicketRequest request) {
        requireTicketCreateAccess(user, request.elderId());
        SupportEnums.TicketPriority priority = SupportEnums.parse(
                SupportEnums.TicketPriority.class, request.priority());
        SupportEnums.TicketSource source = SupportEnums.parse(
                SupportEnums.TicketSource.class, request.sourceType());
        String ticketId = nextId("ticket");
        repository.insertTicket(
                ticketId, request.elderId().trim(), user.userId(), request.category().trim(),
                priority.name(), request.description().trim(), source.name());
        log(user, "CREATE_CUSTOMER_SERVICE_TICKET", "CUSTOMER_SERVICE_TICKET", ticketId,
                null, Map.of("status", "PENDING", "priority", priority.name()));
        return new SupportDtos.TicketResponse(ticketId, "PENDING");
    }

    @Transactional(readOnly = true)
    public List<SupportDtos.TicketResponse> tickets(CurrentUser user) {
        requirePermission(user, TICKET_PERMISSION);
        return repository.findTickets();
    }

    @Transactional
    public SupportDtos.TicketResponse reply(
            CurrentUser user, String ticketId, SupportDtos.TicketRequest request) {
        requirePermission(user, TICKET_PERMISSION);
        Phase43To46SupportRepository.TicketContext ticket = requireTicket(ticketId);
        requireMatchingElder(ticket, request.elderId());
        if (repository.moveTicketToProcessing(ticketId, user.userId()) == 0) {
            throw new ConflictException();
        }
        repository.insertMessage(
                nextId("message"), ticketId, user.userId(), user.primaryRole(),
                "TEXT", request.description().trim());
        log(user, "REPLY_CUSTOMER_SERVICE_TICKET", "CUSTOMER_SERVICE_TICKET", ticketId,
                Map.of("status", ticket.status()), Map.of("status", "PROCESSING"));
        return new SupportDtos.TicketResponse(ticketId, "PROCESSING");
    }

    @Transactional
    public SupportDtos.TicketResponse close(
            CurrentUser user, String ticketId, SupportDtos.TicketRequest request) {
        requirePermission(user, TICKET_PERMISSION);
        Phase43To46SupportRepository.TicketContext ticket = requireTicket(ticketId);
        requireMatchingElder(ticket, request.elderId());
        if ("URGENT".equals(ticket.priority()) && repository.countFollowUps(ticketId) == 0) {
            throw new BusinessRuleException();
        }
        if (repository.closeTicket(ticketId, user.userId()) == 0) {
            throw new ConflictException();
        }
        repository.insertMessage(
                nextId("message"), ticketId, user.userId(), user.primaryRole(),
                "SYSTEM", request.description().trim());
        log(user, "CLOSE_CUSTOMER_SERVICE_TICKET", "CUSTOMER_SERVICE_TICKET", ticketId,
                Map.of("status", ticket.status()), Map.of("status", "CLOSED"));
        return new SupportDtos.TicketResponse(ticketId, "CLOSED");
    }

    @Transactional
    public SupportDtos.FollowUpResponse addFollowUp(
            CurrentUser user, String ticketId, SupportDtos.FollowUpRequest request) {
        requirePermission(user, FOLLOW_UP_PERMISSION);
        Phase43To46SupportRepository.TicketContext ticket = requireTicket(ticketId);
        if ("CLOSED".equals(ticket.status())) {
            throw new ConflictException();
        }
        SupportEnums.FollowUpType type = SupportEnums.parse(
                SupportEnums.FollowUpType.class, request.method());
        String resultText = request.result().trim().toUpperCase(java.util.Locale.ROOT);
        String targetStatus = Set.of("PROCESSING", "RESOLVED").contains(resultText)
                ? resultText : "PROCESSING";
        String content = request.content().trim();
        if (!Set.of("PROCESSING", "RESOLVED").contains(resultText)) {
            // 数据库没有独立 result 列，使用结构明确的中文后缀保留开工文档要求的结果。
            content = limit(content + "\n处理结果：" + request.result().trim(), 1000);
        }
        String followUpId = nextId("followup");
        repository.insertTicketFollowUp(
                followUpId, ticket, type.name(), content, request.nextFollowUpAt(), user.userId());
        repository.updateTicketStatus(ticketId, targetStatus, user.userId());
        log(user, "CREATE_TICKET_FOLLOW_UP", "CUSTOMER_SERVICE_TICKET", ticketId,
                Map.of("status", ticket.status()), Map.of("status", targetStatus, "followUpId", followUpId));
        return new SupportDtos.FollowUpResponse(followUpId, targetStatus);
    }

    @Transactional(readOnly = true)
    public List<SupportDtos.FollowUpResponse> followUps(CurrentUser user, String ticketId) {
        requirePermission(user, FOLLOW_UP_PERMISSION);
        requireTicket(ticketId);
        return repository.findTicketFollowUps(ticketId);
    }

    @Transactional
    public SupportDtos.ReviewComplaintResponse submitReview(
            CurrentUser user, String orderId, SupportDtos.ReviewComplaintRequest request) {
        Phase43To46SupportRepository.OrderReviewContext order = requireReviewOrder(user, orderId);
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
                reviewId, null, order.orderStatus());
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
        return new SupportDtos.ReviewComplaintResponse(null, complaintId, "PENDING");
    }

    @Transactional(readOnly = true)
    public List<SupportDtos.ReviewComplaintResponse> complaints(CurrentUser user) {
        requirePermission(user, COMPLAINT_PERMISSION);
        return repository.findComplaints();
    }

    @Transactional
    public SupportDtos.ReviewComplaintResponse handleComplaint(
            CurrentUser user, String complaintId, SupportDtos.ReviewComplaintRequest request) {
        requirePermission(user, COMPLAINT_PERMISSION);
        Phase43To46SupportRepository.ComplaintContext complaint = repository.findComplaint(complaintId)
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
        return new SupportDtos.ReviewComplaintResponse(null, complaintId, status);
    }

    @Transactional
    public SupportDtos.AppealResponse submitAppeal(
            CurrentUser user, SupportDtos.AppealRequest request) {
        SupportEnums.AppealTarget target = SupportEnums.parse(
                SupportEnums.AppealTarget.class, request.targetType());
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
        return new SupportDtos.AppealResponse(appealId, "PENDING", BigDecimal.ZERO);
    }

    @Transactional(readOnly = true)
    public List<SupportDtos.AppealResponse> appeals(CurrentUser user) {
        if (user.hasRole(RoleCode.NURSE)) {
            return repository.findAppeals(user.userId());
        }
        requirePermission(user, APPEAL_PERMISSION);
        return repository.findAllAppeals();
    }

    @Transactional
    public SupportDtos.AppealResponse reviewAppeal(
            CurrentUser user, String appealId, SupportDtos.AppealRequest request) {
        requirePermission(user, APPEAL_PERMISSION);
        Phase43To46SupportRepository.AppealContext appeal = repository.findAppeal(appealId)
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
        return new SupportDtos.AppealResponse(appealId, decision.name(), adjustment);
    }

    private void requireTicketCreateAccess(CurrentUser user, String elderId) {
        if ((user.hasRole(RoleCode.ADMIN) || user.hasRole(RoleCode.CUSTOMER_SERVICE))
                && repository.hasPermission(user.userId(), TICKET_PERMISSION)) {
            return;
        }
        if ((user.hasRole(RoleCode.ELDER) || user.hasRole(RoleCode.FAMILY))
                && repository.canAccessElder(user.userId(), elderId.trim())) {
            return;
        }
        throw new ForbiddenException();
    }

    private Phase43To46SupportRepository.TicketContext requireTicket(String ticketId) {
        if (ticketId == null || ticketId.isBlank()) {
            throw new BusinessRuleException();
        }
        return repository.findTicket(ticketId.trim()).orElseThrow(NotFoundException::new);
    }

    private void requireMatchingElder(
            Phase43To46SupportRepository.TicketContext ticket, String elderId) {
        if (!ticket.elderId().equals(elderId.trim())) {
            throw new BusinessRuleException();
        }
    }

    private Phase43To46SupportRepository.OrderReviewContext requireReviewOrder(
            CurrentUser user, String orderId) {
        Phase43To46SupportRepository.OrderReviewContext order = repository.findOrderForReview(orderId)
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
