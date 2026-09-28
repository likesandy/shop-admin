package com.acme.admin.generator;

import freemarker.template.Configuration;
import freemarker.template.TemplateExceptionHandler;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Types;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class CrudGenerator {
    private CrudGenerator() {}

    public static void main(String[] args) throws Exception {
        if (args.length != 3) throw new IllegalArgumentException("Usage: CrudGenerator <table> <resource> <output-directory>");
        String url = required("DB_URL");
        String user = required("DB_USER");
        String password = required("DB_PASSWORD");
        try (Connection db = DriverManager.getConnection(url, user, password)) {
            generate(db, args[0], args[1], Path.of(args[2]));
        }
    }

    private static String required(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Set " + name);
        return value;
    }

    public static void generate(Connection db, String table, String resource, Path output) throws Exception {
        if (!table.matches("[a-z][a-z0-9_]*") || !resource.matches("[a-z][a-z0-9-]*"))
            throw new IllegalArgumentException("Table and resource must use lowercase SQL/URL identifiers");
        String className = className(table);
        List<Map<String, Object>> columns = new ArrayList<>();
        DatabaseMetaData meta = db.getMetaData();
        try (ResultSet rs = meta.getColumns(db.getCatalog(), null, table, null)) {
            while (rs.next()) {
                String column = rs.getString("COLUMN_NAME");
                if (!column.matches("[a-z][a-z0-9_]*")) throw new IllegalArgumentException("Unsupported column: " + column);
                String field = camel(column);
                columns.add(Map.of("column", column, "field", field, "getter", Character.toUpperCase(field.charAt(0)) + field.substring(1),
                        "type", javaType(rs.getInt("DATA_TYPE"))));
            }
        }
        if (columns.isEmpty()) throw new IllegalArgumentException("Table not found or has no columns: " + table);
        List<String> keys = new ArrayList<>();
        try (ResultSet rs = meta.getPrimaryKeys(db.getCatalog(), null, table)) {
            while (rs.next()) keys.add(rs.getString("COLUMN_NAME"));
        }
        if (keys.size() != 1 || !keys.getFirst().equals("id") || columns.stream().noneMatch(c -> c.get("column").equals("id") && c.get("type").equals("Long")))
            throw new IllegalArgumentException("This scaffold requires a single BIGINT primary key named id");
        Configuration config = new Configuration(Configuration.VERSION_2_3_34);
        config.setClassLoaderForTemplateLoading(CrudGenerator.class.getClassLoader(), "templates");
        config.setDefaultEncoding("UTF-8");
        config.setTemplateExceptionHandler(TemplateExceptionHandler.RETHROW_HANDLER);
        Map<String, Object> model = new HashMap<>();
        model.put("table", table);
        model.put("resource", resource);
        model.put("className", className);
        model.put("permission", resource.replace('-', ':'));
        model.put("columns", columns);
        Path javaDir = output.resolve("backend/com/acme/admin/generated");
        Map<String, Path> targets = Map.of(
                "Entity.java.ftl", javaDir.resolve(className + "Entity.java"),
                "Mapper.java.ftl", javaDir.resolve(className + "Mapper.java"),
                "Service.java.ftl", javaDir.resolve(className + "Service.java"),
                "Controller.java.ftl", javaDir.resolve(className + "Controller.java"),
                "Page.tsx.ftl", output.resolve("frontend/" + className + "Page.tsx"));
        for (Path target : targets.values()) {
            if (Files.exists(target)) throw new IOException("Output already exists: " + target);
        }
        Map<Path, String> rendered = new HashMap<>();
        for (var entry : targets.entrySet()) {
            StringWriter writer = new StringWriter();
            config.getTemplate(entry.getKey()).process(model, writer);
            rendered.put(entry.getValue(), writer.toString());
        }
        for (var entry : rendered.entrySet()) {
            Files.createDirectories(entry.getKey().getParent());
            Files.writeString(entry.getKey(), entry.getValue());
        }
    }

    private static String camel(String input) {
        StringBuilder out = new StringBuilder();
        boolean upper = false;
        for (char c : input.toCharArray()) {
            if (c == '_') upper = true;
            else { out.append(upper ? Character.toUpperCase(c) : c); upper = false; }
        }
        return out.toString();
    }

    private static String className(String table) {
        String name = camel(table.startsWith("sys_") ? table.substring(4) : table);
        return Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }

    private static String javaType(int sqlType) {
        return switch (sqlType) {
            case Types.BIGINT -> "Long";
            case Types.INTEGER, Types.SMALLINT, Types.TINYINT -> "Integer";
            case Types.BOOLEAN, Types.BIT -> "Boolean";
            case Types.DECIMAL, Types.NUMERIC -> "java.math.BigDecimal";
            case Types.TIMESTAMP, Types.TIMESTAMP_WITH_TIMEZONE -> "java.time.LocalDateTime";
            case Types.DATE -> "java.time.LocalDate";
            case Types.VARCHAR, Types.CHAR, Types.LONGVARCHAR -> "String";
            default -> throw new IllegalArgumentException("Unsupported SQL column type: " + sqlType);
        };
    }
}
