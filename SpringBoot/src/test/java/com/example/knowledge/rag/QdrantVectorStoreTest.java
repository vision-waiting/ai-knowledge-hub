package com.example.knowledge.rag;

import com.example.knowledge.entity.DocumentChunk;
import com.example.knowledge.mapper.DocumentChunkMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class QdrantVectorStoreTest {
    private HttpServer server;
    private QdrantVectorStore store;
    private final DocumentChunkMapper mapper = mock(DocumentChunkMapper.class);
    private final ObjectMapper json = new ObjectMapper();

    @BeforeEach void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.start();
        store = new QdrantVectorStore("http://127.0.0.1:" + server.getAddress().getPort(), mapper);
    }
    @AfterEach void stop() { server.stop(0); }

    @Test void firstUpsertCreatesCollectionWithActualEmbeddingDimension() throws Exception {
        AtomicReference<String> collectionBody = new AtomicReference<>();
        server.createContext("/collections/knowledge_chunks", exchange -> {
            String path = exchange.getRequestURI().getPath();
            int status = 200;
            String response = "{\"result\":true}";
            if (path.equals("/collections/knowledge_chunks") && exchange.getRequestMethod().equals("GET")) status = 404;
            if (path.equals("/collections/knowledge_chunks") && exchange.getRequestMethod().equals("PUT")) collectionBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length); exchange.getResponseBody().write(bytes); exchange.close();
        });
        store.upsert(chunk(1L, 2L), List.of(1f, 0f, 0.5f));
        assertNotNull(collectionBody.get());
        assertEquals(3, json.readTree(collectionBody.get()).path("vectors").path("size").asInt());
    }

    @Test void resultsAreCheckedAgainstDatabaseKnowledgeBaseEvenWithBadRemotePayload() {
        server.createContext("/collections/knowledge_chunks", exchange -> {
            String response = exchange.getRequestURI().getPath().endsWith("search")
                    ? "{\"result\":[{\"payload\":{\"chunkId\":1},\"score\":0.9}]}"
                    : "{\"result\":{\"config\":{\"params\":{\"vectors\":{\"size\":2}}}}}";
            byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length); exchange.getResponseBody().write(bytes); exchange.close();
        });
        when(mapper.selectById(1L)).thenReturn(chunk(1L, 999L));
        assertTrue(store.search(2L, List.of(1f, 0f), 5).isEmpty());
    }

    @Test void cleanupOfMissingCollectionIsIdempotent() {
        server.createContext("/collections/knowledge_chunks", exchange -> {
            exchange.sendResponseHeaders(404, -1); exchange.close();
        });
        assertDoesNotThrow(() -> store.deleteByKnowledgeBase(2L));
    }

    @Test void onlyCurrentDocumentsEnterRemoteTopK() throws Exception {
        var documents = mock(com.example.knowledge.mapper.DocumentMapper.class);
        var current = new com.example.knowledge.entity.Document(); current.setId(5L); current.setStatus("SUCCESS");
        when(documents.selectList(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class))).thenReturn(List.of(current));
        org.springframework.test.util.ReflectionTestUtils.setField(store, "documents", documents);
        AtomicReference<String> body = new AtomicReference<>();
        server.createContext("/collections/knowledge_chunks", exchange -> {
            boolean search = exchange.getRequestURI().getPath().endsWith("search");
            if (search) body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            String response = search ? "{\"result\":[]}" : "{\"result\":{\"config\":{\"params\":{\"vectors\":{\"size\":2}}}}}";
            byte[] bytes=response.getBytes(StandardCharsets.UTF_8);exchange.getResponseHeaders().set("Content-Type","application/json");
            exchange.sendResponseHeaders(200,bytes.length);exchange.getResponseBody().write(bytes);exchange.close();
        });
        store.search(2L,List.of(1f,0f),5);
        var conditions=json.readTree(body.get()).path("filter").path("must");
        assertEquals("documentId",conditions.path(1).path("key").asText());
        assertEquals(5,conditions.path(1).path("match").path("any").path(0).asLong());
    }

    private DocumentChunk chunk(Long id, Long knowledgeBaseId) {
        DocumentChunk chunk = new DocumentChunk(); chunk.setId(id); chunk.setDocumentId(5L); chunk.setKnowledgeBaseId(knowledgeBaseId);
        chunk.setPageNumber(1); chunk.setContent("Evidence"); return chunk;
    }
}
