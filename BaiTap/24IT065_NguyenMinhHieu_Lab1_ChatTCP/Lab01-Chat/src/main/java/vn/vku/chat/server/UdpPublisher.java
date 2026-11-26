package vn.vku.chat.server;

import java.io.*;
import java.net.*;
import vn.vku.chat.common.*;

/** Server phát text bằng UDP thật; không giả lập multicast bằng vòng lặp TCP. */
public final class UdpPublisher implements AutoCloseable {
  private final MulticastSocket socket;
  private final NetworkInterface network;

  public UdpPublisher(NetworkInterface network) throws IOException {
    this.network = network;
    socket = new MulticastSocket(new InetSocketAddress(NetUtil.ipv4(network), 0));
    socket.setNetworkInterface(network);
    socket.setTimeToLive(1);
    socket.setBroadcast(true);
    socket.setOption(StandardSocketOptions.IP_MULTICAST_LOOP, true);
  }

  public synchronized void publish(Packet p, boolean group) throws IOException {
    byte[] b = PacketCodec.encode(p);
    if (b.length > Config.MAX_UDP) throw new IOException("Tin UDP quá dài");
    if (group) {
      socket.send(
          new DatagramPacket(
              b, b.length, InetAddress.getByName(Config.GROUP), Config.MULTICAST_PORT));
    } else {
      var addresses = NetUtil.broadcasts(network);
      if (addresses.isEmpty()) throw new IOException("Card mạng không có địa chỉ broadcast IPv4");
      for (InetAddress a : addresses)
        socket.send(new DatagramPacket(b, b.length, a, Config.BROADCAST_PORT));
    }
  }

  @Override
  public void close() {
    socket.close();
  }
}
