package com.xianyusmart.service.impl;

import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

/** Serializes every Cookie, H5 token and WebSocket token update for one account. */
@Component
public class CredentialUpdateCoordinator {

    private final ConcurrentHashMap<Long, ReentrantLock> accountLocks = new ConcurrentHashMap<>();

    public <T> T withAccountLock(Long accountId, Supplier<T> action) {
        if (accountId == null) {
            return action.get();
        }
        ReentrantLock lock = accountLocks.computeIfAbsent(accountId, ignored -> new ReentrantLock());
        lock.lock();
        try {
            return action.get();
        } finally {
            lock.unlock();
        }
    }

    public void withAccountLock(Long accountId, Runnable action) {
        withAccountLock(accountId, () -> {
            action.run();
            return null;
        });
    }
}