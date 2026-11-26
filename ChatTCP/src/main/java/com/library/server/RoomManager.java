package com.library.server;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class RoomManager {
    private static final RoomManager instance = new RoomManager();
    private final ConcurrentHashMap<String, ChatRoom> rooms;

    private RoomManager() {
        this.rooms = new ConcurrentHashMap<>();
        // Tạo các room mặc định khi Server khởi động
        createRoom("General");
        createRoom("Java");
        createRoom("Network");
    }

    public static RoomManager getInstance() {
        return instance;
    }

    // Chuẩn hóa tên room: loại bỏ khoảng trắng thừa, viết hoa chữ cái đầu
    private String normalizeRoomName(String name) {
        if (name == null || name.trim().isEmpty()) return null;
        String trimmed = name.trim();
        return trimmed.substring(0, 1).toUpperCase() + trimmed.substring(1).toLowerCase();
    }

    public void createRoom(String roomName) {
        String normalizedName = normalizeRoomName(roomName);
        if (normalizedName != null && !rooms.containsKey(normalizedName)) {
            rooms.put(normalizedName, new ChatRoom(normalizedName));
        }
    }

    public ChatRoom getRoom(String roomName) {
        return rooms.get(normalizeRoomName(roomName));
    }

    public boolean containsRoom(String roomName) {
        return rooms.containsKey(normalizeRoomName(roomName));
    }

    public List<String> getRoomNames() {
        return new ArrayList<>(rooms.keySet());
    }

    public boolean joinRoom(String roomName, String username) {
        ChatRoom room = getRoom(roomName);
        if (room == null) return false;
        return room.addMember(username);
    }

    public boolean leaveRoom(String roomName, String username) {
        ChatRoom room = getRoom(roomName);
        if (room == null) return false;
        return room.removeMember(username);
    }

    public void broadcastToRoom(String roomName, String sender, String message) {
        ChatRoom room = getRoom(roomName);
        if (room != null) {
            for (String member : room.getMembers()) {
                // Không gửi lại cho chính người gửi
                if (!member.equals(sender)) {
                    ClientHandler handler = ClientManager.getInstance().getClient(member);
                    if (handler != null) {
                        handler.sendMessage("[Room: " + room.getRoomName() + "] " + sender + ": " + message);
                    }
                }
            }
        }
    }

    // Quan trọng: Xóa user khỏi TẤT CẢ các room khi disconnect
    public void removeUserFromAllRooms(String username) {
        for (ChatRoom room : rooms.values()) {
            room.removeMember(username);
        }
    }
}