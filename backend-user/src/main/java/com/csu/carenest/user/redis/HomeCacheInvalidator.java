package com.csu.carenest.user.redis;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.LinkedHashSet;
import java.util.Set;

@Service
public class HomeCacheInvalidator {

    private final RedisCacheService cacheService;

    public HomeCacheInvalidator(RedisCacheService cacheService) {
        this.cacheService = cacheService;
    }

    public void evictAfterCommit(String role, String userId) {
        String key = RedisKeyFactory.homeKey(role, userId);
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            cacheService.evict(key);
            return;
        }
        @SuppressWarnings("unchecked")
        Set<String> pendingKeys = (Set<String>) TransactionSynchronizationManager.getResource(this);
        if (pendingKeys == null) {
            pendingKeys = new LinkedHashSet<>();
            TransactionSynchronizationManager.bindResource(this, pendingKeys);
            Set<String> transactionKeys = pendingKeys;
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    cacheService.evict(Set.copyOf(transactionKeys));
                }

                @Override
                public void afterCompletion(int status) {
                    TransactionSynchronizationManager.unbindResourceIfPossible(HomeCacheInvalidator.this);
                }
            });
        }
        pendingKeys.add(key);
    }
}
