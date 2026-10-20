package com.example.knowledge.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("chat_message")
public class ChatMessage {
    @TableId(type = IdType.AUTO) private Long id; private Long sessionId; private String role; private String content; private Integer tokenCount; private LocalDateTime createdAt;
    private String mode;
    private String model;
    public String getModel(){return model;} public void setModel(String value){model=value;}
    private String citationsJson; private String feedback; private String feedbackReason; private String retrievalJson;
    public Long getId(){return id;} public void setId(Long v){id=v;} public Long getSessionId(){return sessionId;} public void setSessionId(Long v){sessionId=v;}
    public String getRole(){return role;} public void setRole(String v){role=v;} public String getContent(){return content;} public void setContent(String v){content=v;}
    public Integer getTokenCount(){return tokenCount;} public void setTokenCount(Integer v){tokenCount=v;} public LocalDateTime getCreatedAt(){return createdAt;} public void setCreatedAt(LocalDateTime v){createdAt=v;}
    public String getMode(){return mode;} public void setMode(String value){mode=value;}
    public String getCitationsJson(){return citationsJson;} public void setCitationsJson(String value){citationsJson=value;}
    public String getFeedback(){return feedback;} public void setFeedback(String value){feedback=value;}
    public String getFeedbackReason(){return feedbackReason;} public void setFeedbackReason(String value){feedbackReason=value;}
    public String getRetrievalJson(){return retrievalJson;} public void setRetrievalJson(String value){retrievalJson=value;}
}

