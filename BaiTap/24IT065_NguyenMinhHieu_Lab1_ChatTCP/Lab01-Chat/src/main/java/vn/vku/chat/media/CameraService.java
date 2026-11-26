package vn.vku.chat.media;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.lang.reflect.Method;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import javax.imageio.ImageIO;
import vn.vku.chat.common.*;

/** Webcam thật, resize 320×240, mã hóa JPEG khoảng 8 fps rồi gửi qua TCP. */
public final class CameraService implements AutoCloseable {
  private volatile boolean running;
  private Object webcam;

  private static boolean hasWebcamApi() {
    try {
      Class.forName("com.github.sarxos.webcam.Webcam");
      return true;
    } catch (ClassNotFoundException e) {
      return false;
    }
  }

  @SuppressWarnings("unchecked")
  public synchronized void start(
      Consumer<byte[]> send, Consumer<BufferedImage> preview, Consumer<String> error)
      throws Exception {
    if (!hasWebcamApi()) {
      throw new IOException("Thiếu thư viện webcam-capture trong classpath");
    }

    Class<?> webcamClass = Class.forName("com.github.sarxos.webcam.Webcam");
    Method getDefault = webcamClass.getMethod("getDefault", long.class, TimeUnit.class);
    webcam = getDefault.invoke(null, 5L, TimeUnit.SECONDS);
    if (webcam == null) throw new IOException("Không tìm thấy webcam");

    Dimension small = new Dimension(320, 240);
    Method getViewSizes = webcamClass.getMethod("getViewSizes");
    Object viewSizes = getViewSizes.invoke(webcam);
    if (viewSizes instanceof java.util.List<?>) {
      for (Object d : (java.util.List<?>) viewSizes) {
        if (d instanceof Dimension && d.equals(small)) {
          Method setViewSize = webcamClass.getMethod("setViewSize", Dimension.class);
          setViewSize.invoke(webcam, small);
          break;
        }
      }
    } else if (viewSizes instanceof Object[]) {
      for (Object d : (Object[]) viewSizes) {
        if (d instanceof Dimension && d.equals(small)) {
          Method setViewSize = webcamClass.getMethod("setViewSize", Dimension.class);
          setViewSize.invoke(webcam, small);
          break;
        }
      }
    }

    Method open = webcamClass.getMethod("open");
    if (!(Boolean) open.invoke(webcam)) {
      throw new IOException("Không mở được webcam; có thể camera đang được ứng dụng khác dùng");
    }

    running = true;
    final Object camera = webcam;
    NetUtil.daemon(
        "webcam-capture",
        () -> {
          try {
            Method isOpen = camera.getClass().getMethod("isOpen");
            Method getImage = camera.getClass().getMethod("getImage");
            while (running && Boolean.TRUE.equals(isOpen.invoke(camera))) {
              Object image = getImage.invoke(camera);
              if (image instanceof BufferedImage) {
                BufferedImage source = (BufferedImage) image;
                BufferedImage frame = new BufferedImage(320, 240, BufferedImage.TYPE_INT_RGB);
                Graphics2D g = frame.createGraphics();
                g.drawImage(source, 0, 0, 320, 240, null);
                g.dispose();

                ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                ImageIO.write(frame, "jpg", bytes);
                if (bytes.size() <= Config.MAX_VIDEO) send.accept(bytes.toByteArray());
                preview.accept(frame);
              }
              Thread.sleep(125);
            }
          } catch (Exception e) {
            if (running) error.accept("Webcam: " + e.getMessage());
          }
        });
  }

  @Override
  public synchronized void close() {
    running = false;
    if (webcam != null) {
      try {
        Method close = webcam.getClass().getMethod("close");
        close.invoke(webcam);
      } catch (Exception ignored) {
      }
      webcam = null;
    }
  }
}
