// File: src/com/mail/service/GmailService.java
package com.mail.service;

import com.mail.dao.EmailDAO;
import com.mail.model.Attachment;
import com.mail.model.Email;

import javax.mail.*;
import javax.mail.internet.*;
import java.io.File;
import java.util.List;
import java.util.Properties;

/**
 * Service gửi email qua Gmail SMTP
 * Yêu cầu: Bật "Less secure app access" hoặc dùng App Password
 */
public class GmailService {

    private final String gmailAddress;
    private final String gmailPassword;
    private final EmailDAO emailDAO = new EmailDAO();

    // Gmail SMTP settings
    private static final String SMTP_HOST = "smtp.gmail.com";
    private static final String SMTP_PORT = "587";

    public GmailService(String gmailAddress, String gmailPassword) {
        this.gmailAddress = gmailAddress;
        this.gmailPassword = gmailPassword;
    }

    /**
     * Gửi email qua Gmail với đính kèm file
     */
    public boolean sendEmail(String to, String subject, String body, List<File> attachments) {
        Properties props = new Properties();
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.starttls.enable", "true");
        props.put("mail.smtp.host", SMTP_HOST);
        props.put("mail.smtp.port", SMTP_PORT);
        props.put("mail.smtp.ssl.trust", SMTP_HOST);

        Session session = Session.getInstance(props, new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(gmailAddress, gmailPassword);
            }
        });

        try {
            Message message = new MimeMessage(session);
            message.setFrom(new InternetAddress(gmailAddress));
            message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(to));
            message.setSubject(subject);

            // Nếu có attachments, dùng MimeMultipart
            if (attachments != null && !attachments.isEmpty()) {
                MimeMultipart multipart = new MimeMultipart();

                // Phần text
                MimeBodyPart textPart = new MimeBodyPart();
                textPart.setText(body);
                multipart.addBodyPart(textPart);

                // Thêm file đính kèm
                for (File file : attachments) {
                    MimeBodyPart attachmentPart = new MimeBodyPart();
                    attachmentPart.attachFile(file);
                    attachmentPart.setFileName(file.getName());
                    multipart.addBodyPart(attachmentPart);
                }

                message.setContent(multipart);
            } else {
                message.setText(body);
            }

            Transport.send(message);
            System.out.println("[Gmail] Email đã gửi thành công tới: " + to);

            // Lưu vào database
            Email email = new Email(gmailAddress, to, subject, body);
            int emailId = emailDAO.saveEmail(email);

            // Lưu attachments vào DB
            if (attachments != null && emailId > 0) {
                for (File file : attachments) {
                    Attachment att = new Attachment(
                        emailId,
                        file.getName(),
                        file.getAbsolutePath(),
                        file.length(),
                        getFileType(file.getName())
                    );
                    emailDAO.saveAttachment(att);
                }
            }

            return true;

        } catch (Exception e) {
            System.err.println("[Gmail] Lỗi gửi email: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Gửi email không có attachment
     */
    public boolean sendEmail(String to, String subject, String body) {
        return sendEmail(to, subject, body, null);
    }

    /**
     * Gửi email HTML
     */
    public boolean sendHtmlEmail(String to, String subject, String htmlBody) {
        Properties props = new Properties();
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.starttls.enable", "true");
        props.put("mail.smtp.host", SMTP_HOST);
        props.put("mail.smtp.port", SMTP_PORT);

        Session session = Session.getInstance(props, new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(gmailAddress, gmailPassword);
            }
        });

        try {
            Message message = new MimeMessage(session);
            message.setFrom(new InternetAddress(gmailAddress));
            message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(to));
            message.setSubject(subject);
            message.setContent(htmlBody, "text/html; charset=UTF-8");

            Transport.send(message);
            System.out.println("[Gmail] HTML Email đã gửi thành công tới: " + to);

            // Lưu vào database
            Email email = new Email(gmailAddress, to, subject, htmlBody);
            emailDAO.saveEmail(email);

            return true;

        } catch (Exception e) {
            System.err.println("[Gmail] Lỗi gửi HTML email: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
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