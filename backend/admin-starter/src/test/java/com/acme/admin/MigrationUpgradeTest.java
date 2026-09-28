package com.acme.admin;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.testcontainers.mysql.MySQLContainer;
import java.sql.*;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named = "RUN_MYSQL_TESTS", matches = "true")
class MigrationUpgradeTest {
    @Test void upgradesExistingV1DataAndDoesNotReapplyMigrations() throws Exception {
        try (var mysql = new MySQLContainer("mysql:8.4")) {
            mysql.start();
            var v1 = Flyway.configure().dataSource(mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword())
                    .locations("classpath:db/migration").target("1").load();
            assertEquals(1, v1.migrate().migrationsExecuted);
            try (var connection = DriverManager.getConnection(mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword());
                 var sql = connection.createStatement()) {
                sql.executeUpdate("insert into sys_user(id,username,password,display_name,dept_id) values(100,'legacy_user','test-hash','升级前用户',3)");
                sql.executeUpdate("insert into sys_user_role(user_id,role_id) values(100,3)");
                sql.executeUpdate("insert into sys_dict(type,label,value,sort) values('legacy_type','旧字典','legacy',1)");
                sql.executeUpdate("insert into op_log(username,action,method,outcome,duration_ms) values('legacy_user','旧操作','legacy','SUCCESS',7)");
                var current = Flyway.configure().dataSource(mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword())
                        .locations("classpath:db/migration").load();
                assertEquals(1, current.migrate().migrationsExecuted);
                current.validate();
                assertEquals("2", current.info().current().getVersion().toString());
                assertEquals("升级前用户", value(sql, "select display_name from sys_user where id=100"));
                assertEquals("test-hash", value(sql, "select password from sys_user where id=100"));
                assertEquals("3", value(sql, "select role_id from sys_user_role where user_id=100"));
                assertEquals("旧字典", value(sql, "select label from sys_dict where type='legacy_type'"));
                assertEquals("旧操作", value(sql, "select action from op_log where username='legacy_user'"));
                sql.executeUpdate("insert into audit_outbox(username,action,method,outcome,duration_ms) values('legacy_user','新操作','upgrade','SUCCESS',1)");
                assertThrows(SQLException.class, () -> sql.executeUpdate("insert into sys_user_role(user_id,role_id) values(100,999999)"));
                assertThrows(SQLException.class, () -> sql.executeUpdate("insert into sys_user_role(user_id,role_id) values(100,3)"));
                var restarted = Flyway.configure().dataSource(mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword())
                        .locations("classpath:db/migration").load();
                assertEquals(0, restarted.migrate().migrationsExecuted);
                restarted.validate();
                assertEquals("2", value(sql, "select count(*) from flyway_schema_history where success=true"));
                assertEquals("1", value(sql, "select count(*) from audit_outbox where username='legacy_user'"));
                assertEquals("1", value(sql, "select count(*) from sys_user where username='legacy_user'"));
                assertEquals("4", value(sql, "select count(*) from sys_dept"));
            }
        }
    }

    private static String value(Statement statement, String query) throws SQLException {
        try (var rows = statement.executeQuery(query)) {
            assertTrue(rows.next());
            return rows.getString(1);
        }
    }
}
