package com.example.knowledge.websource;
import com.example.knowledge.common.*;
import com.example.knowledge.security.CurrentUser;
import com.example.knowledge.service.PermissionService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.web.bind.annotation.*;
import java.util.List;
@RestController @RequestMapping("/api/knowledge-bases/{kb}/web-sources")
public class WebSourceController {
 private final WebSourceService service;private final PublicPageFetcher fetcher;private final PermissionService permissions;
 public WebSourceController(WebSourceService s,PublicPageFetcher f,PermissionService p){service=s;fetcher=f;permissions=p;}
 public record Request(@NotBlank @Size(max=2000) String url){}
 @GetMapping public Result<List<WebSourceService.Source>> list(@PathVariable Long kb){permissions.requireKnowledgeBaseAccess(kb,CurrentUser.require(),true);return Result.success(service.list(kb));}
 @PostMapping public Result<WebSourceService.Source> create(@PathVariable Long kb,@Valid @RequestBody Request body) throws java.io.IOException {
  permissions.requireKnowledgeBaseAccess(kb,CurrentUser.require(),true);var page=fetcher.fetch(body.url().trim());return Result.success(service.save(kb,null,body.url().trim(),page,CurrentUser.require()));
 }
 @PostMapping("/{id}/check") public Result<WebSourceService.Source> check(@PathVariable Long kb,@PathVariable long id) throws java.io.IOException {
  permissions.requireKnowledgeBaseAccess(kb,CurrentUser.require(),true);var source=service.find(kb,id);var page=fetcher.fetch(source.url());return Result.success(service.save(kb,id,source.url(),page,CurrentUser.require()));
 }
}
