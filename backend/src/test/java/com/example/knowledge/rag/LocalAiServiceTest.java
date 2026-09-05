package com.example.knowledge.rag;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalAiServiceTest {
    @Test
    void localModeRefusesQuestionWhenContextIsInsufficient() {
        LocalAiService service = new LocalAiService(new PromptBuilder());
        AiResponse response = service.generate(new AiRequest("未知问题", List.of(), false));
        assertTrue(response.answer().contains("没有找到足够相关"));
        assertTrue(response.mode().equals("LOCAL_DEMO"));
    }
}

