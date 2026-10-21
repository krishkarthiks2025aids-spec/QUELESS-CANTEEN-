package com.queueless.canteen;

import com.queueless.canteen.util.Config;
import com.queueless.canteen.util.Db;
import com.queueless.canteen.util.DbInit;
import com.queueless.canteen.web.ApiHandler;
import com.queueless.canteen.web.StaticHandler;
import com.sun.net.httpserver.HttpServer;

import java.awt.Desktop;
import java.net.URI;
import java.net.InetSocketAddress;
import java.net.BindException;
import java.io.IOException;
import java.util.concurrent.Executors;

/** QueueLess Canteen - entry point. Connects to MySQL via JDBC, prepares the tables, starts the web server. */
public class App {
    public static void main(String[] args) throws Exception {
        System.out.println("Connecting to MySQL: " + Db.describe());
        try {
            DbInit.run();
        } catch (Exception e) {
            System.err.println();
            System.err.println("Could not connect to / prepare the MySQL database.");
            System.err.println("  -> Is MySQL running?  Are db.url / db.user / db.password correct in db.properties?");
            System.err.println("  -> Reason: " + e.getMessage());
            System.exit(1);
        }
        System.out.println("Database ready (database: queueless_canteen).");

        int configuredPort = Integer.parseInt(Config.get("server.port", "PORT", "8085"));
        HttpServer server = createServer(configuredPort);
        int port = server.getAddress().getPort();
        server.createContext("/api", new ApiHandler());
        server.createContext("/", new StaticHandler());
        server.setExecutor(Executors.newFixedThreadPool(8));
        server.start();
        String appUrl = "http://localhost:" + port;
        System.out.println("QueueLess Canteen is running -> " + appUrl);
        if (Desktop.isDesktopSupported()) {
            try {
                Desktop.getDesktop().browse(URI.create(appUrl));
            } catch (Exception e) {
                System.out.println("Open this URL in your browser: " + appUrl);
            }
        }
        System.out.println("Press Ctrl+C to stop.");
    }

    private static HttpServer createServer(int firstPort) throws IOException {
        for (int port = firstPort; ; port++) {
            try {
                return HttpServer.create(new InetSocketAddress(port), 0);
            } catch (BindException e) {
                if (port == 0 || port == 65535) throw e;
                System.out.println("Port " + port + " is already in use; trying " + (port + 1) + ".");
            }
        }
    }
}
