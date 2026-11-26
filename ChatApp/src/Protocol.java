import javax.sound.sampled.AudioFormat;
import java.io.DataInputStream;
import java.io.IOException;

/** Cac quy uoc du lieu dung chung giua client va server. */
public final class Protocol {
    public static final int DEFAULT_PORT = 5000;
    public static final int MAX_FILE_SIZE = 20 * 1024 * 1024;
    public static final int MAX_MESSAGE_LENGTH = 4000;
    public static final String EVERYONE = "*";

    // ========== Group Chat ==========
    public static final String GROUP_PREFIX = "grp_";
    public static final int    MAX_GROUP_NAME_LEN = 30;
    public static final int    MAX_GROUP_MEMBERS  = 20;
    public static final String GROUP_DISPLAY_PREFIX = "👥 ";

    // ========== Call (Audio/Video) ==========
    public static final int    AUDIO_BUFFER_SIZE  = 4096;
    public static final int    VIDEO_MAX_FRAME    = 1024 * 1024; // 1 MB / frame
    public static final int    VIDEO_FPS_MS       = 220;         // ~4.5 fps / giây
    public static final int    VIDEO_WIDTH        = 640;
    public static final int    VIDEO_HEIGHT       = 360;

    public static final String CALL_CTL_MUTE_MIC   = "MUTE_MIC";
    public static final String CALL_CTL_UNMUTE_MIC = "UNMUTE_MIC";
    public static final String CALL_CTL_MUTE_SPK   = "MUTE_SPK";
    public static final String CALL_CTL_UNMUTE_SPK = "UNMUTE_SPK";
    public static final String CALL_CTL_VIDEO_ON   = "VIDEO_ON";
    public static final String CALL_CTL_VIDEO_OFF  = "VIDEO_OFF";

    public static final String CALL_SIDE_CALLER = "CALLER";
    public static final String CALL_SIDE_CALLEE = "CALLEE";
    public static final String CALL_STREAM_AUDIO = "AUDIO";
    public static final String CALL_STREAM_VIDEO = "VIDEO";

    private static final AudioFormat AUDIO_FMT = new AudioFormat(
            44100f, 16, 1, true, false);

    private Protocol() { }

    public static AudioFormat getAudioFormat() { return AUDIO_FMT; }

    public static byte[] readFile(DataInputStream input) throws IOException {
        int size = input.readInt();
        if (size < 0 || size > MAX_FILE_SIZE) {
            throw new IOException("Kich thuoc file khong hop le (toi da 20 MB).");
        }
        byte[] bytes = new byte[size];
        input.readFully(bytes);
        return bytes;
    }

    public static String safeFileName(String name) {
        String leaf = name.replace('\\', '/');
        leaf = leaf.substring(leaf.lastIndexOf('/') + 1);
        leaf = leaf.replaceAll("[^\\p{L}\\p{N}._ -]", "_");
        if (leaf.length() > 100) leaf = leaf.substring(leaf.length() - 100);
        return leaf.isEmpty() ? "file" : leaf;
    }

    public static String safeGroupName(String name) {
        if (name == null) return "Nhóm mới";
        String s = name.trim().replaceAll("[^\\p{L}\\p{N} _\\-\\.]", "_");
        if (s.isEmpty()) s = "Nhóm mới";
        if (s.length() > MAX_GROUP_NAME_LEN) s = s.substring(0, MAX_GROUP_NAME_LEN);
        return s;
    }

    public static boolean isGroupId(String id) {
        return id != null && id.startsWith(GROUP_PREFIX);
    }

    public static String groupDisplayName(String groupName) {
        return GROUP_DISPLAY_PREFIX + groupName;
    }
}
