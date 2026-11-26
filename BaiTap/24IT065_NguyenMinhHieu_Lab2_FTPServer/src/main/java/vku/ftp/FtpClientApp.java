package vku.ftp;

import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPFile;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

/** FTP Client có giao diện: đăng nhập, liệt kê, tải lên và tải xuống file. */
public class FtpClientApp extends JFrame {
    private final JTextField hostField = new JTextField("127.0.0.1", 12);
    private final JTextField portField = new JTextField("2121", 5);
    private final JTextField userField = new JTextField("student", 10);
    private final JPasswordField passwordField = new JPasswordField("123456", 10);
    private final JTextField pathField = new JTextField("/", 15);
    private final JLabel statusLabel = new JLabel("Chưa kết nối");
    private final DefaultTableModel tableModel = new DefaultTableModel(
            new Object[]{"Tên", "Loại", "Kích thước (byte)"}, 0) {
        @Override public boolean isCellEditable(int row, int column) { return false; }
    };
    private final JTable fileTable = new JTable(tableModel);
    private final FTPClient ftp = new FTPClient();

    public FtpClientApp() {
        super("Lab 2 - FTP Client");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(760, 480);
        setLocationRelativeTo(null);
        buildInterface();
    }

    private void buildInterface() {
        JPanel connectionPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        connectionPanel.add(new JLabel("Host")); connectionPanel.add(hostField);
        connectionPanel.add(new JLabel("Port")); connectionPanel.add(portField);
        connectionPanel.add(new JLabel("User")); connectionPanel.add(userField);
        connectionPanel.add(new JLabel("Password")); connectionPanel.add(passwordField);
        JButton connectButton = new JButton("Kết nối");
        JButton disconnectButton = new JButton("Ngắt kết nối");
        connectionPanel.add(connectButton); connectionPanel.add(disconnectButton);

        JPanel filePanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        filePanel.add(new JLabel("Thư mục FTP")); filePanel.add(pathField);
        JButton refreshButton = new JButton("Làm mới");
        JButton uploadButton = new JButton("Tải lên");
        JButton downloadButton = new JButton("Tải xuống");
        filePanel.add(refreshButton); filePanel.add(uploadButton); filePanel.add(downloadButton);

        JPanel top = new JPanel(new GridLayout(2, 1));
        top.add(connectionPanel); top.add(filePanel);
        add(top, BorderLayout.NORTH);
        add(new JScrollPane(fileTable), BorderLayout.CENTER);
        statusLabel.setBorder(BorderFactory.createEmptyBorder(6, 10, 6, 10));
        add(statusLabel, BorderLayout.SOUTH);

        connectButton.addActionListener(e -> runTask(this::connect));
        disconnectButton.addActionListener(e -> runTask(this::disconnect));
        refreshButton.addActionListener(e -> runTask(this::refreshFiles));
        uploadButton.addActionListener(e -> chooseAndUpload());
        downloadButton.addActionListener(e -> chooseAndDownload());
        fileTable.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount() == 2) openSelectedDirectory();
            }
        });
        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override public void windowClosing(java.awt.event.WindowEvent e) { disconnectQuietly(); }
        });
    }

    private void connect() throws IOException {
        String host = hostField.getText().trim();
        int port = Integer.parseInt(portField.getText().trim());
        ftp.connect(host, port);
        if (!ftp.login(userField.getText().trim(), new String(passwordField.getPassword()))) {
            ftp.disconnect();
            throw new IOException("Sai tài khoản hoặc mật khẩu");
        }
        ftp.enterLocalPassiveMode();
        ftp.setFileType(FTP.BINARY_FILE_TYPE);
        ftp.changeWorkingDirectory(pathField.getText().trim());
        statusLabel.setText("Đã kết nối: " + host + ":" + port);
        refreshFiles();
    }

    private void refreshFiles() throws IOException {
        requireConnected();
        String path = pathField.getText().trim();
        if (!ftp.changeWorkingDirectory(path)) throw new IOException("Không mở được thư mục: " + path);
        tableModel.setRowCount(0);
        for (FTPFile file : ftp.listFiles()) {
            String type = file.isDirectory() ? "Thư mục" : "Tệp";
            tableModel.addRow(new Object[]{file.getName(), type, file.isDirectory() ? "-" : file.getSize()});
        }
        statusLabel.setText("Đang xem: " + path + " | " + tableModel.getRowCount() + " mục");
    }

    private void chooseAndUpload() {
        JFileChooser chooser = new JFileChooser();
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            File file = chooser.getSelectedFile();
            runTask(() -> {
                requireConnected();
                try (var input = java.nio.file.Files.newInputStream(file.toPath())) {
                    if (!ftp.storeFile(file.getName(), input)) throw new IOException(ftp.getReplyString());
                }
                refreshFiles();
                statusLabel.setText("Đã tải lên: " + file.getName());
            });
        }
    }

    private void chooseAndDownload() {
        int row = fileTable.getSelectedRow();
        if (row < 0 || "Thư mục".equals(tableModel.getValueAt(row, 1))) {
            showError("Chọn một tệp cần tải xuống."); return;
        }
        String name = tableModel.getValueAt(row, 0).toString();
        JFileChooser chooser = new JFileChooser();
        chooser.setSelectedFile(new File(name));
        if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            File destination = chooser.getSelectedFile();
            runTask(() -> {
                requireConnected();
                try (FileOutputStream output = new FileOutputStream(destination)) {
                    if (!ftp.retrieveFile(name, output)) throw new IOException(ftp.getReplyString());
                }
                statusLabel.setText("Đã tải xuống: " + destination.getName());
            });
        }
    }

    private void openSelectedDirectory() {
        int row = fileTable.getSelectedRow();
        if (row < 0 || !"Thư mục".equals(tableModel.getValueAt(row, 1))) return;
        String name = tableModel.getValueAt(row, 0).toString();
        String current = pathField.getText().trim();
        pathField.setText((current.endsWith("/") ? current : current + "/") + name);
        runTask(this::refreshFiles);
    }

    private void disconnect() throws IOException {
        if (ftp.isConnected()) ftp.logout();
        disconnectQuietly();
        tableModel.setRowCount(0);
        statusLabel.setText("Đã ngắt kết nối");
    }

    private void disconnectQuietly() {
        try { if (ftp.isConnected()) ftp.disconnect(); } catch (IOException ignored) { }
    }

    private void requireConnected() throws IOException {
        if (!ftp.isConnected()) throw new IOException("Hãy kết nối đến FTP Server trước");
    }

    private void runTask(IOTask task) {
        statusLabel.setText("Đang xử lý...");
        new SwingWorker<Void, Void>() {
            @Override protected Void doInBackground() throws Exception { task.run(); return null; }
            @Override protected void done() {
                try { get(); }
                catch (Exception e) { showError(e.getCause() == null ? e.getMessage() : e.getCause().getMessage()); }
            }
        }.execute();
    }

    private void showError(String message) {
        statusLabel.setText("Lỗi: " + message);
        JOptionPane.showMessageDialog(this, message, "FTP Client", JOptionPane.ERROR_MESSAGE);
    }

    @FunctionalInterface private interface IOTask { void run() throws Exception; }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new FtpClientApp().setVisible(true));
    }
}
