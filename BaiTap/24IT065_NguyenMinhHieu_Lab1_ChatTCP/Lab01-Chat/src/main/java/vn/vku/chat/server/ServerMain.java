package vn.vku.chat.server;

import java.awt.*;
import java.awt.event.*;
import javax.swing.*;
import vn.vku.chat.common.*;

/** Chạy có GUI hoặc --console [port] [tên-card-mạng] để demo và kiểm thử. */
public final class ServerMain {
  public static void main(String[] args) throws Exception {
    if (args.length > 0 && args[0].equals("--console")) {
      ChatServer server = new ChatServer(System.out::println);
      server.start(
          args.length > 1 ? Integer.parseInt(args[1]) : Config.TCP_PORT,
          NetUtil.choose(args.length > 2 ? args[2] : ""));
      Runtime.getRuntime().addShutdownHook(new Thread(server::close));
      new java.util.concurrent.CountDownLatch(1).await();
      return;
    }
    UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
    SwingUtilities.invokeLater(ServerMain::show);
  }

  private static void show() {
    JFrame frame = new JFrame("Lab 01 • Chat Server");
    JTextArea logs = new JTextArea();
    logs.setEditable(false);
    logs.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 14));
    JTextField port = new JTextField("5500", 6);
    JComboBox<String> nic = new JComboBox<>();
    try {
      for (var n : NetUtil.interfaces()) nic.addItem(n.getName());
    } catch (Exception e) {
      logs.append(e.getMessage());
    }
    JButton start = new JButton("START SERVER"), stop = new JButton("STOP");
    stop.setEnabled(false);
    JPanel top = new JPanel();
    top.add(new JLabel("TCP port"));
    top.add(port);
    top.add(new JLabel("Card LAN/Wi-Fi"));
    top.add(nic);
    top.add(start);
    top.add(stop);
    ChatServer[] current = new ChatServer[1];
    start.addActionListener(
        e -> {
          try {
            ChatServer s =
                new ChatServer(
                    t ->
                        SwingUtilities.invokeLater(
                            () -> {
                              logs.append(t + "\n");
                              logs.setCaretPosition(logs.getDocument().getLength());
                            }));
            try {
              s.start(
                  Integer.parseInt(port.getText().trim()),
                  NetUtil.choose((String) nic.getSelectedItem()));
            } catch (Exception ex) {
              s.close();
              throw ex;
            }
            current[0] = s;
            start.setEnabled(false);
            stop.setEnabled(true);
            port.setEnabled(false);
            nic.setEnabled(false);
          } catch (Exception ex) {
            JOptionPane.showMessageDialog(
                frame, ex.getMessage(), "Không khởi động được", JOptionPane.ERROR_MESSAGE);
          }
        });
    stop.addActionListener(
        e -> {
          current[0].close();
          current[0] = null;
          start.setEnabled(true);
          stop.setEnabled(false);
          port.setEnabled(true);
          nic.setEnabled(true);
        });
    frame.add(top, BorderLayout.NORTH);
    frame.add(new JScrollPane(logs));
    frame.setSize(850, 520);
    frame.setLocationRelativeTo(null);
    frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
    frame.addWindowListener(
        new WindowAdapter() {
          @Override
          public void windowClosing(WindowEvent e) {
            if (current[0] != null) current[0].close();
          }
        });
    frame.setVisible(true);
  }
}
