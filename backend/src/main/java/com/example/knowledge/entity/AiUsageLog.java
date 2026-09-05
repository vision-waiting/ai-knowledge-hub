package com.example.knowledge.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("ai_usage_log")
public class AiUsageLog {
    @TableId(type = IdType.AUTO) private Long id; private Long userId; private String mode; private String model; private Long durationMs; private Integer promptTokens; private Integer completionTokens; private Integer success; private String errorMessage; private LocalDateTime createdAt;
    public Long getId(){return id;} public void setId(Long v){id=v;} public Long getUserId(){return userId;} public void setUserId(Long v){userId=v;} public String getMode(){return mode;} public void setMode(String v){mode=v;} public String getModel(){return model;} public void setModel(String v){model=v;} public Long getDurationMs(){return durationMs;} public void setDurationMs(Long v){durationMs=v;} public Integer getPromptTokens(){return promptTokens;} public void setPromptTokens(Integer v){promptTokens=v;} public Integer getCompletionTokens(){return completionTokens;} public void setCompletionTokens(Integer v){completionTokens=v;} public Integer getSuccess(){return success;} public void setSuccess(Integer v){success=v;} public String getErrorMessage(){return errorMessage;} public void setErrorMessage(String v){errorMessage=v;} public LocalDateTime getCreatedAt(){return createdAt;} public void setCreatedAt(LocalDateTime v){createdAt=v;}
}

