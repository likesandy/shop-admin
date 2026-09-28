package com.acme.admin.system;

import com.acme.admin.common.Problem;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.stereotype.Service;
import java.util.List;
import static com.acme.admin.system.Models.*;

@Service
public class RoleService {
    private final RoleRepository roles;
    public RoleService(RoleRepository roles) { this.roles = roles; }

    public List<Role> roles() { return roles.all(); }

    @Transactional
    public void saveRole(Long id, RoleInput input) {
        if (id != null && id <= 3) throw Problem.bad("内置角色受保护，请创建自定义角色");
        if (input.permissionIds().contains(1L)) throw Problem.bad("通配权限不可分配");
        if (id == null) id = roles.insert(input);
        else if (!roles.update(id, input)) throw Problem.missing();
        roles.replacePermissions(id, input.permissionIds());
    }

    @Transactional
    public void deleteRole(long id) {
        if (id <= 3) throw Problem.bad("内置角色不可删除");
        if (roles.isAssigned(id)) throw Problem.bad("角色已分配给用户，请先解除关联");
        roles.clearPermissions(id);
        if (!roles.delete(id)) throw Problem.missing();
    }
}
