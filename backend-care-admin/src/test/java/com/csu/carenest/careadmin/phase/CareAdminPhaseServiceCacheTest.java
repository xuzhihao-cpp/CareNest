package com.csu.carenest.careadmin.phase;

import com.csu.carenest.careadmin.auth.CurrentUser;
import com.csu.carenest.careadmin.auth.RoleCode;
import com.csu.carenest.careadmin.phase.dto.ServiceItemRequest;
import com.csu.carenest.careadmin.phase.dto.ServiceItemResponse;
import com.csu.carenest.careadmin.redis.RedisCacheService;
import com.csu.carenest.careadmin.redis.RedisKeyFactory;
import com.csu.carenest.careadmin.redis.RedisLockService;
import com.csu.carenest.careadmin.redis.HomeCacheInvalidator;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CareAdminPhaseServiceCacheTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Mock
    private RedisCacheService cacheService;

    @Mock
    private RedisLockService lockService;

    @Mock
    private HomeCacheInvalidator homeCacheInvalidator;

    @Test
    void publicOnShelfListUsesFiveMinuteCacheHit() {
        ServiceItemResponse cached = new ServiceItemResponse("service_001", "上门护理", "照护", 10000, 60, "ON_SHELF");
        when(cacheService.get(RedisKeyFactory.onShelfServiceItemsKey(), ServiceItemResponse[].class))
                .thenReturn(Optional.of(new ServiceItemResponse[]{cached}));
        CareAdminPhaseService service = new CareAdminPhaseService(jdbcTemplate, new ObjectMapper(), cacheService, lockService, homeCacheInvalidator);

        assertEquals(List.of(cached), service.serviceItems(false));
        verify(jdbcTemplate, never()).query(anyString(), any(RowMapper.class));
    }

    @Test
    void publicOnShelfListCachesMysqlResultButAdminListDoesNot() {
        ServiceItemResponse item = new ServiceItemResponse("service_001", "上门护理", "照护", 10000, 60, "ON_SHELF");
        when(cacheService.get(RedisKeyFactory.onShelfServiceItemsKey(), ServiceItemResponse[].class)).thenReturn(Optional.empty());
        when(jdbcTemplate.query(anyString(), any(RowMapper.class))).thenReturn(List.of(item));
        CareAdminPhaseService service = new CareAdminPhaseService(jdbcTemplate, new ObjectMapper(), cacheService, lockService, homeCacheInvalidator);

        assertEquals(List.of(item), service.serviceItems(false));
        assertEquals(List.of(item), service.serviceItems(true));

        ArgumentCaptor<ServiceItemResponse[]> cached = ArgumentCaptor.forClass(ServiceItemResponse[].class);
        verify(cacheService).put(eq(RedisKeyFactory.onShelfServiceItemsKey()), cached.capture(), eq(Duration.ofMinutes(5)));
        assertEquals("service_001", cached.getValue()[0].serviceId());
    }

    @Test
    void serviceItemWriteInvalidatesPublicCacheAfterSuccessfulDatabaseMutation() {
        when(jdbcTemplate.queryForMap(anyString(), any(Object[].class))).thenReturn(Map.of(
                "service_id", "service_001",
                "service_name", "上门护理",
                "service_desc", "照护",
                "price_cent", 10000,
                "duration_minutes", 60,
                "service_status", "ON_SHELF"
        ));
        CareAdminPhaseService service = new CareAdminPhaseService(jdbcTemplate, new ObjectMapper(), cacheService, lockService, homeCacheInvalidator);

        service.createServiceItem(
                new CurrentUser("admin_001", List.of(RoleCode.ADMIN)),
                new ServiceItemRequest("上门护理", "照护", 10000, 60, "ON_SHELF"));

        verify(cacheService).evict(RedisKeyFactory.onShelfServiceItemsKey());
    }

    @Test
    void dispatchUsesConditionalMysqlUpdateWhenRedisIsUnavailable() {
        when(lockService.tryAcquire(RedisKeyFactory.orderLockKey("order_001"), Duration.ofSeconds(20)))
                .thenReturn(new RedisLockService.Acquisition(RedisLockService.State.UNAVAILABLE, null, null, null));
        when(jdbcTemplate.queryForMap(anyString(), any(Object[].class))).thenReturn(Map.of(
                "order_id", "order_001",
                "order_status", "WAIT_DISPATCH"
        ));
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), any(Object[].class))).thenReturn(1);
        when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(1);
        when(jdbcTemplate.queryForList(anyString())).thenReturn(List.of());
        CareAdminPhaseService service = new CareAdminPhaseService(
                jdbcTemplate, new ObjectMapper(), cacheService, lockService, homeCacheInvalidator);

        service.dispatchOrder(
                new CurrentUser("admin_001", List.of(RoleCode.ADMIN)),
                "order_001",
                new com.csu.carenest.careadmin.phase.dto.DispatchRequest("nurse_001", "dispatch", "DISPATCHED"));

        verify(jdbcTemplate).update(
                contains("WHERE order_id = ? AND order_status = ?"),
                eq("DISPATCHED"), eq("order_001"), eq("WAIT_DISPATCH"));
    }

    @Test
    void dispatchReturnsConflictWhenOrderLockIsAlreadyHeld() {
        when(lockService.tryAcquire(RedisKeyFactory.orderLockKey("order_001"), Duration.ofSeconds(20)))
                .thenReturn(new RedisLockService.Acquisition(RedisLockService.State.CONTENDED, null, null, null));
        CareAdminPhaseService service = new CareAdminPhaseService(jdbcTemplate, new ObjectMapper(), cacheService, lockService, homeCacheInvalidator);

        assertThrows(com.csu.carenest.careadmin.common.ConflictException.class, () -> service.dispatchOrder(
                new CurrentUser("admin_001", List.of(RoleCode.ADMIN)),
                "order_001",
                new com.csu.carenest.careadmin.phase.dto.DispatchRequest("nurse_001", "安排服务", "DISPATCHED")));
    }
}
