package com.example.knowledge.document;

import com.example.knowledge.common.BusinessException;
import com.example.knowledge.dto.KnowledgeBaseRequest;
import com.example.knowledge.security.JwtPrincipal;
import com.example.knowledge.service.DocumentService;
import com.example.knowledge.service.KnowledgeBaseService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class DocumentPipelineIntegrationTest {
    @Autowired KnowledgeBaseService bases;
    @Autowired DocumentService documents;
    @Autowired DocumentProcessService processor;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean DocumentProcessWorker worker;
    private final JwtPrincipal owner = new JwtPrincipal(809L, "pipeline-owner", "USER");

    @Test void deletingKnowledgeBaseRemovesChunksTagsChatRowsAndOriginalFile() throws Exception {
        var base = bases.create(new KnowledgeBaseRequest("Pipeline cleanup", ""), owner);
        var document = documents.upload(base.id(), new MockMultipartFile("file", "notes.txt", "text/plain", "Knowledge base cleanup evidence".getBytes(StandardCharsets.UTF_8)), owner);
        processor.process(document.id());
        var path = documents.downloadPath(document.id(), owner);
        jdbc.update("INSERT INTO chat_session (user_id, knowledge_base_id, title) VALUES (?, ?, ?)", owner.userId(), base.id(), "Cleanup session");
        Long sessionId = jdbc.queryForObject("SELECT id FROM chat_session WHERE knowledge_base_id = ?", Long.class, base.id());
        jdbc.update("INSERT INTO chat_message (session_id, role, content) VALUES (?, 'user', 'Question')", sessionId);
        bases.delete(base.id(), owner);
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM document WHERE id = ?", Integer.class, document.id()));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM document_chunk WHERE document_id = ?", Integer.class, document.id()));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM document_tag WHERE document_id = ?", Integer.class, document.id()));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM chat_session WHERE id = ?", Integer.class, sessionId));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM chat_message WHERE session_id = ?", Integer.class, sessionId));
        assertFalse(Files.exists(path));
    }

    @Test void reindexPreservesHistoricalChunksAndDetailRequiresKnowledgeBasePermission() throws Exception {
        var base = bases.create(new KnowledgeBaseRequest("Pipeline reindex", ""), owner);
        var document = documents.upload(base.id(), new MockMultipartFile("file", "notes.txt", "text/plain", "中文知识管理系统中的完整证据。\nEnglish supporting evidence.".getBytes(StandardCharsets.UTF_8)), owner);
        processor.process(document.id());
        int chunkCount = documents.chunks(document.id(), owner).size();
        assertTrue(chunkCount > 0);
        var next = documents.reindex(document.id(), owner);
        assertNotEquals(document.id(), next.id());
        processor.process(next.id());
        assertEquals(chunkCount, documents.chunks(document.id(), owner).size());
        assertEquals("ARCHIVED", documents.get(document.id(), owner).status());
        assertEquals("SUCCESS", documents.get(next.id(), owner).status());
        assertEquals(chunkCount, documents.chunks(next.id(), owner).size());
        assertFalse(documents.get(document.id(), owner).tags().isEmpty());
        assertThrows(BusinessException.class, () -> documents.chunks(document.id(), new JwtPrincipal(810L, "other", "USER")));
        bases.delete(base.id(), owner);
    }
}
