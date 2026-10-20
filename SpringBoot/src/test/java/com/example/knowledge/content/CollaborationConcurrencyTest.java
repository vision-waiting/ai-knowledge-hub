package com.example.knowledge.content;

import com.example.knowledge.common.BusinessException;
import com.example.knowledge.notifications.NotificationPublisher;
import com.example.knowledge.security.JwtPrincipal;
import com.example.knowledge.service.PermissionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Models MySQL RR: a snapshot read may differ from the committed locking read. */
class CollaborationConcurrencyTest {
    @Test void mergeMustNotOverrideAConcurrentWithdrawalVisibleToTheLockingRead() {
        var jdbc=mock(JdbcTemplate.class);
        var service=spy(new CollaborationService(jdbc,mock(PermissionService.class),new ObjectMapper(),mock(NotificationPublisher.class)));
        var stale=new CollaborationService.Suggestion(9,4,1,2,"Reader","Old","Before","New","After","Fix","OPEN",null,"",null);
        doReturn(new ContentController.Entry(4,"FAQ","Old","Before",null,null,0,"",1)).when(service).content(1,4,true);
        doReturn(new ContentController.Entry(4,"FAQ","New","After",null,null,0,"",2)).when(service).content(1,4,false);
        doNothing().when(service).baseline(anyLong(),any());doNothing().when(service).record(anyLong(),any(),any(),any(),anyString());
        when(jdbc.query(contains("FROM content_suggestion"),any(RowMapper.class),eq(1L),eq(9L)))
                .thenReturn(List.of(stale));
        when(jdbc.queryForList(contains("FOR UPDATE"),eq(1L),eq(9L))).thenReturn(List.of(Map.of("status","CLOSED")));
        // The transaction's old snapshot says OPEN; the latest locked row says CLOSED.
        var failure=assertThrows(BusinessException.class,()->service.merge(1,9,new JwtPrincipal(1L,"owner","USER")));
        assertEquals(409,failure.errorCode().code());
        verify(jdbc,never()).update(startsWith("UPDATE knowledge_content"),any(Object[].class));
    }
    @Test void baselineUsesLatestCommittedRevisionAfterWaitingForContentLock() {
        var jdbc=mock(JdbcTemplate.class);
        var service=new CollaborationService(jdbc,mock(PermissionService.class),new ObjectMapper(),mock(NotificationPublisher.class));
        when(jdbc.queryForObject(anyString(),eq(Long.class),eq(4L),eq(2))).thenReturn(0L);
        when(jdbc.queryForList(contains("FROM content_revision"),eq(4L),eq(2))).thenReturn(List.of(Map.of("id",6L)));
        service.baseline(1,new ContentController.Entry(4,"FAQ","Current","Body",null,null,0,"",2));
        verify(jdbc,never()).update(startsWith("INSERT INTO content_revision"),any(Object[].class));
    }
}
