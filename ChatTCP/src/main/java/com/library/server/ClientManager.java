package com.library.server;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ClientManager {
    private static final ClientManager instance = new ClientManager();
    private final ConcurrentHashMap<String, ClientHandler> clients;

    private ClientManager() {
        this.clients = new ConcurrentHashMap<>();
    }

    public static ClientManager getInstance() {
        return instance;
    }

    public boolean addClient(String username, ClientHandler handler) {
        return clients.putIfAbsent(username, handler) == null;
    }

    public boolean containsUsername(String username) {
        return clients.containsKey(username);
    }

    public void removeClient(String username) {
        clients.remove(username);
    }

    // Phương thức quan trọng nhất cho Chat 1-1
    public ClientHandler getClient(String username) {
        return clients.get(username);
    }

    public List<String> getOnlineUsers() {
        return new ArrayList<>(clients.keySet());
    }
    public void broadcast(String message, String senderUsername) {
        // Duyệt qua tất cả các cặp (username, ClientHandler) đang online
        for (Map.Entry<String, ClientHandler> entry : clients.entrySet()) {
            String currentUsername = entry.getKey();
            ClientHandler handler = entry.getValue();

            // Chỉ gửi cho những người KHÔNG phải là người gửi
            if (!currentUsername.equals(senderUsername)) {
                handler.sendMessage(message);
            }
        }
    }
}