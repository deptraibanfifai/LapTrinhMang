package vn.vku.chat.server;

import java.io.*;
import java.net.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import vn.vku.chat.common.*;

/** Mỗi client có luồng đọc và hàng đợi ghi riêng; client chậm không khóa cả server. */
final class ClientSession implements Runnable, AutoCloseable {
  private final ChatServer server;
  private final Socket socket;
  private final BlockingQueue<Packet> outgoing = new ArrayBlockingQueue<>(16);
  private final AtomicBoolean closed = new AtomicBoolean();
  private Thread writer;
  private String name;
  volatile boolean groupJoined;

  ClientSession(ChatServer server, Socket socket) {
    this.server = server;
    this.socket = socket;
  }

  String name() {
    return name;
  }

  @Override
  public void run() {
    try {
      socket.setTcpNoDelay(true);
      socket.setSoTimeout(10000);
      DataInputStream in = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
      DataOutputStream out =
          new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));
      Packet hello = PacketCodec.read(in);
      if (!hello.type().equals("HELLO") || !hello.text().matches("[\\p{L}\\p{N}_-]{1,20}")) {
        PacketCodec.write(out, Packet.of("ERROR", "", "Tên gồm 1–20 chữ, số, _ hoặc -"));
        return;
      }
      name = hello.text();
      if (!server.register(this, out)) return;
      socket.setSoTimeout(0);
      writer =
          NetUtil.daemon(
              "write-" + name,
              () -> {
                try {
                  while (!closed.get()) PacketCodec.write(out, outgoing.take());
                } catch (IOException | InterruptedException e) {
                  close();
                }
              });
      server.usersChanged();
      while (!closed.get()) server.handle(this, PacketCodec.read(in));
    } catch (IOException e) {
      if (!closed.get()) server.log("Ngắt " + name + ": " + e.getMessage());
    } finally {
      close();
    }
  }

  void send(Packet p) {
    if (closed.get()) return;
    if (!outgoing.offer(p) && !p.media()) close(); // video/audio có thể bỏ frame khi nghẽn
  }

  @Override
  public void close() {
    if (!closed.compareAndSet(false, true)) return;
    try {
      socket.close();
    } catch (IOException ignored) {
    }
    if (writer != null) writer.interrupt();
    outgoing.clear();
    server.remove(this);
  }
}
