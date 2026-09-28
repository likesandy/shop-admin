package com.acme.admin.system;

import com.acme.admin.auth.Access;
import com.acme.admin.common.Problem;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.HashSet;
import static com.acme.admin.system.Models.*;

@Service
public class DepartmentService {
    private final DepartmentRepository departments;
    private final DeptCache cache;
    private final DataScope scope;
    private final Access access;
    private final ApplicationEventPublisher events;

    public DepartmentService(DepartmentRepository departments, DeptCache cache, DataScope scope,
                             Access access, ApplicationEventPublisher events) {
        this.departments = departments;
        this.cache = cache;
        this.scope = scope;
        this.access = access;
        this.events = events;
    }

    public List<Dept> departments() {
        var all = cache.all();
        if (scope.all()) return all;
        var ids = scope.departments();
        ids.add(access.actor().deptId());
        return all.stream().filter(dept -> ids.contains(dept.id())).toList();
    }

    @Transactional
    public void saveDept(Long id, DeptInput input) {
        departments.lockTree();
        if (id != null && id == 1) throw Problem.bad("根部门受保护");
        if (input.parentId() == null) throw Problem.bad("请选择上级部门");
        var parents = departments.parents();
        if (!parents.containsKey(input.parentId())) throw Problem.bad("上级部门不存在");
        var seen = new HashSet<Long>();
        Long cursor = input.parentId();
        while (cursor != null) {
            if (cursor.equals(id) || !seen.add(cursor)) throw Problem.bad("部门不能循环挂载");
            cursor = parents.get(cursor);
        }
        if (id == null) departments.insert(input);
        else if (!departments.update(id, input)) throw Problem.missing();
        events.publishEvent(new DeptCache.Changed());
    }

    @Transactional
    public void deleteDept(long id) {
        if (id == 1) throw Problem.bad("根部门不可删除");
        if (departments.hasReferences(id)) throw Problem.bad("部门仍有下级或历史用户引用");
        if (!departments.delete(id)) throw Problem.missing();
        events.publishEvent(new DeptCache.Changed());
    }
}
