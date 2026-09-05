package com.example.knowledge.rag;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

@Component
@Primary
@ConditionalOnProperty(name = "knowledge.ai.llm-enabled", havingValue = "true")
public class OpenAiCompatibleAiService implements AiService {
    private final RestClient client;
    private final String model;
    private final PromptBuilder promptBuilder;

    public OpenAiCompatibleAiService(@org.springframework.beans.factory.annotation.Value("${knowledge.ai.llm-base-url}") String baseUrl,
                                     @org.springframework.beans.factory.annotation.Value("${knowledge.ai.llm-api-key}") String apiKey,
                                     @org.springframework.beans.factory.annotation.Value("${knowledge.ai.llm-model}") String model,
                                     PromptBuilder promptBuilder) {
        this.client = RestClient.builder().baseUrl(baseUrl).defaultHeader("Authorization", "Bearer " + apiKey).build();
        this.model = model; this.promptBuilder = promptBuilder;
    }

    @Override
    public AiResponse generate(AiRequest request) {
        if (!request.sufficient() || request.context().isEmpty()) return new AiResponse("当前知识库中没有找到足够相关的资料，因此无法基于当前知识库准确回答该问题。", "OPENAI_COMPATIBLE");
        String prompt = promptBuilder.build(request.question(), new RetrievalResult(request.context(), true));
        JsonNode root = client.post().uri("/chat/completions").body(Map.of("model", model, "messages", List.of(Map.of("role", "system", "content", promptBuilder.systemPrompt()), Map.of("role", "user", "content", prompt)))).retrieve().body(JsonNode.class);
        String answer = root == null ? "AI 服务没有返回内容" : root.path("choices").path(0).path("message").path("content").asText("AI 服务没有返回内容");
        return new AiResponse(answer, "OPENAI_COMPATIBLE");
    }
}
