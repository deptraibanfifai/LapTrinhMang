import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ChatServer extends JFrame {
    private final ConcurrentHashMap<String, ClientHandler> clients = new ConcurrentHashMap<>();
    private final JTextField portField = new JTextField("5000", 5);
    private final JButton startButton = new JButton("Khởi động Server");
    private final JButton stopButton = new JButton("Dừng Server");
    private final JTextArea logArea = new JTextArea();
    private final DefaultTableModel tableModel = new DefaultTableModel(
            new String[]{"Tên Client", "IP", "Port", "Trạng thái", "Vào lúc"}, 0);
    private final JTable clientTable = new JTable(tableModel);

    private ServerSocket serverSocket;
    private Thread serverThread;
    private volatile boolean isRunning = false;

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new ChatServer().setVisible(true));
    }

    public ChatServer() {
        super("Java Chat Server - Group 4");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(850, 600);
        setLocationRelativeTo(null);

        JPanel root = new JPanel(new BorderLayout(8, 8));
        root.setBorder(new EmptyBorder(10, 10, 10, 10));
        setContentPane(root);

        JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        topPanel.add(new JLabel("Cổng (Port):"));
        topPanel.add(portField);
        topPanel.add(startButton);
        topPanel.add(stopButton);
        stopButton.setEnabled(false);
        root.add(topPanel, BorderLayout.NORTH);

        logArea.setEditable(false);
        logArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        JScrollPane tableScrollPane = new JScrollPane(clientTable);
        tableScrollPane.setBorder(BorderFactory.createTitledBorder("Danh sách Client"));
        JScrollPane logScrollPane = new JScrollPane(logArea);
        logScrollPane.setBorder(BorderFactory.createTitledBorder("Nhật ký Server"));

        JSplitPane centerSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT, tableScrollPane, logScrollPane);
        centerSplit.setDividerLocation(250);
        root.add(centerSplit, BorderLayout.CENTER);

        startButton.addActionListener(e -> startServer());
        stopButton.addActionListener(e -> stopServer());
    }

    private void log(String message) {
        String time = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
        SwingUtilities.invokeLater(() -> {
            logArea.append("[" + time + "] " + message + "\n");
            logArea.setCaretPosition(logArea.getDocument().getLength());
        });
    }

    private synchronized void startServer() {
        int port;
        try {
            port = Integer.parseInt(portField.getText().trim());
        } catch (NumberFormatException e) {
            JOptionPane.showMessageDialog(this, "Port không hợp lệ!", "Lỗi", JOptionPane.ERROR_MESSAGE);
            return;
        }
        serverThread = new Thread(() -> {
            try {
                serverSocket = new ServerSocket(port);
                isRunning = true;
                SwingUtilities.invokeLater(() -> {
                    startButton.setEnabled(false);
                    stopButton.setEnabled(true);
                    portField.setEnabled(false);
                });
                log("Server khởi động trên cổng " + port);
                while (isRunning) {
                    Socket socket = serverSocket.accept();
                    new Thread(new ClientHandler(socket), "client-" + socket.getPort()).start();
                }
            } catch (IOException e) {
                if (isRunning) log("Lỗi: " + e.getMessage());
            } finally {
                stopServer();
            }
        });
        serverThread.start();
    }

    private synchronized void stopServer() {
        if (!isRunning && serverSocket == null) return;
        isRunning = false;
        try {
            if (serverSocket != null) serverSocket.close();
            for (ClientHandler client : clients.values()) client.close();
            clients.clear();
        } catch (IOException e) {
            log("Lỗi khi đóng: " + e.getMessage());
        }
        SwingUtilities.invokeLater(() -> {
            tableModel.setRowCount(0);
            startButton.setEnabled(true);
            stopButton.setEnabled(false);
            portField.setEnabled(true);
        });
        log("Server đã dừng.");
    }

    // ✅ Gửi danh sách user kèm trạng thái
    private synchronized void broadcastUsers() {
        List<String> names = new ArrayList<>(clients.keySet());
        Collections.sort(names);
        for (ClientHandler client : clients.values()) {
            client.send(out -> {
                out.writeUTF("USERS");
                out.writeInt(names.size());
                for (String name : names) {
                    out.writeUTF(name);
                    ClientHandler target = clients.get(name);
                    out.writeUTF(target != null ? target.status : Protocol.STATUS_ONLINE);
                }
            });
        }
    }

    @FunctionalInterface
    private interface PacketWriter {
        void write(DataOutputStream out) throws IOException;
    }

    private class ClientHandler implements Runnable {
        private final Socket socket;
        private DataInputStream input;
        private DataOutputStream output;
        private String name;
        private String status = Protocol.STATUS_ONLINE;
        private boolean registered;
        private String connectTime;

        ClientHandler(Socket socket) { this.socket = socket; }

        synchronized boolean send(PacketWriter packet) {
            try { packet.write(output); output.flush(); return true; }
            catch (IOException e) { close(); return false; }
        }

        @Override
        public void run() {
            try {
                socket.setSoTimeout(15000);
                input = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
                output = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));

                if (!"LOGIN".equals(input.readUTF())) return;
                name = input.readUTF().trim();
                if (!name.matches("[\\p{L}\\p{N}_-]{1,20}")) {
                    send(out -> { out.writeUTF("ERROR"); out.writeUTF("Tên không hợp lệ."); });
                    return;
                }

                synchronized (this) {
                    if (clients.putIfAbsent(name, this) != null) {
                        send(out -> { out.writeUTF("ERROR"); out.writeUTF("Tên đã tồn tại."); });
                        return;
                    }
                    registered = true;
                    connectTime = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
                    send(out -> out.writeUTF("WELCOME"));
                }

                socket.setSoTimeout(0);
                log("Client '" + name + "' vào hệ thống.");
                SwingUtilities.invokeLater(() ->
                        tableModel.addRow(new Object[]{name, socket.getInetAddress().getHostAddress(), socket.getPort(), status, connectTime}));
                broadcastUsers();

                while (true) {
                    String type = input.readUTF();
                    switch (type) {
                        case "CHAT": handleChat(); break;
                        case "FILE": handleFile(); break;
                        case "RECALL": handleRecall(); break; // ✅ Thu hồi
                        case "READ": handleRead(); break;     // ✅ Đã xem
                        case "SET_STATUS": handleStatus(); break; // ✅ Đổi trạng thái
                        default: throw new IOException("Gói tin lạ: " + type);
                    }
                }
            } catch (IOException e) { /* Disconnected */ }
            finally {
                close();
                if (registered) {
                    log("Client '" + name + "' rời đi.");
                    SwingUtilities.invokeLater(() -> {
                        for (int i = 0; i < tableModel.getRowCount(); i++) {
                            if (tableModel.getValueAt(i, 0).equals(name)) { tableModel.removeRow(i); break; }
                        }
                    });
                    if (clients.remove(name, this)) broadcastUsers();
                }
            }
        }

        private void handleChat() throws IOException {
            String msgId = input.readUTF();
            String recipient = input.readUTF();
            String text = input.readUTF();
            if (text.length() > Protocol.MAX_MESSAGE_LENGTH) return;

            PacketWriter packet = out -> {
                out.writeUTF("CHAT"); out.writeUTF(msgId); out.writeUTF(name); out.writeUTF(recipient); out.writeUTF(text);
            };

            if (Protocol.EVERYONE.equals(recipient)) {
                for (ClientHandler c : clients.values()) c.send(packet);
            } else {
                ClientHandler target = clients.get(recipient);
                if (target != null) {
                    target.send(packet);
                    if (target != this) send(packet); // Echo lại cho người gửi
                } else {
                    send(out -> { out.writeUTF("ERROR"); out.writeUTF("Người nhận offline."); });
                }
            }
        }

        // ✅ Xử lý thu hồi tin nhắn
        private void handleRecall() throws IOException {
            String msgId = input.readUTF();
            String recipient = input.readUTF();

            PacketWriter packet = out -> {
                out.writeUTF("RECALL"); out.writeUTF(msgId); out.writeUTF(name); out.writeUTF(recipient);
            };

            if (Protocol.EVERYONE.equals(recipient)) {
                for (ClientHandler c : clients.values()) c.send(packet);
            } else {
                ClientHandler target = clients.get(recipient);
                if (target != null) {
                    target.send(packet);
                    if (target != this) send(packet);
                }
            }
        }

        // ✅ Xử lý thông báo đã đọc
        private void handleRead() throws IOException {
            String msgId = input.readUTF();
            String originalSender = input.readUTF(); // Người gửi tin nhắn gốc

            ClientHandler target = clients.get(originalSender);
            if (target != null && target != this) {
                target.send(out -> {
                    out.writeUTF("READ"); out.writeUTF(msgId); out.writeUTF(name); // name là người đã đọc
                });
            }
        }

        // ✅ Xử lý đổi trạng thái
        private void handleStatus() throws IOException {
            String newStatus = input.readUTF();
            this.status = newStatus;
            SwingUtilities.invokeLater(() -> {
                for (int i = 0; i < tableModel.getRowCount(); i++) {
                    if (tableModel.getValueAt(i, 0).equals(name)) {
                        tableModel.setValueAt(newStatus, i, 3);
                        break;
                    }
                }
            });
            broadcastUsers(); // Cập nhật cho tất cả client biết
        }

        private void handleFile() throws IOException {
            String recipient = input.readUTF();
            String filename = Protocol.safeFileName(input.readUTF());
            long fileSize = input.readLong();

            ClientHandler target = clients.get(recipient);
            if (target == null) {
                skipFileData(fileSize);
                send(out -> { out.writeUTF("ERROR"); out.writeUTF("Người nhận offline."); });
                return;
            }

            target.send(out -> { out.writeUTF("FILE_START"); out.writeUTF(name); out.writeUTF(filename); out.writeLong(fileSize); });

            byte[] buffer = new byte[Protocol.CHUNK_SIZE];
            long remaining = fileSize;
            boolean success = true;
            while (remaining > 0) {
                int toRead = (int) Math.min(Protocol.CHUNK_SIZE, remaining);
                input.readFully(buffer, 0, toRead);
                final byte[] chunk = buffer.clone();
                final int chunkSize = toRead;
                success = target.send(out -> { out.writeUTF("FILE_CHUNK"); out.writeInt(chunkSize); out.write(chunk, 0, chunkSize); });
                if (!success) break;
                remaining -= toRead;
            }

            if (success) {
                target.send(out -> out.writeUTF("FILE_END"));
                send(out -> { out.writeUTF("INFO"); out.writeUTF("Đã gửi file " + filename); });
            }
        }

        private void skipFileData(long size) throws IOException {
            byte[] buffer = new byte[Protocol.CHUNK_SIZE];
            long remaining = size;
            while (remaining > 0) {
                int toRead = (int) Math.min(Protocol.CHUNK_SIZE, remaining);
                input.readFully(buffer, 0, toRead);
                remaining -= toRead;
            }
        }

        private void close() { try { socket.close(); } catch (IOException ignored) {} }
    }
}