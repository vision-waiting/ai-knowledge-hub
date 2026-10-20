package com.example.knowledge.rag;

import com.example.knowledge.common.BusinessException;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ChatModelRouterTest {
    private final AiService local = mock(AiService.class);
    private final AiService cloud = mock(AiService.class);
    private final AiRequest request = new AiRequest("问题", List.of(), true);

    @org.junit.jupiter.api.BeforeEach void realModel() { when(local.mode()).thenReturn("OPENAI_COMPATIBLE"); clearInvocations(local); }

    @Test void demoNeverClaimsAnActualModelEvenWithStaleLocalSettings() {
        when(local.mode()).thenReturn("LOCAL_DEMO");
        var router = new ChatModelRouter(local, "unused-model", true, null, "");
        assertEquals("default", router.defaultProvider());
        assertNull(router.select(null).model());
        assertEquals("LOCAL_DEMO", router.select(null).mode());
        assertEquals("", router.options().getFirst().model());
    }

    @Test void defaultsToLocalAndCloudRequiresExplicitSelection() {
        var router = new ChatModelRouter(local, "local-model", true, cloud, "cloud-model");
        assertEquals("local", router.select(null).provider());
        assertSame(local, router.select(null).service());
        assertSame(cloud, router.select("cloud").service());
        assertThrows(BusinessException.class, () -> router.select("https://other-host"));
        verify(local, never()).stream(any(), any(), any());
        verifyNoInteractions(cloud);
    }

    @Test void unavailableCloudNeverFallsBackToLocal() {
        var router = new ChatModelRouter(local, "local-model", true, null, "");
        assertFalse(router.options().get(1).configured());
        assertThrows(BusinessException.class, () -> router.select("cloud"));
        verify(local, never()).stream(any(), any(), any());
    }

    @Test void cloudFailureNeverCallsLocal() {
        var router = new ChatModelRouter(local, "local-model", true, cloud, "cloud-model");
        when(cloud.stream(eq(request), any(), any())).thenThrow(new IllegalStateException("offline"));
        assertThrows(IllegalStateException.class, () -> router.stream(router.select("cloud"), request, s -> {}, () -> false, s -> {}));
        verify(local, never()).stream(any(), any(), any());
    }

    @Test void cancelledLocalRequestDoesNotReachModel() {
        var router = new ChatModelRouter(local, "local-model", true, cloud, "cloud-model");
        assertThrows(CancellationException.class, () -> router.stream(router.select("local"), request, s -> {}, () -> true, s -> {}));
        verify(local, never()).stream(any(), any(), any());
        verifyNoInteractions(cloud);
    }

    @Test void localQueueCanBeCancelledWhileCloudRemainsIndependent() throws Exception {
        var router = new ChatModelRouter(local, "local-model", true, cloud, "cloud-model");
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var queued = new CountDownLatch(1);
        var cancelled = new AtomicBoolean(false);
        when(local.stream(eq(request), any(), any())).thenAnswer(call -> {
            entered.countDown(); assertTrue(release.await(5, TimeUnit.SECONDS));
            return new AiResponse("local", "OPENAI_COMPATIBLE");
        });
        when(cloud.stream(eq(request), any(), any())).thenReturn(new AiResponse("cloud", "OPENAI_COMPATIBLE"));
        try (var pool = Executors.newVirtualThreadPerTaskExecutor()) {
            var first = pool.submit(() -> router.stream(router.select("local"), request, s -> {}, () -> false, s -> {}));
            try {
                assertTrue(entered.await(2, TimeUnit.SECONDS));
                var waiting = pool.submit(() -> router.stream(router.select("local"), request, s -> {}, cancelled::get, s -> queued.countDown()));
                assertTrue(queued.await(2, TimeUnit.SECONDS));
                assertEquals("CLOUD_API", router.stream(router.select("cloud"), request, s -> {}, () -> false, s -> {}).mode());
                cancelled.set(true);
                var failure = assertThrows(java.util.concurrent.ExecutionException.class, () -> waiting.get(2, TimeUnit.SECONDS));
                assertInstanceOf(CancellationException.class, failure.getCause());
                verify(local, times(1)).stream(eq(request), any(), any());
            } finally { release.countDown(); }
            assertEquals("LOCAL_MODEL", first.get(2, TimeUnit.SECONDS).mode());
            // A cancelled waiter must not leak a permit or block later work.
            assertEquals("local", router.stream(router.select("local"), request, s -> {}, () -> false, s -> {}).answer());
        }
    }
}
