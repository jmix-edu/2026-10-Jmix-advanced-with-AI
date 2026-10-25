package com.company.timesheets.security;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * What Actuator shows without sign-in: specs/04_deployment_operations/01-production-profile.spec.adoc.
 * Real HTTP requests to a started server, no session, redirects not followed.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class ActuatorSecurityTest {

    @LocalServerPort
    private int port;

    private final HttpClient client = HttpClient.newHttpClient();

    @Test
    void healthChecksBothDatabasesWithoutDetails() throws Exception {
        HttpResponse<String> response = get("/actuator/health");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body())
                .contains("\"status\":\"UP\"")
                .contains("\"db\"", "\"dataSource\"", "\"reportsDataSource\"")
                .doesNotContain("\"details\"", "validationQuery", "HSQL", "discoveryComposite", "refreshScope");
    }

    @Test
    void infoIsOpen() throws Exception {
        assertThat(get("/actuator/info").statusCode()).isEqualTo(200);
    }

    @Test
    void otherEndpointsAreClosed() throws Exception {
        for (String path : new String[]{"/actuator", "/actuator/env", "/actuator/beans", "/actuator/configprops",
                "/actuator/loggers", "/actuator/heapdump"}) {
            assertThat(get(path).statusCode()).as(path).isNotEqualTo(200).isLessThan(500);
        }
    }

    private HttpResponse<String> get(String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).GET().build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }
}
