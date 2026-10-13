package com.polon.evomemory.infrastructure.web;

import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;

public class ApiRateLimit {
    private record Window(long startedAt, int count) {}
    private final Map<String, Window> windows = new LinkedHashMap<>();
    private final int requestsPerMinute;
    private final Clock clock;

    @org.springframework.beans.factory.annotation.Autowired
    public ApiRateLimit(@Value("${memory.api.requests-per-minute:120}") int requestsPerMinute) {
        this(requestsPerMinute, Clock.systemUTC());
    }

    ApiRateLimit(int requestsPerMinute, Clock clock) {
        if (requestsPerMinute < 1) throw new IllegalArgumentException("Request limit must be positive");
        this.requestsPerMinute = requestsPerMinute;
        this.clock = clock;
    }

    public synchronized boolean allow(String userId) {
        long now = clock.millis();
        Window window = windows.get(userId);
        if (window == null || now - window.startedAt() >= 60000) {
            if (windows.size() >= 4096) windows.remove(windows.keySet().iterator().next());
            windows.put(userId, new Window(now, 1));
            return true;
        }
        if (window.count() >= requestsPerMinute) return false;
        windows.put(userId, new Window(window.startedAt(), window.count() + 1));
        return true;
    }
}
