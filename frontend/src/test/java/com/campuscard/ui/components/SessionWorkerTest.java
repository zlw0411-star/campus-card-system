package com.campuscard.ui.components;

import com.campuscard.dto.SessionContext;
import com.campuscard.model.Role;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class SessionWorkerTest {
    private static final SessionContext SESSION = new SessionContext("token-a", 17, Role.USER, "Student");

    @Test @Timeout(8) void workReceivesTheCapturedSession() throws Exception {
        CountDownLatch finished = new CountDownLatch(1);
        AtomicReference<SessionContext> received = new AtomicReference<>();
        SessionWorker.execute(SESSION, () -> true, captured -> { received.set(captured); return "done"; },
                value -> finished.countDown(), error -> fail(error), finished::countDown);

        assertTrue(finished.await(5, TimeUnit.SECONDS));
        assertSame(SESSION, received.get());
    }

    @Test @Timeout(8) void staleTaskIsDiscardedBeforeItsServiceCall() throws Exception {
        AtomicBoolean called = new AtomicBoolean();
        AtomicBoolean callback = new AtomicBoolean();
        CountDownLatch finished = new CountDownLatch(1);
        SessionWorker.execute(SESSION, () -> false, captured -> { called.set(true); return "unexpected"; },
                value -> callback.set(true), error -> callback.set(true), finished::countDown);

        assertTrue(finished.await(5, TimeUnit.SECONDS));
        assertFalse(called.get());
        assertFalse(callback.get());
    }

    @Test @Timeout(8) void resultFromWorkThatBecameStaleIsDiscarded() throws Exception {
        AtomicBoolean current = new AtomicBoolean(true);
        AtomicBoolean callback = new AtomicBoolean();
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch finished = new CountDownLatch(1);
        SessionWorker.execute(SESSION, current::get, captured -> {
                    assertSame(SESSION, captured);
                    started.countDown();
                    assertTrue(release.await(5, TimeUnit.SECONDS));
                    return "old result";
                }, value -> callback.set(true), error -> callback.set(true), finished::countDown);

        assertTrue(started.await(5, TimeUnit.SECONDS));
        current.set(false);
        release.countDown();
        assertTrue(finished.await(5, TimeUnit.SECONDS));
        assertFalse(callback.get());
    }
}
