package com.example.knowledge.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

@TableName("document_tag")
public class DocumentTag {
    @TableId(type = IdType.AUTO) private Long id;
    private Long documentId; private String tagName;
    public Long getId(){return id;} public void setId(Long v){id=v;}
    public Long getDocumentId(){return documentId;} public void setDocumentId(Long v){documentId=v;}
    public String getTagName(){return tagName;} public void setTagName(String v){tagName=v;}
}

