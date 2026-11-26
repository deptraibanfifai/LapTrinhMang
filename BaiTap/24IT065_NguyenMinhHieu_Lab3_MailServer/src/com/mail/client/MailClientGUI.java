// File: src/com/mail/client/MailClientGUI.java
package com.mail.client;

import com.mail.dao.EmailDAO;
import com.mail.dao.UserDAO;
import com.mail.model.Attachment;
import com.mail.model.Email;
import com.mail.model.User;
import com.mail.service.FileService;
import com.mail.service.GmailService;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.*;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class MailClientGUI extends JFrame {

    private User currentUser;
    private UserDAO userDAO = new UserDAO();
    private EmailDAO emailDAO = new EmailDAO();
    private FileService fileService = new FileService();

    private CardLayout cardLayout;
    private JPanel cardPanel;

    // Login Panel
    private JTextField txtLoginUsername;
    private JPasswordField txtLoginPassword;

    // Register Panel
    private JTextField txtRegUsername, txtRegEmail, txtRegFullName;
    private JPasswordField txtRegPassword;

    // Main Panel
    private JTable emailTable;
    private DefaultTableModel tableModel;
    private JTextArea txtEmailContent;
    private JLabel lblUserInfo;

    // Compose Panel
    private JTextField txtTo, txtSubject;
    private JTextArea txtBody;
    private JComboBox<String> cboSendMethod;
    private JTextField txtGmailAddress;
    private JPasswordField txtGmailPassword;
    private DefaultListModel<File> attachmentListModel;
    private JList<File> lstAttachments;

    public MailClientGUI() {
        initUI();
    }

    private void initUI() {
        setTitle("📧 Mail Client");
        setSize(900, 650);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout());

        cardLayout = new CardLayout();
        cardPanel = new JPanel(cardLayout);

        cardPanel.add(createLoginPanel(), "LOGIN");
        cardPanel.add(createRegisterPanel(), "REGISTER");
        cardPanel.add(createMainPanel(), "MAIN");
        cardPanel.add(createComposePanel(), "COMPOSE");

        add(cardPanel, BorderLayout.CENTER);

        cardLayout.show(cardPanel, "LOGIN");
    }

    // ==================== LOGIN PANEL ====================
    private JPanel createLoginPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(new EmptyBorder(80, 200, 80, 200));

        // Title
        JLabel lblTitle = new JLabel("📧 ĐĂNG NHẬP MAIL CLIENT");
        lblTitle.setFont(new Font("Arial", Font.BOLD, 24));
        lblTitle.setAlignmentX(Component.CENTER_ALIGNMENT);
        panel.add(lblTitle);
        panel.add(Box.createVerticalStrut(40));

        // Username
        JPanel userPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        userPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        userPanel.add(new JLabel("Username:"));
        txtLoginUsername = new JTextField(20);
        userPanel.add(txtLoginUsername);
        panel.add(userPanel);
        panel.add(Box.createVerticalStrut(10));

        // Password
        JPanel passPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        passPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        passPanel.add(new JLabel("Password:"));
        txtLoginPassword = new JPasswordField(20);
        passPanel.add(txtLoginPassword);
        panel.add(passPanel);
        panel.add(Box.createVerticalStrut(30));

        // Buttons
        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 20, 0));
        btnPanel.setAlignmentX(Component.CENTER_ALIGNMENT);

        JButton btnLogin = new JButton("🔑 Đăng nhập");
        btnLogin.setFont(new Font("Arial", Font.BOLD, 14));
        btnLogin.setBackground(new Color(33, 150, 243));
        btnLogin.setForeground(Color.WHITE);
        btnLogin.setPreferredSize(new Dimension(150, 40));
        btnLogin.addActionListener(e -> login());

        JButton btnRegister = new JButton("📝 Đăng ký");
        btnRegister.setFont(new Font("Arial", Font.BOLD, 14));
        btnRegister.setBackground(new Color(76, 175, 80));
        btnRegister.setForeground(Color.WHITE);
        btnRegister.setPreferredSize(new Dimension(150, 40));
        btnRegister.addActionListener(e -> cardLayout.show(cardPanel, "REGISTER"));

        btnPanel.add(btnLogin);
        btnPanel.add(btnRegister);
        panel.add(btnPanel);

        return panel;
    }

    // ==================== REGISTER PANEL ====================
    private JPanel createRegisterPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(new EmptyBorder(50, 200, 50, 200));

        JLabel lblTitle = new JLabel("📝 ĐĂNG KÝ TÀI KHOẢN");
        lblTitle.setFont(new Font("Arial", Font.BOLD, 24));
        lblTitle.setAlignmentX(Component.CENTER_ALIGNMENT);
        panel.add(lblTitle);
        panel.add(Box.createVerticalStrut(30));

        // Username
        JPanel row1 = new JPanel(new FlowLayout(FlowLayout.LEFT));
        row1.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        row1.add(new JLabel("Username:"));
        txtRegUsername = new JTextField(20);
        row1.add(txtRegUsername);
        panel.add(row1);
        panel.add(Box.createVerticalStrut(10));

        // Password - TẠO RIÊNG JPasswordField
        JPanel row2 = new JPanel(new FlowLayout(FlowLayout.LEFT));
        row2.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        row2.add(new JLabel("Password:"));
        txtRegPassword = new JPasswordField(20);  // ✅ Tạo đúng kiểu
        row2.add(txtRegPassword);
        panel.add(row2);
        panel.add(Box.createVerticalStrut(10));

        // Email
        JPanel row3 = new JPanel(new FlowLayout(FlowLayout.LEFT));
        row3.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        row3.add(new JLabel("Email:"));
        txtRegEmail = new JTextField(20);
        row3.add(txtRegEmail);
        panel.add(row3);
        panel.add(Box.createVerticalStrut(10));

        // Họ tên
        JPanel row4 = new JPanel(new FlowLayout(FlowLayout.LEFT));
        row4.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        row4.add(new JLabel("Họ tên:"));
        txtRegFullName = new JTextField(20);
        row4.add(txtRegFullName);
        panel.add(row4);
        panel.add(Box.createVerticalStrut(20));

        // Buttons
        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 20, 0));
        btnPanel.setAlignmentX(Component.CENTER_ALIGNMENT);

        JButton btnSubmit = new JButton(" Đăng ký");
        btnSubmit.setFont(new Font("Arial", Font.BOLD, 14));
        btnSubmit.setBackground(new Color(76, 175, 80));
        btnSubmit.setForeground(Color.WHITE);
        btnSubmit.setPreferredSize(new Dimension(150, 40));
        btnSubmit.addActionListener(e -> register());

        JButton btnBack = new JButton("️ Quay lại");
        btnBack.setFont(new Font("Arial", Font.BOLD, 14));
        btnBack.setPreferredSize(new Dimension(150, 40));
        btnBack.addActionListener(e -> cardLayout.show(cardPanel, "LOGIN"));

        btnPanel.add(btnSubmit);
        btnPanel.add(btnBack);
        panel.add(btnPanel);

        return panel;
    }

    // ==================== MAIN PANEL ====================
    private JPanel createMainPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(new EmptyBorder(10, 10, 10, 10));

        // Top panel
        JPanel topPanel = new JPanel(new BorderLayout());
        lblUserInfo = new JLabel();
        lblUserInfo.setFont(new Font("Arial", Font.BOLD, 14));
        topPanel.add(lblUserInfo, BorderLayout.WEST);

        JPanel btnTopPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton btnCompose = new JButton("✉️ Soạn email");
        btnCompose.setBackground(new Color(33, 150, 243));
        btnCompose.setForeground(Color.WHITE);
        btnCompose.addActionListener(e -> cardLayout.show(cardPanel, "COMPOSE"));

        JButton btnRefresh = new JButton("🔄 Làm mới");
        btnRefresh.addActionListener(e -> loadInbox());

        JButton btnLogout = new JButton("🚪 Đăng xuất");
        btnLogout.setBackground(new Color(244, 67, 54));
        btnLogout.setForeground(Color.WHITE);
        btnLogout.addActionListener(e -> logout());

        btnTopPanel.add(btnCompose);
        btnTopPanel.add(btnRefresh);
        btnTopPanel.add(btnLogout);
        topPanel.add(btnTopPanel, BorderLayout.EAST);

        panel.add(topPanel, BorderLayout.NORTH);

        // Email table
        String[] columns = {"ID", "From", "Subject", "Date", "Status"};
        tableModel = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        emailTable = new JTable(tableModel);
        emailTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        emailTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    viewSelectedEmail();
                }
            }
        });

        JScrollPane tableScroll = new JScrollPane(emailTable);
        tableScroll.setBorder(BorderFactory.createTitledBorder("📥 Inbox"));
        panel.add(tableScroll, BorderLayout.CENTER);

        // Email content
        JPanel contentPanel = new JPanel(new BorderLayout());
        txtEmailContent = new JTextArea();
        txtEmailContent.setEditable(false);
        txtEmailContent.setFont(new Font("Consolas", Font.PLAIN, 12));
        JScrollPane contentScroll = new JScrollPane(txtEmailContent);
        contentScroll.setBorder(BorderFactory.createTitledBorder("📄 Nội dung email"));
        contentPanel.add(contentScroll, BorderLayout.CENTER);
        contentPanel.setPreferredSize(new Dimension(0, 200));

        panel.add(contentPanel, BorderLayout.SOUTH);

        return panel;
    }

    // ==================== COMPOSE PANEL ====================
    private JPanel createComposePanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(new EmptyBorder(10, 10, 10, 10));

        // Form
        JPanel formPanel = new JPanel();
        formPanel.setLayout(new BoxLayout(formPanel, BoxLayout.Y_AXIS));

        JPanel row1 = new JPanel(new FlowLayout(FlowLayout.LEFT));
        row1.add(new JLabel("Gửi qua:"));
        cboSendMethod = new JComboBox<>(new String[]{"SMTP Localhost", "Gmail"});
        cboSendMethod.addActionListener(e -> toggleGmailFields());
        row1.add(cboSendMethod);
        formPanel.add(row1);

        JPanel row2 = new JPanel(new FlowLayout(FlowLayout.LEFT));
        row2.add(new JLabel("To:"));
        txtTo = new JTextField(30);
        row2.add(txtTo);
        formPanel.add(row2);

        JPanel row3 = new JPanel(new FlowLayout(FlowLayout.LEFT));
        row3.add(new JLabel("Subject:"));
        txtSubject = new JTextField(30);
        row3.add(txtSubject);
        formPanel.add(row3);

        JPanel row4 = new JPanel(new FlowLayout(FlowLayout.LEFT));
        row4.add(new JLabel("Gmail:"));
        txtGmailAddress = new JTextField(30);
        txtGmailAddress.setEnabled(false);
        row4.add(txtGmailAddress);
        formPanel.add(row4);

        JPanel row5 = new JPanel(new FlowLayout(FlowLayout.LEFT));
        row5.add(new JLabel("Password:"));
        txtGmailPassword = new JPasswordField(30);
        txtGmailPassword.setEnabled(false);
        row5.add(txtGmailPassword);
        formPanel.add(row5);

        panel.add(formPanel, BorderLayout.NORTH);

        // Body
        txtBody = new JTextArea(10, 40);
        txtBody.setFont(new Font("Consolas", Font.PLAIN, 12));
        JScrollPane bodyScroll = new JScrollPane(txtBody);
        bodyScroll.setBorder(BorderFactory.createTitledBorder("📝 Nội dung"));
        panel.add(bodyScroll, BorderLayout.CENTER);

        // Attachments
        JPanel attachPanel = new JPanel(new BorderLayout());
        attachmentListModel = new DefaultListModel<>();
        lstAttachments = new JList<>(attachmentListModel);
        JScrollPane attachScroll = new JScrollPane(lstAttachments);
        attachScroll.setPreferredSize(new Dimension(0, 100));
        attachPanel.add(attachScroll, BorderLayout.CENTER);

        JPanel attachBtnPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton btnAddFile = new JButton("📎 Thêm file");
        btnAddFile.addActionListener(e -> addAttachment());
        JButton btnRemoveFile = new JButton("❌ Xóa file");
        btnRemoveFile.addActionListener(e -> removeAttachment());
        attachBtnPanel.add(btnAddFile);
        attachBtnPanel.add(btnRemoveFile);
        attachPanel.add(attachBtnPanel, BorderLayout.SOUTH);

        attachPanel.setBorder(BorderFactory.createTitledBorder("📁 File đính kèm"));
        panel.add(attachPanel, BorderLayout.SOUTH);

        // Action buttons
        JPanel actionPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton btnSend = new JButton("📤 Gửi email");
        btnSend.setFont(new Font("Arial", Font.BOLD, 14));
        btnSend.setBackground(new Color(76, 175, 80));
        btnSend.setForeground(Color.WHITE);
        btnSend.addActionListener(e -> sendEmail());

        JButton btnCancel = new JButton("⬅️ Quay lại");
        btnCancel.addActionListener(e -> {
            cardLayout.show(cardPanel, "MAIN");
            loadInbox();
        });

        actionPanel.add(btnSend);
        actionPanel.add(btnCancel);
        panel.add(actionPanel, BorderLayout.EAST);

        return panel;
    }

    // ==================== METHODS ====================

    private void login() {
        String username = txtLoginUsername.getText().trim();
        String password = new String(txtLoginPassword.getPassword()).trim();

        if (username.isEmpty() || password.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Vui lòng nhập đầy đủ thông tin!", "Lỗi", JOptionPane.ERROR_MESSAGE);
            return;
        }

        currentUser = userDAO.login(username, password);
        if (currentUser != null) {
            JOptionPane.showMessageDialog(this, "✅ Đăng nhập thành công! Xin chào " + currentUser.getFullName());
            lblUserInfo.setText("👤 " + currentUser.getFullName() + " (" + currentUser.getEmail() + ")");
            cardLayout.show(cardPanel, "MAIN");
            loadInbox();
        } else {
            JOptionPane.showMessageDialog(this, "❌ Sai username hoặc password!", "Lỗi", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void register() {
        String username = txtRegUsername.getText().trim();
        String password = new String(txtRegPassword.getPassword()).trim();
        String email = txtRegEmail.getText().trim();
        String fullName = txtRegFullName.getText().trim();

        if (username.isEmpty() || password.isEmpty() || email.isEmpty() || fullName.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Vui lòng nhập đầy đủ thông tin!", "Lỗi", JOptionPane.ERROR_MESSAGE);
            return;
        }

        User user = new User(username, password, email, fullName);
        if (userDAO.register(user)) {
            JOptionPane.showMessageDialog(this, "✅ Đăng ký thành công! Hãy đăng nhập.");
            cardLayout.show(cardPanel, "LOGIN");
        } else {
            JOptionPane.showMessageDialog(this, "❌ Đăng ký thất bại! Username hoặc email đã tồn tại.", "Lỗi", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void logout() {
        currentUser = null;
        txtLoginUsername.setText("");
        txtLoginPassword.setText("");
        cardLayout.show(cardPanel, "LOGIN");
    }

    private void loadInbox() {
        if (currentUser == null) return;

        tableModel.setRowCount(0);
        List<Email> emails = emailDAO.getInbox(currentUser.getEmail());

        for (Email email : emails) {
            String status = email.isRead() ? "📖 Read" : "📕 Unread";
            String date = email.getSentAt() != null ? email.getSentAt().toString().substring(0, 16) : "N/A";
            tableModel.addRow(new Object[]{
                    email.getId(),
                    email.getSenderEmail(),
                    email.getSubject(),
                    date,
                    status
            });
        }
    }

    private void viewSelectedEmail() {
        int row = emailTable.getSelectedRow();
        if (row < 0) return;

        int emailId = (int) tableModel.getValueAt(row, 0);
        Email email = emailDAO.getEmailById(emailId);

        if (email != null) {
            StringBuilder content = new StringBuilder();
            content.append("From:    ").append(email.getSenderEmail()).append("\n");
            content.append("To:      ").append(email.getReceiverEmail()).append("\n");
            content.append("Subject: ").append(email.getSubject()).append("\n");
            content.append("Date:    ").append(email.getSentAt()).append("\n");
            content.append("────────────────────────────────────────\n\n");
            content.append(email.getBody());

            if (!email.getAttachments().isEmpty()) {
                content.append("\n\n📎 Attachments:\n");
                for (Attachment att : email.getAttachments()) {
                    content.append("  - ").append(att.getFileName())
                            .append(" (").append(fileService.getHumanReadableSize(att.getFileSize())).append(")\n");
                }
            }

            txtEmailContent.setText(content.toString());
            emailDAO.markAsRead(emailId);
            loadInbox();
        }
    }

    private void toggleGmailFields() {
        boolean isGmail = "Gmail".equals(cboSendMethod.getSelectedItem());
        txtGmailAddress.setEnabled(isGmail);
        txtGmailPassword.setEnabled(isGmail);
    }

    private void addAttachment() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setMultiSelectionEnabled(true);
        int result = fileChooser.showOpenDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            File[] files = fileChooser.getSelectedFiles();
            for (File file : files) {
                attachmentListModel.addElement(file);
            }
        }
    }

    private void removeAttachment() {
        int index = lstAttachments.getSelectedIndex();
        if (index >= 0) {
            attachmentListModel.remove(index);
        }
    }

    private void sendEmail() {
        String to = txtTo.getText().trim();
        String subject = txtSubject.getText().trim();
        String body = txtBody.getText().trim();

        if (to.isEmpty() || subject.isEmpty() || body.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Vui lòng nhập đầy đủ thông tin!", "Lỗi", JOptionPane.ERROR_MESSAGE);
            return;
        }

        String method = (String) cboSendMethod.getSelectedItem();
        List<File> attachments = new ArrayList<>();
        for (int i = 0; i < attachmentListModel.size(); i++) {
            attachments.add(attachmentListModel.getElementAt(i));
        }

        try {
            if ("SMTP Localhost".equals(method)) {
                Email email = new Email(currentUser.getEmail(), to, subject, body);
                email.setSenderId(currentUser.getId());
                int emailId = emailDAO.saveEmail(email);

                for (File file : attachments) {
                    fileService.uploadFile(emailId, file);
                }

                JOptionPane.showMessageDialog(this, "✅ Email đã gửi thành công qua SMTP Localhost!");

            } else {
                String gmailAddress = txtGmailAddress.getText().trim();
                String gmailPassword = new String(txtGmailPassword.getPassword()).trim();

                if (gmailAddress.isEmpty() || gmailPassword.isEmpty()) {
                    JOptionPane.showMessageDialog(this, "Vui lòng nhập Gmail và Password!", "Lỗi", JOptionPane.ERROR_MESSAGE);
                    return;
                }

                GmailService gmailService = new GmailService(gmailAddress, gmailPassword);
                boolean success = gmailService.sendEmail(to, subject, body, attachments);

                if (success) {
                    JOptionPane.showMessageDialog(this, "✅ Email đã gửi thành công qua Gmail!");
                } else {
                    JOptionPane.showMessageDialog(this, "❌ Gửi email thất bại!", "Lỗi", JOptionPane.ERROR_MESSAGE);
                }
            }

            txtTo.setText("");
            txtSubject.setText("");
            txtBody.setText("");
            attachmentListModel.clear();
            cardLayout.show(cardPanel, "MAIN");
            loadInbox();

        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "❌ Lỗi: " + e.getMessage(), "Lỗi", JOptionPane.ERROR_MESSAGE);
        }
    }

    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            e.printStackTrace();
        }

        SwingUtilities.invokeLater(() -> {
            MailClientGUI gui = new MailClientGUI();
            gui.setVisible(true);
        });
    }
}