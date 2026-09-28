package com.acme.admin.system;

import com.acme.admin.auth.Access;
import com.acme.admin.common.Page;
import com.acme.admin.common.Problem;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import static com.acme.admin.system.Models.*;

@Service
public class UserService {
    private final UserRepository users;
    private final DataScope scope;
    private final Access access;
    private final PasswordEncoder passwords;

    public UserService(UserRepository users, DataScope scope, Access access, PasswordEncoder passwords) {
        this.users = users;
        this.scope = scope;
        this.access = access;
        this.passwords = passwords;
    }

    public Page<User> users(String query, int page, int size) {
        return users.search(query, Math.max(1, page), Math.max(1, Math.min(100, size)), scope.users());
    }

    public User user(long id) {
        scope.requireUser(id);
        return users.findDetails(id).orElseThrow(Problem::missing);
    }

    // Keep locks and authorization reads inside the service transaction, before any writes.
    private void lockUser(long id) {
        if (!users.lockActive(id)) throw Problem.forbidden();
    }

    private void protectPrivilegedUser(long id) {
        if (!access.actor().admin() && users.hasPrivilegedRole(id)) throw Problem.forbidden();
    }

    @Transactional
    public long createUser(UserInput input) {
        scope.requireDept(input.deptId());
        if (input.password() == null || input.password().length() < 12) throw Problem.bad("初始密码至少 12 位");
        var user = new UserEntity();
        user.setUsername(input.username());
        user.setDisplayName(input.displayName());
        user.setPassword(passwords.encode(input.password()));
        user.setDeptId(input.deptId());
        user.setEnabled(input.enabled());
        users.insert(user);
        users.addRole(user.getId(), 3);
        return user.getId();
    }

    @Transactional
    public void updateUser(long id, UserInput input) {
        lockUser(id);
        scope.requireUser(id);
        protectPrivilegedUser(id);
        scope.requireDept(input.deptId());
        if (id == 1) throw Problem.bad("内置管理员资料受保护，请使用个人密码修改功能");
        var user = users.find(id);
        if (user == null) throw Problem.missing();
        user.setDisplayName(input.displayName());
        user.setDeptId(input.deptId());
        user.setEnabled(input.enabled());
        if (input.password() != null && !input.password().isBlank()) {
            if (input.password().length() < 12) throw Problem.bad("密码至少 12 位");
            user.setPassword(passwords.encode(input.password()));
        }
        users.update(user);
        users.revokeSessions(id);
    }

    @Transactional
    public void deleteUser(long id) {
        lockUser(id);
        scope.requireUser(id);
        protectPrivilegedUser(id);
        if (id == 1 || id == access.actor().id()) throw Problem.bad("不能删除内置管理员或自己");
        users.delete(id);
        users.revokeSessions(id);
    }

    @Transactional
    public void assign(long id, Roles input) {
        lockUser(id);
        scope.requireUser(id);
        if (id == 1) throw Problem.bad("内置管理员角色受保护");
        if (input.roleIds().contains(1L)) throw Problem.bad("内置超级管理员角色不可分配");
        users.replaceRoles(id, input.roleIds());
    }

    @Transactional
    public void changePassword(PasswordInput input) {
        var actor = access.actor();
        lockUser(actor.id());
        var user = users.find(actor.id());
        if (!passwords.matches(input.oldPassword(), user.getPassword())) throw Problem.bad("原密码错误");
        user.setPassword(passwords.encode(input.newPassword()));
        users.update(user);
        users.revokeSessions(actor.id());
    }
}
