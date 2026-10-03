package com.example.knowledge.evaluation;

import com.example.knowledge.common.Result;
import com.example.knowledge.security.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/knowledge-bases/{knowledgeBaseId}/retrieval")
public class EvaluationController {
    private final EvaluationService service;
    public EvaluationController(EvaluationService service) { this.service = service; }

    public record DebugRequest(@NotBlank @Size(max = 2000) String question, @NotNull @Min(1) @Max(20) Integer topK) {}
    public record EvaluationCase(@NotBlank @Size(max = 2000) String question,
                                 @NotNull @Size(max = 20) List<@NotNull @Positive Long> expectedDocumentIds) {}
    public record EvaluationRequest(@NotNull @Min(1) @Max(20) Integer topK,
                                    @NotNull @Size(min = 1, max = 30) List<@NotNull @Valid EvaluationCase> cases) {}

    @PostMapping("/debug")
    public Result<EvaluationService.DebugResult> debug(@PathVariable Long knowledgeBaseId, @Valid @RequestBody DebugRequest request) {
        return Result.success(service.debug(knowledgeBaseId, CurrentUser.require(), request));
    }
    @GetMapping("/runs")
    public Result<List<EvaluationService.RunSummary>> runs(@PathVariable Long knowledgeBaseId) {
        return Result.success(service.runs(knowledgeBaseId, CurrentUser.require()));
    }
    @PostMapping("/evaluate")
    public Result<EvaluationService.EvaluationRun> evaluate(@PathVariable Long knowledgeBaseId, @Valid @RequestBody EvaluationRequest request) {
        return Result.success(service.evaluate(knowledgeBaseId, CurrentUser.require(), request));
    }
}
