// File: src/com/mail/service/FileService.java
package com.mail.service;

import com.mail.dao.EmailDAO;
import com.mail.model.Attachment;

import java.io.*;
import java.nio.file.*;
import java.util.List;

/**
 * Service quản lý file đính kèm
 * Tương tự Filezilla - upload/download file
 */
public class FileService {

    private static final String UPLOAD_DIR = "uploads/";
    private EmailDAO emailDAO = new EmailDAO();

    public FileService() {
        // Tạo thư mục uploads nếu chưa có
        File dir = new File(UPLOAD_DIR);
        if (!dir.exists()) {
            dir.mkdirs();
        }
    }

    /**
     * Upload file và lưu vào database
     */
    public Attachment uploadFile(int emailId, File file) {
        try {
            // Tạo tên file unique
            String uniqueFileName = System.currentTimeMillis() + "_" + file.getName();
            String destPath = UPLOAD_DIR + uniqueFileName;

            // Copy file
            Files.copy(file.toPath(), Paths.get(destPath), StandardCopyOption.REPLACE_EXISTING);

            // Tạo attachment object
            Attachment attachment = new Attachment(
                emailId,
                file.getName(),
                destPath,
                file.length(),
                getFileType(file.getName())
            );

            // Lưu vào database
            emailDAO.saveAttachment(attachment);
            System.out.println("[FileService] File đã upload: " + file.getName());

            return attachment;

        } catch (IOException e) {
            System.err.println("[FileService] Lỗi upload file: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Download file từ server
     */
    public File downloadFile(int attachmentId) {
        List<Attachment> allAttachments = getAllAttachments();
        for (Attachment att : allAttachments) {
            if (att.getId() == attachmentId) {
                File file = new File(att.getFilePath());
                if (file.exists()) {
                    System.out.println("[FileService] Download file: " + att.getFileName());
                    return file;
                }
            }
        }
        return null;
    }

    /**
     * Lấy danh sách tất cả attachments
     */
    public List<Attachment> getAllAttachments() {
        return emailDAO.getAllAttachments();
    }

    /**
     * Xóa file
     */
    public boolean deleteFile(int attachmentId) {
        List<Attachment> allAttachments = getAllAttachments();
        for (Attachment att : allAttachments) {
            if (att.getId() == attachmentId) {
                File file = new File(att.getFilePath());
                if (file.exists()) {
                    file.delete();
                }
                System.out.println("[FileService] File đã xóa: " + att.getFileName());
                return true;
            }
        }
        return false;
    }

    /**
     * Lấy thông tin file size dạng human readable
     */
    public String getHumanReadableSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024));
        return String.format("%.1f GB", bytes / (1024.0 * 1024 * 1024));
    }

    private String getFileType(String fileName) {
        if (fileName.endsWith(".pdf")) return "application/pdf";
        if (fileName.endsWith(".jpg") || fileName.endsWith(".jpeg")) return "image/jpeg";
        if (fileName.endsWith(".png")) return "image/png";
        if (fileName.endsWith(".doc") || fileName.endsWith(".docx")) return "application/msword";
        if (fileName.endsWith(".xls") || fileName.endsWith(".xlsx")) return "application/vnd.ms-excel";
        if (fileName.endsWith(".zip")) return "application/zip";
        if (fileName.endsWith(".txt")) return "text/plain";
        return "application/octet-stream";
    }
}