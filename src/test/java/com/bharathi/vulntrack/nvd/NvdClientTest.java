package com.bharathi.vulntrack.nvd;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.bharathi.vulntrack.domain.Severity;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClient;

class NvdClientTest {

    private static final String LOG4SHELL = """
            {
              "totalResults": 1,
              "vulnerabilities": [{
                "cve": {
                  "id": "CVE-2021-44228",
                  "published": "2021-12-10T10:15:09.143",
                  "descriptions": [
                    {"lang": "es", "value": "Descripcion en espanol"},
                    {"lang": "en", "value": "Apache Log4j2 JNDI features do not protect against attacker-controlled LDAP endpoints."}
                  ],
                  "metrics": {
                    "cvssMetricV31": [
                      {"type": "Secondary", "cvssData": {"baseScore": 9.0, "baseSeverity": "CRITICAL"}},
                      {"type": "Primary",   "cvssData": {"baseScore": 10.0, "baseSeverity": "CRITICAL"}}
                    ]
                  }
                }
              }]
            }
            """;

    private MockRestServiceServer server;
    private NvdClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://nvd.test/cves/2.0");
        server = MockRestServiceServer.bindTo(builder).build();
        client = new NvdClient(builder.build());
    }

    @Test
    void mapsPrimaryScoreEnglishDescriptionAndUtcPublishDate() {
        server.expect(method(HttpMethod.GET))
                .andExpect(queryParam("cveId", "CVE-2021-44228"))
                .andRespond(withSuccess(LOG4SHELL, MediaType.APPLICATION_JSON));

        CveDetails details = client.fetch("CVE-2021-44228").orElseThrow();

        assertThat(details.severity()).isEqualTo(Severity.CRITICAL);
        assertThat(details.cvssScore()).isEqualByComparingTo("10.0");
        assertThat(details.description()).startsWith("Apache Log4j2");
        assertThat(details.publishedAt()).isEqualTo(Instant.parse("2021-12-10T10:15:09.143Z"));
        server.verify();
    }

    @Test
    void returnsEmptyWhenNvdDoesNotKnowTheCve() {
        server.expect(queryParam("cveId", "CVE-2099-0001"))
                .andRespond(withSuccess("{\"totalResults\": 0, \"vulnerabilities\": []}", MediaType.APPLICATION_JSON));

        assertThat(client.fetch("CVE-2099-0001")).isEmpty();
    }

    @Test
    void serverErrorsAreThrownNotHidden() {
        server.expect(queryParam("cveId", "CVE-2021-44228")).andRespond(withServerError());

        assertThatThrownBy(() -> client.fetch("CVE-2021-44228"))
                .isInstanceOf(HttpServerErrorException.class);
    }
}