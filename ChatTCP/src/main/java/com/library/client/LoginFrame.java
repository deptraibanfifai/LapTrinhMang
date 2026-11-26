package com.library.client;

import javax.swing.*;
import java.awt.*;

public class LoginFrame extends JFrame implements ChatClientListener {
    private JTextField txtHost, txtPort, txtUsername;
    private JButton btnConnect;
    private ChatClient chatClient;

    public LoginFrame() {
        setTitle("TCP Chat - Login");
        setSize(350, 300);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setLayout(new GridLayout(5, 1, 10, 10));
        getContentPane().setBackground(new Color(30, 31, 34));

        txtUsername = createStyledTextField("Username (e.g., Hieu)");
        txtHost = createStyledTextField("Host", "127.0.0.1");
        txtPort = createStyledTextField("Port", "5000");

        btnConnect = new JButton("CONNECT");
        btnConnect.setBackground(new Color(0, 123, 255));
        btnConnect.setForeground(Color.WHITE);
        btnConnect.setFont(new Font("Segoe UI", Font.BOLD, 14));
        btnConnect.addActionListener(e -> attemptLogin());

        add(createLabel("Username:")); add(txtUsername);
        add(createLabel("Host:")); add(txtHost);
        add(createLabel("Port:")); add(txtPort);
        add(btnConnect);
    }

    private JLabel createLabel(String text) {
        JLabel lbl = new JLabel("  " + text);
        lbl.setForeground(Color.WHITE);
        lbl.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        return lbl;
    }

    private JTextField createStyledTextField(String placeholder, String defaultVal) {
        JTextField tf = new JTextField(defaultVal);
        tf.setBackground(new Color(56, 58, 64));
        tf.setForeground(Color.WHITE);
        tf.setCaretColor(Color.WHITE);
        tf.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));
        return tf;
    }

    private JTextField createStyledTextField(String placeholder) {
        return createStyledTextField(placeholder, "");
    }

    private void attemptLogin() {
        String username = txtUsername.getText().trim();
        String host = txtHost.getText().trim();
        String portStr = txtPort.getText().trim();

        if (username.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Username không được để trống!", "Lỗi", JOptionPane.ERROR_MESSAGE);
            return;
        }

        int port;
        try {
            port = Integer.parseInt(portStr);
        } catch (NumberFormatException e) {
            JOptionPane.showMessageDialog(this, "Port phải là số!", "Lỗi", JOptionPane.ERROR_MESSAGE);
            return;
        }

        btnConnect.setEnabled(false);
        btnConnect.setText("Connecting...");

        chatClient = new ChatClient(host, port, this);
        chatClient.connect(username);
    }

    @Override
    public void onMessageReceived(String message) {
        if ("LOGIN_SUCCESS".equals(message)) {
            SwingUtilities.invokeLater(() -> {
                this.dispose();
                ChatFrame chatFrame = new ChatFrame(txtUsername.getText().trim(), chatClient);
                chatFrame.setVisible(true);
            });
        } else if ("LOGIN_FAILED".equals(message)) {
            SwingUtilities.invokeLater(() -> {
                JOptionPane.showMessageDialog(this, "Username đã tồn tại. Vui lòng chọn tên khác.", "Lỗi đăng nhập", JOptionPane.ERROR_MESSAGE);
                btnConnect.setEnabled(true);
                btnConnect.setText("CONNECT");
            });
        }
    }

    @Override
    public void onUserListUpdated(java.util.List<String> users) { /* Bỏ qua ở màn hình login */ }
    @Override
    public void onRoomListUpdated(java.util.List<String> rooms) { /* Bỏ qua ở màn hình login */ }

    @Override
    public void onDisconnected() {
        SwingUtilities.invokeLater(() -> {
            JOptionPane.showMessageDialog(this, "Mất kết nối đến Server.", "Disconnected", JOptionPane.WARNING_MESSAGE);
            btnConnect.setEnabled(true);
            btnConnect.setText("CONNECT");
        });
    }

    @Override
    public void onError(String errorMessage) {
        SwingUtilities.invokeLater(() -> {
            JOptionPane.showMessageDialog(this, errorMessage, "Lỗi kết nối", JOptionPane.ERROR_MESSAGE);
            btnConnect.setEnabled(true);
            btnConnect.setText("CONNECT");
        });
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new LoginFrame().setVisible(true));
    }
}