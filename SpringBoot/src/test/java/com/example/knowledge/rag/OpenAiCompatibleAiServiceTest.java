package com.example.knowledge.rag;

import com.example.knowledge.common.BusinessException;
import com.example.knowledge.entity.DocumentChunk;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class OpenAiCompatibleAiServiceTest {
    @Test void billingFailureIsActionableWithoutExposingProviderBody() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/chat/completions", exchange -> {
            byte[] bytes = "{\"message\":\"secret-provider-details\"}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(402, bytes.length); exchange.getResponseBody().write(bytes); exchange.close();
        });
        server.start(); var ai = service(server);
        try {
            var streamError = assertThrows(BusinessException.class, () -> ai.stream(request(), ignored -> {}, () -> false));
            assertTrue(streamError.getMessage().contains("余额不足"));
            assertFalse(streamError.getMessage().contains("secret-provider-details"));
            assertTrue(assertThrows(BusinessException.class, () -> ai.generate(request())).getMessage().contains("余额不足"));
        } finally { ai.close(); server.stop(0); }
    }
    @Test void localInferenceOptionsReachBothCompletionAndStreamingRequests() throws Exception {
        var requests = new java.util.concurrent.CopyOnWriteArrayList<com.fasterxml.jackson.databind.JsonNode>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/chat/completions", exchange -> {
            var body = new ObjectMapper().readTree(exchange.getRequestBody()); requests.add(body);
            boolean streaming = body.path("stream").asBoolean();
            String response = streaming
                    ? "data: {\"choices\":[{\"delta\":{\"content\":\"answer\"}}]}\n\ndata: [DONE]\n\n"
                    : "{\"choices\":[{\"message\":{\"content\":\"answer\"}}]}";
            byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", streaming ? "text/event-stream" : "application/json");
            exchange.sendResponseHeaders(200, bytes.length); exchange.getResponseBody().write(bytes); exchange.close();
        });
        server.start();
        var ai = service(server);
        try {
            assertDoesNotThrow(() -> org.springframework.test.util.ReflectionTestUtils.setField(ai, "reasoningEffort", "none"));
            assertDoesNotThrow(() -> org.springframework.test.util.ReflectionTestUtils.setField(ai, "maxTokens", 768));
            ai.generate(request()); ai.stream(request(), ignored -> {}, () -> false);
            assertEquals(2, requests.size());
            for (var body : requests) {
                assertEquals("none", body.path("reasoning_effort").asText());
                assertEquals(768, body.path("max_tokens").asInt());
            }
        } finally { ai.close(); server.stop(0); }
    }
    @Test
    void providerMetadataResponseMustBeStructuredAndBounded() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/chat/completions", exchange -> {
            byte[] bytes = "{\"choices\":[{\"message\":{\"content\":\"{\\\"summary\\\":\\\"Generated summary\\\",\\\"tags\\\":[\\\"Redis\\\",\\\"Persistence\\\"]}\"}}]}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json"); exchange.sendResponseHeaders(200, bytes.length);
            try (var output = exchange.getResponseBody()) { output.write(bytes); }
        });
        server.start();
        try {
            DocumentMetadata metadata = service(server).describeDocument("Redis snapshot and append only persistence");
            assertEquals("Generated summary", metadata.summary());
            assertEquals(List.of("Redis", "Persistence"), metadata.tags());
            assertEquals("OPENAI_COMPATIBLE", metadata.mode());
        } finally { server.stop(0); }
    }
    @Test
    void cancellationClosesIdleProviderConnectionPromptly() throws Exception {
        CountDownLatch firstSent = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicBoolean cancelled = new AtomicBoolean();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/chat/completions", exchange -> {
            exchange.getResponseHeaders().add("Content-Type", "text/event-stream");
            exchange.sendResponseHeaders(200, 0);
            try (var output = exchange.getResponseBody()) {
                output.write("data: {\"choices\":[{\"delta\":{\"content\":\"first\"}}]}\n\n".getBytes(StandardCharsets.UTF_8));
                output.flush();
                try { release.await(4, TimeUnit.SECONDS); }
                catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            }
        });
        server.start();
        try {
            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> assertThrows(CancellationException.class,
                    () -> service(server).stream(request(), fragment -> firstSent.countDown(), cancelled::get)));
            assertTrue(firstSent.await(2, TimeUnit.SECONDS));
            cancelled.set(true);
            assertDoesNotThrow(() -> future.get(1500, TimeUnit.MILLISECONDS));
        } finally { release.countDown(); server.stop(0); }
    }

    @Test
    void deliversFragmentWhileProviderResponseIsStillOpen() throws Exception {
        CountDownLatch received = new CountDownLatch(1);
        AtomicBoolean incremental = new AtomicBoolean();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/chat/completions", exchange -> {
            exchange.getResponseHeaders().add("Content-Type", "text/event-stream");
            exchange.sendResponseHeaders(200, 0);
            try (var output = exchange.getResponseBody()) {
                output.write("data: {\"choices\":[{\"delta\":{\"content\":\"first\"}}]}\n\n".getBytes(StandardCharsets.UTF_8));
                output.flush();
                try { incremental.set(received.await(2, TimeUnit.SECONDS)); }
                catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                output.write("data: {\"choices\":[{\"delta\":{\"content\":\" second\"}}]}\n\ndata: [DONE]\n\n".getBytes(StandardCharsets.UTF_8));
            }
        });
        server.start();
        try {
            StringBuilder fragments = new StringBuilder();
            AiResponse result = service(server).stream(request(), fragment -> {
                fragments.append(fragment);
                received.countDown();
            }, () -> false);
            assertTrue(incremental.get(), "First fragment must arrive before provider closes the response");
            assertEquals("first second", result.answer());
            assertEquals(result.answer(), fragments.toString());
        } finally { server.stop(0); }
    }

    @Test
    void providerFailureIsNotReturnedAsSuccessfulAnswer() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/chat/completions", exchange -> {
            exchange.sendResponseHeaders(503, -1);
            exchange.close();
        });
        server.start();
        try {
            assertThrows(BusinessException.class, () -> service(server).stream(request(), ignored -> {}, () -> false));
        } finally { server.stop(0); }
    }

    @Test
    void truncatedProviderStreamIsNotAcceptedAsComplete() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/chat/completions", exchange -> {
            exchange.getResponseHeaders().add("Content-Type", "text/event-stream");
            byte[] bytes = "data: {\"choices\":[{\"delta\":{\"content\":\"partial\"}}]}\n\n".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            try (var output = exchange.getResponseBody()) { output.write(bytes); }
        });
        server.start();
        try {
            assertThrows(BusinessException.class, () -> service(server).stream(request(), ignored -> {}, () -> false));
        } finally { server.stop(0); }
    }

    private OpenAiCompatibleAiService service(HttpServer server) {
        return new OpenAiCompatibleAiService("http://127.0.0.1:" + server.getAddress().getPort() + "/v1", "test", "test",
                new PromptBuilder(), new ObjectMapper());
    }

    private AiRequest request() {
        DocumentChunk chunk = new DocumentChunk();
        chunk.setContent("A relevant document");
        return new AiRequest("question", List.of(new RetrievedChunk(chunk, 1, 1, 1)), true);
    }
}
