package com.acme.admin.system;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import java.util.Map;
import java.util.HashMap;
import static com.acme.admin.system.Models.*;

@Repository
public class DepartmentRepository {
    private final JdbcClient db;
    public DepartmentRepository(JdbcClient db) { this.db = db; }

    public void lockTree() {
        db.sql("select id from sys_dept order by id for update").query(Long.class).list();
    }

    public Map<Long, Long> parents() {
        var parents = new HashMap<Long, Long>();
        db.sql("select id,parent_id from sys_dept").query().listOfRows().forEach(row -> parents.put(
                ((Number) row.get("id")).longValue(), row.get("parent_id") == null
                        ? null : ((Number) row.get("parent_id")).longValue()));
        return parents;
    }

    public void insert(DeptInput input) {
        db.sql("insert into sys_dept(parent_id,name) values(?,?)").params(input.parentId(), input.name()).update();
    }

    public boolean update(long id, DeptInput input) {
        return db.sql("update sys_dept set parent_id=?,name=? where id=?")
                .params(input.parentId(), input.name(), id).update() > 0;
    }

    public boolean hasReferences(long id) {
        return db.sql("select count(*) from sys_dept where parent_id=?").param(id).query(Long.class).single() > 0
                || db.sql("select count(*) from sys_user where dept_id=?").param(id).query(Long.class).single() > 0;
    }

    public boolean delete(long id) {
        return db.sql("delete from sys_dept where id=?").param(id).update() > 0;
    }
}
