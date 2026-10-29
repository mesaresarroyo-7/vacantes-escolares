package pe.edu.vacantes.security;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Component;

/** In-memory limiter for this single-instance academic application. */
@Component
public class LoginAttempts {
    private static final Duration WINDOW = Duration.ofMinutes(5);
    private static final int MAX_KEYS = 4096;
    private final Map<String, Window> attempts = new HashMap<>();

    public synchronized boolean isBlocked(String ip, String username) {
        purge();
        return attempts.size() >= MAX_KEYS && !attempts.containsKey(pair(ip, username))
                || count(pair(ip, username)) >= 5 || count("ip:" + safe(ip)) >= 20;
    }

    public synchronized void failure(String ip, String username) {
        purge();
        increment(pair(ip, username));
        increment("ip:" + safe(ip));
    }

    public synchronized void success(String ip, String username) {
        attempts.remove(pair(ip, username));
    }

    public synchronized void clear() {
        attempts.clear();
    }

    private void increment(String key) {
        Window window = attempts.get(key);
        if (window != null) {
            attempts.put(key, new Window(Math.min(window.failures + 1, 100), window.startedAt));
        } else if (attempts.size() < MAX_KEYS) {
            attempts.put(key, new Window(1, Instant.now()));
        }
    }

    private int count(String key) {
        Window window = attempts.get(key);
        return window == null ? 0 : window.failures;
    }

    private void purge() {
        Instant cutoff = Instant.now().minus(WINDOW);
        attempts.entrySet().removeIf(entry -> entry.getValue().startedAt.isBefore(cutoff));
    }

    private String pair(String ip, String username) {
        return "pair:" + safe(ip) + "|" + safe(username).toLowerCase(Locale.ROOT);
    }

    private String safe(String value) {
        if (value == null) return "";
        String clean = value.trim();
        return clean.length() <= 190 ? clean : clean.substring(0, 190);
    }

    private record Window(int failures, Instant startedAt) {}
}
