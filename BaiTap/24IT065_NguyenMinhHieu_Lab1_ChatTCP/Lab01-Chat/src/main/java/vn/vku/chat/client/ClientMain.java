package vn.vku.chat.client;

import javax.swing.*;

/** Mỗi lần chạy main này là một client độc lập. */
public final class ClientMain {
  public static void main(String[] args) throws Exception {
    UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
    SwingUtilities.invokeLater(() -> new ClientFrame().setVisible(true));
  }
}
