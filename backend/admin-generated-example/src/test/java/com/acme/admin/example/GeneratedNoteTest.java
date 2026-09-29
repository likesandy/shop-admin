package com.acme.admin.example;

import com.acme.admin.audit.*;
import com.acme.admin.auth.*;
import com.acme.admin.common.Errors;
import com.acme.admin.generated.*;
import java.net.URI;
import java.net.http.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.mysql.MySQLContainer;
import tools.jackson.databind.*;
import tools.jackson.databind.node.ObjectNode;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = GeneratedNoteTest.App.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "app.jwt-secret=generated-example-test-secret-at-least-32-bytes",
        "mybatis-plus.configuration.map-underscore-to-camel-case=true"
})
@EnabledIfEnvironmentVariable(named = "RUN_MYSQL_TESTS", matches = "true")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class GeneratedNoteTest {
    @SpringBootConfiguration
    @EnableAutoConfiguration
    @MapperScan("com.acme.admin.generated")
    @Import({SecurityConfig.class, AuthController.class, AuthService.class, Tokens.class, Access.class,
            Errors.class, NoteController.class, NoteService.class, AuditAspect.class, AuditWriter.class, AuditDrain.class})
    static class App {}

    static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4");
    @DynamicPropertySource static void mysql(DynamicPropertyRegistry properties) {
        MYSQL.start();
        properties.add("spring.datasource.url", MYSQL::getJdbcUrl);
        properties.add("spring.datasource.username", MYSQL::getUsername);
        properties.add("spring.datasource.password", MYSQL::getPassword);
    }
    @LocalServerPort int port;
    @Autowired JdbcClient db;
    @Autowired ObjectMapper json;
    @Autowired PasswordEncoder passwords;
    @Autowired AuditDrain drain;
    final HttpClient http = HttpClient.newHttpClient();
    static final String PASSWORD = "Generated-example-test-password!";
    String alice, bob, reader, denied, admin;
    static final String PATH = "/api/generated/notes";

    record Result(int status, JsonNode body) {}
    Result call(String method, String path, String token, Object body) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Content-Type", "application/json");
        if (token != null) request.header("Authorization", "Bearer " + token);
        request.method(method, body == null ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)));
        var response = http.send(request.build(), HttpResponse.BodyHandlers.ofString());
        return new Result(response.statusCode(), json.readTree(response.body()));
    }
    String login(String username) throws Exception {
        var result = call("POST", "/api/auth/login", null, Map.of("username", username, "password", PASSWORD));
        assertEquals(200, result.status());
        return result.body().path("data").path("token").asText();
    }
    long create(String token, Object body) throws Exception {
        var result = call("POST", PATH, token, body);
        assertEquals(200, result.status(), result.body().toString());
        return result.body().path("data").asLong();
    }
    @BeforeAll void accounts() throws Exception {
        db.sql("insert into sys_perm(id,code,name,type) values(1000,'notes:read','读记录','API'),(1001,'notes:write','写记录','API')").update();
        db.sql("insert into sys_role(id,code,name,data_scope) values(100,'note_writer','记录维护','SELF'),(101,'note_reader','记录只读','SELF')").update();
        db.sql("insert into sys_role_perm(role_id,perm_id) values(100,1000),(100,1001),(101,1000)").update();
        String hash = passwords.encode(PASSWORD);
        for (var user : List.of(new Object[]{1L, "admin", 1L}, new Object[]{100L, "alice", 100L},
                new Object[]{101L, "bob", 100L}, new Object[]{102L, "reader", 101L}, new Object[]{103L, "denied", 3L})) {
            db.sql("insert into sys_user(id,username,password,display_name,dept_id) values(?,?,?,?,3)")
                    .params(user[0], user[1], hash, user[1]).update();
            db.sql("insert into sys_user_role(user_id,role_id) values(?,?)").params(user[0], user[2]).update();
        }
        alice = login("alice"); bob = login("bob"); reader = login("reader"); denied = login("denied"); admin = login("admin");
    }
    @BeforeEach void cleanFixture() {
        db.sql("delete from sys_note").update();
        db.sql("delete from audit_outbox").update();
        db.sql("delete from op_log").update();
    }

    @Test void crudNullableUpdateAndFailedWriteAudit() throws Exception {
        long first = create(alice, Map.of("title", "first", "content", "original"));
        long second = create(alice, Map.of("title", "second"));
        assertEquals(2, call("GET", PATH, alice, null).body().path("data").size());
        assertEquals("original", call("GET", PATH + "/" + first, alice, null).body().path("data").path("content").asText());
        assertEquals(409, call("PUT", PATH + "/" + first, alice, Map.of("title", "second", "content", "must roll back")).status());
        assertEquals("first", db.sql("select title from sys_note where id=?").param(first).query(String.class).single());
        assertEquals("original", db.sql("select content from sys_note where id=?").param(first).query(String.class).single());
        var cleared = new HashMap<String, Object>();
        cleared.put("title", "changed"); cleared.put("content", null);
        assertEquals(200, call("PUT", PATH + "/" + first, alice, cleared).status());
        var value = call("GET", PATH + "/" + first, alice, null).body().path("data");
        assertEquals("changed", value.path("title").asText());
        assertTrue(value.path("content").isNull());
        assertEquals(200, call("DELETE", PATH + "/" + second, alice, null).status());
        assertEquals(404, call("GET", PATH + "/" + second, alice, null).status());
        assertEquals(404, call("PUT", PATH + "/" + second, alice, Map.of("title", "missing")).status());
        assertEquals(404, call("DELETE", PATH + "/" + second, alice, null).status());
        drain.drain();
        assertTrue(db.sql("select count(*) from op_log where action='修改Note' and outcome='FAILED'").query(Long.class).single() > 0);
        assertTrue(db.sql("select count(*) from op_log where outcome='SUCCESS'").query(Long.class).single() > 0);
        assertFalse(json.writeValueAsString(db.sql("select * from op_log").query().listOfRows()).contains(PASSWORD));
    }

    @Test void invalidInputsNeverWriteRows() throws Exception {
        for (Object input : List.of(Map.of(), Map.of("title", " "), Map.of("title", "x".repeat(65)),
                Map.of("title", "valid", "content", "x".repeat(256)), Map.of("title", List.of("invalid")))) {
            assertEquals(400, call("POST", PATH, alice, input).status());
        }
        assertEquals(0L, db.sql("select count(*) from sys_note").query(Long.class).single());
        long id = create(alice, Map.of("title", "unchanged"));
        assertEquals(400, call("PUT", PATH + "/" + id, alice, Map.of("title", "")).status());
        assertEquals("unchanged", db.sql("select title from sys_note where id=?").param(id).query(String.class).single());
    }

    @Test void ownerScopeAndServerManagedFieldsCoverEveryWritePath() throws Exception {
        long id = create(alice, Map.of("title", "private", "ownerId", 101, "id", 999999));
        assertNotEquals(999999L, id);
        assertEquals(100L, db.sql("select owner_id from sys_note where id=?").param(id).query(Long.class).single());
        assertEquals(0, call("GET", PATH, bob, null).body().path("data").size());
        assertEquals(404, call("GET", PATH + "/" + id, bob, null).status());
        assertEquals(404, call("PUT", PATH + "/" + id, bob, Map.of("title", "stolen")).status());
        assertEquals(404, call("DELETE", PATH + "/" + id, bob, null).status());
        assertEquals("private", db.sql("select title from sys_note where id=?").param(id).query(String.class).single());
        assertEquals(200, call("PUT", PATH + "/" + id, alice, Map.of("title", "mine", "ownerId", 101)).status());
        assertEquals(100L, db.sql("select owner_id from sys_note where id=?").param(id).query(Long.class).single());
        assertEquals(1, call("GET", PATH, admin, null).body().path("data").size());
        assertEquals(200, call("GET", PATH + "/" + id, admin, null).status());
        assertEquals(200, call("PUT", PATH + "/" + id, admin, Map.of("title", "reviewed")).status());
        assertEquals(200, call("DELETE", PATH + "/" + id, admin, null).status());
        long adminId = create(admin, Map.of("title", "admin note"));
        assertEquals(1L, db.sql("select owner_id from sys_note where id=?").param(adminId).query(Long.class).single());
    }

    @Test void endpointPermissionsCannotBeReplacedByFrontendHiding() throws Exception {
        long id = create(alice, Map.of("title", "protected"));
        assertEquals(401, call("GET", PATH, null, null).status());
        assertEquals(403, call("GET", PATH, denied, null).status());
        assertEquals(200, call("GET", PATH, reader, null).status());
        assertEquals(403, call("POST", PATH, reader, Map.of("title", "forbidden")).status());
        assertEquals(403, call("PUT", PATH + "/" + id, reader, Map.of("title", "forbidden")).status());
        assertEquals(403, call("DELETE", PATH + "/" + id, reader, null).status());
        assertEquals(1L, db.sql("select count(*) from sys_note").query(Long.class).single());
    }

    @Test void liveOpenApiMatchesReviewedGeneratedContract() throws Exception {
        var response = call("GET", "/v3/api-docs", null, null);
        assertEquals(200, response.status());
        var actual = (ObjectNode) response.body();
        actual.remove("servers");
        var input = actual.path("components").path("schemas").path("NoteInput");
        assertTrue(input.path("required").toString().contains("title"));
        assertEquals(64, input.path("properties").path("title").path("maxLength").asInt());
        assertEquals(255, input.path("properties").path("content").path("maxLength").asInt());
        assertFalse(input.path("properties").has("ownerId"));
        assertFalse(input.path("properties").has("id"));
        assertEquals("#/components/schemas/NoteInput", actual.path("paths").path(PATH).path("post")
                .path("requestBody").path("content").path("application/json").path("schema").path("$ref").asText());
        Path contract = Path.of("contract/openapi.json");
        if (Boolean.getBoolean("refreshGeneratedContract")) {
            Files.createDirectories(contract.getParent());
            Files.writeString(contract, json.writerWithDefaultPrettyPrinter().writeValueAsString(actual) + "\n");
        }
        assertEquals(json.readTree(Files.readString(contract)), actual,
                "Generated API drifted: review and regenerate its contract");
    }
}
