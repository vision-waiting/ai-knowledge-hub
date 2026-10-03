package com.example.knowledge.rag;

import org.junit.jupiter.api.Test;
import com.example.knowledge.entity.DocumentChunk;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

class LocalAiServiceTest {
    @Test
    void localModeRefusesQuestionWhenContextIsInsufficient() {
        LocalAiService service = new LocalAiService(new PromptBuilder());
        AiResponse response = service.generate(new AiRequest("未知问题", List.of(), false));
        assertTrue(response.answer().contains("没有找到足够相关"));
        assertTrue(response.mode().equals("LOCAL_DEMO"));
    }

    @Test
    void streamsExactAnswerInOrdered24CodePointFragments() {
        DocumentChunk chunk = new DocumentChunk();
        chunk.setContent("一二三四五六七八九十一二三四五六七八九十一二三四五六七八九十");
        AiRequest request = new AiRequest("问题", List.of(new RetrievedChunk(chunk, 1, 1, 1)), true);
        LocalAiService service = new LocalAiService(new PromptBuilder());
        List<String> fragments = new ArrayList<>();

        AiResponse response = service.stream(request, fragments::add, () -> false);

        assertEquals(service.generate(request), response);
        assertTrue(response.answer().contains("原文检索"));
        assertTrue(!response.answer().contains("演示"));
        assertEquals(service.generate(request).answer(), String.join("", fragments));
        assertTrue(fragments.stream().limit(fragments.size() - 1L)
                .allMatch(fragment -> fragment.codePointCount(0, fragment.length()) == 24));
        assertTrue(fragments.getLast().codePointCount(0, fragments.getLast().length()) <= 24);
    }

    @Test
    void stopsBeforeCallbackWhenCancellationIsSignalled() {
        DocumentChunk chunk = new DocumentChunk();
        chunk.setContent("有足够长的内容用于产生多个流式片段，继续补充文字确保答案超过二十四个码点。");
        AiRequest request = new AiRequest("问题", List.of(new RetrievedChunk(chunk, 1, 1, 1)), true);
        LocalAiService service = new LocalAiService(new PromptBuilder());
        List<String> fragments = new ArrayList<>();
        AtomicBoolean cancelled = new AtomicBoolean();

        service.stream(request, fragment -> { fragments.add(fragment); cancelled.set(true); }, cancelled::get);

        assertEquals(1, fragments.size());
    }
}

