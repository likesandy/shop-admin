package com.acme.admin.system;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import static com.acme.admin.system.Models.*;

@Repository
public class OverviewRepository {
    private final JdbcClient db;
    public OverviewRepository(JdbcClient db) { this.db = db; }

    public long users(DataScope.Filter scope) {
        return db.sql("select count(*) from sys_user u where u.deleted=false and " + scope.sql())
                .params(scope.params()).query(Long.class).single();
    }

    public long roles() {
        return db.sql("select count(*) from sys_role").query(Long.class).single();
    }
}
