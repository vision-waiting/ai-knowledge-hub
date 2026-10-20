package com.example.knowledge.rag;

import com.example.knowledge.common.BusinessException;
import com.example.knowledge.common.ErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** Request-scoped selection; never changes the default service or falls back between providers. */
@Component
public class ChatModelRouter {
    public record Option(String id, String label, String model, boolean configured) {}
    public record Selection(AiService service, String provider, String model, String mode) {}
    private final AiService local;
    private final AiService cloud;
    private final String localModel;
    private final String cloudModel;
    private final boolean localEnabled;
    private final Semaphore localSlots = new Semaphore(1, true);
    private final Semaphore localAdmissions = new Semaphore(3, true);

    @Autowired
    public ChatModelRouter(AiService local, Environment env, PromptBuilder prompts, ObjectMapper json) {
        this(local, env.getProperty("knowledge.ai.llm-model", ""),
                env.getProperty("knowledge.ai.local-enabled", Boolean.class, false), cloud(env, prompts, json),
                env.getProperty("knowledge.ai.cloud-model", ""));
    }

    ChatModelRouter(AiService local, String localModel, boolean localEnabled, AiService cloud, String cloudModel) {
        this.local = local;
        boolean realModel = "OPENAI_COMPATIBLE".equals(local.mode());
        this.localModel = realModel ? localModel : null;
        this.localEnabled = localEnabled && realModel;
        this.cloud = cloud; this.cloudModel = cloudModel;
    }

    private static AiService cloud(Environment env, PromptBuilder prompts, ObjectMapper json) {
        String url = env.getProperty("knowledge.ai.cloud-base-url", "").strip();
        String key = env.getProperty("knowledge.ai.cloud-api-key", "").strip();
        String model = env.getProperty("knowledge.ai.cloud-model", "").strip();
        if (url.isEmpty() || key.isEmpty() || model.isEmpty()) return null;
        return new OpenAiCompatibleAiService(url, key, model, prompts, json,
                env.getProperty("knowledge.ai.connect-timeout-ms", Integer.class, 10000),
                env.getProperty("knowledge.ai.read-timeout-ms", Integer.class, 90000),
                env.getProperty("knowledge.ai.cloud-reasoning-effort", ""),
                env.getProperty("knowledge.ai.cloud-max-tokens", Integer.class, 2048));
    }

    public String defaultProvider() { return localEnabled ? "local" : "default"; }
    public List<Option> options() {
        return List.of(new Option(defaultProvider(), localEnabled ? "本地模型" : "默认模型", localModel == null ? "" : localModel, true),
                new Option("cloud", "云端 API", cloudModel, cloud != null));
    }
    public Selection select(String provider) {
        String chosen = provider == null || provider.isBlank() ? defaultProvider() : provider;
        if (chosen.equals(defaultProvider()))
            return new Selection(local, chosen, localModel, localEnabled ? "LOCAL_MODEL" : local.mode());
        if (chosen.equals("cloud")) {
            if (cloud == null) throw new BusinessException(ErrorCode.AI_UNAVAILABLE, "云端 API 尚未配置，请使用本地模型或联系管理员");
            return new Selection(cloud, "cloud", cloudModel, "CLOUD_API");
        }
        throw new BusinessException(ErrorCode.BAD_REQUEST, "不支持的模型来源");
    }

    public AiResponse stream(Selection selected, AiRequest request, Consumer<String> fragment,
                             BooleanSupplier cancelled, Consumer<String> status) {
        checkCancelled(cancelled);
        if (!selected.provider().equals("local")) return invoke(selected, request, fragment, cancelled);
        if (!localAdmissions.tryAcquire()) throw busy();
        boolean acquired = false;
        try {
            acquired = localSlots.tryAcquire(0, TimeUnit.MILLISECONDS);
            if (!acquired) {
                status.accept("本地模型正在处理其他问题，正在排队（最多等待 15 秒）；可停止后手动选择云端 API。");
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
                while (!acquired && System.nanoTime() < deadline) {
                    checkCancelled(cancelled);
                    acquired = localSlots.tryAcquire(100, TimeUnit.MILLISECONDS);
                }
            }
            if (!acquired) throw busy();
            checkCancelled(cancelled);
            status.accept("本地模型正在生成回答");
            return invoke(selected, request, fragment, cancelled);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt(); throw new CancellationException("请求已取消");
        } finally {
            if (acquired) localSlots.release();
            localAdmissions.release();
        }
    }
    private AiResponse invoke(Selection selected, AiRequest request, Consumer<String> fragment, BooleanSupplier cancelled) {
        AiResponse response = selected.service().stream(request, fragment, cancelled);
        return new AiResponse(response.answer(), selected.mode());
    }
    private static BusinessException busy() {
        return new BusinessException(ErrorCode.TOO_MANY_REQUESTS, "本地模型繁忙，请稍后重试，或手动选择云端 API");
    }
    private static void checkCancelled(BooleanSupplier cancelled) {
        if (cancelled.getAsBoolean() || Thread.currentThread().isInterrupted()) throw new CancellationException("请求已取消");
    }
    @PreDestroy void close() { if (cloud instanceof OpenAiCompatibleAiService service) service.close(); }
}
