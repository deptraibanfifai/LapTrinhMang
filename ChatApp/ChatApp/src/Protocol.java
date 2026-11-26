import java.io.DataInputStream;
import java.io.IOException;

public final class Protocol {
    public static final int DEFAULT_PORT = 5000;
    public static final long MAX_FILE_SIZE = 2L * 1024 * 1024 * 1024; // 2GB
    public static final int MAX_MESSAGE_LENGTH = 4000;
    public static final String EVERYONE = "";
    public static final int CHUNK_SIZE = 64 * 1024;

    // Các trạng thái
    public static final String STATUS_ONLINE = " Online";
    public static final String STATUS_AWAY = " Away";
    public static final String STATUS_BUSY = " Busy";

    private Protocol() { }

    public static String safeFileName(String name) {
        String leaf = name.replace('\\', '/');
        leaf = leaf.substring(leaf.lastIndexOf('/') + 1);
        leaf = leaf.replaceAll("[^\\p{L}\\p{N}._ -]", "_");
        if (leaf.length() > 100) leaf = leaf.substring(leaf.length() - 100);
        return leaf.isEmpty() ? "file" : leaf;
    }
}