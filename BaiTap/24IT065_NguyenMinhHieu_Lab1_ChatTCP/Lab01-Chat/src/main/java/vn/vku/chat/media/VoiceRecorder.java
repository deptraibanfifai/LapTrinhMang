package vn.vku.chat.media;

import java.io.*;
import javax.sound.sampled.*;

/** Ghi tối đa 10 giây rồi trả file WAV, tách biệt với gọi thoại trực tiếp. */
public final class VoiceRecorder implements AutoCloseable {
  private volatile TargetDataLine line;
  private volatile boolean cancelled;

  public byte[] record() throws Exception {
    if (cancelled) throw new IOException("Đã hủy ghi âm");
    TargetDataLine mic = AudioSystem.getTargetDataLine(AudioService.FORMAT);
    synchronized (this) {
      if (cancelled) throw new IOException("Đã hủy ghi âm");
      mic.open(AudioService.FORMAT);
      line = mic;
      mic.start();
    }
    ByteArrayOutputStream pcm = new ByteArrayOutputStream();
    try {
      byte[] b = new byte[3200];
      int remaining = 320000; // 10 s × 16000 × 2
      while (!cancelled && remaining > 0) {
        int n = mic.read(b, 0, Math.min(remaining, b.length));
        if (n <= 0) break;
        pcm.write(b, 0, n);
        remaining -= n;
      }
    } finally {
      synchronized (this) {
        mic.stop();
        mic.close();
        line = null;
      }
    }
    if (cancelled) throw new IOException("Đã hủy ghi âm");
    byte[] data = pcm.toByteArray();
    ByteArrayOutputStream wav = new ByteArrayOutputStream();
    try (AudioInputStream stream =
        new AudioInputStream(
            new ByteArrayInputStream(data), AudioService.FORMAT, data.length / 2)) {
      AudioSystem.write(stream, AudioFileFormat.Type.WAVE, wav);
    }
    return wav.toByteArray();
  }

  @Override
  public synchronized void close() {
    cancelled = true;
    if (line != null) {
      line.stop();
      line.close();
      line = null;
    }
  }

  public static void play(java.nio.file.Path path) throws Exception {
    try (AudioInputStream stream = AudioSystem.getAudioInputStream(path.toFile())) {
      Clip clip = AudioSystem.getClip();
      clip.addLineListener(
          e -> {
            if (e.getType() == LineEvent.Type.STOP) clip.close();
          });
      try {
        clip.open(stream);
        clip.start();
      } catch (Exception e) {
        clip.close();
        throw e;
      }
    }
  }
}
