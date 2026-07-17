package com.csu.carenest.careadmin.delivery;

import com.csu.carenest.careadmin.auth.CurrentUser;
import com.csu.carenest.careadmin.auth.RoleCode;
import com.csu.carenest.careadmin.common.BusinessRuleException;
import com.csu.carenest.careadmin.common.ForbiddenException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.csu.carenest.careadmin.redis.RedisCacheService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 验证阶段51-55随访提醒事务、统计日期和演示数据重置入口。 */
class Phase51To55DeliveryServiceTest {

    private static final CurrentUser ADMIN = new CurrentUser("admin_1", List.of(RoleCode.ADMIN));
    private static final CurrentUser FAMILY = new CurrentUser("family_1", List.of(RoleCode.FAMILY));

    @Test
    void reminderIsCreatedInsideFollowUpWorkflow() {
        Phase51To55DeliveryRepository repository = mock(Phase51To55DeliveryRepository.class);
        DemoDataSeedExecutor seedExecutor = mock(DemoDataSeedExecutor.class);
        Phase51To55DeliveryService service = new Phase51To55DeliveryService(
                repository, seedExecutor, new ObjectMapper(), mock(RedisCacheService.class));
        when(repository.hasPermission("admin_1", "FOLLOW_UP_MANAGE")).thenReturn(true);
        when(repository.elderExists("elder_1")).thenReturn(true);
        when(repository.orderBelongsToElder("order_1", "elder_1")).thenReturn(true);
        DeliveryDtos.FollowUpRequest request = new DeliveryDtos.FollowUpRequest(
                "elder_1", "order_1", "phone", "康复情况稳定",
                LocalDateTime.now().plusDays(1), true);

        DeliveryDtos.FollowUpResponse response = service.createFollowUp(ADMIN, request);

        assertNotNull(response.createdReminderTaskId());
        verify(repository).insertFollowUp(anyString(), any(DeliveryDtos.FollowUpRequest.class), anyString());
        verify(repository).insertReminder(
                anyString(), anyString(), any(DeliveryDtos.FollowUpRequest.class), anyString());
    }

    @Test
    void statisticsRejectInvalidOrExcessiveDateRange() {
        Phase51To55DeliveryRepository repository = mock(Phase51To55DeliveryRepository.class);
        Phase51To55DeliveryService service = new Phase51To55DeliveryService(
                repository, mock(DemoDataSeedExecutor.class), new ObjectMapper(),
                mock(RedisCacheService.class));
        when(repository.hasPermission("admin_1", "DASHBOARD_BASIC_VIEW")).thenReturn(true);

        assertThrows(BusinessRuleException.class, () -> service.basicStatistics(
                ADMIN, LocalDate.of(2025, 1, 1), LocalDate.of(2026, 2, 1)));
        assertThrows(BusinessRuleException.class, () -> service.basicStatistics(
                ADMIN, LocalDate.of(2026, 2, 1), LocalDate.of(2026, 1, 1)));
    }

    @Test
    void resetUsesMemberOneSeedExecutorThenReturnsReadiness() {
        Phase51To55DeliveryRepository repository = mock(Phase51To55DeliveryRepository.class);
        DemoDataSeedExecutor seedExecutor = mock(DemoDataSeedExecutor.class);
        Phase51To55DeliveryService service = new Phase51To55DeliveryService(
                repository, seedExecutor, new ObjectMapper(), mock(RedisCacheService.class));
        when(repository.hasPermission("admin_1", "DEMO_DATA_MANAGE")).thenReturn(true);
        when(repository.demoStatus()).thenReturn(new DeliveryDtos.DemoDataStatusResponse(
                true, List.of("admin_demo", "cs_demo", "elder_demo", "family_demo", "nurse_demo"),
                8, null));

        service.resetDemoData(ADMIN);

        verify(seedExecutor).reset();
        verify(repository).insertOperationLog(
                anyString(), anyString(), anyString(), anyString(), anyString(), anyString(),
                any(), anyString(), anyString());
        verify(repository).demoStatus();
    }

    @Test
    void familyCanOnlyReadFollowUpsWithActiveHealthViewBinding() {
        Phase51To55DeliveryRepository repository = mock(Phase51To55DeliveryRepository.class);
        Phase51To55DeliveryService service = new Phase51To55DeliveryService(
                repository, mock(DemoDataSeedExecutor.class), new ObjectMapper(),
                mock(RedisCacheService.class));
        when(repository.findActiveBindingScopes("family_1", "elder_1"))
                .thenReturn(Optional.of("[\"HEALTH_VIEW\",\"REPORT_VIEW\"]"));
        when(repository.findFollowUps("elder_1")).thenReturn(List.of(
                new DeliveryDtos.FollowUpRecordResponse(
                        "follow_1", "elder_1", null, "PHONE", "状态稳定",
                        null, false, null, LocalDateTime.now())));

        assertEquals(1, service.familyFollowUps(FAMILY, "elder_1").size());
        when(repository.findActiveBindingScopes("family_1", "elder_2"))
                .thenReturn(Optional.of("[\"REPORT_VIEW\"]"));
        assertThrows(ForbiddenException.class,
                () -> service.familyFollowUps(FAMILY, "elder_2"));
        assertThrows(ForbiddenException.class,
                () -> service.familyFollowUps(FAMILY, "elder_3"));
    }
}
