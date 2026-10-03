package com.example.knowledge.service;

import com.example.knowledge.common.BusinessException;
import com.example.knowledge.entity.KnowledgeBase;
import com.example.knowledge.mapper.KnowledgeBaseMapper;
import com.example.knowledge.mapper.ChatSessionMapper;
import com.example.knowledge.mapper.ChatMessageMapper;
import com.example.knowledge.security.JwtPrincipal;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class KnowledgeBaseServiceTest {
    @Test
    void ownerCanCreateAndReadOwnKnowledgeBase() {
        KnowledgeBaseMapper mapper = mock(KnowledgeBaseMapper.class);
        KnowledgeBase saved = new KnowledgeBase();
        saved.setId(1L); saved.setName("Java"); saved.setOwnerId(7L); saved.setStatus(1);
        when(mapper.insert(org.mockito.ArgumentMatchers.<KnowledgeBase>any())).thenAnswer(invocation -> { ((KnowledgeBase) invocation.getArgument(0)).setId(1L); return 1; });
        when(mapper.selectById(1L)).thenReturn(saved);
        KnowledgeBaseService service = service(mapper);

        var created = service.create(new com.example.knowledge.dto.KnowledgeBaseRequest("Java", "基础资料"), new JwtPrincipal(7L, "student", "USER"));
        var found = service.get(1L, new JwtPrincipal(7L, "student", "USER"));

        assertEquals("Java", created.name());
        assertEquals(1L, found.id());
    }

    @Test
    void otherUserCannotReadKnowledgeBase() {
        KnowledgeBase base = new KnowledgeBase();
        base.setId(1L); base.setName("Java"); base.setOwnerId(7L); base.setStatus(1);
        KnowledgeBaseMapper mapper = mock(KnowledgeBaseMapper.class);
        when(mapper.selectById(1L)).thenReturn(base);
        KnowledgeBaseService service = service(mapper);

        assertThrows(BusinessException.class, () -> service.get(1L, new JwtPrincipal(8L, "other", "USER")));
    }

    @Test
    void adminCanListAllKnowledgeBases() {
        KnowledgeBaseMapper mapper = mock(KnowledgeBaseMapper.class);
        when(mapper.selectList(any())).thenReturn(List.of(new KnowledgeBase(), new KnowledgeBase()));
        KnowledgeBaseService service = service(mapper);

        assertEquals(2, service.list(new JwtPrincipal(99L, "admin", "ADMIN"), null).size());
    }

    private KnowledgeBaseService service(KnowledgeBaseMapper mapper) {
        var users = mock(com.example.knowledge.mapper.SysUserMapper.class);
        return new KnowledgeBaseService(mapper, mock(DocumentService.class), mock(ChatSessionMapper.class), mock(ChatMessageMapper.class), new PermissionService(mapper, users), users, mock(org.springframework.jdbc.core.JdbcTemplate.class));
    }
}
