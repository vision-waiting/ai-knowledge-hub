package com.example.knowledge.controller;

import com.example.knowledge.common.Result;
import com.example.knowledge.security.CurrentUser;
import com.example.knowledge.service.DocumentService;
import com.example.knowledge.vo.DocumentVO;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequestMapping("/api")
public class DocumentController {
    private final DocumentService service;
    public DocumentController(DocumentService service) { this.service = service; }

    @GetMapping("/knowledge-bases/{knowledgeBaseId}/documents")
    public ResponseEntity<Result<List<DocumentVO>>> list(@PathVariable Long knowledgeBaseId) {
        return ResponseEntity.ok(Result.success(service.list(knowledgeBaseId, CurrentUser.require())));
    }

    @PostMapping(value = "/knowledge-bases/{knowledgeBaseId}/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Result<DocumentVO>> upload(@PathVariable Long knowledgeBaseId, @RequestParam("file") MultipartFile file) throws IOException {
        return ResponseEntity.accepted().body(Result.success(service.upload(knowledgeBaseId, file, CurrentUser.require())));
    }

    @GetMapping("/documents/{id}")
    public ResponseEntity<Result<DocumentVO>> get(@PathVariable Long id) { return ResponseEntity.ok(Result.success(service.get(id, CurrentUser.require()))); }

    @GetMapping("/documents/{id}/download")
    public ResponseEntity<Resource> download(@PathVariable Long id) throws IOException {
        DocumentVO document = service.get(id, CurrentUser.require());
        FileSystemResource resource = new FileSystemResource(service.downloadPath(id, CurrentUser.require()));
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(document.fileName(), StandardCharsets.UTF_8).build().toString()).body(resource);
    }

    @DeleteMapping("/documents/{id}")
    public ResponseEntity<Result<Void>> delete(@PathVariable Long id) throws IOException { service.delete(id, CurrentUser.require()); return ResponseEntity.ok(Result.success()); }

    @PostMapping("/documents/{id}/reindex")
    public ResponseEntity<Result<DocumentVO>> reindex(@PathVariable Long id) { return ResponseEntity.accepted().body(Result.success(service.reindex(id, CurrentUser.require()))); }

}
