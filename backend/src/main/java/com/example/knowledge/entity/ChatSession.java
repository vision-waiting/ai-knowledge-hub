package com.example.knowledge.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("chat_session")
public class ChatSession {
    @TableId(type = IdType.AUTO) private Long id; private Long userId; private Long knowledgeBaseId; private String title; private LocalDateTime createdAt; private LocalDateTime updatedAt;
    public Long getId(){return id;} public void setId(Long v){id=v;} public Long getUserId(){return userId;} public void setUserId(Long v){userId=v;}
    public Long getKnowledgeBaseId(){return knowledgeBaseId;} public void setKnowledgeBaseId(Long v){knowledgeBaseId=v;} public String getTitle(){return title;} public void setTitle(String v){title=v;}
    public LocalDateTime getCreatedAt(){return createdAt;} public void setCreatedAt(LocalDateTime v){createdAt=v;} public LocalDateTime getUpdatedAt(){return updatedAt;} public void setUpdatedAt(LocalDateTime v){updatedAt=v;}
}

