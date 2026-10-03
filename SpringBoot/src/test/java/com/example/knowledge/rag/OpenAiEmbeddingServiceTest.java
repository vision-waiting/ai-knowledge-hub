package com.example.knowledge.rag;

import com.example.knowledge.common.BusinessException;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class OpenAiEmbeddingServiceTest {
    @Test
    void batchUsesProviderIndicesToPreserveInputOrder() throws Exception {
        HttpServer server = server("{\"data\":[{\"index\":1,\"embedding\":[0,1]},{\"index\":0,\"embedding\":[1,0]}]}");
        try { assertEquals(List.of(List.of(1f, 0f), List.of(0f, 1f)), service(server).embedBatch(List.of("first", "second"))); }
        finally { server.stop(0); }
    }

    @Test
    void emptyEmbeddingResponseIsFailure() throws Exception {
        HttpServer server = server("{\"data\":[]}");
        try { assertThrows(BusinessException.class, () -> service(server).embed("text")); }
        finally { server.stop(0); }
    }

    @Test
    void inconsistentDimensionsAreRejected() throws Exception {
        HttpServer server = server("{\"data\":[{\"index\":0,\"embedding\":[1,0]},{\"index\":1,\"embedding\":[1]}]}");
        try { assertThrows(BusinessException.class, () -> service(server).embedBatch(List.of("first", "second"))); }
        finally { server.stop(0); }
    }

    private HttpServer server(String response) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/embeddings", exchange -> {
            byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            try (var output = exchange.getResponseBody()) { output.write(bytes); }
        });
        server.start(); return server;
    }

    private OpenAiEmbeddingService service(HttpServer server) {
        return new OpenAiEmbeddingService("http://127.0.0.1:" + server.getAddress().getPort() + "/v1", "test", "test");
    }
}
