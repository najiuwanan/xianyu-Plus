package com.xianyusmart.service.impl;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CredentialUpdateCoordinatorTest {

    @Test
    void updatesForTheSameAccountAreSerialized() throws Exception {
        CredentialUpdateCoordinator coordinator = new CredentialUpdateCoordinator();
        AtomicInteger active = new AtomicInteger();
        AtomicInteger peak = new AtomicInteger();
        CountDownLatch firstEntered = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        CountDownLatch secondStarted = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);

        try {
            var first = executor.submit(() -> coordinator.withAccountLock(7L, () -> {
                int now = active.incrementAndGet();
                peak.accumulateAndGet(now, Math::max);
                firstEntered.countDown();
                try {
                    assertTrue(releaseFirst.await(2, TimeUnit.SECONDS));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new AssertionError(e);
                } finally {
                    active.decrementAndGet();
                }
            }));

            assertTrue(firstEntered.await(2, TimeUnit.SECONDS));

            var second = executor.submit(() -> {
                secondStarted.countDown();
                coordinator.withAccountLock(7L, () -> {
                    int now = active.incrementAndGet();
                    peak.accumulateAndGet(now, Math::max);
                    active.decrementAndGet();
                });
            });

            assertTrue(secondStarted.await(2, TimeUnit.SECONDS));
            assertEquals(1, active.get());
            releaseFirst.countDown();
            first.get(2, TimeUnit.SECONDS);
            second.get(2, TimeUnit.SECONDS);
            assertEquals(1, peak.get());
        } finally {
            releaseFirst.countDown();
            executor.shutdownNow();
        }
    }
}
