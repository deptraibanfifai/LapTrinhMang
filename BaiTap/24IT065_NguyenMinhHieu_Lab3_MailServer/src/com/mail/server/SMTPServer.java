// File: src/com/mail/server/SMTPServer.java
package com.mail.server;

import com.mail.dao.EmailDAO;
import com.mail.dao.UserDAO;
import com.mail.model.Email;
import com.mail.model.User;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Base64;

/**
 * SMTP Server - Nhận email từ client
 * Port mặc định: 2525
 */
public class SMTPServer {
    private int port;
    private ServerSocket serverSocket;
    private boolean running = false;
    private EmailDAO emailDAO = new EmailDAO();
    private UserDAO userDAO = new UserDAO();

    public SMTPServer(int port) {
        this.port = port;
    }

    public void start() {
        try {
            serverSocket = new ServerSocket(port);
            running = true;
            System.out.println("============================================");
            System.out.println("  SMTP Server đang chạy trên port: " + port);
            System.out.println("============================================");

            while (running) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("[SMTP] Kết nối mới từ: " + clientSocket.getInetAddress());
                new Thread(() -> handleClient(clientSocket)).start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void handleClient(Socket socket) {
        try (BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true)) {

            // Gửi greeting
            out.println("220 localhost SMTP Mail Server Ready");

            String line;
            String from = "";
            String to = "";
            StringBuilder data = new StringBuilder();
            boolean receivingData = false;

            while ((line = in.readLine()) != null) {
                System.out.println("[SMTP] Client: " + line);

                if (line.toUpperCase().startsWith("EHLO") || line.toUpperCase().startsWith("HELO")) {
                    out.println("250-localhost Hello");
                    out.println("250-SIZE 10485760");
                    out.println("250-AUTH LOGIN");
                    out.println("250 OK");

                } else if (line.toUpperCase().startsWith("AUTH LOGIN")) {
                    out.println("334 " + Base64.getEncoder().encodeToString("Username:".getBytes()));
                    String username = in.readLine();
                    out.println("334 " + Base64.getEncoder().encodeToString("Password:".getBytes()));
                    String password = in.readLine();

                    String decodedUser = new String(Base64.getDecoder().decode(username));
                    String decodedPass = new String(Base64.getDecoder().decode(password));

                    User user = userDAO.login(decodedUser, decodedPass);
                    if (user != null) {
                        out.println("235 Authentication successful");
                    } else {
                        out.println("535 Authentication failed");
                    }

                } else if (line.toUpperCase().startsWith("MAIL FROM:")) {
                    from = line.substring(10).trim().replaceAll("[<>]", "");
                    out.println("250 OK");

                } else if (line.toUpperCase().startsWith("RCPT TO:")) {
                    to = line.substring(8).trim().replaceAll("[<>]", "");
                    out.println("250 OK");

                } else if (line.toUpperCase().equals("DATA")) {
                    out.println("354 Start mail input; end with <CRLF>.<CRLF>");
                    receivingData = true;

                } else if (receivingData) {
                    if (line.equals(".")) {
                        receivingData = false;
                        // Lưu email vào database
                        Email email = new Email(from, to, "No Subject", data.toString());
                        User sender = userDAO.findByEmail(from);
                        if (sender != null) {
                            email.setSenderId(sender.getId());
                        }
                        int emailId = emailDAO.saveEmail(email);
                        System.out.println("[SMTP] Email đã lưu vào DB với ID: " + emailId);
                        out.println("250 OK: Message queued");
                        data.setLength(0);
                    } else {
                        data.append(line).append("\n");
                    }

                } else if (line.toUpperCase().equals("QUIT")) {
                    out.println("221 Bye");
                    break;

                } else {
                    out.println("500 Command not recognized");
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                socket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public void stop() {
        running = false;
        try {
            if (serverSocket != null) {
                serverSocket.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        System.out.println("[SMTP] Server đã dừng.");
    }
}