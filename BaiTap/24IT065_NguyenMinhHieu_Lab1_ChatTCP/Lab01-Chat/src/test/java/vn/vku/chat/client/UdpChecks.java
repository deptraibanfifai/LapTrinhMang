package vn.vku.chat.client;

import java.io.*;
import java.net.*;
import java.util.concurrent.*;
import vn.vku.chat.common.*;
import vn.vku.chat.server.ChatServer;

/** Test UDP thật trên card chỉ định. Thất bại nếu mạng/firewall không chuyển datagram. */
public final class UdpChecks {
  public static void main(String[] args) throws Exception {
    NetworkInterface nic = NetUtil.choose(args.length > 0 ? args[0] : "");
    try (ChatServer server = new ChatServer(System.out::println)) {
      server.start(0, nic);
      BlockingQueue<Packet> eventsA = new LinkedBlockingQueue<>(),
          eventsB = new LinkedBlockingQueue<>();
      ChatClient.Listener listenerA = listener(eventsA), listenerB = listener(eventsB);
      try (ChatClient a = new ChatClient("127.0.0.1", server.port(), "UdpA", nic, listenerA);
          ChatClient b = new ChatClient("127.0.0.1", server.port(), "UdpB", nic, listenerB)) {
        a.start();
        b.start();
        a.joinGroup(true);
        expect(eventsA, "INFO");
        a.send(Packet.of("UDP_GROUP", Config.ALL, "multicast-test"));
        expect(eventsA, "MULTICAST");
        reject(eventsB, "MULTICAST");
        System.out.println("PASS multicast chỉ đến client đã join");
        b.joinGroup(true);
        expect(eventsB, "INFO");
        a.send(Packet.of("UDP_GROUP", Config.ALL, "two-members"));
        expect(eventsA, "MULTICAST");
        expect(eventsB, "MULTICAST");
        System.out.println("PASS cả hai thành viên nhận multicast");
        b.joinGroup(false);
        expect(eventsB, "INFO");
        a.send(Packet.of("UDP_GROUP", Config.ALL, "after-leave"));
        expect(eventsA, "MULTICAST");
        reject(eventsB, "MULTICAST");
        System.out.println("PASS leaveGroup dừng nhận multicast");
        if (NetUtil.broadcasts(nic).isEmpty()) {
          System.out.println(
              "SKIP broadcast: card không có địa chỉ broadcast IPv4. Kiểm tra lại trên LAN.");
        } else {
          a.send(Packet.of("UDP_BROADCAST", Config.ALL, "broadcast-test"));
          expect(eventsA, "BROADCAST");
          expect(eventsB, "BROADCAST");
          System.out.println("PASS cả hai client nhận broadcast dù B đã leave multicast");
        }
      }
    }
  }

  private static ChatClient.Listener listener(BlockingQueue<Packet> q) {
    return new ChatClient.Listener() {
      @Override
      public void onPacket(Packet p) {
        q.offer(p);
      }

      @Override
      public void onDisconnected(String reason) {}
    };
  }

  private static void expect(BlockingQueue<Packet> q, String type) throws Exception {
    long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(4);
    while (System.nanoTime() < until) {
      Packet p = q.poll(100, TimeUnit.MILLISECONDS);
      if (p != null && p.type().equals(type)) return;
      if (p != null && p.type().equals("ERROR")) throw new IOException(p.text());
    }
    throw new AssertionError(
        "Không nhận được " + type + "; kiểm tra card mạng, firewall và hỗ trợ multicast/broadcast");
  }

  private static void reject(BlockingQueue<Packet> q, String type) throws Exception {
    long until = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(500);
    while (System.nanoTime() < until) {
      Packet p = q.poll(50, TimeUnit.MILLISECONDS);
      if (p != null && p.type().equals(type))
        throw new AssertionError("Client chưa join/đã leave vẫn nhận " + type);
    }
  }
}
