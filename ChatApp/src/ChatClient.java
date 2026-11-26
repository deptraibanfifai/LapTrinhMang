import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.sound.sampled.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ChatClient extends JFrame {

    // ========== Màu sắc chủ đạo ==========
    private static final Color PRIMARY        = new Color(79, 70, 229);
    private static final Color PRIMARY_DARK   = new Color(67, 56, 202);
    private static final Color PRIMARY_LIGHT  = new Color(224, 231, 255);
    private static final Color ACCENT_GREEN   = new Color(16, 185, 129);
    private static final Color ACCENT_RED     = new Color(239, 68, 68);
    private static final Color ACCENT_PURPLE  = new Color(139, 92, 246);
    private static final Color ACCENT_ORANGE  = new Color(249, 115, 22);
    private static final Color SURFACE        = new Color(248, 250, 252);
    private static final Color SURFACE_DARK   = new Color(241, 245, 249);
    private static final Color TEXT_PRIMARY   = new Color(15, 23, 42);
    private static final Color TEXT_SECONDARY = new Color(71, 85, 105);
    private static final Color TEXT_WHITE     = Color.WHITE;
    private static final Color BORDER_COLOR   = new Color(226, 232, 240);

    private static final String GROUP = "Tất cả mọi người";
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");

    // ========== Emoji ==========
    private static final String[] EMOJI_LIST = {
            "☺",   "☻",   "♥",   "♡",   "★",   "☆",   "☀",   "☁",
            "☂",   "❄",   "☕",   "♪",   "♫",   "✈",   "✉",   "☎",
            "✓",   "✗",   "✔",   "✘",   "♣",   "♠",   "♦",   "•",
            "►",   "◄",   "▲",   "▼",   "↔",   "↕",   "◉",   "◎",
            "[Haha]","[Love]","[Sad]","[Angry]","[Wow]","[Kiss]","[Hi]","[Bye]",
            "[OK]","[Sorry]","[Thanks]","[Good]","[Bad]","[Congrat]","[HahaHi]","[LoveU]"
    };

    // ========== Components ==========
    private final JTextField hostField       = new JTextField("localhost", 12);
    private final JTextField portField       = new JTextField("5000", 5);
    private final JTextField nameField       = new JTextField(10);
    private final JButton    connectButton   = new JButton("Kết nối");
    private final JButton    disconnectButton = new JButton("Ngắt kết nối");
    private final JTextArea  history         = new JTextArea();
    private final JComboBox<String> recipientBox = new JComboBox<>();
    private final JTextField messageField    = new JTextField();
    private final JButton    sendButton      = new JButton("Gửi tin");
    private final JButton    fileButton      = new JButton("Gửi file…");
    private final JButton    emojiButton     = new JButton("Biểu cảm");
    private final JButton    groupButton     = new JButton("Tạo nhóm");
    private final JButton    callButton      = new JButton("Gọi");
    private final JLabel     statusLabel     = new JLabel("Chưa kết nối");

    private final Map<String, String> groupIdToName = new HashMap<>();
    private final Map<String, String> displayToId = new HashMap<>(); // display -> real id

    private final ExecutorService sender = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "socket-sender");
        t.setDaemon(true);
        return t;
    });

    private volatile Connection connection;
    private boolean connecting;
    private boolean closing;

    private final javax.swing.Timer onlineTimer;

    // ====== Call state ======
    private volatile CallDialog activeCallDialog;
    private volatile Socket callAudioSocket;
    private volatile Socket callVideoSocket;
    private volatile Thread micThread;
    private volatile Thread speakerThread;
    private volatile Thread videoSendThread;
    private volatile Thread videoRecvThread;

    // ========== Entry Point ==========
    public static void main(String[] args) {
        setModernLookAndFeel();
        SwingUtilities.invokeLater(() -> new ChatClient().setVisible(true));
    }

    private static void setModernLookAndFeel() {
        try {
            for (UIManager.LookAndFeelInfo info : UIManager.getInstalledLookAndFeels()) {
                if ("Nimbus".equals(info.getName())) {
                    UIManager.setLookAndFeel(info.getClassName());
                    break;
                }
            }
        } catch (Exception ignored) { }
        UIManager.put("control", new Color(241, 245, 249));
        UIManager.put("info", new Color(224, 231, 255));
        UIManager.put("nimbusBase", new Color(79, 70, 229));
        UIManager.put("nimbusFocus", new Color(139, 92, 246));
        UIManager.put("nimbusSelection", new Color(165, 180, 252));
        UIManager.put("nimbusLightBackground", Color.WHITE);
    }

    public ChatClient() {
        super("ChatApp - Ung dung chat mang");
        setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        setSize(980, 680);
        setMinimumSize(new Dimension(860, 560));
        setLocationByPlatform(true);
        getContentPane().setBackground(SURFACE);

        onlineTimer = new javax.swing.Timer(1000, e -> refreshOnlineStatus());
        onlineTimer.start();

        JPanel root = new JPanel(new BorderLayout(12, 12));
        root.setBorder(new EmptyBorder(16, 16, 16, 16));
        root.setBackground(SURFACE);
        setContentPane(root);

        root.add(buildHeaderPanel(),   BorderLayout.NORTH);
        root.add(buildCenterPanel(),   BorderLayout.CENTER);
        root.add(buildBottomPanel(),   BorderLayout.SOUTH);

        connectButton.addActionListener(e -> connect());
        disconnectButton.addActionListener(e -> disconnect(connection, "Đã ngắt kết nối."));
        sendButton.addActionListener(e -> sendMessage());
        messageField.addActionListener(e -> sendMessage());
        fileButton.addActionListener(e -> sendFile());
        emojiButton.addActionListener(e -> openEmojiPicker());
        groupButton.addActionListener(e -> openCreateGroupDialog());
        callButton.addActionListener(e -> initiateCall());
        recipientBox.addActionListener(e -> onRecipientChanged());

        addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent event) {
                closing = true;
                onlineTimer.stop();
                cleanupCall();
                if (connection != null) connection.close();
                sender.shutdownNow();
                dispose();
            }
        });

        styleAll();
        updateControls();
        append("Hệ thống", "Nhập tên rồi bấm Kết nối. Mở thêm cửa sổ client để chat cùng nhau.");
    }

    // ========== Build layout ==========

    private JPanel buildHeaderPanel() {
        JPanel header = new JPanel(new BorderLayout()) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                GradientPaint gp = new GradientPaint(0, 0,
                        new Color(99, 102, 241),
                        getWidth(), 0,
                        new Color(168, 85, 247));
                g2.setPaint(gp);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 16, 16);
            }
        };
        header.setPreferredSize(new Dimension(0, 84));
        header.setOpaque(false);

//        JLabel title = new JLabel("<html><div style='color:white; font-size:20px; font-weight:bold;'>ChatApp</div>"
//                + "<div style='color:rgba(255,255,255,0.9); font-size:12px;'>Chat TCP • Nhóm • Gọi Audio/Video</div></html>");
//        title.setBorder(new EmptyBorder(14, 20, 14, 20));
//        header.add(title, BorderLayout.WEST);

        JPanel login = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        login.setOpaque(false);
        login.setBorder(new EmptyBorder(12, 12, 12, 12));

        login.add(labelWhite("Server:"));
        styleField(hostField, 11);
        login.add(hostField);
        login.add(labelWhite("Cổng:"));
        styleField(portField, 11);
        login.add(portField);
        login.add(labelWhite("Tên:"));
        styleField(nameField, 11);
        login.add(nameField);
        login.add(connectButton);
        login.add(disconnectButton);

        header.add(login, BorderLayout.EAST);
        return header;
    }

    private JPanel buildCenterPanel() {
        history.setEditable(false);
        history.setLineWrap(true);
        history.setWrapStyleWord(true);
        history.setBackground(Color.WHITE);
        history.setForeground(TEXT_PRIMARY);
        history.setCaretColor(PRIMARY);
        history.setMargin(new Insets(14, 16, 14, 16));
        history.setFont(fontSegoe(Font.PLAIN, 15));
        history.setBorder(new LineBorder(BORDER_COLOR, 1, true));

        JScrollPane scroll = new JScrollPane(history);
        scroll.setBorder(BorderFactory.createTitledBorder(
                new LineBorder(BORDER_COLOR, 1, true),
                " Khung chat ",
                javax.swing.border.TitledBorder.LEADING,
                javax.swing.border.TitledBorder.TOP,
                fontSegoe(Font.BOLD, 13),
                PRIMARY_DARK));
        scroll.getViewport().setBackground(Color.WHITE);
        scroll.setBackground(SURFACE);

        JPanel wrap = new JPanel(new BorderLayout());
        wrap.setBackground(SURFACE);
        wrap.add(scroll, BorderLayout.CENTER);
        return wrap;
    }

    private JPanel buildBottomPanel() {
        JPanel bottom = new JPanel(new BorderLayout(8, 10));
        bottom.setBackground(SURFACE);
        bottom.setBorder(BorderFactory.createTitledBorder(
                new LineBorder(BORDER_COLOR, 1, true),
                " Soan tin nhan & Gui file & Nhom & Goi ",
                javax.swing.border.TitledBorder.LEADING,
                javax.swing.border.TitledBorder.TOP,
                fontSegoe(Font.BOLD, 13),
                PRIMARY_DARK));

        JPanel recipientRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        recipientRow.setBackground(SURFACE_DARK);
        recipientRow.setBorder(new EmptyBorder(8, 10, 8, 10));

        recipientRow.add(labelBold("Gửi đến:"));
        recipientBox.setPreferredSize(new Dimension(240, 34));
        recipientBox.setFont(fontSegoe(Font.PLAIN, 13));
        recipientBox.setBackground(Color.WHITE);
        recipientRow.add(recipientBox);
        recipientRow.add(emojiButton);
        recipientRow.add(fileButton);
        recipientRow.add(groupButton);
        recipientRow.add(callButton);

        fileButton.setToolTipText("<html>Gửi file RIÊNG hoặc theo NHÓM (chọn người/nhóm trước). Tối đa 20 MB.</html>");
        emojiButton.setToolTipText("Chọn biểu cảm để chèn vào tin nhắn.");
        groupButton.setToolTipText("Tạo nhóm chat gồm thêm thành viên.");
        callButton.setToolTipText("<html>Gọi audio/video tới người đang chọn (không có nhóm).");

        bottom.add(recipientRow, BorderLayout.NORTH);

        JPanel compose = new JPanel(new BorderLayout(8, 0));
        compose.setBackground(SURFACE);
        messageField.setBorder(new LineBorder(BORDER_COLOR, 1, true));
        messageField.setFont(fontSegoe(Font.PLAIN, 14));
        messageField.setMargin(new Insets(6, 8, 6, 8));
        compose.add(messageField, BorderLayout.CENTER);
        compose.add(sendButton, BorderLayout.EAST);
        bottom.add(compose, BorderLayout.CENTER);

        statusLabel.setFont(fontSegoe(Font.BOLD, 13));
        statusLabel.setForeground(PRIMARY_DARK);
        statusLabel.setIcon(new ColorIcon(ACCENT_RED, 10));
        statusLabel.setBorder(new EmptyBorder(6, 4, 0, 4));
        bottom.add(statusLabel, BorderLayout.SOUTH);

        return bottom;
    }

    // ========== Style ==========

    private void styleAll() {
        styleButton(connectButton,   ACCENT_GREEN,  new Color(5, 150, 105));
        styleButton(disconnectButton,ACCENT_RED,    new Color(220, 38, 38));
        styleButton(sendButton,      PRIMARY,       PRIMARY_DARK);
        styleButton(fileButton,      ACCENT_PURPLE, new Color(124, 58, 237));
        styleButton(emojiButton,     ACCENT_ORANGE, new Color(234, 88, 12));
        styleButton(groupButton,     new Color(14, 165, 233), new Color(2, 132, 199)); // xanh nước biển
        styleButton(callButton,      ACCENT_GREEN,  new Color(5, 150, 105));
        disconnectButton.setEnabled(false);
    }

    private void openEmojiPicker() {
        JWindow picker = new JWindow(this);
        picker.setAlwaysOnTop(true);
        picker.setType(java.awt.Window.Type.POPUP);

        JPanel panel = new JPanel(new GridLayout(5, 8, 4, 4));
        panel.setBorder(new EmptyBorder(8, 8, 8, 8));
        panel.setBackground(new Color(255, 251, 235));
        panel.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(new Color(251, 146, 60), 2, true),
                new EmptyBorder(8, 8, 8, 8)));

        Font big = fontSegoe(Font.PLAIN, 24);
        for (String emoji : EMOJI_LIST) {
            JButton btn = new JButton(emoji);
            btn.setFont(big);
            btn.setContentAreaFilled(false);
            btn.setBorder(new EmptyBorder(4, 4, 4, 4));
            btn.setFocusPainted(false);
            btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            btn.setToolTipText("Chèn " + emoji);
            btn.addActionListener(ev -> {
                messageField.replaceSelection(emoji);
                messageField.requestFocusInWindow();
                picker.dispose();
            });
            panel.add(btn);
        }

        picker.setContentPane(panel);
        picker.pack();
        Point loc = emojiButton.getLocationOnScreen();
        picker.setLocation(loc.x, loc.y + emojiButton.getHeight() + 3);
        picker.setVisible(true);

        Toolkit.getDefaultToolkit().addAWTEventListener(ev -> {
            if (ev.getID() == java.awt.event.MouseEvent.MOUSE_PRESSED && picker.isVisible()) {
                java.awt.event.MouseEvent me = (java.awt.event.MouseEvent) ev;
                if (!SwingUtilities.isDescendingFrom(me.getComponent(), picker)) picker.dispose();
            }
        }, java.awt.AWTEvent.MOUSE_EVENT_MASK);
    }

    private static JLabel labelWhite(String text) {
        JLabel l = new JLabel(text);
        l.setForeground(TEXT_WHITE);
        l.setFont(fontSegoe(Font.BOLD, 12));
        return l;
    }

    private static JLabel labelBold(String text) {
        JLabel l = new JLabel(text);
        l.setFont(fontSegoe(Font.BOLD, 13));
        l.setForeground(TEXT_PRIMARY);
        return l;
    }

    private static Font fontSegoe(int style, int size) {
        try { return new Font("Segoe UI", style, size); }
        catch (Exception e) { return new Font(Font.SANS_SERIF, style, size); }
    }

    private static void styleField(JTextField f, int size) {
        f.setFont(fontSegoe(Font.PLAIN, size));
        f.setBorder(new LineBorder(BORDER_COLOR, 1, true));
        f.setBackground(Color.WHITE);
        f.setForeground(TEXT_PRIMARY);
        f.setMargin(new Insets(4, 6, 4, 6));
    }

    private static void styleButton(JButton b, Color bg, Color bgHover) {
        b.setForeground(TEXT_WHITE);
        b.setFont(fontSegoe(Font.BOLD, 12));
        b.setFocusPainted(false);
        b.setBorderPainted(false);
        b.setContentAreaFilled(false);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.setBorder(new EmptyBorder(8, 14, 8, 14));

        Border normal = new RoundBorder(14, bg);
        b.setBorder(normal);

        b.setUI(new javax.swing.plaf.basic.BasicButtonUI() {
            @Override public void paint(Graphics g, JComponent c) {
                AbstractButton btn = (AbstractButton) c;
                ButtonModel m = btn.getModel();
                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                Color base = !m.isEnabled() ? new Color(203, 213, 225) :
                             (m.isPressed() || m.isRollover()) ? bgHover : bg;
                g2.setColor(base);
                g2.fillRoundRect(0, 0, c.getWidth(), c.getHeight(), 14, 14);
                super.paint(g, c);
            }
        });

        b.getModel().addChangeListener(e -> {
            ButtonModel m = b.getModel();
            b.setForeground(!m.isEnabled() ? new Color(148, 163, 184) : TEXT_WHITE);
        });
    }

    private static class RoundBorder implements Border {
        private final int radius;
        private final Insets insets = new Insets(6, 14, 6, 14);
        @SuppressWarnings("unused")
        private final Color color;
        RoundBorder(int radius, Color color) { this.radius = radius; this.color = color; }
        @Override public void paintBorder(Component c, Graphics g, int x, int y, int w, int h) { }
        @Override public Insets getBorderInsets(Component c) { return insets; }
        @Override public boolean isBorderOpaque() { return false; }
    }

    private static class ColorIcon implements Icon {
        private final Color color;
        private final int size;
        ColorIcon(Color c, int size) { this.color = c; this.size = size; }
        @Override public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2 = (Graphics2D) g;
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(color);
            g2.fillOval(x, y + (c.getHeight() - size) / 2 - 1, size, size);
        }
        @Override public int getIconWidth()  { return size + 6; }
        @Override public int getIconHeight() { return size; }
    }

    // ========== Logic điều khiển ==========

    private void onRecipientChanged() {
        Object sel = recipientBox.getSelectedItem();
        if (sel == null) return;
        String real = displayToId.getOrDefault(sel.toString(), sel.toString());
        callButton.setEnabled(connection != null
                && !GROUP.equals(sel.toString())
                && !Protocol.isGroupId(real));
    }

    private void updateControls() {
        boolean online = connection != null;
        connectButton.setEnabled(!online && !connecting);
        disconnectButton.setEnabled(online);
        hostField.setEnabled(!online && !connecting);
        portField.setEnabled(!online && !connecting);
        nameField.setEnabled(!online && !connecting);
        sendButton.setEnabled(online);
        fileButton.setEnabled(online);
        emojiButton.setEnabled(online);
        groupButton.setEnabled(online);
        messageField.setEnabled(online);
        recipientBox.setEnabled(online);
        onRecipientChanged();
        if (!online) {
            recipientBox.removeAllItems();
            displayToId.clear();
            recipientBox.addItem(GROUP);
            displayToId.put(GROUP, Protocol.EVERYONE);
        }
    }

    private void refreshOnlineStatus() {
        Connection c = this.connection;
        if (c == null) {
            statusLabel.setIcon(new ColorIcon(ACCENT_RED, 10));
            statusLabel.setText(connecting ? "Đang kết nối…" : "Chưa kết nối");
        } else {
            long sec = Duration.between(c.connectedAt, LocalTime.now()).getSeconds();
            if (sec < 0) sec = 0;
            long h = sec / 3600, m = (sec % 3600) / 60, s = sec % 60;
            String hhmmss = String.format("%02d:%02d:%02d", h, m, s);
            statusLabel.setIcon(new ColorIcon(ACCENT_GREEN, 10));
            statusLabel.setText(c.name + " • " + c.hostInfo
                    + "   |   Online: " + c.onlineCount
                    + "   |   Thời gian: " + hhmmss);
        }
    }

    private void connect() {
        String host = hostField.getText().trim();
        String name = nameField.getText().trim();
        int port;
        try {
            port = Integer.parseInt(portField.getText().trim());
            if (port < 1 || port > 65535) throw new NumberFormatException();
        } catch (NumberFormatException e) {
            showError("Cổng phải là số từ 1 đến 65535.");
            return;
        }
        if (host.isEmpty() || !name.matches("[\\p{L}\\p{N}_-]{1,20}")) {
            showError("Nhập server và tên gồm 1–20 chữ cái, số, _ hoặc -.");
            return;
        }
        connecting = true;
        statusLabel.setText("Đang kết nối đến " + host + ":" + port + "…");
        updateControls();
        final String hostInfo = host + ":" + port;
        sender.execute(() -> {

            Socket socket = new Socket();
            try {
                socket.connect(new InetSocketAddress(host, port), 5000);
                socket.setSoTimeout(15000);
                Connection next = new Connection(socket, name, hostInfo, host, port);

                next.output.writeUTF("LOGIN");
                next.output.writeUTF(name);
                next.output.flush();
                String reply = next.input.readUTF();
                if ("ERROR".equals(reply)) throw new IOException(next.input.readUTF());
                if (!"WELCOME".equals(reply)) throw new IOException("Server trả lời không hợp lệ.");
                socket.setSoTimeout(0);
                SwingUtilities.invokeLater(() -> {
                    connecting = false;
                    if (closing) { next.close(); return; }
                    connection = next;
                    setTitle("ChatApp - " + name);
                    updateControls();
                    append("Hệ thống", "Đã kết nối với tên " + name + " (server " + hostInfo + ").");
                    Thread receiver = new Thread(() -> receive(next), "socket-receiver");
                    receiver.setDaemon(true);
                    receiver.start();
                    messageField.requestFocusInWindow();
                });
            } catch (IOException e) {
                try { socket.close(); } catch (IOException ignored) { }
                SwingUtilities.invokeLater(() -> {
                    connecting = false;
                    statusLabel.setText("Chưa kết nối");
                    statusLabel.setIcon(new ColorIcon(ACCENT_RED, 10));
                    updateControls();
                    if (!closing) showError("Không kết nối được: " + e.getMessage());
                });
            }
        });
    }

    private void receive(Connection current) {
        try {
            while (true) {
                String type = current.input.readUTF();
                switch (type) {
                    case "USERS": {
                        int count = current.input.readInt();
                        if (count < 0 || count > 10000) throw new IOException("Danh sách không hợp lệ.");
                        List<String> names = new ArrayList<>();
                        for (int i = 0; i < count; i++) names.add(current.input.readUTF());
                        final List<String> fNames = names;
                        SwingUtilities.invokeLater(() -> {
                            if (connection != current) return;
                            Object selected = recipientBox.getSelectedItem();
                            recipientBox.removeAllItems();
                            displayToId.clear();
                            recipientBox.addItem(GROUP);
                            displayToId.put(GROUP, Protocol.EVERYONE);
                            for (String gId : groupIdToName.keySet()) {
                                String display = Protocol.groupDisplayName(groupIdToName.get(gId));
                                recipientBox.addItem(display);
                                displayToId.put(display, gId);
                            }
                            for (String nm : fNames) {
                                if (!nm.equals(current.name)) {
                                    recipientBox.addItem(nm);
                                    displayToId.put(nm, nm);
                                }
                            }
                            String selStr = selected == null ? null : selected.toString();
                            if (displayToId.containsKey(selStr)) recipientBox.setSelectedItem(selStr);
                            current.onlineCount = fNames.size();
                            onRecipientChanged();
                        });
                        break;
                    }
                    case "GROUPS_LIST": {
                        int gCount = current.input.readInt();
                        Map<String, String> newMap = new HashMap<>();
                        for (int i = 0; i < gCount; i++) {
                            String gid = current.input.readUTF();
                            String gname = current.input.readUTF();
                            String gcount = current.input.readUTF();
                            newMap.put(gid, gname);
                        }
                        SwingUtilities.invokeLater(() -> {
                            groupIdToName.clear();
                            groupIdToName.putAll(newMap);
                            if (connection == current) rebuildRecipientBox();
                        });
                        break;
                    }
                    case "GROUP_CREATED": {
                        String gid = current.input.readUTF();
                        String gname = current.input.readUTF();
                        String creator = current.input.readUTF();
                        int mc = current.input.readInt();
                        List<String> members = new ArrayList<>();
                        for (int i = 0; i < mc; i++) members.add(current.input.readUTF());
                        SwingUtilities.invokeLater(() -> {
                            groupIdToName.put(gid, gname);
                            if (connection == current) rebuildRecipientBox();
                            append("Hệ thống", "Đã tạo nhóm \"" + gname + "\" (bởi " + creator
                                    + ") — " + mc + " thành viên: " + String.join(", ", members));
                        });
                        break;
                    }
                    case "CHAT": {
                        String from = current.input.readUTF();
                        String to = current.input.readUTF();
                        String text = current.input.readUTF();
                        log(current, Protocol.EVERYONE.equals(to) ?
                                        "[Nhóm] " + from + " -> Chung" :
                                        "[Riêng] " + from + " -> " + to,
                                text);
                        break;
                    }
                    case "GROUP_CHAT": {
                        String gid = current.input.readUTF();
                        String gname = current.input.readUTF();
                        String from = current.input.readUTF();
                        String text = current.input.readUTF();
                        String gDisp = Protocol.groupDisplayName(gname);
                        log(current, "[Nhóm - " + gname + "] " + from, text);
                        break;
                    }
                    case "FILE": {
                        String owner = current.input.readUTF();
                        String filename = Protocol.safeFileName(current.input.readUTF());
                        byte[] bytes = Protocol.readFile(current.input);
                        saveAndLogFile(current, owner, filename, bytes, "(riêng)");
                        break;
                    }
                    case "GROUP_FILE": {
                        String gid = current.input.readUTF();
                        String gname = current.input.readUTF();
                        String owner = current.input.readUTF();
                        String filename = Protocol.safeFileName(current.input.readUTF());
                        byte[] bytes = Protocol.readFile(current.input);
                        saveAndLogFile(current, owner, filename, bytes, "(nhóm " + gname + ")");
                        break;
                    }
                    case "CALL_INVITE": {
                        String callId = current.input.readUTF();
                        String from = current.input.readUTF();
                        String to = current.input.readUTF();
                        boolean hasVideo = current.input.readBoolean();
                        int portOffer = current.input.readInt();
                        SwingUtilities.invokeLater(() -> onIncomingCall(current, callId, from, to, hasVideo, portOffer));
                        break;
                    }
                    case "CALL_ACCEPT": {
                        String callId = current.input.readUTF();
                        String from = current.input.readUTF();
                        String to = current.input.readUTF();
                        int acceptPort = current.input.readInt();
                        SwingUtilities.invokeLater(() -> onCallAccepted(callId, from, to, acceptPort));
                        break;
                    }
                    case "CALL_REJECT": {
                        String callId = current.input.readUTF();
                        String from = current.input.readUTF();
                        String to = current.input.readUTF();
                        String reason = current.input.readUTF();
                        SwingUtilities.invokeLater(() -> onCallRejected(callId, from, reason));
                        break;
                    }
                    case "CALL_END": {
                        String callId = current.input.readUTF();
                        String from = current.input.readUTF();
                        String to = current.input.readUTF();
                        SwingUtilities.invokeLater(() -> onCallEnded(callId, from));
                        break;
                    }
                    case "CALL_CONTROL": {
                        String callId = current.input.readUTF();
                        String from = current.input.readUTF();
                        String to = current.input.readUTF();
                        String control = current.input.readUTF();
                        SwingUtilities.invokeLater(() -> onCallControl(callId, from, control));
                        break;
                    }
                    case "INFO":
                    case "ERROR": {
                        String tag = "ERROR".equals(type) ? "[Lỗi]" : "[Hệ thống]";
                        log(current, tag, current.input.readUTF());
                        break;
                    }
                    default:
                        throw new IOException("Gói tin không hợp lệ: " + type);
                }
            }
        } catch (IOException e) {
            current.close();
            SwingUtilities.invokeLater(() -> disconnect(current, "[Mất kết nối] Mất kết nối với server."));
        }
    }

    private void rebuildRecipientBox() {
        Object selected = recipientBox.getSelectedItem();
        List<String> usersOnly = new ArrayList<>();
        for (int i = 0; i < recipientBox.getItemCount(); i++) {
            String it = recipientBox.getItemAt(i);
            if (GROUP.equals(it)) continue;
            String real = displayToId.getOrDefault(it, it);
            if (!Protocol.isGroupId(real) && !Protocol.EVERYONE.equals(real)) usersOnly.add(it);
        }
        recipientBox.removeAllItems();
        displayToId.clear();
        recipientBox.addItem(GROUP);
        displayToId.put(GROUP, Protocol.EVERYONE);
        for (String gid : groupIdToName.keySet()) {
            String display = Protocol.groupDisplayName(groupIdToName.get(gid));
            recipientBox.addItem(display);
            displayToId.put(display, gid);
        }
        for (String u : usersOnly) {
            recipientBox.addItem(u);
            displayToId.put(u, u);
        }
        String selStr = selected == null ? null : selected.toString();
        if (displayToId.containsKey(selStr)) recipientBox.setSelectedItem(selStr);
        onRecipientChanged();
    }

    private void saveAndLogFile(Connection current, String owner, String filename, byte[] bytes, String tag) {
        try {
            Path directory = Paths.get("received-files", current.name).toAbsolutePath();
            Files.createDirectories(directory);
            Path saved = Files.createTempFile(directory, "received-", "-" + filename);
            Files.write(saved, bytes);
            log(current, "[File] " + owner + " " + tag,
                    bytes.length + " byte đã lưu: " + saved);
        } catch (IOException e) {
            log(current, "[Lỗi file]", e.getMessage());
        }
    }

    private String recipient() {
        Object selected = recipientBox.getSelectedItem();
        if (selected == null) return Protocol.EVERYONE;
        return displayToId.getOrDefault(selected.toString(), Protocol.EVERYONE);
    }

    private boolean recipientIsGroup() {
        return Protocol.isGroupId(recipient());
    }

    private void sendMessage() {
        Connection current = connection;
        String text = messageField.getText().trim();
        String to = recipient();
        if (current == null || text.isEmpty()) return;
        if (text.length() > Protocol.MAX_MESSAGE_LENGTH) {
            showError("Tin nhắn tối đa 4000 ký tự.");
            return;
        }
        messageField.setText("");
        boolean toGroup = Protocol.isGroupId(to);
        sender.execute(() -> {
            try {
                if (toGroup) {
                    current.output.writeUTF("GROUP_CHAT");
                    current.output.writeUTF(to);
                    current.output.writeUTF(text);
                } else {
                    current.output.writeUTF("CHAT");
                    current.output.writeUTF(to);
                    current.output.writeUTF(text);
                }
                current.output.flush();
            } catch (IOException e) {
                SwingUtilities.invokeLater(() -> disconnect(current, "Không gửi được tin: kết nối đã đóng."));
            }
        });
    }

    private void sendFile() {
        Connection current = connection;
        String to = recipient();
        if (current == null) return;
        JFileChooser chooser = new JFileChooser();
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
        Path path = chooser.getSelectedFile().toPath();
        boolean toGroup = Protocol.isGroupId(to);
        sender.execute(() -> {
            byte[] bytes;
            try {
                if (Files.size(path) > Protocol.MAX_FILE_SIZE) throw new IOException("File vượt quá 20 MB.");
                try (java.io.InputStream file = Files.newInputStream(path)) {
                    bytes = file.readNBytes(Protocol.MAX_FILE_SIZE + 1);
                }
                if (bytes.length > Protocol.MAX_FILE_SIZE) throw new IOException("File vượt quá 20 MB.");
            } catch (IOException e) {
                log(current, "[Lỗi đọc file]", e.getMessage());
                return;
            }
            log(current, "[Hệ thống]", "[Đang gửi] \"" + path.getFileName() + "\" đến "
                    + (toGroup ? "nhóm" : to) + "...");
            try {
                if (toGroup) {
                    current.output.writeUTF("GROUP_FILE");
                    current.output.writeUTF(to);
                } else {
// gửi file riêng
                    current.output.writeUTF("FILE");
                    current.output.writeUTF(to);
                }
                current.output.writeUTF(Protocol.safeFileName(path.getFileName().toString()));
                current.output.writeInt(bytes.length);
                current.output.write(bytes);
                current.output.flush();
            } catch (IOException e) {
                SwingUtilities.invokeLater(() -> disconnect(current, "Gửi file bị gián đoạn: kết nối đã đóng."));
            }
        });
    }

    private void disconnect(Connection current, String reason) {
        if (current == null || current != connection) return;
        cleanupCall();
        current.close();
        connection = null;
        statusLabel.setIcon(new ColorIcon(ACCENT_RED, 10));
        statusLabel.setText("Chưa kết nối");
        groupIdToName.clear();
        updateControls();
        append("[Hệ thống]", reason);
    }

    private void log(Connection current, String label, String text) {
        SwingUtilities.invokeLater(() -> {
            if (connection == current) append(label, text);
        });
    }

    private void append(String label, String text) {
        String time = LocalTime.now().format(TIME_FMT);
        history.append("[" + time + "] " + label + ":  " + text + "\n");
        history.setCaretPosition(history.getDocument().getLength());
    }

    private void showError(String message) {
        JOptionPane.showMessageDialog(this, message, "Thông báo", JOptionPane.WARNING_MESSAGE);
    }

    // ========== Tạo nhóm ==========
    private void openCreateGroupDialog() {
        Connection current = connection;
        if (current == null) return;
        List<String> candidates = new ArrayList<>();
        for (String key : displayToId.keySet()) {
            String real = displayToId.get(key);
            if (Protocol.EVERYONE.equals(real)) continue;
            if (Protocol.isGroupId(real)) continue;
            if (real.equals(current.name)) continue;
            candidates.add(real);
        }
        Collections.sort(candidates);
        if (candidates.isEmpty()) {
            showError("Chưa có người dùng nào online khác để tạo nhóm.");
            return;
        }

        JDialog dialog = new JDialog(this, "Tạo nhóm chat", true);
        dialog.setSize(460, 520);
        dialog.setLocationRelativeTo(this);
        dialog.setLayout(new BorderLayout(10, 10));
        ((JPanel) dialog.getContentPane()).setBorder(new EmptyBorder(14, 14, 14, 14));

        JPanel top = new JPanel(new BorderLayout(8, 6));
        top.setBackground(SURFACE);
        JLabel lName = labelBold("Tên nhóm (1–" + Protocol.MAX_GROUP_NAME_LEN + " ký tự):");
        JTextField tfName = new JTextField();
        tfName.setFont(fontSegoe(Font.PLAIN, 14));
        tfName.setBorder(new LineBorder(BORDER_COLOR, 1, true));
        tfName.setMargin(new Insets(6, 8, 6, 8));
        top.add(lName, BorderLayout.NORTH);
        top.add(tfName, BorderLayout.CENTER);

        JLabel lMembers = labelBold("Chọn thành viên (ít nhất 1 người):");
        top.add(lMembers, BorderLayout.SOUTH);

        JCheckBox[] boxes = new JCheckBox[candidates.size()];
        JPanel center = new JPanel();
        center.setLayout(new BoxLayout(center, BoxLayout.Y_AXIS));
        center.setBackground(Color.WHITE);
        center.setBorder(new LineBorder(BORDER_COLOR, 1, true));
        JScrollPane scroll = new JScrollPane(center);
        scroll.setBackground(SURFACE);
        for (int i = 0; i < candidates.size(); i++) {
            boxes[i] = new JCheckBox("👤 " + candidates.get(i));
            boxes[i].setFont(fontSegoe(Font.PLAIN, 14));
            boxes[i].setBackground(Color.WHITE);
            boxes[i].setBorder(new EmptyBorder(6, 10, 6, 10));
            center.add(boxes[i]);
        }

        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        bottom.setBackground(SURFACE);
        JButton cancel = new JButton("Hủy");
        JButton ok = new JButton("Tạo nhóm");
        styleButton(ok, ACCENT_GREEN, new Color(5, 150, 105));
        styleButton(cancel, ACCENT_RED, new Color(220, 38, 38));
        bottom.add(ok);
        bottom.add(cancel);

        cancel.addActionListener(ev -> dialog.dispose());
        final Connection fCur = current;
        ok.addActionListener(ev -> {
            String gn = Protocol.safeGroupName(tfName.getText());
            List<String> chosen = new ArrayList<>();
            for (JCheckBox b : boxes) if (b.isSelected()) {
                chosen.add(b.getText().replaceFirst("^👤 ", ""));
            }
            if (chosen.isEmpty() || gn.isEmpty()) {
                showError("Nhập tên nhóm và chọn ít nhất 1 thành viên.");
                return;
            }
            sender.execute(() -> {
                try {
                    fCur.output.writeUTF("CREATE_GROUP");
                    fCur.output.writeUTF(gn);
                    fCur.output.writeInt(chosen.size());
                    for (String c : chosen) fCur.output.writeUTF(c);
                    fCur.output.flush();
                } catch (IOException e) {
                    SwingUtilities.invokeLater(() -> disconnect(fCur, "Kết nối đã đóng."));
                }
            });
            dialog.dispose();
        });

        dialog.add(top, BorderLayout.NORTH);
        dialog.add(scroll, BorderLayout.CENTER);
        dialog.add(bottom, BorderLayout.SOUTH);
        dialog.setVisible(true);
    }

    // ========== Call: Signaling + UI + Media ==========
    private void initiateCall() {
        Connection current = connection;
        if (current == null) return;
        String to = recipient();
        if (Protocol.EVERYONE.equals(to) || Protocol.isGroupId(to)) {
            showError("Chỉ gọi được từng người 1 (không gọi được nhóm / tất cả).");
            return;
        }
        String peerDisplayName = "";
        for (Map.Entry<String, String> en : displayToId.entrySet()) {
            if (en.getValue().equals(to)) { peerDisplayName = en.getKey(); break; }
        }
        if (peerDisplayName.isEmpty()) peerDisplayName = to;

        Object[] opts = {"Video", " Audio", "Hủy"};
        int choice = JOptionPane.showOptionDialog(this,
                "Bắt đầu gọi với " + peerDisplayName + "?",
                "Gọi " + peerDisplayName,
                JOptionPane.YES_NO_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE, null, opts, opts[0]);
        if (choice == JOptionPane.CLOSED_OPTION || choice == 2) return;
        boolean withVideo = (choice == 0);
        final String callId = "call_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        final String fTo = to;
        final String fPeer = peerDisplayName;

        CallDialog dlg = new CallDialog(this, fPeer, callId, connection.name, to, /*outgoing*/true, withVideo);
        activeCallDialog = dlg;
        dlg.setStatus("Đang gọi...");
        dlg.setVisible(true);

        int mediaPort = current.serverPort + 1;
        sender.execute(() -> {
            try {
                current.output.writeUTF("CALL_INVITE");
                current.output.writeUTF(callId);
                current.output.writeUTF(connection.name);
                current.output.writeUTF(fTo);
                current.output.writeBoolean(withVideo);
                current.output.writeInt(mediaPort);
                current.output.flush();
            } catch (IOException e) {
                SwingUtilities.invokeLater(() -> disconnect(current, "Kết nối đã đóng khi gửi lời mời gọi."));
            }
        });
    }

    private void onIncomingCall(Connection current, String callId, String from, String to,
                                boolean hasVideo, int portOffer) {
        if (activeCallDialog != null && activeCallDialog.isVisible()) {
            sender.execute(() -> {
                try {
                    current.output.writeUTF("CALL_REJECT");
                    current.output.writeUTF(callId);
                    current.output.writeUTF(current.name);
                    current.output.writeUTF(from);
                    current.output.writeUTF("Máy bận");
                    current.output.flush();
                } catch (IOException ignored) { }
            });
            return;
        }
        Object[] opts = {" Nhận", " Từ chối"};
        int rc = JOptionPane.showOptionDialog(this,
                (hasVideo ? "📹" : "🎙") + "  " + from + " đang gọi bạn " + (hasVideo ? "(có video)" : "(chỉ audio)")
                        + "\nBạn có muốn nhận không?",
                "Cuộc gọi đến — " + from,
                JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE, null, opts, opts[0]);
        if (rc == JOptionPane.YES_OPTION) {
            final int acceptPort = current.serverPort + 1;
            CallDialog dlg = new CallDialog(this, from, callId, current.name, from, false, hasVideo);
            activeCallDialog = dlg;
            dlg.setVideoEnabled(hasVideo);
            dlg.setStatus("Kết nối...");
            dlg.setVisible(true);
            startCallMedia(callId, current.serverHost, acceptPort, /*callerSide*/false, hasVideo);
            sender.execute(() -> {
                try {
                    current.output.writeUTF("CALL_ACCEPT");
                    current.output.writeUTF(callId);
                    current.output.writeUTF(current.name);
                    current.output.writeUTF(from);
                    current.output.writeInt(acceptPort);
                    current.output.flush();
                    dlg.startedAt = LocalTime.now();
                    SwingUtilities.invokeLater(dlg::startTicking);
                } catch (IOException e) {
                    SwingUtilities.invokeLater(() -> disconnect(current, "Kết nối đã đóng khi nhận cuộc gọi."));
                }
            });
        } else {
            sender.execute(() -> {
                try {
                    current.output.writeUTF("CALL_REJECT");
                    current.output.writeUTF(callId);
                    current.output.writeUTF(current.name);
                    current.output.writeUTF(from);
                    current.output.writeUTF("Bận / không nhận");
                    current.output.flush();
                } catch (IOException ignored) { }
            });
        }
    }

    private void onCallAccepted(String callId, String from, String to, int acceptPort) {
        CallDialog dlg = activeCallDialog;
        if (dlg == null || !callId.equals(dlg.callId)) return;
        Connection cur = connection;
        if (cur == null) return;
        dlg.setStatus("Đã kết nối!");
        dlg.startedAt = LocalTime.now();
        dlg.startTicking();
        startCallMedia(callId, cur.serverHost, acceptPort, true, dlg.requestedWithVideo);
    }

    private void onCallRejected(String callId, String from, String reason) {
        CallDialog dlg = activeCallDialog;
        if (dlg == null || !callId.equals(dlg.callId)) return;
        append("[Gọi]", from + " đã từ chối cuộc gọi: " + reason);
        dlg.dispose();
        cleanupCall();
    }

    private void onCallEnded(String callId, String from) {
        CallDialog dlg = activeCallDialog;
        if (dlg == null || !callId.equals(dlg.callId)) return;
        append("[Gọi]", "Cuộc gọi [" + dlg.peerName + "] đã kết thúc bởi " + from);
        dlg.dispose();
        cleanupCall();
    }

    private void onCallControl(String callId, String from, String control) {
        CallDialog dlg = activeCallDialog;
        if (dlg == null || !callId.equals(dlg.callId)) return;
        switch (control) {
            case Protocol.CALL_CTL_MUTE_MIC:   dlg.setPeerMicMuted(true);  break;
            case Protocol.CALL_CTL_UNMUTE_MIC: dlg.setPeerMicMuted(false); break;
            case Protocol.CALL_CTL_MUTE_SPK:   break; // đích tự toggle
            case Protocol.CALL_CTL_UNMUTE_SPK: break;
            case Protocol.CALL_CTL_VIDEO_ON:   dlg.setPeerVideoOn(true); break;
            case Protocol.CALL_CTL_VIDEO_OFF:  dlg.setPeerVideoOn(false); break;
        }
    }

    private void sendCallControl(String control) {
        Connection c = connection;
        CallDialog dlg = activeCallDialog;
        if (c == null || dlg == null) return;
        String to = dlg.isOutgoing ? dlg.peerName : dlg.myName.equals(dlg.callerName) ? dlg.calleeName : dlg.callerName;
        String peer = dlg.peerName;
        sender.execute(() -> {
            try {
                c.output.writeUTF("CALL_CONTROL");
                c.output.writeUTF(dlg.callId);
                c.output.writeUTF(c.name);
                c.output.writeUTF(peer);
                c.output.writeUTF(control);
                c.output.flush();
            } catch (IOException e) {
                SwingUtilities.invokeLater(() -> disconnect(c, "Kết nối đã đóng khi gửi điều khiển."));
            }
        });
    }

    private void sendCallEnd() {
        Connection c = connection;
        CallDialog dlg = activeCallDialog;
        if (c == null || dlg == null) return;
        String peer = dlg.peerName;
        sender.execute(() -> {
            try {
                c.output.writeUTF("CALL_END");
                c.output.writeUTF(dlg.callId);
                c.output.writeUTF(c.name);
                c.output.writeUTF(peer);
                c.output.flush();
            } catch (IOException ignored) { }
        });
    }

    // ===== Media streaming =====
    private void startCallMedia(String callId, String host, int mediaPort, boolean callerSide, boolean withVideo) {
        String side = callerSide ? Protocol.CALL_SIDE_CALLER : Protocol.CALL_SIDE_CALLEE;
        Connection c = connection;
        if (c == null) return;
        ExecutorService svc = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r);
            t.setDaemon(true);
            return t;
        });
        svc.execute(() -> {
            try {
                Socket s = new Socket();
                s.connect(new InetSocketAddress(host, mediaPort), 5000);
                DataOutputStream out = new DataOutputStream(s.getOutputStream());
                out.writeUTF(callId);
                out.writeUTF(side);
// gửi audio
                out.writeUTF(Protocol.CALL_STREAM_AUDIO);
                out.flush();
                callAudioSocket = s;
                micThread = new Thread(() -> runMic(out, s), "mic-" + callId);
                speakerThread = new Thread(() -> runSpeaker(s), "spk-" + callId);
                micThread.setDaemon(true);
                speakerThread.setDaemon(true);
                micThread.start();
                speakerThread.start();
                append("[Gọi]", " Kênh audio đã kết nối.");
            } catch (Exception e) {
                append("[Gọi - Lỗi]", "Không mở được kênh audio: " + e.getMessage());
            }
        });
        if (withVideo) {
            svc.execute(() -> {
                try {
                    Socket s = new Socket();
                    s.connect(new InetSocketAddress(host, mediaPort), 5000);
                    DataOutputStream out = new DataOutputStream(s.getOutputStream());
                    out.writeUTF(callId);
                    out.writeUTF(side);
                    out.writeUTF(Protocol.CALL_STREAM_VIDEO);
                    out.flush();
                    callVideoSocket = s;
                    CallDialog dlg = activeCallDialog;
                    videoSendThread = new Thread(() -> runVideoSend(out, s, dlg), "vsend-" + callId);
                    videoRecvThread = new Thread(() -> runVideoRecv(s, dlg), "vrecv-" + callId);
                    videoSendThread.setDaemon(true);
                    videoRecvThread.setDaemon(true);
                    videoSendThread.start();
                    videoRecvThread.start();
                    append("[Gọi]", " Kênh video đã kết nối.");
                } catch (Exception e) {
                    append("[Gọi - Lỗi]", "Không mở được kênh video: " + e.getMessage());
                }
            });
        }
    }

    private void runMic(DataOutputStream out, Socket s) {
        TargetDataLine line = null;
        try {
            AudioFormat fmt = Protocol.getAudioFormat();
            DataLine.Info info = new DataLine.Info(TargetDataLine.class, fmt);
            line = (TargetDataLine) AudioSystem.getLine(info);
            line.open(fmt, Protocol.AUDIO_BUFFER_SIZE * 4);
            line.start();
            byte[] buf = new byte[Protocol.AUDIO_BUFFER_SIZE];
            CallDialog dlg;
            while (!s.isClosed() && (dlg = activeCallDialog) != null && !dlg.isLocallyClosed) {
                int n = line.read(buf, 0, buf.length);
                if (n <= 0) continue;
                if (dlg.myMicMuted) continue;
                try {
                    out.write(buf, 0, n);
                    out.flush();
                } catch (IOException e) { break; }
            }
        } catch (Exception e) {
            append("[Mic - Lỗi]", e.getMessage());
        } finally {
            if (line != null) try { line.stop(); line.close(); } catch (Exception ignored) { }
        }
    }

    private void runSpeaker(Socket s) {
        SourceDataLine line = null;
        try {
            AudioFormat fmt = Protocol.getAudioFormat();
            DataLine.Info info = new DataLine.Info(SourceDataLine.class, fmt);
            line = (SourceDataLine) AudioSystem.getLine(info);
            line.open(fmt, Protocol.AUDIO_BUFFER_SIZE * 4);
            line.start();
            DataInputStream in = new DataInputStream(s.getInputStream());
            byte[] buf = new byte[Protocol.AUDIO_BUFFER_SIZE];
            CallDialog dlg;
            while (!s.isClosed() && (dlg = activeCallDialog) != null && !dlg.isLocallyClosed) {
                int n = in.read(buf);
                if (n <= 0) break;
                if (!dlg.mySpeakerMuted) line.write(buf, 0, n);
            }
        } catch (Exception e) {
            if (!s.isClosed()) append("[Speaker - Lỗi]", e.getMessage());
        } finally {
            if (line != null) try { line.drain(); line.stop(); line.close(); } catch (Exception ignored) { }
        }
    }

    private void runVideoSend(DataOutputStream out, Socket s, CallDialog dlg0) {
        try {
            Robot robot = new Robot();
            Dimension dim = Toolkit.getDefaultToolkit().getScreenSize();
            while (!s.isClosed()) {
                CallDialog dlg = activeCallDialog;
                if (dlg == null || dlg.isLocallyClosed) break;
                if (dlg.myVideoOn) {
                    long start = System.currentTimeMillis();
                    try {
                        BufferedImage shot = robot.createScreenCapture(new Rectangle(0, 0, dim.width, dim.height));
                        BufferedImage scaled = new BufferedImage(Protocol.VIDEO_WIDTH, Protocol.VIDEO_HEIGHT, BufferedImage.TYPE_INT_RGB);
                        Graphics2D g2 = scaled.createGraphics();
                        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                        g2.drawImage(shot, 0, 0, Protocol.VIDEO_WIDTH, Protocol.VIDEO_HEIGHT, null);
                        g2.dispose();
                        ByteArrayOutputStream baos = new ByteArrayOutputStream();
                        ImageIO.write(scaled, "jpg", baos);
                        byte[] bytes = baos.toByteArray();
                        if (bytes.length > Protocol.VIDEO_MAX_FRAME) continue;
                        out.writeInt(bytes.length);
                        out.write(bytes);
                        out.flush();
                        long elapsed = System.currentTimeMillis() - start;
                        long sleep = Protocol.VIDEO_FPS_MS - elapsed;
                        if (sleep > 0) Thread.sleep(sleep);
                    } catch (Exception e) {
                        if (s.isClosed()) break;
                        Thread.sleep(Protocol.VIDEO_FPS_MS);
                    }
                } else {
                    Thread.sleep(Protocol.VIDEO_FPS_MS);
                }
            }
        } catch (Exception e) {
            append("[Video Gửi - Lỗi]", e.getMessage());
        }
    }

    private void runVideoRecv(Socket s, CallDialog dlg0) {
        try {
            DataInputStream in = new DataInputStream(s.getInputStream());
            while (!s.isClosed()) {
                CallDialog dlg = activeCallDialog;
                if (dlg == null || dlg.isLocallyClosed) break;
                int len = in.readInt();
                if (len <= 0 || len > Protocol.VIDEO_MAX_FRAME) break;
                byte[] buf = new byte[len];
                in.readFully(buf);
                if (dlg.peerVideoOn) {
                    try {
                        BufferedImage img = ImageIO.read(new ByteArrayInputStream(buf));
                        if (img != null) {
                            ImageIcon icon = new ImageIcon(img.getScaledInstance(
                                    Protocol.VIDEO_WIDTH, Protocol.VIDEO_HEIGHT, Image.SCALE_SMOOTH));
                            SwingUtilities.invokeLater(() -> dlg.setPeerVideoImage(icon));
                        }
                    } catch (Exception ignored) { }
                }
            }
        } catch (Exception ignored) { }
    }

    private void cleanupCall() {
        CallDialog dlg = activeCallDialog;
        activeCallDialog = null;
        if (dlg != null) {
            dlg.isLocallyClosed = true;
            try { dlg.dispose(); } catch (Exception ignored) { }
        }
        if (micThread != null)    { try { micThread.interrupt(); }    catch (Exception ignored) {} }
        if (speakerThread != null){ try { speakerThread.interrupt(); }catch (Exception ignored) {} }
        if (videoSendThread != null) { try { videoSendThread.interrupt(); } catch (Exception ignored) {} }
        if (videoRecvThread != null) { try { videoRecvThread.interrupt(); } catch (Exception ignored) {} }
        micThread = speakerThread = videoSendThread = videoRecvThread = null;
        if (callAudioSocket != null) try { callAudioSocket.close(); } catch (IOException ignored) {}
        if (callVideoSocket != null) try { callVideoSocket.close(); } catch (IOException ignored) {}
        callAudioSocket = callVideoSocket = null;
    }

    // ========== CallDialog ==========
    private class CallDialog extends JDialog {
        final String callId;
        final String peerName;
        final String myName;
        final String callerName;
        final String calleeName;
        final boolean isOutgoing;
        final boolean requestedWithVideo;
        LocalTime startedAt;
        volatile boolean isLocallyClosed;

        final JLabel statusLabel;
        final JLabel timerLabel;
        final JLabel peerAvatar;
        final JLabel peerStateMic;
        final JLabel peerStateVideo;
        final JLabel videoLabel;
        final JButton btnMic;
        final JButton btnSpk;
        final JButton btnVideo;
        final JButton btnHang;
        javax.swing.Timer callTimer;

        volatile boolean myMicMuted;
        volatile boolean mySpeakerMuted;
        volatile boolean myVideoOn;
        volatile boolean peerMicMuted;
        volatile boolean peerVideoOn;

        CallDialog(JFrame owner, String peerName, String callId,
                   String myName, String peerReal, boolean outgoing, boolean withVideo) {
            super(owner, " Cuộc gọi — " + peerName, false);
            this.callId = callId;
            this.peerName = peerName;
            this.myName = myName;
            this.callerName = outgoing ? myName : peerReal;
            this.calleeName = outgoing ? peerReal : myName;
            this.isOutgoing = outgoing;
            this.requestedWithVideo = withVideo;
            this.myVideoOn = outgoing ? withVideo : withVideo;
            this.peerVideoOn = outgoing ? withVideo : withVideo;

            setSize(620, withVideo ? 620 : 420);
            setLocationRelativeTo(owner);
            setDefaultCloseOperation(DISPOSE_ON_CLOSE);

            JPanel root = new JPanel(new BorderLayout(14, 14));
            root.setBorder(new EmptyBorder(18, 18, 18, 18));
            root.setBackground(new Color(241, 245, 249));
            setContentPane(root);

            // Avatar area
            JPanel top = new JPanel(new FlowLayout(FlowLayout.CENTER, 20, 8));
            top.setBackground(new Color(241, 245, 249));
            peerAvatar = new JLabel();
            peerAvatar.setPreferredSize(new Dimension(120, 120));
            peerAvatar.setHorizontalAlignment(SwingConstants.CENTER);
            peerAvatar.setVerticalAlignment(SwingConstants.CENTER);
            peerAvatar.setOpaque(true);
            peerAvatar.setBackground(new Color(79, 70, 229));
            peerAvatar.setForeground(Color.WHITE);
            peerAvatar.setFont(fontSegoe(Font.BOLD, 46));
            peerAvatar.setBorder(BorderFactory.createCompoundBorder(
                    new LineBorder(new Color(79, 70, 229), 4, true),
                    new EmptyBorder(4, 4, 4, 4)));
            String ch = peerName.isEmpty() ? "?" : peerName.substring(0, 1).toUpperCase();
            peerAvatar.setText(ch);

            JPanel info = new JPanel();
            info.setLayout(new BoxLayout(info, BoxLayout.Y_AXIS));
            info.setBackground(new Color(241, 245, 249));
            JLabel nameLbl = new JLabel(peerName);
            nameLbl.setFont(fontSegoe(Font.BOLD, 22));
            nameLbl.setAlignmentX(Component.CENTER_ALIGNMENT);
            statusLabel = new JLabel("...");
            statusLabel.setFont(fontSegoe(Font.PLAIN, 14));
            statusLabel.setForeground(TEXT_SECONDARY);
            statusLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
            timerLabel = new JLabel("00:00:00");
            timerLabel.setFont(fontSegoe(Font.BOLD, 16));
            timerLabel.setForeground(PRIMARY_DARK);
            timerLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
            peerStateMic = new JLabel(" Mic: bình thường");
            peerStateMic.setFont(fontSegoe(Font.PLAIN, 12));
            peerStateMic.setForeground(TEXT_SECONDARY);
            peerStateMic.setAlignmentX(Component.CENTER_ALIGNMENT);
            peerStateVideo = new JLabel(" Video: bình thường");
            peerStateVideo.setFont(fontSegoe(Font.PLAIN, 12));
            peerStateVideo.setForeground(TEXT_SECONDARY);
            peerStateVideo.setAlignmentX(Component.CENTER_ALIGNMENT);
            info.add(Box.createVerticalStrut(10));
            info.add(nameLbl);
            info.add(Box.createVerticalStrut(4));
            info.add(statusLabel);
            info.add(Box.createVerticalStrut(4));
            info.add(timerLabel);
            info.add(Box.createVerticalStrut(6));
            info.add(peerStateMic);
            info.add(peerStateVideo);

            top.add(peerAvatar);
            top.add(info);

            root.add(top, BorderLayout.NORTH);

            // Video panel
            videoLabel = new JLabel(" (video tắt) ");
            videoLabel.setHorizontalAlignment(SwingConstants.CENTER);
            videoLabel.setVerticalAlignment(SwingConstants.CENTER);
            videoLabel.setOpaque(true);
            videoLabel.setBackground(Color.BLACK);
            videoLabel.setForeground(new Color(148, 163, 184));
            videoLabel.setFont(fontSegoe(Font.BOLD, 14));
            videoLabel.setPreferredSize(new Dimension(Protocol.VIDEO_WIDTH, Protocol.VIDEO_HEIGHT));
            videoLabel.setBorder(new LineBorder(Color.BLACK, 2, true));
            JPanel videoWrap = new JPanel(new BorderLayout());
            videoWrap.setBackground(new Color(241, 245, 249));
            videoWrap.setBorder(BorderFactory.createTitledBorder(
                    new LineBorder(BORDER_COLOR, 1, true),
                    " Video (đối phương) — Demo: hiển thị ảnh chụp màn hình đối phương ",
                    javax.swing.border.TitledBorder.LEADING,
                    javax.swing.border.TitledBorder.TOP,
                    fontSegoe(Font.BOLD, 13),
                    PRIMARY_DARK));
            videoWrap.add(videoLabel, BorderLayout.CENTER);
            root.add(videoWrap, BorderLayout.CENTER);

            // Controls
            JPanel controls = new JPanel(new FlowLayout(FlowLayout.CENTER, 12, 8));
            controls.setBackground(new Color(241, 245, 249));
            btnMic = new JButton(" Mic: BẬT");
            btnSpk = new JButton(" Loa: BẬT");
            btnVideo = new JButton(myVideoOn ? " Video: BẬT" : " Video: TẮT");
            btnHang = new JButton(" CÚP MÁY");
            styleButton(btnMic, ACCENT_GREEN, new Color(5, 150, 105));
            styleButton(btnSpk, new Color(14, 165, 233), new Color(2, 132, 199));
            styleButton(btnVideo, ACCENT_PURPLE, new Color(124, 58, 237));
            styleButton(btnHang, ACCENT_RED, new Color(220, 38, 38));
            btnMic.setFont(fontSegoe(Font.BOLD, 13));
            btnSpk.setFont(fontSegoe(Font.BOLD, 13));
            btnVideo.setFont(fontSegoe(Font.BOLD, 13));
            btnHang.setFont(fontSegoe(Font.BOLD, 14));
            controls.add(btnMic);
            controls.add(btnSpk);
            controls.add(btnVideo);
            controls.add(btnHang);
            root.add(controls, BorderLayout.SOUTH);

            btnMic.addActionListener(ev -> {
                myMicMuted = !myMicMuted;
                if (myMicMuted) {
                    btnMic.setText("🎤 Mic: TẮT");
                    styleButton(btnMic, new Color(148, 163, 184), new Color(100, 116, 139));
                    sendCallControl(Protocol.CALL_CTL_MUTE_MIC);
                } else {
                    btnMic.setText("🎤 Mic: BẬT");
                    styleButton(btnMic, ACCENT_GREEN, new Color(5, 150, 105));
                    sendCallControl(Protocol.CALL_CTL_UNMUTE_MIC);
                }
            });
            btnSpk.addActionListener(ev -> {
                mySpeakerMuted = !mySpeakerMuted;
                if (mySpeakerMuted) {
                    btnSpk.setText(" Loa: TẮT");
                    styleButton(btnSpk, new Color(148, 163, 184), new Color(100, 116, 139));
                } else {
                    btnSpk.setText(" Loa: BẬT");
                    styleButton(btnSpk, new Color(14, 165, 233), new Color(2, 132, 199));
                }
            });
            btnVideo.addActionListener(ev -> {
                myVideoOn = !myVideoOn;
                btnVideo.setText(myVideoOn ? " Video: BẬT" : " Video: TẮT");
                sendCallControl(myVideoOn ? Protocol.CALL_CTL_VIDEO_ON : Protocol.CALL_CTL_VIDEO_OFF);
                if (!myVideoOn) videoLabel.setIcon(null);
            });
            btnHang.addActionListener(ev -> endCallNow());
            addWindowListener(new WindowAdapter() {
                @Override public void windowClosing(WindowEvent e) { endCallNow(); }
            });
        }

        void setStatus(String s) {
            statusLabel.setText(s);
        }

        void setPeerVideoImage(ImageIcon im) {
            if (isLocallyClosed) return;
            videoLabel.setText("");
            videoLabel.setIcon(im);
        }

        void setVideoEnabled(boolean on) {
            myVideoOn = on;
            peerVideoOn = on;
            btnVideo.setText(on ? " Video: BẬT" : " Video: TẮT");
        }

        void setPeerMicMuted(boolean muted) {
            peerMicMuted = muted;
            peerStateMic.setText(muted ? "🎙 Đối phương TẮT mic" : "🎙 Mic: bình thường");
            peerStateMic.setForeground(muted ? ACCENT_RED : TEXT_SECONDARY);
        }

        void setPeerVideoOn(boolean on) {
            peerVideoOn = on;
            peerStateVideo.setText(on ? " Video: đối phương bật video" : " Video: đối phương tắt video");
            peerStateVideo.setForeground(on ? ACCENT_GREEN : TEXT_SECONDARY);
            if (!on && videoLabel != null) videoLabel.setIcon(null);
        }

        void startTicking() {
            if (callTimer != null) return;
            callTimer = new javax.swing.Timer(1000, ev -> {
                if (startedAt == null) return;
                long sec = Duration.between(startedAt, LocalTime.now()).getSeconds();
                if (sec < 0) sec = 0;
                long h = sec / 3600, m = (sec % 3600) / 60, s = sec % 60;
                timerLabel.setText(String.format("%02d:%02d:%02d", h, m, s));
            });
            callTimer.start();
        }

        void endCallNow() {
            if (isLocallyClosed) return;
            isLocallyClosed = true;
            if (callTimer != null) { callTimer.stop(); callTimer = null; }
            append("[Gọi]", "Bạn đã cúp máy cuộc gọi với " + peerName);
            sendCallEnd();
            cleanupCall();
        }
    }

    // ========== Connection ==========
    private static class Connection {
        final Socket socket;
        final String name;
        final String hostInfo;
        final String serverHost;
        final int serverPort;
        final LocalTime connectedAt;
        final DataInputStream input;
        final DataOutputStream output;
        volatile int onlineCount;

        Connection(Socket socket, String name, String hostInfo, String serverHost, int serverPort) throws IOException {
            this.socket      = socket;
            this.name        = name;
            this.hostInfo    = hostInfo;
            this.serverHost  = serverHost;
            this.serverPort  = serverPort;
            this.connectedAt = LocalTime.now();
//   lấy luong
            this.input       = new DataInputStream(socket.getInputStream());
            this.output      = new DataOutputStream(socket.getOutputStream());
            this.onlineCount = 1;
        }

        void close() {
            try { socket.close(); } catch (IOException ignored) { }
        }
    }
}
