package com.example.knowledge.rag;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
@ConditionalOnProperty(name = "knowledge.ai.embedding-enabled", havingValue = "true")
public class OpenAiEmbeddingService implements EmbeddingService {
    private final RestClient client;
    private final String model;

    public OpenAiEmbeddingService(@org.springframework.beans.factory.annotation.Value("${knowledge.ai.embedding-base-url}") String baseUrl,
                                  @org.springframework.beans.factory.annotation.Value("${knowledge.ai.embedding-api-key}") String apiKey,
                                  @org.springframework.beans.factory.annotation.Value("${knowledge.ai.embedding-model}") String model) {
        this.model = model;
        this.client = RestClient.builder().baseUrl(baseUrl).defaultHeader("Authorization", "Bearer " + apiKey).build();
    }

    @Override
    public List<Float> embed(String text) {
        JsonNode root = client.post().uri("/embeddings").body(Map.of("model", model, "input", text == null ? "" : text)).retrieve().body(JsonNode.class);
        List<Float> values = new ArrayList<>();
        if (root != null && root.path("data").isArray() && root.path("data").size() > 0) {
            root.path("data").get(0).path("embedding").forEach(value -> values.add(value.floatValue()));
        }
        return values;
    }

    @Override
    public List<List<Float>> embedBatch(List<String> texts) {
        JsonNode root = client.post().uri("/embeddings").body(Map.of("model", model, "input", texts)).retrieve().body(JsonNode.class);
        List<List<Float>> result = new ArrayList<>();
        if (root != null && root.path("data").isArray()) root.path("data").forEach(item -> { List<Float> vector = new ArrayList<>(); item.path("embedding").forEach(value -> vector.add(value.floatValue())); result.add(vector); });
        return result;
    }
}
