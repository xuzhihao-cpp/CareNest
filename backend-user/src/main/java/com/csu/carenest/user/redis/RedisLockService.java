package com.csu.carenest.user.redis;

import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

@Service
public class RedisLockService {

    private static final DefaultRedisScript<Long> COMPARE_AND_DELETE = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end",
            Long.class);

    private final StringRedisTemplate redisTemplate;

    public RedisLockService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public Acquisition tryAcquire(String key, Duration ttl) {
        String token = UUID.randomUUID().toString();
        try {
            Boolean acquired = redisTemplate.opsForValue().setIfAbsent(key, token, ttl);
            return Boolean.TRUE.equals(acquired) ? Acquisition.acquired(key, token, this) : Acquisition.contendedResult();
        } catch (DataAccessException ignored) {
            return Acquisition.unavailableResult();
        }
    }

    private void release(String key, String token) {
        try {
            redisTemplate.execute(COMPARE_AND_DELETE, List.of(key), token);
        } catch (DataAccessException ignored) {
            // Expiry and ownership checks prevent an unavailable cache from changing MySQL truth.
        }
    }

    public record Acquisition(State state, String key, String token, RedisLockService owner) implements AutoCloseable {

        public boolean acquired() {
            return state == State.ACQUIRED;
        }

        public boolean contended() {
            return state == State.CONTENDED;
        }

        public boolean unavailable() {
            return state == State.UNAVAILABLE;
        }

        @Override
        public void close() {
            if (!acquired()) {
                return;
            }
            if (TransactionSynchronizationManager.isSynchronizationActive()) {
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCompletion(int status) {
                        owner.release(key, token);
                    }
                });
            } else {
                owner.release(key, token);
            }
        }

        private static Acquisition acquired(String key, String token, RedisLockService owner) {
            return new Acquisition(State.ACQUIRED, key, token, owner);
        }

        private static Acquisition contendedResult() {
            return new Acquisition(State.CONTENDED, null, null, null);
        }

        private static Acquisition unavailableResult() {
            return new Acquisition(State.UNAVAILABLE, null, null, null);
        }
    }

    public enum State {
        ACQUIRED,
        CONTENDED,
        UNAVAILABLE
    }
}
