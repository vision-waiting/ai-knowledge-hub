package com.example.knowledge.search;

import com.example.knowledge.common.Result;
import com.example.knowledge.security.CurrentUser;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/search")
public class SearchController {
    private final SearchService service;
    public SearchController(SearchService service) { this.service = service; }
    @GetMapping
    public Result<List<SearchResult>> search(@RequestParam Long knowledgeBaseId, @RequestParam String q,
        @RequestParam(required=false) String fileName, @RequestParam(required=false) String fileType,
        @RequestParam(required=false) String tag, @RequestParam(defaultValue="0") int offset,
        @RequestParam(defaultValue="50") int limit) {
        return Result.success(service.search(knowledgeBaseId, q, fileName, fileType, tag, offset, limit, CurrentUser.require()));
    }
}
