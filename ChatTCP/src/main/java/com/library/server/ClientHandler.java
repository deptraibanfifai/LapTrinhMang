package com.library.server;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.List;

public class ClientHandler implements Runnable {
    private final Socket clientSocket;
    private String username;
    private BufferedReader in;
    private PrintWriter out;

    public ClientHandler(Socket clientSocket) {
        this.clientSocket = clientSocket;
    }

    @Override
    public void run() {
        try {
            in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
            out = new PrintWriter(clientSocket.getOutputStream(), true);

            // --- GIAI ĐOẠN 1: XỬ LÝ LOGIN ---
            while (true) {
                String input = in.readLine();
                if (input == null) {
                    System.out.println("Client disconnected during login.");
                    return;
                }

                if (ClientManager.getInstance().addClient(input, this)) {
                    this.username = input;
                    out.println("LOGIN_SUCCESS");
                    System.out.println("User " + username + " logged in successfully.");

                    // TỰ ĐỘNG JOIN ROOM GENERAL
                    RoomManager.getInstance().joinRoom("General", this.username);
                    out.println("You have automatically joined room: General");

                    System.out.println("Online users: " + ClientManager.getInstance().getOnlineUsers());
                    break;
                } else {
                    out.println("LOGIN_FAILED");
                }
            }

            // --- GIAI ĐOẠN 2: CHỜ VÀ XỬ LÝ TIN NHẮN LIÊN TỤC ---
            String message;
            while ((message = in.readLine()) != null) {
                System.out.println("[" + username + "] sent: " + message);

                if (message.startsWith("/msg ")) {
                    String[] parts = message.split(" ", 3);
                    if (parts.length < 3) {
                        this.sendMessage("Usage: /msg <username> <message>");
                    } else {
                        String targetUser = parts[1];
                        String content = parts[2];
                        if (targetUser.equals(this.username)) {
                            this.sendMessage("You cannot send a private message to yourself.");
                        } else {
                            ClientHandler targetHandler = ClientManager.getInstance().getClient(targetUser);
                            if (targetHandler == null) {
                                this.sendMessage("User " + targetUser + " is not online.");
                            } else {
                                targetHandler.sendMessage("[Private] " + this.username + ": " + content);
                                this.sendMessage("[To " + targetUser + "]: " + content);
                            }
                        }
                    }

                } else if (message.startsWith("/all ")) {
                    String[] parts = message.split(" ", 2);
                    if (parts.length < 2 || parts[1].trim().isEmpty()) {
                        this.sendMessage("Usage: /all <message>");
                    } else {
                        String content = parts[1].trim();
                        ClientManager.getInstance().broadcast("[All] " + this.username + ": " + content, this.username);
                        this.sendMessage("To All " + this.username + ": " + content);
                    }

                } else if (message.equals("/rooms")) {
                    List<String> roomNames = RoomManager.getInstance().getRoomNames();
                    StringBuilder sb = new StringBuilder("Available rooms:\n");
                    for (String room : roomNames) {
                        sb.append("- ").append(room).append("\n");
                    }
                    this.sendMessage(sb.toString().trim());

                } else if (message.startsWith("/join ")) {
                    String[] parts = message.split(" ", 2);
                    if (parts.length < 2 || parts[1].trim().isEmpty()) {
                        this.sendMessage("Usage: /join <room>");
                    } else {
                        String roomName = parts[1].trim();
                        if (!RoomManager.getInstance().containsRoom(roomName)) {
                            this.sendMessage("Room not found: " + roomName);
                        } else if (RoomManager.getInstance().getRoom(roomName).hasMember(this.username)) {
                            this.sendMessage("You are already a member of room: " + roomName);
                        } else {
                            RoomManager.getInstance().joinRoom(roomName, this.username);
                            this.sendMessage("Joined room: " + roomName);
                        }
                    }

                } else if (message.startsWith("/leave ")) {
                    String[] parts = message.split(" ", 2);
                    if (parts.length < 2 || parts[1].trim().isEmpty()) {
                        this.sendMessage("Usage: /leave <room>");
                    } else {
                        String roomName = parts[1].trim();
                        if (!RoomManager.getInstance().containsRoom(roomName)) {
                            this.sendMessage("Room not found: " + roomName);
                        } else if (!RoomManager.getInstance().getRoom(roomName).hasMember(this.username)) {
                            this.sendMessage("You are not a member of room: " + roomName);
                        } else {
                            RoomManager.getInstance().leaveRoom(roomName, this.username);
                            this.sendMessage("Left room: " + roomName);
                        }
                    }

                } else if (message.startsWith("/members ")) {
                    String[] parts = message.split(" ", 2);
                    if (parts.length < 2 || parts[1].trim().isEmpty()) {
                        this.sendMessage("Usage: /members <room>");
                    } else {
                        String roomName = parts[1].trim();
                        if (!RoomManager.getInstance().containsRoom(roomName)) {
                            this.sendMessage("Room not found: " + roomName);
                        } else {
                            ChatRoom room = RoomManager.getInstance().getRoom(roomName);
                            StringBuilder sb = new StringBuilder("Members in " + roomName + ":\n");
                            for (String member : room.getMembers()) {
                                sb.append("- ").append(member).append("\n");
                            }
                            this.sendMessage(sb.toString().trim());
                        }
                    }

                } else if (message.equals("/myrooms")) {
                    List<String> allRooms = RoomManager.getInstance().getRoomNames();
                    StringBuilder sb = new StringBuilder();
                    boolean inAny = false;
                    for (String roomName : allRooms) {
                        if (RoomManager.getInstance().getRoom(roomName).hasMember(this.username)) {
                            if (!inAny) {
                                sb.append("My rooms:\n");
                                inAny = true;
                            }
                            sb.append("- ").append(roomName).append("\n");
                        }
                    }
                    this.sendMessage(inAny ? sb.toString().trim() : "You are not in any room.");

                } else if (message.startsWith("/roommsg ")) {
                    String[] parts = message.split(" ", 3);
                    if (parts.length < 3) {
                        this.sendMessage("Usage: /roommsg <room> <message>");
                    } else {
                        String roomName = parts[1];
                        String content = parts[2];

                        if (!RoomManager.getInstance().containsRoom(roomName)) {
                            this.sendMessage("Room not found: " + roomName);
                        } else if (!RoomManager.getInstance().getRoom(roomName).hasMember(this.username)) {
                            this.sendMessage("You are not a member of room: " + roomName);
                        } else {
                            RoomManager.getInstance().broadcastToRoom(roomName, this.username, content);
                            this.sendMessage("[To Room: " + roomName + "] " + this.username + ": " + content);
                        }
                    }

                } else if (message.equals("/help")) {
                    this.sendMessage("Available commands:\n" +
                            "/msg <username> <message>\n" +
                            "/all <message>\n" +
                            "/rooms\n" +
                            "/join <room>\n" +
                            "/leave <room>\n" +
                            "/members <room>\n" +
                            "/myrooms\n" +
                            "/roommsg <room> <message>\n" +
                            "/help");
                } else {
                    this.sendMessage("Unknown command. Type /help for available commands.");
                }
            }

        } catch (IOException e) {
            System.out.println("Connection lost for user: " + username);
        } finally {
            // --- GIAI ĐOẠN 3: DỌN DẸP KHI DISCONNECT ---
            if (this.username != null) {
                // 1. Xóa user khỏi TẤT CẢ các room TRƯỚC
                RoomManager.getInstance().removeUserFromAllRooms(this.username);
                // 2. Sau đó mới xóa khỏi ClientManager
                ClientManager.getInstance().removeClient(this.username);
                System.out.println("User " + this.username + " disconnected.");
                System.out.println("Online users: " + ClientManager.getInstance().getOnlineUsers());
            }
            closeResources();
        }
    }

    public void sendMessage(String message) {
        if (out != null) {
            out.println(message);
        }
    }

    private void closeResources() {
        try {
            if (in != null) in.close();
            if (out != null) out.close();
            if (clientSocket != null && !clientSocket.isClosed()) {
                clientSocket.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}