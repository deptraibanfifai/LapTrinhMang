package vn.vku.chat.common;

/** Một thông điệp có header và payload nhị phân. Không dùng Java serialization. */
public record Packet(
    String type, String sender, String target, String id, String text, byte[] data) {
  public Packet {
    if (type == null || sender == null || target == null || id == null || text == null)
      throw new IllegalArgumentException("Header không được null");
    if (data == null) data = new byte[0];
  }

  public static Packet of(String type, String target, String text) {
    return new Packet(type, "", target, "", text, null);
  }

  public Packet from(String name) {
    return new Packet(type, name, target, id, text, data);
  }

  public boolean media() {
    return type.equals("AUDIO") || type.equals("VIDEO");
  }
}
