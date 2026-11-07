package studentperformance;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Robust configuration reader that inspects multiple sources in priority order:
 * 1. Java System properties (-DKEY=value)
 * 2. Process Environment variables (System.getenv)
 * 3. Local project .env file
 * 4. Windows User Registry (HKCU\\Environment) as fallback for variables set via setx or User scope
 */
public final class EnvConfig {
    private static final Map<String, String> DOT_ENV_CACHE = new HashMap<>();
    private static boolean dotEnvLoaded = false;

    private EnvConfig() {}

    public static String get(String key) {
        return get(key, null);
    }

    public static String get(String key, String defaultValue) {
        if (key == null || key.isBlank()) return defaultValue;

        // 1. System property (-Dkey=value)
        String val = System.getProperty(key);
        if (val != null && !val.isBlank()) return val.trim();

        // Standard dot notation property fallback (-Dgemini.api.key=value)
        val = System.getProperty(key.toLowerCase(Locale.ROOT).replace('_', '.'));
        if (val != null && !val.isBlank()) return val.trim();

        // 2. Process Environment variable
        val = System.getenv(key);
        if (val != null && !val.isBlank()) return val.trim();

        // 3. Local .env file
        val = getFromDotEnv(key);
        if (val != null && !val.isBlank()) return val.trim();

        // 4. Windows User Registry fallback
        val = getFromWindowsUserRegistry(key);
        if (val != null && !val.isBlank()) return val.trim();

        return defaultValue;
    }

    private static synchronized String getFromDotEnv(String key) {
        if (!dotEnvLoaded) {
            dotEnvLoaded = true;
            loadDotEnv(Path.of(".env"));
            loadDotEnv(Path.of("../.env"));
        }
        return DOT_ENV_CACHE.get(key);
    }

    private static void loadDotEnv(Path envPath) {
        if (!Files.exists(envPath) || !Files.isRegularFile(envPath)) return;
        try (BufferedReader reader = Files.newBufferedReader(envPath)) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                int eq = line.indexOf('=');
                if (eq > 0) {
                    String k = line.substring(0, eq).trim();
                    String v = line.substring(eq + 1).trim();
                    if ((v.startsWith("\"") && v.endsWith("\"")) || (v.startsWith("'") && v.endsWith("'"))) {
                        v = v.substring(1, v.length() - 1);
                    }
                    if (!k.isEmpty() && !DOT_ENV_CACHE.containsKey(k)) {
                        DOT_ENV_CACHE.put(k, v);
                    }
                }
            }
        } catch (IOException ignored) {
        }
    }

    private static String getFromWindowsUserRegistry(String key) {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (!os.contains("win")) return null;
        try {
            Process process = new ProcessBuilder("reg", "query", "HKCU\\Environment", "/v", key)
                    .redirectErrorStream(true)
                    .start();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.contains(key)) {
                        String[] tokens = line.trim().split("\\s+");
                        if (tokens.length >= 3 && tokens[0].equalsIgnoreCase(key)) {
                            return tokens[2].trim();
                        }
                    }
                }
            }
            process.waitFor();
        } catch (Exception ignored) {
        }
        return null;
    }
}
