package com.example.knowledge.controller;

import com.example.knowledge.chat.ChatAnswer;
import com.example.knowledge.chat.ChatService;
import com.example.knowledge.common.Result;
import com.example.knowledge.common.BusinessException;
import com.example.knowledge.chat.ChatMetadata;
import jakarta.annotation.PreDestroy;
import com.example.knowledge.security.CurrentUser;
import com.example.knowledge.security.JwtPrincipal;
import com.example.knowledge.vo.ChatSessionVO;
import com.example.knowledge.vo.ChatMessageVO;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import jakarta.validation.Valid;
import com.example.knowledge.dto.ChatFeedbackRequest;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CancellationException;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.List;

@RestController
@RequestMapping("/api/chat")
public class ChatController {
    private final ChatService service;
    private final ThreadPoolExecutor executor = new ThreadPoolExecutor(8, 8, 30, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(32), Thread.ofPlatform().daemon().name("chat-stream-", 0).factory());
    public ChatController(ChatService service) { this.service = service; }

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@RequestParam Long knowledgeBaseId, @RequestParam String question,
                             @RequestParam(required = false) Long sessionId,
                             @RequestParam(required = false) String provider) {
        return stream(sessionId, knowledgeBaseId, question, CurrentUser.require(), provider);
    }

    public SseEmitter stream(Long sessionId, Long knowledgeBaseId, String question, JwtPrincipal user) {
        return stream(sessionId, knowledgeBaseId, question, user, null);
    }

    public SseEmitter stream(Long sessionId, Long knowledgeBaseId, String question, JwtPrincipal user, String provider) {
        SseEmitter emitter = new SseEmitter(120_000L);
        AtomicBoolean cancelled = new AtomicBoolean(false);
        AtomicReference<Future<?>> task = new AtomicReference<>();
        Runnable cancel = () -> {
            cancelled.set(true);
            Future<?> active = task.get();
            if (active != null) active.cancel(true);
        };
        emitter.onCompletion(cancel);
        emitter.onTimeout(() -> { cancel.run(); emitter.complete(); });
        emitter.onError(error -> cancel.run());
        // Initialize the response headers before async dispatch can observe the emitter.
        // The comment frame is ignored by EventSource clients and is not part of the API event contract.
        try {
            emitter.send(SseEmitter.event().comment("connected"));
        } catch (IOException | IllegalStateException exception) {
            cancelled.set(true);
            emitter.completeWithError(exception);
            return emitter;
        }
        try {
            task.set(executor.submit(() -> {
            try {
                ChatAnswer answer = service.answerStream(knowledgeBaseId, question, user, sessionId, fragment -> {
                    if (!cancelled.get()) {
                        try { emitter.send(SseEmitter.event().name("message").data(fragment)); }
                        catch (IOException exception) { cancelled.set(true); throw new RuntimeException(exception); }
                    }
                }, cancelled::get, metadata -> send(emitter, "meta", metadata, cancelled), provider,
                        status -> send(emitter, "status", status, cancelled));
                if (cancelled.get()) return;
                for (var citation : answer.citations()) emitter.send(SseEmitter.event().name("citation").data(citation));
                if (cancelled.get()) return;
                emitter.send(SseEmitter.event().name("done").data(new Result<>(200, "success", answer.sessionId(), System.currentTimeMillis())));
                emitter.complete();
            } catch (Exception exception) {
                if (cancelled.get() || exception instanceof CancellationException) { emitter.complete(); return; }
                String message = exception instanceof BusinessException ? exception.getMessage() : "问答处理失败，请稍后重试";
                try { emitter.send(SseEmitter.event().name("error").data(message)); } catch (IOException | IllegalStateException ignored) { }
                emitter.complete();
            }
            }));
            if (cancelled.get()) task.get().cancel(true);
        } catch (RejectedExecutionException exception) {
            send(emitter, "error", "问答服务繁忙，请稍后重试", cancelled);
            emitter.complete();
        }
        return emitter;
    }

    private static void send(SseEmitter emitter, String event, Object data, AtomicBoolean cancelled) {
        if (cancelled.get()) throw new CancellationException("Chat request cancelled");
        try { emitter.send(SseEmitter.event().name(event).data(data)); }
        catch (IOException | IllegalStateException exception) { cancelled.set(true); throw new CancellationException("Client disconnected"); }
    }

    @PreDestroy
    void stop() { executor.shutdownNow(); }

    @GetMapping("/sessions")
    public com.example.knowledge.common.Result<List<ChatSessionVO>> sessions() { return com.example.knowledge.common.Result.success(service.listSessions(CurrentUser.require())); }

    @GetMapping("/sessions/{sessionId}/messages")
    public com.example.knowledge.common.Result<List<ChatMessageVO>> messages(@org.springframework.web.bind.annotation.PathVariable Long sessionId) { return com.example.knowledge.common.Result.success(service.listMessages(sessionId, CurrentUser.require())); }

    @DeleteMapping("/sessions/{sessionId}")
    public Result<Void> deleteSession(@org.springframework.web.bind.annotation.PathVariable Long sessionId) {
        service.deleteSession(sessionId, CurrentUser.require());
        return Result.success(null);
    }

    @PutMapping("/messages/{messageId}/feedback")
    public Result<Void> feedback(@org.springframework.web.bind.annotation.PathVariable Long messageId, @Valid @RequestBody ChatFeedbackRequest request) {
        service.feedback(messageId, request, CurrentUser.require()); return Result.success(null);
    }
}
