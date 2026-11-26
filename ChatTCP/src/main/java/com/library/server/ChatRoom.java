package com.library.server;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class ChatRoom {
    private final String roomName;
    // Sử dụng newKeySet() để có một Set an toàn cho đa luồng (Thread-safe)
    private final Set<String> members = ConcurrentHashMap.newKeySet();

    public ChatRoom(String roomName) {
        this.roomName = roomName;
    }

    public String getRoomName() {
        return roomName;
    }

    public boolean addMember(String username) {
        return members.add(username); // Trả về true nếu thêm thành công, false nếu đã tồn tại
    }

    public boolean removeMember(String username) {
        return members.remove(username);
    }

    public boolean hasMember(String username) {
        return members.contains(username);
    }

    public Set<String> getMembers() {
        return Collections.unmodifiableSet(members); // Trả về bản chỉ đọc để an toàn
    }

    public int getMemberCount() {
        return members.size();
    }
}