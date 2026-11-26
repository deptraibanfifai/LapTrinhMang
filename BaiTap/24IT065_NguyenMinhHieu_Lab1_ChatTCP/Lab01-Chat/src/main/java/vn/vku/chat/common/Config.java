package vn.vku.chat.common;

/** Các thông số dùng chung: thay ở một nơi để client và server thống nhất. */
public final class Config {
  private Config() {}

  public static final int TCP_PORT = 5500;
  public static final int BROADCAST_PORT = 5501;
  public static final int MULTICAST_PORT = 5502;
  public static final String GROUP = "239.192.5.10";
  public static final int MAX_FILE = 20 * 1024 * 1024;
  public static final int MAX_TEXT = 2000;
  public static final int MAX_VIDEO = 300_000;
  public static final int MAX_UDP = 12_000;
  public static final String ALL = "*";
}
