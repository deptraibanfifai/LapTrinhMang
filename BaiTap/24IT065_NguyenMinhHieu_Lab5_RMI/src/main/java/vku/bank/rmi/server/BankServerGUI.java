package vku.bank.rmi.server;

import vku.bank.rmi.model.Account;
import vku.bank.rmi.model.Transaction;
import vku.bank.rmi.remote.BankRemote;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class BankServerGUI extends JFrame implements ServerLogListener {
    private static final long serialVersionUID = 1L;

    public static void main(String[] args) {
        String serverName = "SERVER-1";
        String bindIp = "127.0.0.1";
        int registryPort = 1099;
        int servicePort = 10991;
        String peerIp = "127.0.0.1";
        int peerRegistryPort = 1100;

        String defaultDbUrl = "jdbc:mysql://localhost:3306/bank_db?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Ho_Chi_Minh";
        String dbUrl = System.getenv().getOrDefault("DB_URL", defaultDbUrl);
        String dbUser = System.getenv().getOrDefault("DB_USER", "root");
        String dbPassword = System.getenv().getOrDefault("DB_PASSWORD", "");

        boolean autoStart = false;
        if (args.length >= 6) {
            serverName = args[0];
            bindIp = args[1];
            registryPort = Integer.parseInt(args[2]);
            servicePort = Integer.parseInt(args[3]);
            peerIp = args[4];
            peerRegistryPort = Integer.parseInt(args[5]);
            autoStart = true;
        }
        if (args.length >= 7) dbUrl = args[6];
        if (args.length >= 8) dbUser = args[7];
        if (args.length >= 9) dbPassword = args[8];

        final String fServerName = serverName;
        final String fBindIp = bindIp;
        final int fRegistryPort = registryPort;
        final int fServicePort = servicePort;
        final String fPeerIp = peerIp;
        final int fPeerRegistryPort = peerRegistryPort;
        final String fDbUrl = dbUrl;
        final String fDbUser = dbUser;
        final String fDbPassword = dbPassword;
        final boolean fAutoStart = autoStart;

        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignored) {
            }
            BankServerGUI gui = new BankServerGUI(fServerName, fBindIp, fRegistryPort, fServicePort,
                    fPeerIp, fPeerRegistryPort, fDbUrl, fDbUser, fDbPassword);
            gui.setVisible(true);

            if (fAutoStart) {
                gui.startServer();
            }
        });
    }

    // Config Fields
    private JComboBox<String> cbServerPreset;
    private JTextField txtServerName;
    private JTextField txtBindIp;
    private JTextField txtRegistryPort;
    private JTextField txtServicePort;
    private JTextField txtPeerIp;
    private JTextField txtPeerRegistryPort;
    private JTextField txtDbUrl;
    private JTextField txtDbUser;
    private JPasswordField txtDbPassword;

    // Control Buttons
    private JButton btnStart;
    private JButton btnStop;
    private JButton btnSyncPeer;
    private JButton btnRefresh;
    private JLabel lblServerStatus;

    // Tables
    private DefaultTableModel accountTableModel;
    private JTable tblAccounts;
    private JLabel lblTotalBalance;

    private DefaultTableModel txTableModel;
    private JTable tblTransactions;

    private JTextArea txtLogs;

    // Server State
    private BankStore store;
    private BankServiceImpl service;
    private Registry registry;
    private boolean isRunning = false;

    public BankServerGUI(String defaultServerName, String defaultBindIp, int defaultRegPort,
                         int defaultSvcPort, String defaultPeerIp, int defaultPeerRegPort,
                         String defaultDbUrl, String defaultDbUser, String defaultDbPass) {
        setTitle("BẢNG ĐIỀU KHIỂN RMI BANK SERVER - LAB 5 VKU");
        setSize(1050, 740);
        setMinimumSize(new Dimension(920, 650));
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        initComponents(defaultServerName, defaultBindIp, defaultRegPort, defaultSvcPort,
                defaultPeerIp, defaultPeerRegPort, defaultDbUrl, defaultDbUser, defaultDbPass);

        // Đóng server khi tắt cửa sổ
        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent e) {
                stopServer();
            }
        });
    }

    private void initComponents(String defaultServerName, String defaultBindIp, int defaultRegPort,
                                int defaultSvcPort, String defaultPeerIp, int defaultPeerRegPort,
                                String defaultDbUrl, String defaultDbUser, String defaultDbPass) {
        setLayout(new BorderLayout(5, 5));

        // 1. TOP PANEL: Server Configuration & Control
        JPanel topPanel = createTopConfigPanel(defaultServerName, defaultBindIp, defaultRegPort,
                defaultSvcPort, defaultPeerIp, defaultPeerRegPort, defaultDbUrl, defaultDbUser, defaultDbPass);
        add(topPanel, BorderLayout.NORTH);

        // 2. CENTER PANEL: Tabs for Data Inspection
        JTabbedPane tabbedPane = new JTabbedPane();
        tabbedPane.setFont(new Font("Segoe UI", Font.BOLD, 13));

        tabbedPane.addTab("Tài Khoản & Số Dư Cục Bộ (MySQL)", createAccountsPanel());
        tabbedPane.addTab("Lịch Sử Giao Dịch Cục Bộ", createTransactionsPanel());
        tabbedPane.addTab("Nhật Ký Đồng Bộ RMI Thời Gian Thực", createLogsPanel());

        add(tabbedPane, BorderLayout.CENTER);

        // 3. BOTTOM PANEL: Quick Info
        JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        bottomPanel.setBorder(BorderFactory.createEtchedBorder());
        JLabel lblInfo = new JLabel("Hệ thống Ngân hàng RMI đồng bộ 2 Server | Môn: Lập trình mạng (Lab 5 VKU)");
        lblInfo.setFont(new Font("Segoe UI", Font.ITALIC, 12));
        bottomPanel.add(lblInfo);
        add(bottomPanel, BorderLayout.SOUTH);
    }

    private JPanel createTopConfigPanel(String serverName, String bindIp, int regPort,
                                        int svcPort, String peerIp, int peerRegPort,
                                        String dbUrl, String dbUser, String dbPass) {
        JPanel panel = new JPanel(new BorderLayout(5, 5));
        panel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createEtchedBorder(), "Cấu Hình & Trạng Thái Server",
                TitledBorder.LEFT, TitledBorder.TOP, new Font("Segoe UI", Font.BOLD, 12)));

        JPanel formGrid = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(3, 5, 3, 5);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        // Row 0: Preset
        gbc.gridx = 0; gbc.gridy = 0;
        formGrid.add(new JLabel("Mẫu Cấu Hình:"), gbc);
        gbc.gridx = 1;
        cbServerPreset = new JComboBox<>(new String[]{"-- Tùy chỉnh --", "SERVER-1 (Port 1099)", "SERVER-2 (Port 1100)"});
        cbServerPreset.addActionListener(e -> applyPreset(cbServerPreset.getSelectedIndex()));
        formGrid.add(cbServerPreset, gbc);

        gbc.gridx = 2;
        formGrid.add(new JLabel("Tên Server:"), gbc);
        gbc.gridx = 3;
        txtServerName = new JTextField(serverName, 10);
        formGrid.add(txtServerName, gbc);

        // Row 1: Bind IP & Registry / Service Port
        gbc.gridx = 0; gbc.gridy = 1;
        formGrid.add(new JLabel("IP Máy Này:"), gbc);
        gbc.gridx = 1;
        txtBindIp = new JTextField(bindIp, 12);
        formGrid.add(txtBindIp, gbc);

        gbc.gridx = 2;
        formGrid.add(new JLabel("Port Registry:"), gbc);
        gbc.gridx = 3;
        txtRegistryPort = new JTextField(String.valueOf(regPort), 6);
        formGrid.add(txtRegistryPort, gbc);

        gbc.gridx = 4;
        formGrid.add(new JLabel("Port Service:"), gbc);
        gbc.gridx = 5;
        txtServicePort = new JTextField(String.valueOf(svcPort), 6);
        formGrid.add(txtServicePort, gbc);

        // Row 2: Peer IP & Peer Registry Port
        gbc.gridx = 0; gbc.gridy = 2;
        formGrid.add(new JLabel("IP Server Đối Tác:"), gbc);
        gbc.gridx = 1;
        txtPeerIp = new JTextField(peerIp, 12);
        formGrid.add(txtPeerIp, gbc);

        gbc.gridx = 2;
        formGrid.add(new JLabel("Port Đối Tác:"), gbc);
        gbc.gridx = 3;
        txtPeerRegistryPort = new JTextField(String.valueOf(peerRegPort), 6);
        formGrid.add(txtPeerRegistryPort, gbc);

        // Row 3: MySQL Config
        gbc.gridx = 0; gbc.gridy = 3;
        formGrid.add(new JLabel("MySQL DB URL:"), gbc);
        gbc.gridx = 1; gbc.gridwidth = 3;
        txtDbUrl = new JTextField(dbUrl, 30);
        formGrid.add(txtDbUrl, gbc);
        gbc.gridwidth = 1;

        gbc.gridx = 4;
        formGrid.add(new JLabel("DB User/Pass:"), gbc);
        gbc.gridx = 5;
        JPanel userPassPnl = new JPanel(new GridLayout(1, 2, 2, 0));
        txtDbUser = new JTextField(dbUser, 6);
        txtDbPassword = new JPasswordField(dbPass, 6);
        userPassPnl.add(txtDbUser);
        userPassPnl.add(txtDbPassword);
        formGrid.add(userPassPnl, gbc);

        panel.add(formGrid, BorderLayout.CENTER);

        // Action Buttons & Status Badge
        JPanel actionPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 5));
        lblServerStatus = new JLabel("TRẠNG THÁI: ĐANG DỪNG");
        lblServerStatus.setFont(new Font("Segoe UI", Font.BOLD, 13));
        lblServerStatus.setForeground(Color.RED);

        btnStart = new JButton("KHỞI ĐỘNG SERVER");
        btnStart.setFont(new Font("Segoe UI", Font.BOLD, 12));
        btnStart.setBackground(new Color(0, 153, 76));
        btnStart.setForeground(Color.WHITE);
        btnStart.addActionListener(e -> startServer());

        btnStop = new JButton("DỪNG SERVER");
        btnStop.setFont(new Font("Segoe UI", Font.BOLD, 12));
        btnStop.setBackground(new Color(204, 0, 0));
        btnStop.setForeground(Color.WHITE);
        btnStop.setEnabled(false);
        btnStop.addActionListener(e -> stopServer());

        btnSyncPeer = new JButton("ĐỒNG BỘ PEER NGAY");
        btnSyncPeer.setFont(new Font("Segoe UI", Font.BOLD, 12));
        btnSyncPeer.setBackground(new Color(102, 0, 153));
        btnSyncPeer.setForeground(Color.WHITE);
        btnSyncPeer.setEnabled(false);
        btnSyncPeer.addActionListener(e -> triggerManualSync());

        btnRefresh = new JButton("LÀM MỚI BẢNG");
        btnRefresh.setEnabled(false);
        btnRefresh.addActionListener(e -> refreshAllTables());

        actionPanel.add(lblServerStatus);
        actionPanel.add(btnStart);
        actionPanel.add(btnStop);
        actionPanel.add(btnSyncPeer);
        actionPanel.add(btnRefresh);

        panel.add(actionPanel, BorderLayout.SOUTH);
        return panel;
    }

    private void applyPreset(int index) {
        if (index == 1) { // SERVER 1
            txtServerName.setText("SERVER-1");
            txtRegistryPort.setText("1099");
            txtServicePort.setText("10991");
            txtPeerRegistryPort.setText("1100");
        } else if (index == 2) { // SERVER 2
            txtServerName.setText("SERVER-2");
            txtRegistryPort.setText("1100");
            txtServicePort.setText("11001");
            txtPeerRegistryPort.setText("1099");
        }
    }

    private JPanel createAccountsPanel() {
        JPanel panel = new JPanel(new BorderLayout(5, 5));
        panel.setBorder(new EmptyBorder(8, 8, 8, 8));

        String[] cols = {"Số Tài Khoản", "Chủ Tài Khoản", "Số Dư (VNĐ)", "Cập Nhật Lúc", "Server Cập Nhật Cuối"};
        accountTableModel = new DefaultTableModel(cols, 0) {
            @Override public boolean isCellEditable(int row, int col) { return false; }
        };
        tblAccounts = new JTable(accountTableModel);
        tblAccounts.setRowHeight(24);
        tblAccounts.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 12));

        DefaultTableCellRenderer center = new DefaultTableCellRenderer();
        center.setHorizontalAlignment(JLabel.CENTER);
        tblAccounts.getColumnModel().getColumn(0).setCellRenderer(center);
        tblAccounts.getColumnModel().getColumn(3).setCellRenderer(center);
        tblAccounts.getColumnModel().getColumn(4).setCellRenderer(center);

        panel.add(new JScrollPane(tblAccounts), BorderLayout.CENTER);

        JPanel bottomBar = new JPanel(new BorderLayout());
        lblTotalBalance = new JLabel("Tổng số dư hệ thống: 0 ₫");
        lblTotalBalance.setFont(new Font("Segoe UI", Font.BOLD, 14));
        lblTotalBalance.setForeground(new Color(0, 102, 204));
        bottomBar.add(lblTotalBalance, BorderLayout.WEST);

        panel.add(bottomBar, BorderLayout.SOUTH);
        return panel;
    }

    private JPanel createTransactionsPanel() {
        JPanel panel = new JPanel(new BorderLayout(5, 5));
        panel.setBorder(new EmptyBorder(8, 8, 8, 8));

        String[] cols = {"Mã Giao Dịch", "Số Tài Khoản", "Loại Giao Dịch", "Số Tiền", "TK Đối Ứng", "Nội Dung", "Thời Gian", "Server Khởi Tạo"};
        txTableModel = new DefaultTableModel(cols, 0) {
            @Override public boolean isCellEditable(int row, int col) { return false; }
        };
        tblTransactions = new JTable(txTableModel);
        tblTransactions.setRowHeight(22);
        tblTransactions.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 12));

        DefaultTableCellRenderer center = new DefaultTableCellRenderer();
        center.setHorizontalAlignment(JLabel.CENTER);
        tblTransactions.getColumnModel().getColumn(0).setCellRenderer(center);
        tblTransactions.getColumnModel().getColumn(1).setCellRenderer(center);
        tblTransactions.getColumnModel().getColumn(2).setCellRenderer(center);
        tblTransactions.getColumnModel().getColumn(6).setCellRenderer(center);
        tblTransactions.getColumnModel().getColumn(7).setCellRenderer(center);

        panel.add(new JScrollPane(tblTransactions), BorderLayout.CENTER);
        return panel;
    }

    private JPanel createLogsPanel() {
        JPanel panel = new JPanel(new BorderLayout(5, 5));
        panel.setBorder(new EmptyBorder(8, 8, 8, 8));

        txtLogs = new JTextArea();
        txtLogs.setEditable(false);
        txtLogs.setFont(new Font("Consolas", Font.PLAIN, 12));
        txtLogs.setBackground(new Color(248, 248, 248));
        panel.add(new JScrollPane(txtLogs), BorderLayout.CENTER);

        JPanel btnBar = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton btnClear = new JButton("Xóa Màn Hình Log");
        btnClear.addActionListener(e -> txtLogs.setText(""));
        btnBar.add(btnClear);
        panel.add(btnBar, BorderLayout.SOUTH);

        return panel;
    }

    public synchronized void startServer() {
        if (isRunning) return;
        try {
            String serverName = txtServerName.getText().trim();
            String bindIp = txtBindIp.getText().trim();
            int regPort = Integer.parseInt(txtRegistryPort.getText().trim());
            int svcPort = Integer.parseInt(txtServicePort.getText().trim());
            String peerIp = txtPeerIp.getText().trim();
            int peerRegPort = Integer.parseInt(txtPeerRegistryPort.getText().trim());

            String dbUrl = txtDbUrl.getText().trim();
            String dbUser = txtDbUser.getText().trim();
            String dbPass = new String(txtDbPassword.getPassword()).trim();

            System.setProperty("java.rmi.server.hostname", bindIp);
            Class.forName("com.mysql.cj.jdbc.Driver");

            store = new BankStore(dbUrl, dbUser, dbPass);
            store.initialize();

            service = new BankServiceImpl(serverName, store, peerIp, peerRegPort, svcPort);
            service.setListener(this);

            registry = LocateRegistry.createRegistry(regPort);
            registry.rebind(BankRemote.SERVICE_NAME, service);

            isRunning = true;
            setTitle("BẢNG ĐIỀU KHIỂN RMI BANK SERVER [" + serverName + "] - SẴN SÀNG");
            lblServerStatus.setText("ĐANG CHẠY: " + serverName + " (" + bindIp + ":" + regPort + ")");
            lblServerStatus.setForeground(new Color(0, 153, 76));

            btnStart.setEnabled(false);
            btnStop.setEnabled(true);
            btnSyncPeer.setEnabled(true);
            btnRefresh.setEnabled(true);
            setInputsEnabled(false);

            onLog(serverName, "START", "Server đã khởi động tại " + bindIp + ":" + regPort + " | Peer: " + peerIp + ":" + peerRegPort);
            refreshAllTables();
            loadInitialLogs();

            JOptionPane.showMessageDialog(this, "Khởi động " + serverName + " thành công trên port " + regPort + "!", "Thông báo", JOptionPane.INFORMATION_MESSAGE);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Không thể khởi động Server: " + ex.getMessage(), "Lỗi Khởi Động", JOptionPane.ERROR_MESSAGE);
            onLog("SYSTEM", "ERROR", "Khởi động thất bại: " + ex.getMessage());
        }
    }

    public synchronized void stopServer() {
        if (!isRunning) return;
        try {
            if (service != null) {
                service.stopBackgroundTasks();
                try { UnicastRemoteObject.unexportObject(service, true); } catch (Exception ignored) {}
            }
            if (registry != null) {
                try { UnicastRemoteObject.unexportObject(registry, true); } catch (Exception ignored) {}
            }
            isRunning = false;
            lblServerStatus.setText("TRẠNG THÁI: ĐÃ DỪNG");
            lblServerStatus.setForeground(Color.RED);

            btnStart.setEnabled(true);
            btnStop.setEnabled(false);
            btnSyncPeer.setEnabled(false);
            btnRefresh.setEnabled(false);
            setInputsEnabled(true);

            onLog(txtServerName.getText().trim(), "STOP", "Server đã dừng hoạt động.");
        } catch (Exception ex) {
            onLog("SYSTEM", "ERROR", "Lỗi khi dừng server: " + ex.getMessage());
        }
    }

    private void setInputsEnabled(boolean enabled) {
        cbServerPreset.setEnabled(enabled);
        txtServerName.setEnabled(enabled);
        txtBindIp.setEnabled(enabled);
        txtRegistryPort.setEnabled(enabled);
        txtServicePort.setEnabled(enabled);
        txtPeerIp.setEnabled(enabled);
        txtPeerRegistryPort.setEnabled(enabled);
        txtDbUrl.setEnabled(enabled);
        txtDbUser.setEnabled(enabled);
        txtDbPassword.setEnabled(enabled);
    }

    private void triggerManualSync() {
        if (!isRunning || service == null) return;
        new Thread(() -> {
            try {
                String msg = service.synchronizeNow();
                SwingUtilities.invokeLater(() -> {
                    refreshAllTables();
                    JOptionPane.showMessageDialog(this, msg, "Kết Quả Đồng Bộ", JOptionPane.INFORMATION_MESSAGE);
                });
            } catch (Exception e) {
                SwingUtilities.invokeLater(() -> {
                    JOptionPane.showMessageDialog(this, "Lỗi khi đồng bộ đối tác: " + e.getMessage(), "Lỗi Đồng Bộ", JOptionPane.ERROR_MESSAGE);
                });
            }
        }).start();
    }

    private void refreshAllTables() {
        if (store == null) return;
        try {
            // 1. Refresh Accounts Table
            List<Account> accounts = store.listAccounts(false);
            accountTableModel.setRowCount(0);
            double total = 0;
            NumberFormat nf = NumberFormat.getCurrencyInstance(new Locale("vi", "VN"));
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

            for (Account acc : accounts) {
                total += acc.getBalance();
                accountTableModel.addRow(new Object[]{
                        acc.getAccountNumber(),
                        acc.getHolderName(),
                        nf.format(acc.getBalance()),
                        sdf.format(new Date(acc.getUpdatedAt())),
                        acc.getUpdatedBy()
                });
            }
            lblTotalBalance.setText("Tổng số dư trong CSDL: " + nf.format(total));

            // 2. Refresh Transactions Table
            List<Transaction> txs = store.listAllTransactions();
            txTableModel.setRowCount(0);
            for (Transaction tx : txs) {
                txTableModel.addRow(new Object[]{
                        tx.getTxId(),
                        tx.getAccountNumber(),
                        tx.getTxType(),
                        tx.getFormattedAmount(),
                        tx.getTargetAccount() != null ? tx.getTargetAccount() : "---",
                        tx.getDescription(),
                        tx.getFormattedDate(),
                        tx.getCreatedBy()
                });
            }
        } catch (Exception ex) {
            System.err.println("Lỗi refresh tables: " + ex.getMessage());
        }
    }

    private void loadInitialLogs() {
        if (store == null) return;
        try {
            List<String> list = store.getLogs();
            StringBuilder sb = new StringBuilder();
            for (String s : list) {
                sb.append(s).append("\n");
            }
            txtLogs.setText(sb.toString());
        } catch (Exception ignored) {
        }
    }

    @Override
    public void onLog(String serverName, String action, String message) {
        SwingUtilities.invokeLater(() -> {
            String time = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
            txtLogs.append("[" + time + "] [" + serverName + "] [" + action + "] " + message + "\n");
            txtLogs.setCaretPosition(txtLogs.getDocument().getLength());
        });
    }

    @Override
    public void onDataChanged() {
        SwingUtilities.invokeLater(this::refreshAllTables);
    }
}
