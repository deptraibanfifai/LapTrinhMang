// File: src/com/mail/Main.java
package com.mail;

import com.mail.client.MailClientGUI;
import com.mail.server.MailServerGUI;

import javax.swing.*;

public class Main {

    public static void main(String[] args) {
        // Set look and feel
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            e.printStackTrace();
        }

        String[] options = {"🖥️ Mail Server", "👤 Mail Client", "🚪 Thoát"};
        int choice = JOptionPane.showOptionDialog(
                null,
                "Chọn ứng dụng để khởi động:",
                "Mail Project - Java + MySQL",
                JOptionPane.DEFAULT_OPTION,
                JOptionPane.INFORMATION_MESSAGE,
                null,
                options,
                options[0]
        );

        switch (choice) {
            case 0:
                SwingUtilities.invokeLater(() -> {
                    MailServerGUI serverGUI = new MailServerGUI();
                    serverGUI.setVisible(true);
                });
                break;
            case 1:
                SwingUtilities.invokeLater(() -> {
                    MailClientGUI clientGUI = new MailClientGUI();
                    clientGUI.setVisible(true);
                });
                break;
            case 2:
            default:
                System.exit(0);
                break;
        }
    }
}