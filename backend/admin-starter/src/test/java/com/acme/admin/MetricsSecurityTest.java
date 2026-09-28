package com.acme.admin;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.profiles.active=local",
        "spring.datasource.url=jdbc:h2:mem:metrics;MODE=MySQL;DATABASE_TO_LOWER=TRUE;NON_KEYWORDS=VALUE;DB_CLOSE_DELAY=-1",
        "app.metrics-enabled=true",
        "app.metrics-password=metrics-test-only-password-32-characters"
})
class MetricsSecurityTest {
    @LocalServerPort int port;
    private final HttpClient client = HttpClient.newHttpClient();

    private HttpResponse<String> get(String path, String credentials) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path));
        if (credentials != null) request.header("Authorization", "Basic " + Base64.getEncoder()
                .encodeToString(credentials.getBytes(StandardCharsets.UTF_8)));
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    @Test void scrapeCredentialWorksOnlyForMetrics() throws Exception {
        String credentials = "metrics:metrics-test-only-password-32-characters";
        assertEquals(401, get("/actuator/prometheus", null).statusCode());
        assertEquals(401, get("/actuator/prometheus", "metrics:wrong").statusCode());
        var metrics = get("/actuator/prometheus", credentials);
        assertEquals(200, metrics.statusCode());
        assertTrue(metrics.body().contains("jvm_memory_used_bytes"));
        assertEquals(401, get("/api/users", credentials).statusCode());
        assertEquals(401, get("/api/auth/me", credentials).statusCode());
    }
}
