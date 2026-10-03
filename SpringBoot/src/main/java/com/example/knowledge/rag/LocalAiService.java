package com.example.knowledge.rag;

import org.springframework.context.annotation.Primary;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

@Component
@Primary
@ConditionalOnExpression("'${knowledge.ai.mode:}' == 'LOCAL_DEMO' || ('${knowledge.ai.mode:}' == '' && !${knowledge.ai.llm-enabled:false})")
public class LocalAiService implements AiService {
    private final PromptBuilder promptBuilder;
    public LocalAiService(PromptBuilder promptBuilder) { this.promptBuilder = promptBuilder; }

    @Override
    public AiResponse generate(AiRequest request) {
        if (!request.sufficient() || request.context().isEmpty()) return new AiResponse("当前知识库中没有找到足够相关的资料，因此无法基于当前知识库准确回答该问题。", "LOCAL_DEMO");
        String answer = java.util.stream.IntStream.range(0, request.context().size())
                .mapToObj(index -> "[" + (index + 1) + "] " + request.context().get(index).chunk().getContent())
                .collect(java.util.stream.Collectors.joining("\n\n"));
        return new AiResponse("基于当前知识库资料，相关内容如下：\n\n" + answer + "\n\n（原文检索：以上为文档片段摘录，未经大语言模型生成。）", "LOCAL_DEMO");
    }

    @Override
    public AiResponse stream(AiRequest request, Consumer<String> onFragment, BooleanSupplier cancelled) {
        AiResponse response = generate(request);
        String answer = response.answer();
        int offset = 0;
        while (offset < answer.length() && !cancelled.getAsBoolean()) {
            int end = answer.offsetByCodePoints(offset, Math.min(24, answer.codePointCount(offset, answer.length())));
            onFragment.accept(answer.substring(offset, end));
            offset = end;
        }
        return response;
    }
}
