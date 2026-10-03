package com.example.knowledge.rag;

import com.example.knowledge.entity.DocumentChunk;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PromptBuilderTest {
    @Test
    void retrievedSourcesHaveStableCitationNumbers() {
        DocumentChunk chunk = new DocumentChunk(); chunk.setContent("Redis snapshot"); chunk.setPageNumber(3);
        PromptBuilder prompts = new PromptBuilder();
        String prompt = prompts.build("persistence", new RetrievalResult(List.of(new RetrievedChunk(chunk, 1, 1, 1)), true));
        assertTrue(prompt.contains("[1]"));
        assertTrue(prompts.systemPrompt().contains("[1]"));
    }
}
