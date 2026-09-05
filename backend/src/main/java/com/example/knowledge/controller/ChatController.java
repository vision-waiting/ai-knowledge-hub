package com.example.knowledge.controller;

import com.example.knowledge.chat.ChatAnswer;
import com.example.knowledge.chat.ChatService;
import com.example.knowledge.common.Result;
import com.example.knowledge.security.CurrentUser;
import com.example.knowledge.security.JwtPrincipal;
import com.example.knowledge.vo.ChatSessionVO;
import com.example.knowledge.vo.ChatMessageVO;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.List;

@RestController
@RequestMapping("/api/chat")
public class ChatController {
    private final ChatService service;
    public ChatController(ChatService service) { this.service = service; }

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@RequestParam Long knowledgeBaseId, @RequestParam String question,
                             @RequestParam(required = false) Long sessionId) {
        return stream(sessionId, knowledgeBaseId, question, CurrentUser.require());
    }

    public SseEmitter stream(Long sessionId, Long knowledgeBaseId, String question, JwtPrincipal user) {
        SseEmitter emitter = new SseEmitter(120_000L);
        CompletableFuture.runAsync(() -> {
            try {
                ChatAnswer answer = service.answer(knowledgeBaseId, question, user, sessionId);
                emitter.send(SseEmitter.event().name("message").data(answer.answer()));
                for (var citation : answer.citations()) emitter.send(SseEmitter.event().name("citation").data(citation));
                emitter.send(SseEmitter.event().name("done").data(new Result<>(200, "success", answer.sessionId(), System.currentTimeMillis())));
                emitter.complete();
            } catch (Exception exception) {
                try { emitter.send(SseEmitter.event().name("error").data("问答处理失败，请稍后重试")); } catch (IOException ignored) { }
                emitter.completeWithError(exception);
            }
        });
        return emitter;
    }

    @GetMapping("/sessions")
    public com.example.knowledge.common.Result<List<ChatSessionVO>> sessions() { return com.example.knowledge.common.Result.success(service.listSessions(CurrentUser.require())); }

    @GetMapping("/sessions/{sessionId}/messages")
    public com.example.knowledge.common.Result<List<ChatMessageVO>> messages(@org.springframework.web.bind.annotation.PathVariable Long sessionId) { return com.example.knowledge.common.Result.success(service.listMessages(sessionId, CurrentUser.require())); }
}
