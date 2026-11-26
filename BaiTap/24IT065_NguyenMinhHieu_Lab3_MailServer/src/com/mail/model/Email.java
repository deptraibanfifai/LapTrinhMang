// File: src/com/mail/model/Email.java
package com.mail.model;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

public class Email {
    private int id;
    private int senderId;
    private String senderEmail;
    private String receiverEmail;
    private String subject;
    private String body;
    private String status; // sent, received, draft, deleted
    private boolean isRead;
    private Timestamp sentAt;
    private Timestamp receivedAt;
    private List<Attachment> attachments = new ArrayList<>();

    // Constructors
    public Email() {}

    public Email(String senderEmail, String receiverEmail, String subject, String body) {
        this.senderEmail = senderEmail;
        this.receiverEmail = receiverEmail;
        this.subject = subject;
        this.body = body;
        this.status = "sent";
    }

    // Getters & Setters
    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public int getSenderId() { return senderId; }
    public void setSenderId(int senderId) { this.senderId = senderId; }

    public String getSenderEmail() { return senderEmail; }
    public void setSenderEmail(String senderEmail) { this.senderEmail = senderEmail; }

    public String getReceiverEmail() { return receiverEmail; }
    public void setReceiverEmail(String receiverEmail) { this.receiverEmail = receiverEmail; }

    public String getSubject() { return subject; }
    public void setSubject(String subject) { this.subject = subject; }

    public String getBody() { return body; }
    public void setBody(String body) { this.body = body; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public boolean isRead() { return isRead; }
    public void setRead(boolean read) { isRead = read; }

    public Timestamp getSentAt() { return sentAt; }
    public void setSentAt(Timestamp sentAt) { this.sentAt = sentAt; }

    public Timestamp getReceivedAt() { return receivedAt; }
    public void setReceivedAt(Timestamp receivedAt) { this.receivedAt = receivedAt; }

    public List<Attachment> getAttachments() { return attachments; }
    public void setAttachments(List<Attachment> attachments) { this.attachments = attachments; }
    public void addAttachment(Attachment attachment) { this.attachments.add(attachment); }

    @Override
    public String toString() {
        return "Email{from='" + senderEmail + "', to='" + receiverEmail + 
               "', subject='" + subject + "', status='" + status + "'}";
    }
}