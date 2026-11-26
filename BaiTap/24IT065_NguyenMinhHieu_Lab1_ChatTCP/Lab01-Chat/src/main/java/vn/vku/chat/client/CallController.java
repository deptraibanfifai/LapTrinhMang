package vn.vku.chat.client;

import java.awt.image.BufferedImage;
import java.io.*;
import java.util.UUID;
import java.util.concurrent.*;
import javax.imageio.ImageIO;
import vn.vku.chat.common.*;
import vn.vku.chat.media.*;

/** Máy trạng thái cuộc gọi. Mọi phương thức điều khiển được gọi trên Swing EDT. */
final class CallController implements AutoCloseable {
  interface View {
    boolean confirmCall(String peer, boolean video);

    void callStatus(String text, boolean busy);

    void video(BufferedImage image, boolean local);

    void message(String text);
  }

  private final ChatClient client;
  private final View view;
  private final AudioService audio = new AudioService();
  private final CameraService camera = new CameraService();
  private final ExecutorService media =
      Executors.newSingleThreadExecutor(
          r -> {
            Thread t = new Thread(r, "media-lifecycle");
            t.setDaemon(true);
            return t;
          });
  private final BlockingQueue<Packet> videoFrames = new ArrayBlockingQueue<>(2);
  private volatile String id = "", peer = "";
  private volatile boolean active;
  private volatile boolean closed;

  CallController(ChatClient client, View view) {
    this.client = client;
    this.view = view;
    NetUtil.daemon(
        "video-decoder",
        () -> {
          while (!closed) {
            try {
              Packet p = videoFrames.poll(200, TimeUnit.MILLISECONDS);
              if (p == null || !active || !p.id().equals(id)) continue;
              BufferedImage frame = ImageIO.read(new ByteArrayInputStream(p.data()));
              if (frame != null && active && p.id().equals(id)) view.video(frame, false);
            } catch (IOException e) {
              view.message("Lỗi giải mã video: " + e.getMessage());
            } catch (InterruptedException e) {
              Thread.currentThread().interrupt();
              break;
            }
          }
        });
  }

  boolean busy() {
    return !id.isEmpty();
  }

  void invite(String target, boolean video) throws IOException {
    if (busy()) throw new IOException("Đang có cuộc gọi khác");
    id = UUID.randomUUID().toString();
    peer = target;
    try {
      send("CALL_INVITE", video ? "VIDEO" : "AUDIO", null);
    } catch (IOException e) {
      finish();
      throw e;
    }
    view.callStatus("Đang gọi " + peer + "…", true);
  }

  void control(Packet p) {
    if (p.type().equals("CALL_INVITE")) {
      if (busy()) {
        reject(p, "Đang bận");
        return;
      }
      id = p.id();
      peer = p.sender();
      view.callStatus("Cuộc gọi đến từ " + peer, true);
      if (view.confirmCall(peer, p.text().equals("VIDEO")) && p.id().equals(id)) {
        try {
          send("CALL_ACCEPT", "", null);
          startMedia(p.text().equals("VIDEO"));
        } catch (IOException e) {
          view.message(e.getMessage());
          finish();
        }
      } else if (p.id().equals(id)) {
        reject(p, "Đã từ chối");
        finish();
      }
      return;
    }
    if (!p.id().equals(id)) return;
    switch (p.type()) {
      case "CALL_ACCEPT" -> startMedia(p.text().equals("VIDEO"));
      case "CALL_REJECT", "CALL_END" -> {
        view.message("Cuộc gọi: " + p.text());
        finish();
      }
      default -> {}
    }
  }

  // Gọi ngoài EDT cho media để không xếp hàng hàng trăm sự kiện âm thanh/video trên giao diện.
  void receiveMedia(Packet p) {
    if (!active || !p.id().equals(id) || !p.sender().equals(peer)) return;
    if (p.type().equals("AUDIO")) audio.receive(p.data());
    else videoFrames.offer(p);
  }

  private void reject(Packet invitation, String reason) {
    try {
      client.send(
          new Packet("CALL_REJECT", "", invitation.sender(), invitation.id(), reason, null));
    } catch (IOException e) {
      view.message(e.getMessage());
    }
  }

  private void startMedia(boolean video) {
    active = true;
    String callId = id, target = peer;
    view.callStatus("Đang gọi " + target + (video ? " • video" : " • thoại"), true);
    media.submit(
        () -> {
          if (!active || !id.equals(callId)) return;
          try {
            audio.start(bytes -> stream("AUDIO", bytes, callId, target), view::message);
          } catch (Exception e) {
            view.message("Không mở được micro/loa: " + e.getMessage());
          }
          if (video && active && id.equals(callId)) {
            try {
              camera.start(
                  bytes -> stream("VIDEO", bytes, callId, target),
                  image -> {
                    if (active && id.equals(callId)) view.video(image, true);
                  },
                  view::message);
            } catch (Exception | LinkageError e) {
              camera.close();
              view.message("Không mở được camera: " + e.getMessage());
            }
          }
        });
  }

  private void stream(String type, byte[] bytes, String callId, String target) {
    if (!active || !id.equals(callId)) return;
    try {
      client.send(new Packet(type, "", target, callId, "", bytes));
    } catch (IOException e) {
      view.message(e.getMessage());
    }
  }

  private void send(String type, String text, byte[] data) throws IOException {
    client.send(new Packet(type, "", peer, id, text, data));
  }

  void hangup() {
    if (busy())
      try {
        send("CALL_END", "Đã kết thúc cuộc gọi", null);
      } catch (IOException e) {
        view.message(e.getMessage());
      }
    finish();
  }

  private void finish() {
    active = false;
    id = "";
    peer = "";
    videoFrames.clear();
    media.submit(
        () -> {
          audio.close();
          camera.close();
        });
    view.callStatus("Chưa có cuộc gọi", false);
  }

  @Override
  public void close() {
    if (closed) return;
    hangup();
    closed = true;
    media.shutdown();
  }
}
