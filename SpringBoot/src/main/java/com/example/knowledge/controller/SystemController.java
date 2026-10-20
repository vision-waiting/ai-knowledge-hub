package com.example.knowledge.controller;

import com.example.knowledge.common.Result;
import org.springframework.core.env.Environment;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/system")
public class SystemController {
    private final Environment environment;
    private final com.example.knowledge.rag.AiService ai;
    @org.springframework.beans.factory.annotation.Autowired
    private com.example.knowledge.rag.ChatModelRouter models;
    @GetMapping("/chat-models") public Result<ChatModels> chatModels() {
        return Result.success(new ChatModels(models.defaultProvider(), models.options()));
    }
    public record ChatModels(String defaultProvider, java.util.List<com.example.knowledge.rag.ChatModelRouter.Option> options) {}
    public SystemController(Environment environment, com.example.knowledge.rag.AiService ai) { this.environment = environment; this.ai = ai; }
    @GetMapping("/status") public Result<RuntimeStatus> status() {
        return Result.success(new RuntimeStatus(ai.mode(),
                enabled("knowledge.ai.embedding-enabled") ? "OPENAI_COMPATIBLE" : "LOCAL_DEMO",
                enabled("knowledge.qdrant-enabled") ? "QDRANT" : "LOCAL", enabled("knowledge.redis-enabled")));
    }
    private boolean enabled(String property) { return environment.getProperty(property, Boolean.class, false); }
    public record RuntimeStatus(String llmMode, String embeddingMode, String vectorStore, boolean redisEnabled) {}
}
