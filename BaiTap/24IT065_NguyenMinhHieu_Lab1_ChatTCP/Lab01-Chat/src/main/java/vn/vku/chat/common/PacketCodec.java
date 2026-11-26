package vn.vku.chat.common;

import java.io.*;
import java.nio.charset.StandardCharsets;

/** TCP là luồng byte: mỗi chuỗi/payload đều ghi độ dài trước rồi mới ghi dữ liệu. */
public final class PacketCodec {
  private static final int MAGIC = 0x564B5531; // VKU1, phiên bản giao thức

  private PacketCodec() {}

  public static void write(DataOutputStream out, Packet p) throws IOException {
    if (p.data().length > payloadLimit(p.type())) throw new IOException("Payload quá lớn");
    out.writeInt(MAGIC);
    for (String s : new String[] {p.type(), p.sender(), p.target(), p.id(), p.text()}) {
      byte[] bytes = s.getBytes(StandardCharsets.UTF_8);
      if (bytes.length > 8192) throw new IOException("Header quá dài");
      out.writeInt(bytes.length);
      out.write(bytes);
    }
    out.writeInt(p.data().length);
    out.write(p.data());
    out.flush();
  }

  public static Packet read(DataInputStream in) throws IOException {
    if (in.readInt() != MAGIC) throw new IOException("Sai phiên bản giao thức");
    String[] h = new String[5];
    for (int i = 0; i < h.length; i++) {
      int n = in.readInt();
      if (n < 0 || n > 8192) throw new IOException("Độ dài header không hợp lệ");
      byte[] b = new byte[n];
      in.readFully(b);
      h[i] = new String(b, StandardCharsets.UTF_8);
    }
    int n = in.readInt();
    if (n < 0 || n > payloadLimit(h[0])) throw new IOException("Độ dài payload không hợp lệ");
    byte[] data = new byte[n];
    in.readFully(data);
    return new Packet(h[0], h[1], h[2], h[3], h[4], data);
  }

  public static byte[] encode(Packet p) throws IOException {
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    write(new DataOutputStream(bytes), p);
    return bytes.toByteArray();
  }

  public static Packet decode(byte[] bytes, int length) throws IOException {
    DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes, 0, length));
    Packet p = read(in);
    if (in.available() != 0) throw new IOException("Datagram có dữ liệu thừa");
    return p;
  }

  private static int payloadLimit(String type) {
    return switch (type) {
      case "FILE" -> Config.MAX_FILE;
      case "VOICE" -> 1_000_000;
      case "VIDEO" -> Config.MAX_VIDEO;
      case "AUDIO" -> 6400;
      default -> 0;
    };
  }
}
