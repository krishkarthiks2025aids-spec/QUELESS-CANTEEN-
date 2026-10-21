package com.queueless.canteen.web;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Map;

/** Serves the front end (src/main/resources/static) at http://localhost:8080/ */
public class StaticHandler implements HttpHandler {
    private static final Map<String, String> TYPES = Map.of(
            "html", "text/html; charset=utf-8",
            "css", "text/css; charset=utf-8",
            "js", "application/javascript; charset=utf-8",
            "svg", "image/svg+xml",
            "png", "image/png",
            "ico", "image/x-icon");

    @Override
    public void handle(HttpExchange ex) throws IOException {
        String path = ex.getRequestURI().getPath();
        if (path.equals("/")) path = "/index.html";
        if (path.contains("..")) {
            send(ex, 400, "text/plain", "Bad request".getBytes());
            return;
        }
        try (InputStream in = StaticHandler.class.getResourceAsStream("/static" + path)) {
            if (in == null) {
                send(ex, 404, "text/plain; charset=utf-8", "Not found".getBytes());
                return;
            }
            String ext = path.substring(path.lastIndexOf('.') + 1).toLowerCase();
            send(ex, 200, TYPES.getOrDefault(ext, "application/octet-stream"), in.readAllBytes());
        }
    }

    private void send(HttpExchange ex, int code, String type, byte[] body) throws IOException {
        ex.getResponseHeaders().set("Content-Type", type);
        ex.getResponseHeaders().set("Cache-Control", "no-cache");
        ex.sendResponseHeaders(code, body.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(body);
        }
    }
}
