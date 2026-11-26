package vn.vku.chat.client;

import java.io.*;
import java.net.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import vn.vku.chat.common.*;

/** Tầng mạng không phụ thuộc Swing. Giao diện nhận sự kiện qua Listener. */
public final class ChatClient implements AutoCloseable {
  public interface Listener {
    void onPacket(Packet p);

    void onDisconnected(String reason);
  }

  private final Socket socket = new Socket();
  private final Listener listener;
  private final BlockingQueue<Packet> outgoing = new ArrayBlockingQueue<>(16);
  private final AtomicBoolean closed = new AtomicBoolean();
  private final DataInputStream in;
  private final DataOutputStream out;
  private UdpReceiver udp;
  private Thread writer;
  private boolean started;
  public final String name;

  public ChatClient(String host, int port, String name, NetworkInterface network, Listener listener)
      throws IOException {
    this.name = name;
    this.listener = listener;
    try {
      socket.connect(new InetSocketAddress(host, port), 5000);
      socket.setTcpNoDelay(true);
      socket.setSoTimeout(10000);
      in = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
      out = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));
      PacketCodec.write(out, Packet.of("HELLO", "", name));
      Packet welcome = PacketCodec.read(in);
      if (!welcome.type().equals("WELCOME")) throw new IOException(welcome.text());
      socket.setSoTimeout(0);
      // Khi TCP đi qua loopback, UDP vẫn phát ra card LAN do server chọn.
      InetAddress source =
          socket.getInetAddress().isLoopbackAddress()
              ? NetUtil.ipv4(network)
              : socket.getInetAddress();
      udp =
          new UdpReceiver(
              network,
              welcome.id(),
              source,
              listener::onPacket,
              message -> listener.onPacket(Packet.of("ERROR", "", message)));
      try {
        udp.start();
      } catch (IOException e) {
        listener.onPacket(
            Packet.of("ERROR", "", "TCP vẫn dùng được. Không mở được UDP: " + e.getMessage()));
      }
    } catch (IOException | RuntimeException e) {
      try {
        socket.close();
      } catch (IOException ignored) {
      }
      throw e;
    }
  }

  /** Gọi sau khi giao diện đã giữ tham chiếu client, tránh callback quá sớm. */
  public synchronized void start() {
    if (started || closed.get()) return;
    started = true;
    writer =
        NetUtil.daemon(
            "tcp-client-writer",
            () -> {
              try {
                while (!closed.get()) PacketCodec.write(out, outgoing.take());
              } catch (IOException | InterruptedException e) {
                disconnect("Không gửi được: " + e.getMessage());
              }
            });
    NetUtil.daemon(
        "tcp-client-reader",
        () -> {
          try {
            while (!closed.get()) listener.onPacket(PacketCodec.read(in));
          } catch (IOException e) {
            disconnect("Server đã ngắt kết nối: " + e.getMessage());
          }
        });
  }

  public void send(Packet p) throws IOException {
    if (closed.get()) throw new IOException("Chưa kết nối server");
    if (!outgoing.offer(p) && !p.media())
      throw new IOException("Hàng đợi gửi đang đầy, hãy thử lại");
  }

  public void joinGroup(boolean join) throws IOException {
    if (join) {
      udp.join();
      send(Packet.of("GROUP_JOIN", "", ""));
    } else {
      udp.leave();
      send(Packet.of("GROUP_LEAVE", "", ""));
    }
  }

  public boolean isOpen() {
    return !closed.get();
  }

  private void disconnect(String reason) {
    if (!closed.compareAndSet(false, true)) return;
    if (udp != null) udp.close();
    try {
      socket.close();
    } catch (IOException ignored) {
    }
    if (writer != null) writer.interrupt();
    outgoing.clear();
    listener.onDisconnected(reason);
  }

  @Override
  public void close() {
    disconnect("Đã ngắt kết nối");
  }
}
