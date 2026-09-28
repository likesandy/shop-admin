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
                    javaDir.resolve("ExampleMapper.java").toString(),
                    javaDir.resolve("ExampleService.java").toString(),
                    javaDir.resolve("ExampleController.java").toString()));
            assertThrows(Exception.class, () -> CrudGenerator.generate(db, "sys_example", "examples", output));
        }
    }
}
