package com.acme.admin.generator;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.sql.DriverManager;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;

class CrudGeneratorTest {
    @TempDir Path output;

    @Test void rejectsAmbiguousOwnershipAndEmptyInputs() throws Exception {
        try (var db = DriverManager.getConnection("jdbc:h2:mem:invalid_generator;DATABASE_TO_LOWER=TRUE", "sa", "")) {
            for (String ddl : java.util.List.of(
                    "create table sys_bad_owner(id bigint auto_increment primary key, title varchar(64), owner_id bigint)",
                    "create table sys_bad_type(id bigint auto_increment primary key, title varchar(64), owner_id varchar(64) not null)",
                    "create table sys_empty(id bigint auto_increment primary key)")) db.createStatement().execute(ddl);
            for (String table : java.util.List.of("sys_bad_owner", "sys_bad_type", "sys_empty")) {
                assertThrows(IllegalArgumentException.class, () -> CrudGenerator.generate(db, table, "invalid", output));
            }
            try (var files = Files.list(output)) { assertEquals(0, files.count()); }
        }
    }

    @Test void committedExampleMatchesCurrentTemplates() throws Exception {
        Path example = Path.of("../admin-generated-example");
        try (var db = DriverManager.getConnection("jdbc:h2:mem:note_generator;DATABASE_TO_LOWER=TRUE", "sa", "")) {
            db.createStatement().execute("create table sys_user(id bigint primary key)");
            db.createStatement().execute(Files.readString(example.resolve("src/test/resources/db/migration/V3__generated_note.sql")));
            CrudGenerator.generate(db, "sys_note", "notes", output);
            try (var files = Files.walk(output)) {
                for (var file : files.filter(Files::isRegularFile).toList()) {
                    Path relative = output.relativize(file);
                    Path target = relative.startsWith("backend")
                            ? example.resolve("src/main/java").resolve(relative.subpath(1, relative.getNameCount()))
                            : example.resolve("generated-frontend").resolve(file.getFileName());
                    if (Boolean.getBoolean("refreshGeneratedExample")) {
                        Files.createDirectories(target.getParent());
                        Files.writeString(target, Files.readString(file));
                    }
                    assertTrue(Files.exists(target), "Regenerate missing example: " + target);
                    assertEquals(Files.readString(file), Files.readString(target),
                            "Template drift: regenerate the example before running business tests: " + target);
                }
            }
        }
    }

    @Test void generatesReviewableCrudFromTableMetadata() throws Exception {
        try (var db = DriverManager.getConnection("jdbc:h2:mem:generator;DATABASE_TO_LOWER=TRUE", "sa", "")) {
            db.createStatement().execute("create table sys_example(id bigint auto_increment primary key, display_name varchar(64), enabled boolean)");
            CrudGenerator.generate(db, "sys_example", "examples", output);
            var entity = Files.readString(output.resolve("backend/com/acme/admin/generated/ExampleEntity.java"));
            assertTrue(entity.contains("@TableName(\"sys_example\")"));
            assertTrue(entity.contains("private String displayName"));
            assertTrue(Files.readString(output.resolve("backend/com/acme/admin/generated/ExampleController.java")).contains("@access.has('examples:write')"));
            assertTrue(Files.readString(output.resolve("frontend/ExamplePage.tsx")).contains("/api/generated/examples"));
            var javaDir = output.resolve("backend/com/acme/admin/generated");
            var classes = output.resolve("compiled");
            Files.createDirectories(classes);
            String classpath = System.getProperty("java.class.path");
            assertEquals(0, ToolProvider.getSystemJavaCompiler().run(null, null, null,
                    "-classpath", classpath, "-d", classes.toString(),
                    javaDir.resolve("ExampleEntity.java").toString(),
                    javaDir.resolve("ExampleInput.java").toString(),
                    javaDir.resolve("ExampleMapper.java").toString(),
                    javaDir.resolve("ExampleService.java").toString(),
                    javaDir.resolve("ExampleController.java").toString()));
            assertThrows(Exception.class, () -> CrudGenerator.generate(db, "sys_example", "examples", output));
        }
    }
}
