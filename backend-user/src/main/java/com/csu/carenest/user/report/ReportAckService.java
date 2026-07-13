package com.csu.carenest.user.report;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.csu.carenest.user.auth.AuthService;
import com.csu.carenest.user.auth.OperationLog;
import com.csu.carenest.user.auth.OperationLogMapper;
import com.csu.carenest.user.auth.RoleCode;
import com.csu.carenest.user.common.ApiException;
import com.csu.carenest.user.common.ForbiddenException;
import com.csu.carenest.user.common.NotFoundException;
import com.csu.carenest.user.flow.ElderFamilyBinding;
import com.csu.carenest.user.flow.ElderFamilyBindingMapper;
import com.csu.carenest.user.flow.ElderProfile;
import com.csu.carenest.user.flow.ElderProfileMapper;
import com.csu.carenest.user.redis.RedisKeyFactory;
import com.csu.carenest.user.redis.RedisLockService;
import com.csu.carenest.user.redis.HomeCacheInvalidator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

@Service
public class ReportAckService {

    private static final String ACCEPTED = "ACCEPTED";
    private static final String REJECTED = "REJECTED";
    private static final String WAIT_CONFIRM = "WAIT_CONFIRM";
    private static final String REPORT_CONFIRM = "REPORT_CONFIRM";
    private static final String ARCHIVE_EDIT = "ARCHIVE_EDIT";

    private final AuthService authService;
    private final ServiceReportMapper reportMapper;
    private final NursingOrderMapper orderMapper;
    private final CareReportAckMapper ackMapper;
    private final HealthInfoReviewTaskMapper reviewTaskMapper;
    private final OrderStatusLogMapper orderStatusLogMapper;
    private final OperationLogMapper operationLogMapper;
    private final ElderFamilyBindingMapper bindingMapper;
    private final ElderProfileMapper elderProfileMapper;
    private final ObjectMapper objectMapper;
    private final RedisLockService lockService;
    private final HomeCacheInvalidator homeCacheInvalidator;
    private final JdbcTemplate jdbcTemplate;

    public ReportAckService(
            AuthService authService,
            ServiceReportMapper reportMapper,
            NursingOrderMapper orderMapper,
            CareReportAckMapper ackMapper,
            HealthInfoReviewTaskMapper reviewTaskMapper,
            OrderStatusLogMapper orderStatusLogMapper,
            OperationLogMapper operationLogMapper,
            ElderFamilyBindingMapper bindingMapper,
            ElderProfileMapper elderProfileMapper,
            ObjectMapper objectMapper,
            RedisLockService lockService,
            HomeCacheInvalidator homeCacheInvalidator,
            JdbcTemplate jdbcTemplate) {
        this.authService = authService;
        this.reportMapper = reportMapper;
        this.orderMapper = orderMapper;
        this.ackMapper = ackMapper;
        this.reviewTaskMapper = reviewTaskMapper;
        this.orderStatusLogMapper = orderStatusLogMapper;
        this.operationLogMapper = operationLogMapper;
        this.bindingMapper = bindingMapper;
        this.elderProfileMapper = elderProfileMapper;
        this.objectMapper = objectMapper;
        this.lockService = lockService;
        this.homeCacheInvalidator = homeCacheInvalidator;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public ReportAckResponse elderAck(String authorization, String reportId, ReportAckRequest request) {
        AuthService.CurrentUser currentUser = requireRole(authorization, RoleCode.ELDER);
        ReportContext context = requireContext(reportId);
        requireElderSelf(currentUser, context.order());
        return withReportLock(reportId, () -> {
            ReportContext lockedContext = requireContext(reportId);
            requireElderSelf(currentUser, lockedContext.order());
            return acknowledge(currentUser, RoleCode.ELDER.name(), lockedContext, request);
        });
    }

    public List<PendingReportResponse> pendingReports(String authorization, RoleCode role) {
        return reports(authorization, role, true);
    }

    public List<PendingReportResponse> reports(String authorization, RoleCode role, boolean pendingOnly) {
        AuthService.CurrentUser currentUser = requireRole(authorization, role);
        var query = Wrappers.<ServiceReport>query();
        if (pendingOnly) {
            query.eq("report_status", "WAIT_CONFIRM");
        }
        return reportMapper.selectList(query.orderByDesc("generated_at"))
                .stream()
                .map(report -> {
                    NursingOrder order = orderMapper.selectById(report.getOrderId());
                    if (order == null) return null;
                    try {
                        if (role == RoleCode.ELDER) requireElderSelf(currentUser, order);
                        else requireFamilyReportConfirm(currentUser, order);
                    } catch (ForbiddenException ignored) {
                        return null;
                    }
                    ElderProfile elder = elderProfileMapper.selectById(order.getElderId());
                    return new PendingReportResponse(report.getReportId(), order.getOrderId(), order.getElderId(),
                            elder == null ? "长辈" : elder.getElderName());
                })
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    @Transactional
    public ReportAckResponse familyAck(String authorization, String reportId, ReportAckRequest request) {
        AuthService.CurrentUser currentUser = requireRole(authorization, RoleCode.FAMILY);
        ReportContext context = requireContext(reportId);
        requireFamilyReportConfirm(currentUser, context.order());
        return withReportLock(reportId, () -> {
            ReportContext lockedContext = requireContext(reportId);
            requireFamilyReportConfirm(currentUser, lockedContext.order());
            return acknowledge(currentUser, RoleCode.FAMILY.name(), lockedContext, request);
        });
    }

    @Transactional
    public ReportAckResponse decideArchiveSuggestions(
            String authorization,
            String reportId,
            ReportAckRequest request) {
        AuthService.CurrentUser currentUser = requireRole(authorization, RoleCode.FAMILY);
        ReportContext context = requireContext(reportId);
        requireFamilyScopes(currentUser, context.order(), REPORT_CONFIRM, ARCHIVE_EDIT);
        return withReportLock(reportId, () -> decideArchiveSuggestionsLocked(currentUser, reportId, request));
    }

    private ReportAckResponse decideArchiveSuggestionsLocked(
            AuthService.CurrentUser currentUser,
            String reportId,
            ReportAckRequest request) {
        ReportContext context = requireContext(reportId);
        requireFamilyScopes(currentUser, context.order(), REPORT_CONFIRM, ARCHIVE_EDIT);
        List<HealthInfoReviewTask> tasks = reviewTaskMapper.selectList(
                Wrappers.<HealthInfoReviewTask>query().eq("report_id", reportId));
        Set<String> acceptedIds = new HashSet<>(request.acceptedSuggestionIds());
        Set<String> existingIds = tasks.stream()
                .map(HealthInfoReviewTask::getReviewTaskId)
                .collect(java.util.stream.Collectors.toSet());
        if (!existingIds.containsAll(acceptedIds)) {
            throw new NotFoundException();
        }

        ReportAckResponse response = acknowledge(currentUser, RoleCode.FAMILY.name(), context, request);
        LocalDateTime now = LocalDateTime.now();
        for (HealthInfoReviewTask task : tasks) {
            boolean accepted = ACCEPTED.equals(request.ackResult()) && acceptedIds.contains(task.getReviewTaskId());
            task.setReviewStatus(accepted ? "APPROVED" : "REJECTED");
            task.setReviewerId(currentUser.userId());
            task.setReviewedAt(now);
            task.setReviewRemark(request.remark());
            reviewTaskMapper.updateById(task);
        }
        saveOperationLog(currentUser, "DECIDE_ARCHIVE_SUGGESTIONS", reportId, request);
        return response;
    }

    private ReportAckResponse acknowledge(
            AuthService.CurrentUser currentUser,
            String ackRole,
            ReportContext context,
            ReportAckRequest request) {
        if (!WAIT_CONFIRM.equals(context.report().getReportStatus())
                || !WAIT_CONFIRM.equals(context.order().getOrderStatus())) {
            throw new ApiException(409, "state conflict");
        }
        validateAckResult(request.ackResult());
        String reportStatus = ACCEPTED.equals(request.ackResult()) ? "CONFIRMED" : "REJECTED";
        String orderStatus = ACCEPTED.equals(request.ackResult()) ? "COMPLETED" : "WAIT_REPORT";

        CareReportAck ack = ackMapper.selectOne(
                Wrappers.<CareReportAck>query().eq("report_id", context.report().getReportId()));
        boolean creating = ack == null;
        if (creating) {
            ack = new CareReportAck();
            ack.setAckId(nextId("ack"));
            ack.setReportId(context.report().getReportId());
            ack.setOrderId(context.order().getOrderId());
        }
        ack.setAckUserId(currentUser.userId());
        ack.setAckRole(ackRole);
        ack.setAckResult(request.ackResult());
        ack.setSatisfaction(request.satisfaction());
        ack.setRemark(request.remark());
        ack.setAcceptedSuggestionIds(writeJson(request.acceptedSuggestionIds()));
        if (creating) {
            try {
                ackMapper.insert(ack);
            } catch (DuplicateKeyException duplicate) {
                throw new ApiException(409, "state conflict");
            }
        } else {
            ackMapper.updateById(ack);
        }

        String previousOrderStatus = context.order().getOrderStatus();
        int reportUpdated = reportMapper.update(null, Wrappers.<ServiceReport>update()
                .eq("report_id", context.report().getReportId())
                .eq("report_status", WAIT_CONFIRM)
                .set("report_status", reportStatus)
                .set("confirmed_at", ACCEPTED.equals(request.ackResult()) ? LocalDateTime.now() : null));
        if (reportUpdated != 1) {
            throw new ApiException(409, "state conflict");
        }
        int orderUpdated = orderMapper.update(null, Wrappers.<NursingOrder>update()
                .eq("order_id", context.order().getOrderId())
                .eq("order_status", WAIT_CONFIRM)
                .set("order_status", orderStatus));
        if (orderUpdated != 1) {
            throw new ApiException(409, "state conflict");
        }
        saveOrderStatusLog(currentUser, context.order().getOrderId(), previousOrderStatus, orderStatus);
        saveOperationLog(currentUser, "ACK_SERVICE_REPORT", context.report().getReportId(), request);
        evictReportHomesAfterCommit(currentUser, ackRole, context.order());

        return new ReportAckResponse(ack.getAckId(), request.ackResult(), reportStatus);
    }

    private void evictReportHomesAfterCommit(
            AuthService.CurrentUser currentUser,
            String ackRole,
            NursingOrder order) {
        homeCacheInvalidator.evictAfterCommit(ackRole, currentUser.userId());
        homeCacheInvalidator.evictAfterCommit(RoleCode.FAMILY.name(), order.getFamilyId());
        ElderProfile elder = elderProfileMapper.selectById(order.getElderId());
        if (elder != null) {
            homeCacheInvalidator.evictAfterCommit(RoleCode.ELDER.name(), elder.getUserId());
        }
        List<String> nurseIds = jdbcTemplate.query(
                "SELECT nurse_id FROM nurse_task WHERE order_id = ?",
                (rs, rowNum) -> rs.getString("nurse_id"), order.getOrderId());
        for (String nurseId : nurseIds) {
            homeCacheInvalidator.evictAfterCommit("NURSE", nurseId);
        }
        List<Map<String, Object>> operators = jdbcTemplate.queryForList("""
                SELECT ur.user_id, r.role_code
                FROM user_role ur
                JOIN sys_role r ON r.role_id = ur.role_id
                WHERE r.role_code IN ('ADMIN', 'CUSTOMER_SERVICE')
                """);
        for (Map<String, Object> operator : operators) {
            homeCacheInvalidator.evictAfterCommit(
                    operator.get("role_code").toString(), operator.get("user_id").toString());
        }
    }

    private <T> T withReportLock(String reportId, Supplier<T> action) {
        try (RedisLockService.Acquisition lock = lockService.tryAcquire(
                RedisKeyFactory.reportLockKey(reportId), Duration.ofSeconds(20))) {
            if (lock.contended()) {
                throw new ApiException(409, "state conflict");
            }
            return action.get();
        }
    }

    private ReportContext requireContext(String reportId) {
        ServiceReport report = reportMapper.selectById(reportId);
        if (report == null) {
            throw new NotFoundException();
        }
        NursingOrder order = orderMapper.selectById(report.getOrderId());
        if (order == null) {
            throw new NotFoundException();
        }
        return new ReportContext(report, order);
    }

    private AuthService.CurrentUser requireRole(String authorization, RoleCode role) {
        AuthService.CurrentUser currentUser = authService.requireCurrentUser(authorization);
        if (!currentUser.roles().contains(role)) {
            throw new ForbiddenException();
        }
        return currentUser;
    }

    private void requireElderSelf(AuthService.CurrentUser currentUser, NursingOrder order) {
        ElderProfile elder = elderProfileMapper.selectById(order.getElderId());
        if (elder == null) {
            throw new NotFoundException();
        }
        if (!currentUser.userId().equals(elder.getUserId())) {
            throw new ForbiddenException();
        }
    }

    private void requireFamilyReportConfirm(AuthService.CurrentUser currentUser, NursingOrder order) {
        requireFamilyScopes(currentUser, order, REPORT_CONFIRM);
    }

    private void requireFamilyScopes(AuthService.CurrentUser currentUser, NursingOrder order, String... requiredScopes) {
        boolean authorized = bindingMapper.selectList(
                        Wrappers.<ElderFamilyBinding>query()
                                .eq("family_id", currentUser.userId())
                                .eq("elder_id", order.getElderId())
                                .eq("binding_status", "ACTIVE"))
                .stream()
                .anyMatch(binding -> readScopes(binding.getScopeCodes()).containsAll(List.of(requiredScopes)));
        if (!authorized) {
            throw new ForbiddenException();
        }
    }

    private void validateAckResult(String ackResult) {
        if (!ACCEPTED.equals(ackResult) && !REJECTED.equals(ackResult)) {
            throw new ApiException(422, "Unsupported acknowledgement result");
        }
    }

    private List<String> readScopes(String value) {
        try {
            return objectMapper.readValue(
                    value,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, String.class));
        } catch (JsonProcessingException exception) {
            throw new ApiException(500, "Invalid binding scope data");
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new ApiException(500, "Failed to serialize report acknowledgement");
        }
    }

    private void saveOrderStatusLog(
            AuthService.CurrentUser currentUser,
            String orderId,
            String fromStatus,
            String toStatus) {
        OrderStatusLog log = new OrderStatusLog();
        log.setStatusLogId(nextId("statuslog"));
        log.setOrderId(orderId);
        log.setFromStatus(fromStatus);
        log.setToStatus(toStatus);
        log.setChangedBy(currentUser.userId());
        log.setChangeReason("REPORT_ACK");
        orderStatusLogMapper.insert(log);
    }

    private void saveOperationLog(
            AuthService.CurrentUser currentUser,
            String operationType,
            String reportId,
            Object afterValue) {
        OperationLog log = new OperationLog();
        log.setLogId(nextId("op"));
        log.setOperatorId(currentUser.userId());
        log.setRoleCode(currentUser.roles().get(0).name());
        log.setOperationType(operationType);
        log.setBizType("SERVICE_REPORT");
        log.setBizId(reportId);
        log.setAfterValue(writeJson(afterValue));
        log.setTraceId(nextId("reportack"));
        operationLogMapper.insert(log);
    }

    private String nextId(String prefix) {
        return prefix + "_" + UUID.randomUUID().toString().replace("-", "").substring(0, 20);
    }

    private record ReportContext(ServiceReport report, NursingOrder order) {
    }
}
