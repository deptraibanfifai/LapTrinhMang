import java.io.*;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;
import java.util.Collections;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.lang.reflect.Method;
import java.lang.reflect.Field;

/** Kiem thu socket that, khong can thu vien ngoai hay thao tac giao dien. */
public class ChatIntegrationTest {

    // Lay hang so va phuong thuc tu class Protocol (Reflection).
    private static final int    MAX_FILE_SIZE;
    private static final String EVERYONE;
    private static final Method READ_FILE;
    private static final Method SAFE_FILE;
    static {
        try {
            Class<?> p = Class.forName("Protocol");
            MAX_FILE_SIZE = (int) p.getField("MAX_FILE_SIZE").get(null);
            EVERYONE      = (String) p.getField("EVERYONE").get(null);
            READ_FILE     = p.getMethod("readFile", DataInputStream.class);
            SAFE_FILE     = p.getMethod("safeFileName", String.class);
        } catch (Exception e) {
            throw new RuntimeException("Khong the load class Protocol", e);
        }
    }
    private static byte[] readFile(DataInputStream in) throws IOException {
        try { return (byte[]) READ_FILE.invoke(null, in); }
        catch (Exception e) { throw new IOException("readFile failed", e); }
    }
    private static String safeFileName(String s) {
        try { return (String) SAFE_FILE.invoke(null, s); }
        catch (Exception e) { return s; }
    }

    // ==== HEADLESS SERVER (cho test - ko GUI, chay trong cung 1 JVM) ====
    static int startHeadlessServer() throws Exception {
        final int[] portHolder = new int[1];
        final ConcurrentHashMap<String, DataOutputStream> clients = new ConcurrentHashMap<>();
        Thread t = new Thread(() -> {
            try (java.net.ServerSocket ss = new java.net.ServerSocket(0)) {
                portHolder[0] = ss.getLocalPort();
                System.out.println("Chat server dang chay tai cong " + portHolder[0]);
                while (true) {
                    java.net.Socket sk = ss.accept();
                    new Thread(() -> serveOne(sk, clients), "client-" + sk.getPort()).start();
                }
            } catch (IOException ignored) { }
        }, "test-server");
        t.setDaemon(true);
        t.start();
        for (int i = 0; i < 50 && portHolder[0] == 0; i++) Thread.sleep(50);
        if (portHolder[0] == 0) throw new AssertionError("Server khong khoi dong duoc");
        return portHolder[0];
    }

    private static void serveOne(java.net.Socket socket, ConcurrentHashMap<String, DataOutputStream> clients) {
        String name = null;
        DataOutputStream out = null;
        try {
            socket.setSoTimeout(15000);
            DataInputStream in  = new DataInputStream(socket.getInputStream());
            out = new DataOutputStream(socket.getOutputStream());
            if (!"LOGIN".equals(in.readUTF())) return;
            name = in.readUTF().trim();
            if (!name.matches("[\\p{L}\\p{N}_-]{1,20}")) {
                out.writeUTF("ERROR"); out.writeUTF("Ten khong hop le"); return;
            }
            synchronized (ChatIntegrationTest.class) {
                if (clients.putIfAbsent(name, out) != null) {
                    out.writeUTF("ERROR"); out.writeUTF("Ten nay dang duoc su dung."); return;
                }
            }
            out.writeUTF("WELCOME"); out.flush();
            socket.setSoTimeout(0);
            broadcastUsers(clients);
            while (true) {
                String type = in.readUTF();
                if ("CHAT".equals(type)) {
                    String to = in.readUTF();
                    String text = in.readUTF();
                    if (text.trim().isEmpty() || text.length() > 4000) {
                        out.writeUTF("ERROR"); out.writeUTF("Tin nhan khong hop le"); continue;
                    }
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    DataOutputStream o = new DataOutputStream(baos);
                    o.writeUTF("CHAT"); o.writeUTF(name); o.writeUTF(to); o.writeUTF(text); o.flush();
                    byte[] pkt = baos.toByteArray();
                    if (EVERYONE.equals(to)) {
                        for (DataOutputStream dst : clients.values()) sendRaw(dst, pkt);
                    } else {
                        DataOutputStream target = clients.get(to);
                        if (target == null) { out.writeUTF("ERROR"); out.writeUTF("Nguoi nhan da offline"); }
                        else {
                            sendRaw(target, pkt);
                            if (target != out) sendRaw(out, pkt);
                        }
                    }
                } else if ("FILE".equals(type)) {
                    String to = in.readUTF();
                    String filename = safeFileName(in.readUTF());
                    byte[] bytes = readFile(in);
                    DataOutputStream target = clients.get(to);
                    if (target == null) { out.writeUTF("ERROR"); out.writeUTF("Nguoi nhan da offline"); continue; }
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    DataOutputStream o = new DataOutputStream(baos);
                    o.writeUTF("FILE"); o.writeUTF(name); o.writeUTF(filename);
                    o.writeInt(bytes.length); o.write(bytes); o.flush();
                    sendRaw(target, baos.toByteArray());
                    out.writeUTF("INFO"); out.writeUTF("Da chuyen file " + filename);
                } else {
                    out.writeUTF("ERROR"); out.writeUTF("Loai goi tin khong hop le");
                    return;
                }
            }
        } catch (Exception ignored) {
        } finally {
            if (name != null && out != null && clients.remove(name, out)) broadcastUsers(clients);
            try { socket.close(); } catch (Exception ignored) {}
        }
    }

    private static void broadcastUsers(ConcurrentHashMap<String, DataOutputStream> clients) {
        List<String> names = new ArrayList<>(clients.keySet());
        Collections.sort(names);
        for (DataOutputStream out : clients.values()) {
            try {
                out.writeUTF("USERS"); out.writeInt(names.size());
                for (String n : names) out.writeUTF(n);
                out.flush();
            } catch (Exception ignored) {}
        }
    }

    private static boolean sendRaw(DataOutputStream out, byte[] pkt) {
        try { synchronized (out) { out.write(pkt); out.flush(); } return true; }
        catch (IOException e) { return false; }
    }

    // ==== MAIN TEST ====
    public static void main(String[] args) throws Exception {
        int port = startHeadlessServer();
        try (Peer alice = new Peer(port, "Alice");
             Peer bob   = new Peer(port, "Bob");
             Peer carol = new Peer(port, "Carol")) {

            alice.awaitUsers("Alice", "Bob", "Carol");
            pass("Online list contains all three clients");

            alice.chat("*", "Xin chào cả lớp 👋");
            for (Peer p : new Peer[]{alice, bob, carol}) {
                Packet pk = p.next("CHAT");
                check("Alice".equals(pk.from) && "*".equals(pk.to) &&
                        "Xin chào cả lớp 👋".equals(pk.text), "Group chat contents");
            }
            pass("Unicode group chat reaches all clients");

            alice.chat("Bob", "Tin riêng");
            check("Tin riêng".equals(bob.next("CHAT").text), "Private recipient");
            check("Tin riêng".equals(alice.next("CHAT").text), "Sender echo");
            carol.assertNoData();
            pass("Private message is not sent to third client");

            Thread first = new Thread(() -> sendBurst(alice, "A"));
            Thread second = new Thread(() -> sendBurst(carol, "C"));
            first.start(); second.start();
            java.util.Set<String> msgs = new java.util.HashSet<>();
            for (int i = 0; i < 80; i++) msgs.add(bob.next("CHAT").text);
            first.join(); second.join();
            check(msgs.size() == 80, "Concurrent message count");
            for (int i = 0; i < 40; i++) {
                check(msgs.contains("A" + i) && msgs.contains("C" + i), "Concurrent contents");
                alice.next("CHAT");
                carol.next("CHAT");
            }
            pass("Concurrent senders preserve packet boundaries");

            byte[] payload = new byte[256 * 1024 + 13];
            new Random(7).nextBytes(payload);
            alice.file("Bob", "../demo.bin", payload);
            Packet file = bob.next("FILE");
            check("Alice".equals(file.from) && "demo.bin".equals(file.text), "File header");
            check(Arrays.equals(payload, file.bytes), "Binary file bytes");
            alice.next("INFO");
            carol.assertNoData();
            pass("Binary file arrives byte-for-byte, with safe filename and correct recipient");

            alice.file("Bob", "empty.txt", new byte[0]);
            check(bob.next("FILE").bytes.length == 0, "Empty file");
            alice.next("INFO");
            pass("Empty file transfer");

            byte[] maximum = new byte[MAX_FILE_SIZE];
            new Random(8).nextBytes(maximum);
            alice.file("Bob", "maximum.bin", maximum);
            check(Arrays.equals(maximum, bob.next("FILE").bytes), "Maximum file");
            alice.next("INFO");
            pass("20 MB file transfer");

            try (Socket duplicate = new Socket("localhost", port)) {
                duplicate.setSoTimeout(5000);
                DataOutputStream o = new DataOutputStream(duplicate.getOutputStream());
                o.writeUTF("LOGIN"); o.writeUTF("Alice"); o.flush();
                DataInputStream i = new DataInputStream(duplicate.getInputStream());
                check("ERROR".equals(i.readUTF()), "Duplicate login rejected");
                i.readUTF();
            }
            pass("Duplicate username rejected");

            alice.chat("Nobody", "Hello");
            alice.next("ERROR");
            alice.file("Nobody", "missing.bin", payload);
            alice.next("ERROR");
            alice.chat("Bob", "x".repeat(4001));
            alice.next("ERROR");
            alice.chat("Bob", "Vẫn hoạt động");
            check("Vẫn hoạt động".equals(bob.next("CHAT").text), "Valid chat after errors");
            alice.next("CHAT");
            pass("Offline recipients and oversized messages handled without breaking stream");

            bob.close();
            alice.awaitUsers("Alice", "Carol");
            try (Peer reconnected = new Peer(port, "Bob")) {
                alice.awaitUsers("Alice", "Bob", "Carol");
                reconnected.chat("Alice", "Đã kết nối lại");
                check("Đã kết nối lại".equals(alice.next("CHAT").text), "Reconnect message");
            }
            pass("Disconnect updates online list and username can reconnect");

            try (Peer bad = new Peer(port, "Bad")) {
                bad.output.writeUTF("FILE");
                bad.output.writeUTF("Alice");
                bad.output.writeUTF("too-large.bin");
                bad.output.writeInt(MAX_FILE_SIZE + 1);
                bad.output.flush();
                try { while (true) bad.readPacket(); }
                catch (EOFException expected) { }
            }
            try (Peer recheck = new Peer(port, "ReCheck")) {
                recheck.chat("Alice", "Server còn chạy");
                check("Server còn chạy".equals(alice.next("CHAT").text), "Server survives bad client");
            }
            pass("Invalid file size disconnects only offending client");
        }
        System.out.println("\n========== ALL TESTS PASSED ==========");
    }

    // ==== Helpers ====
    private static void sendBurst(Peer peer, String prefix) {
        try { for (int i = 0; i < 40; i++) peer.chat("Bob", prefix + i); }
        catch (IOException e) { throw new java.io.UncheckedIOException(e); }
    }
    private static void check(boolean cond, String msg) {
        if (!cond) throw new AssertionError(msg);
    }
    private static void pass(String msg) { System.out.println("PASS: " + msg); }

    // ==== Data classes ====
    static class Packet {
        String type, from, to, text;
        byte[] bytes;
        List<String> names;
    }

    static class Peer implements AutoCloseable {
        final Socket socket;
        final DataInputStream input;
        final DataOutputStream output;

        Peer(int port, String name) throws IOException {
            socket = new Socket("localhost", port);
            socket.setSoTimeout(10000);
            input = new DataInputStream(socket.getInputStream());
            output = new DataOutputStream(socket.getOutputStream());
            output.writeUTF("LOGIN"); output.writeUTF(name); output.flush();
            String r = input.readUTF();
            if ("ERROR".equals(r)) throw new IOException(name + ": " + input.readUTF());
            check("WELCOME".equals(r), "Login " + name);
        }
        void chat(String to, String text) throws IOException {
            output.writeUTF("CHAT"); output.writeUTF(to); output.writeUTF(text); output.flush();
        }
        void file(String to, String filename, byte[] bytes) throws IOException {
            output.writeUTF("FILE"); output.writeUTF(to); output.writeUTF(filename);
            output.writeInt(bytes.length); output.write(bytes); output.flush();
        }
        Packet readPacket() throws IOException {
            Packet p = new Packet();
            p.type = input.readUTF();
            switch (p.type) {
                case "USERS":
                    p.names = new ArrayList<>();
                    int n = input.readInt();
                    for (int i = 0; i < n; i++) p.names.add(input.readUTF());
                    break;
                case "CHAT":
                    p.from = input.readUTF(); p.to = input.readUTF(); p.text = input.readUTF();
                    break;
                case "FILE":
                    p.from = input.readUTF(); p.text = input.readUTF(); p.bytes = readFile(input);
                    break;
                case "INFO": case "ERROR":
                    p.text = input.readUTF();
                    break;
                default: throw new IOException("Unexpected packet: " + p.type);
            }
            return p;
        }
        Packet next(String type) throws IOException {
            Packet p;
            do { p = readPacket(); } while ("USERS".equals(p.type));
            check(type.equals(p.type), "Expected " + type + ", got " + p.type);
            return p;
        }
        void awaitUsers(String... names) throws IOException {
            List<String> expected = Arrays.asList(names);
            while (true) {
                Packet p = readPacket();
                check("USERS".equals(p.type), "Expected online list");
                if (p.names.equals(expected)) return;
            }
        }
        void assertNoData() throws IOException {
            socket.setSoTimeout(350);
            try { while (true) check("USERS".equals(readPacket().type), "Unexpected private data"); }
            catch (SocketTimeoutException expected) { }
            finally { socket.setSoTimeout(10000); }
        }
        @Override public void close() throws IOException { socket.close(); }
    }
}
