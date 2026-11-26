package vn.vku.chat.common;

import java.net.*;
import java.util.*;

/** Chọn rõ card mạng, tránh multicast đi nhầm sang VPN/VirtualBox. */
public final class NetUtil {
  private NetUtil() {}

  public static List<NetworkInterface> interfaces() throws SocketException {
    List<NetworkInterface> list = new ArrayList<>();
    for (NetworkInterface n : Collections.list(NetworkInterface.getNetworkInterfaces())) {
      if (n.isUp()
          && n.supportsMulticast()
          && n.inetAddresses().anyMatch(a -> a instanceof Inet4Address)) list.add(n);
    }
    return list;
  }

  public static NetworkInterface choose(String name) throws SocketException {
    if (name != null && !name.isBlank()) {
      NetworkInterface n = NetworkInterface.getByName(name);
      if (n == null || !n.isUp()) throw new SocketException("Không tìm thấy card mạng: " + name);
      return n;
    }
    List<NetworkInterface> list = interfaces();
    if (list.isEmpty()) throw new SocketException("Không có card IPv4 hỗ trợ multicast");
    return list.get(0);
  }

  public static List<InetAddress> broadcasts(NetworkInterface n) {
    return n.getInterfaceAddresses().stream()
        .map(InterfaceAddress::getBroadcast)
        .filter(Objects::nonNull)
        .distinct()
        .toList();
  }

  public static InetAddress ipv4(NetworkInterface n) throws SocketException {
    return n.inetAddresses()
        .filter(a -> a instanceof Inet4Address)
        .findFirst()
        .orElseThrow(() -> new SocketException("Card mạng không có IPv4"));
  }

  public static Thread daemon(String name, Runnable task) {
    Thread t = new Thread(task, name);
    t.setDaemon(true);
    t.start();
    return t;
  }
}
