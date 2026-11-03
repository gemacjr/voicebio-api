package com.example.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

class MdcTaskDecoratorTest {

    private final MdcTaskDecorator decorator = new MdcTaskDecorator();
    private final ExecutorService otherThread = Executors.newSingleThreadExecutor();

    @AfterEach
    void tearDown() {
        MDC.clear();
        otherThread.shutdownNow();
    }

    @Test
    void propagatesCallerMdcToTaskThread() throws Exception {
        MDC.put("requestId", "abc");
        AtomicReference<String> seen = new AtomicReference<>();
        Runnable task = decorator.decorate(() -> seen.set(MDC.get("requestId")));

        CompletableFuture.runAsync(task, otherThread).get();

        assertThat(seen.get()).isEqualTo("abc");
    }

    @Test
    void restoresWorkerThreadMdcAfterTask() throws Exception {
        CompletableFuture.runAsync(() -> MDC.setContextMap(Map.of("requestId", "worker-own")), otherThread).get();
        MDC.put("requestId", "caller");

        CompletableFuture.runAsync(decorator.decorate(() -> {}), otherThread).get();

        String afterTask = CompletableFuture.supplyAsync(() -> MDC.get("requestId"), otherThread).get();
        assertThat(afterTask).isEqualTo("worker-own");
    }

    @Test
    void clearsLeftoverMdcWhenCallerHasNone() throws Exception {
        CompletableFuture.runAsync(() -> MDC.put("requestId", "stale"), otherThread).get();
        AtomicReference<String> seen = new AtomicReference<>("not-run");

        CompletableFuture.runAsync(decorator.decorate(() -> seen.set(MDC.get("requestId"))), otherThread).get();

        assertThat(seen.get()).isNull();
    }
}
