package net.zash.panel;

import android.content.res.AssetManager;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Tiny static file server bound to 127.0.0.1 serving assets/dist. */
public class LocalServer {
    private final AssetManager am;
    private final int port;
    private ServerSocket server;
    private final ExecutorService pool = Executors.newCachedThreadPool();

    public LocalServer(AssetManager am, int port) { this.am = am; this.port = port; }

    public int start() throws IOException {
        try {
            server = new ServerSocket(port, 50, InetAddress.getByName("127.0.0.1"));
        } catch (IOException e) {
            server = new ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"));
        }
        Thread t = new Thread(() -> {
            while (!server.isClosed()) {
                try {
                    final Socket s = server.accept();
                    pool.execute(() -> handle(s));
                } catch (IOException ignored) { }
            }
        }, "zash-server");
        t.setDaemon(true);
        t.start();
        return server.getLocalPort();
    }

    public void stop() {
        try { if (server != null) server.close(); } catch (IOException ignored) { }
        pool.shutdownNow();
    }

    private void handle(Socket s) {
        try (Socket sock = s) {
            sock.setSoTimeout(15000);
            BufferedReader in = new BufferedReader(new InputStreamReader(sock.getInputStream(), StandardCharsets.ISO_8859_1));
            String line = in.readLine();
            if (line == null) return;
            String h;
            while ((h = in.readLine()) != null && !h.isEmpty()) { /* skip headers */ }
            String[] parts = line.split(" ");
            if (parts.length < 2) return;
            String method = parts[0];
            String path = parts[1];
            int q = path.indexOf('?'); if (q >= 0) path = path.substring(0, q);
            int f = path.indexOf('#'); if (f >= 0) path = path.substring(0, f);
            path = URLDecoder.decode(path, "UTF-8");
            if (path.contains("..")) path = "/";
            if (path.equals("/") || path.isEmpty()) path = "/index.html";
            OutputStream out = new BufferedOutputStream(sock.getOutputStream());
            byte[] body = read("dist" + path);
            String status = "200 OK";
            if (body == null) {
                if (path.lastIndexOf('.') > path.lastIndexOf('/')) {
                    status = "404 Not Found";
                    body = "Not found".getBytes(StandardCharsets.UTF_8);
                    path = "/404.txt";
                } else { // SPA fallback
                    body = read("dist/index.html");
                    path = "/index.html";
                }
            }
            String cache = path.startsWith("/assets/") ? "public, max-age=31536000, immutable" : "no-cache";
            String hdr = "HTTP/1.1 " + status + "\r\n" +
                    "Content-Type: " + mime(path) + "\r\n" +
                    "Content-Length: " + body.length + "\r\n" +
                    "Cache-Control: " + cache + "\r\n" +
                    "Connection: close\r\n\r\n";
            out.write(hdr.getBytes(StandardCharsets.ISO_8859_1));
            if (!"HEAD".equals(method)) out.write(body);
            out.flush();
        } catch (Exception ignored) { }
    }

    private byte[] read(String name) {
        try (InputStream is = am.open(name)) {
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            byte[] buf = new byte[16384]; int n;
            while ((n = is.read(buf)) > 0) bo.write(buf, 0, n);
            return bo.toByteArray();
        } catch (IOException e) { return null; }
    }

    private static String mime(String p) {
        p = p.toLowerCase();
        if (p.endsWith(".html")) return "text/html; charset=utf-8";
        if (p.endsWith(".js") || p.endsWith(".mjs")) return "text/javascript; charset=utf-8";
        if (p.endsWith(".css")) return "text/css; charset=utf-8";
        if (p.endsWith(".json")) return "application/json";
        if (p.endsWith(".webmanifest")) return "application/manifest+json";
        if (p.endsWith(".svg")) return "image/svg+xml";
        if (p.endsWith(".png")) return "image/png";
        if (p.endsWith(".jpg") || p.endsWith(".jpeg")) return "image/jpeg";
        if (p.endsWith(".webp")) return "image/webp";
        if (p.endsWith(".ico")) return "image/x-icon";
        if (p.endsWith(".woff2")) return "font/woff2";
        if (p.endsWith(".woff")) return "font/woff";
        if (p.endsWith(".ttf")) return "font/ttf";
        if (p.endsWith(".wasm")) return "application/wasm";
        if (p.endsWith(".txt") || p.endsWith(".md")) return "text/plain; charset=utf-8";
        return "application/octet-stream";
    }
}
