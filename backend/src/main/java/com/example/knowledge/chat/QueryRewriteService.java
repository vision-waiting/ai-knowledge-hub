package com.example.knowledge.chat;

import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class QueryRewriteService {
    public String rewrite(String question, List<String> history) {
        if (question == null || question.isBlank() || history == null || history.isEmpty()) return question;
        String trimmed = question.trim();
        if (trimmed.startsWith("它") || trimmed.startsWith("这") || trimmed.startsWith("该") || trimmed.startsWith("那个")) {
            String previous = history.get(0).trim();
            return previous + " " + trimmed;
        }
        return trimmed;
    }
}

