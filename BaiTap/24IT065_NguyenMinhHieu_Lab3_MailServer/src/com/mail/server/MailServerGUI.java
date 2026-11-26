// File: src/com/mail/server/MailServerGUI.java
package com.mail.server;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

public class MailServerGUI extends JFrame {

    private MailServer mailServer;
    private JTextArea logArea;
    private JButton btnStart, btnStop;
    private JLabel lblStatus, lblSmtpPort, lblPop3Port;
    private JTextField txtSmtpPort, txtPop3Port;
    private boolean isRunning = false;

    public MailServerGUI() {
        initUI();
    }

    private void initUI() {
        setTitle("🖥️ Mail Server - SMTP & POP3");
        setSize(700, 600);
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        setLocationRelativeTo(null);

        // Sử dụng BorderLayout với spacing
        setLayout(new BorderLayout(5, 5));

        // === NORTH: Panel cấu hình port ===
        JPanel configPanel = new JPanel(new GridLayout(2, 4, 10, 5));
        configPanel.setBorder(new EmptyBorder(10, 10, 5, 10));
        configPanel.setBackground(new Color(240, 240, 240));

        configPanel.add(new JLabel("SMTP Port:"));
        txtSmtpPort = new JTextField("2525");
        configPanel.add(txtSmtpPort);

        configPanel.add(new JLabel("POP3 Port:"));
        txtPop3Port = new JTextField("1110");
        configPanel.add(txtPop3Port);

        add(configPanel, BorderLayout.NORTH);

        // === CENTER: Panel chứa nút + Log area ===
        JPanel centerPanel = new JPanel(new BorderLayout(5, 5));
        centerPanel.setBorder(new EmptyBorder(5, 10, 5, 10));

        // Panel nút điều khiển (ở trên cùng của center)
        JPanel controlPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 20, 10));
        controlPanel.setBackground(new Color(240, 240, 240));

        btnStart = new JButton("▶️ Khởi động Server");
        btnStart.setFont(new Font("Arial", Font.BOLD, 14));
        btnStart.setBackground(new Color(76, 175, 80));
        btnStart.setForeground(Color.WHITE);
        btnStart.setPreferredSize(new Dimension(200, 40));
        btnStart.addActionListener(e -> startServer());

        btnStop = new JButton("⏹️ Dừng Server");
        btnStop.setFont(new Font("Arial", Font.BOLD, 14));
        btnStop.setBackground(new Color(244, 67, 54));
        btnStop.setForeground(Color.WHITE);
        btnStop.setPreferredSize(new Dimension(200, 40));
        btnStop.setEnabled(false);
        btnStop.addActionListener(e -> stopServer());

        controlPanel.add(btnStart);
        controlPanel.add(btnStop);

        centerPanel.add(controlPanel, BorderLayout.NORTH);

        // Log area (chiếm phần lớn center)
        logArea = new JTextArea();
        logArea.setEditable(false);
        logArea.setFont(new Font("Consolas", Font.PLAIN, 12));
        logArea.setBackground(Color.BLACK);
        logArea.setForeground(Color.GREEN);
        JScrollPane scrollPane = new JScrollPane(logArea);
        scrollPane.setBorder(BorderFactory.createTitledBorder("📋 Server Log"));

        centerPanel.add(scrollPane, BorderLayout.CENTER);

        add(centerPanel, BorderLayout.CENTER);

        // === SOUTH: Panel trạng thái ===
        JPanel statusPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        statusPanel.setBorder(new EmptyBorder(5, 10, 10, 10));
        statusPanel.setBackground(new Color(240, 240, 240));

        lblStatus = new JLabel("⚪ Trạng thái: Chưa khởi động");
        lblStatus.setFont(new Font("Arial", Font.BOLD, 12));
        statusPanel.add(lblStatus);

        lblSmtpPort = new JLabel("SMTP: -");
        statusPanel.add(lblSmtpPort);

        lblPop3Port = new JLabel("POP3: -");
        statusPanel.add(lblPop3Port);

        add(statusPanel, BorderLayout.SOUTH);

        // Xử lý đóng window
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                if (isRunning) {
                    int confirm = JOptionPane.showConfirmDialog(
                            MailServerGUI.this,
                            "Server đang chạy. Bạn có muốn dừng server trước khi thoát?",
                            "Xác nhận thoát",
                            JOptionPane.YES_NO_OPTION
                    );
                    if (confirm == JOptionPane.YES_OPTION) {
                        stopServer();
                    }
                }
                System.exit(0);
            }
        });

        appendLog("=== Mail Server GUI đã sẵn sàng ===\n");
        appendLog("Nhấn 'Khởi động Server' để bắt đầu...\n");
    }

    private void startServer() {
        if (isRunning) {
            JOptionPane.showMessageDialog(this,
                    "Server đang chạy rồi!",
                    "Thông báo",
                    JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        try {
            int smtpPort = Integer.parseInt(txtSmtpPort.getText().trim());
            int pop3Port = Integer.parseInt(txtPop3Port.getText().trim());

            if (smtpPort < 1024 || pop3Port < 1024) {
                JOptionPane.showMessageDialog(this,
                        "Port phải lớn hơn 1024!",
                        "Lỗi",
                        JOptionPane.ERROR_MESSAGE);
                return;
            }

            appendLog("\n🚀 Đang khởi động Mail Server...\n");
            appendLog("SMTP Port: " + smtpPort + "\n");
            appendLog("POP3 Port: " + pop3Port + "\n");

            // Disable nút start ngay lập tức
            btnStart.setEnabled(false);
            txtSmtpPort.setEnabled(false);
            txtPop3Port.setEnabled(false);

            mailServer = new MailServer(smtpPort, pop3Port);

            // Chạy server trên thread riêng
            Thread serverThread = new Thread(() -> {
                try {
                    mailServer.start();
                    isRunning = true;

                    SwingUtilities.invokeLater(() -> {
                        btnStop.setEnabled(true);
                        lblStatus.setText("🟢 Trạng thái: Đang chạy");
                        lblStatus.setForeground(Color.GREEN);
                        lblSmtpPort.setText("SMTP: " + smtpPort);
                        lblPop3Port.setText("POP3: " + pop3Port);
                        appendLog("✅ Server đã khởi động thành công!\n");
                    });

                } catch (Exception e) {
                    isRunning = false;
                    SwingUtilities.invokeLater(() -> {
                        appendLog("❌ Lỗi khởi động: " + e.getMessage() + "\n");
                        lblStatus.setText("🔴 Trạng thái: Lỗi");
                        lblStatus.setForeground(Color.RED);
                        btnStart.setEnabled(true);
                        txtSmtpPort.setEnabled(true);
                        txtPop3Port.setEnabled(true);
                    });
                }
            });

            serverThread.setDaemon(true);
            serverThread.start();

        } catch (NumberFormatException e) {
            JOptionPane.showMessageDialog(this,
                    "Port phải là số!",
                    "Lỗi",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    private void stopServer() {
        if (mailServer != null) {
            appendLog("\n⏹️ Đang dừng server...\n");
            mailServer.stop();
            isRunning = false;

            btnStart.setEnabled(true);
            btnStop.setEnabled(false);
            txtSmtpPort.setEnabled(true);
            txtPop3Port.setEnabled(true);
            lblStatus.setText("⚪ Trạng thái: Đã dừng");
            lblStatus.setForeground(Color.BLACK);
            lblSmtpPort.setText("SMTP: -");
            lblPop3Port.setText("POP3: -");

            appendLog("✅ Server đã dừng.\n");
        }
    }

    private void appendLog(String message) {
        SwingUtilities.invokeLater(() -> {
            logArea.append(message);
            logArea.setCaretPosition(logArea.getDocument().getLength());
        });
    }

    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            e.printStackTrace();
        }

        SwingUtilities.invokeLater(() -> {
            MailServerGUI gui = new MailServerGUI();
            gui.setVisible(true);
        });
    }
}