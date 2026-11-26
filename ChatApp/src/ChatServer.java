import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.JTableHeader;
import java.awt.*;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.Duration;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ChatServer extends JFrame {

    // ========== Màu sắc chủ đạo (Modern UI palette - giống Client cho đồng bộ) ==========
    private static final Color PRIMARY        = new Color(16, 185, 129);   // Emerald #10B981 (màu server)
    private static final Color PRIMARY_DARK   = new Color(5, 150, 105);    // #059669
    private static final Color SECONDARY      = new Color(79, 70, 229);    // Indigo #4F46E5
    private static final Color ACCENT_RED     = new Color(239, 68, 68);    // Red #EF4444
    private static final Color SURFACE        = new Color(248, 250, 252);  // #F8FAFC
    private static final Color SURFACE_DARK   = new Color(241, 245, 249);  // #F1F5F9
    private static final Color TEXT_PRIMARY   = new Color(15, 23, 42);     // Slate #0F172A
    private static final Color TEXT_SECONDARY = new Color(71, 85, 105);    // Slate #475569
    private static final Color TEXT_WHITE     = Color.WHITE;
    private static final Color BORDER_COLOR   = new Color(226, 232, 240);  // #E2E8F0
    private static final Color ROW_ALT        = new Color(240, 253, 244);  // Zebra row xanh nhạt

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");

    // ========== State server ==========
    private final ConcurrentHashMap<String, ClientHandler> clients = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, LocalTime> connectTimeMap = new ConcurrentHashMap<>();
    private final JTextField portField = new JTextField("5000", 5);
    private final JButton startButton = new JButton("Khởi động Server");
    private final JButton stopButton  = new JButton("Dừng Server");
    private final JTextArea logArea   = new JTextArea();
    private final JLabel statusBadge  = new JLabel("Server đã dừng");

    // Bảng hiển thị thông tin Client — cột cuối đổi thành "Thời gian online"
    private final DefaultTableModel tableModel = new DefaultTableModel(
            new String[]{"Tên Client", "IP Client", "Port Client", "Thời gian online"}, 0) {
        @Override public boolean isCellEditable(int row, int column) { return false; }
    };
    private final JTable clientTable = new JTable(tableModel);

    private ServerSocket serverSocket;
    private Thread serverThread;
    private volatile boolean isRunning = false;

    // ========== Group Chat ==========
    private final ConcurrentHashMap<String, GroupInfo> groups = new ConcurrentHashMap<>();

    // ========== Call Signaling + Media Relay ==========
    private final ConcurrentHashMap<String, CallInfo> activeCalls = new ConcurrentHashMap<>();
    private ServerSocket mediaServerSocket;
    private Thread mediaRelayThread;
    private final Map<String, MediaSlot> pendingMedia = new ConcurrentHashMap<>();

    // Bộ đếm cập nhật cột "Thời gian online" mỗi giây
    private final javax.swing.Timer ticker;

    // ========== Inner classes: GroupInfo, CallInfo, MediaSlot ==========
    private static class GroupInfo {
        final String id;
        final String name;
        final String creator;
        final Set<String> members;
        final LocalTime createdAt;
        GroupInfo(String id, String name, String creator, Collection<String> members) {
            this.id = id;
            this.name = name;
            this.creator = creator;
            this.members = Collections.newSetFromMap(new ConcurrentHashMap<>());
            this.members.addAll(members);
            this.createdAt = LocalTime.now();
        }
    }

    private static class CallInfo {
        final String callId;
        final String caller;
        final String callee;
        volatile boolean accepted;
        volatile boolean ended;
        CallInfo(String callId, String caller, String callee) {
            this.callId = callId;
            this.caller = caller;
            this.callee = callee;
        }
    }

    private static class MediaSlot {
        final String callId;
        final String side;   // CALLER / CALLEE
        final String type;   // AUDIO / VIDEO
        final Socket socket;
        final DataInputStream in;
        final DataOutputStream out;
        MediaSlot(String callId, String side, String type, Socket socket) throws IOException {
            this.callId = callId;
            this.side = side;
            this.type = type;
            this.socket = socket;
            this.in = new DataInputStream(socket.getInputStream());
            this.out = new DataOutputStream(socket.getOutputStream());
        }
    }

    public static void main(String[] args) {
        setModernLookAndFeel();
        SwingUtilities.invokeLater(() -> new ChatServer().setVisible(true));
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
        UIManager.put("nimbusBase", new Color(16, 185, 129));
        UIManager.put("nimbusFocus", new Color(79, 70, 229));
        UIManager.put("nimbusLightBackground", Color.WHITE);
        UIManager.put("Table.background", Color.WHITE);
        UIManager.put("Table.gridColor", new Color(226, 232, 240));
    }

    public ChatServer() {
        super("ChatApp Server — Trung tâm điều khiển");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(960, 680);
        setMinimumSize(new Dimension(860, 560));
        setLocationRelativeTo(null);
        getContentPane().setBackground(SURFACE);

        ticker = new javax.swing.Timer(1000, e -> updateOnlineDurationColumn());
        ticker.start();

        JPanel root = new JPanel(new BorderLayout(12, 12));
        root.setBorder(new EmptyBorder(16, 16, 16, 16));
        root.setBackground(SURFACE);
        setContentPane(root);

        root.add(buildHeader(),  BorderLayout.NORTH);
        root.add(buildCenter(),  BorderLayout.CENTER);
        root.add(buildStatusBar(), BorderLayout.SOUTH);

        styleAll();
        stopButton.setEnabled(false);

        startButton.addActionListener(e -> startServer());
        stopButton.addActionListener(e -> stopServer());

        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override public void windowClosing(java.awt.event.WindowEvent e) {
                ticker.stop();
                stopServer();
                dispose();
            }
        });
    }

    // ========== Layout ==========
    private JPanel buildHeader() {
        JPanel header = new JPanel(new BorderLayout()) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                GradientPaint gp = new GradientPaint(0, 0,
                        new Color(16, 185, 129),
                        getWidth(), 0,
                        new Color(79, 70, 229));
                g2.setPaint(gp);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 16, 16);
            }
        };
        header.setPreferredSize(new Dimension(0, 92));
        header.setOpaque(false);

        JLabel title = new JLabel("<html>"
                + "<div style='color:white; font-size:20px; font-weight:bold;'>ChatApp — Server Control</div>"
//                + "<div style='color:rgba(255,255,255,0.92); font-size:12px;'>Mô hình TCP Client-Server • Chat riêng & chung • Nhóm • Gọi Audio/Video</div>"
                + "</html>");
        title.setBorder(new EmptyBorder(14, 22, 14, 22));
        header.add(title, BorderLayout.WEST);

        JPanel ctrl = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        ctrl.setOpaque(false);
        ctrl.setBorder(new EmptyBorder(14, 14, 14, 22));

        ctrl.add(labelWhite("Cổng (Port):"));
        portField.setFont(fontSegoe(Font.BOLD, 13));
        portField.setBorder(new LineBorder(Color.WHITE, 1, true));
        portField.setBackground(new Color(255, 255, 255, 240));
        portField.setMargin(new Insets(4, 8, 4, 8));
        ctrl.add(portField);
        ctrl.add(startButton);
        ctrl.add(stopButton);

        header.add(ctrl, BorderLayout.EAST);
        return header;
    }

    private JSplitPane buildCenter() {
        // --- Bảng danh sách client ---
        clientTable.setFont(fontSegoe(Font.PLAIN, 13));
        clientTable.setRowHeight(30);
        clientTable.setShowVerticalLines(false);
        clientTable.setIntercellSpacing(new Dimension(0, 0));
        clientTable.setSelectionBackground(new Color(167, 243, 208));
        clientTable.setSelectionForeground(TEXT_PRIMARY);
        clientTable.setGridColor(BORDER_COLOR);
        clientTable.getTableHeader().setReorderingAllowed(false);

        JTableHeader h = clientTable.getTableHeader();
        h.setFont(fontSegoe(Font.BOLD, 13));
        h.setBackground(new Color(175, 239, 234));
        h.setForeground(Color.BLACK);
        h.setPreferredSize(new Dimension(h.getWidth(), 36));
        DefaultTableCellRenderer headerRenderer = (DefaultTableCellRenderer) h.getDefaultRenderer();
        headerRenderer.setHorizontalAlignment(SwingConstants.CENTER);
        headerRenderer.setBackground(new Color(175, 239, 234));
        headerRenderer.setForeground(Color.BLACK);
        headerRenderer.setBorder(new LineBorder(new Color(94, 234, 212), 1));
        h.setDefaultRenderer(headerRenderer);

        DefaultTableCellRenderer center = new DefaultTableCellRenderer();
        center.setHorizontalAlignment(SwingConstants.CENTER);
        for (int i = 0; i < 4; i++) {
            clientTable.getColumnModel().getColumn(i).setCellRenderer(new ZebraCellRenderer());
            if (i != 0) clientTable.getColumnModel().getColumn(i).setCellRenderer(center);
        }
        clientTable.getColumnModel().getColumn(0).setPreferredWidth(180);
        clientTable.getColumnModel().getColumn(1).setPreferredWidth(160);
        clientTable.getColumnModel().getColumn(2).setPreferredWidth(110);
        clientTable.getColumnModel().getColumn(3).setPreferredWidth(160);

        JScrollPane tableScroll = new JScrollPane(clientTable);
        tableScroll.setBorder(BorderFactory.createTitledBorder(
                new LineBorder(BORDER_COLOR, 1, true),
                " Danh sách Client đang Online (đồng bộ mỗi giây) ",
                javax.swing.border.TitledBorder.LEADING,
                javax.swing.border.TitledBorder.TOP,
                fontSegoe(Font.BOLD, 13),
                PRIMARY_DARK));
        tableScroll.getViewport().setBackground(Color.WHITE);

        // --- Nhật ký log ---
        logArea.setEditable(false);
        logArea.setLineWrap(true);
        logArea.setWrapStyleWord(true);
        logArea.setBackground(Color.BLACK);
        logArea.setForeground(new Color(74, 222, 128));
        logArea.setCaretColor(new Color(134, 239, 172));
        logArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        logArea.setMargin(new Insets(10, 12, 10, 12));

        JScrollPane logScroll = new JScrollPane(logArea);
        logScroll.setBorder(BorderFactory.createTitledBorder(
                new LineBorder(BORDER_COLOR, 1, true),
                " Nhật ký Server  ",
                javax.swing.border.TitledBorder.LEADING,
                javax.swing.border.TitledBorder.TOP,
                fontSegoe(Font.BOLD, 13),
                PRIMARY_DARK));

        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, tableScroll, logScroll);
        split.setDividerLocation(270);
        split.setResizeWeight(0.42);
        split.setBackground(SURFACE);
        split.setBorder(null);
        return split;
    }

    private JPanel buildStatusBar() {
        JPanel bar = new JPanel(new BorderLayout());
        bar.setBorder(new EmptyBorder(4, 8, 0, 8));
        bar.setBackground(SURFACE);
        statusBadge.setFont(fontSegoe(Font.BOLD, 13));
        statusBadge.setForeground(ACCENT_RED);
        statusBadge.setIcon(new ColorDotIcon(ACCENT_RED, 10));
        statusBadge.setBorder(new EmptyBorder(4, 6, 4, 6));

//        JLabel tip = new JLabel("  Lưu ý: Chat Nhóm • Gọi Audio/Video đều chạy qua TCP & Media Relay.");
//        tip.setFont(fontSegoe(Font.ITALIC, 12));
//        tip.setForeground(TEXT_SECONDARY);
//
//        bar.add(statusBadge, BorderLayout.WEST);
//        bar.add(tip, BorderLayout.EAST);
        return bar;
    }

    // ========== Style ==========
    private void styleAll() {
        styleButton(startButton, PRIMARY,      PRIMARY_DARK);
        styleButton(stopButton,  ACCENT_RED,   new Color(220, 38, 38));
    }

    private static JLabel labelWhite(String text) {
        JLabel l = new JLabel(text);
        l.setForeground(TEXT_WHITE);
        l.setFont(fontSegoe(Font.BOLD, 13));
        return l;
    }

    private static Font fontSegoe(int style, int size) {
        try { return new Font("Segoe UI", style, size); }
        catch (Exception e) { return new Font(Font.SANS_SERIF, style, size); }
    }

    private static class ZebraCellRenderer extends DefaultTableCellRenderer {
        @Override public Component getTableCellRendererComponent(JTable t, Object v,
                boolean sel, boolean focus, int row, int col) {
            super.getTableCellRendererComponent(t, v, sel, focus, row, col);
            if (!sel) {
                setBackground(row % 2 == 0 ? Color.WHITE : ROW_ALT);
                setForeground(TEXT_PRIMARY);
            }
            setBorder(new EmptyBorder(0, 8, 0, 8));
            if (col == 3) {
                setHorizontalAlignment(SwingConstants.CENTER);
                setFont(fontSegoe(Font.BOLD, 13));
                setForeground(SECONDARY);
            }
            return this;
        }
    }

    private static void styleButton(JButton b, Color bg, Color bgHover) {
        b.setForeground(TEXT_WHITE);
        b.setFont(fontSegoe(Font.BOLD, 12));
        b.setFocusPainted(false);
        b.setBorderPainted(false);
        b.setContentAreaFilled(false);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.setBorder(new EmptyBorder(10, 18, 10, 18));

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

    private static class ColorDotIcon implements Icon {
        private final Color color;
        private final int size;
        ColorDotIcon(Color c, int s) { this.color = c; this.size = s; }
        @Override public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2 = (Graphics2D) g;
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(color);
            g2.fillOval(x, y + (c.getHeight() - size) / 2 - 1, size, size);
        }
        @Override public int getIconWidth()  { return size + 6; }
        @Override public int getIconHeight() { return size; }
    }

    // ========== Bộ đếm thời gian online ==========
    private void updateOnlineDurationColumn() {
        if (tableModel.getRowCount() == 0) return;
        LocalTime now = LocalTime.now();
        for (int i = 0; i < tableModel.getRowCount(); i++) {
            String name = String.valueOf(tableModel.getValueAt(i, 0));
            LocalTime at = connectTimeMap.get(name);
            if (at == null) continue;
            long sec = Duration.between(at, now).getSeconds();
            if (sec < 0) sec = 0;
            long h = sec / 3600, m = (sec % 3600) / 60, s = sec % 60;
            tableModel.setValueAt(String.format("%02d:%02d:%02d", h, m, s), i, 3);
        }
    }

    // ========== Log helper ==========
    private void log(String message) {
        String t = LocalTime.now().format(TIME_FMT);
        SwingUtilities.invokeLater(() -> {
            logArea.append("[" + t + "]  " + message + "\n");
            logArea.setCaretPosition(logArea.getDocument().getLength());
        });
    }

    // ========== Group helpers ==========
    private void sendGroupsToClient(String userName) {
        ClientHandler ch = clients.get(userName);
        if (ch == null) return;
        java.util.List<String[]> userGroups = new ArrayList<>(); // [id, name, count]
        for (GroupInfo g : groups.values()) {
            if (g.members.contains(userName)) {
                userGroups.add(new String[]{g.id, g.name, String.valueOf(g.members.size())});
            }
        }
        ch.send(out -> {
            out.writeUTF("GROUPS_LIST");
            out.writeInt(userGroups.size());
            for (String[] g : userGroups) {
                out.writeUTF(g[0]);
                out.writeUTF(g[1]);
                out.writeUTF(g[2]);
            }
        });
    }

    private void broadcastGroupsToMembers(GroupInfo g) {
        for (String member : g.members) sendGroupsToClient(member);
    }

    // ========== Control server start/stop ==========
    private synchronized void startServer() {
        int port;
        try {
            port = Integer.parseInt(portField.getText().trim());
            if (port < 1 || port > 65535) throw new NumberFormatException();
        } catch (NumberFormatException e) {
            JOptionPane.showMessageDialog(this, "Port phải là số nguyên từ 1 đến 65535!", "Lỗi", JOptionPane.ERROR_MESSAGE);
            return;
        }

        serverThread = new Thread(() -> {
            try {
                serverSocket = new ServerSocket(port);
                startMediaRelay(port + 1);
                isRunning = true;
                SwingUtilities.invokeLater(() -> {
                    startButton.setEnabled(false);
                    stopButton.setEnabled(true);
                    portField.setEnabled(false);
                    statusBadge.setText("Server đang chạy — cổng " + port + " (Media cổng " + (port + 1) + ")");
                    statusBadge.setForeground(PRIMARY_DARK);
                    statusBadge.setIcon(new ColorDotIcon(PRIMARY, 10));
                });
                log(" ======= SERVER KHỞI ĐỘNG THÀNH CÔNG trên cổng " + port + " (Media cổng " + (port + 1) + ") =======");

                while (isRunning) {
                    Socket socket = serverSocket.accept();
                    new Thread(new ClientHandler(socket), "client-" + socket.getPort()).start();
                }
            } catch (IOException e) {
                if (isRunning) log(" Lỗi ServerSocket: " + e.getMessage());
            } finally {
                stopServer();
            }
        });
        serverThread.start();
    }

    private void startMediaRelay(int mediaPort) {
        int attempts = 0;
        while (attempts < 5) {
            try {
                final int chosen = mediaPort + attempts;
                mediaServerSocket = new ServerSocket(chosen);
                final int realPort = chosen;
                mediaRelayThread = new Thread(() -> runMediaRelay(realPort), "media-relay");
                mediaRelayThread.setDaemon(true);
                mediaRelayThread.start();
                log(" Media Relay sẵn sàng trên cổng " + realPort);
                return;
            } catch (IOException e) {
                attempts++;
            }
        }
        log(" Không mở được Media Relay (từ cổng " + mediaPort + " đến " + (mediaPort + 4) + ").");
    }

    private void runMediaRelay(int mediaPort) {
        while (isRunning && !Thread.currentThread().isInterrupted()) {
            try {
                Socket s = mediaServerSocket.accept();
                new Thread(() -> handleMediaSocket(s), "media-slot").start();
            } catch (IOException e) {
                if (!isRunning) break;
            }
        }
    }

    private void handleMediaSocket(Socket socket) {
        try {
            DataInputStream in = new DataInputStream(socket.getInputStream());
            DataOutputStream out = new DataOutputStream(socket.getOutputStream());
            String callId = in.readUTF();
            String side = in.readUTF();
            String streamType = in.readUTF();
            String slotKey = callId + "|" + side + "|" + streamType;
            String peerSide = Protocol.CALL_SIDE_CALLER.equals(side)
                    ? Protocol.CALL_SIDE_CALLEE : Protocol.CALL_SIDE_CALLER;
            String peerKey = callId + "|" + peerSide + "|" + streamType;

            MediaSlot mySlot = new MediaSlot(callId, side, streamType, socket);
            MediaSlot peerSlot = pendingMedia.remove(peerKey);
            if (peerSlot != null) {
                log(" Media Relay ghép cặp [" + callId + "/" + streamType + "]: " + side + " <-> " + peerSide);
                MediaSlot a = mySlot, b = peerSlot;
                Thread t1 = new Thread(() -> pumpBytes(a.in, b.out, streamType, callId, a.side + "->" + b.side),
                        "pump-" + slotKey);
                Thread t2 = new Thread(() -> pumpBytes(b.in, a.out, streamType, callId, b.side + "->" + a.side),
                        "pump-" + peerKey);
                t1.setDaemon(true); t2.setDaemon(true);
                t1.start(); t2.start();
            } else {
                pendingMedia.put(slotKey, mySlot);
            }
        } catch (IOException e) {
            try { socket.close(); } catch (IOException ignored) { }
        }
    }

    private void pumpBytes(DataInputStream in, DataOutputStream out, String type,
                           String callId, String tag) {
        try {
            if (Protocol.CALL_STREAM_AUDIO.equals(type)) {
                byte[] buf = new byte[Protocol.AUDIO_BUFFER_SIZE];
                int n;
                while ((n = in.read(buf)) != -1) {
                    out.write(buf, 0, n);
                    out.flush();
                }
            } else {
                while (true) {
                    int len = in.readInt();
                    if (len <= 0 || len > Protocol.VIDEO_MAX_FRAME) break;
                    byte[] buf = new byte[len];
                    in.readFully(buf);
                    out.writeInt(len);
                    out.write(buf);
                    out.flush();
                }
            }
        } catch (IOException ignored) { }
    }

    private synchronized void stopServer() {
        if (!isRunning && (serverSocket == null || serverSocket.isClosed())
                && tableModel.getRowCount() == 0
                && (mediaServerSocket == null || mediaServerSocket.isClosed())) {
            SwingUtilities.invokeLater(() -> {
                startButton.setEnabled(true);
                stopButton.setEnabled(false);
                portField.setEnabled(true);
                statusBadge.setText(" Server đã dừng");
                statusBadge.setForeground(ACCENT_RED);
                statusBadge.setIcon(new ColorDotIcon(ACCENT_RED, 10));
            });
            return;
        }
        isRunning = false;
        try {
            if (serverSocket != null && !serverSocket.isClosed()) serverSocket.close();
        } catch (IOException e) {
            log("️ Lỗi đóng ServerSocket: " + e.getMessage());
        }
        try {
            if (mediaServerSocket != null && !mediaServerSocket.isClosed()) mediaServerSocket.close();
        } catch (IOException e) {
            log(" Lỗi đóng MediaServerSocket: " + e.getMessage());
        }
        for (ClientHandler client : clients.values()) client.close();
        clients.clear();
        connectTimeMap.clear();
        groups.clear();
        activeCalls.clear();
        pendingMedia.clear();

        SwingUtilities.invokeLater(() -> {
            tableModel.setRowCount(0);
            startButton.setEnabled(true);
            stopButton.setEnabled(false);
            portField.setEnabled(true);
            statusBadge.setText(" Server đã dừng");
            statusBadge.setForeground(ACCENT_RED);
            statusBadge.setIcon(new ColorDotIcon(ACCENT_RED, 10));
        });
        log(" ======= SERVER ĐÃ DỪNG HOÀN TOÀN =======");
    }

    // ========== Broadcast danh sách user ==========
    private synchronized void broadcastUsers() {
        java.util.List<String> names = new ArrayList<>(clients.keySet());
        Collections.sort(names);
        for (ClientHandler client : clients.values()) {
            client.send(out -> {
                out.writeUTF("USERS");
                out.writeInt(names.size());
                for (String name : names) out.writeUTF(name);
            });
        }
    }

    @FunctionalInterface
    private interface PacketWriter {
        void write(DataOutputStream out) throws IOException;
    }

    // ========== Thread xử lý mỗi client ==========
    private class ClientHandler implements Runnable {
        private final Socket socket;
        private DataInputStream input;
        private DataOutputStream output;
        private String name;
        private boolean registered;
        private final String clientIp;
        private final int clientPort;

        ClientHandler(Socket socket) {
            this.socket     = socket;
            this.clientIp   = socket.getInetAddress().getHostAddress();
            this.clientPort = socket.getPort();
        }

        synchronized boolean send(PacketWriter packet) {
            try {
                packet.write(output);
                output.flush();
                return true;
            } catch (IOException e) {
                close();
                return false;
            }
        }

        private void error(String message) {
            send(out -> {
                out.writeUTF("ERROR");
                out.writeUTF(message);
            });
        }

        @Override
        public void run() {
            try {
                socket.setSoTimeout(15000);
                input  = new DataInputStream(socket.getInputStream());
                output = new DataOutputStream(socket.getOutputStream());

                if (!"LOGIN".equals(input.readUTF())) return;
                name = input.readUTF().trim();
                if (!name.matches("[\\p{L}\\p{N}_-]{1,20}")) {
                    error("Tên gồm 1–20 chữ cái, chữ số, dấu _ hoặc -.");
                    return;
                }

                LocalTime connectedAt;
                synchronized (this) {
                    if (clients.putIfAbsent(name, this) != null) {
                        error("Tên này đang được sử dụng.");
                        return;
                    }
                    registered = true;
                    connectedAt = LocalTime.now();
                    connectTimeMap.put(name, connectedAt);
                    send(out -> out.writeUTF("WELCOME"));
                }
                socket.setSoTimeout(0);
                sendGroupsToClient(name);

                String t = connectedAt.format(TIME_FMT);
                log(" CLIENT VÀO: \"" + name + "\" [IP=" + clientIp + " | Port=" + clientPort + " | lúc " + t + "]");
                final LocalTime fat = connectedAt;
                SwingUtilities.invokeLater(() -> {
                    tableModel.addRow(new Object[]{name, clientIp, clientPort, "00:00:00"});
                    updateOnlineDurationColumn();
                });

                broadcastInfo(name + " đã tham gia phòng chat lúc " + t);
                broadcastUsers();

                while (true) {
                    String type = input.readUTF();
                    if ("CHAT".equals(type))              handleChat();
                    else if ("FILE".equals(type))         handleFile();
                    else if ("CREATE_GROUP".equals(type)) handleCreateGroup();
                    else if ("GROUP_CHAT".equals(type))   handleGroupChat();
                    else if ("GROUP_FILE".equals(type))   handleGroupFile();
                    else if ("CALL_INVITE".equals(type))  handleCallInvite();
                    else if ("CALL_ACCEPT".equals(type))  handleCallAccept();
                    else if ("CALL_REJECT".equals(type))  handleCallReject();
                    else if ("CALL_END".equals(type))     handleCallEnd();
                    else if ("CALL_CONTROL".equals(type)) handleCallControl();
                    else throw new IOException("Loại gói tin không hợp lệ: " + type);
                }
            } catch (IOException e) {
                // client ngắt kết nối
            } finally {
                close();
                if (registered) {
                    String leave = LocalTime.now().format(TIME_FMT);
                    log(" CLIENT RA: \"" + name + "\" [Port=" + clientPort + " | lúc " + leave + "]");
                    SwingUtilities.invokeLater(() -> {
                        for (int i = 0; i < tableModel.getRowCount(); i++) {
                            if (String.valueOf(tableModel.getValueAt(i, 0)).equals(name)) {
                                tableModel.removeRow(i);
                                break;
                            }
                        }
                    });
                    connectTimeMap.remove(name);
                    Iterator<Map.Entry<String, CallInfo>> it = activeCalls.entrySet().iterator();
                    while (it.hasNext()) {
                        Map.Entry<String, CallInfo> ent = it.next();
                        CallInfo ci = ent.getValue();
                        if (name.equals(ci.caller) || name.equals(ci.callee)) {
                            String other = name.equals(ci.caller) ? ci.callee : ci.caller;
                            it.remove();
                            ClientHandler otherH = clients.get(other);
                            if (otherH != null) {
                                final String fCallId = ci.callId;
                                otherH.send(out -> {
                                    out.writeUTF("CALL_END");
                                    out.writeUTF(fCallId);
                                    out.writeUTF(name);
                                    out.writeUTF(other);
                                });
                            }
                            log(" Cuộc gọi [" + ci.callId + "] kết thúc do \"" + name + "\" ngắt kết nối.");
                        }
                    }
                    if (clients.remove(name, this)) {
                        broadcastInfo(name + " đã rời phòng chat lúc " + leave);
                        broadcastUsers();
                    }
                }
            }
        }

        private void handleChat() throws IOException {
            String recipient = input.readUTF();
            String text = input.readUTF();
            if (text.trim().isEmpty() || text.length() > Protocol.MAX_MESSAGE_LENGTH) {
                error("Tin nhắn phải có từ 1 đến 4000 ký tự.");
                return;
            }
            log(" CHAT: " + name + " → " + recipient + " | " +
                    (text.length() <= 80 ? text : text.substring(0, 80) + "..."));
            PacketWriter packet = out -> {
                out.writeUTF("CHAT");
                out.writeUTF(name);
                out.writeUTF(recipient);
                out.writeUTF(text);
            };
            if (Protocol.EVERYONE.equals(recipient)) {
                for (ClientHandler client : clients.values()) client.send(packet);
            } else {
                ClientHandler target = clients.get(recipient);
                if (target == null || !target.send(packet)) error("Người nhận đã offline.");
                else if (target != this) send(packet);
            }
        }

        private void handleFile() throws IOException {
            String recipient = input.readUTF();
            String filename = Protocol.safeFileName(input.readUTF());
            byte[] bytes = Protocol.readFile(input);
            log(" FILE: " + name + " → " + recipient + " | " + filename + " | " + bytes.length + " byte");
            ClientHandler target = clients.get(recipient);
            if (target == null) {
                error("Không gửi được file: người nhận đã offline.");
                return;
            }
            boolean sent = target.send(out -> {
                out.writeUTF("FILE");
                out.writeUTF(name);
                out.writeUTF(filename);
                out.writeInt(bytes.length);
                out.write(bytes);
            });
            if (sent) {
                send(out -> {
                    out.writeUTF("INFO");
                    out.writeUTF("Đã chuyển file " + filename + " (" + bytes.length + " byte) đến " + recipient + ".");
                });
            } else {
                error("Không chuyển được file: kết nối người nhận đã đóng.");
            }
        }

        // ========== Group handlers ==========
        private void handleCreateGroup() throws IOException {
            String rawName = input.readUTF();
            int memberCount = input.readInt();
            if (memberCount < 1 || memberCount > Protocol.MAX_GROUP_MEMBERS) {
                error("Số thành viên nhóm không hợp lệ (từ 1 đến " + Protocol.MAX_GROUP_MEMBERS + ").");
                return;
            }
            Set<String> members = new HashSet<>();
            members.add(name); // creator luôn là thành viên
            for (int i = 0; i < memberCount; i++) {
                String m = input.readUTF();
                if (clients.containsKey(m)) members.add(m);
            }
            if (members.size() < 2) {
                error("Cần ít nhất 1 thành viên khác trong nhóm.");
                return;
            }
            String groupName = Protocol.safeGroupName(rawName);
            String groupId = Protocol.GROUP_PREFIX + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
            GroupInfo g = new GroupInfo(groupId, groupName, name, members);
            groups.put(groupId, g);

            log("👥 NHÓM MỚI: \"" + groupName + "\" [" + groupId + "] tạo bởi " + name
                    + " | " + members.size() + " thành viên: " + String.join(", ", members));

            PacketWriter pw = out -> {
                out.writeUTF("GROUP_CREATED");
                out.writeUTF(groupId);
                out.writeUTF(groupName);
                out.writeUTF(name);
                out.writeInt(members.size());
                for (String m : members) out.writeUTF(m);
            };
            for (String m : members) {
                ClientHandler ch = clients.get(m);
                if (ch != null) ch.send(pw);
            }
            broadcastGroupsToMembers(g);
            send(out -> {
                out.writeUTF("INFO");
                out.writeUTF("Bạn đã tạo nhóm \"" + groupName + "\" (" + members.size() + " người).");
            });
        }

        private void handleGroupChat() throws IOException {
            String groupId = input.readUTF();
            String text = input.readUTF();
            if (text.trim().isEmpty() || text.length() > Protocol.MAX_MESSAGE_LENGTH) {
                error("Tin nhắn phải có từ 1 đến 4000 ký tự.");
                return;
            }
            GroupInfo g = groups.get(groupId);
            if (g == null || !g.members.contains(name)) {
                error("Bạn không tham gia nhóm này.");
                return;
            }
            log(" NHÓM CHAT: [" + g.name + "] " + name + " → " + g.members.size() + " thành viên");
            PacketWriter pw = out -> {
                out.writeUTF("GROUP_CHAT");
                out.writeUTF(groupId);
                out.writeUTF(g.name);
                out.writeUTF(name);
                out.writeUTF(text);
            };
            for (String m : g.members) {
                ClientHandler ch = clients.get(m);
                if (ch != null) ch.send(pw);
            }
        }

        private void handleGroupFile() throws IOException {
            String groupId = input.readUTF();
            String filename = Protocol.safeFileName(input.readUTF());
            byte[] bytes = Protocol.readFile(input);
            GroupInfo g = groups.get(groupId);
            if (g == null || !g.members.contains(name)) {
                error("Bạn không tham gia nhóm này.");
                return;
            }
            log(" NHÓM FILE: [" + g.name + "] " + name + " gửi " + filename + " (" + bytes.length + " bytes) đến " + g.members.size() + " thành viên");
            PacketWriter pw = out -> {
                out.writeUTF("GROUP_FILE");
                out.writeUTF(groupId);
                out.writeUTF(g.name);
                out.writeUTF(name);
                out.writeUTF(filename);
                out.writeInt(bytes.length);
                out.write(bytes);
            };
            int delivered = 0;
            for (String m : g.members) {
                ClientHandler ch = clients.get(m);
                if (ch != null && ch.send(pw)) delivered++;
            }
            final int sentCount = delivered;
            send(out -> {
                out.writeUTF("INFO");
                out.writeUTF("Đã gửi file \"" + filename + "\" đến " + sentCount + "/" + g.members.size() + " thành viên nhóm \"" + g.name + "\".");
            });
        }

        // ========== Call Signaling handlers ==========
        private void handleCallInvite() throws IOException {
            String callId = input.readUTF();
            String from = input.readUTF();
            String to = input.readUTF();
            boolean hasVideo = input.readBoolean();
            int mediaPort = input.readInt();
            if (!name.equals(from) || from.equals(to)) {
                error("Thông tin cuộc gọi không hợp lệ.");
                return;
            }
            ClientHandler callee = clients.get(to);
            if (callee == null) {
                send(out -> {
                    out.writeUTF("CALL_REJECT");
                    out.writeUTF(callId);
                    out.writeUTF(to);
                    out.writeUTF(from);
                    out.writeUTF("Người dùng đã offline.");
                });
                return;
            }
            CallInfo ci = new CallInfo(callId, from, to);
            activeCalls.put(callId, ci);
            log(" CUỘC GỌI [" + callId + "] " + from + " → " + to
                    + (hasVideo ? " (video)" : " (audio-only)") + " | media cổng đề nghị: " + mediaPort);
            boolean ok = callee.send(out -> {
                out.writeUTF("CALL_INVITE");
                out.writeUTF(callId);
                out.writeUTF(from);
                out.writeUTF(to);
                out.writeBoolean(hasVideo);
                out.writeInt(mediaPort);
            });
            if (!ok) {
                activeCalls.remove(callId);
                send(o -> {
                    o.writeUTF("CALL_REJECT");
                    o.writeUTF(callId);
                    o.writeUTF(to);
                    o.writeUTF(from);
                    o.writeUTF("Kết nối người nhận không ổn định.");
                });
            }
        }

        private void handleCallAccept() throws IOException {
            String callId = input.readUTF();
            String from = input.readUTF();
            String to = input.readUTF();
            int mediaPort = input.readInt();
            if (!name.equals(from)) { error("Thông tin không hợp lệ."); return; }
            CallInfo ci = activeCalls.get(callId);
            if (ci == null || !ci.callee.equals(from) || !ci.caller.equals(to)) return;
            ci.accepted = true;
            log("Cuộc gọi [" + callId + "] được chấp nhận bởi " + from);
            ClientHandler caller = clients.get(to);
            if (caller != null) {
                caller.send(out -> {
                    out.writeUTF("CALL_ACCEPT");
                    out.writeUTF(callId);
                    out.writeUTF(from);
                    out.writeUTF(to);
                    out.writeInt(mediaPort);
                });
            }
        }

        private void handleCallReject() throws IOException {
            String callId = input.readUTF();
            String from = input.readUTF();
            String to = input.readUTF();
            String reason = input.readUTF();
            if (!name.equals(from)) return;
            CallInfo ci = activeCalls.remove(callId);
            if (ci == null) return;
            log(" Cuộc gọi [" + callId + "] bị " + from + " từ chối: " + reason);
            ClientHandler caller = clients.get(to);
            if (caller != null) {
                caller.send(out -> {
                    out.writeUTF("CALL_REJECT");
                    out.writeUTF(callId);
                    out.writeUTF(from);
                    out.writeUTF(to);
                    out.writeUTF(reason);
                });
            }
        }

        private void handleCallEnd() throws IOException {
            String callId = input.readUTF();
            String from = input.readUTF();
            String to = input.readUTF();
            if (!name.equals(from)) return;
            CallInfo ci = activeCalls.remove(callId);
            if (ci == null) return;
            ci.ended = true;
            log(" Cuộc gọi [" + callId + "] kết thúc bởi " + from);
            ClientHandler other = clients.get(to);
            if (other != null) {
                other.send(out -> {
                    out.writeUTF("CALL_END");
                    out.writeUTF(callId);
                    out.writeUTF(from);
                    out.writeUTF(to);
                });
            }
        }

        private void handleCallControl() throws IOException {
            String callId = input.readUTF();
            String from = input.readUTF();
            String to = input.readUTF();
            String control = input.readUTF();
            if (!name.equals(from)) return;
            CallInfo ci = activeCalls.get(callId);
            if (ci == null) return;
            log(" Control [" + callId + "] " + from + " → " + control);
            ClientHandler other = clients.get(to);
            if (other != null) {
                other.send(out -> {
                    out.writeUTF("CALL_CONTROL");
                    out.writeUTF(callId);
                    out.writeUTF(from);
                    out.writeUTF(to);
                    out.writeUTF(control);
                });
            }
        }

        void close() {
            try { socket.close(); } catch (IOException ignored) { }
        }
    }

    // Gửi thông báo INFO đến tất cả client
    private synchronized void broadcastInfo(String message) {
        String withTime = "[" + LocalTime.now().format(TIME_FMT) + "] " + message;
        for (ClientHandler client : clients.values()) {
            client.send(out -> {
                out.writeUTF("INFO");
                out.writeUTF(withTime);
            });
        }
    }
}
