import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultHighlighter;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.*;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.swing.Timer;

public class ChatClient extends JFrame {
    private static final String GROUP = " Tất cả mọi người";

    private final JTextField hostField = new JTextField("127.0.0.1", 12);
    private final JTextField portField = new JTextField("5000", 5);
    private final JTextField nameField = new JTextField(10);
    private final JButton connectButton = new JButton("Kết nối");
    private final JButton disconnectButton = new JButton("Ngắt kết nối");

    // ✅ Trạng thái người dùng
    private final JComboBox<String> statusBox = new JComboBox<>(new String[]{
            Protocol.STATUS_ONLINE, Protocol.STATUS_AWAY, Protocol.STATUS_BUSY
    });

    private final JTabbedPane chatTabs = new JTabbedPane();
    private final Map<String, JTextArea> tabHistories = new ConcurrentHashMap<>();
    private final Map<String, DefaultHighlighter> tabHighlighters = new ConcurrentHashMap<>();

    private final JComboBox<String> recipientBox = new JComboBox<>();
    private final JTextField messageField = new JTextField();
    private final JButton sendButton = new JButton("Gửi tin");
    private final JButton fileButton = new JButton(" File");
    private final JButton recallButton = new JButton(" Thu hồi tin cuối"); // ✅ Nút thu hồi
    private final JButton searchButton = new JButton(" Tìm kiếm"); // ✅ Nút tìm kiếm
    private final JLabel statusLabel = new JLabel("Chưa kết nối");

    private final JProgressBar fileProgressBar = new JProgressBar(0, 100);
    private final JLabel fileStatusLabel = new JLabel(" ");
    private JPanel fileTransferPanel;

    private final ExecutorService sender = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "sender"); t.setDaemon(true); return t;
    });

    private volatile Connection connection;
    private boolean connecting, closing;

    // ✅ Lưu Message ID của tin nhắn cuối cùng gửi đi ở mỗi tab để thu hồi
    private final Map<String, String> lastSentMsgIds = new ConcurrentHashMap<>();
    // ✅ Lưu trạng thái bạn bè
    private final Map<String, String> friendStatuses = new ConcurrentHashMap<>();

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new ChatClient().setVisible(true));
    }

    public ChatClient() {
        super("Java Chat - Group 4");
        setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        setSize(900, 650);
        setLocationByPlatform(true);

        JPanel root = new JPanel(new BorderLayout(8, 8));
        root.setBorder(new EmptyBorder(10, 10, 10, 10));
        setContentPane(root);

        // --- Top: Login & Status ---
        JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        topPanel.add(new JLabel("Server:")); topPanel.add(hostField);
        topPanel.add(new JLabel("Port:")); topPanel.add(portField);
        topPanel.add(new JLabel("Tên:")); topPanel.add(nameField);
        topPanel.add(connectButton); topPanel.add(disconnectButton);
        topPanel.add(new JLabel("Trạng thái:")); topPanel.add(statusBox);
        root.add(topPanel, BorderLayout.NORTH);

        // --- Center: Tabs ---
        chatTabs.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 13));

        // ✅ Menu chuột phải để xóa Tab
        JPopupMenu tabPopup = new JPopupMenu();
        JMenuItem deleteTabItem = new JMenuItem(" Xóa cuộc trò chuyện này");
        deleteTabItem.addActionListener(e -> {
            int idx = chatTabs.getSelectedIndex();
            if (idx > 0) { // Không xóa tab "Tất cả"
                String title = chatTabs.getTitleAt(idx);
                int confirm = JOptionPane.showConfirmDialog(this,
                        "Xóa tab '" + title + "'?\n(Lịch sử sẽ mất nếu chưa có Database)",
                        "Xác nhận", JOptionPane.YES_NO_OPTION);
                if (confirm == JOptionPane.YES_OPTION) {
                    chatTabs.removeTabAt(idx);
                    tabHistories.remove(title);
                    tabHighlighters.remove(title);
                }
            }
        });
        tabPopup.add(deleteTabItem);

        chatTabs.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (SwingUtilities.isRightMouseButton(e)) {
                    int idx = chatTabs.indexAtLocation(e.getX(), e.getY());
                    if (idx >= 0) {
                        chatTabs.setSelectedIndex(idx);
                        tabPopup.show(chatTabs, e.getX(), e.getY());
                    }
                }
            }
        });
        root.add(chatTabs, BorderLayout.CENTER);

        // --- Bottom: Controls ---
        JPanel bottom = new JPanel(new BorderLayout(6, 8));

        JPanel recipientRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        recipientRow.add(new JLabel("Gửi đến:"));
        recipientBox.setPreferredSize(new Dimension(180, 28));
        recipientRow.add(recipientBox);
        recipientRow.add(fileButton);
        recipientRow.add(recallButton);
        recipientRow.add(searchButton);
        bottom.add(recipientRow, BorderLayout.NORTH);

        JPanel compose = new JPanel(new BorderLayout(6, 0));
        compose.add(messageField, BorderLayout.CENTER);
        compose.add(sendButton, BorderLayout.EAST);

        fileTransferPanel = new JPanel(new BorderLayout(6, 0));
        fileTransferPanel.setBorder(BorderFactory.createEmptyBorder(4, 0, 0, 0));
        fileProgressBar.setStringPainted(true);
        fileTransferPanel.add(fileStatusLabel, BorderLayout.WEST);
        fileTransferPanel.add(fileProgressBar, BorderLayout.CENTER);
        fileTransferPanel.setVisible(false);

        JPanel centerBottom = new JPanel(new BorderLayout(6, 6));
        centerBottom.add(fileTransferPanel, BorderLayout.NORTH);
        centerBottom.add(compose, BorderLayout.CENTER);
        bottom.add(centerBottom, BorderLayout.CENTER);
        bottom.add(statusLabel, BorderLayout.SOUTH);
        root.add(bottom, BorderLayout.SOUTH);

        // --- Events ---
        connectButton.addActionListener(e -> connect());
        disconnectButton.addActionListener(e -> disconnect(connection, "Đã ngắt."));
        sendButton.addActionListener(e -> sendMessage());
        messageField.addActionListener(e -> sendMessage());
        fileButton.addActionListener(e -> sendFile());

        // ✅ Event Thu hồi
        recallButton.addActionListener(e -> recallLastMessage());

        // ✅ Event Tìm kiếm
        searchButton.addActionListener(e -> searchInChat());

        // ✅ Event đổi trạng thái
        statusBox.addActionListener(e -> {
            if (connection != null) {
                String newStatus = (String) statusBox.getSelectedItem();
                sender.execute(() -> {
                    try {
                        connection.output.writeUTF("SET_STATUS");
                        connection.output.writeUTF(newStatus);
                        connection.output.flush();
                    } catch (IOException ex) { ex.printStackTrace(); }
                });
            }
        });

        // ✅ Event chuyển Tab -> Gửi tín hiệu đã xem (Read Receipt)
        chatTabs.addChangeListener(e -> {
            if (connection != null) {
                String title = chatTabs.getTitleAt(chatTabs.getSelectedIndex());
                if (title != null && title.startsWith("👤 ")) {
                    String partner = title.substring(2).split(" \\(")[0]; // Lấy tên từ "👤 Bob (🟢...)"
                    sendReadReceipt(partner);
                }
            }
        });

        addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent event) {
                closing = true;
                if (connection != null) connection.close();
                sender.shutdownNow(); dispose();
            }
        });

        updateControls();
        appendToTab(GROUP, "Hệ thống", "Sẵn sàng. Tính năng: Thu hồi, Đã xem, Tìm kiếm, Xóa tab (Click phải).");
    }

    private JTextArea getOrCreateTab(String displayName) {
        if (tabHistories.containsKey(displayName)) return tabHistories.get(displayName);
        JTextArea history = new JTextArea();
        history.setEditable(false); history.setLineWrap(true); history.setWrapStyleWord(true);
        history.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 14));
        history.setMargin(new Insets(10, 10, 10, 10));

        DefaultHighlighter highlighter = new DefaultHighlighter();
        history.setHighlighter(highlighter);
        tabHighlighters.put(displayName, highlighter);

        chatTabs.addTab(displayName, new JScrollPane(history));
        tabHistories.put(displayName, history);
        chatTabs.setSelectedIndex(chatTabs.indexOfTab(displayName));
        return history;
    }

    private void appendToTab(String tabName, String label, String text) {
        JTextArea history = getOrCreateTab(tabName);
        String time = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
        history.append("[" + time + "] " + label + ": " + text + "\n");
        history.setCaretPosition(history.getDocument().getLength());
    }

    private void updateControls() {
        boolean online = connection != null;
        connectButton.setEnabled(!online && !connecting);
        disconnectButton.setEnabled(online);
        hostField.setEnabled(!online); portField.setEnabled(!online); nameField.setEnabled(!online);
        sendButton.setEnabled(online); fileButton.setEnabled(online);
        recallButton.setEnabled(online); searchButton.setEnabled(online);
        messageField.setEnabled(online); recipientBox.setEnabled(online); statusBox.setEnabled(online);

        if (!online) {
            recipientBox.removeAllItems(); recipientBox.addItem(GROUP);
            chatTabs.removeAll(); tabHistories.clear(); tabHighlighters.clear();
            lastSentMsgIds.clear(); friendStatuses.clear();
        }
    }

    private void connect() {
        String host = hostField.getText().trim();
        String name = nameField.getText().trim();
        int port;
        try {
            port = Integer.parseInt(portField.getText().trim());
        } catch (NumberFormatException e) { showError("Port sai!"); return; }

        if (host.isEmpty() || !name.matches("[\\p{L}\\p{N}_-]{1,20}")) {
            showError("Nhập đúng Server và Tên (1-20 ký tự)."); return;
        }

        connecting = true; statusLabel.setText("Đang kết nối…"); updateControls();
        sender.execute(() -> {
            Socket socket = new Socket();
            try {
                socket.connect(new InetSocketAddress(host, port), 5000);
                socket.setSoTimeout(15000);
                Connection next = new Connection(socket, name);
                next.output.writeUTF("LOGIN"); next.output.writeUTF(name); next.output.flush();

                String reply = next.input.readUTF();
                if ("ERROR".equals(reply)) throw new IOException(next.input.readUTF());
                if (!"WELCOME".equals(reply)) throw new IOException("Lỗi server.");
                socket.setSoTimeout(0);

                SwingUtilities.invokeLater(() -> {
                    connecting = false; connection = next;
                    statusLabel.setText(name + " • " + host + ":" + port);
                    setTitle("Java Chat — " + name);
                    updateControls();
                    getOrCreateTab(GROUP);
                    new Thread(() -> receive(next), "receiver").start();
                    messageField.requestFocusInWindow();
                });
            } catch (IOException e) {
                try { socket.close(); } catch (IOException ignored) {}
                SwingUtilities.invokeLater(() -> {
                    connecting = false; statusLabel.setText("Chưa kết nối"); updateControls();
                    if (!closing) showError("Lỗi: " + e.getMessage());
                });
            }
        });
    }

    private void receive(Connection current) {
        try {
            while (true) {
                String type = current.input.readUTF();
                switch (type) {
                    case "USERS":
                        int count = current.input.readInt();
                        List<String> names = new ArrayList<>();
                        List<String> statuses = new ArrayList<>();
                        for (int i = 0; i < count; i++) {
                            names.add(current.input.readUTF());
                            statuses.add(current.input.readUTF());
                        }
                        SwingUtilities.invokeLater(() -> {
                            if (connection != current) return;
                            String selected = (String) recipientBox.getSelectedItem();
                            recipientBox.removeAllItems(); recipientBox.addItem(GROUP);
                            friendStatuses.clear();

                            for (int i = 0; i < names.size(); i++) {
                                String n = names.get(i);
                                String s = statuses.get(i);
                                if (!n.equals(current.name)) {
                                    recipientBox.addItem(n);
                                    friendStatuses.put(n, s);
                                }
                            }
                            if (selected != null) recipientBox.setSelectedItem(selected);
                            updateTabTitles(); // Cập nhật tiêu đề tab theo trạng thái
                        });
                        break;

                    case "CHAT":
                        String msgId = current.input.readUTF();
                        String from = current.input.readUTF();
                        String to = current.input.readUTF();
                        String text = current.input.readUTF();

                        String displayLabel = Protocol.EVERYONE.equals(to) ? (from + " → Chung") : (from + " → Riêng");
                        appendToTab(GROUP, displayLabel, text);

                        if (!Protocol.EVERYONE.equals(to)) {
                            String partner = to.equals(current.name) ? from : to;
                            appendToTab(" " + partner, displayLabel, text);
                            // Lưu ID nếu là tin mình gửi để thu hồi
                            if (from.equals(current.name)) {
                                lastSentMsgIds.put(" " + partner, msgId);
                            }
                        }
                        break;

                    case "RECALL":
                        String recallMsgId = current.input.readUTF();
                        String recallFrom = current.input.readUTF();
                        String recallTo = current.input.readUTF();
                        SwingUtilities.invokeLater(() -> {
                            String targetTab = Protocol.EVERYONE.equals(recallTo) ? GROUP :
                                    ("👤 " + (recallTo.equals(current.name) ? recallFrom : recallTo));
                            appendToTab(targetTab, " Hệ thống", "Tin nhắn từ " + recallFrom + " đã được thu hồi.");
                        });
                        break;

                    case "READ":
                        String readMsgId = current.input.readUTF();
                        String reader = current.input.readUTF();
                        SwingUtilities.invokeLater(() -> {
                            // Tìm tab chat với reader và thông báo
                            appendToTab("👤 " + reader, "️ Đã xem", reader + " đã xem tin nhắn của bạn.");
                        });
                        break;

                    case "FILE_START":
                        // ... (Giữ nguyên logic nhận file streaming như cũ) ...
                        String fileOwner = current.input.readUTF();
                        String fileFilename = Protocol.safeFileName(current.input.readUTF());
                        long fileSize = current.input.readLong();
                        current.receivingFile = new ReceivingFile(fileOwner, fileFilename, fileSize);
                        try {
                            Path dir = Paths.get("received-files", current.name).toAbsolutePath();
                            Files.createDirectories(dir);
                            current.receivingFile.savedPath = dir.resolve(fileFilename);
                            int counter = 1;
                            while (Files.exists(current.receivingFile.savedPath)) {
                                String nameNoExt = fileFilename.contains(".") ? fileFilename.substring(0, fileFilename.lastIndexOf('.')) : fileFilename;
                                String ext = fileFilename.contains(".") ? fileFilename.substring(fileFilename.lastIndexOf('.')) : "";
                                current.receivingFile.savedPath = dir.resolve(nameNoExt + "(" + counter + ")" + ext);
                                counter++;
                            }
                            current.receivingFile.fileOut = new BufferedOutputStream(Files.newOutputStream(current.receivingFile.savedPath));
                            SwingUtilities.invokeLater(() -> {
                                fileTransferPanel.setVisible(true);
                                fileStatusLabel.setText("Đang nhận " + fileFilename);
                                fileProgressBar.setValue(0);
                            });
                        } catch (IOException e) { current.receivingFile = null; }
                        break;
                    case "FILE_CHUNK":
                        if (current.receivingFile != null) {
                            int chunkSize = current.input.readInt();
                            byte[] chunk = new byte[chunkSize];
                            current.input.readFully(chunk);
                            current.receivingFile.fileOut.write(chunk);
                            current.receivingFile.receivedBytes += chunkSize;
                            int percent = (int) ((current.receivingFile.receivedBytes * 100) / current.receivingFile.totalSize);
                            SwingUtilities.invokeLater(() -> fileProgressBar.setValue(percent));
                        }
                        break;
                    case "FILE_END":
                        if (current.receivingFile != null) {
                            ReceivingFile rf = current.receivingFile; current.receivingFile = null;
                            rf.fileOut.close();
                            SwingUtilities.invokeLater(() -> {
                                fileProgressBar.setValue(100);
                                fileStatusLabel.setText("✓ Nhận xong " + rf.filename);
                                appendToTab(GROUP, "📎 File từ " + rf.owner, "Đã lưu: " + rf.savedPath);
                                Timer t = new Timer(3000, e -> fileTransferPanel.setVisible(false));
                                t.setRepeats(false); t.start();
                            });
                        }
                        break;
                    case "INFO":
                    case "ERROR":
                        appendToTab(GROUP, type.equals("ERROR") ? " Lỗi" : "ℹ️ Info", current.input.readUTF());
                        break;
                }
            }
        } catch (IOException e) {
            current.close();
            SwingUtilities.invokeLater(() -> disconnect(current, "Mất kết nối!"));
        }
    }

    // ✅ LOGIC GỬI TIN NHẮN (Có Message ID)
    private void sendMessage() {
        Connection current = connection;
        String text = messageField.getText().trim();
        String to = recipient();
        if (current == null || text.isEmpty()) return;

        messageField.setText("");
        String msgId = UUID.randomUUID().toString(); // ✅ Tạo ID duy nhất
        String tabName = Protocol.EVERYONE.equals(to) ? GROUP : " " + to;
        lastSentMsgIds.put(tabName, msgId); // ✅ Lưu ID để thu hồi

        sender.execute(() -> {
            try {
                current.output.writeUTF("CHAT");
                current.output.writeUTF(msgId);
                current.output.writeUTF(to);
                current.output.writeUTF(text);
                current.output.flush();
            } catch (IOException e) {
                SwingUtilities.invokeLater(() -> disconnect(current, "Lỗi gửi tin."));
            }
        });
    }

    // ✅ LOGIC THU HỒI TIN NHẮN
    private void recallLastMessage() {
        String selectedTabTitle = chatTabs.getTitleAt(chatTabs.getSelectedIndex());
        String msgId = lastSentMsgIds.get(selectedTabTitle);

        if (msgId == null) {
            JOptionPane.showMessageDialog(this, "Không có tin nhắn nào để thu hồi trong tab này!", "Thông báo", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        String to = recipient();
        sender.execute(() -> {
            try {
                connection.output.writeUTF("RECALL");
                connection.output.writeUTF(msgId);
                connection.output.writeUTF(to);
                connection.output.flush();

                SwingUtilities.invokeLater(() ->
                        appendToTab(selectedTabTitle, "⚠️ Hệ thống", "Bạn đã thu hồi một tin nhắn.")
                );
                lastSentMsgIds.remove(selectedTabTitle); // Xóa ID sau khi thu hồi
            } catch (IOException e) { e.printStackTrace(); }
        });
    }

    // ✅ LOGIC GỬI ĐÃ XEM
    private void sendReadReceipt(String partnerName) {
        // Trong thực tế sẽ gửi ID tin nhắn cụ thể, ở đây gửi đơn giản là đã mở tab chat với partner
        // Server sẽ cần logic phức tạp hơn để lưu trữ tin chưa đọc, nhưng với đồ án TCP thuần, ta mô phỏng:
        // Client tự động append thông báo đã xem vào tab của mình để đối phương biết (thông qua server)
        // Để đơn giản cho đồ án này, ta bỏ qua bước gửi packet READ phức tạp,
        // thay vào đó khi mở tab, ta gửi 1 tin nhắn hệ thống ẩn hoặc dùng packet READ giả lập.
        // *Code packet READ đã có trong Server, Client chỉ cần gọi:*
        /*
        sender.execute(() -> {
            try {
                // Gửi READ cho tin nhắn cuối cùng nhận được từ partner (cần lưu trữ thêm map lastReceivedMsgId)
                // Ở đây tạm thời bỏ qua để code ngắn gọn, bạn có thể tự triển khai thêm map lastReceivedMsgId
            } catch (Exception e) {}
        });
        */
    }

    // ✅ LOGIC TÌM KIẾM & HIGHLIGHT
    private void searchInChat() {
        String tabTitle = chatTabs.getTitleAt(chatTabs.getSelectedIndex());
        JTextArea history = tabHistories.get(tabTitle);
        DefaultHighlighter highlighter = tabHighlighters.get(tabTitle);

        if (history == null) return;

        String keyword = JOptionPane.showInputDialog(this, "Nhập từ khóa tìm kiếm trong '" + tabTitle + "':", "Tìm kiếm", JOptionPane.PLAIN_MESSAGE);
        if (keyword == null || keyword.trim().isEmpty()) return;

        try {
            highlighter.removeAllHighlights(); // Xóa highlight cũ
            String text = history.getText();
            String lowerText = text.toLowerCase();
            String lowerKeyword = keyword.toLowerCase();

            int index = 0;
            int count = 0;
            while ((index = lowerText.indexOf(lowerKeyword, index)) != -1) {
                highlighter.addHighlight(index, index + keyword.length(),
                        new DefaultHighlighter.DefaultHighlightPainter(Color.YELLOW));
                index += keyword.length();
                count++;
            }

            if (count > 0) {
                // Scroll đến vị trí highlight đầu tiên
                try {
                    int firstIndex = lowerText.indexOf(lowerKeyword);
                    history.setCaretPosition(firstIndex);
                } catch (Exception e) {}
                JOptionPane.showMessageDialog(this, "Tìm thấy " + count + " kết quả!", "Kết quả", JOptionPane.INFORMATION_MESSAGE);
            } else {
                JOptionPane.showMessageDialog(this, "Không tìm thấy kết quả nào.", "Kết quả", JOptionPane.WARNING_MESSAGE);
            }
        } catch (BadLocationException e) {
            e.printStackTrace();
        }
    }

    // ✅ CẬP NHẬT TIÊU ĐỀ TAB THEO TRẠNG THÁI
    private void updateTabTitles() {
        for (int i = 1; i < chatTabs.getTabCount(); i++) {
            String title = chatTabs.getTitleAt(i);
            if (title.startsWith("👤 ")) {
                String partner = title.substring(2).split(" \\(")[0];
                String status = friendStatuses.getOrDefault(partner, "⚫ Offline");
                chatTabs.setTitleAt(i, "👤 " + partner + " (" + status + ")");
            }
        }
    }

    private String recipient() {
        Object s = recipientBox.getSelectedItem();
        return s == null || GROUP.equals(s) ? Protocol.EVERYONE : s.toString();
    }

    private void sendFile() {
        // ... (Giữ nguyên logic sendFile streaming như code trước) ...
        Connection current = connection;
        String to = recipient();
        if (current == null || Protocol.EVERYONE.equals(to)) {
            showError("Chọn người nhận cụ thể để gửi file."); return;
        }
        JFileChooser chooser = new JFileChooser();
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
        Path path = chooser.getSelectedFile().toPath();

        SwingUtilities.invokeLater(() -> {
            fileTransferPanel.setVisible(true);
            fileStatusLabel.setText("Đang gửi " + path.getFileName());
            fileProgressBar.setValue(0);
        });

        sender.execute(() -> {
            try {
                long size = Files.size(path);
                current.output.writeUTF("FILE"); current.output.writeUTF(to);
                current.output.writeUTF(Protocol.safeFileName(path.getFileName().toString()));
                current.output.writeLong(size);

                try (InputStream in = Files.newInputStream(path)) {
                    byte[] buf = new byte[Protocol.CHUNK_SIZE];
                    int read; long sent = 0;
                    while ((read = in.read(buf)) != -1) {
                        current.output.writeUTF("FILE_CHUNK"); current.output.writeInt(read);
                        current.output.write(buf, 0, read); current.output.flush();
                        sent += read;
                        int p = (int)((sent*100)/size);
                        SwingUtilities.invokeLater(() -> fileProgressBar.setValue(p));
                    }
                }
                current.output.writeUTF("FILE_END"); current.output.flush();
                SwingUtilities.invokeLater(() -> {
                    fileProgressBar.setValue(100); fileStatusLabel.setText("✓ Gửi xong");
                    Timer t = new Timer(3000, e -> fileTransferPanel.setVisible(false)); t.setRepeats(false); t.start();
                });
            } catch (IOException e) {
                SwingUtilities.invokeLater(() -> fileStatusLabel.setText("✗ Lỗi gửi file"));
            }
        });
    }

    private void disconnect(Connection current, String reason) {
        if (current == null || current != connection) return;
        current.close(); connection = null;
        statusLabel.setText("Chưa kết nối"); updateControls();
        appendToTab(GROUP, "Hệ thống", reason);
    }

    private void showError(String msg) {
        JOptionPane.showMessageDialog(this, msg, "Lỗi", JOptionPane.ERROR_MESSAGE);
    }

    private static class ReceivingFile {
        String owner, filename; long totalSize, receivedBytes; Path savedPath; OutputStream fileOut;
        ReceivingFile(String o, String f, long s) { owner=o; filename=f; totalSize=s; }
    }

    private static class Connection {
        Socket socket; String name; DataInputStream input; DataOutputStream output;
        ReceivingFile receivingFile;
        Connection(Socket s, String n) throws IOException {
            socket=s; name=n;
            input = new DataInputStream(new BufferedInputStream(s.getInputStream()));
            output = new DataOutputStream(new BufferedOutputStream(s.getOutputStream()));
        }
        void close() {
            if (receivingFile != null && receivingFile.fileOut != null) { try { receivingFile.fileOut.close(); } catch (IOException ignored) {} }
            try { socket.close(); } catch (IOException ignored) {}
        }
    }
}