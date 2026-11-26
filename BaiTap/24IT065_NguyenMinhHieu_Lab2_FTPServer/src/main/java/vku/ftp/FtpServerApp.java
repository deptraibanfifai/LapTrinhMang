package vku.ftp;

import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.sql.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;

/** FTP Server tối giản hỗ trợ USER/PASS, PASV, LIST, RETR, STOR, DELE, CWD, PWD, TYPE, QUIT. */
public class FtpServerApp extends JFrame {
    private final JTextArea output = new JTextArea();
    private final JButton startButton = new JButton("Khởi động FTP Server");
    private final FtpServer server = new FtpServer(this::log);

    public FtpServerApp() {
        super("Lab 2 - FTP Server + MySQL");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(780, 500);
        setLocationRelativeTo(null);
        output.setEditable(false);
        output.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        add(new JScrollPane(output), BorderLayout.CENTER);
        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT));
        bar.add(startButton);
        bar.add(new JLabel("Tài khoản mẫu: student / 123456    |    FTP port: 2121"));
        add(bar, BorderLayout.NORTH);
        startButton.addActionListener(e -> toggleServer());
        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override public void windowClosing(java.awt.event.WindowEvent e) { server.stop(); }
        });
        log("Sẵn sàng. Hãy import database.sql vào Laragon trước khi khởi động.");
    }

    private void toggleServer() {
        if (server.isRunning()) {
            server.stop(); startButton.setText("Khởi động FTP Server");
        } else {
            try {
                server.start(); startButton.setText("Dừng FTP Server");
            } catch (Exception ex) { log("Không khởi động được: " + ex.getMessage()); }
        }
    }

    private void log(String message) {
        SwingUtilities.invokeLater(() -> {
            output.append("[" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")) + "] " + message + "\n");
            output.setCaretPosition(output.getDocument().getLength());
        });
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new FtpServerApp().setVisible(true));
    }

    static final class FtpServer {
        private final java.util.function.Consumer<String> logger;
        private volatile boolean running;
        private ServerSocket controlSocket;
        private ExecutorService workers;
        private final int port = Integer.parseInt(System.getenv().getOrDefault("FTP_PORT", "2121"));
        private final Path root = Paths.get(System.getenv().getOrDefault("FTP_ROOT", "storage")).toAbsolutePath().normalize();
        FtpServer(java.util.function.Consumer<String> logger) { this.logger = logger; }
        boolean isRunning() { return running; }

        synchronized void start() throws Exception {
            Class.forName("com.mysql.cj.jdbc.Driver");
            try (Connection ignored = db()) { }
            Files.createDirectories(root);
            controlSocket = new ServerSocket(port);
            workers = Executors.newCachedThreadPool(); running = true;
            workers.submit(() -> {
                logger.accept("FTP Server đang nghe cổng " + port + "; thư mục: " + root);
                while (running) try {
                    Socket client = controlSocket.accept();
                    workers.submit(new ClientSession(client));
                } catch (IOException e) { if (running) logger.accept("Lỗi nhận kết nối: " + e.getMessage()); }
            });
        }

        synchronized void stop() {
            running = false;
            try { if (controlSocket != null) controlSocket.close(); } catch (IOException ignored) { }
            if (workers != null) workers.shutdownNow();
            logger.accept("FTP Server đã dừng.");
        }

        private Connection db() throws SQLException {
            String url = System.getenv().getOrDefault("DB_URL", "jdbc:mysql://localhost:3306/lab2_ftp?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Ho_Chi_Minh");
            String user = System.getenv().getOrDefault("DB_USER", "root");
            String pass = System.getenv().getOrDefault("DB_PASSWORD", "12345Ty67?");
            return DriverManager.getConnection(url, user, pass);
        }

        private boolean authenticate(String username, String password) {
            String sql = "SELECT id FROM ftp_users WHERE username=? AND password_sha256=SHA2(?,256) AND enabled=TRUE";
            try (Connection c = db(); PreparedStatement p = c.prepareStatement(sql)) {
                p.setString(1, username); p.setString(2, password);
                try (ResultSet r = p.executeQuery()) { return r.next(); }
            } catch (SQLException e) { logger.accept("MySQL: " + e.getMessage()); return false; }
        }

        private void history(String username, String action, String file, long size, String ip) {
            String sql = "INSERT INTO transfer_history(username,action,file_name,file_size,client_ip) VALUES(?,?,?,?,?)";
            try (Connection c = db(); PreparedStatement p = c.prepareStatement(sql)) {
                p.setString(1, username); p.setString(2, action); p.setString(3, file);
                if (size < 0) p.setNull(4, Types.BIGINT); else p.setLong(4, size);
                p.setString(5, ip); p.executeUpdate();
            } catch (SQLException e) { logger.accept("Không ghi được lịch sử MySQL: " + e.getMessage()); }
        }

        private final class ClientSession implements Runnable {
            private final Socket control;
            private BufferedReader in; private BufferedWriter out;
            private String username = "", pendingUser = "";
            private boolean loggedIn;
            private Path current = root;
            private ServerSocket passive;
            private final String ip;
            ClientSession(Socket control) { this.control = control; this.ip = control.getInetAddress().getHostAddress(); }

            @Override public void run() {
                try (control) {
                    in = new BufferedReader(new InputStreamReader(control.getInputStream(), StandardCharsets.UTF_8));
                    out = new BufferedWriter(new OutputStreamWriter(control.getOutputStream(), StandardCharsets.UTF_8));
                    reply("220 Lab2 FTP Server ready");
                    String line;
                    while ((line = in.readLine()) != null && running) {
                        String[] parts = line.split(" ", 2);
                        String command = parts[0].toUpperCase(Locale.ROOT);
                        String arg = parts.length > 1 ? parts[1].trim() : "";
                        logger.accept(ip + " -> " + command + (arg.isEmpty() ? "" : " " + arg));
                        if (!handle(command, arg)) break;
                    }
                } catch (IOException e) { logger.accept("Client " + ip + " ngắt kết nối."); }
                finally { closePassive(); }
            }

            private boolean handle(String command, String arg) throws IOException {
                switch (command) {
                    case "USER" -> { pendingUser = arg; reply("331 Password required"); }
                    case "PASS" -> {
                        username = pendingUser;
                        loggedIn = authenticate(username, arg);
                        history(username, loggedIn ? "LOGIN" : "LOGIN_FAILED", null, -1, ip);
                        reply(loggedIn ? "230 Login successful" : "530 Login incorrect");
                    }
                    case "QUIT" -> { reply("221 Goodbye"); return false; }
                    case "SYST" -> reply("215 UNIX Type: L8");
                    case "FEAT" -> { replyRaw("211-Features\r\n PASV\r\n SIZE\r\n UTF8\r\n211 End"); }
                    case "TYPE" -> reply("200 Type set");
                    case "NOOP" -> reply("200 OK");
                    case "PWD", "XPWD" -> { if (!authRequired()) break; reply("257 \"" + virtualPath(current) + "\""); }
                    case "PASV" -> openPassive();
                    case "CWD", "CDUP" -> {
                        if (!authRequired()) break;
                        Path next = command.equals("CDUP") ? current.getParent() : resolve(arg);
                        if (next != null && next.startsWith(root) && Files.isDirectory(next)) { current = next; reply("250 Directory changed"); }
                        else reply("550 Directory unavailable");
                    }
                    case "LIST", "NLST" -> list(arg);
                    case "RETR" -> retrieve(arg);
                    case "STOR" -> store(arg);
                    case "DELE" -> delete(arg);
                    case "MKD", "XMKD" -> mkdir(arg);
                    case "RMD", "XRMD" -> removeDir(arg);
                    case "SIZE" -> size(arg);
                    case "OPTS" -> reply("200 Options accepted");
                    default -> reply("502 Command not implemented");
                }
                return true;
            }

            private boolean authRequired() throws IOException { if (!loggedIn) { reply("530 Please login"); return false; } return true; }
            private void openPassive() throws IOException {
                closePassive();
                for (int p = 21000; p <= 21020; p++) try { passive = new ServerSocket(p, 1, InetAddress.getByName("0.0.0.0")); break; } catch (IOException ignored) { }
                if (passive == null) { reply("425 No passive port available"); return; }
                int p = passive.getLocalPort();
                byte[] a = control.getLocalAddress().getAddress();
                reply("227 Entering Passive Mode (" + (a[0]&255) + "," + (a[1]&255) + "," + (a[2]&255) + "," + (a[3]&255) + "," + p/256 + "," + p%256 + ")");
            }
            private Socket dataSocket() throws IOException {
                if (passive == null) throw new IOException("Hãy gửi lệnh PASV trước");
                passive.setSoTimeout(15000);
                Socket data = passive.accept(); closePassive(); return data;
            }
            private void list(String arg) throws IOException {
                if (!authRequired()) return;
                try (Socket data = dataSocket(); BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(data.getOutputStream(), StandardCharsets.UTF_8))) {
                    Path target = arg.isBlank() ? current : resolve(arg);
                    if (target == null || !Files.isDirectory(target)) { reply("550 Not a directory"); return; }
                    reply("150 Opening data connection");
                    try (var entries = Files.list(target)) {
                        for (Path f : entries.sorted().toList()) {
                            String type = Files.isDirectory(f) ? "d" : "-";
                            String name = f.getFileName().toString();
                            writer.write(type + "rw-r--r-- 1 owner group " + Files.size(f) + " Jan 01 00:00 " + name + "\r\n");
                        }
                    }
                    writer.flush(); reply("226 Transfer complete");
                } catch (IOException e) { reply("425 " + e.getMessage()); }
            }
            private void retrieve(String arg) throws IOException {
                if (!authRequired()) return;
                Path file = resolve(arg);
                if (file == null || !Files.isRegularFile(file)) { reply("550 File not found"); return; }
                long bytes = Files.size(file);
                try (Socket data = dataSocket(); OutputStream os = data.getOutputStream()) {
                    reply("150 Opening binary data connection"); Files.copy(file, os); os.flush();
                    history(username, "DOWNLOAD", file.getFileName().toString(), bytes, ip);
                    reply("226 Transfer complete");
                } catch (IOException e) { reply("425 " + e.getMessage()); }
            }
            private void store(String arg) throws IOException {
                if (!authRequired()) return;
                Path file = resolve(arg);
                if (file == null || arg.isBlank()) { reply("550 Invalid path"); return; }
                Files.createDirectories(file.getParent());
                try (Socket data = dataSocket(); InputStream is = data.getInputStream()) {
                    reply("150 Ready to receive"); long bytes = Files.copy(is, file, StandardCopyOption.REPLACE_EXISTING);
                    history(username, "UPLOAD", file.getFileName().toString(), bytes, ip);
                    reply("226 Transfer complete");
                } catch (IOException e) { reply("425 " + e.getMessage()); }
            }
            private void delete(String arg) throws IOException {
                if (!authRequired()) return;
                Path file = resolve(arg);
                if (file != null && Files.isRegularFile(file)) {
                    long bytes = Files.size(file); Files.delete(file); history(username, "DELETE", file.getFileName().toString(), bytes, ip); reply("250 File deleted");
                } else reply("550 File unavailable");
            }
            private void mkdir(String arg) throws IOException {
                if (!authRequired()) return;
                Path dir = resolve(arg);
                try { if (dir == null) throw new IOException(); Files.createDirectories(dir); reply("257 Directory created"); }
                catch (IOException e) { reply("550 Cannot create directory"); }
            }
            private void removeDir(String arg) throws IOException {
                if (!authRequired()) return;
                Path dir = resolve(arg);
                try { if (dir == null || dir.equals(root)) throw new IOException(); Files.delete(dir); reply("250 Directory removed"); }
                catch (IOException e) { reply("550 Cannot remove directory"); }
            }
            private void size(String arg) throws IOException {
                if (!authRequired()) return;
                Path f = resolve(arg);
                if (f != null && Files.isRegularFile(f)) reply("213 " + Files.size(f)); else reply("550 File unavailable");
            }
            private Path resolve(String raw) {
                if (raw == null || raw.isBlank()) return current;
                Path p = Paths.get(raw.replace('\\', '/'));
                Path resolved = (p.isAbsolute() ? root.resolve(p.toString().replaceFirst("^/+", "")) : current.resolve(p)).normalize().toAbsolutePath();
                return resolved.startsWith(root) ? resolved : null;
            }
            private String virtualPath(Path path) { String v = root.relativize(path).toString().replace('\\', '/'); return "/" + v; }
            private void closePassive() { if (passive != null) try { passive.close(); } catch (IOException ignored) { } finally { passive = null; } }
            private void reply(String text) throws IOException { out.write(text + "\r\n"); out.flush(); }
            private void replyRaw(String text) throws IOException { out.write(text + "\r\n"); out.flush(); }
        }
    }
}
