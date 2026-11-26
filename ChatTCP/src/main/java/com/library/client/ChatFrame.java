package com.library.client;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ChatFrame extends JFrame implements ChatClientListener {

    // --- MÀU SẮC & UI CONSTANTS ---
    private static final Color PRIMARY_COLOR = new Color(70, 130, 180);       // Steel Blue
    private static final Color SECONDARY_COLOR = new Color(100, 149, 237);    // Cornflower Blue
    private static final Color BG_COLOR = new Color(245, 247, 250);           // Xám rất nhạt
    private static final Color BUBBLE_SELF = new Color(70, 130, 180);         // Xanh cho tin của mình
    private static final Color BUBBLE_OTHER = new Color(230, 230, 230);       // Xám cho tin người khác

    // --- UI COMPONENTS ---
    private final JLabel lblChatHeaderName;
    private final JLabel lblChatHeaderStatus;

    // QUAN TRỌNG: Phải dùng DefaultListModel để cập nhật JList động
    private final DefaultListModel<String> userModel = new DefaultListModel<>();
    private final DefaultListModel<String> roomModel = new DefaultListModel<>();
    private final JList<String> userList;
    private final JList<String> roomList;

    private final JPanel chatPanel;
    private final JScrollPane chatScrollPane;
    private final JTextField inputField;

    // --- LOGIC COMPONENTS ---
    private final ChatClient chatClient;
    private final String currentUsername;
    private String currentChatName = "General";
    private boolean isCurrentChatRoom = true;

    // Quản lý lịch sử chat trong RAM
    private final Map<String, List<String>> chatHistories = new HashMap<>();

    public ChatFrame(String username, ChatClient chatClient) {
        this.currentUsername = username;
        this.chatClient = chatClient;

        setTitle("ChatTCP - " + username);
        setSize(1000, 650);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        getContentPane().setBackground(BG_COLOR);
        setLayout(new BorderLayout(0, 0));

        // Khởi tạo Components
        lblChatHeaderName = new JLabel("# General");
        lblChatHeaderStatus = new JLabel("Room");

        userList = new JList<>(userModel);
        userList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        userList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting() && userList.getSelectedValue() != null) {
                selectUser(userList.getSelectedValue());
            }
        });

        roomList = new JList<>(roomModel);
        roomList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        roomList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting() && roomList.getSelectedValue() != null) {
                selectRoom(roomList.getSelectedValue());
            }
        });

        chatPanel = new JPanel();
        chatPanel.setLayout(new BoxLayout(chatPanel, BoxLayout.Y_AXIS));
        chatPanel.setBackground(Color.WHITE);
        chatPanel.setBorder(new EmptyBorder(15, 15, 15, 15));

        chatScrollPane = new JScrollPane(chatPanel);
        chatScrollPane.setBorder(null);
        chatScrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);

        inputField = new JTextField();
        inputField.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        inputField.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Color.LIGHT_GRAY, 1, true),
                new EmptyBorder(8, 12, 8, 12)
        ));

        // Build UI
        initHeader();
        initSidebar();
        initChatArea();
        initInputArea();

        // Xử lý đóng cửa sổ
        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent windowEvent) {
                chatClient.disconnect();
                System.exit(0);
            }
        });

        setVisible(true);

        // Server đã tự gửi USER_LIST và ROOM_LIST khi LOGIN_SUCCESS,
        // nhưng gửi /rooms để đảm bảo cập nhật nếu cần.
        chatClient.sendMessage("/rooms");
    }

    private void initHeader() {
        JPanel headerPanel = new JPanel(new BorderLayout(10, 0));
        headerPanel.setBackground(PRIMARY_COLOR);
        headerPanel.setBorder(new EmptyBorder(15, 20, 15, 20));

        lblChatHeaderName.setForeground(Color.WHITE);
        lblChatHeaderName.setFont(new Font("Segoe UI", Font.BOLD, 18));

        lblChatHeaderStatus.setForeground(new Color(200, 230, 255));
        lblChatHeaderStatus.setFont(new Font("Segoe UI", Font.PLAIN, 13));

        JPanel headerLeft = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        headerLeft.setOpaque(false);
        headerLeft.add(lblChatHeaderName);
        headerLeft.add(Box.createHorizontalStrut(10));
        headerLeft.add(lblChatHeaderStatus);

        headerPanel.add(headerLeft, BorderLayout.WEST);
        getContentPane().add(headerPanel, BorderLayout.NORTH);
    }

    private void initSidebar() {
        JPanel sidebarPanel = new JPanel();
        sidebarPanel.setLayout(new BoxLayout(sidebarPanel, BoxLayout.Y_AXIS));
        sidebarPanel.setBackground(new Color(235, 238, 245));
        sidebarPanel.setPreferredSize(new Dimension(220, 0));
        sidebarPanel.setBorder(new EmptyBorder(15, 15, 15, 15));

        // Users Section
        JLabel lblUsers = new JLabel("ONLINE USERS");
        lblUsers.setFont(new Font("Segoe UI", Font.BOLD, 12));
        lblUsers.setForeground(Color.GRAY);
        sidebarPanel.add(lblUsers);
        sidebarPanel.add(Box.createVerticalStrut(10));

        JScrollPane userScroll = new JScrollPane(userList);
        userScroll.setBorder(null);
        userScroll.setPreferredSize(new Dimension(200, 200));
        sidebarPanel.add(userScroll);
        sidebarPanel.add(Box.createVerticalStrut(20));

        // Rooms Section
        JLabel lblRooms = new JLabel("ROOMS");
        lblRooms.setFont(new Font("Segoe UI", Font.BOLD, 12));
        lblRooms.setForeground(Color.GRAY);
        sidebarPanel.add(lblRooms);
        sidebarPanel.add(Box.createVerticalStrut(10));

        JScrollPane roomScroll = new JScrollPane(roomList);
        roomScroll.setBorder(null);
        sidebarPanel.add(roomScroll);

        getContentPane().add(sidebarPanel, BorderLayout.WEST);
    }

    private void initChatArea() {
        getContentPane().add(chatScrollPane, BorderLayout.CENTER);
    }

    private void initInputArea() {
        JPanel inputPanel = new JPanel(new BorderLayout(10, 0));
        inputPanel.setBackground(BG_COLOR);
        inputPanel.setBorder(new EmptyBorder(15, 20, 20, 20));

        JButton sendBtn = new JButton("Send");
        sendBtn.setBackground(SECONDARY_COLOR);
        sendBtn.setForeground(Color.WHITE);
        sendBtn.setFont(new Font("Segoe UI", Font.BOLD, 14));
        sendBtn.setFocusPainted(false);
        sendBtn.setBorderPainted(false);
        sendBtn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        sendBtn.addActionListener(e -> sendMessageToServer());

        // Gửi bằng phím Enter
        inputField.addActionListener(e -> sendMessageToServer());

        inputPanel.add(inputField, BorderLayout.CENTER);
        inputPanel.add(sendBtn, BorderLayout.EAST);

        getContentPane().add(inputPanel, BorderLayout.SOUTH);
    }

    // =========================================================
    // IMPLEMENT CHATCLIENTLISTENER (BẮT BUỘC PHẢI CÓ)
    // =========================================================

    @Override
    public void onMessageReceived(String message) {
        SwingUtilities.invokeLater(() -> {
            // 1. Xử lý các thông báo hệ thống / room
            if (message.startsWith("Joined room:") || message.startsWith("Left room:") ||
                    message.startsWith("You are not a member") || message.startsWith("Room not found") ||
                    message.startsWith("Available rooms:")) {
                appendSystemMessage(message);
            }
            // 2. Xử lý tin nhắn chat thông thường
            else {
                String target = currentChatName;
                chatHistories.computeIfAbsent(target, k -> new ArrayList<>()).add(message);

                // Hiển thị nếu đang ở đúng phòng/người, hoặc nếu là tin broadcast/system
                if (currentChatName.equals(target) || isGlobalMessage(message)) {
                    appendChatMessageToUI(message);
                }
            }
        });
    }

    private boolean isGlobalMessage(String msg) {
        return msg.startsWith("[All]") || msg.startsWith("[System]");
    }

    @Override
    public void onUserListUpdated(List<String> users) {
        SwingUtilities.invokeLater(() -> updateUserList(users));
    }

    @Override
    public void onRoomListUpdated(List<String> rooms) {
        SwingUtilities.invokeLater(() -> updateRoomList(rooms));
    }

    @Override
    public void onDisconnected() {
        SwingUtilities.invokeLater(() -> {
            JOptionPane.showMessageDialog(this, "Đã mất kết nối đến Server!", "Disconnected", JOptionPane.ERROR_MESSAGE);
            System.exit(0);
        });
    }

    @Override
    public void onError(String errorMessage) {
        SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(this, errorMessage, "Error", JOptionPane.ERROR_MESSAGE));
    }

    // =========================================================
    // CÁC PHƯƠNG THỨC CẬP NHẬT GUI (ĐÃ SỬA LỖI)
    // =========================================================

    private void updateUserList(List<String> users) {
        userModel.clear(); // Xóa cũ
        for (String user : users) {
            userModel.addElement(user); // Thêm mới vào Model
        }
    }

    private void updateRoomList(List<String> rooms) {
        roomModel.clear(); // Xóa cũ
        for (String room : rooms) {
            roomModel.addElement(room); // Thêm mới vào Model
        }
    }

    private void appendSystemMessage(String message) {
        JPanel wrapper = new JPanel(new FlowLayout(FlowLayout.CENTER));
        wrapper.setOpaque(false);

        JLabel lbl = new JLabel("⚙ " + message);
        lbl.setForeground(Color.GRAY);
        lbl.setFont(new Font("Segoe UI", Font.ITALIC, 12));

        wrapper.add(lbl);
        chatPanel.add(wrapper);
        scrollToBottom();
    }

    private void appendChatMessageToUI(String message) {
        boolean isMe = message.startsWith("Me:");

        JPanel wrapper = new JPanel(new FlowLayout(isMe ? FlowLayout.RIGHT : FlowLayout.LEFT, 0, 5));
        wrapper.setOpaque(false);
        wrapper.setMaximumSize(new Dimension(800, 300));

        JPanel bubble = new JPanel();
        bubble.setLayout(new BoxLayout(bubble, BoxLayout.Y_AXIS));
        bubble.setBackground(isMe ? BUBBLE_SELF : BUBBLE_OTHER);
        bubble.setBorder(new EmptyBorder(10, 15, 10, 15));

        // Xử lý nội dung: bỏ prefix "Me: " nếu có để hiển thị gọn
        String displayText = isMe ? message.substring(3).trim() : message;

        JLabel lblContent = new JLabel("<html><div style='width: 350px; word-wrap: break-word;'>" + displayText + "</div></html>");
        lblContent.setForeground(isMe ? Color.WHITE : Color.BLACK);
        lblContent.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        lblContent.setAlignmentX(Component.LEFT_ALIGNMENT);

        bubble.add(lblContent);
        wrapper.add(bubble);
        chatPanel.add(wrapper);

        scrollToBottom();
    }

    private void scrollToBottom() {
        chatPanel.revalidate();
        chatPanel.repaint();
        SwingUtilities.invokeLater(() -> {
            JScrollBar vertical = chatScrollPane.getVerticalScrollBar();
            vertical.setValue(vertical.getMaximum());
        });
    }

    private void clearChatArea() {
        chatPanel.removeAll();
        chatPanel.revalidate();
        chatPanel.repaint();
    }

    // =========================================================
    // XỬ LÝ LOGIC CHỌN CHAT & GỬI TIN
    // =========================================================

    private void sendMessageToServer() {
        String text = inputField.getText().trim();
        if (text.isEmpty()) return;

        String command;
        if (isCurrentChatRoom) {
            command = "/roommsg " + currentChatName + " " + text;
        } else {
            command = "/msg " + currentChatName + " " + text;
        }

        chatClient.sendMessage(command);

        // Optimistic UI: Hiển thị ngay cho người dùng
        appendChatMessageToUI("Me: " + text);
        inputField.setText("");
        inputField.requestFocus();
    }

    private void selectUser(String username) {
        currentChatName = username;
        isCurrentChatRoom = false;
        lblChatHeaderName.setText("👤 " + username);
        lblChatHeaderStatus.setText("● Online");
        renderCurrentChatHistory();
    }

    private void selectRoom(String roomName) {
        currentChatName = roomName;
        isCurrentChatRoom = true;
        lblChatHeaderName.setText("# " + roomName);
        lblChatHeaderStatus.setText("Room");

        // Tự động join room nếu chưa có lịch sử (tức là chưa từng vào)
        if (!chatHistories.containsKey(roomName)) {
            chatClient.sendMessage("/join " + roomName);
        }
        renderCurrentChatHistory();
    }

    private void renderCurrentChatHistory() {
        clearChatArea();
        List<String> history = chatHistories.getOrDefault(currentChatName, new ArrayList<>());
        for (String msg : history) {
            appendChatMessageToUI(msg);
        }
    }
}