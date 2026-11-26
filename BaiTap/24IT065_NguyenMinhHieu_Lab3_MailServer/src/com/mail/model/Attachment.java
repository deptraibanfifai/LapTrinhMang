// File: src/com/mail/model/Attachment.java
package com.mail.model;

import java.sql.Timestamp;

public class Attachment {
    private int id;
    private int emailId;
    private String fileName;
    private String filePath;
    private long fileSize;
    private String fileType;
    private Timestamp uploadedAt;

    // Constructors
    public Attachment() {}

    public Attachment(int emailId, String fileName, String filePath, long fileSize, String fileType) {
        this.emailId = emailId;
        this.fileName = fileName;
        this.filePath = filePath;
        this.fileSize = fileSize;
        this.fileType = fileType;
    }

    // Getters & Setters
    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public int getEmailId() { return emailId; }
    public void setEmailId(int emailId) { this.emailId = emailId; }

    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }

    public String getFilePath() { return filePath; }
    public void setFilePath(String filePath) { this.filePath = filePath; }

    public long getFileSize() { return fileSize; }
    public void setFileSize(long fileSize) { this.fileSize = fileSize; }

    public String getFileType() { return fileType; }
    public void setFileType(String fileType) { this.fileType = fileType; }

    public Timestamp getUploadedAt() { return uploadedAt; }
    public void setUploadedAt(Timestamp uploadedAt) { this.uploadedAt = uploadedAt; }

    @Override
    public String toString() {
        return "Attachment{fileName='" + fileName + "', size=" + fileSize + " bytes}";
    }
}