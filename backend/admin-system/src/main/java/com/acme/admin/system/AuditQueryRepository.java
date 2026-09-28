package com.acme.admin.system;

import com.acme.admin.common.Page;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import static com.acme.admin.system.Models.*;

@Repository
public class AuditQueryRepository {
    private final JdbcClient db;
    public AuditQueryRepository(JdbcClient db) { this.db = db; }

    public Page<Audit> page(int page, int size) {
        var entries = db.sql("select id,username,action,method,outcome,duration_ms,created_at from op_log order by id desc limit ? offset ?")
                .params(size, (page - 1) * size).query((rs, n) -> new Audit(rs.getLong(1),
                        rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5),
                        rs.getLong(6), rs.getString(7))).list();
        return new Page<>(entries, count(), page, size);
    }

    public long count() {
        return db.sql("select count(*) from op_log").query(Long.class).single();
    }
}
