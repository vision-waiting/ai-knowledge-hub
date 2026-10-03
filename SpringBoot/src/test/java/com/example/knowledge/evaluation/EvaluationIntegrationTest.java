package com.example.knowledge.evaluation;

import com.example.knowledge.entity.*;
import com.example.knowledge.mapper.*;
import com.example.knowledge.rag.*;
import com.example.knowledge.security.JwtTokenProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test") @Transactional
class EvaluationIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired SysUserMapper users;
    @Autowired KnowledgeBaseMapper bases;
    @Autowired DocumentMapper documents;
    @Autowired JwtTokenProvider tokens;
    @MockBean HybridRetriever retriever;

    @Test void debugExposesFinalScoresAndRejectsSharedReaders() throws Exception {
        SysUser owner = user(); KnowledgeBase kb = base(owner); Document d = doc(kb);
        when(retriever.retrieve(kb.getId(), "question", 3)).thenReturn(new RetrievalResult(List.of(hit(d, 1L)), true));
        mvc.perform(post(url(kb, "debug")).header("Authorization", auth(owner)).contentType("application/json")
            .content("{\"question\":\"question\",\"topK\":3}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.chunks[0].hybridScore").value(.76))
            .andExpect(jsonPath("$.data.chunks[0].documentName").value("evidence.txt"))
            .andExpect(jsonPath("$.data.sufficient").value(true));
        mvc.perform(post(url(kb,"debug")).header("Authorization",auth(user())).contentType("application/json")
            .content("{\"question\":\"question\",\"topK\":3}")).andExpect(status().isForbidden());
    }
    @Test void aggregatesChunkRankMetricsSeparatelyFromUnanswerableCases() throws Exception {
        SysUser owner=user(); KnowledgeBase kb=base(owner); Document first=doc(kb), expected=doc(kb);
        when(retriever.retrieve(kb.getId(),"answer",3)).thenReturn(new RetrievalResult(List.of(hit(first,1L),hit(expected,2L)),true));
        when(retriever.retrieve(kb.getId(),"unknown",3)).thenReturn(new RetrievalResult(List.of(),false));
        mvc.perform(post(url(kb,"evaluate")).header("Authorization",auth(owner)).contentType("application/json")
            .content("{\"topK\":3,\"cases\":[{\"question\":\"answer\",\"expectedDocumentIds\":["+expected.getId()+"]},{\"question\":\"unknown\",\"expectedDocumentIds\":[]}]}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.hitAtK").value(1.0))
            .andExpect(jsonPath("$.data.mrr").value(.5)).andExpect(jsonPath("$.data.sourceRecall").value(1.0))
            .andExpect(jsonPath("$.data.unanswerableCount").value(1)).andExpect(jsonPath("$.data.unanswerableRejectionRate").value(1.0));
    }
    @Test void validatesAllCasesBeforeAnyEmbeddingAndRejectsOutOfScopeDocuments() throws Exception {
        SysUser owner=user(); KnowledgeBase kb=base(owner); Document foreign=doc(base(owner));
        mvc.perform(post(url(kb,"evaluate")).header("Authorization",auth(owner)).contentType("application/json")
            .content("{\"topK\":3,\"cases\":[{\"question\":\"ok\",\"expectedDocumentIds\":[]},{\"question\":\"bad\",\"expectedDocumentIds\":["+foreign.getId()+"]}]}"))
            .andExpect(status().isBadRequest());
        mvc.perform(post(url(kb,"debug")).header("Authorization",auth(owner)).contentType("application/json")
            .content("{\"question\":\"ok\",\"topK\":21}")).andExpect(status().isBadRequest());
        verifyNoInteractions(retriever);
    }
    @Test void rejectsUnauthenticatedAndOversizedOrMalformedBatchesWithoutRetrieval() throws Exception {
        SysUser owner=user(); KnowledgeBase kb=base(owner);
        mvc.perform(post(url(kb,"debug")).contentType("application/json").content("{\"question\":\"ok\",\"topK\":3}"))
            .andExpect(status().isUnauthorized());
        for (String body : List.of(
                "{\"topK\":3,\"cases\":[]}",
                "{\"topK\":3,\"cases\":[null]}",
                "{\"topK\":3,\"cases\":[{\"question\":\"ok\",\"expectedDocumentIds\":[null]}]}",
                "{\"topK\":3,\"cases\":[{\"question\":\" \",\"expectedDocumentIds\":[]}]}",
                "{\"topK\":3,\"cases\":["+String.join(",",java.util.Collections.nCopies(31,"{\"question\":\"ok\",\"expectedDocumentIds\":[]}"))+"]}")) {
            mvc.perform(post(url(kb,"evaluate")).header("Authorization",auth(owner)).contentType("application/json").content(body))
                .andExpect(status().isBadRequest());
        }
        verifyNoInteractions(retriever);
    }
    @Test void missingAnswerableCasesProducesNullMetricsAndRepeatedChunksDoNotInflateSourceRecall() throws Exception {
        SysUser owner=user(); KnowledgeBase kb=base(owner); Document expected=doc(kb), missing=doc(kb);
        when(retriever.retrieve(kb.getId(),"unknown",3)).thenReturn(new RetrievalResult(List.of(hit(expected,1L)),true));
        mvc.perform(post(url(kb,"evaluate")).header("Authorization",auth(owner)).contentType("application/json")
            .content("{\"topK\":3,\"cases\":[{\"question\":\"unknown\",\"expectedDocumentIds\":[]}]}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.hitAtK").doesNotExist())
            .andExpect(jsonPath("$.data.mrr").doesNotExist()).andExpect(jsonPath("$.data.sourceRecall").doesNotExist())
            .andExpect(jsonPath("$.data.unanswerableRejectionRate").value(0.0));
        when(retriever.retrieve(kb.getId(),"answer",3)).thenReturn(new RetrievalResult(List.of(hit(expected,1L),hit(expected,2L)),true));
        mvc.perform(post(url(kb,"evaluate")).header("Authorization",auth(owner)).contentType("application/json")
            .content("{\"topK\":3,\"cases\":[{\"question\":\"answer\",\"expectedDocumentIds\":["+expected.getId()+","+expected.getId()+","+missing.getId()+"]}]}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.sourceRecall").value(.5))
            .andExpect(jsonPath("$.data.unanswerableRejectionRate").doesNotExist());
    }
    private RetrievedChunk hit(Document d,Long id){ DocumentChunk c=new DocumentChunk();c.setId(id);c.setDocumentId(d.getId());c.setKnowledgeBaseId(d.getKnowledgeBaseId());c.setContent("evidence");c.setPageNumber(1); return new RetrievedChunk(c,.8,.6666666667,.76); }
    private String url(KnowledgeBase b,String action){return "/api/knowledge-bases/"+b.getId()+"/retrieval/"+action;}
    private String auth(SysUser u){return "Bearer "+tokens.createToken(u);}
    private SysUser user(){SysUser u=new SysUser();u.setUsername(java.util.UUID.randomUUID().toString());u.setPassword("unused");u.setRole("USER");u.setStatus(1);users.insert(u);return u;}
    private KnowledgeBase base(SysUser u){KnowledgeBase b=new KnowledgeBase();b.setName("eval");b.setOwnerId(u.getId());b.setStatus(1);b.setShared(true);b.setDocumentCount(0);bases.insert(b);return b;}
    private Document doc(KnowledgeBase b){Document d=new Document();d.setKnowledgeBaseId(b.getId());d.setFileName("evidence.txt");d.setFileType("txt");d.setFilePath("evidence.txt");d.setFileSize(1L);d.setStatus("SUCCESS");documents.insert(d);return d;}
}
