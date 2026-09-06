package com.example.knowledge.rag;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

@Component
@Primary
@ConditionalOnProperty(name = "knowledge.ai.llm-enabled", havingValue = "true")
public class OpenAiCompatibleAiService implements AiService {
    private final RestClient client;
    private final String model;
    private final PromptBuilder promptBuilder;
    private final ObjectMapper objectMapper;

    public OpenAiCompatibleAiService(@org.springframework.beans.factory.annotation.Value("${knowledge.ai.llm-base-url}") String baseUrl,
                                     @org.springframework.beans.factory.annotation.Value("${knowledge.ai.llm-api-key}") String apiKey,
                                     @org.springframework.beans.factory.annotation.Value("${knowledge.ai.llm-model}") String model,
                                     PromptBuilder promptBuilder, ObjectMapper objectMapper) {
        this.client = RestClient.builder().baseUrl(baseUrl).defaultHeader("Authorization", "Bearer " + apiKey).build();
        this.model = model; this.promptBuilder = promptBuilder; this.objectMapper = objectMapper;
    }

    @Override
    public AiResponse generate(AiRequest request) {
        if (!request.sufficient() || request.context().isEmpty()) return new AiResponse("当前知识库中没有找到足够相关的资料，因此无法基于当前知识库准确回答该问题。", "OPENAI_COMPATIBLE");
        String prompt = promptBuilder.build(request.question(), new RetrievalResult(request.context(), true));
        JsonNode root = client.post().uri("/chat/completions").body(Map.of("model", model, "messages", List.of(Map.of("role", "system", "content", promptBuilder.systemPrompt()), Map.of("role", "user", "content", prompt)))).retrieve().body(JsonNode.class);
        String answer = root == null ? "AI 服务没有返回内容" : root.path("choices").path(0).path("message").path("content").asText("AI 服务没有返回内容");
        return new AiResponse(answer, "OPENAI_COMPATIBLE");
    }

    @Override
    public AiResponse stream(AiRequest request, Consumer<String> onFragment, BooleanSupplier cancelled) {
        if (!request.sufficient() || request.context().isEmpty()) {
            AiResponse response = new AiResponse("当前知识库中没有找到足够相关的资料，因此无法基于当前知识库准确回答该问题。", "OPENAI_COMPATIBLE");
            if (!cancelled.getAsBoolean()) onFragment.accept(response.answer());
            return response;
        }
        String prompt = promptBuilder.build(request.question(), new RetrievalResult(request.context(), true));
        StringBuilder answer = new StringBuilder();
        try {
            String body = client.post().uri("/chat/completions")
                    .body(Map.of("model", model, "stream", true, "messages", List.of(
                            Map.of("role", "system", "content", promptBuilder.systemPrompt()),
                            Map.of("role", "user", "content", prompt))))
                    .retrieve().body(String.class);
            if (body != null) {
                for (String line : body.lines().toList()) {
                    if (cancelled.getAsBoolean()) break;
                    if (!line.startsWith("data:")) continue;
                    String data = line.substring(5).trim();
                    if ("[DONE]".equals(data)) break;
                    JsonNode delta = objectMapper.readTree(data).path("choices").path(0).path("delta").path("content");
                    if (!delta.isTextual() || delta.asText().isEmpty()) continue;
                    String fragment = delta.asText();
                    answer.append(fragment);
                    onFragment.accept(fragment);
                }
            }
        } catch (RuntimeException | java.io.IOException ignored) {
            return new AiResponse("AI 服务暂时不可用，请稍后重试。", "OPENAI_COMPATIBLE");
        }
        return new AiResponse(answer.toString(), "OPENAI_COMPATIBLE");
    }
}
