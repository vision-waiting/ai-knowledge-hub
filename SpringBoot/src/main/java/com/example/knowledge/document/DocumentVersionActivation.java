package com.example.knowledge.document;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.example.knowledge.common.BusinessException;
import com.example.knowledge.common.ErrorCode;
import com.example.knowledge.entity.Document;
import com.example.knowledge.entity.KnowledgeBase;
import com.example.knowledge.mapper.DocumentMapper;
import com.example.knowledge.mapper.KnowledgeBaseMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Publishes a completed version atomically; retrieval never sees two current versions. */
@Service
public class DocumentVersionActivation {
    private final DocumentMapper documents;
    private final KnowledgeBaseMapper bases;
    public DocumentVersionActivation(DocumentMapper documents, KnowledgeBaseMapper bases) {
        this.documents = documents; this.bases = bases;
    }
    @Transactional
    public void activate(Document document) {
        if (bases.selectOne(new QueryWrapper<KnowledgeBase>().eq("id", document.getKnowledgeBaseId()).last("FOR UPDATE")) == null)
            throw new BusinessException(ErrorCode.NOT_FOUND);
        Document current = documents.selectOne(new QueryWrapper<Document>().eq("id", document.getId()).last("FOR UPDATE"));
        if (current == null || !"INDEXING".equals(current.getStatus())) throw new BusinessException(ErrorCode.CONFLICT);
        if (document.getPreviousVersionId() != null) {
            int archived = documents.update(null, new UpdateWrapper<Document>().eq("id", document.getPreviousVersionId())
                    .eq("knowledge_base_id", document.getKnowledgeBaseId()).eq("status", "SUCCESS")
                    .set("status", "ARCHIVED").set("updated_at", java.time.LocalDateTime.now()));
            if (archived != 1) throw new BusinessException(ErrorCode.CONFLICT, "原版本已不再是当前版本");
        }
        documents.update(null, new UpdateWrapper<Document>().eq("id", document.getId()).set("status", "SUCCESS")
                .set("error_message", null).set("updated_at", java.time.LocalDateTime.now()));
        document.setStatus("SUCCESS"); document.setErrorMessage(null);
    }
}
