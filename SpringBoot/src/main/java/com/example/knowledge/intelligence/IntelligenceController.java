package com.example.knowledge.intelligence;

import com.example.knowledge.common.Result;
import com.example.knowledge.security.CurrentUser;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import static com.example.knowledge.intelligence.IntelligenceModels.*;

@RestController
@RequestMapping("/api")
public class IntelligenceController {
    private final IntelligenceService service;
    public IntelligenceController(IntelligenceService service) { this.service = service; }
    @PostMapping("/knowledge-bases/{id}/analysis")
    public Result<Analysis> analyze(@PathVariable Long id, @RequestBody AnalysisRequest body) {
        return Result.success(service.analyze(id, body, CurrentUser.require()));
    }
    @GetMapping("/knowledge-bases/{id}/analysis")
    public Result<List<Analysis>> analyses(@PathVariable Long id) {
        return Result.success(service.analyses(id, CurrentUser.require()));
    }
    @PostMapping("/knowledge-bases/{id}/study")
    public Result<Study> study(@PathVariable Long id, @RequestBody StudyRequest body) {
        return Result.success(service.createStudy(id, body, CurrentUser.require()));
    }
    @GetMapping("/knowledge-bases/{id}/study")
    public Result<List<Study>> studies(@PathVariable Long id) { return Result.success(service.studies(id, CurrentUser.require())); }
    @GetMapping("/study")
    public Result<List<Study>> studies() { return Result.success(service.studies(null, CurrentUser.require())); }
    @GetMapping("/study/{id}")
    public Result<Study> study(@PathVariable Long id) { return Result.success(service.study(id, CurrentUser.require())); }
    @PostMapping("/study/{id}/submit")
    public Result<Grade> submit(@PathVariable Long id, @RequestBody SubmitRequest body) {
        return Result.success(service.submit(id, body, CurrentUser.require()));
    }
    @GetMapping("/study/{id}/attempts")
    public Result<List<Grade>> attempts(@PathVariable Long id) { return Result.success(service.attempts(id, CurrentUser.require())); }
    @GetMapping("/study/mistakes")
    public Result<List<Mistake>> mistakes(@RequestParam(defaultValue="false") boolean includeResolved) {
        return Result.success(service.mistakes(includeResolved, CurrentUser.require()));
    }
    @PutMapping("/study/mistakes/{id}/resolve")
    public Result<Void> resolve(@PathVariable Long id, @RequestBody ResolveRequest body) {
        service.resolve(id, body, CurrentUser.require()); return Result.success();
    }
}
