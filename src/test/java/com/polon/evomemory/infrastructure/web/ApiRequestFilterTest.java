package com.polon.evomemory.infrastructure.web;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.assertj.core.api.Assertions.*;

class ApiRequestFilterTest {
    private MockHttpServletRequest request(byte[] body) {
        var request = new MockHttpServletRequest("POST", "/api/conversations/user");
        request.setContent(body);
        return request;
    }

    @Test
    void oversizedBodiesNeverReachTheApplication() throws Exception {
        var response = new MockHttpServletResponse();
        var reached = new AtomicBoolean();
        new ApiRequestFilter(1, 10).doFilter(request(new byte[11]), response,
                (req, res) -> reached.set(true));
        assertThat(response.getStatus()).isEqualTo(413);
        assertThat(reached).isFalse();
    }

    @Test
    void fullConcurrencyBudgetRejectsWorkAndExceptionsReleaseThePermit() throws Exception {
        var filter = new ApiRequestFilter(1, 10);
        var nested = new MockHttpServletResponse();
        assertThatThrownBy(() -> filter.doFilter(request(new byte[0]), new MockHttpServletResponse(), (req, res) -> {
            filter.doFilter(request(new byte[0]), nested, (innerReq, innerRes) -> fail("Must reject excess work"));
            throw new IOException("test");
        })).isInstanceOf(IOException.class);
        assertThat(nested.getStatus()).isEqualTo(429);
        var reached = new AtomicBoolean();
        filter.doFilter(request(new byte[0]), new MockHttpServletResponse(), (req, res) -> reached.set(true));
        assertThat(reached).isTrue();
    }
}
