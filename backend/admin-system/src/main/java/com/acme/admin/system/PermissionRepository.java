package com.acme.admin.system;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;
import java.util.Arrays;
import static com.acme.admin.system.Models.*;

@Repository
public class PermissionRepository {
    private final JdbcClient db;
    public PermissionRepository(JdbcClient db) { this.db = db; }

    public List<Permission> all() {
        return db.sql("select id,code,name,type,path,parent_id from sys_perm where id<>1 order by id")
                .query(Permission.class).list();
    }

    public Optional<String> type(long id) {
        return db.sql("select type from sys_perm where id=?").param(id).query(String.class).optional();
    }

    public void insert(PermissionInput input) {
        db.sql("insert into sys_perm(code,name,type,path,parent_id) values(?,?,?,?,?)")
                .params(Arrays.asList(input.code(), input.name(), input.type(), input.path(), input.parentId())).update();
    }

    public boolean update(long id, PermissionInput input) {
        return db.sql("update sys_perm set name=?,type=?,path=?,parent_id=? where id=?")
                .params(Arrays.asList(input.name(), input.type(), input.path(), input.parentId(), id)).update() > 0;
    }

    public boolean hasChildren(long id) {
        return db.sql("select count(*) from sys_perm where parent_id=?").param(id).query(Long.class).single() > 0;
    }

    public void delete(long id) {
        db.sql("delete from sys_role_perm where perm_id=?").param(id).update();
        db.sql("delete from sys_perm where id=?").param(id).update();
    }
}
