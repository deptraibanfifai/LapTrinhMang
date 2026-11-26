// File: src/com/mail/server/MailServer.java
package com.mail.server;

/**
 * Mail Server chính - Khởi chạy cả SMTP và POP3
 * Đã bỏ System.in.read() để không block thread
 */
public class MailServer {
    private SMTPServer smtpServer;
    private POP3Server pop3Server;
    private boolean running = false;

    public MailServer(int smtpPort, int pop3Port) {
        this.smtpServer = new SMTPServer(smtpPort);
        this.pop3Server = new POP3Server(pop3Port);
    }

    public void start() {
        System.out.println("╔══════════════════════════════════════════╗");
        System.out.println("║       MAIL SERVER STARTING...            ║");
        System.out.println("╠══════════════════════════════════════════╣");
        System.out.println("║  SMTP Port: 2525                         ║");
        System.out.println("║  POP3 Port: 1110                         ║");
        System.out.println("╚══════════════════════════════════════════╝");

        // Chạy SMTP server trên thread riêng
        Thread smtpThread = new Thread(() -> {
            try {
                smtpServer.start();
            } catch (Exception e) {
                System.err.println("SMTP Server error: " + e.getMessage());
            }
        });
        smtpThread.setDaemon(true);
        smtpThread.start();

        // Chạy POP3 server trên thread riêng
        Thread pop3Thread = new Thread(() -> {
            try {
                pop3Server.start();
            } catch (Exception e) {
                System.err.println("POP3 Server error: " + e.getMessage());
            }
        });
        pop3Thread.setDaemon(true);
        pop3Thread.start();

        running = true;
        System.out.println("\n[MailServer] ✅ Cả SMTP và POP3 đã khởi động!");
    }

    public void stop() {
        if (!running) return;

        System.out.println("\n[MailServer] Đang dừng server...");
        running = false;

        smtpServer.stop();
        pop3Server.stop();

        System.out.println("[MailServer] ✅ Server đã dừng.");
    }

    public boolean isRunning() {
        return running;
    }

    public SMTPServer getSmtpServer() {
        return smtpServer;
    }

    public POP3Server getPop3Server() {
        return pop3Server;
    }
}