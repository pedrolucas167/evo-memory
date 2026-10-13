package com.polon.evomemory.infrastructure.web;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class ApiRateLimitTest {
    @Test
    void limitsEachUserSeparately() {
        var budget = new ApiRateLimit(2, Clock.fixed(Instant.EPOCH, ZoneOffset.UTC));
        assertThat(budget.allow("one")).isTrue();
        assertThat(budget.allow("one")).isTrue();
        assertThat(budget.allow("one")).isFalse();
        assertThat(budget.allow("two")).isTrue();
    }
}
