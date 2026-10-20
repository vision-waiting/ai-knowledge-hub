package com.example.knowledge.content;

import com.example.knowledge.common.Result;
import com.example.knowledge.security.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController @RequestMapping("/api/knowledge-bases/{kb}")
public class CollaborationController {
    private final CollaborationService service;
    public CollaborationController(CollaborationService service) { this.service=service; }
    @GetMapping("/star") public Result<CollaborationService.Star> star(@PathVariable long kb) { return Result.success(service.star(kb,CurrentUser.require())); }
    @PutMapping("/star") public Result<CollaborationService.Star> addStar(@PathVariable long kb) { return Result.success(service.addStar(kb,CurrentUser.require())); }
    @DeleteMapping("/star") public Result<Void> removeStar(@PathVariable long kb) { service.removeStar(kb,CurrentUser.require());return Result.success(); }
    @GetMapping("/suggestions") public Result<CollaborationService.Page> list(@PathVariable long kb,@RequestParam(defaultValue="OPEN") CollaborationService.Status status,@RequestParam(defaultValue="0") int offset) { return Result.success(service.list(kb,status,offset,CurrentUser.require())); }
    @PostMapping("/suggestions") public Result<CollaborationService.Suggestion> propose(@PathVariable long kb,@Valid @RequestBody CollaborationService.Proposal body) { return Result.success(service.propose(kb,body,CurrentUser.require())); }
    @PostMapping("/suggestions/{id}/merge") public Result<CollaborationService.Suggestion> merge(@PathVariable long kb,@PathVariable long id) { return Result.success(service.merge(kb,id,CurrentUser.require())); }
    @PostMapping("/suggestions/{id}/close") public Result<CollaborationService.Suggestion> close(@PathVariable long kb,@PathVariable long id) { return Result.success(service.close(kb,id,CurrentUser.require())); }
    @GetMapping("/content/{id}/history") public Result<List<CollaborationService.Revision>> history(@PathVariable long kb,@PathVariable long id) { return Result.success(service.history(kb,id,CurrentUser.require())); }
    @PostMapping("/content/{id}/history/{version}/restore") public Result<ContentController.Entry> restore(@PathVariable long kb,@PathVariable long id,@PathVariable int version) { return Result.success(service.restore(kb,id,version,CurrentUser.require())); }
}
