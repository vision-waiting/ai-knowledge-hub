package com.example.knowledge.rag;

import org.springframework.context.annotation.Primary;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@Primary
@ConditionalOnProperty(name = "knowledge.ai.llm-enabled", havingValue = "false", matchIfMissing = true)
public class LocalAiService implements AiService {
    private final PromptBuilder promptBuilder;
    public LocalAiService(PromptBuilder promptBuilder) { this.promptBuilder = promptBuilder; }

    @Override
    public AiResponse generate(AiRequest request) {
        if (!request.sufficient() || request.context().isEmpty()) return new AiResponse("当前知识库中没有找到足够相关的资料，因此无法基于当前知识库准确回答该问题。", "LOCAL_DEMO");
        String answer = request.context().stream().limit(3).map(item -> item.chunk().getContent()).reduce((a, b) -> a + "\n\n" + b).orElse("");
        return new AiResponse("基于当前知识库资料，相关内容如下：\n\n" + answer + "\n\n（本地演示模式：回答仅来自召回片段。）", "LOCAL_DEMO");
    }
}
