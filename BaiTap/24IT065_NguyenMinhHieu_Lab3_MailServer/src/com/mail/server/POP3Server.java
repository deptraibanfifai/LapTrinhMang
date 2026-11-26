// File: src/com/mail/server/POP3Server.java
package com.mail.server;

import com.mail.dao.EmailDAO;
import com.mail.dao.UserDAO;
import com.mail.model.Email;
import com.mail.model.User;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;

/**
 * POP3 Server - Cho phép client lấy email
 * Port mặc định: 1110
 */
public class POP3Server {
    private int port;
    private ServerSocket serverSocket;
    private boolean running = false;
    private EmailDAO emailDAO = new EmailDAO();
    private UserDAO userDAO = new UserDAO();

    public POP3Server(int port) {
        this.port = port;
    }

    public void start() {
        try {
            serverSocket = new ServerSocket(port);
            running = true;
            System.out.println("============================================");
            System.out.println("  POP3 Server đang chạy trên port: " + port);
            System.out.println("============================================");

            while (running) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("[POP3] Kết nối mới từ: " + clientSocket.getInetAddress());
                new Thread(() -> handleClient(clientSocket)).start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void handleClient(Socket socket) {
        String currentUserEmail = null;
        User currentUser = null;

        try (BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true)) {

            out.println("+OK POP3 Server Ready");

            String line;
            while ((line = in.readLine()) != null) {
                System.out.println("[POP3] Client: " + line);
                String[] parts = line.split(" ", 2);
                String command = parts[0].toUpperCase();

                switch (command) {
                    case "USER":
                        if (parts.length > 1) {
                            currentUserEmail = parts[1];
                            out.println("+OK User accepted");
                        } else {
                            out.println("-ERR Username required");
                        }
                        break;

                    case "PASS":
                        if (parts.length > 1 && currentUserEmail != null) {
                            String password = parts[1];
                            // Xác thực email + mật khẩu
                            User user = userDAO.loginByEmail(currentUserEmail, password);
                            if (user != null) {
                                currentUser = user;
                                out.println("+OK Login successful");
                            } else {
                                out.println("-ERR Invalid credentials");
                            }
                        } else {
                            out.println("-ERR Username required first");
                        }
                        break;

                    case "STAT":
                        if (currentUser != null) {
                            List<Email> inbox = emailDAO.getInbox(currentUserEmail);
                            int count = inbox.size();
                            int size = inbox.stream().mapToInt(e -> e.getBody() != null ? e.getBody().length() : 0).sum();
                            out.println("+OK " + count + " " + size);
                        } else {
                            out.println("-ERR Not authenticated");
                        }
                        break;

                    case "LIST":
                        if (currentUser != null) {
                            List<Email> inbox = emailDAO.getInbox(currentUserEmail);
                            out.println("+OK " + inbox.size() + " messages");
                            for (int i = 0; i < inbox.size(); i++) {
                                Email email = inbox.get(i);
                                int size = email.getBody() != null ? email.getBody().length() : 0;
                                out.println((i + 1) + " " + size);
                            }
                            out.println(".");
                        } else {
                            out.println("-ERR Not authenticated");
                        }
                        break;

                    case "RETR":
                        if (currentUser != null && parts.length > 1) {
                            try {
                                int msgNum = Integer.parseInt(parts[1]);
                                List<Email> inbox = emailDAO.getInbox(currentUserEmail);
                                if (msgNum > 0 && msgNum <= inbox.size()) {
                                    Email email = inbox.get(msgNum - 1);
                                    emailDAO.markAsRead(email.getId());
                                    out.println("+OK " + email.getId() + " octets");
                                    out.println("From: " + email.getSenderEmail());
                                    out.println("To: " + email.getReceiverEmail());
                                    out.println("Subject: " + email.getSubject());
                                    out.println("Date: " + email.getSentAt());
                                    out.println("");
                                    out.println(email.getBody());
                                    // Hiển thị attachments
                                    if (!email.getAttachments().isEmpty()) {
                                        out.println("");
                                        out.println("--- Attachments ---");
                                        for (var att : email.getAttachments()) {
                                            out.println("[" + att.getFileName() + " - " + att.getFileSize() + " bytes]");
                                        }
                                    }
                                    out.println(".");
                                } else {
                                    out.println("-ERR No such message");
                                }
                            } catch (NumberFormatException e) {
                                out.println("-ERR Invalid message number");
                            }
                        } else {
                            out.println("-ERR Not authenticated");
                        }
                        break;

                    case "DELE":
                        if (currentUser != null && parts.length > 1) {
                            try {
                                int msgNum = Integer.parseInt(parts[1]);
                                List<Email> inbox = emailDAO.getInbox(currentUserEmail);
                                if (msgNum > 0 && msgNum <= inbox.size()) {
                                    Email email = inbox.get(msgNum - 1);
                                    emailDAO.deleteEmail(email.getId());
                                    out.println("+OK Message deleted");
                                } else {
                                    out.println("-ERR No such message");
                                }
                            } catch (NumberFormatException e) {
                                out.println("-ERR Invalid message number");
                            }
                        } else {
                            out.println("-ERR Not authenticated");
                        }
                        break;

                    case "NOOP":
                        out.println("+OK");
                        break;

                    case "RSET":
                        out.println("+OK Maildrop has been reset");
                        break;

                    case "QUIT":
                        out.println("+OK Bye");
                        currentUser = null;
                        currentUserEmail = null;
                        socket.close();
                        return;

                    default:
                        out.println("-ERR Unknown command");
                        break;
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
        System.out.println("[POP3] Server đã dừng.");
    }
}