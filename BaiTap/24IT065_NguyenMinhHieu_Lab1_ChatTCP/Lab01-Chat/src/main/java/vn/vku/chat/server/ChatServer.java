package vn.vku.chat.server;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import vn.vku.chat.common.*;

/** Điều phối client, xác định người gửi, chuyển tin và quản lý trạng thái cuộc gọi. */
public final class ChatServer implements AutoCloseable {
  private final ConcurrentMap<String, ClientSession> clients = new ConcurrentHashMap<>();
  private final Map<String, Call> calls = new HashMap<>();
  private final Consumer<String> logger;
  private final String runId = UUID.randomUUID().toString();
  private final ScheduledExecutorService timer =
      Executors.newSingleThreadScheduledExecutor(
          r -> {
            Thread t = new Thread(r, "call-timeouts");
            t.setDaemon(true);
            return t;
          });
  private volatile boolean running;
  private ServerSocket listener;
  private UdpPublisher udp;

  private static final class Call {
    final String id, a, b, kind;
    final long created = System.currentTimeMillis();
    boolean accepted;

    Call(Packet p) {
      id = p.id();
      a = p.sender();
      b = p.target();
      kind = p.text();
    }

    boolean includes(String who) {
      return a.equals(who) || b.equals(who);
    }

    String other(String who) {
      return a.equals(who) ? b : a;
    }
  }

  public ChatServer(Consumer<String> logger) {
    this.logger = logger;
  }

  public synchronized void start(int port, NetworkInterface network) throws IOException {
    if (running) throw new IOException("Server đã chạy");
    UdpPublisher publisher = new UdpPublisher(network);
    ServerSocket tcp = new ServerSocket();
    try {
      tcp.setReuseAddress(true);
      tcp.bind(new InetSocketAddress(port));
    } catch (IOException e) {
      publisher.close();
      tcp.close();
      throw e;
    }
    udp = publisher;
    listener = tcp;
    running = true;
    log(
        "TCP :"
            + tcp.getLocalPort()
            + " | UDP broadcast :5501 | multicast "
            + Config.GROUP
            + ":5502 | "
            + network.getName());
    NetUtil.daemon(
        "accept-clients",
        () -> {
          while (running) {
            try {
              Socket socket = tcp.accept();
              NetUtil.daemon("client-session", new ClientSession(this, socket));
            } catch (IOException e) {
              if (running) log("Accept lỗi: " + e.getMessage());
            }
          }
        });
    timer.scheduleAtFixedRate(this::expireCalls, 1, 1, TimeUnit.SECONDS);
  }

  public int port() {
    return listener.getLocalPort();
  }

  synchronized boolean register(ClientSession session, DataOutputStream out) throws IOException {
    if (!running || clients.putIfAbsent(session.name(), session) != null) {
      PacketCodec.write(out, Packet.of("ERROR", "", "Tên đã được dùng hoặc server đã dừng"));
      return false;
    }
    PacketCodec.write(
        out, new Packet("WELCOME", "SERVER", session.name(), runId, "Kết nối thành công", null));
    log("+ " + session.name() + " | online=" + clients.size());
    return true;
  }

  void usersChanged() {
    String users = String.join("\n", new TreeSet<>(clients.keySet()));
    broadcast(Packet.of("USERS", Config.ALL, users).from("SERVER"));
  }

  void handle(ClientSession session, Packet raw) {
    // Không tin trường sender do client gửi lên: luôn lấy danh tính của socket.
    Packet p = raw.from(session.name());
    try {
      switch (p.type()) {
        case "TEXT" -> {
          if (p.text().isBlank() || p.text().length() > Config.MAX_TEXT)
            throw new IOException("Text cần 1–2000 ký tự");
          route(p);
          log("TEXT " + p.sender() + " → " + p.target());
        }
        case "FILE", "VOICE" -> {
          if (p.data().length == 0) throw new IOException("Không gửi đính kèm rỗng");
          route(p);
          log(
              p.type()
                  + " "
                  + p.sender()
                  + " → "
                  + p.target()
                  + " ("
                  + p.data().length
                  + " bytes)");
        }
        case "GROUP_JOIN" -> {
          session.groupJoined = true;
          session.send(Packet.of("INFO", "", "Đã đăng ký nhóm multicast"));
        }
        case "GROUP_LEAVE" -> {
          session.groupJoined = false;
          session.send(Packet.of("INFO", "", "Đã rời nhóm multicast"));
        }
        case "UDP_BROADCAST", "UDP_GROUP" -> {
          if (p.text().isBlank() || p.text().length() > Config.MAX_TEXT)
            throw new IOException("Text cần 1–2000 ký tự");
          boolean group = p.type().equals("UDP_GROUP");
          if (group && !session.groupJoined) throw new IOException("Hãy tham gia nhóm trước");
          udp.publish(
              new Packet(
                  group ? "MULTICAST" : "BROADCAST", p.sender(), Config.ALL, runId, p.text(), null),
              group);
          log((group ? "UDP MULTICAST " : "UDP BROADCAST ") + p.sender());
        }
        case "CALL_INVITE", "CALL_ACCEPT", "CALL_REJECT", "CALL_END", "AUDIO", "VIDEO" -> signal(p);
        default -> throw new IOException("Loại thông điệp không hợp lệ: " + p.type());
      }
    } catch (IOException e) {
      session.send(Packet.of("ERROR", "", e.getMessage()).from("SERVER"));
    }
  }

  private void route(Packet p) throws IOException {
    if (p.target().equals(Config.ALL)) {
      broadcast(p);
      return;
    }
    ClientSession recipient = clients.get(p.target());
    if (recipient == null) throw new IOException("Người nhận đã offline");
    recipient.send(p);
    ClientSession sender = clients.get(p.sender());
    if (sender != recipient && sender != null) sender.send(p); // echo để hiển thị tin đã gửi
  }

  private void broadcast(Packet p) {
    clients.values().forEach(c -> c.send(p));
  }

  private synchronized void signal(Packet p) throws IOException {
    if (p.type().equals("CALL_INVITE")) {
      if (p.id().isBlank()
          || p.id().length() > 80
          || calls.containsKey(p.id())
          || !Set.of("AUDIO", "VIDEO").contains(p.text()))
        throw new IOException("Lời mời không hợp lệ");
      String caller = p.sender(), target = p.target();
      boolean busy =
          calls.values().stream().anyMatch(c -> c.includes(caller) || c.includes(target));
      if (p.sender().equals(p.target()) || !clients.containsKey(p.target()) || busy) {
        clients
            .get(p.sender())
            .send(
                new Packet(
                    "CALL_REJECT",
                    "SERVER",
                    p.sender(),
                    p.id(),
                    "Người nhận bận hoặc offline",
                    null));
        return;
      }
      calls.put(p.id(), new Call(p));
      clients.get(p.target()).send(p);
      log("Mời gọi " + p.sender() + " → " + p.target());
      return;
    }
    Call c = calls.get(p.id());
    if (c == null || !c.includes(p.sender()) || !c.other(p.sender()).equals(p.target())) return;
    if (p.type().equals("CALL_ACCEPT")) {
      if (!c.b.equals(p.sender()) || c.accepted) return;
      c.accepted = true;
      p = new Packet(p.type(), p.sender(), p.target(), p.id(), c.kind, null);
    } else if (p.type().equals("CALL_REJECT")) {
      if (!c.b.equals(p.sender()) || c.accepted) return;
      calls.remove(c.id);
    } else if (p.type().equals("CALL_END")) {
      calls.remove(c.id);
    } else if (!c.accepted || (p.type().equals("VIDEO") && !c.kind.equals("VIDEO"))) return;
    ClientSession other = clients.get(p.target());
    if (other != null) other.send(p);
  }

  private synchronized void expireCalls() {
    for (Call c : new ArrayList<>(calls.values())) {
      if (!c.accepted && System.currentTimeMillis() - c.created > 30000)
        endCall(c, "Hết thời gian chờ trả lời");
    }
  }

  private void endCall(Call c, String reason) {
    calls.remove(c.id);
    for (String who : List.of(c.a, c.b)) {
      ClientSession s = clients.get(who);
      if (s != null) s.send(new Packet("CALL_END", "SERVER", who, c.id, reason, null));
    }
  }

  synchronized void remove(ClientSession session) {
    if (session.name() == null || !clients.remove(session.name(), session)) return;
    for (Call c : new ArrayList<>(calls.values()))
      if (c.includes(session.name())) endCall(c, "Người kia đã mất kết nối");
    log("- " + session.name() + " | online=" + clients.size());
    usersChanged();
  }

  public void log(String text) {
    logger.accept(java.time.LocalTime.now().withNano(0) + "  " + text);
  }

  @Override
  public synchronized void close() {
    running = false;
    if (listener != null)
      try {
        listener.close();
      } catch (IOException ignored) {
      }
    for (ClientSession session : new ArrayList<>(clients.values())) session.close();
    if (udp != null) udp.close();
    timer.shutdownNow();
    calls.clear();
    log("Server đã dừng");
  }
}
