package vn.vku.chat.client;

import java.io.*;
import java.net.*;
import java.util.function.Consumer;
import vn.vku.chat.common.*;

/** Socket broadcast luôn lắng nghe; socket multicast chỉ join khi người dùng chọn. */
final class UdpReceiver implements AutoCloseable {
  private final NetworkInterface network;
  private final String serverRun;
  private final InetAddress serverAddress;
  private final Consumer<Packet> receive;
  private final Consumer<String> errors;
  private DatagramSocket broadcast;
  private MulticastSocket group;

  UdpReceiver(
      NetworkInterface network,
      String serverRun,
      InetAddress serverAddress,
      Consumer<Packet> receive,
      Consumer<String> errors) {
    this.network = network;
    this.serverRun = serverRun;
    this.serverAddress = serverAddress;
    this.receive = receive;
    this.errors = errors;
  }

  void start() throws IOException {
    DatagramSocket s = new DatagramSocket(null);
    try {
      s.setReuseAddress(true);
      s.bind(new InetSocketAddress(Config.BROADCAST_PORT));
    } catch (IOException e) {
      s.close();
      throw e;
    }
    broadcast = s;
    listen(s, "BROADCAST");
  }

  synchronized void join() throws IOException {
    if (group != null) return;
    MulticastSocket s = new MulticastSocket(null);
    try {
      s.setReuseAddress(true);
      s.bind(new InetSocketAddress(Config.MULTICAST_PORT));
      s.setNetworkInterface(network);
      s.joinGroup(new InetSocketAddress(Config.GROUP, Config.MULTICAST_PORT), network);
    } catch (IOException e) {
      s.close();
      throw e;
    }
    group = s;
    listen(s, "MULTICAST");
  }

  synchronized void leave() {
    if (group == null) return;
    try {
      group.leaveGroup(new InetSocketAddress(Config.GROUP, Config.MULTICAST_PORT), network);
    } catch (IOException ignored) {
    }
    group.close();
    group = null;
  }

  private void listen(DatagramSocket socket, String expectedType) {
    NetUtil.daemon(
        "receive-" + expectedType,
        () -> {
          byte[] buffer = new byte[Config.MAX_UDP];
          while (!socket.isClosed()) {
            try {
              DatagramPacket datagram = new DatagramPacket(buffer, buffer.length);
              socket.receive(datagram);
              // Lọc phiên server và địa chỉ nguồn, tránh lẫn buổi demo khác trong cùng LAN.
              if (!datagram.getAddress().equals(serverAddress)) continue;
              Packet p;
              try {
                p = PacketCodec.decode(datagram.getData(), datagram.getLength());
              } catch (IOException malformed) {
                continue;
              }
              if (p.type().equals(expectedType) && p.id().equals(serverRun)) receive.accept(p);
            } catch (IOException e) {
              if (!socket.isClosed()) errors.accept("UDP: " + e.getMessage());
              break;
            }
          }
        });
  }

  @Override
  public synchronized void close() {
    leave();
    if (broadcast != null) broadcast.close();
  }
}
