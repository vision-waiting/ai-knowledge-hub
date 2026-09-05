package com.example.knowledge.rag;

import org.springframework.stereotype.Component;

@Component
public class PromptBuilder {
    public String systemPrompt() { return "你是企业知识库智能助手。只依据提供的资料回答，不得凭空编造；资料不足时明确说明。"; }

    public String build(String question, RetrievalResult result) {
        String context = result.items().stream().map(item -> "来源页码 " + item.chunk().getPageNumber() + "：" + item.chunk().getContent()).reduce((a, b) -> a + "\n" + b).orElse("无");
        return systemPrompt() + "\n\n资料：\n" + context + "\n\n问题：" + question;
    }
}

