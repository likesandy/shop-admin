package com.acme.admin.system;

import com.acme.admin.auth.Access;
import com.acme.admin.common.Problem;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.stereotype.Service;
import java.util.List;
import static com.acme.admin.system.Models.*;

@Service
public class PermissionService {
    private final PermissionRepository permissions;
    private final Access access;
    public PermissionService(PermissionRepository permissions, Access access) {
        this.permissions = permissions;
        this.access = access;
    }

    public List<Permission> permissions() { return permissions.all(); }

    public List<Permission> menus() {
        var actor = access.actor();
        return permissions().stream().filter(permission -> permission.type().equals("MENU")
                && (actor.admin() || actor.permissions().contains(permission.code()))).toList();
    }

    @Transactional
    public void savePermission(Long id, PermissionInput input) {
        if (id != null && id < 1000) throw Problem.bad("内置权限受保护");
        if (id != null && !input.type().equals("MENU") && permissions.hasChildren(id)) {
            throw Problem.bad("该菜单仍有子权限，不能修改为非菜单类型");
        }
        if (input.parentId() != null) {
            if (input.parentId().equals(id)) throw Problem.bad("不能关联自己");
            var parent = permissions.type(input.parentId());
            if (parent.isEmpty() || !parent.get().equals("MENU")) throw Problem.bad("父级必须是菜单");
            if (input.type().equals("MENU")) throw Problem.bad("当前版本菜单采用一级结构");
        }
        if (id == null) permissions.insert(input);
        else if (!permissions.update(id, input)) throw Problem.missing();
    }

    @Transactional
    public void deletePermission(long id) {
        if (id < 1000) throw Problem.bad("内置权限不可删除");
        if (permissions.hasChildren(id)) throw Problem.bad("请先删除子权限");
        permissions.delete(id);
    }
}
