package com.example.knowledge.chat;

import org.springframework.stereotype.Service;
import java.util.List;
import java.util.ArrayList;
import java.util.Collections;
import com.example.knowledge.entity.ChatMessage;

@Service
public class QueryRewriteService {
    public String rewrite(String question, List<String> history) {
        if (question == null || question.isBlank() || history == null || history.isEmpty()) return question;
        String trimmed = question.trim();
        if (isFollowUp(trimmed)) {
            List<String> context = new ArrayList<>();
            for (String previous : history) {
                if (previous == null || previous.isBlank()) continue;
                context.add(previous.trim());
                if (!isFollowUp(previous.trim()) || context.size() >= 4) break;
            }
            Collections.reverse(context);
            context.add(trimmed);
            return String.join(" ", context);
        }
        return trimmed;
    }

    public String rewriteConversation(String question, List<ChatMessage> descendingHistory) {
        return rewrite(question, descendingHistory.stream().filter(message -> "USER".equals(message.getRole()))
                .map(ChatMessage::getContent).toList());
    }

    private boolean isFollowUp(String question) {
        return question.matches("(?is)^(它|这[个些种]|该|那个|那[么些]|为什么|为啥|怎么做|还有|继续|详细|举个|举例|再说|what about\\b|why\\b|how about\\b|and\\b|it\\b|its\\b|they\\b|them\\b|that\\b|those\\b|explain more\\b).*");
    }
}
