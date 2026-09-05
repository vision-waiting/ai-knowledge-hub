package com.example.knowledge.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.example.knowledge.common.BusinessException;
import com.example.knowledge.common.ErrorCode;
import com.example.knowledge.document.DocumentProcessService;
import com.example.knowledge.document.StorageService;
import com.example.knowledge.entity.Document;
import com.example.knowledge.mapper.DocumentChunkMapper;
import com.example.knowledge.mapper.DocumentMapper;
import com.example.knowledge.mapper.DocumentTagMapper;
import com.example.knowledge.mapper.KnowledgeBaseMapper;
import com.example.knowledge.security.JwtPrincipal;
import com.example.knowledge.vo.DocumentVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

@Service
public class DocumentService {
    private final DocumentMapper documentMapper; private final DocumentChunkMapper chunkMapper; private final DocumentTagMapper tagMapper;
    private final KnowledgeBaseMapper knowledgeBaseMapper; private final PermissionService permissionService; private final StorageService storageService;
    private final DocumentProcessService processService;

    public DocumentService(DocumentMapper documentMapper, DocumentChunkMapper chunkMapper, DocumentTagMapper tagMapper,
                           KnowledgeBaseMapper knowledgeBaseMapper, PermissionService permissionService, StorageService storageService,
                           DocumentProcessService processService) {
        this.documentMapper = documentMapper; this.chunkMapper = chunkMapper; this.tagMapper = tagMapper; this.knowledgeBaseMapper = knowledgeBaseMapper;
        this.permissionService = permissionService; this.storageService = storageService; this.processService = processService;
    }

    @Transactional
    public DocumentVO upload(Long knowledgeBaseId, MultipartFile file, JwtPrincipal user) throws IOException {
        permissionService.requireKnowledgeBaseAccess(knowledgeBaseId, user, true);
        StorageService.StoredFile stored = storageService.save(knowledgeBaseId, file);
        Document document = new Document(); document.setKnowledgeBaseId(knowledgeBaseId); document.setFileName(stored.originalName());
        document.setFilePath(stored.path()); document.setFileType(stored.extension()); document.setFileSize(file.getSize()); document.setStatus("WAITING");
        documentMapper.insert(document);
        knowledgeBaseMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<com.example.knowledge.entity.KnowledgeBase>()
                .eq("id", knowledgeBaseId).setSql("document_count = document_count + 1"));
        processService.processAsync(document.getId());
        return toVO(document);
    }

    public List<DocumentVO> list(Long knowledgeBaseId, JwtPrincipal user) {
        permissionService.requireKnowledgeBaseAccess(knowledgeBaseId, user, false);
        return documentMapper.selectList(new QueryWrapper<Document>().eq("knowledge_base_id", knowledgeBaseId).orderByDesc("created_at"))
                .stream().map(this::toVO).toList();
    }

    public DocumentVO get(Long id, JwtPrincipal user) {
        Document document = find(id); permissionService.requireKnowledgeBaseAccess(document.getKnowledgeBaseId(), user, false); return toVO(document);
    }

    public Path downloadPath(Long id, JwtPrincipal user) {
        Document document = find(id);
        permissionService.requireKnowledgeBaseAccess(document.getKnowledgeBaseId(), user, false);
        return Path.of(document.getFilePath()).toAbsolutePath().normalize();
    }

    @Transactional
    public void delete(Long id, JwtPrincipal user) throws IOException {
        Document document = find(id); permissionService.requireKnowledgeBaseAccess(document.getKnowledgeBaseId(), user, true);
        chunkMapper.delete(new QueryWrapper<com.example.knowledge.entity.DocumentChunk>().eq("document_id", id));
        tagMapper.delete(new QueryWrapper<com.example.knowledge.entity.DocumentTag>().eq("document_id", id));
        documentMapper.deleteById(id); storageService.delete(document.getFilePath());
        knowledgeBaseMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<com.example.knowledge.entity.KnowledgeBase>()
                .eq("id", document.getKnowledgeBaseId()).setSql("document_count = GREATEST(document_count - 1, 0)"));
    }

    @Transactional
    public DocumentVO reindex(Long id, JwtPrincipal user) {
        Document document = find(id); permissionService.requireKnowledgeBaseAccess(document.getKnowledgeBaseId(), user, true);
        document.setStatus("WAITING"); document.setErrorMessage(null); documentMapper.updateById(document); processService.processAsync(id); return toVO(document);
    }

    private Document find(Long id) { Document d = documentMapper.selectById(id); if (d == null) throw new BusinessException(ErrorCode.NOT_FOUND); return d; }
    private DocumentVO toVO(Document d) { return new DocumentVO(d.getId(), d.getKnowledgeBaseId(), d.getFileName(), d.getFileType(), d.getFileSize(), d.getPageCount(), d.getStatus(), d.getErrorMessage(), d.getSummary(), d.getCreatedAt()); }
}
