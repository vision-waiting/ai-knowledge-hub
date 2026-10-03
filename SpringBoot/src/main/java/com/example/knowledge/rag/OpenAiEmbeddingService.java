package com.example.knowledge.rag;

import com.fasterxml.jackson.databind.JsonNode;
import com.example.knowledge.common.BusinessException;
import com.example.knowledge.common.ErrorCode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.http.MediaType;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Component
@ConditionalOnProperty(name = "knowledge.ai.embedding-enabled", havingValue = "true")
public class OpenAiEmbeddingService implements EmbeddingService {
    private final RestClient client;
    private final String model;

    public OpenAiEmbeddingService(String baseUrl, String apiKey, String model) { this(baseUrl, apiKey, model, 10000, 60000); }

    @Autowired
    public OpenAiEmbeddingService(@Value("${knowledge.ai.embedding-base-url}") String baseUrl,
                                  @Value("${knowledge.ai.embedding-api-key}") String apiKey,
                                  @Value("${knowledge.ai.embedding-model}") String model,
                                  @Value("${knowledge.ai.connect-timeout-ms:10000}") int connectTimeoutMs,
                                  @Value("${knowledge.ai.read-timeout-ms:60000}") int readTimeoutMs) {
        this.model = model;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(connectTimeoutMs));
        factory.setReadTimeout(Duration.ofMillis(readTimeoutMs));
        this.client = RestClient.builder().baseUrl(baseUrl).requestFactory(factory).defaultHeader("Authorization", "Bearer " + apiKey).build();
    }

    @Override
    public List<Float> embed(String text) {
        return embedBatch(List.of(text == null ? "" : text)).getFirst();
    }

    @Override
    public List<List<Float>> embedBatch(List<String> texts) {
        if (texts == null || texts.isEmpty()) return List.of();
        List<List<Float>> result = new ArrayList<>();
        try {
            for (int start = 0; start < texts.size(); start += 64) {
                List<String> batch = texts.subList(start, Math.min(start + 64, texts.size()));
                JsonNode root = client.post().uri("/embeddings").contentType(MediaType.APPLICATION_JSON)
                        .body(Map.of("model", model, "input", batch)).retrieve().body(JsonNode.class);
                if (root == null || !root.path("data").isArray() || root.path("data").size() != batch.size()) throw unavailable();
                List<List<Float>> ordered = new ArrayList<>(Collections.nCopies(batch.size(), null));
                for (JsonNode item : root.path("data")) {
                    int index = item.path("index").asInt(-1);
                    if (index < 0 || index >= batch.size() || ordered.get(index) != null || !item.path("embedding").isArray()
                            || item.path("embedding").isEmpty()) throw unavailable();
                    List<Float> vector = new ArrayList<>();
                    for (JsonNode value : item.path("embedding")) {
                        if (!value.isNumber() || !Float.isFinite(value.floatValue())) throw unavailable();
                        vector.add(value.floatValue());
                    }
                    ordered.set(index, List.copyOf(vector));
                }
                result.addAll(ordered);
            }
            int dimensions = result.getFirst().size();
            if (result.stream().anyMatch(vector -> vector == null || vector.size() != dimensions)) throw unavailable();
            return List.copyOf(result);
        } catch (RestClientException exception) { throw unavailable(); }
    }

    private BusinessException unavailable() { return new BusinessException(ErrorCode.AI_UNAVAILABLE, "向量服务不可用或返回了无效数据"); }
}
