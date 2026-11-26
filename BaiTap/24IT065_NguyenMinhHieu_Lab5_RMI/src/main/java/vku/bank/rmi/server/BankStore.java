package vku.bank.rmi.server;

import vku.bank.rmi.model.Account;
import vku.bank.rmi.model.Transaction;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class BankStore {
    private final String dbUrl;
    private final String dbUser;
    private final String dbPassword;

    public BankStore(String dbUrl, String dbUser, String dbPassword) {
        this.dbUrl = dbUrl;
        this.dbUser = dbUser;
        this.dbPassword = dbPassword;
    }

    private Connection getConnection() throws SQLException {
        return DriverManager.getConnection(dbUrl, dbUser, dbPassword);
    }

    public void initialize() throws SQLException {
        try (Connection conn = getConnection(); Statement st = conn.createStatement()) {
            st.executeUpdate("""
                CREATE TABLE IF NOT EXISTS accounts (
                    account_number VARCHAR(20) NOT NULL PRIMARY KEY,
                    holder_name VARCHAR(100) NOT NULL,
                    pin VARCHAR(20) NOT NULL,
                    balance DECIMAL(15, 2) NOT NULL DEFAULT 0.00,
                    updated_at BIGINT NOT NULL,
                    updated_by VARCHAR(50) NOT NULL,
                    deleted TINYINT(1) NOT NULL DEFAULT 0
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
            """);

            st.executeUpdate("""
                CREATE TABLE IF NOT EXISTS transactions (
                    tx_id VARCHAR(50) NOT NULL PRIMARY KEY,
                    account_number VARCHAR(20) NOT NULL,
                    tx_type VARCHAR(20) NOT NULL,
                    amount DECIMAL(15, 2) NOT NULL,
                    target_account VARCHAR(20) DEFAULT NULL,
                    description VARCHAR(255) DEFAULT NULL,
                    created_at BIGINT NOT NULL,
                    created_by VARCHAR(50) NOT NULL
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
            """);

            st.executeUpdate("""
                CREATE TABLE IF NOT EXISTS sync_logs (
                    id INT AUTO_INCREMENT PRIMARY KEY,
                    event_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    server_name VARCHAR(50) NOT NULL,
                    action_name VARCHAR(50) NOT NULL,
                    message TEXT NOT NULL
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
            """);

            // Seed sample accounts if empty
            try (ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM accounts")) {
                if (rs.next() && rs.getInt(1) == 0) {
                    st.executeUpdate("""
                        INSERT INTO accounts (account_number, holder_name, pin, balance, updated_at, updated_by, deleted) VALUES
                        ('1001', 'Nguyen Van An', '1234', 10000000.00, 1700000000000, 'INIT', 0),
                        ('1002', 'Tran Thi Binh', '1234', 5000000.00, 1700000000000, 'INIT', 0),
                        ('1003', 'Le Van Cuong', '1234', 20000000.00, 1700000000000, 'INIT', 0),
                        ('1004', 'Pham Minh Duc', '1234', 1500000.00, 1700000000000, 'INIT', 0)
                    """);
                }
            }
        }
    }

    public Account findAccount(String accountNumber) throws SQLException {
        String sql = "SELECT account_number, holder_name, pin, balance, updated_at, updated_by, deleted FROM accounts WHERE account_number = ?";
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, accountNumber);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapAccount(rs);
                }
            }
        }
        return null;
    }

    public List<Account> listAccounts(boolean includeDeleted) throws SQLException {
        String sql = includeDeleted
                ? "SELECT * FROM accounts ORDER BY account_number"
                : "SELECT * FROM accounts WHERE deleted = 0 ORDER BY account_number";
        List<Account> list = new ArrayList<>();
        try (Connection conn = getConnection(); Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                list.add(mapAccount(rs));
            }
        }
        return list;
    }

    public Account login(String accountNumber, String pin) throws SQLException {
        Account acc = findAccount(accountNumber);
        if (acc != null && !acc.isDeleted() && acc.getPin().equals(pin)) {
            return acc;
        }
        return null;
    }

    public TransactionResult deposit(String accountNumber, double amount, String description, String serverName) throws SQLException {
        if (amount <= 0) {
            throw new IllegalArgumentException("Số tiền nạp phải lớn hơn 0");
        }
        String sqlUpdate = "UPDATE accounts SET balance = balance + ?, updated_at = ?, updated_by = ? WHERE account_number = ? AND deleted = 0";
        String sqlInsertTx = "INSERT INTO transactions (tx_id, account_number, tx_type, amount, target_account, description, created_at, created_by) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";

        long now = System.currentTimeMillis();
        String txId = "TX-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        try (Connection conn = getConnection()) {
            conn.setAutoCommit(false);
            try {
                try (PreparedStatement psUpdate = conn.prepareStatement(sqlUpdate)) {
                    psUpdate.setDouble(1, amount);
                    psUpdate.setLong(2, now);
                    psUpdate.setString(3, serverName);
                    psUpdate.setString(4, accountNumber);
                    int updated = psUpdate.executeUpdate();
                    if (updated == 0) {
                        throw new SQLException("Không tìm thấy tài khoản: " + accountNumber);
                    }
                }

                Transaction tx = new Transaction(txId, accountNumber, Transaction.TYPE_DEPOSIT, amount, null, description, now, serverName);
                try (PreparedStatement psTx = conn.prepareStatement(sqlInsertTx)) {
                    bindTransaction(psTx, tx);
                    psTx.executeUpdate();
                }

                conn.commit();
                Account updatedAcc = findAccount(accountNumber);
                List<Account> affectedAccounts = List.of(updatedAcc);
                return new TransactionResult(updatedAcc, tx, affectedAccounts);
            } catch (Exception e) {
                conn.rollback();
                throw e;
            }
        }
    }

    public TransactionResult withdraw(String accountNumber, double amount, String description, String serverName) throws SQLException {
        if (amount <= 0) {
            throw new IllegalArgumentException("Số tiền rút phải lớn hơn 0");
        }
        Account current = findAccount(accountNumber);
        if (current == null || current.isDeleted()) {
            throw new SQLException("Không tìm thấy tài khoản: " + accountNumber);
        }
        if (current.getBalance() < amount) {
            throw new IllegalArgumentException("Số dư không đủ để thực hiện giao dịch! Hiện có: " + current.getFormattedBalance());
        }

        String sqlUpdate = "UPDATE accounts SET balance = balance - ?, updated_at = ?, updated_by = ? WHERE account_number = ? AND deleted = 0 AND balance >= ?";
        String sqlInsertTx = "INSERT INTO transactions (tx_id, account_number, tx_type, amount, target_account, description, created_at, created_by) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";

        long now = System.currentTimeMillis();
        String txId = "TX-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        try (Connection conn = getConnection()) {
            conn.setAutoCommit(false);
            try {
                try (PreparedStatement psUpdate = conn.prepareStatement(sqlUpdate)) {
                    psUpdate.setDouble(1, amount);
                    psUpdate.setLong(2, now);
                    psUpdate.setString(3, serverName);
                    psUpdate.setString(4, accountNumber);
                    psUpdate.setDouble(5, amount);
                    int updated = psUpdate.executeUpdate();
                    if (updated == 0) {
                        throw new SQLException("Rút tiền thất bại do số dư thay đổi đồng thời");
                    }
                }

                Transaction tx = new Transaction(txId, accountNumber, Transaction.TYPE_WITHDRAW, amount, null, description, now, serverName);
                try (PreparedStatement psTx = conn.prepareStatement(sqlInsertTx)) {
                    bindTransaction(psTx, tx);
                    psTx.executeUpdate();
                }

                conn.commit();
                Account updatedAcc = findAccount(accountNumber);
                List<Account> affectedAccounts = List.of(updatedAcc);
                return new TransactionResult(updatedAcc, tx, affectedAccounts);
            } catch (Exception e) {
                conn.rollback();
                throw e;
            }
        }
    }

    public TransactionResult transfer(String fromAccount, String toAccount, double amount, String description, String serverName) throws SQLException {
        if (amount <= 0) {
            throw new IllegalArgumentException("Số tiền chuyển khoản phải lớn hơn 0");
        }
        if (fromAccount.equals(toAccount)) {
            throw new IllegalArgumentException("Tài khoản thụ hưởng không được trùng với tài khoản chuyển tiền");
        }
        Account from = findAccount(fromAccount);
        if (from == null || from.isDeleted()) {
            throw new SQLException("Tài khoản gửi không tồn tại: " + fromAccount);
        }
        if (from.getBalance() < amount) {
            throw new IllegalArgumentException("Số dư không đủ để chuyển khoản! Hiện có: " + from.getFormattedBalance());
        }
        Account to = findAccount(toAccount);
        if (to == null || to.isDeleted()) {
            throw new SQLException("Tài khoản thụ hưởng không tồn tại: " + toAccount);
        }

        long now = System.currentTimeMillis();
        String txId = "TX-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        String sqlDeduct = "UPDATE accounts SET balance = balance - ?, updated_at = ?, updated_by = ? WHERE account_number = ? AND deleted = 0 AND balance >= ?";
        String sqlAdd = "UPDATE accounts SET balance = balance + ?, updated_at = ?, updated_by = ? WHERE account_number = ? AND deleted = 0";
        String sqlInsertTx = "INSERT INTO transactions (tx_id, account_number, tx_type, amount, target_account, description, created_at, created_by) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";

        try (Connection conn = getConnection()) {
            conn.setAutoCommit(false);
            try {
                // Deduct from sender
                try (PreparedStatement psDeduct = conn.prepareStatement(sqlDeduct)) {
                    psDeduct.setDouble(1, amount);
                    psDeduct.setLong(2, now);
                    psDeduct.setString(3, serverName);
                    psDeduct.setString(4, fromAccount);
                    psDeduct.setDouble(5, amount);
                    int updated = psDeduct.executeUpdate();
                    if (updated == 0) {
                        throw new SQLException("Chuyển tiền thất bại do số dư người gửi thay đổi");
                    }
                }

                // Add to receiver
                try (PreparedStatement psAdd = conn.prepareStatement(sqlAdd)) {
                    psAdd.setDouble(1, amount);
                    psAdd.setLong(2, now);
                    psAdd.setString(3, serverName);
                    psAdd.setString(4, toAccount);
                    psAdd.executeUpdate();
                }

                // Save transaction record (Sender side: TRANSFER_OUT)
                Transaction tx = new Transaction(txId, fromAccount, Transaction.TYPE_TRANSFER_OUT, amount, toAccount, description, now, serverName);
                try (PreparedStatement psTx = conn.prepareStatement(sqlInsertTx)) {
                    bindTransaction(psTx, tx);
                    psTx.executeUpdate();
                }

                // Also save corresponding receiver side: TRANSFER_IN
                String receiverTxId = txId + "-IN";
                Transaction txReceiver = new Transaction(receiverTxId, toAccount, Transaction.TYPE_TRANSFER_IN, amount, fromAccount, description, now, serverName);
                try (PreparedStatement psTxReceiver = conn.prepareStatement(sqlInsertTx)) {
                    bindTransaction(psTxReceiver, txReceiver);
                    psTxReceiver.executeUpdate();
                }

                conn.commit();
                Account updatedFrom = findAccount(fromAccount);
                Account updatedTo = findAccount(toAccount);
                return new TransactionResult(updatedFrom, tx, List.of(updatedFrom, updatedTo));
            } catch (Exception e) {
                conn.rollback();
                throw e;
            }
        }
    }

    public boolean mergeAccount(Account incoming) throws SQLException {
        if (incoming == null) return false;
        Account current = findAccount(incoming.getAccountNumber());
        if (current == null) {
            String sqlInsert = "INSERT INTO accounts (account_number, holder_name, pin, balance, updated_at, updated_by, deleted) VALUES (?, ?, ?, ?, ?, ?, ?)";
            try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sqlInsert)) {
                ps.setString(1, incoming.getAccountNumber());
                ps.setString(2, incoming.getHolderName());
                ps.setString(3, incoming.getPin());
                ps.setDouble(4, incoming.getBalance());
                ps.setLong(5, incoming.getUpdatedAt());
                ps.setString(6, incoming.getUpdatedBy());
                ps.setBoolean(7, incoming.isDeleted());
                ps.executeUpdate();
                return true;
            }
        } else {
            // Last-Write-Wins based on updatedAt
            if (incoming.getUpdatedAt() > current.getUpdatedAt()) {
                String sqlUpdate = "UPDATE accounts SET holder_name = ?, pin = ?, balance = ?, updated_at = ?, updated_by = ?, deleted = ? WHERE account_number = ?";
                try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sqlUpdate)) {
                    ps.setString(1, incoming.getHolderName());
                    ps.setString(2, incoming.getPin());
                    ps.setDouble(3, incoming.getBalance());
                    ps.setLong(4, incoming.getUpdatedAt());
                    ps.setString(5, incoming.getUpdatedBy());
                    ps.setBoolean(6, incoming.isDeleted());
                    ps.setString(7, incoming.getAccountNumber());
                    ps.executeUpdate();
                    return true;
                }
            }
        }
        return false;
    }

    public boolean mergeTransaction(Transaction tx) throws SQLException {
        if (tx == null) return false;
        String sqlCheck = "SELECT 1 FROM transactions WHERE tx_id = ?";
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sqlCheck)) {
            ps.setString(1, tx.getTxId());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return false; // Already present
                }
            }
        }

        String sqlInsert = "INSERT INTO transactions (tx_id, account_number, tx_type, amount, target_account, description, created_at, created_by) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sqlInsert)) {
            bindTransaction(ps, tx);
            ps.executeUpdate();
            return true;
        }
    }

    public List<Transaction> listTransactions(String accountNumber) throws SQLException {
        String sql = "SELECT * FROM transactions WHERE account_number = ? ORDER BY created_at DESC LIMIT 50";
        List<Transaction> list = new ArrayList<>();
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, accountNumber);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapTransaction(rs));
                }
            }
        }
        return list;
    }

    public List<Transaction> listAllTransactions() throws SQLException {
        String sql = "SELECT * FROM transactions ORDER BY created_at DESC LIMIT 100";
        List<Transaction> list = new ArrayList<>();
        try (Connection conn = getConnection(); Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                list.add(mapTransaction(rs));
            }
        }
        return list;
    }

    public void addLog(String serverName, String actionName, String message) {
        String sql = "INSERT INTO sync_logs (server_name, action_name, message) VALUES (?, ?, ?)";
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, serverName);
            ps.setString(2, actionName);
            ps.setString(3, message);
            ps.executeUpdate();
        } catch (SQLException ignored) {
        }
    }

    public List<String> getLogs() throws SQLException {
        String sql = "SELECT event_time, server_name, action_name, message FROM sync_logs ORDER BY id DESC LIMIT 50";
        List<String> logs = new ArrayList<>();
        try (Connection conn = getConnection(); Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                logs.add("[" + rs.getString("event_time") + "] [" + rs.getString("server_name") + "] "
                        + rs.getString("action_name") + " — " + rs.getString("message"));
            }
        }
        return logs;
    }

    private Account mapAccount(ResultSet rs) throws SQLException {
        return new Account(
                rs.getString("account_number"),
                rs.getString("holder_name"),
                rs.getString("pin"),
                rs.getDouble("balance"),
                rs.getLong("updated_at"),
                rs.getString("updated_by"),
                rs.getBoolean("deleted")
        );
    }

    private Transaction mapTransaction(ResultSet rs) throws SQLException {
        return new Transaction(
                rs.getString("tx_id"),
                rs.getString("account_number"),
                rs.getString("tx_type"),
                rs.getDouble("amount"),
                rs.getString("target_account"),
                rs.getString("description"),
                rs.getLong("created_at"),
                rs.getString("created_by")
        );
    }

    private void bindTransaction(PreparedStatement ps, Transaction tx) throws SQLException {
        ps.setString(1, tx.getTxId());
        ps.setString(2, tx.getAccountNumber());
        ps.setString(3, tx.getTxType());
        ps.setDouble(4, tx.getAmount());
        ps.setString(5, tx.getTargetAccount());
        ps.setString(6, tx.getDescription());
        ps.setLong(7, tx.getCreatedAt());
        ps.setString(8, tx.getCreatedBy());
    }

    public static class TransactionResult {
        private final Account account;
        private final Transaction transaction;
        private final List<Account> affectedAccounts;

        public TransactionResult(Account account, Transaction transaction, List<Account> affectedAccounts) {
            this.account = account;
            this.transaction = transaction;
            this.affectedAccounts = affectedAccounts;
        }

        public Account getAccount() {
            return account;
        }

        public Transaction getTransaction() {
            return transaction;
        }

        public List<Account> getAffectedAccounts() {
            return affectedAccounts;
        }
    }
}
