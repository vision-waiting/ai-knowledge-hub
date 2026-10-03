package com.example.knowledge.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.example.knowledge.common.BusinessException;
import com.example.knowledge.common.ErrorCode;
import com.example.knowledge.document.DocumentProcessWorker;
import com.example.knowledge.document.StorageService;
import com.example.knowledge.entity.Document;
import com.example.knowledge.entity.DocumentChunk;
import com.example.knowledge.entity.DocumentTag;
import com.example.knowledge.entity.KnowledgeBase;
import com.example.knowledge.mapper.DocumentChunkMapper;
import com.example.knowledge.mapper.DocumentMapper;
import com.example.knowledge.mapper.DocumentTagMapper;
import com.example.knowledge.mapper.KnowledgeBaseMapper;
import com.example.knowledge.rag.VectorStore;
import com.example.knowledge.security.JwtPrincipal;
import com.example.knowledge.vo.DocumentVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Files;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class DocumentService {
    private static final Logger log = LoggerFactory.getLogger(DocumentService.class);
    private static final Set<String> ACTIVE_STATUSES = Set.of("WAITING", "PARSING", "CHUNKING", "EMBEDDING", "INDEXING");
    private final DocumentMapper documentMapper; private final DocumentChunkMapper chunkMapper; private final DocumentTagMapper tagMapper;
    private final KnowledgeBaseMapper knowledgeBaseMapper; private final PermissionService permissionService; private final StorageService storageService;
    private final DocumentProcessWorker processWorker;
    private final VectorStore vectorStore;
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.example.knowledge.notifications.NotificationPublisher notificationPublisher;

    public DocumentService(DocumentMapper documentMapper, DocumentChunkMapper chunkMapper, DocumentTagMapper tagMapper,
                           KnowledgeBaseMapper knowledgeBaseMapper, PermissionService permissionService, StorageService storageService,
                           DocumentProcessWorker processWorker, VectorStore vectorStore) {
        this.documentMapper = documentMapper; this.chunkMapper = chunkMapper; this.tagMapper = tagMapper; this.knowledgeBaseMapper = knowledgeBaseMapper;
        this.permissionService = permissionService; this.storageService = storageService; this.processWorker = processWorker; this.vectorStore = vectorStore;
    }

    @Transactional
    public DocumentVO upload(Long knowledgeBaseId, MultipartFile file, JwtPrincipal user) throws IOException {
        permissionService.requireKnowledgeBaseAccess(knowledgeBaseId, user, true);
        lockKnowledgeBase(knowledgeBaseId);
        StorageService.StoredFile stored = storageService.save(knowledgeBaseId, file);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCompletion(int status) {
                if (status != STATUS_COMMITTED) deleteFile(stored.path());
            }
        });
        Document document = new Document(); document.setKnowledgeBaseId(knowledgeBaseId); document.setFileName(stored.originalName());
        document.setFilePath(stored.path()); document.setFileType(stored.extension()); document.setFileSize(file.getSize()); document.setStatus("WAITING");
        documentMapper.insert(document);
        knowledgeBaseMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<com.example.knowledge.entity.KnowledgeBase>()
                .eq("id", knowledgeBaseId).setSql("document_count = document_count + 1"));
        notifyUpdate(document, "UPLOADED");
        scheduleAfterCommit(document.getId());
        return toVO(document);
    }

    public List<DocumentVO> list(Long knowledgeBaseId, JwtPrincipal user) {
        permissionService.requireKnowledgeBaseAccess(knowledgeBaseId, user, false);
        return documentMapper.selectList(new QueryWrapper<Document>().eq("knowledge_base_id", knowledgeBaseId).notIn("status", "ARCHIVED", "TRASHED").orderByDesc("created_at"))
                .stream().map(this::toVO).toList();
    }

    public DocumentVO get(Long id, JwtPrincipal user) {
        Document document = find(id); permissionService.requireKnowledgeBaseAccess(document.getKnowledgeBaseId(), user, false); return toVO(document);
    }

    public Path downloadPath(Long id, JwtPrincipal user) {
        Document document = find(id);
        permissionService.requireKnowledgeBaseAccess(document.getKnowledgeBaseId(), user, false);
        Path path = storageService.resolve(document.getFilePath());
        if (!Files.isRegularFile(path)) throw new BusinessException(ErrorCode.NOT_FOUND, "原文件不存在，请重新上传");
        return path;
    }

    public List<ChunkVO> chunks(Long id, JwtPrincipal user) {
        Document document = find(id);
        permissionService.requireKnowledgeBaseAccess(document.getKnowledgeBaseId(), user, false);
        return chunkMapper.selectList(new QueryWrapper<DocumentChunk>().eq("document_id", id).orderByAsc("chunk_index"))
                .stream().map(chunk -> new ChunkVO(chunk.getId(), chunk.getDocumentId(), chunk.getChunkIndex(),
                        chunk.getPageNumber(), chunk.getTokenCount(), chunk.getContent())).toList();
    }

    @Transactional
    public void delete(Long id, JwtPrincipal user) throws IOException {
        Document document = find(id); permissionService.requireKnowledgeBaseAccess(document.getKnowledgeBaseId(), user, true);
        lockKnowledgeBase(document.getKnowledgeBaseId());
        document = lockDocument(id);
        requireIdle(document);
        requireNoPendingVersion(document);
        if ("ARCHIVED".equals(document.getStatus()) || "TRASHED".equals(document.getStatus()))
            throw new BusinessException(ErrorCode.CONFLICT, "只能将当前文档移入回收站");
        boolean counted = document.getPreviousVersionId() == null || "SUCCESS".equals(document.getStatus());
        document.setDeletedStatus(document.getStatus());
        document.setDeletedAt(java.time.LocalDateTime.now());
        document.setStatus("TRASHED");
        documentMapper.updateById(document);
        if (counted) knowledgeBaseMapper.update(null, new UpdateWrapper<KnowledgeBase>()
                .eq("id", document.getKnowledgeBaseId()).setSql("document_count = GREATEST(document_count - 1, 0)"));
        notifyUpdate(document, "DELETED");
    }

    @Transactional
    public DocumentVO reindex(Long id, JwtPrincipal user) {
        Document document = find(id); permissionService.requireKnowledgeBaseAccess(document.getKnowledgeBaseId(), user, true);
        lockKnowledgeBase(document.getKnowledgeBaseId());
        document = lockDocument(id);
        requireIdle(document);
        requireLive(document);
        if ("SUCCESS".equals(document.getStatus())) {
            requireNoPendingVersion(document);
            Document next = newVersion(document);
            next.setFileName(document.getFileName()); next.setFilePath(document.getFilePath());
            next.setFileType(document.getFileType()); next.setFileSize(document.getFileSize());
            documentMapper.insert(next); notifyUpdate(next, "REINDEXED"); scheduleAfterCommit(next.getId()); return toVO(next);
        }
        requireNoOtherPendingVersion(document);
        document.setStatus("WAITING"); document.setErrorMessage(null); document.setSummary(null);
        documentMapper.update(null, new UpdateWrapper<Document>().eq("id", id).set("status", "WAITING")
                .set("error_message", null).set("summary", null).set("updated_at", java.time.LocalDateTime.now()));
        notifyUpdate(document, "REINDEXED");
        scheduleAfterCommit(id);
        return toVO(document);
    }

    public List<DocumentVO> trash(Long knowledgeBaseId, JwtPrincipal user) {
        permissionService.requireKnowledgeBaseAccess(knowledgeBaseId, user, true);
        return documentMapper.selectList(new QueryWrapper<Document>().eq("knowledge_base_id", knowledgeBaseId)
                .eq("status", "TRASHED").orderByDesc("deleted_at")).stream().map(this::toVO).toList();
    }

    public List<VersionVO> versions(Long id, JwtPrincipal user) {
        Document document = find(id);
        permissionService.requireKnowledgeBaseAccess(document.getKnowledgeBaseId(), user, false);
        return documentMapper.selectList(family(document).orderByDesc("version_number", "id")).stream()
                .map(d -> new VersionVO(toVO(d), d.getVersionNumber() == null ? 1 : d.getVersionNumber(),
                        d.getPreviousVersionId(), d.getDeletedAt())).toList();
    }

    @Transactional
    public DocumentVO uploadVersion(Long id, MultipartFile file, JwtPrincipal user) throws IOException {
        Document previous = find(id);
        permissionService.requireKnowledgeBaseAccess(previous.getKnowledgeBaseId(), user, true);
        lockKnowledgeBase(previous.getKnowledgeBaseId()); previous = lockDocument(id);
        if (!"SUCCESS".equals(previous.getStatus())) throw new BusinessException(ErrorCode.CONFLICT, "只能为当前已完成文档上传新版本");
        requireNoPendingVersion(previous);
        StorageService.StoredFile stored = storageService.save(previous.getKnowledgeBaseId(), file);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCompletion(int status) { if (status != STATUS_COMMITTED) deleteFile(stored.path()); }
        });
        Document next = newVersion(previous);
        next.setFileName(stored.originalName()); next.setFilePath(stored.path());
        next.setFileType(stored.extension()); next.setFileSize(file.getSize());
        documentMapper.insert(next); scheduleAfterCommit(next.getId());
        notifyUpdate(next, "UPLOADED");
        return toVO(next);
    }

    @Transactional
    public DocumentVO restore(Long id, JwtPrincipal user) {
        Document document = find(id);
        permissionService.requireKnowledgeBaseAccess(document.getKnowledgeBaseId(), user, true);
        lockKnowledgeBase(document.getKnowledgeBaseId()); document = lockDocument(id);
        if (!"TRASHED".equals(document.getStatus())) throw new BusinessException(ErrorCode.CONFLICT, "文档不在回收站中");
        if (documentMapper.selectCount(family(document).ne("id", id).in("status", "SUCCESS", "WAITING", "PARSING", "CHUNKING", "EMBEDDING", "INDEXING")) > 0)
            throw new BusinessException(ErrorCode.CONFLICT, "该文档已有当前版本或正在处理的版本，无法恢复");
        String status = document.getDeletedStatus();
        if (!"SUCCESS".equals(status) && !"FAILED".equals(status)) throw new BusinessException(ErrorCode.CONFLICT, "无法恢复此文档状态");
        documentMapper.update(null, new UpdateWrapper<Document>().eq("id", id).set("status", status)
                .set("deleted_at", null).set("deleted_status", null).set("updated_at", java.time.LocalDateTime.now()));
        document.setStatus(status); document.setDeletedAt(null); document.setDeletedStatus(null);
        if (document.getPreviousVersionId() == null || "SUCCESS".equals(status)) knowledgeBaseMapper.update(null,
                new UpdateWrapper<KnowledgeBase>().eq("id", document.getKnowledgeBaseId()).setSql("document_count = document_count + 1"));
        notifyUpdate(document, "RESTORED"); return toVO(document);
    }

    private QueryWrapper<Document> family(Document document) {
        Long root = document.getVersionRootId() == null ? document.getId() : document.getVersionRootId();
        return new QueryWrapper<Document>().eq("knowledge_base_id", document.getKnowledgeBaseId())
                .and(q -> q.eq("id", root).or().eq("version_root_id", root));
    }

    private Document newVersion(Document previous) {
        Document next = new Document(); next.setKnowledgeBaseId(previous.getKnowledgeBaseId());
        next.setVersionRootId(previous.getVersionRootId() == null ? previous.getId() : previous.getVersionRootId());
        next.setPreviousVersionId(previous.getId()); next.setStatus("WAITING");
        List<Document> existing = documentMapper.selectList(family(previous));
        next.setVersionNumber(existing.stream().mapToInt(d -> d.getVersionNumber() == null ? 1 : d.getVersionNumber()).max().orElse(1) + 1);
        return next;
    }

    private void requireNoPendingVersion(Document document) {
        if (documentMapper.selectCount(family(document).in("status", ACTIVE_STATUSES)) > 0)
            throw new BusinessException(ErrorCode.CONFLICT, "该文档已有正在处理的版本");
    }
    private void requireNoOtherPendingVersion(Document document) {
        if (documentMapper.selectCount(family(document).ne("id", document.getId()).in("status", ACTIVE_STATUSES)) > 0)
            throw new BusinessException(ErrorCode.CONFLICT, "该文档已有正在处理的版本");
        if (document.getPreviousVersionId() != null) {
            Document previous = find(document.getPreviousVersionId());
            if (!"SUCCESS".equals(previous.getStatus())) throw new BusinessException(ErrorCode.CONFLICT, "原版本已不再是当前版本");
        }
    }
    private void requireLive(Document document) {
        if ("ARCHIVED".equals(document.getStatus()) || "TRASHED".equals(document.getStatus()))
            throw new BusinessException(ErrorCode.CONFLICT, "历史或回收站文档不能重新索引");
    }
    public record VersionVO(DocumentVO document, int versionNumber, Long previousVersionId, java.time.LocalDateTime deletedAt) {}
    public void deleteKnowledgeBaseDocuments(Long knowledgeBaseId) {
        List<Document> documents = documentMapper.selectList(new QueryWrapper<Document>().eq("knowledge_base_id", knowledgeBaseId).last("FOR UPDATE"));
        documents.forEach(this::requireIdle);
        vectorStore.deleteByKnowledgeBase(knowledgeBaseId);
        chunkMapper.delete(new QueryWrapper<DocumentChunk>().eq("knowledge_base_id", knowledgeBaseId));
        for (Document document : documents) {
            tagMapper.delete(new QueryWrapper<DocumentTag>().eq("document_id", document.getId()));
            deleteFileAfterCommit(document.getFilePath());
        }
        documentMapper.delete(new QueryWrapper<Document>().eq("knowledge_base_id", knowledgeBaseId));
    }

    private void lockKnowledgeBase(Long id) {
        if (knowledgeBaseMapper.selectOne(new QueryWrapper<KnowledgeBase>().eq("id", id).last("FOR UPDATE")) == null)
            throw new BusinessException(ErrorCode.NOT_FOUND);
    }

    private Document lockDocument(Long id) {
        Document document = documentMapper.selectOne(new QueryWrapper<Document>().eq("id", id).last("FOR UPDATE"));
        if (document == null) throw new BusinessException(ErrorCode.NOT_FOUND);
        return document;
    }

    private void requireIdle(Document document) {
        if (ACTIVE_STATUSES.contains(document.getStatus())) throw new BusinessException(ErrorCode.CONFLICT);
    }

    private void scheduleAfterCommit(Long id) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() { processWorker.processAsync(id); }
        });
    }

    private void notifyUpdate(Document document, String type) {
        if (notificationPublisher != null) notificationPublisher.documentChanged(document, type);
    }

    private void deleteFileAfterCommit(String path) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() { deleteFile(path); }
        });
    }

    private void deleteFile(String path) {
        try { storageService.delete(path); }
        // Cleanup runs after commit (or rollback); it must not change the completed transaction's result.
        catch (IOException | RuntimeException exception) { log.warn("Could not delete document file {}", path, exception); }
    }

    private Document find(Long id) { Document d = documentMapper.selectById(id); if (d == null) throw new BusinessException(ErrorCode.NOT_FOUND); return d; }
    private DocumentVO toVO(Document d) {
        List<String> tags = tagMapper.selectList(new QueryWrapper<DocumentTag>().eq("document_id", d.getId()).orderByAsc("id"))
                .stream().map(DocumentTag::getTagName).toList();
        return new DocumentVO(d.getId(), d.getKnowledgeBaseId(), d.getFileName(), d.getFileType(), d.getFileSize(), d.getPageCount(),
                d.getStatus(), d.getErrorMessage(), d.getSummary(), d.getCreatedAt(), tags,
                d.getSummaryMode() == null ? "EXTRACTIVE" : d.getSummaryMode(),
                "OPENAI_COMPATIBLE".equals(d.getSummaryMode()) ? "OPENAI_COMPATIBLE" : "KEYWORD",
                "pdf".equals(d.getFileType()) || "pptx".equals(d.getFileType()) ? "PAGE" : "SECTION");
    }

    public record ChunkVO(Long id, Long documentId, Integer chunkIndex, Integer pageNumber, Integer tokenCount, String content) {}
}
