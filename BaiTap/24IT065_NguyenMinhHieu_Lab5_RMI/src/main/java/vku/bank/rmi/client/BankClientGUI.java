package vku.bank.rmi.client;

import vku.bank.rmi.model.Account;
import vku.bank.rmi.model.Transaction;
import vku.bank.rmi.remote.BankRemote;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;

public class BankClientGUI extends JFrame {
    private static final long serialVersionUID = 1L;

    // Connection fields
    private JTextField txtServer1Ip;
    private JTextField txtServer1Port;
    private JTextField txtServer2Ip;
    private JTextField txtServer2Port;
    private JRadioButton rbServer1;
    private JRadioButton rbServer2;
    private JCheckBox chkAutoFailover;
    private JButton btnConnect;
    private JLabel lblStatus;

    // Login fields
    private JComboBox<String> cbQuickAccounts;
    private JTextField txtAccountNum;
    private JPasswordField txtPin;
    private JButton btnLogin;
    private JButton btnLogout;

    // Dashboard fields
    private JLabel lblCurrentAcc;
    private JLabel lblHolderName;
    private JLabel lblBalance;
    private JButton btnRefresh;

    // Deposit fields
    private JTextField txtDepositAmount;
    private JTextField txtDepositNote;
    private JButton btnDeposit;

    // Withdraw fields
    private JTextField txtWithdrawAmount;
    private JTextField txtWithdrawNote;
    private JButton btnWithdraw;

    // Transfer fields
    private JTextField txtTransferTo;
    private JTextField txtTransferAmount;
    private JTextField txtTransferNote;
    private JButton btnTransfer;

    // History Table
    private DefaultTableModel historyTableModel;
    private JTable tblHistory;

    // All Accounts Table (for verification)
    private DefaultTableModel allAccountsTableModel;
    private JTable tblAllAccounts;

    // Sync Logs
    private JTextArea txtLogs;
    private JButton btnSyncNow;
    private JButton btnRefreshLogs;

    // RMI References
    private BankRemote currentRemoteService;
    private String activeServerTag = "SERVER-1";
    private Account currentAccount = null;

    public BankClientGUI() {
        setTitle("HỆ THỐNG NGÂN HÀNG PHÂN TÁN RMI - ĐỒNG BỘ 2 SERVER (LAB 5 VKU)");
        setSize(1000, 720);
        setMinimumSize(new Dimension(880, 640));
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        initComponents();
        updateUIState(false);
    }

    private void initComponents() {
        setLayout(new BorderLayout(5, 5));

        // 1. TOP PANEL: Connection and Failover Setup
        JPanel topPanel = createConnectionPanel();
        add(topPanel, BorderLayout.NORTH);

        // 2. CENTER PANEL: Tabbed Pane for Operations
        JTabbedPane tabbedPane = new JTabbedPane();
        tabbedPane.setFont(new Font("Segoe UI", Font.BOLD, 13));

        tabbedPane.addTab("Giao Dịch Khách Hàng", createBankingPanel());
        tabbedPane.addTab("Lịch Sử Giao Dịch", createHistoryPanel());
        tabbedPane.addTab("Đồng Bộ 2 Server & Đối Soát", createSyncMonitorPanel());

        add(tabbedPane, BorderLayout.CENTER);

        // 3. BOTTOM PANEL: Status Bar
        JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        bottomPanel.setBorder(BorderFactory.createEtchedBorder());
        lblStatus = new JLabel("Chưa kết nối đến Server RMI.");
        lblStatus.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        bottomPanel.add(lblStatus);
        add(bottomPanel, BorderLayout.SOUTH);
    }

    private JPanel createConnectionPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createEtchedBorder(), "Cấu hình Kết nối 2 Server RMI",
                TitledBorder.LEFT, TitledBorder.TOP, new Font("Segoe UI", Font.BOLD, 12)));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 6, 4, 6);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        // Server 1
        gbc.gridx = 0; gbc.gridy = 0;
        rbServer1 = new JRadioButton("Server 1 (Chính):", true);
        rbServer1.setFont(new Font("Segoe UI", Font.BOLD, 12));
        panel.add(rbServer1, gbc);

        gbc.gridx = 1;
        txtServer1Ip = new JTextField("127.0.0.1", 10);
        panel.add(txtServer1Ip, gbc);

        gbc.gridx = 2;
        panel.add(new JLabel("Port:"), gbc);

        gbc.gridx = 3;
        txtServer1Port = new JTextField("1099", 5);
        panel.add(txtServer1Port, gbc);

        // Server 2
        gbc.gridx = 0; gbc.gridy = 1;
        rbServer2 = new JRadioButton("Server 2 (Phụ):");
        rbServer2.setFont(new Font("Segoe UI", Font.BOLD, 12));
        panel.add(rbServer2, gbc);

        ButtonGroup group = new ButtonGroup();
        group.add(rbServer1);
        group.add(rbServer2);

        gbc.gridx = 1;
        txtServer2Ip = new JTextField("127.0.0.1", 10);
        panel.add(txtServer2Ip, gbc);

        gbc.gridx = 2;
        panel.add(new JLabel("Port:"), gbc);

        gbc.gridx = 3;
        txtServer2Port = new JTextField("1100", 5);
        panel.add(txtServer2Port, gbc);

        // Auto failover & Connect Button
        gbc.gridx = 4; gbc.gridy = 0;
        chkAutoFailover = new JCheckBox("Tự động Failover khi Server chết", true);
        chkAutoFailover.setForeground(new Color(0, 102, 0));
        panel.add(chkAutoFailover, gbc);

        gbc.gridx = 4; gbc.gridy = 1;
        btnConnect = new JButton("KẾT NỐI SERVER");
        btnConnect.setFont(new Font("Segoe UI", Font.BOLD, 12));
        btnConnect.setBackground(new Color(0, 122, 204));
        btnConnect.setForeground(Color.WHITE);
        btnConnect.setFocusPainted(false);
        btnConnect.addActionListener(e -> connectToServer());
        panel.add(btnConnect, gbc);

        return panel;
    }

    private JPanel createBankingPanel() {
        JPanel main = new JPanel(new BorderLayout(10, 10));
        main.setBorder(new EmptyBorder(10, 10, 10, 10));

        // Left side: Login and Account Info
        JPanel leftPanel = new JPanel();
        leftPanel.setLayout(new BoxLayout(leftPanel, BoxLayout.Y_AXIS));
        leftPanel.setPreferredSize(new Dimension(320, 0));

        // Login Card
        JPanel loginCard = new JPanel(new GridBagLayout());
        loginCard.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createEtchedBorder(), "Đăng Nhập Tài Khoản",
                TitledBorder.LEFT, TitledBorder.TOP, new Font("Segoe UI", Font.BOLD, 12)));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 4, 4, 4);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        gbc.gridx = 0; gbc.gridy = 0; gbc.gridwidth = 2;
        cbQuickAccounts = new JComboBox<>(new String[]{
                "-- Chọn tài khoản mẫu để test nhanh --",
                "1001 - Nguyen Van An (PIN: 1234)",
                "1002 - Tran Thi Binh (PIN: 1234)",
                "1003 - Le Van Cuong (PIN: 1234)",
                "1004 - Pham Minh Duc (PIN: 1234)"
        });
        cbQuickAccounts.addActionListener(e -> {
            int idx = cbQuickAccounts.getSelectedIndex();
            if (idx == 1) { txtAccountNum.setText("1001"); txtPin.setText("1234"); }
            else if (idx == 2) { txtAccountNum.setText("1002"); txtPin.setText("1234"); }
            else if (idx == 3) { txtAccountNum.setText("1003"); txtPin.setText("1234"); }
            else if (idx == 4) { txtAccountNum.setText("1004"); txtPin.setText("1234"); }
        });
        loginCard.add(cbQuickAccounts, gbc);

        gbc.gridwidth = 1;
        gbc.gridx = 0; gbc.gridy = 1;
        loginCard.add(new JLabel("Số Tài Khoản:"), gbc);
        gbc.gridx = 1;
        txtAccountNum = new JTextField("1001", 10);
        loginCard.add(txtAccountNum, gbc);

        gbc.gridx = 0; gbc.gridy = 2;
        loginCard.add(new JLabel("Mã PIN:"), gbc);
        gbc.gridx = 1;
        txtPin = new JPasswordField("1234", 10);
        loginCard.add(txtPin, gbc);

        gbc.gridx = 0; gbc.gridy = 3; gbc.gridwidth = 2;
        JPanel btnLoginPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        btnLogin = new JButton("Đăng Nhập");
        btnLogin.setBackground(new Color(34, 139, 34));
        btnLogin.setForeground(Color.WHITE);
        btnLogin.addActionListener(e -> loginAccount());

        btnLogout = new JButton("Đăng Xuất");
        btnLogout.setBackground(new Color(178, 34, 34));
        btnLogout.setForeground(Color.WHITE);
        btnLogout.setEnabled(false);
        btnLogout.addActionListener(e -> logoutAccount());

        btnLoginPanel.add(btnLogin);
        btnLoginPanel.add(btnLogout);
        loginCard.add(btnLoginPanel, gbc);

        leftPanel.add(loginCard);
        leftPanel.add(Box.createVerticalStrut(10));

        // Account Information Dashboard Card
        JPanel accCard = new JPanel(new GridLayout(4, 1, 4, 4));
        accCard.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createEtchedBorder(), "Thông Tin Tài Khoản Hiện Tại",
                TitledBorder.LEFT, TitledBorder.TOP, new Font("Segoe UI", Font.BOLD, 12)));

        lblCurrentAcc = new JLabel("Số TK: ---");
        lblCurrentAcc.setFont(new Font("Segoe UI", Font.BOLD, 13));
        lblHolderName = new JLabel("Chủ TK: ---");
        lblHolderName.setFont(new Font("Segoe UI", Font.BOLD, 13));

        lblBalance = new JLabel("0 ₫");
        lblBalance.setFont(new Font("Segoe UI", Font.BOLD, 22));
        lblBalance.setForeground(new Color(0, 102, 204));

        btnRefresh = new JButton("Làm Mới Số Dư");
        btnRefresh.addActionListener(e -> refreshCurrentAccount());

        accCard.add(lblCurrentAcc);
        accCard.add(lblHolderName);
        accCard.add(lblBalance);
        accCard.add(btnRefresh);

        leftPanel.add(accCard);
        main.add(leftPanel, BorderLayout.WEST);

        // Right side: Transaction Tabs (Deposit, Withdraw, Transfer)
        JTabbedPane actionTabs = new JTabbedPane();
        actionTabs.setFont(new Font("Segoe UI", Font.BOLD, 12));

        // Tab Nạp tiền
        JPanel pnlDeposit = new JPanel(new GridBagLayout());
        pnlDeposit.setBorder(new EmptyBorder(20, 20, 20, 20));
        GridBagConstraints dGbc = new GridBagConstraints();
        dGbc.insets = new Insets(8, 8, 8, 8);
        dGbc.fill = GridBagConstraints.HORIZONTAL;

        dGbc.gridx = 0; dGbc.gridy = 0;
        pnlDeposit.add(new JLabel("Số tiền cần nạp (VNĐ):"), dGbc);
        dGbc.gridx = 1;
        txtDepositAmount = new JTextField(15);
        pnlDeposit.add(txtDepositAmount, dGbc);

        dGbc.gridx = 0; dGbc.gridy = 1;
        pnlDeposit.add(new JLabel("Nội dung nạp:"), dGbc);
        dGbc.gridx = 1;
        txtDepositNote = new JTextField("Nạp tiền vào tài khoản", 15);
        pnlDeposit.add(txtDepositNote, dGbc);

        dGbc.gridx = 1; dGbc.gridy = 2;
        btnDeposit = new JButton("XÁC NHẬN NẠP TIỀN");
        btnDeposit.setFont(new Font("Segoe UI", Font.BOLD, 13));
        btnDeposit.setBackground(new Color(0, 153, 76));
        btnDeposit.setForeground(Color.WHITE);
        btnDeposit.addActionListener(e -> handleDeposit());
        pnlDeposit.add(btnDeposit, dGbc);

        actionTabs.addTab("Nạp Tiền (Deposit)", pnlDeposit);

        // Tab Rút tiền
        JPanel pnlWithdraw = new JPanel(new GridBagLayout());
        pnlWithdraw.setBorder(new EmptyBorder(20, 20, 20, 20));
        GridBagConstraints wGbc = new GridBagConstraints();
        wGbc.insets = new Insets(8, 8, 8, 8);
        wGbc.fill = GridBagConstraints.HORIZONTAL;

        wGbc.gridx = 0; wGbc.gridy = 0;
        pnlWithdraw.add(new JLabel("Số tiền cần rút (VNĐ):"), wGbc);
        wGbc.gridx = 1;
        txtWithdrawAmount = new JTextField(15);
        pnlWithdraw.add(txtWithdrawAmount, wGbc);

        wGbc.gridx = 0; wGbc.gridy = 1;
        pnlWithdraw.add(new JLabel("Nội dung rút:"), wGbc);
        wGbc.gridx = 1;
        txtWithdrawNote = new JTextField("Rút tiền mặt ATM", 15);
        pnlWithdraw.add(txtWithdrawNote, wGbc);

        wGbc.gridx = 1; wGbc.gridy = 2;
        btnWithdraw = new JButton("XÁC NHẬN RÚT TIỀN");
        btnWithdraw.setFont(new Font("Segoe UI", Font.BOLD, 13));
        btnWithdraw.setBackground(new Color(204, 102, 0));
        btnWithdraw.setForeground(Color.WHITE);
        btnWithdraw.addActionListener(e -> handleWithdraw());
        pnlWithdraw.add(btnWithdraw, wGbc);

        actionTabs.addTab("Rút Tiền (Withdraw)", pnlWithdraw);

        // Tab Chuyển khoản
        JPanel pnlTransfer = new JPanel(new GridBagLayout());
        pnlTransfer.setBorder(new EmptyBorder(20, 20, 20, 20));
        GridBagConstraints tGbc = new GridBagConstraints();
        tGbc.insets = new Insets(8, 8, 8, 8);
        tGbc.fill = GridBagConstraints.HORIZONTAL;

        tGbc.gridx = 0; tGbc.gridy = 0;
        pnlTransfer.add(new JLabel("TK người nhận:"), tGbc);
        tGbc.gridx = 1;
        txtTransferTo = new JTextField("1002", 15);
        pnlTransfer.add(txtTransferTo, tGbc);

        tGbc.gridx = 0; tGbc.gridy = 1;
        pnlTransfer.add(new JLabel("Số tiền chuyển (VNĐ):"), tGbc);
        tGbc.gridx = 1;
        txtTransferAmount = new JTextField(15);
        pnlTransfer.add(txtTransferAmount, tGbc);

        tGbc.gridx = 0; tGbc.gridy = 2;
        pnlTransfer.add(new JLabel("Nội dung chuyển:"), tGbc);
        tGbc.gridx = 1;
        txtTransferNote = new JTextField("Chuyển tiền thanh toán", 15);
        pnlTransfer.add(txtTransferNote, tGbc);

        tGbc.gridx = 1; tGbc.gridy = 3;
        btnTransfer = new JButton("XÁC NHẬN CHUYỂN KHOẢN");
        btnTransfer.setFont(new Font("Segoe UI", Font.BOLD, 13));
        btnTransfer.setBackground(new Color(0, 102, 204));
        btnTransfer.setForeground(Color.WHITE);
        btnTransfer.addActionListener(e -> handleTransfer());
        pnlTransfer.add(btnTransfer, tGbc);

        actionTabs.addTab("Chuyển Khoản (Transfer)", pnlTransfer);

        main.add(actionTabs, BorderLayout.CENTER);
        return main;
    }

    private JPanel createHistoryPanel() {
        JPanel panel = new JPanel(new BorderLayout(5, 5));
        panel.setBorder(new EmptyBorder(10, 10, 10, 10));

        String[] cols = {"Mã GD", "Loại GD", "Số Tiền", "TK Liên Quan", "Nội Dung", "Thời Gian", "Server Thực Hiện"};
        historyTableModel = new DefaultTableModel(cols, 0) {
            @Override public boolean isCellEditable(int row, int col) { return false; }
        };
        tblHistory = new JTable(historyTableModel);
        tblHistory.setRowHeight(24);
        tblHistory.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 12));

        DefaultTableCellRenderer centerRenderer = new DefaultTableCellRenderer();
        centerRenderer.setHorizontalAlignment(JLabel.CENTER);
        tblHistory.getColumnModel().getColumn(0).setCellRenderer(centerRenderer);
        tblHistory.getColumnModel().getColumn(1).setCellRenderer(centerRenderer);
        tblHistory.getColumnModel().getColumn(5).setCellRenderer(centerRenderer);
        tblHistory.getColumnModel().getColumn(6).setCellRenderer(centerRenderer);

        panel.add(new JScrollPane(tblHistory), BorderLayout.CENTER);

        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton btnLoadHistory = new JButton("Làm Mới Lịch Sử Giao Dịch");
        btnLoadHistory.addActionListener(e -> loadTransactionHistory());
        btnPanel.add(btnLoadHistory);

        panel.add(btnPanel, BorderLayout.SOUTH);
        return panel;
    }

    private JPanel createSyncMonitorPanel() {
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        panel.setBorder(new EmptyBorder(10, 10, 10, 10));

        // Top: All accounts in system to verify consistency between Server 1 and Server 2
        JPanel topAccountsPanel = new JPanel(new BorderLayout());
        topAccountsPanel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createEtchedBorder(), "Đối Soát Dữ Liệu Toàn Hệ Thống (Kiểm tra số dư trên Server đang kết nối)",
                TitledBorder.LEFT, TitledBorder.TOP, new Font("Segoe UI", Font.BOLD, 12)));

        String[] cols = {"Số TK", "Chủ Tài Khoản", "Số Dư Hiện Tại", "Cập Nhật Lúc", "Server Cập Nhật Cuối"};
        allAccountsTableModel = new DefaultTableModel(cols, 0) {
            @Override public boolean isCellEditable(int row, int col) { return false; }
        };
        tblAllAccounts = new JTable(allAccountsTableModel);
        tblAllAccounts.setRowHeight(22);
        tblAllAccounts.setPreferredScrollableViewportSize(new Dimension(800, 120));
        topAccountsPanel.add(new JScrollPane(tblAllAccounts), BorderLayout.CENTER);

        panel.add(topAccountsPanel, BorderLayout.NORTH);

        // Center: Real-time Sync Logs
        JPanel logsPanel = new JPanel(new BorderLayout(5, 5));
        logsPanel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createEtchedBorder(), "Nhật Ký Đồng Bộ Giữa 2 Server (RMI Replication Logs)",
                TitledBorder.LEFT, TitledBorder.TOP, new Font("Segoe UI", Font.BOLD, 12)));

        txtLogs = new JTextArea();
        txtLogs.setEditable(false);
        txtLogs.setFont(new Font("Consolas", Font.PLAIN, 12));
        txtLogs.setBackground(new Color(245, 245, 245));
        logsPanel.add(new JScrollPane(txtLogs), BorderLayout.CENTER);

        JPanel logActions = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        btnSyncNow = new JButton("ĐỒNG BỘ 2 CHIỀU NGAY (Sync Now)");
        btnSyncNow.setBackground(new Color(153, 0, 153));
        btnSyncNow.setForeground(Color.WHITE);
        btnSyncNow.setFont(new Font("Segoe UI", Font.BOLD, 12));
        btnSyncNow.addActionListener(e -> handleSyncNow());

        btnRefreshLogs = new JButton("Tải Lại Nhật Ký & Dữ Liệu");
        btnRefreshLogs.addActionListener(e -> {
            loadAllAccounts();
            loadSyncLogs();
        });

        logActions.add(btnSyncNow);
        logActions.add(btnRefreshLogs);
        logsPanel.add(logActions, BorderLayout.SOUTH);

        panel.add(logsPanel, BorderLayout.CENTER);

        return panel;
    }

    private void updateUIState(boolean connected) {
        btnLogin.setEnabled(connected && currentAccount == null);
        btnLogout.setEnabled(connected && currentAccount != null);
        btnRefresh.setEnabled(connected && currentAccount != null);
        btnDeposit.setEnabled(connected && currentAccount != null);
        btnWithdraw.setEnabled(connected && currentAccount != null);
        btnTransfer.setEnabled(connected && currentAccount != null);
        btnSyncNow.setEnabled(connected);
        btnRefreshLogs.setEnabled(connected);

        if (!connected) {
            lblBalance.setText("0 ₫");
            lblCurrentAcc.setText("Số TK: ---");
            lblHolderName.setText("Chủ TK: ---");
        }
    }

    private void connectToServer() {
        String ip1 = txtServer1Ip.getText().trim();
        int port1 = Integer.parseInt(txtServer1Port.getText().trim());
        String ip2 = txtServer2Ip.getText().trim();
        int port2 = Integer.parseInt(txtServer2Port.getText().trim());

        String targetTag = rbServer1.isSelected() ? "SERVER-1" : "SERVER-2";
        String targetIp = rbServer1.isSelected() ? ip1 : ip2;
        int targetPort = rbServer1.isSelected() ? port1 : port2;

        try {
            Registry registry = LocateRegistry.getRegistry(targetIp, targetPort);
            BankRemote service = (BankRemote) registry.lookup(BankRemote.SERVICE_NAME);
            if ("OK".equals(service.ping())) {
                currentRemoteService = service;
                activeServerTag = targetTag;
                lblStatus.setText("ĐÃ KẾT NỐI TỚI " + targetTag + " (" + targetIp + ":" + targetPort + ") | Tên Server: " + service.getServerName());
                lblStatus.setForeground(new Color(0, 128, 0));
                updateUIState(true);
                loadAllAccounts();
                loadSyncLogs();
                JOptionPane.showMessageDialog(this, "Kết nối thành công tới " + targetTag + "!", "Thông báo", JOptionPane.INFORMATION_MESSAGE);
            }
        } catch (Exception ex) {
            lblStatus.setText("Lỗi kết nối tới " + targetTag + " (" + targetIp + ":" + targetPort + "): " + ex.getMessage());
            lblStatus.setForeground(Color.RED);
            currentRemoteService = null;
            updateUIState(false);

            if (chkAutoFailover.isSelected()) {
                String backupTag = rbServer1.isSelected() ? "SERVER-2" : "SERVER-1";
                String backupIp = rbServer1.isSelected() ? ip2 : ip1;
                int backupPort = rbServer1.isSelected() ? port2 : port1;

                int choice = JOptionPane.showConfirmDialog(this,
                        "Không thể kết nối đến " + targetTag + ".\nBạn có muốn tự động chuyển sang " + backupTag + " (" + backupIp + ":" + backupPort + ") không?",
                        "Chuyển đổi dự phòng (Failover)", JOptionPane.YES_NO_OPTION);

                if (choice == JOptionPane.YES_OPTION) {
                    if (rbServer1.isSelected()) rbServer2.setSelected(true);
                    else rbServer1.setSelected(true);
                    connectToServer();
                }
            } else {
                JOptionPane.showMessageDialog(this, "Không thể kết nối tới Server: " + ex.getMessage(), "Lỗi Kết Nối", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private <T> T executeWithFailover(RemoteCallable<T> callable) throws Exception {
        try {
            if (currentRemoteService == null) throw new RemoteException("Chưa kết nối đến Server");
            return callable.call(currentRemoteService);
        } catch (RemoteException e) {
            if (chkAutoFailover.isSelected()) {
                // Thử failover sang server còn lại
                boolean wasServer1 = "SERVER-1".equals(activeServerTag);
                String backupTag = wasServer1 ? "SERVER-2" : "SERVER-1";
                String backupIp = wasServer1 ? txtServer2Ip.getText().trim() : txtServer1Ip.getText().trim();
                int backupPort = wasServer1 ? Integer.parseInt(txtServer2Port.getText().trim()) : Integer.parseInt(txtServer1Port.getText().trim());

                try {
                    Registry registry = LocateRegistry.getRegistry(backupIp, backupPort);
                    BankRemote backupService = (BankRemote) registry.lookup(BankRemote.SERVICE_NAME);
                    if ("OK".equals(backupService.ping())) {
                        currentRemoteService = backupService;
                        activeServerTag = backupTag;
                        if (wasServer1) rbServer2.setSelected(true);
                        else rbServer1.setSelected(true);

                        lblStatus.setText("FAILOVER THÀNH CÔNG: Đã chuyển sang " + backupTag + " (" + backupIp + ":" + backupPort + ")");
                        lblStatus.setForeground(new Color(204, 102, 0));
                        JOptionPane.showMessageDialog(this,
                                "Mất kết nối với Server trước đó!\nHệ thống đã tự động kích hoạt Failover chuyển sang " + backupTag + " thành công.",
                                "Tự động Chịu Lỗi (Failover)", JOptionPane.WARNING_MESSAGE);

                        return callable.call(currentRemoteService);
                    }
                } catch (Exception failoverEx) {
                    throw new RemoteException("Cả 2 Server đều không phản hồi: " + failoverEx.getMessage(), failoverEx);
                }
            }
            throw e;
        }
    }

    private void loginAccount() {
        String accNum = txtAccountNum.getText().trim();
        String pin = new String(txtPin.getPassword()).trim();
        if (accNum.isEmpty() || pin.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Vui lòng nhập đầy đủ Số tài khoản và Mã PIN", "Cảnh báo", JOptionPane.WARNING_MESSAGE);
            return;
        }

        try {
            Account acc = executeWithFailover(srv -> srv.login(accNum, pin));
            if (acc != null) {
                currentAccount = acc;
                lblCurrentAcc.setText("Số TK: " + acc.getAccountNumber());
                lblHolderName.setText("Chủ TK: " + acc.getHolderName());
                lblBalance.setText(acc.getFormattedBalance());
                updateUIState(true);
                loadTransactionHistory();
                JOptionPane.showMessageDialog(this, "Đăng nhập thành công!\nXin chào: " + acc.getHolderName(), "Thành công", JOptionPane.INFORMATION_MESSAGE);
            } else {
                JOptionPane.showMessageDialog(this, "Sai số tài khoản hoặc mã PIN!", "Đăng nhập thất bại", JOptionPane.ERROR_MESSAGE);
            }
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Lỗi khi đăng nhập: " + ex.getMessage(), "Lỗi", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void logoutAccount() {
        currentAccount = null;
        updateUIState(true);
        historyTableModel.setRowCount(0);
        JOptionPane.showMessageDialog(this, "Đã đăng xuất tài khoản.", "Thông báo", JOptionPane.INFORMATION_MESSAGE);
    }

    private void refreshCurrentAccount() {
        if (currentAccount == null) return;
        try {
            Account acc = executeWithFailover(srv -> srv.getAccount(currentAccount.getAccountNumber()));
            if (acc != null) {
                currentAccount = acc;
                lblBalance.setText(acc.getFormattedBalance());
                loadTransactionHistory();
                loadAllAccounts();
            }
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Lỗi làm mới số dư: " + ex.getMessage(), "Lỗi", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void handleDeposit() {
        if (currentAccount == null) return;
        try {
            double amount = Double.parseDouble(txtDepositAmount.getText().trim());
            String note = txtDepositNote.getText().trim();
            Account updated = executeWithFailover(srv -> srv.deposit(currentAccount.getAccountNumber(), amount, note));
            currentAccount = updated;
            lblBalance.setText(updated.getFormattedBalance());
            txtDepositAmount.setText("");
            loadTransactionHistory();
            loadAllAccounts();
            loadSyncLogs();
            JOptionPane.showMessageDialog(this, "Nạp tiền thành công! Số dư mới: " + updated.getFormattedBalance(), "Thành công", JOptionPane.INFORMATION_MESSAGE);
        } catch (NumberFormatException e) {
            JOptionPane.showMessageDialog(this, "Số tiền không hợp lệ!", "Lỗi nhập liệu", JOptionPane.ERROR_MESSAGE);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Lỗi nạp tiền: " + ex.getMessage(), "Lỗi Giao Dịch", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void handleWithdraw() {
        if (currentAccount == null) return;
        try {
            double amount = Double.parseDouble(txtWithdrawAmount.getText().trim());
            String note = txtWithdrawNote.getText().trim();
            Account updated = executeWithFailover(srv -> srv.withdraw(currentAccount.getAccountNumber(), amount, note));
            currentAccount = updated;
            lblBalance.setText(updated.getFormattedBalance());
            txtWithdrawAmount.setText("");
            loadTransactionHistory();
            loadAllAccounts();
            loadSyncLogs();
            JOptionPane.showMessageDialog(this, "Rút tiền thành công! Số dư mới: " + updated.getFormattedBalance(), "Thành công", JOptionPane.INFORMATION_MESSAGE);
        } catch (NumberFormatException e) {
            JOptionPane.showMessageDialog(this, "Số tiền không hợp lệ!", "Lỗi nhập liệu", JOptionPane.ERROR_MESSAGE);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Lỗi rút tiền: " + ex.getMessage(), "Lỗi Giao Dịch", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void handleTransfer() {
        if (currentAccount == null) return;
        try {
            String toAcc = txtTransferTo.getText().trim();
            double amount = Double.parseDouble(txtTransferAmount.getText().trim());
            String note = txtTransferNote.getText().trim();
            Account updated = executeWithFailover(srv -> srv.transfer(currentAccount.getAccountNumber(), toAcc, amount, note));
            currentAccount = updated;
            lblBalance.setText(updated.getFormattedBalance());
            txtTransferAmount.setText("");
            loadTransactionHistory();
            loadAllAccounts();
            loadSyncLogs();
            JOptionPane.showMessageDialog(this, "Chuyển khoản thành công đến TK " + toAcc + "!\nSố dư mới: " + updated.getFormattedBalance(), "Thành công", JOptionPane.INFORMATION_MESSAGE);
        } catch (NumberFormatException e) {
            JOptionPane.showMessageDialog(this, "Số tiền không hợp lệ!", "Lỗi nhập liệu", JOptionPane.ERROR_MESSAGE);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Lỗi chuyển khoản: " + ex.getMessage(), "Lỗi Giao Dịch", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void loadTransactionHistory() {
        if (currentAccount == null) return;
        try {
            List<Transaction> list = executeWithFailover(srv -> srv.getTransactionHistory(currentAccount.getAccountNumber()));
            historyTableModel.setRowCount(0);
            for (Transaction tx : list) {
                historyTableModel.addRow(new Object[]{
                        tx.getTxId(),
                        tx.getTxType(),
                        tx.getFormattedAmount(),
                        tx.getTargetAccount() != null ? tx.getTargetAccount() : "---",
                        tx.getDescription(),
                        tx.getFormattedDate(),
                        tx.getCreatedBy()
                });
            }
        } catch (Exception ex) {
            System.err.println("Lỗi load history: " + ex.getMessage());
        }
    }

    private void loadAllAccounts() {
        try {
            List<Account> list = executeWithFailover(BankRemote::getAllAccounts);
            allAccountsTableModel.setRowCount(0);
            NumberFormat nf = NumberFormat.getCurrencyInstance(new Locale("vi", "VN"));
            for (Account acc : list) {
                allAccountsTableModel.addRow(new Object[]{
                        acc.getAccountNumber(),
                        acc.getHolderName(),
                        nf.format(acc.getBalance()),
                        new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new java.util.Date(acc.getUpdatedAt())),
                        acc.getUpdatedBy()
                });
            }
        } catch (Exception ex) {
            System.err.println("Lỗi load all accounts: " + ex.getMessage());
        }
    }

    private void loadSyncLogs() {
        try {
            List<String> logs = executeWithFailover(BankRemote::getSyncLogs);
            StringBuilder sb = new StringBuilder();
            for (String log : logs) {
                sb.append(log).append("\n");
            }
            txtLogs.setText(sb.toString());
        } catch (Exception ex) {
            System.err.println("Lỗi load logs: " + ex.getMessage());
        }
    }

    private void handleSyncNow() {
        try {
            String result = executeWithFailover(BankRemote::synchronizeNow);
            JOptionPane.showMessageDialog(this, result, "Kết Quả Đồng Bộ", JOptionPane.INFORMATION_MESSAGE);
            loadAllAccounts();
            loadSyncLogs();
            if (currentAccount != null) refreshCurrentAccount();
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Lỗi đồng bộ: " + ex.getMessage(), "Lỗi Đồng Bộ", JOptionPane.ERROR_MESSAGE);
            loadSyncLogs();
        }
    }

    @FunctionalInterface
    private interface RemoteCallable<T> {
        T call(BankRemote service) throws Exception;
    }
}
