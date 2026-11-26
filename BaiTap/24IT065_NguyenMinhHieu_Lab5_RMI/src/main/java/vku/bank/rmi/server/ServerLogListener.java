package vku.bank.rmi.server;

public interface ServerLogListener {
    void onLog(String serverName, String action, String message);
    void onDataChanged();
}
