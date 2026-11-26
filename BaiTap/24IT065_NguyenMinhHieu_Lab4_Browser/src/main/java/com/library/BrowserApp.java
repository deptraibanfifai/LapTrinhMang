package com.library;

import javafx.application.Platform;
import javafx.concurrent.Worker;
import javafx.embed.swing.JFXPanel;
import javafx.scene.Scene;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.text.html.HTMLDocument;
import javax.swing.text.html.HTMLEditorKit;
import javax.swing.text.html.StyleSheet;
import java.awt.*;
import java.io.File;
import java.io.FileOutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Mini Browser có giao diện thật: GET / POST / HEAD / DOWNLOAD.
 * Đã tích hợp JavaFX WebView để hiển thị HTML5/CSS3/JS và cho phép tương tác.
 */
public class BrowserApp extends JFrame {
    private static final Color NAVY = new Color(23, 39, 70);
    private static final Color BLUE = new Color(44, 91, 208);
    private static final Color PURPLE = new Color(117, 76, 209);
    private static final Color ACCENT = new Color(255, 183, 77);
    private static final Color BACKGROUND = new Color(239, 243, 251);
    private static final Color MUTED = new Color(99, 114, 139);
    private static final Color BORDER = new Color(220, 227, 239);
    private static final Color OK = new Color(25, 135, 84);
    private static final Color WARN = new Color(214, 120, 0);
    private static final Color ERR = new Color(200, 45, 60);

    private static final int MAX_RENDER_CHARS = 400_000;
    private static final int MAX_SOURCE_CHARS = 1_000_000;

    private static final String K_STATUS = "Status";
    private static final String K_TIME = "Thời gian";
    private static final String K_LEN = "Độ dài";
    private static final String K_SIZE = "Dung lượng";
    private static final String K_P = "Thẻ <p>";
    private static final String K_DIV = "Thẻ <div>";
    private static final String K_SPAN = "Thẻ <span>";
    private static final String K_IMG = "Thẻ <img>";

    private final JTextField urlField = new JTextField("https://example.com");
    private final JComboBox<String> methodBox = new JComboBox<>(new String[]{"GET", "POST", "HEAD"});
    private final JTextArea postBody = new JTextArea("name=Hieu&message=Hello", 3, 40);
    private final JPanel postPanel = new JPanel(new BorderLayout());

    private final ActionButton goButton = new ActionButton("Gửi request", ACCENT, NAVY);
    private final ActionButton backButton = new ActionButton("←", new Color(255, 255, 255, 45), Color.WHITE);
    private final ActionButton downloadButton = new ActionButton("Tải xuống", OK, Color.WHITE);

    private final JLabel statusLabel = new JLabel("Nhập URL, chọn phương thức rồi bấm Gửi request");
    private final Map<String, StatCard> cards = new LinkedHashMap<>();

    // THAY THẾ JEditorPane BẰNG JFXPanel CHỨA TRÌNH DUYỆT THẬT
    private final JFXPanel jfxPanel = new JFXPanel();
    private WebEngine webEngine;

    private final JTextArea sourceArea = new JTextArea();
    private final JTextArea infoArea = new JTextArea();
    private final JTabbedPane tabs = new JTabbedPane();
    private final HTMLEditorKit htmlKit = new HTMLEditorKit(); // Vẫn dùng cho fallback text

    private final Deque<String> history = new ArrayDeque<>();
    private String currentUrl = null;
    private Result currentResult = null;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(12))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    public BrowserApp() {
        super("Mini Browser | Real Render (JavaFX WebView)");
        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        setMinimumSize(new Dimension(920, 680));
        setSize(1180, 820);
        setLocationRelativeTo(null);

        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(BACKGROUND);
        setContentPane(root);

        JPanel content = new JPanel(new BorderLayout(0, 14));
        content.setOpaque(false);
        content.setBorder(new EmptyBorder(16, 18, 14, 18));
        content.add(buildTop(), BorderLayout.NORTH);
        content.add(buildTabs(), BorderLayout.CENTER);

        root.add(content, BorderLayout.CENTER);
        root.add(buildStatusBar(), BorderLayout.SOUTH);

        goButton.addActionListener(e -> sendRequest());
        urlField.addActionListener(e -> sendRequest());
        methodBox.addActionListener(e -> updateMethodUI());
        backButton.addActionListener(e -> goBack());
        backButton.setEnabled(false);

        downloadButton.setEnabled(false);
        downloadButton.addActionListener(e -> downloadCurrentResource());

        initJavaFXBrowser();
        updateMethodUI();
    }

    // Khởi tạo JavaFX WebView (Trình duyệt thật)
    private void initJavaFXBrowser() {
        Platform.runLater(() -> {
            WebView webView = new WebView();
            webEngine = webView.getEngine();

            // Lắng nghe sự kiện load xong của trình duyệt
            webEngine.getLoadWorker().stateProperty().addListener((obs, oldState, newState) -> {
                if (newState == Worker.State.SUCCEEDED) {
                    // Cập nhật thanh URL nếu người dùng click link trong trang web
                    SwingUtilities.invokeLater(() -> urlField.setText(webEngine.getLocation()));
                }
            });

            jfxPanel.setScene(new Scene(webView));
        });
    }

    private JPanel buildTop() {
        JPanel top = new JPanel(new BorderLayout(0, 12));
        top.setOpaque(false);
        top.add(buildHeader(), BorderLayout.NORTH);

        JPanel sub = new JPanel(new BorderLayout(0, 12));
        sub.setOpaque(false);
        sub.add(buildPostPanel(), BorderLayout.NORTH);
        sub.add(buildStatsPanel(), BorderLayout.CENTER);
        top.add(sub, BorderLayout.CENTER);
        return top;
    }

    private JPanel buildHeader() {
        RoundedPanel header = new RoundedPanel(BLUE, PURPLE, 22);
        header.setLayout(new BorderLayout(0, 12));
        header.setBorder(new EmptyBorder(16, 20, 18, 20));

        JPanel brand = new JPanel(new BorderLayout(0, 2));
        brand.setOpaque(false);
        JLabel title = new JLabel("Mini Browser");
        title.setFont(new Font("SansSerif", Font.BOLD, 24));
        title.setForeground(Color.WHITE);
        JLabel subtitle = new JLabel("Trình duyệt thực tế: Hỗ trợ HTML5, CSS3, JavaScript");
        subtitle.setFont(new Font("SansSerif", Font.PLAIN, 13));
        subtitle.setForeground(new Color(228, 234, 255));
        brand.add(title, BorderLayout.NORTH);
        brand.add(subtitle, BorderLayout.CENTER);
        header.add(brand, BorderLayout.NORTH);

        urlField.setFont(new Font("SansSerif", Font.PLAIN, 15));
        urlField.setOpaque(true);
        urlField.setBackground(Color.WHITE);
        urlField.setForeground(NAVY);
        urlField.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER), new EmptyBorder(8, 12, 8, 12)));

        methodBox.setFont(new Font("SansSerif", Font.BOLD, 14));
        methodBox.setBackground(Color.WHITE);
        methodBox.setFocusable(false);
        methodBox.setPreferredSize(new Dimension(95, 40));

        JPanel urlWrap = new JPanel(new BorderLayout(8, 0));
        urlWrap.setOpaque(false);
        urlWrap.add(urlField, BorderLayout.CENTER);
        urlWrap.add(methodBox, BorderLayout.EAST);

        JPanel controls = new JPanel(new BorderLayout(10, 0));
        controls.setOpaque(false);
        controls.add(backButton, BorderLayout.WEST);
        controls.add(urlWrap, BorderLayout.CENTER);

        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 0));
        btnPanel.setOpaque(false);
        btnPanel.add(downloadButton);
        btnPanel.add(goButton);

        controls.add(btnPanel, BorderLayout.EAST);
        header.add(controls, BorderLayout.CENTER);
        return header;
    }

    private JPanel buildPostPanel() {
        postPanel.setOpaque(false);
        postBody.setFont(new Font("Monospaced", Font.PLAIN, 13));
        postBody.setLineWrap(true);
        postBody.setWrapStyleWord(true);
        postBody.setBorder(new EmptyBorder(6, 8, 6, 8));
        JScrollPane scroll = new JScrollPane(postBody);
        scroll.setBorder(BorderFactory.createLineBorder(BORDER));
        scroll.setPreferredSize(new Dimension(0, 74));
        postPanel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(BORDER), "Body (POST) – dạng key=value&key2=value2"));
        postPanel.add(scroll, BorderLayout.CENTER);
        return postPanel;
    }

    private JPanel buildStatsPanel() {
        JPanel panel = new JPanel(new GridLayout(1, 8, 8, 0));
        panel.setOpaque(false);
        for (String key : new String[]{K_STATUS, K_TIME, K_LEN, K_SIZE, K_P, K_DIV, K_SPAN, K_IMG}) {
            StatCard card = new StatCard(key);
            cards.put(key, card);
            panel.add(card);
        }
        panel.setPreferredSize(new Dimension(0, 64));
        cards.get(K_LEN).setToolTipText("Số ký tự của nội dung HTML");
        cards.get(K_SIZE).setToolTipText("Số byte nhận được");
        return panel;
    }

    private JComponent buildTabs() {
        // jfxPanel đã được khởi tạo, không cần config EditorKit nữa

        for (JTextArea area : new JTextArea[]{sourceArea, infoArea}) {
            area.setEditable(false);
            area.setFont(new Font("Monospaced", Font.PLAIN, 13));
            area.setLineWrap(true);
            area.setBorder(new EmptyBorder(10, 12, 10, 12));
        }

        tabs.setFont(new Font("SansSerif", Font.BOLD, 13));
        // Tab 0: Trình duyệt thật (JFXPanel)
        tabs.addTab("Trang (Tương tác được)", jfxPanel);
        tabs.addTab("Mã nguồn HTML", scroll(sourceArea));
        tabs.addTab("Thông tin / Headers", scroll(infoArea));
        return tabs;
    }

    private static JScrollPane scroll(Component c) {
        JScrollPane sp = new JScrollPane(c);
        sp.setBorder(BorderFactory.createLineBorder(BORDER));
        sp.getVerticalScrollBar().setUnitIncrement(16);
        return sp;
    }

    private JPanel buildStatusBar() {
        JPanel bar = new JPanel(new BorderLayout());
        bar.setBackground(new Color(251, 252, 255));
        bar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, BORDER), new EmptyBorder(8, 14, 8, 14)));
        statusLabel.setForeground(NAVY);
        statusLabel.setFont(new Font("SansSerif", Font.PLAIN, 13));
        bar.add(statusLabel, BorderLayout.CENTER);
        return bar;
    }

    private void updateMethodUI() {
        boolean post = "POST".equals(methodBox.getSelectedItem());
        postPanel.setVisible(post);
        getContentPane().revalidate();
        getContentPane().repaint();
    }

    // ================================================================== Logic

    private void sendRequest() {
        navigate(urlField.getText(), (String) methodBox.getSelectedItem(), true);
    }

    private void goBack() {
        if (history.isEmpty()) return;
        String prev = history.pop();
        backButton.setEnabled(!history.isEmpty());
        methodBox.setSelectedItem("GET");
        navigate(prev, "GET", false);
    }

    private void navigate(String input, String method, boolean pushHistory) {
        final URI uri;
        try {
            uri = parseUri(input);
        } catch (URISyntaxException | IllegalArgumentException e) {
            showError("URL không hợp lệ: " + e.getMessage());
            return;
        }
        urlField.setText(uri.toString());
        final String postData = postBody.getText().replace("\r", "").replace("\n", "").trim();

        setBusy(true);
        downloadButton.setEnabled(false);

        // Nếu là GET, ta có thể load thẳng qua WebView để nó tự xử lý (nhanh hơn, có JS)
        // Nhưng để đồng bộ với việc phân tích thẻ, ta vẫn gửi HTTP Client, rồi showResult sẽ render.

        new SwingWorker<Result, Void>() {
            @Override
            protected Result doInBackground() throws Exception {
                return fetch(uri, method, postData);
            }

            @Override
            protected void done() {
                setBusy(false);
                try {
                    showResult(get(), pushHistory);
                } catch (ExecutionException e) {
                    Throwable c = e.getCause() == null ? e : e.getCause();
                    showError("Lỗi khi gửi request: " + c.getClass().getSimpleName() + " – " + c.getMessage());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    showError("Request bị gián đoạn.");
                }
            }
        }.execute();
    }

    private static URI parseUri(String input) throws URISyntaxException {
        String s = input == null ? "" : input.trim();
        if (s.isEmpty()) throw new IllegalArgumentException("URL đang trống.");
        if (!s.matches("(?i)^[a-z][a-z0-9+.-]*://.*")) s = "https://" + s;
        URI u = new URI(s);
        String scheme = u.getScheme();
        if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
            throw new IllegalArgumentException("Chỉ hỗ trợ http và https.");
        }
        if (u.getHost() == null) throw new IllegalArgumentException("Thiếu tên miền (host).");
        return u;
    }

    private Result fetch(URI uri, String method, String postData) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(20))
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) MiniBrowser/2.0")
                .header("Accept", "text/html,application/xhtml+xml,*/*;q=0.8")
                .header("Accept-Language", "vi,en;q=0.8");
        switch (method) {
            case "HEAD":
                b.method("HEAD", HttpRequest.BodyPublishers.noBody());
                break;
            case "POST":
                b.header("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
                        .POST(HttpRequest.BodyPublishers.ofString(postData, StandardCharsets.UTF_8));
                break;
            default:
                b.GET();
        }

        long start = System.nanoTime();
        HttpResponse<byte[]> resp = httpClient.send(b.build(), HttpResponse.BodyHandlers.ofByteArray());
        long ms = (System.nanoTime() - start) / 1_000_000L;

        Result r = new Result();
        r.method = method;
        r.requestUri = uri;
        r.finalUri = resp.uri();
        r.status = resp.statusCode();
        r.headers = resp.headers();
        r.version = String.valueOf(resp.version());
        r.bytes = resp.body() == null ? new byte[0] : resp.body();
        r.ms = ms;
        r.contentType = resp.headers().firstValue("Content-Type").orElse("").toLowerCase(Locale.ROOT);

        r.isHtml = r.contentType.contains("html");
        r.isImage = r.contentType.startsWith("image/");
        r.isDownloadable = r.contentType.contains("pdf") || r.contentType.contains("audio")
                || r.contentType.contains("video") || r.contentType.contains("octet-stream")
                || r.contentType.contains("zip") || r.contentType.contains("msword")
                || r.contentType.contains("excel");

        r.isText = r.contentType.isEmpty() || r.contentType.contains("text") || r.contentType.contains("json") || r.contentType.contains("xml")
                || r.contentType.contains("javascript") || r.contentType.contains("html");

        if (!"HEAD".equals(method) && r.isText && !r.isImage) {
            r.body = decode(r.bytes, r.contentType);
            if (r.contentType.isEmpty() && !r.body.trim().startsWith("<")) r.isHtml = false;
            else if (r.contentType.isEmpty()) r.isHtml = true;
        }
        return r;
    }

    private void showResult(Result r, boolean pushHistory) {
        currentResult = r;
        boolean head = "HEAD".equals(r.method);

        cards.get(K_STATUS).set(String.valueOf(r.status), statusColor(r.status));
        cards.get(K_TIME).set(r.ms + " ms", NAVY);

        if (head) {
            String cl = r.headers.firstValue("Content-Length").orElse(null);
            String clText = "N/A", sizeText = "N/A";
            if (cl != null) {
                try {
                    long n = Long.parseLong(cl.trim());
                    clText = String.format(Locale.US, "%,d", n);
                    sizeText = formatBytes(n);
                } catch (NumberFormatException ignored) {
                    clText = cl;
                }
            }
            cards.get(K_LEN).set(clText, NAVY);
            cards.get(K_SIZE).set(sizeText, NAVY);
            for (String k : new String[]{K_P, K_DIV, K_SPAN, K_IMG}) cards.get(k).set("—", MUTED);

            // Render text đơn giản lên WebView báo cáo
            Platform.runLater(() -> webEngine.loadContent("<html><body><h3>Request HEAD</h3><p>HEAD chỉ trả về header, không có nội dung trang.</p></body></html>"));

            sourceArea.setText("(Request HEAD không trả về body)");
            infoArea.setText(buildInfo(r, null));
            tabs.setSelectedIndex(2);
        } else {
            cards.get(K_SIZE).set(formatBytes(r.bytes.length), NAVY);
            downloadButton.setEnabled(true);

            if (r.isImage || r.isDownloadable || !r.isHtml) {
                // Với file không phải HTML, ta load trực tiếp URL vào WebView để nó tự hiển thị ảnh/text
                Platform.runLater(() -> webEngine.load(r.finalUri.toString()));

                cards.get(K_LEN).set("—", MUTED);
                for (String k : new String[]{K_P, K_DIV, K_SPAN, K_IMG}) cards.get(k).set("—", MUTED);

                if (r.isImage) {
                    sourceArea.setText("(Dữ liệu hình ảnh – " + r.bytes.length + " byte)");
                } else if (r.isDownloadable) {
                    sourceArea.setText("(Dữ liệu nhị phân – " + r.bytes.length + " byte)");
                } else if (r.isText) {
                    String body = r.body;
                    int[] n = analyze(body);
                    cards.get(K_LEN).set(String.format(Locale.US, "%,d", body.length()), NAVY);
                    cards.get(K_P).set(String.valueOf(n[0]), NAVY);
                    cards.get(K_DIV).set(String.valueOf(n[1]), NAVY);
                    cards.get(K_SPAN).set(String.valueOf(n[2]), NAVY);
                    cards.get(K_IMG).set(String.valueOf(n[3]), NAVY);
                    sourceArea.setText(truncate(body, MAX_SOURCE_CHARS));
                    infoArea.setText(buildInfo(r, n));
                } else {
                    sourceArea.setText("(Nội dung nhị phân – " + r.bytes.length + " byte)");
                }
                tabs.setSelectedIndex(0);
            } else {
                // NẾU LÀ HTML -> DÙNG TRÌNH DUYỆT THẬT (JAVAFX) ĐỂ HIỂN THỊ
                String targetUrl = r.finalUri.toString();
                Platform.runLater(() -> webEngine.load(targetUrl));

                String body = r.body;
                int[] n = analyze(body);
                cards.get(K_LEN).set(String.format(Locale.US, "%,d", body.length()), NAVY);
                cards.get(K_P).set(String.valueOf(n[0]), NAVY);
                cards.get(K_DIV).set(String.valueOf(n[1]), NAVY);
                cards.get(K_SPAN).set(String.valueOf(n[2]), NAVY);
                cards.get(K_IMG).set(String.valueOf(n[3]), NAVY);

                sourceArea.setText(truncate(body, MAX_SOURCE_CHARS));
                infoArea.setText(buildInfo(r, n));
                tabs.setSelectedIndex(0);
            }

            infoArea.setCaretPosition(0);
            sourceArea.setCaretPosition(0);

            String finalUrl = r.finalUri.toString();
            if (pushHistory && currentUrl != null && !currentUrl.equals(finalUrl)) {
                history.push(currentUrl);
            }
            currentUrl = finalUrl;
            backButton.setEnabled(!history.isEmpty());
        }

        urlField.setText(r.finalUri.toString());
        String icon = r.status < 400 ? "✔" : "✖";
        statusLabel.setForeground(r.status < 400 ? NAVY : ERR);
        statusLabel.setText(icon + "  " + r.method + " · " + r.status + " · " + r.finalUri + " · " + r.ms + " ms");
    }

    private void downloadCurrentResource() {
        if (currentResult == null || currentResult.bytes == null || currentResult.bytes.length == 0) {
            JOptionPane.showMessageDialog(this, "Không có dữ liệu để tải xuống!", "Lỗi", JOptionPane.WARNING_MESSAGE);
            return;
        }

        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("Lưu tệp tin");

        String fileName = "download";
        String path = currentResult.finalUri.getPath();
        if (path != null && path.lastIndexOf('/') > 0) {
            fileName = path.substring(path.lastIndexOf('/') + 1);
            if (fileName.isEmpty()) fileName = "index.html";
        }

        if (fileName.equals("download") || !fileName.contains(".")) {
            if (currentResult.contentType.contains("pdf")) fileName += ".pdf";
            else if (currentResult.contentType.contains("audio")) fileName += ".mp3";
            else if (currentResult.contentType.contains("video")) fileName += ".mp4";
            else if (currentResult.isImage) fileName += ".png";
            else fileName += ".bin";
        }

        fileChooser.setSelectedFile(new File(fileName));

        if (fileChooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            File fileToSave = fileChooser.getSelectedFile();
            try (FileOutputStream fos = new FileOutputStream(fileToSave)) {
                fos.write(currentResult.bytes);
                JOptionPane.showMessageDialog(this, "Tải xuống thành công:\n" + fileToSave.getAbsolutePath(),
                        "Thành công", JOptionPane.INFORMATION_MESSAGE);
            } catch (Exception ex) {
                showError("Lỗi khi lưu file: " + ex.getMessage());
            }
        }
    }

    private void showError(String message) {
        statusLabel.setForeground(ERR);
        statusLabel.setText("✖  " + message);
        infoArea.setText(message);
        tabs.setSelectedIndex(2);
        cards.get(K_STATUS).set("Lỗi", ERR);
    }

    private void setBusy(boolean busy) {
        goButton.setEnabled(!busy);
        goButton.setText(busy ? "Đang gửi…" : "Gửi request");
        setCursor(busy ? Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR) : Cursor.getDefaultCursor());
        if (busy) {
            statusLabel.setForeground(NAVY);
            statusLabel.setText("⏳  Đang gửi request...");
        }
    }

    private String buildInfo(Result r, int[] tagCounts) {
        StringBuilder sb = new StringBuilder();
        String nl = "\n";
        sb.append("===== THÔNG TIN TÀI NGUYÊN =====").append(nl);
        sb.append("Phương thức    : ").append(r.method).append(nl);
        sb.append("URL yêu cầu    : ").append(r.requestUri).append(nl);
        sb.append("URL cuối cùng  : ").append(r.finalUri);
        if (!r.requestUri.equals(r.finalUri)) sb.append("   (đã chuyển hướng)");
        sb.append(nl);
        sb.append("Giao thức      : ").append(r.version).append(nl);
        sb.append("Mã trạng thái  : ").append(r.status).append(nl);
        sb.append("Thời gian      : ").append(r.ms).append(" ms").append(nl);
        sb.append("Content-Type   : ").append(headerOr(r, "Content-Type")).append(nl);
        sb.append("Content-Length : ").append(headerOr(r, "Content-Length")).append(nl);
        sb.append("Content-Encoding: ").append(headerOr(r, "Content-Encoding")).append(nl);
        sb.append("Last-Modified  : ").append(headerOr(r, "Last-Modified")).append(nl);
        sb.append("ETag           : ").append(headerOr(r, "ETag")).append(nl);
        sb.append("Cache-Control  : ").append(headerOr(r, "Cache-Control")).append(nl);
        sb.append("Server         : ").append(headerOr(r, "Server")).append(nl);
        sb.append("Date           : ").append(headerOr(r, "Date")).append(nl);

        if (tagCounts != null) {
            sb.append(nl).append("===== PHÂN TÍCH HTML =====").append(nl);
            sb.append("Độ dài (ký tự) : ").append(String.format(Locale.US, "%,d", r.body.length())).append(nl);
            sb.append("Dung lượng     : ").append(r.bytes.length).append(" byte").append(nl);
            sb.append("Số thẻ <p>     : ").append(tagCounts[0]).append(nl);
            sb.append("Số thẻ <div>   : ").append(tagCounts[1]).append(nl);
            sb.append("Số thẻ <span>  : ").append(tagCounts[2]).append(nl);
            sb.append("Số thẻ <img>   : ").append(tagCounts[3]).append(nl);
        }

        sb.append(nl).append("===== TOÀN BỘ HEADERS =====").append(nl);
        r.headers.map().forEach((k, v) -> sb.append(k).append(": ").append(String.join(", ", v)).append(nl));
        return sb.toString();
    }

    private static String headerOr(Result r, String name) {
        return r.headers.firstValue(name).orElse("(không có)");
    }

    static int[] analyze(String html) {
        String clean = html
                .replaceAll("(?is)<!--.*?-->", "")
                .replaceAll("(?is)<script\\b.*?</script>", "")
                .replaceAll("(?is)<style\\b.*?</style>", "");
        return new int[]{
                countTag(clean, "p"),
                countTag(clean, "div"),
                countTag(clean, "span"),
                countTag(clean, "img")
        };
    }

    private static int countTag(String html, String tag) {
        Matcher m = Pattern.compile("<" + tag + "(?=[\\s/>])", Pattern.CASE_INSENSITIVE).matcher(html);
        int n = 0;
        while (m.find()) n++;
        return n;
    }

    private static String decode(byte[] bytes, String contentType) {
        Charset cs = null;
        Matcher m = Pattern.compile("(?i)charset\\s*=\\s*\"?([\\w.:-]+)").matcher(contentType);
        if (m.find()) cs = charsetOrNull(m.group(1));
        if (cs == null) {
            String head = new String(bytes, 0, Math.min(bytes.length, 2048), StandardCharsets.ISO_8859_1);
            Matcher mm = Pattern.compile("(?i)<meta[^>]+charset\\s*=\\s*[\"']?([\\w.:-]+)").matcher(head);
            if (mm.find()) cs = charsetOrNull(mm.group(1));
        }
        if (cs == null) cs = StandardCharsets.UTF_8;
        return new String(bytes, cs);
    }

    private static Charset charsetOrNull(String name) {
        try {
            return Charset.forName(name);
        } catch (Exception e) {
            return null;
        }
    }

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max);
    }

    private static String formatBytes(long n) {
        if (n < 1024) return n + " B";
        if (n < 1024L * 1024) return String.format(Locale.US, "%.1f KB", n / 1024.0);
        return String.format(Locale.US, "%.2f MB", n / (1024.0 * 1024.0));
    }

    private static Color statusColor(int code) {
        if (code < 300) return OK;
        if (code < 400) return WARN;
        return ERR;
    }

    private static class Result {
        String method;
        URI requestUri;
        URI finalUri;
        int status;
        HttpHeaders headers;
        String version;
        byte[] bytes;
        String body = "";
        String contentType = "";
        boolean isHtml;
        boolean isText;
        boolean isImage;
        boolean isDownloadable;
        long ms;
    }

    private static class StatCard extends JPanel {
        private final JLabel value = new JLabel("—", SwingConstants.CENTER);

        StatCard(String title) {
            super(new BorderLayout(0, 3));
            setOpaque(false);
            setBorder(new EmptyBorder(8, 6, 8, 6));
            JLabel t = new JLabel(title, SwingConstants.CENTER);
            t.setFont(new Font("SansSerif", Font.BOLD, 11));
            t.setForeground(MUTED);
            value.setFont(new Font("SansSerif", Font.BOLD, 16));
            value.setForeground(MUTED);
            add(t, BorderLayout.NORTH);
            add(value, BorderLayout.CENTER);
        }

        void set(String text, Color color) {
            value.setText(text);
            value.setForeground(color);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(Color.WHITE);
            g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 14, 14);
            g2.setColor(BORDER);
            g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 14, 14);
            g2.dispose();
            super.paintComponent(g);
        }
    }

    public static class ActionButton extends JButton {
        private final Color base;

        public ActionButton(String text, Color background, Color foreground) {
            super(text);
            this.base = background;
            setForeground(foreground);
            setFont(new Font("SansSerif", Font.BOLD, 14));
            setFocusPainted(false);
            setBorderPainted(false);
            setContentAreaFilled(false);
            setOpaque(false);
            setRolloverEnabled(true);
            setBorder(new EmptyBorder(10, 20, 10, 20));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            Color c = base;
            if (!isEnabled()) c = new Color(base.getRed(), base.getGreen(), base.getBlue(), Math.min(base.getAlpha(), 110));
            else if (getModel().isPressed()) c = base.darker();
            else if (getModel().isRollover()) c = new Color(
                    Math.min(255, base.getRed() + 18), Math.min(255, base.getGreen() + 18),
                    Math.min(255, base.getBlue() + 18), Math.max(base.getAlpha(), 90));
            g2.setColor(c);
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), 14, 14);
            g2.dispose();
            super.paintComponent(g);
        }
    }

    public static class RoundedPanel extends JPanel {
        private final Color startColor;
        private final Color endColor;
        private final int radius;

        public RoundedPanel(Color startColor, Color endColor, int radius) {
            this.startColor = startColor;
            this.endColor = endColor;
            this.radius = radius;
            setOpaque(false);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setPaint(new GradientPaint(0, 0, startColor, getWidth(), getHeight(), endColor));
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), radius, radius);
            g2.dispose();
            super.paintComponent(g);
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new BrowserApp().setVisible(true));
    }
}