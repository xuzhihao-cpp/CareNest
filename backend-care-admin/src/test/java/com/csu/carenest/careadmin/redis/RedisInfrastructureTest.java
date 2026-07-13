package com.csu.carenest.careadmin.redis;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RedisInfrastructureTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Test
    void cacheReadsJsonAndTreatsRedisFailuresAsMisses() {
        RedisCacheService cache = new RedisCacheService(redisTemplate, new ObjectMapper());
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("carenest:test:v1")).thenReturn("{\"value\":\"cached\"}");

        assertEquals("cached", cache.get("carenest:test:v1", Payload.class).orElseThrow().value());

        when(valueOperations.get("carenest:test:v1")).thenThrow(new DataAccessResourceFailureException("redis down"));
        assertTrue(cache.get("carenest:test:v1", Payload.class).isEmpty());
    }

    @Test
    void cacheWriteFailureIsIgnored() {
        RedisCacheService cache = new RedisCacheService(redisTemplate, new ObjectMapper());
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        org.mockito.Mockito.doThrow(new DataAccessResourceFailureException("redis down"))
                .when(valueOperations).set(eq("carenest:test:v1"), any(), eq(Duration.ofSeconds(30)));

        cache.put("carenest:test:v1", new Payload("cached"), Duration.ofSeconds(30));
    }

    @Test
    void userKeysUseStableHashInsteadOfRawUserId() {
        String first = RedisKeyFactory.userHash("user_001");

        assertEquals(first, RedisKeyFactory.userHash("user_001"));
        assertFalse(first.contains("user_001"));
        assertEquals("carenest:home:ELDER:" + first + ":v1", RedisKeyFactory.homeKey("ELDER", "user_001"));
    }

    @Test
    void lockReleaseUsesOwnershipTokenCompareAndDeleteScript() {
        RedisLockService lockService = new RedisLockService(redisTemplate);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(eq("carenest:lock:order:order_001"), any(), eq(Duration.ofSeconds(20))))
                .thenReturn(true);

        RedisLockService.Acquisition acquisition = lockService.tryAcquire("carenest:lock:order:order_001", Duration.ofSeconds(20));

        assertTrue(acquisition.acquired());
        acquisition.close();

        ArgumentCaptor<DefaultRedisScript<Long>> script = ArgumentCaptor.forClass(DefaultRedisScript.class);
        verify(redisTemplate).execute(script.capture(), eq(List.of("carenest:lock:order:order_001")), eq(acquisition.token()));
        assertTrue(script.getValue().getScriptAsString().contains("redis.call('get'"));
    }

    @Test
    void lockIsReleasedOnlyAfterActiveTransactionCompletes() {
        RedisLockService lockService = new RedisLockService(redisTemplate);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(eq("carenest:lock:order:order_001"), any(), eq(Duration.ofSeconds(20))))
                .thenReturn(true);
        TransactionSynchronizationManager.initSynchronization();
        try {
            RedisLockService.Acquisition acquisition = lockService.tryAcquire(
                    "carenest:lock:order:order_001", Duration.ofSeconds(20));

            acquisition.close();

            verify(redisTemplate, never()).execute(any(DefaultRedisScript.class), any(List.class), any());
            TransactionSynchronizationManager.getSynchronizations().forEach(
                    synchronization -> synchronization.afterCompletion(TransactionSynchronization.STATUS_COMMITTED));
            verify(redisTemplate).execute(any(DefaultRedisScript.class),
                    eq(List.of("carenest:lock:order:order_001")), eq(acquisition.token()));
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void lockReportsRedisUnavailableInsteadOfContention() {
        RedisLockService lockService = new RedisLockService(redisTemplate);
        when(redisTemplate.opsForValue()).thenThrow(new DataAccessResourceFailureException("redis down"));

        RedisLockService.Acquisition acquisition = lockService.tryAcquire("carenest:lock:order:order_001", Duration.ofSeconds(20));

        assertTrue(acquisition.unavailable());
        assertFalse(acquisition.contended());
    }

    @Test
    void homeInvalidationUsesOnlyHashedUserKeyWhenNoTransactionIsActive() {
        RedisCacheService cache = org.mockito.Mockito.mock(RedisCacheService.class);
        HomeCacheInvalidator invalidator = new HomeCacheInvalidator(cache);

        invalidator.evictAfterCommit("ELDER", "user_001");

        verify(cache).evict(RedisKeyFactory.homeKey("ELDER", "user_001"));
    }

    @Test
    void homeInvalidationsAreBatchedAfterTransactionCommit() {
        RedisCacheService cache = org.mockito.Mockito.mock(RedisCacheService.class);
        HomeCacheInvalidator invalidator = new HomeCacheInvalidator(cache);
        TransactionSynchronizationManager.initSynchronization();
        try {
            invalidator.evictAfterCommit("NURSE", "user_001");
            invalidator.evictAfterCommit("ADMIN", "user_002");
            invalidator.evictAfterCommit("NURSE", "user_001");

            assertEquals(1, TransactionSynchronizationManager.getSynchronizations().size());
            TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
            TransactionSynchronizationManager.getSynchronizations().forEach(
                    synchronization -> synchronization.afterCompletion(TransactionSynchronization.STATUS_COMMITTED));
            verify(cache).evict(Set.of(
                    RedisKeyFactory.homeKey("NURSE", "user_001"),
                    RedisKeyFactory.homeKey("ADMIN", "user_002")));
        } finally {
            TransactionSynchronizationManager.unbindResourceIfPossible(invalidator);
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    private record Payload(String value) {
    }
}
