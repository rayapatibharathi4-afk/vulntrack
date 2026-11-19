package com.bharathi.vulntrack.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class CorrelationIdFilterTest {

    private final CorrelationIdFilter filter = new CorrelationIdFilter();

    @Test
    void echoesAClientSuppliedId() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/scans");
        request.addHeader(CorrelationId.HTTP_HEADER, "ci-build-4711");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getHeader(CorrelationId.HTTP_HEADER)).isEqualTo("ci-build-4711");
    }

    @Test
    void replacesAnUnsafeIdSoItCannotForgeLogLines() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/scans");
        request.addHeader(CorrelationId.HTTP_HEADER, "ok\nINFO forged line");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        String id = response.getHeader(CorrelationId.HTTP_HEADER);
        assertThat(id).isNotEqualTo("ok\nINFO forged line").matches("[A-Za-z0-9-]{1,64}");
    }

    @Test
    void generatesAnIdWhenTheClientSendsNone() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(new MockHttpServletRequest("GET", "/api/findings"), response, new MockFilterChain());

        assertThat(response.getHeader(CorrelationId.HTTP_HEADER)).isNotBlank();
    }

    @Test
    void clearsTheLoggingContextAfterTheRequest() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/findings");

        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        assertThat(MDC.get(CorrelationId.MDC_KEY)).isNull();
    }
}
