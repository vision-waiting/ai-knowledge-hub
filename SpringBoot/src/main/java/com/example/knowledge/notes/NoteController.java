package com.example.knowledge.notes;

import com.example.knowledge.common.Result;
import com.example.knowledge.security.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/notes")
public class NoteController {
    private final NoteService service;
    public NoteController(NoteService service) { this.service = service; }
    @GetMapping public Result<List<NoteVO>> list(@RequestParam(required = false) String keyword, @RequestParam(required = false) String category) {
        return Result.success(service.list(CurrentUser.require(), keyword, category));
    }
    @GetMapping("/categories") public Result<List<String>> categories() { return Result.success(service.categories(CurrentUser.require())); }
    @GetMapping("/{id}") public Result<NoteVO> get(@PathVariable Long id) { return Result.success(service.get(id, CurrentUser.require())); }
    @PostMapping public Result<NoteVO> create(@Valid @RequestBody NoteRequest request) { return Result.success(service.create(request, CurrentUser.require())); }
    @PutMapping("/{id}") public Result<NoteVO> update(@PathVariable Long id, @Valid @RequestBody NoteRequest request) { return Result.success(service.update(id, request, CurrentUser.require())); }
    @DeleteMapping("/{id}") public Result<Void> delete(@PathVariable Long id) { service.delete(id, CurrentUser.require()); return Result.success(); }
}
