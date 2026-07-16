package com.csu.carenest.careadmin.delivery;

import com.csu.carenest.careadmin.auth.CurrentUser;
import com.csu.carenest.careadmin.auth.RoleCode;
import com.csu.carenest.careadmin.common.BusinessRuleException;
import com.csu.carenest.careadmin.common.ForbiddenException;
import com.csu.carenest.careadmin.common.NotFoundException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** 阶段51-55随访、统计、演示重置和就绪检查服务。 */
@Service
public class Phase51To55DeliveryService {

    private static final String FOLLOW_UP_PERMISSION = "FOLLOW_UP_MANAGE";
    private static final String BASIC_PERMISSION = "DASHBOARD_BASIC_VIEW";
    private static final String QUALITY_PERMISSION = "DASHBOARD_QUALITY_VIEW";
    private static final String DEMO_PERMISSION = "DEMO_DATA_MANAGE";
    private static final Set<String> FOLLOW_UP_TYPES =
            Set.of("PHONE", "ONLINE", "HOME", "AI", "CUSTOMER_SERVICE");

    private final Phase51To55DeliveryRepository repository;
    private final DemoDataSeedExecutor seedExecutor;
    private final ObjectMapper objectMapper;

    public Phase51To55DeliveryService(
            Phase51To55DeliveryRepository repository,
            DemoDataSeedExecutor seedExecutor,
            ObjectMapper objectMapper) {
        this.repository = repository;
        this.seedExecutor = seedExecutor;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public DeliveryDtos.FollowUpResponse createFollowUp(
            CurrentUser user, DeliveryDtos.FollowUpRequest source) {
        requirePermission(user, FOLLOW_UP_PERMISSION);
        if (!repository.elderExists(source.elderId())) {
            throw new NotFoundException();
        }
        String orderId = source.orderId() == null || source.orderId().isBlank()
                ? null : source.orderId().trim();
        if (orderId != null && !repository.orderBelongsToElder(orderId, source.elderId())) {
            throw new BusinessRuleException();
        }
        String type = source.followUpType().trim().toUpperCase(java.util.Locale.ROOT);
        if (!FOLLOW_UP_TYPES.contains(type)) {
            throw new BusinessRuleException();
        }
        if (source.needReminder() && source.nextFollowUpAt() == null) {
            throw new BusinessRuleException();
        }
        DeliveryDtos.FollowUpRequest request = new DeliveryDtos.FollowUpRequest(
                source.elderId().trim(), orderId, type, source.content().trim(),
                source.nextFollowUpAt(), source.needReminder());
        String followUpId = nextId("followup");
        repository.insertFollowUp(followUpId, request, user.userId());
        String reminderId = null;
        if (request.needReminder()) {
            // 只在成员3的管理端创建随访事务内生成提醒，不实现成员1的独立提醒接口。
            reminderId = nextId("reminder");
            repository.insertReminder(reminderId, followUpId, request, user.userId());
        }
        log(user, "CREATE_FOLLOW_UP", "FOLLOW_UP", followUpId,
                Map.of("needReminder", request.needReminder()));
        return new DeliveryDtos.FollowUpResponse(followUpId, reminderId);
    }

    @Transactional(readOnly = true)
    public DeliveryDtos.BasicStatisticsResponse basicStatistics(
            CurrentUser user, LocalDate dateFrom, LocalDate dateTo) {
        requirePermission(user, BASIC_PERMISSION);
        DateRange range = requireRange(dateFrom, dateTo);
        return repository.basicStatistics(range.from(), range.toExclusive());
    }

    @Transactional(readOnly = true)
    public DeliveryDtos.QualityStatisticsResponse qualityStatistics(
            CurrentUser user, LocalDate dateFrom, LocalDate dateTo) {
        requirePermission(user, QUALITY_PERMISSION);
        DateRange range = requireRange(dateFrom, dateTo);
        return repository.qualityStatistics(range.from(), range.toExclusive());
    }

    public DeliveryDtos.DemoDataStatusResponse resetDemoData(CurrentUser user) {
        requirePermission(user, DEMO_PERMISSION);
        seedExecutor.reset();
        log(user, "RESET_DEMO_DATA", "DEMO_DATA", "phase-54", Map.of("reset", true));
        return repository.demoStatus();
    }

    @Transactional(readOnly = true)
    public DeliveryDtos.DemoDataStatusResponse demoDataStatus(CurrentUser user) {
        requirePermission(user, DEMO_PERMISSION);
        return repository.demoStatus();
    }

    private DateRange requireRange(LocalDate from, LocalDate to) {
        if (from == null || to == null || to.isBefore(from)
                || ChronoUnit.DAYS.between(from, to) > 366) {
            throw new BusinessRuleException();
        }
        return new DateRange(from.atStartOfDay(), to.plusDays(1).atStartOfDay());
    }

    private void requirePermission(CurrentUser user, String permission) {
        boolean role = user.hasRole(RoleCode.ADMIN) || user.hasRole(RoleCode.CUSTOMER_SERVICE);
        if (!role || !repository.hasPermission(user.userId(), permission)) {
            throw new ForbiddenException();
        }
    }

    private void log(
            CurrentUser user, String operation, String bizType, String bizId, Object after) {
        repository.insertOperationLog(
                nextId("op"), user.userId(), user.primaryRole(), operation, bizType, bizId,
                null, json(after), nextId("trace"));
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to serialize delivery operation log", exception);
        }
    }

    private String nextId(String prefix) {
        String value = prefix + "_" + UUID.randomUUID().toString().replace("-", "");
        return value.substring(0, Math.min(32, value.length()));
    }

    private record DateRange(LocalDateTime from, LocalDateTime toExclusive) {
    }
}
