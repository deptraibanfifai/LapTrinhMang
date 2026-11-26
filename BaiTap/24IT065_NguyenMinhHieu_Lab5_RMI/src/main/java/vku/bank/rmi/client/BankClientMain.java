package vku.bank.rmi.client;

import javax.swing.*;

public final class BankClientMain {
    private BankClientMain() {
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignored) {
            }
            BankClientGUI gui = new BankClientGUI();
            gui.setVisible(true);
        });
    }
}
