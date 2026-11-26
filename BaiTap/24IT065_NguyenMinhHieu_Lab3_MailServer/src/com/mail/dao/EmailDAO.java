// File: src/com/mail/dao/EmailDAO.java
package com.mail.dao;

import com.mail.config.DatabaseConfig;
import com.mail.model.Attachment;
import com.mail.model.Email;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class EmailDAO {

    // Lưu email vào database
    public int saveEmail(Email email) {
        String sql = "INSERT INTO emails (sender_id, sender_email, receiver_email, subject, body, status) " +
                     "VALUES (?, ?, ?, ?, ?, ?)";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, email.getSenderId());
            ps.setString(2, email.getSenderEmail());
            ps.setString(3, email.getReceiverEmail());
            ps.setString(4, email.getSubject());
            ps.setString(5, email.getBody());
            ps.setString(6, email.getStatus());
            ps.executeUpdate();

            ResultSet rs = ps.getGeneratedKeys();
            if (rs.next()) {
                return rs.getInt(1);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return -1;
    }

    // Lưu attachment
    public boolean saveAttachment(Attachment attachment) {
        String sql = "INSERT INTO attachments (email_id, file_name, file_path, file_size, file_type) " +
                     "VALUES (?, ?, ?, ?, ?)";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, attachment.getEmailId());
            ps.setString(2, attachment.getFileName());
            ps.setString(3, attachment.getFilePath());
            ps.setLong(4, attachment.getFileSize());
            ps.setString(5, attachment.getFileType());
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    // Lấy email theo ID
    public Email getEmailById(int id) {
        String sql = "SELECT * FROM emails WHERE id = ?";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return mapEmail(rs);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    // Lấy inbox của user
    public List<Email> getInbox(String email) {
        List<Email> emails = new ArrayList<>();
        String sql = "SELECT * FROM emails WHERE receiver_email = ? AND status != 'deleted' ORDER BY sent_at DESC";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, email);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                Email emailObj = mapEmail(rs);
                emailObj.setAttachments(getAttachments(emailObj.getId()));
                emails.add(emailObj);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return emails;
    }

    // Lấy sent emails
    public List<Email> getSentEmails(String email) {
        List<Email> emails = new ArrayList<>();
        String sql = "SELECT * FROM emails WHERE sender_email = ? AND status != 'deleted' ORDER BY sent_at DESC";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, email);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                Email emailObj = mapEmail(rs);
                emailObj.setAttachments(getAttachments(emailObj.getId()));
                emails.add(emailObj);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return emails;
    }

    // Lấy attachments của email
    public List<Attachment> getAttachments(int emailId) {
        List<Attachment> attachments = new ArrayList<>();
        String sql = "SELECT * FROM attachments WHERE email_id = ?";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, emailId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                Attachment att = new Attachment();
                att.setId(rs.getInt("id"));
                att.setEmailId(rs.getInt("email_id"));
                att.setFileName(rs.getString("file_name"));
                att.setFilePath(rs.getString("file_path"));
                att.setFileSize(rs.getLong("file_size"));
                att.setFileType(rs.getString("file_type"));
                att.setUploadedAt(rs.getTimestamp("uploaded_at"));
                attachments.add(att);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return attachments;
    }

    // Lấy tất cả attachments (dùng cho FileService)
    public List<Attachment> getAllAttachments() {
        List<Attachment> attachments = new ArrayList<>();
        String sql = "SELECT * FROM attachments";
        try (Connection conn = DatabaseConfig.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                Attachment att = new Attachment();
                att.setId(rs.getInt("id"));
                att.setEmailId(rs.getInt("email_id"));
                att.setFileName(rs.getString("file_name"));
                att.setFilePath(rs.getString("file_path"));
                att.setFileSize(rs.getLong("file_size"));
                att.setFileType(rs.getString("file_type"));
                att.setUploadedAt(rs.getTimestamp("uploaded_at"));
                attachments.add(att);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return attachments;
    }

    // Đánh dấu đã đọc
    public boolean markAsRead(int emailId) {
        String sql = "UPDATE emails SET is_read = TRUE WHERE id = ?";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, emailId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    // Xóa email (soft delete)
    public boolean deleteEmail(int emailId) {
        String sql = "UPDATE emails SET status = 'deleted' WHERE id = ?";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, emailId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    // Map ResultSet to Email object
    private Email mapEmail(ResultSet rs) throws SQLException {
        Email email = new Email();
        email.setId(rs.getInt("id"));
        email.setSenderId(rs.getInt("sender_id"));
        email.setSenderEmail(rs.getString("sender_email"));
        email.setReceiverEmail(rs.getString("receiver_email"));
        email.setSubject(rs.getString("subject"));
        email.setBody(rs.getString("body"));
        email.setStatus(rs.getString("status"));
        email.setRead(rs.getBoolean("is_read"));
        email.setSentAt(rs.getTimestamp("sent_at"));
        email.setReceivedAt(rs.getTimestamp("received_at"));
        return email;
    }
}