package com.example.knowledge.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("document_chunk")
public class DocumentChunk {
    @TableId(type = IdType.AUTO) private Long id;
    private Long documentId; private Long knowledgeBaseId; private String content; private Integer chunkIndex;
    private Integer pageNumber; private Integer tokenCount; private String vectorId; private LocalDateTime createdAt;
    public Long getId(){return id;} public void setId(Long v){id=v;}
    public Long getDocumentId(){return documentId;} public void setDocumentId(Long v){documentId=v;}
    public Long getKnowledgeBaseId(){return knowledgeBaseId;} public void setKnowledgeBaseId(Long v){knowledgeBaseId=v;}
    public String getContent(){return content;} public void setContent(String v){content=v;}
    public Integer getChunkIndex(){return chunkIndex;} public void setChunkIndex(Integer v){chunkIndex=v;}
    public Integer getPageNumber(){return pageNumber;} public void setPageNumber(Integer v){pageNumber=v;}
    public Integer getTokenCount(){return tokenCount;} public void setTokenCount(Integer v){tokenCount=v;}
    public String getVectorId(){return vectorId;} public void setVectorId(String v){vectorId=v;}
    public LocalDateTime getCreatedAt(){return createdAt;} public void setCreatedAt(LocalDateTime v){createdAt=v;}
}

