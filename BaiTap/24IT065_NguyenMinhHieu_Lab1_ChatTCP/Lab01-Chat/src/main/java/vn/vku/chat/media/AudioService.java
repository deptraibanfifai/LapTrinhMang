package vn.vku.chat.media;

import java.util.Arrays;
import java.util.concurrent.*;
import java.util.function.Consumer;
import javax.sound.sampled.*;
import vn.vku.chat.common.NetUtil;

/** Gọi thoại: PCM mono 16 kHz, 16 bit; mỗi gói khoảng 100 ms = 3200 byte. */
public final class AudioService implements AutoCloseable {
  public static final AudioFormat FORMAT = new AudioFormat(16000, 16, 1, true, false);
  private volatile boolean running;
  private TargetDataLine mic;
  private SourceDataLine speaker;
  private final BlockingQueue<byte[]> incoming = new ArrayBlockingQueue<>(8);

  public synchronized void start(Consumer<byte[]> send, Consumer<String> error)
      throws LineUnavailableException {
    if (running) return;
    try {
      mic = AudioSystem.getTargetDataLine(FORMAT);
      mic.open(FORMAT);
      speaker = AudioSystem.getSourceDataLine(FORMAT);
      speaker.open(FORMAT);
      mic.start();
      speaker.start();
      running = true;
    } catch (LineUnavailableException | IllegalArgumentException e) {
      close();
      throw e;
    }
    TargetDataLine input = mic;
    SourceDataLine output = speaker;
    NetUtil.daemon(
        "mic-capture",
        () -> {
          try {
            byte[] b = new byte[3200];
            while (running && input.isOpen()) {
              int n = input.read(b, 0, b.length);
              if (n > 0) send.accept(Arrays.copyOf(b, n));
            }
          } catch (RuntimeException e) {
            if (running) error.accept("Micro: " + e.getMessage());
          }
        });
    NetUtil.daemon(
        "audio-playback",
        () -> {
          try {
            while (running && output.isOpen()) {
              byte[] b = incoming.poll(200, TimeUnit.MILLISECONDS);
              if (b != null) output.write(b, 0, b.length);
            }
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
          } catch (RuntimeException e) {
            if (running) error.accept("Loa: " + e.getMessage());
          }
        });
  }

  public void receive(byte[] bytes) {
    if (running && bytes.length % 2 == 0) incoming.offer(bytes);
  }

  @Override
  public synchronized void close() {
    running = false;
    if (mic != null) {
      mic.stop();
      mic.close();
      mic = null;
    }
    if (speaker != null) {
      speaker.stop();
      speaker.flush();
      speaker.close();
      speaker = null;
    }
    incoming.clear();
  }
}
