package com.acme.admin.system;

import com.acme.admin.common.Page;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.*;
import static com.acme.admin.system.Models.User;

@Repository
public class UserRepository {
    private final JdbcClient db;
    private final UserMapper mapper;

    public UserRepository(JdbcClient db, UserMapper mapper) {
        this.db = db;
        this.mapper = mapper;
    }

    public Page<User> search(String query, int page, int size, DataScope.Filter scope) {
        String where = " from sys_user u join sys_dept d on d.id=u.dept_id where u.deleted=false and "
                + scope.sql() + " and (u.username like :q or u.display_name like :q)";
        var params = new HashMap<>(scope.params());
        params.put("q", "%" + query + "%");
        long total = db.sql("select count(*)" + where).params(params).query(Long.class).single();
        var rows = db.sql("select u.id,u.username,u.display_name,u.dept_id,d.name as dept_name,u.enabled"
                        + where + " order by u.id desc limit :limit offset :offset")
                .params(params).param("limit", size).param("offset", (page - 1) * size)
                .query((rs, n) -> new User(rs.getLong("id"), rs.getString("username"),
                        rs.getString("display_name"), rs.getLong("dept_id"), rs.getString("dept_name"),
                        rs.getBoolean("enabled"), List.of())).list();
        var ids = rows.stream().map(User::id).toList();
        Map<Long, List<Long>> roles = new HashMap<>();
        if (!ids.isEmpty()) {
            db.sql("select user_id,role_id from sys_user_role where user_id in (:ids)")
                    .param("ids", ids).query().listOfRows().forEach(row -> roles.computeIfAbsent(
                            ((Number) row.get("user_id")).longValue(), key -> new ArrayList<>())
                            .add(((Number) row.get("role_id")).longValue()));
        }
        return new Page<>(rows.stream().map(u -> new User(u.id(), u.username(), u.displayName(),
                u.deptId(), u.deptName(), u.enabled(), roles.getOrDefault(u.id(), List.of()))).toList(),
                total, page, size);
    }

    /** Must be the first database read in a target-user write transaction. */
    public boolean lockActive(long id) {
        return db.sql("select id from sys_user where id=? and deleted=false for update")
                .param(id).query(Long.class).optional().isPresent();
    }

    public boolean hasPrivilegedRole(long id) {
        return db.sql("select count(*) from sys_user_role where user_id=? and role_id<>3")
                .param(id).query(Long.class).single() > 0;
    }

    public Optional<User> findDetails(long id) {
        return db.sql("select u.*,d.name as dept_name from sys_user u join sys_dept d on d.id=u.dept_id where u.id=? and u.deleted=false")
                .param(id).query((rs, n) -> new User(id, rs.getString("username"),
                        rs.getString("display_name"), rs.getLong("dept_id"), rs.getString("dept_name"),
                        rs.getBoolean("enabled"), roleIds(id))).optional();
    }

    private List<Long> roleIds(long id) {
        return db.sql("select role_id from sys_user_role where user_id=?")
                .param(id).query(Long.class).list();
    }

    public UserEntity find(long id) { return mapper.selectById(id); }
    public void insert(UserEntity user) { mapper.insert(user); }
    public void update(UserEntity user) { mapper.updateById(user); }
    public void delete(long id) { mapper.deleteById(id); }

    public void addRole(long userId, long roleId) {
        db.sql("insert into sys_user_role(user_id,role_id) values(?,?)").params(userId, roleId).update();
    }

    public void replaceRoles(long id, Collection<Long> roleIds) {
        db.sql("delete from sys_user_role where user_id=?").param(id).update();
        for (long role : new HashSet<>(roleIds)) addRole(id, role);
    }

    public void revokeSessions(long id) {
        db.sql("delete from auth_session where user_id=?").param(id).update();
    }
}
