package com.acme.admin.system;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.HashSet;
import java.util.Collection;
import static com.acme.admin.system.Models.*;

@Repository
public class RoleRepository {
    private final JdbcClient db;
    public RoleRepository(JdbcClient db) { this.db = db; }

    public List<Role> all() {
        return db.sql("select * from sys_role order by id").query((rs, n) -> new Role(
                rs.getLong("id"), rs.getString("code"), rs.getString("name"),
                rs.getString("data_scope"), rs.getBoolean("enabled"),
                db.sql("select perm_id from sys_role_perm where role_id=?")
                        .param(rs.getLong("id")).query(Long.class).list())).list();
    }

    public long insert(RoleInput input) {
        var key = new org.springframework.jdbc.support.GeneratedKeyHolder();
        db.sql("insert into sys_role(code,name,data_scope,enabled) values(?,?,?,?)")
                .params(input.code(), input.name(), input.dataScope(), input.enabled()).update(key);
        return key.getKey().longValue();
    }

    public boolean update(long id, RoleInput input) {
        return db.sql("update sys_role set name=?,data_scope=?,enabled=? where id=?")
                .params(input.name(), input.dataScope(), input.enabled(), id).update() > 0;
    }

    public void replacePermissions(long id, Collection<Long> permissions) {
        clearPermissions(id);
        for (long permission : new HashSet<>(permissions)) {
            db.sql("insert into sys_role_perm(role_id,perm_id) values(?,?)").params(id, permission).update();
        }
    }

    public boolean isAssigned(long id) {
        return db.sql("select count(*) from sys_user_role where role_id=?").param(id).query(Long.class).single() > 0;
    }

    public void clearPermissions(long id) {
        db.sql("delete from sys_role_perm where role_id=?").param(id).update();
    }

    public boolean delete(long id) {
        return db.sql("delete from sys_role where id=?").param(id).update() > 0;
    }
}
