package vn.vku.chat;

import java.io.*;
import java.net.*;
import java.util.*;
import vn.vku.chat.common.*;
import vn.vku.chat.server.ChatServer;

/** Kiểm thử bằng socket thật, không cần giao diện, không cần micro/camera. */
public final class IntegrationChecks {
  private static int checks;

  private static void check(boolean ok, String message) {
    if (!ok) throw new AssertionError(message);
    checks++;
    System.out.println("PASS " + message);
  }

  private static final class Peer implements AutoCloseable {
    final Socket socket;
    final DataInputStream in;
    final DataOutputStream out;
    final String name;
    final Packet welcome;

    Peer(int port, String name) throws IOException {
      this.name = name;
      socket = new Socket("127.0.0.1", port);
      socket.setSoTimeout(2500);
      in = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
      out = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));
      send(Packet.of("HELLO", "", name));
      welcome = PacketCodec.read(in);
    }

    void send(Packet p) throws IOException {
      PacketCodec.write(out, p);
    }

    Packet next(String type) throws IOException {
      long until = System.currentTimeMillis() + 3000;
      while (System.currentTimeMillis() < until) {
        Packet p = PacketCodec.read(in);
        if (p.type().equals(type)) return p;
      }
      throw new IOException("Không nhận được " + type);
    }

    boolean none(String type) throws IOException {
      socket.setSoTimeout(200);
      try {
        for (; ; ) if (PacketCodec.read(in).type().equals(type)) return false;
      } catch (SocketTimeoutException expected) {
        return true;
      } finally {
        socket.setSoTimeout(2500);
      }
    }

    @Override
    public void close() throws IOException {
      socket.close();
    }
  }

  public static void main(String[] args) throws Exception {
    codecChecks();
    String iface;
    if (args.length > 0) iface = args[0];
    else {
      var list = NetUtil.interfaces();
      iface = list.isEmpty() ? "lo" : list.get(0).getName();
    }
    try (ChatServer server = new ChatServer(t -> {})) {
      server.start(0, NetUtil.choose(iface));
      List<Peer> peers = new ArrayList<>();
      try {
        for (int i = 0; i < 6; i++) {
          Peer p = new Peer(server.port(), "User" + i);
          peers.add(p);
          check(p.welcome.type().equals("WELCOME"), "Kết nối client " + (i + 1));
        }
        Peer a = peers.get(0), b = peers.get(1), outsider = peers.get(2);
        for (Peer p : peers) {
          Packet list;
          do {
            list = p.next("USERS");
          } while (list.text().split("\n").length != 6);
        }
        check(true, "Cả 6 client thấy danh sách 6 người online");
        try (Peer duplicate = new Peer(server.port(), "User0")) {
          check(duplicate.welcome.type().equals("ERROR"), "Từ chối tên trùng");
        }
        try (Peer invalid = new Peer(server.port(), "bad name")) {
          check(invalid.welcome.type().equals("ERROR"), "Từ chối tên không hợp lệ");
        }
        a.send(new Packet("TEXT", "ADMIN_GIA", b.name, "", "Chào Hiếu 😀", null));
        check(
            b.next("TEXT").sender().equals(a.name), "Server thay sender giả bằng danh tính socket");
        check(
            a.next("TEXT").text().equals("Chào Hiếu 😀"),
            "Echo text Unicode và emoji cho người gửi");
        for (int i = 2; i < 6; i++)
          check(peers.get(i).none("TEXT"), "Chat riêng không lọt sang client " + i);
        a.send(Packet.of("TEXT", Config.ALL, "Hello six"));
        for (Peer p : peers)
          check(p.next("TEXT").text().equals("Hello six"), "Chat chung đến " + p.name);
        byte[] file = new byte[128 * 1024];
        new Random(7).nextBytes(file);
        a.send(new Packet("FILE", "", b.name, "file1", "sample.bin", file));
        check(Arrays.equals(b.next("FILE").data(), file), "File nhị phân 128 KiB nguyên vẹn");
        check(Arrays.equals(a.next("FILE").data(), file), "Echo file nguyên vẹn");
        check(outsider.none("FILE"), "File riêng không lọt sang người thứ ba");
        byte[] voice = {82, 73, 70, 70, 1, 2, 3, 4};
        b.send(new Packet("VOICE", "", a.name, "v1", "voice.wav", voice));
        check(Arrays.equals(a.next("VOICE").data(), voice), "Chuyển tiếp payload ghi âm");
        b.next("VOICE");
        a.send(Packet.of("TEXT", "Nobody", "test"));
        check(a.next("ERROR").text().contains("offline"), "Thông báo người nhận offline");
        a.send(Packet.of("UDP_GROUP", Config.ALL, "chưa tham gia"));
        check(a.next("ERROR").text().contains("tham gia"), "Chặn gửi nhóm khi chưa tham gia");
        a.send(Packet.of("GROUP_JOIN", "", ""));
        a.next("INFO");
        a.send(Packet.of("GROUP_LEAVE", "", ""));
        check(a.next("INFO").text().contains("rời"), "Join/leave được ghi nhận trên server");
        a.send(new Packet("CALL_INVITE", "", b.name, "call1", "VIDEO", null));
        check(b.next("CALL_INVITE").sender().equals(a.name), "Nhận lời mời gọi video");
        // Trước ACCEPT, server không cho truyền media.
        a.send(new Packet("AUDIO", "", b.name, "call1", "", new byte[3200]));
        check(b.none("AUDIO"), "Chặn âm thanh trước khi chấp nhận");
        outsider.send(new Packet("CALL_INVITE", "", b.name, "busy1", "AUDIO", null));
        check(
            outsider.next("CALL_REJECT").text().contains("bận"), "Từ chối gọi khi người nhận bận");
        b.send(new Packet("CALL_ACCEPT", "", a.name, "call1", "", null));
        check(a.next("CALL_ACCEPT").text().equals("VIDEO"), "Bảo toàn loại VIDEO khi chấp nhận");
        a.send(new Packet("AUDIO", "", b.name, "call1", "", new byte[3200]));
        check(b.next("AUDIO").data().length == 3200, "Chuyển tiếp chunk audio trong cuộc gọi");
        a.send(new Packet("VIDEO", "", b.name, "call1", "", new byte[] {1, 2, 3}));
        check(b.next("VIDEO").data().length == 3, "Chuyển tiếp frame video trong cuộc gọi");
        outsider.send(new Packet("AUDIO", "User0", b.name, "call1", "", new byte[3200]));
        check(b.none("AUDIO"), "Người thứ ba không chèn được audio vào cuộc gọi");
        b.send(new Packet("CALL_END", "", a.name, "call1", "Đã kết thúc", null));
        check(a.next("CALL_END").id().equals("call1"), "Kết thúc cuộc gọi");
        a.send(new Packet("VIDEO", "", b.name, "call1", "", new byte[] {1}));
        check(b.none("VIDEO"), "Chặn media sau khi kết thúc");
        a.send(new Packet("CALL_INVITE", "", b.name, "call2", "AUDIO", null));
        b.next("CALL_INVITE");
        b.send(new Packet("CALL_REJECT", "", a.name, "call2", "Không tiện nghe", null));
        check(a.next("CALL_REJECT").text().equals("Không tiện nghe"), "Từ chối cuộc gọi");
        a.send(new Packet("CALL_INVITE", "", b.name, "call3", "AUDIO", null));
        b.next("CALL_INVITE");
        b.close();
        check(
            a.next("CALL_END").text().contains("mất kết nối"),
            "Dọn cuộc gọi khi client tắt đột ngột");
        check(a.next("USERS").text().split("\n").length == 5, "Cập nhật danh sách còn 5 người");
        a.send(Packet.of("TEXT", Config.ALL, "vẫn chạy"));
        check(
            outsider.next("TEXT").text().equals("vẫn chạy"),
            "Server tiếp tục chạy sau khi một client tắt");
      } finally {
        for (Peer p : peers) p.close();
      }
    }
    System.out.println(
        "ALL " + checks + " CHECKS PASSED. Hardware/LAN tests are manual; see docs/KIEM_THU.md.");
  }

  private static void codecChecks() throws Exception {
    Packet original = new Packet("TEXT", "Hiếu", "Biên", "1", "Xin chào 😀❤️", null);
    byte[] encoded = PacketCodec.encode(original);
    check(
        PacketCodec.decode(encoded, encoded.length).text().equals(original.text()),
        "Codec Unicode/emoji");
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    PacketCodec.write(new DataOutputStream(bytes), original);
    PacketCodec.write(new DataOutputStream(bytes), original);
    DataInputStream stream = new DataInputStream(new ByteArrayInputStream(bytes.toByteArray()));
    check(
        PacketCodec.read(stream).text().equals(PacketCodec.read(stream).text()),
        "Hai packet liên tiếp không dính thông điệp");
    try {
      PacketCodec.decode(new byte[] {0, 0, 0, 0}, 4);
      throw new AssertionError("Nhận sai magic");
    } catch (IOException expected) {
      check(true, "Từ chối sai magic");
    }
    byte[] oversized = encoded.clone();
    java.nio.ByteBuffer.wrap(oversized, oversized.length - 4, 4).putInt(Integer.MAX_VALUE);
    try {
      PacketCodec.decode(oversized, oversized.length);
      throw new AssertionError("Nhận payload quá lớn");
    } catch (IOException expected) {
      check(true, "Từ chối payload khai báo quá lớn trước khi cấp phát bộ nhớ");
    }
  }
}
