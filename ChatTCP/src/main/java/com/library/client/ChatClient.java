package com.library.client;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.Arrays;
import java.util.List;

public class ChatClient {
    private final String host;
    private final int port;
    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;
    private ChatClientListener listener;
    private Thread receiverThread;
    private boolean isConnected = false;

    public ChatClient(String host, int port, ChatClientListener listener) {
        this.host = host;
        this.port = port;
        this.listener = listener;
    }

    public void connect(String username) {
        new Thread(() -> {
            try {
                socket = new Socket(host, port);
                out = new PrintWriter(socket.getOutputStream(), true);
                in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                isConnected = true;

                // Gửi username để login
                out.println(username);

                // Bắt đầu Receiver Thread
                receiverThread = new Thread(new Receiver());
                receiverThread.start();

            } catch (IOException e) {
                isConnected = false;
                if (listener != null) {
                    listener.onError("Không thể kết nối đến Server. Kiểm tra Host/Port hoặc Server chưa chạy.");
                }
            }
        }).start(); // Chạy kết nối trên background thread để không block GUI
    }

    public void sendMessage(String message) {
        if (isConnected && out != null) {
            out.println(message);
        }
    }

    public void disconnect() {
        isConnected = false;
        try {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        if (receiverThread != null && receiverThread.isAlive()) {
            receiverThread.interrupt();
        }
    }

    // --- RECEIVER THREAD ---
    private class Receiver implements Runnable {
        @Override
        public void run() {
            try {
                String line;
                while (isConnected && (line = in.readLine()) != null) {
                    if (line.startsWith("USER_LIST:")) {
                        String usersStr = line.substring(10);
                        List<String> users = usersStr.isEmpty() ? Arrays.asList() : Arrays.asList(usersStr.split(","));
                        if (listener != null) listener.onUserListUpdated(users);
                    } else if (line.startsWith("ROOM_LIST:")) {
                        String roomsStr = line.substring(10);
                        List<String> rooms = roomsStr.isEmpty() ? Arrays.asList() : Arrays.asList(roomsStr.split(","));
                        if (listener != null) listener.onRoomListUpdated(rooms);
                    } else {
                        if (listener != null) listener.onMessageReceived(line);
                    }
                }
            } catch (IOException e) {
                // Kết nối bị đóng
            } finally {
                isConnected = false;
                if (listener != null) listener.onDisconnected();
            }
        }
    }
}