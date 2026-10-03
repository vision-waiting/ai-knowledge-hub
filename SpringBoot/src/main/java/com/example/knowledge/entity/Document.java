package com.example.knowledge.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("document")
public class Document {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long versionRootId;
    private Long previousVersionId;
    private Integer versionNumber;
    private LocalDateTime deletedAt;
    private String deletedStatus;
    public Long getVersionRootId(){return versionRootId;} public void setVersionRootId(Long v){versionRootId=v;}
    public Long getPreviousVersionId(){return previousVersionId;} public void setPreviousVersionId(Long v){previousVersionId=v;}
    public Integer getVersionNumber(){return versionNumber;} public void setVersionNumber(Integer v){versionNumber=v;}
    public LocalDateTime getDeletedAt(){return deletedAt;} public void setDeletedAt(LocalDateTime v){deletedAt=v;}
    public String getDeletedStatus(){return deletedStatus;} public void setDeletedStatus(String v){deletedStatus=v;}
    private Long knowledgeBaseId;
    private String fileName;
    private String filePath;
    private String fileType;
    private Long fileSize;
    private Integer pageCount;
    private String status;
    private String errorMessage;
    private String summary;
    private String summaryMode;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    public Long getId(){return id;} public void setId(Long v){id=v;}
    public Long getKnowledgeBaseId(){return knowledgeBaseId;} public void setKnowledgeBaseId(Long v){knowledgeBaseId=v;}
    public String getFileName(){return fileName;} public void setFileName(String v){fileName=v;}
    public String getFilePath(){return filePath;} public void setFilePath(String v){filePath=v;}
    public String getFileType(){return fileType;} public void setFileType(String v){fileType=v;}
    public Long getFileSize(){return fileSize;} public void setFileSize(Long v){fileSize=v;}
    public Integer getPageCount(){return pageCount;} public void setPageCount(Integer v){pageCount=v;}
    public String getStatus(){return status;} public void setStatus(String v){status=v;}
    public String getErrorMessage(){return errorMessage;} public void setErrorMessage(String v){errorMessage=v;}
    public String getSummary(){return summary;} public void setSummary(String v){summary=v;}
    public String getSummaryMode(){return summaryMode;} public void setSummaryMode(String v){summaryMode=v;}
    public LocalDateTime getCreatedAt(){return createdAt;} public void setCreatedAt(LocalDateTime v){createdAt=v;}
    public LocalDateTime getUpdatedAt(){return updatedAt;} public void setUpdatedAt(LocalDateTime v){updatedAt=v;}
}

