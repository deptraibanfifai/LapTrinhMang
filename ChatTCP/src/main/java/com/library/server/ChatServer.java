package com.library.server;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

public class ChatServer {
    private final int port;

    public ChatServer(int port) {
        this.port = port;
    }

    public void start() {
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("Server started on port " + port);
            System.out.println("Waiting for clients...");

            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("New client connected from: " + clientSocket.getInetAddress());

                ClientHandler handler = new ClientHandler(clientSocket);
                Thread thread = new Thread(handler);
                thread.setName("Thread-" + clientSocket.getPort());
                thread.start();
            }
        } catch (IOException e) {
            System.err.println("Server Error: " + e.getMessage());
        }
    }

    public static void main(String[] args) {
        ChatServer server = new ChatServer(5000);
        server.start();
    }
}