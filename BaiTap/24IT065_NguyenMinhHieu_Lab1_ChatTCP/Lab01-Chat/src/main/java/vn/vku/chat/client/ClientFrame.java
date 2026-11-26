package vn.vku.chat.client;

import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.*;
import java.time.LocalTime;
import java.util.UUID;
import java.util.concurrent.*;
import javax.swing.*;
import vn.vku.chat.common.*;
import vn.vku.chat.media.*;

/** Giao diện: sự kiện UI ở đây, socket ở ChatClient, media ở các service riêng. */
final class ClientFrame extends JFrame implements ChatClient.Listener, CallController.View {
  private enum Mode {
    ALL("Chat chung • TCP"),
    PRIVATE("Chat riêng • TCP"),
    BROADCAST("Broadcast LAN • UDP"),
    GROUP("Nhóm multicast • UDP");
    final String label;

    Mode(String label) {
      this.label = label;
    }

    @Override
    public String toString() {
      return label;
    }
  }

  private record Attachment(String sender, String kind, Path path, String fileName) {
    @Override
    public String toString() {
      return sender + " • " + fileName + (kind.equals("VOICE") ? " [ghi âm]" : "");
    }
  }

  private final JTextField host = new JTextField("127.0.0.1", 11),
      port = new JTextField("5500", 4),
      nickname = new JTextField("", 9);
  private final JComboBox<String> nic = new JComboBox<>(), recipient = new JComboBox<>();
  private final JComboBox<Mode> mode = new JComboBox<>(Mode.values());
  private final JButton connect = new JButton("Kết nối"), disconnect = new JButton("Ngắt");
  private final JButton send = new JButton("Gửi"),
      attach = new JButton("Đính kèm"),
      voice = new JButton("Ghi âm 10 giây");
  private final JButton audioCall = new JButton("Gọi thoại"),
      videoCall = new JButton("Gọi video"),
      hangup = new JButton("Kết thúc");
  private final JCheckBox join = new JCheckBox("Tham gia nhóm multicast");
  private final JTextArea history = new JTextArea(), input = new JTextArea(3, 30);
  private final DefaultListModel<String> users = new DefaultListModel<>();
  private final JList<String> userList = new JList<>(users);
  private final DefaultListModel<Attachment> files = new DefaultListModel<>();
  private final JList<Attachment> fileList = new JList<>(files);
  private final JLabel status = new JLabel("Chưa kết nối"),
      callLabel = new JLabel("Chưa có cuộc gọi");
  private final JLabel localVideo = videoBox("Webcam của bạn"),
      remoteVideo = videoBox("Webcam người kia");
  private volatile ChatClient client;
  private volatile CallController calls;
  private VoiceRecorder recorder;
  private boolean recording;
  private boolean connecting;
  private final ExecutorService disk =
      Executors.newSingleThreadExecutor(
          r -> {
            Thread t = new Thread(r, "file-worker");
            t.setDaemon(true);
            return t;
          });
  private final Path downloadDir =
      Path.of("downloads", "client-" + UUID.randomUUID().toString().substring(0, 8))
          .toAbsolutePath();
  // Chỉ giữ khung hình mới nhất, tránh làm nghẽn hàng đợi Swing khi máy yếu.
  private volatile BufferedImage latestLocal, latestRemote;

  ClientFrame() {
    super("Lab 01 • VKU LAN Chat");
    setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
    setMinimumSize(new Dimension(1000, 700));
    setSize(1220, 780);
    setLocationRelativeTo(null);
    try {
      for (var n : NetUtil.interfaces()) nic.addItem(n.getName());
    } catch (Exception e) {
      message(e.getMessage());
    }
    JPanel root = new JPanel(new BorderLayout(10, 10));
    root.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
    root.setBackground(new Color(239, 245, 253));
    setContentPane(root);
    JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT));
    top.add(new JLabel("Server"));
    top.add(host);
    top.add(new JLabel("Port"));
    top.add(port);
    top.add(new JLabel("Tên"));
    top.add(nickname);
    top.add(new JLabel("Card LAN"));
    top.add(nic);
    top.add(connect);
    top.add(disconnect);
    root.add(top, BorderLayout.NORTH);

    JPanel sidebar = new JPanel(new BorderLayout(6, 8));
    sidebar.setPreferredSize(new Dimension(210, 400));
    JLabel title = new JLabel("  NGƯỜI DÙNG ONLINE");
    title.setFont(title.getFont().deriveFont(Font.BOLD, 14f));
    sidebar.add(title, BorderLayout.NORTH);
    sidebar.add(new JScrollPane(userList));
    JTextArea hint =
        new JTextArea(
            "Chọn người để chat riêng.\n\n"
                + "UDP chỉ dùng cho text.\n"
                + "Đính kèm / gọi: dùng TCP.\n\n"
                + "Multicast: chỉ các client\n"
                + "đã tham gia nhận được tin.");
    hint.setEditable(false);
    hint.setOpaque(false);
    hint.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
    sidebar.add(hint, BorderLayout.SOUTH);

    JPanel middle = new JPanel(new BorderLayout(5, 8));
    JPanel routes = new JPanel(new FlowLayout(FlowLayout.LEFT));
    routes.add(mode);
    recipient.setPreferredSize(new Dimension(130, 28));
    routes.add(new JLabel("Người nhận"));
    routes.add(recipient);
    routes.add(join);
    middle.add(routes, BorderLayout.NORTH);
    history.setEditable(false);
    history.setLineWrap(true);
    history.setWrapStyleWord(true);
    history.setMargin(new Insets(12, 12, 12, 12));
    history.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 15));
    middle.add(new JScrollPane(history));
    JPanel compose = new JPanel(new BorderLayout(4, 6));
    input.setLineWrap(true);
    input.setWrapStyleWord(true);
    input.setFont(history.getFont());
    input.setMargin(new Insets(7, 7, 7, 7));
    compose.add(new JScrollPane(input));
    JPanel tools = new JPanel(new FlowLayout(FlowLayout.LEFT));
    JButton emojis = new JButton("Biểu cảm");
    tools.add(emojis);
    tools.add(attach);
    tools.add(voice);
    tools.add(send);
    compose.add(tools, BorderLayout.SOUTH);
    middle.add(compose, BorderLayout.SOUTH);

    JPanel right = new JPanel(new BorderLayout(5, 8));
    right.setPreferredSize(new Dimension(285, 500));
    JPanel media = new JPanel();
    media.setLayout(new BoxLayout(media, BoxLayout.Y_AXIS));
    media.add(callLabel);
    media.add(Box.createVerticalStrut(8));
    media.add(remoteVideo);
    media.add(Box.createVerticalStrut(6));
    media.add(localVideo);
    JPanel callButtons = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 6));
    callButtons.add(audioCall);
    callButtons.add(videoCall);
    callButtons.add(hangup);
    media.add(callButtons);
    right.add(media, BorderLayout.NORTH);
    JPanel downloads = new JPanel(new BorderLayout(4, 4));
    downloads.setBorder(BorderFactory.createTitledBorder("Đính kèm đã nhận / đã gửi"));
    downloads.add(new JScrollPane(fileList));
    JPanel fileButtons = new JPanel();
    JButton open = new JButton("Mở / phát"), folder = new JButton("Mở thư mục");
    fileButtons.add(open);
    fileButtons.add(folder);
    downloads.add(fileButtons, BorderLayout.SOUTH);
    right.add(downloads);
    JPanel center = new JPanel(new BorderLayout(10, 0));
    center.add(sidebar, BorderLayout.WEST);
    center.add(middle);
    center.add(right, BorderLayout.EAST);
    root.add(center);
    root.add(status, BorderLayout.SOUTH);

    connect.addActionListener(e -> connect());
    disconnect.addActionListener(
        e -> {
          if (client != null) client.close();
        });
    mode.addActionListener(e -> updateControls());
    join.addActionListener(
        e -> {
          try {
            requireClient().joinGroup(join.isSelected());
            message(join.isSelected() ? "Đã join " + Config.GROUP : "Đã leave nhóm multicast");
          } catch (Exception ex) {
            join.setSelected(false);
            error(ex);
          }
        });
    userList.addListSelectionListener(
        e -> {
          if (!e.getValueIsAdjusting()
              && client != null
              && userList.getSelectedValue() != null
              && !client.name.equals(userList.getSelectedValue())) {
            mode.setSelectedItem(Mode.PRIVATE);
            recipient.setSelectedItem(userList.getSelectedValue());
          }
        });
    send.addActionListener(e -> sendText());
    attach.addActionListener(e -> sendFile());
    voice.addActionListener(e -> recordVoice());
    audioCall.addActionListener(e -> invite(false));
    videoCall.addActionListener(e -> invite(true));
    hangup.addActionListener(
        e -> {
          if (calls != null) calls.hangup();
        });
    emojis.addActionListener(
        e -> {
          JPopupMenu popup = new JPopupMenu();
          for (String s :
              new String[] {"😀", "😂", "😍", "👍", "❤️", "😢", "🎉", "🔥", "😊", "👏"}) {
            JMenuItem item = new JMenuItem(s);
            item.addActionListener(
                ev -> {
                  input.append(s);
                  input.requestFocusInWindow();
                });
            popup.add(item);
          }
          popup.show(emojis, 0, -popup.getPreferredSize().height);
        });
    input
        .getInputMap()
        .put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, InputEvent.CTRL_DOWN_MASK), "send");
    input
        .getActionMap()
        .put(
            "send",
            new AbstractAction() {
              @Override
              public void actionPerformed(ActionEvent e) {
                sendText();
              }
            });
    open.addActionListener(e -> openAttachment());
    folder.addActionListener(
        e ->
            disk.submit(
                () -> {
                  try {
                    Files.createDirectories(downloadDir);
                    Desktop.getDesktop().open(downloadDir.toFile());
                  } catch (Exception ex) {
                    message("Không mở được thư mục: " + ex.getMessage());
                  }
                }));
    javax.swing.Timer previewTimer =
        new javax.swing.Timer(
            125,
            e -> {
              if (latestLocal != null) {
                localVideo.setText("");
                localVideo.setIcon(
                    new ImageIcon(latestLocal.getScaledInstance(256, 192, Image.SCALE_FAST)));
                latestLocal = null;
              }
              if (latestRemote != null) {
                remoteVideo.setText("");
                remoteVideo.setIcon(
                    new ImageIcon(latestRemote.getScaledInstance(256, 192, Image.SCALE_FAST)));
                latestRemote = null;
              }
            });
    previewTimer.start();
    addWindowListener(
        new WindowAdapter() {
          @Override
          public void windowClosing(WindowEvent e) {
            previewTimer.stop();
            if (recorder != null) recorder.close();
            if (calls != null) calls.close();
            if (client != null) client.close();
            disk.shutdownNow();
          }
        });
    message("Nhập server, tên không dấu cách và chọn đúng card Wi-Fi/LAN. Ctrl+Enter để gửi tin.");
    updateControls();
  }

  private static JLabel videoBox(String title) {
    JLabel box = new JLabel(title, SwingConstants.CENTER);
    box.setOpaque(true);
    box.setBackground(new Color(226, 234, 245));
    box.setPreferredSize(new Dimension(270, 190));
    box.setMaximumSize(new Dimension(270, 190));
    box.setMinimumSize(new Dimension(270, 190));
    return box;
  }

  private void connect() {
    String address = host.getText().trim(),
        name = nickname.getText().trim(),
        network = (String) nic.getSelectedItem();
    int tcpPort;
    try {
      tcpPort = Integer.parseInt(port.getText().trim());
      if (tcpPort < 1 || tcpPort > 65535) throw new Exception("Port cần từ 1 đến 65535");
    } catch (Exception ex) {
      error(ex);
      return;
    }
    if (!name.matches("[\\p{L}\\p{N}_-]{1,20}")) {
      error(new Exception("Tên gồm 1–20 chữ, số, _ hoặc -"));
      return;
    }
    connecting = true;
    updateControls();
    status.setText("Đang kết nối…");
    new SwingWorker<ChatClient, Void>() {
      @Override
      protected ChatClient doInBackground() throws Exception {
        return new ChatClient(address, tcpPort, name, NetUtil.choose(network), ClientFrame.this);
      }

      @Override
      protected void done() {
        connecting = false;
        try {
          client = get();
          calls = new CallController(client, ClientFrame.this);
          client.start();
          setTitle("Lab 01 • " + name);
          status.setText("Online: " + name + " | " + address + ":" + tcpPort + " | " + network);
          message("Đã kết nối. File nhận được lưu tại: " + downloadDir);
        } catch (Exception ex) {
          status.setText("Kết nối thất bại");
          error(ex.getCause() == null ? ex : ex.getCause());
        }
        updateControls();
      }
    }.execute();
  }

  private ChatClient requireClient() throws IOException {
    ChatClient c = client;
    if (c == null || !c.isOpen()) throw new IOException("Hãy kết nối server trước");
    return c;
  }

  private String tcpTarget() throws IOException {
    Mode selected = (Mode) mode.getSelectedItem();
    if (selected == Mode.ALL) return Config.ALL;
    if (selected != Mode.PRIVATE)
      throw new IOException("Đính kèm và media chỉ dùng trong chat TCP");
    String to = (String) recipient.getSelectedItem();
    if (to == null || to.isBlank()) throw new IOException("Hãy chọn người nhận");
    return to;
  }

  private void sendText() {
    try {
      ChatClient c = requireClient();
      String text = input.getText().trim();
      if (text.isEmpty()) return;
      if (text.length() > Config.MAX_TEXT) throw new IOException("Tối đa 2000 ký tự/tin");
      String type = "TEXT", to = Config.ALL;
      Mode selected = (Mode) mode.getSelectedItem();
      if (selected == Mode.PRIVATE) to = tcpTarget();
      if (selected == Mode.BROADCAST) type = "UDP_BROADCAST";
      if (selected == Mode.GROUP) {
        if (!join.isSelected()) throw new IOException("Bạn chưa tham gia nhóm multicast");
        type = "UDP_GROUP";
      }
      c.send(Packet.of(type, to, text));
      input.setText("");
      input.requestFocusInWindow();
    } catch (Exception ex) {
      error(ex);
    }
  }

  private void sendFile() {
    try {
      ChatClient c = requireClient();
      String to = tcpTarget();
      JFileChooser chooser = new JFileChooser();
      if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
      Path path = chooser.getSelectedFile().toPath();
      disk.submit(
          () -> {
            try {
              long size = Files.size(path);
              if (size <= 0 || size > Config.MAX_FILE)
                throw new IOException("File phải lớn hơn 0 và không quá 20 MiB");
              byte[] bytes;
              try (InputStream stream = Files.newInputStream(path)) {
                bytes = stream.readNBytes(Config.MAX_FILE + 1);
              }
              if (bytes.length == 0 || bytes.length > Config.MAX_FILE)
                throw new IOException("Kích thước file đã thay đổi hoặc vượt 20 MiB");
              c.send(
                  new Packet(
                      "FILE",
                      "",
                      to,
                      UUID.randomUUID().toString(),
                      path.getFileName().toString(),
                      bytes));
              message("Đã xếp hàng gửi file: " + path.getFileName());
            } catch (Exception ex) {
              message("Không gửi được file: " + ex.getMessage());
            }
          });
    } catch (Exception ex) {
      error(ex);
    }
  }

  private void recordVoice() {
    try {
      ChatClient c = requireClient();
      String to = tcpTarget();
      if (recording || (calls != null && calls.busy())) throw new IOException("Micro đang bận");
      VoiceRecorder capture = new VoiceRecorder();
      recorder = capture;
      recording = true;
      updateControls();
      message("Đang ghi âm 10 giây…");
      new SwingWorker<byte[], Void>() {
        @Override
        protected byte[] doInBackground() throws Exception {
          return capture.record();
        }

        @Override
        protected void done() {
          recording = false;
          recorder = null;
          updateControls();
          try {
            c.send(new Packet("VOICE", "", to, UUID.randomUUID().toString(), "voice.wav", get()));
          } catch (Exception ex) {
            error(ex.getCause() == null ? ex : ex.getCause());
          }
        }
      }.execute();
    } catch (Exception ex) {
      error(ex);
    }
  }

  private void invite(boolean video) {
    try {
      requireClient();
      if (mode.getSelectedItem() != Mode.PRIVATE)
        throw new IOException("Chọn Chat riêng và người nhận để gọi");
      if (recording) throw new IOException("Đang ghi âm, hãy chờ hoàn tất");
      calls.invite(tcpTarget(), video);
    } catch (Exception ex) {
      error(ex);
    }
  }

  @Override
  public void onPacket(Packet p) {
    if (p.media()) {
      CallController current = calls;
      if (current != null) current.receiveMedia(p);
      return;
    }
    if (p.type().equals("FILE") || p.type().equals("VOICE")) {
      saveAttachment(p);
      return;
    }
    SwingUtilities.invokeLater(
        () -> {
          switch (p.type()) {
            case "USERS" -> {
              Object selected = recipient.getSelectedItem();
              users.clear();
              recipient.removeAllItems();
              for (String name : p.text().split("\n"))
                if (!name.isBlank()) {
                  users.addElement(name);
                  if (client != null && !name.equals(client.name)) recipient.addItem(name);
                }
              if (selected != null)
                for (int i = 0; i < recipient.getItemCount(); i++)
                  if (selected.equals(recipient.getItemAt(i))) recipient.setSelectedIndex(i);
            }
            case "TEXT" ->
                message(
                    "[TCP "
                        + (p.target().equals(Config.ALL)
                            ? "chung"
                            : p.sender() + " → " + p.target())
                        + "] "
                        + p.sender()
                        + ": "
                        + p.text());
            case "BROADCAST", "MULTICAST" ->
                message("[UDP " + p.type() + "] " + p.sender() + ": " + p.text());
            case "CALL_INVITE", "CALL_ACCEPT", "CALL_REJECT", "CALL_END" -> {
              if (calls != null) calls.control(p);
            }
            default -> message("[" + p.type() + "] " + p.text());
          }
        });
  }

  private void saveAttachment(Packet p) {
    disk.submit(
        () -> {
          try {
            Files.createDirectories(downloadDir);
            String safe = p.text().replaceAll("[^\\p{L}\\p{N}._-]", "_");
            if (safe.isBlank()) safe = "attachment.bin";
            if (safe.length() > 90) safe = safe.substring(safe.length() - 90);
            Path destination = Files.createTempFile(downloadDir, "recv-", "-" + safe);
            Files.write(destination, p.data());
            Attachment item = new Attachment(p.sender(), p.type(), destination, p.text());
            SwingUtilities.invokeLater(
                () -> {
                  files.addElement(item);
                  message(
                      "[TCP "
                          + p.sender()
                          + " → "
                          + p.target()
                          + "] "
                          + p.type()
                          + ": "
                          + p.text()
                          + " ("
                          + p.data().length
                          + " bytes)");
                });
          } catch (IOException ex) {
            message("Không lưu được đính kèm: " + ex.getMessage());
          }
        });
  }

  private void openAttachment() {
    Attachment file = fileList.getSelectedValue();
    if (file == null) return;
    disk.submit(
        () -> {
          try {
            if (file.kind().equals("VOICE")) VoiceRecorder.play(file.path());
            else Desktop.getDesktop().open(file.path().toFile());
          } catch (Exception ex) {
            message("Không mở được file: " + ex.getMessage());
          }
        });
  }

  @Override
  public void onDisconnected(String reason) {
    SwingUtilities.invokeLater(
        () -> {
          if (calls != null) {
            calls.close();
            calls = null;
          }
          if (recorder != null) recorder.close();
          users.clear();
          recipient.removeAllItems();
          join.setSelected(false);
          status.setText(reason);
          message(reason);
          updateControls();
        });
  }

  private void updateControls() {
    boolean online = client != null && client.isOpen();
    Mode selected = (Mode) mode.getSelectedItem();
    boolean tcp = selected == Mode.ALL || selected == Mode.PRIVATE;
    boolean busy = calls != null && calls.busy();
    connect.setEnabled(!online && !connecting && !recording);
    disconnect.setEnabled(online);
    host.setEnabled(!online && !connecting);
    port.setEnabled(!online && !connecting);
    nickname.setEnabled(!online && !connecting);
    nic.setEnabled(!online && !connecting);
    mode.setEnabled(online);
    recipient.setEnabled(online && selected == Mode.PRIVATE);
    join.setEnabled(online);
    input.setEnabled(online);
    send.setEnabled(online);
    attach.setEnabled(online && tcp);
    voice.setEnabled(online && tcp && !recording && !busy);
    voice.setText(recording ? "Đang ghi âm…" : "Ghi âm 10 giây");
    audioCall.setEnabled(online && selected == Mode.PRIVATE && !busy && !recording);
    videoCall.setEnabled(audioCall.isEnabled());
    hangup.setEnabled(online && busy);
  }

  @Override
  public boolean confirmCall(String peer, boolean video) {
    if (recording) return false;
    return JOptionPane.showConfirmDialog(
            this,
            peer + " mời gọi " + (video ? "video + thoại" : "thoại") + ". Chấp nhận?",
            "Cuộc gọi đến",
            JOptionPane.YES_NO_OPTION)
        == JOptionPane.YES_OPTION;
  }

  @Override
  public void callStatus(String text, boolean busy) {
    callLabel.setText(text);
    if (!busy) {
      latestLocal = null;
      latestRemote = null;
      localVideo.setIcon(null);
      remoteVideo.setIcon(null);
      localVideo.setText("Webcam của bạn");
      remoteVideo.setText("Webcam người kia");
    }
    updateControls();
  }

  @Override
  public void video(BufferedImage image, boolean local) {
    if (local) latestLocal = image;
    else latestRemote = image;
  }

  @Override
  public void message(String text) {
    if (!SwingUtilities.isEventDispatchThread()) {
      SwingUtilities.invokeLater(() -> message(text));
      return;
    }
    history.append(LocalTime.now().withNano(0) + "  " + text + "\n");
    history.setCaretPosition(history.getDocument().getLength());
  }

  private void error(Throwable ex) {
    JOptionPane.showMessageDialog(this, ex.getMessage(), "Thông báo", JOptionPane.WARNING_MESSAGE);
  }
}
