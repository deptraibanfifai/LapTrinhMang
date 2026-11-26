package com.library.client;

import java.util.List;

public interface ChatClientListener {
    void onMessageReceived(String message);
    void onUserListUpdated(List<String> users);
    void onRoomListUpdated(List<String> rooms);
    void onDisconnected();
    void onError(String errorMessage);
}