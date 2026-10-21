package com.queueless.canteen.util;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/** Reads db.properties (from the classpath, then an optional ./db.properties next to where you run the app). */
public final class Config {
    private static final Properties PROPS = new Properties();

    static {
        try (InputStream in = Config.class.getResourceAsStream("/db.properties")) {
            if (in != null) PROPS.load(in);
        } catch (IOException ignored) { }
        Path external = Path.of("db.properties");
        if (Files.exists(external)) {
            try (InputStream in = Files.newInputStream(external)) {
                PROPS.load(in);
            } catch (IOException ignored) { }
        }
    }

    private Config() { }

    /** Environment variable wins, then db.properties, then the default. */
    public static String get(String key, String envName, String defaultValue) {
        String env = System.getenv(envName);
        if (env != null && !env.isBlank()) return env;
        return PROPS.getProperty(key, defaultValue);
    }
}
