// File: src/com/mail/client/MailClient.java
package com.mail.client;

import com.mail.dao.EmailDAO;
import com.mail.dao.UserDAO;
import com.mail.model.Attachment;
import com.mail.model.Email;
import com.mail.model.User;
import com.mail.service.GmailService;
import com.mail.service.FileService;

import java.io.*;
import java.net.Socket;
import java.util.*;

/**
 * Mail Client - Giao diện console để gửi/nhận email
 * Hỗ trợ kết nối SMTP server localhost và Gmail
 */
public class MailClient {

    private User currentUser;
    private EmailDAO emailDAO = new EmailDAO();
    private UserDAO userDAO = new UserDAO();
    private FileService fileService = new FileService();
    private Scanner scanner = new Scanner(System.in);

    // SMTP Server localhost
    private static final String LOCAL_SMTP_HOST = "localhost";
    private static final int LOCAL_SMTP_PORT = 2525;

    // POP3 Server localhost
    private static final String LOCAL_POP3_HOST = "localhost";
    private static final int LOCAL_POP3_PORT = 1110;

    public void start() {
        System.out.println("╔══════════════════════════════════════════╗");
        System.out.println("║         MAIL CLIENT v1.0                 ║");
        System.out.println("║  Hỗ trợ: SMTP, POP3, Gmail, FileAttach   ║");
        System.out.println("╚══════════════════════════════════════════╝");

        while (true) {
            if (currentUser == null) {
                showLoginMenu();
            } else {
                showMainMenu();
            }
        }
    }

    private void showLoginMenu() {
        System.out.println("\n--- ĐĂNG NHẬP ---");
        System.out.println("1. Đăng nhập");
        System.out.println("2. Đăng ký");
        System.out.println("3. Thoát");
        System.out.print("Chọn: ");

        String choice = scanner.nextLine();
        switch (choice) {
            case "1":
                login();
                break;
            case "2":
                register();
                break;
            case "3":
                System.out.println("Tạm biệt!");
                System.exit(0);
                break;
            default:
                System.out.println("Lựa chọn không hợp lệ!");
        }
    }

    private void login() {
        System.out.print("Username: ");
        String username = scanner.nextLine();
        System.out.print("Password: ");
        String password = scanner.nextLine();

        currentUser = userDAO.login(username, password);
        if (currentUser != null) {
            System.out.println("✅ Đăng nhập thành công! Xin chào " + currentUser.getFullName());
        } else {
            System.out.println("❌ Sai username hoặc password!");
        }
    }

    private void register() {
        System.out.print("Username: ");
        String username = scanner.nextLine();
        System.out.print("Password: ");
        String password = scanner.nextLine();
        System.out.print("Email: ");
        String email = scanner.nextLine();
        System.out.print("Họ tên: ");
        String fullName = scanner.nextLine();

        User user = new User(username, password, email, fullName);
        if (userDAO.register(user)) {
            System.out.println("✅ Đăng ký thành công! Hãy đăng nhập.");
        } else {
            System.out.println("❌ Đăng ký thất bại! Username hoặc email đã tồn tại.");
        }
    }

    private void showMainMenu() {
        System.out.println("\n╔══════════════════════════════════════════╗");
        System.out.println("║  Xin chào: " + currentUser.getFullName());
        System.out.println("║  Email: " + currentUser.getEmail());
        System.out.println("╠══════════════════════════════════════════╣");
        System.out.println("║  1. 📥 Xem Inbox (POP3 Localhost)        ║");
        System.out.println("║  2. 📤 Gửi email (SMTP Localhost)        ║");
        System.out.println("║  3. 📧 Gửi email qua Gmail               ║");
        System.out.println("║  4. 📁 Quản lý file đính kèm             ║");
        System.out.println("║  5. 📋 Email đã gửi                      ║");
        System.out.println("║  6. 👤 Xem thông tin                     ║");
        System.out.println("║  7. 🚪 Đăng xuất                         ║");
        System.out.println("╚══════════════════════════════════════════╝");
        System.out.print("Chọn: ");

        String choice = scanner.nextLine();
        switch (choice) {
            case "1":
                viewInbox();
                break;
            case "2":
                sendEmailLocalhost();
                break;
            case "3":
                sendEmailGmail();
                break;
            case "4":
                manageAttachments();
                break;
            case "5":
                viewSentEmails();
                break;
            case "6":
                viewProfile();
                break;
            case "7":
                currentUser = null;
                System.out.println("Đã đăng xuất.");
                break;
            default:
                System.out.println("Lựa chọn không hợp lệ!");
        }
    }

    /**
     * Xem inbox qua POP3 protocol
     */
    private void viewInbox() {
        System.out.println("\n--- INBOX (qua POP3) ---");
        List<Email> emails = emailDAO.getInbox(currentUser.getEmail());

        if (emails.isEmpty()) {
            System.out.println("📭 Inbox trống!");
            return;
        }

        System.out.printf("%-5s %-25s %-30s %-20s %-10s%n",
            "ID", "From", "Subject", "Date", "Status");
        System.out.println("------------------------------------------------------------------------");

        for (int i = 0; i < emails.size(); i++) {
            Email email = emails.get(i);
            String status = email.isRead() ? "📖 Read" : "📕 Unread";
            System.out.printf("%-5d %-25s %-30s %-20s %-10s%n",
                i + 1,
                truncate(email.getSenderEmail(), 25),
                truncate(email.getSubject(), 30),
                email.getSentAt() != null ? email.getSentAt().toString().substring(0, 16) : "N/A",
                status);
        }

        System.out.print("\nNhập số để xem chi tiết (0 để quay lại): ");
        String choice = scanner.nextLine();
        try {
            int num = Integer.parseInt(choice);
            if (num > 0 && num <= emails.size()) {
                viewEmailDetail(emails.get(num - 1));
            }
        } catch (NumberFormatException e) {
            // Không làm gì
        }
    }

    private void viewEmailDetail(Email email) {
        System.out.println("\n════════════════════════════════════════");
        System.out.println("From:    " + email.getSenderEmail());
        System.out.println("To:      " + email.getReceiverEmail());
        System.out.println("Subject: " + email.getSubject());
        System.out.println("Date:    " + email.getSentAt());
        System.out.println("────────────────────────────────────────");
        System.out.println(email.getBody());

        if (!email.getAttachments().isEmpty()) {
            System.out.println("\n📎 Attachments:");
            for (var att : email.getAttachments()) {
                System.out.println("  - " + att.getFileName() + " (" +
                    fileService.getHumanReadableSize(att.getFileSize()) + ")");
            }
        }
        System.out.println("════════════════════════════════════════");

        // Đánh dấu đã đọc
        emailDAO.markAsRead(email.getId());

        System.out.print("\n1. Xóa email | 0. Quay lại: ");
        String choice = scanner.nextLine();
        if (choice.equals("1")) {
            emailDAO.deleteEmail(email.getId());
            System.out.println("✅ Email đã xóa.");
        }
    }

    /**
     * Gửi email qua SMTP localhost
     */
    private void sendEmailLocalhost() {
        System.out.println("\n--- GỬI EMAIL (SMTP Localhost) ---");
        System.out.print("To: ");
        String to = scanner.nextLine();
        System.out.print("Subject: ");
        String subject = scanner.nextLine();
        System.out.println("Body (nhập 'END' để kết thúc):");

        StringBuilder body = new StringBuilder();
        String line;
        while (!(line = scanner.nextLine()).equalsIgnoreCase("END")) {
            body.append(line).append("\n");
        }

        // Gửi qua SMTP socket
        try (Socket socket = new Socket(LOCAL_SMTP_HOST, LOCAL_SMTP_PORT);
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true)) {

            System.out.println(in.readLine()); // 220 greeting

            out.println("EHLO localhost");
            System.out.println(in.readLine());
            System.out.println(in.readLine());
            System.out.println(in.readLine());
            System.out.println(in.readLine());

            // AUTH LOGIN
            out.println("AUTH LOGIN");
            System.out.println(in.readLine()); // 334 Username

            String encodedUser = Base64.getEncoder().encodeToString(
                currentUser.getUsername().getBytes());
            out.println(encodedUser);
            System.out.println(in.readLine()); // 334 Password

            String encodedPass = Base64.getEncoder().encodeToString(
                currentUser.getPassword().getBytes());
            out.println(encodedPass);
            System.out.println(in.readLine()); // 235 OK

            out.println("MAIL FROM:<" + currentUser.getEmail() + ">");
            System.out.println(in.readLine());

            out.println("RCPT TO:<" + to + ">");
            System.out.println(in.readLine());

            out.println("DATA");
            System.out.println(in.readLine());

            out.println("Subject: " + subject);
            out.println("");
            out.println(body.toString());
            out.println(".");
            System.out.println(in.readLine());

            out.println("QUIT");
            System.out.println(in.readLine());

            System.out.println("✅ Email đã gửi thành công qua SMTP Localhost!");

        } catch (Exception e) {
            System.err.println("❌ Lỗi gửi email: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Gửi email qua Gmail
     */
    private void sendEmailGmail() {
        System.out.println("\n--- GỬI EMAIL QUA GMAIL ---");
        System.out.print("Gmail address: ");
        String gmailAddress = scanner.nextLine();
        System.out.print("Gmail password (App Password): ");
        String gmailPassword = scanner.nextLine();

        System.out.print("To: ");
        String to = scanner.nextLine();
        System.out.print("Subject: ");
        String subject = scanner.nextLine();
        System.out.println("Body (nhập 'END' để kết thúc):");

        StringBuilder body = new StringBuilder();
        String line;
        while (!(line = scanner.nextLine()).equalsIgnoreCase("END")) {
            body.append(line).append("\n");
        }

        // Hỏi có đính kèm file không
        System.out.print("Đính kèm file? (y/n): ");
        String attachChoice = scanner.nextLine();
        List<File> files = null;

        if (attachChoice.equalsIgnoreCase("y")) {
            files = new ArrayList<>();
            System.out.print("Đường dẫn file (nhập 'done' để kết thúc): ");
            while (true) {
                String filePath = scanner.nextLine();
                if (filePath.equalsIgnoreCase("done")) break;
                File file = new File(filePath);
                if (file.exists()) {
                    files.add(file);
                    System.out.println("  ✅ Added: " + file.getName());
                } else {
                    System.out.println("  ❌ File không tồn tại!");
                }
                System.out.print("Đường dẫn file tiếp (hoặc 'done'): ");
            }
        }

        GmailService gmailService = new GmailService(gmailAddress, gmailPassword);
        boolean success = gmailService.sendEmail(to, subject, body.toString(), files);

        if (success) {
            System.out.println("✅ Email đã gửi thành công qua Gmail!");
        } else {
            System.out.println("❌ Gửi email thất bại!");
        }
    }

    private void manageAttachments() {
        System.out.println("\n--- QUẢN LÝ FILE ĐÍNH KÈM ---");
        System.out.println("1. Upload file");
        System.out.println("2. Xem files đã upload");
        System.out.print("Chọn: ");

        String choice = scanner.nextLine();
        if (choice.equals("1")) {
            System.out.print("Đường dẫn file: ");
            String path = scanner.nextLine();
            File file = new File(path);
            if (file.exists()) {
                // Cần emailId - tạo email draft trước
                Email draft = new Email(currentUser.getEmail(), currentUser.getEmail(), "Draft", "");
                int emailId = emailDAO.saveEmail(draft);
                Attachment att = fileService.uploadFile(emailId, file);
                if (att != null) {
                    System.out.println("✅ File đã upload: " + att.getFileName() +
                        " (" + fileService.getHumanReadableSize(att.getFileSize()) + ")");
                }
            } else {
                System.out.println("❌ File không tồn tại!");
            }
        }
    }

    private void viewSentEmails() {
        System.out.println("\n--- EMAIL ĐÃ GỬI ---");
        List<Email> emails = emailDAO.getSentEmails(currentUser.getEmail());

        if (emails.isEmpty()) {
            System.out.println("📭 Chưa có email nào được gửi.");
            return;
        }

        System.out.printf("%-5s %-25s %-30s %-20s%n",
            "ID", "To", "Subject", "Date");
        System.out.println("----------------------------------------------------------------");

        for (Email email : emails) {
            System.out.printf("%-5d %-25s %-30s %-20s%n",
                email.getId(),
                truncate(email.getReceiverEmail(), 25),
                truncate(email.getSubject(), 30),
                email.getSentAt() != null ? email.getSentAt().toString().substring(0, 16) : "N/A");
        }
    }

    private void viewProfile() {
        System.out.println("\n--- THÔNG TIN CÁ NHÂN ---");
        System.out.println("ID:       " + currentUser.getId());
        System.out.println("Username: " + currentUser.getUsername());
        System.out.println("Email:    " + currentUser.getEmail());
        System.out.println("Họ tên:   " + currentUser.getFullName());
        System.out.println("Created:  " + currentUser.getCreatedAt());
        System.out.println("Last login: " + currentUser.getLastLogin());
    }

    private String truncate(String str, int maxLen) {
        if (str == null) return "";
        return str.length() > maxLen ? str.substring(0, maxLen - 3) + "..." : str;
    }
}