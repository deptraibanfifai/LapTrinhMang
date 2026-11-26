package vku.bank.rmi.server;

import vku.bank.rmi.model.Account;
import vku.bank.rmi.model.SyncPackage;
import vku.bank.rmi.model.Transaction;
import vku.bank.rmi.remote.BankRemote;

import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.sql.SQLException;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class BankServiceImpl extends UnicastRemoteObject implements BankRemote {
    private static final long serialVersionUID = 1L;

    private final String serverName;
    private final BankStore store;
    private final String peerIp;
    private final int peerRegistryPort;

    private ServerLogListener listener;

    private final ConcurrentLinkedQueue<SyncItem> retryQueue = new ConcurrentLinkedQueue<>();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread thread = new Thread(r, "bank-sync-" + System.currentTimeMillis());
        thread.setDaemon(true);
        return thread;
    });

    public BankServiceImpl(String serverName, BankStore store, String peerIp,
                           int peerRegistryPort, int servicePort) throws RemoteException {
        super(servicePort);
        this.serverName = serverName;
        this.store = store;
        this.peerIp = peerIp;
        this.peerRegistryPort = peerRegistryPort;

        // Định kỳ kiểm tra hàng đợi và tự động đồng bộ bù nếu server đối tác online lại
        scheduler.scheduleWithFixedDelay(this::retryAndSync, 10, 15, TimeUnit.SECONDS);
    }

    public void setListener(ServerLogListener listener) {
        this.listener = listener;
    }

    private void recordLog(String action, String message) {
        store.addLog(serverName, action, message);
        if (listener != null) {
            listener.onLog(serverName, action, message);
        }
    }

    private void triggerDataChanged() {
        if (listener != null) {
            listener.onDataChanged();
        }
    }

    @Override
    public String ping() throws RemoteException {
        return "OK";
    }

    @Override
    public String getServerName() throws RemoteException {
        return serverName;
    }

    @Override
    public Account login(String accountNumber, String pin) throws RemoteException {
        try {
            Account acc = store.login(accountNumber, pin);
            if (acc != null) {
                recordLog("LOGIN", "Tài khoản " + accountNumber + " (" + acc.getHolderName() + ") đăng nhập");
            }
            return acc;
        } catch (SQLException e) {
            throw remoteError("Lỗi CSDL khi đăng nhập", e);
        }
    }

    @Override
    public Account getAccount(String accountNumber) throws RemoteException {
        try {
            return store.findAccount(accountNumber);
        } catch (SQLException e) {
            throw remoteError("Không tìm thấy tài khoản", e);
        }
    }

    @Override
    public List<Account> getAllAccounts() throws RemoteException {
        try {
            return store.listAccounts(false);
        } catch (SQLException e) {
            throw remoteError("Không lấy được danh sách tài khoản", e);
        }
    }

    @Override
    public synchronized Account deposit(String accountNumber, double amount, String description) throws RemoteException {
        try {
            BankStore.TransactionResult res = store.deposit(accountNumber, amount, description, serverName);
            recordLog("LOCAL-DEPOSIT", "Nạp " + res.getTransaction().getFormattedAmount()
                    + " vào TK " + accountNumber + " (Mã GD: " + res.getTransaction().getTxId() + ")");
            triggerDataChanged();
            replicateToPeer(res.getTransaction(), res.getAffectedAccounts());
            return res.getAccount();
        } catch (IllegalArgumentException e) {
            throw new RemoteException(e.getMessage());
        } catch (SQLException e) {
            throw remoteError("Lỗi thực hiện nạp tiền", e);
        }
    }

    @Override
    public synchronized Account withdraw(String accountNumber, double amount, String description) throws RemoteException {
        try {
            BankStore.TransactionResult res = store.withdraw(accountNumber, amount, description, serverName);
            recordLog("LOCAL-WITHDRAW", "Rút " + res.getTransaction().getFormattedAmount()
                    + " từ TK " + accountNumber + " (Mã GD: " + res.getTransaction().getTxId() + ")");
            triggerDataChanged();
            replicateToPeer(res.getTransaction(), res.getAffectedAccounts());
            return res.getAccount();
        } catch (IllegalArgumentException e) {
            throw new RemoteException(e.getMessage());
        } catch (SQLException e) {
            throw remoteError("Lỗi thực hiện rút tiền", e);
        }
    }

    @Override
    public synchronized Account transfer(String fromAccount, String toAccount, double amount, String description) throws RemoteException {
        try {
            BankStore.TransactionResult res = store.transfer(fromAccount, toAccount, amount, description, serverName);
            recordLog("LOCAL-TRANSFER", "Chuyển " + res.getTransaction().getFormattedAmount()
                    + " từ TK " + fromAccount + " sang TK " + toAccount + " (Mã GD: " + res.getTransaction().getTxId() + ")");
            triggerDataChanged();
            replicateToPeer(res.getTransaction(), res.getAffectedAccounts());
            return res.getAccount();
        } catch (IllegalArgumentException e) {
            throw new RemoteException(e.getMessage());
        } catch (SQLException e) {
            throw remoteError("Lỗi thực hiện chuyển khoản", e);
        }
    }

    @Override
    public List<Transaction> getTransactionHistory(String accountNumber) throws RemoteException {
        try {
            return store.listTransactions(accountNumber);
        } catch (SQLException e) {
            throw remoteError("Không lấy được lịch sử giao dịch", e);
        }
    }

    @Override
    public List<String> getSyncLogs() throws RemoteException {
        try {
            return store.getLogs();
        } catch (SQLException e) {
            throw remoteError("Không lấy được nhật ký đồng bộ", e);
        }
    }

    @Override
    public synchronized boolean mergePeerTransaction(Transaction tx, List<Account> updatedAccounts, String sourceServer) throws RemoteException {
        try {
            boolean accChanged = false;
            if (updatedAccounts != null) {
                for (Account acc : updatedAccounts) {
                    if (store.mergeAccount(acc)) {
                        accChanged = true;
                    }
                }
            }
            boolean txInserted = store.mergeTransaction(tx);
            recordLog("PULL", "Nhận đồng bộ GD " + tx.getTxId() + " (" + tx.getTxType() + " "
                    + tx.getFormattedAmount() + ") từ " + sourceServer);
            triggerDataChanged();
            return accChanged || txInserted;
        } catch (SQLException e) {
            throw remoteError("Lỗi merge dữ liệu từ peer", e);
        }
    }

    @Override
    public synchronized SyncPackage mergeAllFromPeer(SyncPackage incoming, String sourceServer) throws RemoteException {
        try {
            int accUpdates = 0;
            int txInserts = 0;
            if (incoming != null) {
                for (Account acc : incoming.getAccounts()) {
                    if (store.mergeAccount(acc)) accUpdates++;
                }
                for (Transaction tx : incoming.getTransactions()) {
                    if (store.mergeTransaction(tx)) txInserts++;
                }
            }
            if (accUpdates > 0 || txInserts > 0) {
                recordLog("PULL-BATCH", "Đồng bộ tổng: Cập nhật " + accUpdates
                        + " tài khoản, " + txInserts + " giao dịch từ " + sourceServer);
                triggerDataChanged();
            }
            return new SyncPackage(store.listAccounts(true), store.listAllTransactions());
        } catch (SQLException e) {
            throw remoteError("Lỗi CSDL khi hợp nhất toàn bộ", e);
        }
    }

    @Override
    public synchronized String synchronizeNow() throws RemoteException {
        try {
            BankRemote peer = getPeerRemote();
            SyncPackage localPack = new SyncPackage(store.listAccounts(true), store.listAllTransactions());
            SyncPackage peerPack = peer.mergeAllFromPeer(localPack, serverName);

            int accUpdates = 0;
            int txInserts = 0;
            if (peerPack != null) {
                for (Account acc : peerPack.getAccounts()) {
                    if (store.mergeAccount(acc)) accUpdates++;
                }
                for (Transaction tx : peerPack.getTransactions()) {
                    if (store.mergeTransaction(tx)) txInserts++;
                }
            }

            String msg = "Đồng bộ 2 chiều thành công với " + peerIp + ":" + peerRegistryPort
                    + " — Đã cập nhật " + accUpdates + " tài khoản, " + txInserts + " giao dịch.";
            recordLog("SYNC-SUCCESS", msg);
            if (accUpdates > 0 || txInserts > 0) {
                triggerDataChanged();
            }
            return msg;
        } catch (SQLException e) {
            throw remoteError("Lỗi CSDL khi đồng bộ", e);
        } catch (Exception e) {
            recordLog("SYNC-FAIL", "Không kết nối được server đối tác (" + peerIp + "): " + e.getMessage());
            throw new RemoteException("Không kết nối được server đối tác " + peerIp + ":" + peerRegistryPort + " — " + e.getMessage(), e);
        }
    }

    private BankRemote getPeerRemote() throws Exception {
        Registry registry = LocateRegistry.getRegistry(peerIp, peerRegistryPort);
        BankRemote peer = (BankRemote) registry.lookup(BankRemote.SERVICE_NAME);
        if (!"OK".equals(peer.ping())) {
            throw new RemoteException("Server đối tác chưa sẵn sàng (ping != OK)");
        }
        return peer;
    }

    private void replicateToPeer(Transaction tx, List<Account> affectedAccounts) {
        try {
            BankRemote peer = getPeerRemote();
            peer.mergePeerTransaction(tx, affectedAccounts, serverName);
            recordLog("PUSH", "Đã gửi đồng bộ GD " + tx.getTxId() + " tới " + peerIp);
        } catch (Exception e) {
            retryQueue.offer(new SyncItem(tx, affectedAccounts));
            recordLog("QUEUE", "Server đối tác (" + peerIp + ") offline. Tạm lưu GD "
                    + tx.getTxId() + " vào hàng đợi để gửi lại.");
        }
    }

    private void retryAndSync() {
        if (!retryQueue.isEmpty()) {
            SyncItem item;
            while ((item = retryQueue.peek()) != null) {
                try {
                    BankRemote peer = getPeerRemote();
                    peer.mergePeerTransaction(item.tx, item.accounts, serverName);
                    retryQueue.poll();
                    recordLog("RETRY-OK", "Đã gửi lại thành công GD " + item.tx.getTxId() + " tới " + peerIp);
                    triggerDataChanged();
                } catch (Exception e) {
                    break;
                }
            }
        }

        // Tự động đồng bộ đối soát 2 chiều
        try {
            synchronizeNow();
        } catch (Exception ignored) {
        }
    }

    public void stopBackgroundTasks() {
        scheduler.shutdownNow();
    }

    private RemoteException remoteError(String message, Exception cause) {
        return new RemoteException(message + ": " + cause.getMessage(), cause);
    }

    private static class SyncItem {
        final Transaction tx;
        final List<Account> accounts;

        SyncItem(Transaction tx, List<Account> accounts) {
            this.tx = tx;
            this.accounts = accounts;
        }
    }
}
