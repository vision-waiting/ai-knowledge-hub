package com.example.knowledge.notifications;

import com.example.knowledge.common.Result;
import com.example.knowledge.security.CurrentUser;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class NotificationController {
    private final NotificationService service;
    public NotificationController(NotificationService service) { this.service = service; }
    @GetMapping("/knowledge-bases/{id}/subscription")
    public Result<NotificationService.Subscription> subscription(@PathVariable Long id) { return Result.success(service.subscription(id, CurrentUser.require())); }
    @PutMapping("/knowledge-bases/{id}/subscription")
    public Result<NotificationService.Subscription> follow(@PathVariable Long id) { return Result.success(service.follow(id, CurrentUser.require())); }
    @DeleteMapping("/knowledge-bases/{id}/subscription")
    public Result<NotificationService.Subscription> unfollow(@PathVariable Long id) { return Result.success(service.unfollow(id, CurrentUser.require())); }
    @GetMapping("/notifications")
    public Result<NotificationService.NotificationPage> list(@RequestParam(defaultValue="false") boolean unreadOnly,
            @RequestParam(defaultValue="1") int page, @RequestParam(defaultValue="20") int pageSize) {
        return Result.success(service.list(CurrentUser.require(), unreadOnly, page, pageSize));
    }
    @GetMapping("/notifications/unread-count")
    public Result<Map<String, Long>> unreadCount() { return Result.success(Map.of("count", service.unreadCount(CurrentUser.require()))); }
    @PutMapping("/notifications/{id}/read")
    public Result<Void> read(@PathVariable Long id) { service.markRead(id, CurrentUser.require()); return Result.success(); }
    @PutMapping("/notifications/read-all")
    public Result<Void> readAll() { service.markAllRead(CurrentUser.require()); return Result.success(); }
}
