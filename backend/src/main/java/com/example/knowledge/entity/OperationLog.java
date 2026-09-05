package com.example.knowledge.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("operation_log")
public class OperationLog {
    @TableId(type = IdType.AUTO) private Long id; private Long userId; private String operation; private String method; private String requestUri; private String ip; private Integer success; private LocalDateTime createdAt;
    public Long getId(){return id;} public void setId(Long v){id=v;} public Long getUserId(){return userId;} public void setUserId(Long v){userId=v;} public String getOperation(){return operation;} public void setOperation(String v){operation=v;} public String getMethod(){return method;} public void setMethod(String v){method=v;} public String getRequestUri(){return requestUri;} public void setRequestUri(String v){requestUri=v;} public String getIp(){return ip;} public void setIp(String v){ip=v;} public Integer getSuccess(){return success;} public void setSuccess(Integer v){success=v;} public LocalDateTime getCreatedAt(){return createdAt;} public void setCreatedAt(LocalDateTime v){createdAt=v;}
}

