package com.acme.admin.system;
import com.acme.admin.common.*;import static com.acme.admin.system.Models.*;import org.springframework.web.bind.annotation.*;import org.springframework.security.access.prepost.PreAuthorize;import jakarta.validation.Valid;import java.util.List;
@RestController @RequestMapping("/api")
public class SystemController {
 private final UserService users;
 private final RoleService roles;
 private final DepartmentService departments;
 private final PermissionService permissions;
 private final AuditQueryService audits;
 private final OverviewService overview;
 private final DictService dict;
 public SystemController(UserService users, RoleService roles, DepartmentService departments,
                         PermissionService permissions, AuditQueryService audits,
                         OverviewService overview, DictService dict) {
     this.users=users;
     this.roles=roles;
     this.departments=departments;
     this.permissions=permissions;
     this.audits=audits;
     this.overview=overview;
     this.dict=dict;
 }
 @GetMapping("/overview") public Api<Overview> overview(){return Api.ok(overview.overview());}
 @GetMapping("/menus") public Api<List<Permission>> menus(){return Api.ok(permissions.menus());}
 @GetMapping("/users") @PreAuthorize("@access.has('user:read')") public Api<Page<User>> users(@RequestParam(defaultValue="") String q,@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="20") int size){return Api.ok(users.users(q,page,size));}
 @GetMapping("/users/{id}") @PreAuthorize("@access.has('user:read')") public Api<User> user(@PathVariable long id){return Api.ok(users.user(id));}
 @PostMapping("/users") @PreAuthorize("@access.has('user:write')") @Audited("新增用户") public Api<Long> createUser(@Valid @RequestBody UserInput in){return Api.ok(users.createUser(in));}
 @PutMapping("/users/{id}") @PreAuthorize("@access.has('user:write')") @Audited("修改用户") public Api<Void> updateUser(@PathVariable long id,@Valid @RequestBody UserInput in){users.updateUser(id,in);return Api.ok(null);}
 @DeleteMapping("/users/{id}") @PreAuthorize("@access.has('user:write')") @Audited("删除用户") public Api<Void> deleteUser(@PathVariable long id){users.deleteUser(id);return Api.ok(null);}
 @PutMapping("/users/{id}/roles") @PreAuthorize("@access.actor().admin()") @Audited("分配角色") public Api<Void> assign(@PathVariable long id,@Valid @RequestBody Roles in){users.assign(id,in);return Api.ok(null);}
 @PostMapping("/account/password") @Audited("修改个人密码") public Api<Void> password(@Valid @RequestBody PasswordInput in){users.changePassword(in);return Api.ok(null);}
 @GetMapping("/roles") @PreAuthorize("@access.actor().admin()") public Api<List<Role>> roles(){return Api.ok(roles.roles());}
 @PostMapping("/roles") @PreAuthorize("@access.actor().admin()") @Audited("新增角色") public Api<Void> createRole(@Valid @RequestBody RoleInput in){roles.saveRole(null,in);return Api.ok(null);}
 @PutMapping("/roles/{id}") @PreAuthorize("@access.actor().admin()") @Audited("修改角色") public Api<Void> updateRole(@PathVariable long id,@Valid @RequestBody RoleInput in){roles.saveRole(id,in);return Api.ok(null);}
 @DeleteMapping("/roles/{id}") @PreAuthorize("@access.actor().admin()") @Audited("删除角色") public Api<Void> deleteRole(@PathVariable long id){roles.deleteRole(id);return Api.ok(null);}
 @GetMapping("/departments") public Api<List<Dept>> departments(){return Api.ok(departments.departments());}
 @PostMapping("/departments") @PreAuthorize("@access.actor().admin()") @Audited("新增部门") public Api<Void> createDept(@Valid @RequestBody DeptInput in){departments.saveDept(null,in);return Api.ok(null);}
 @PutMapping("/departments/{id}") @PreAuthorize("@access.actor().admin()") @Audited("修改部门") public Api<Void> updateDept(@PathVariable long id,@Valid @RequestBody DeptInput in){departments.saveDept(id,in);return Api.ok(null);}
 @DeleteMapping("/departments/{id}") @PreAuthorize("@access.actor().admin()") @Audited("删除部门") public Api<Void> deleteDept(@PathVariable long id){departments.deleteDept(id);return Api.ok(null);}
 @GetMapping("/permissions") @PreAuthorize("@access.actor().admin()") public Api<List<Permission>> permissions(){return Api.ok(permissions.permissions());}
 @PostMapping("/permissions") @PreAuthorize("@access.actor().admin()") @Audited("新增权限") public Api<Void> createPerm(@Valid @RequestBody PermissionInput in){permissions.savePermission(null,in);return Api.ok(null);}
 @PutMapping("/permissions/{id}") @PreAuthorize("@access.actor().admin()") @Audited("修改权限") public Api<Void> updatePerm(@PathVariable long id,@Valid @RequestBody PermissionInput in){permissions.savePermission(id,in);return Api.ok(null);}
 @DeleteMapping("/permissions/{id}") @PreAuthorize("@access.actor().admin()") @Audited("删除权限") public Api<Void> deletePerm(@PathVariable long id){permissions.deletePermission(id);return Api.ok(null);}
 @GetMapping("/dictionaries") @PreAuthorize("@access.has('dict:read')") public Api<List<Dict>> dictionaries(){return Api.ok(dict.all());}
 @GetMapping("/dictionaries/type/{type}") public Api<List<Dict>> dictType(@PathVariable String type){return Api.ok(dict.byType(type));}
 @PostMapping("/dictionaries") @PreAuthorize("@access.has('dict:write')") @Audited("新增字典") public Api<Void> createDict(@Valid @RequestBody DictInput in){dict.save(null,in);return Api.ok(null);}
 @PutMapping("/dictionaries/{id}") @PreAuthorize("@access.has('dict:write')") @Audited("修改字典") public Api<Void> updateDict(@PathVariable long id,@Valid @RequestBody DictInput in){dict.save(id,in);return Api.ok(null);}
 @DeleteMapping("/dictionaries/{id}") @PreAuthorize("@access.has('dict:write')") @Audited("删除字典") public Api<Void> deleteDict(@PathVariable long id){dict.delete(id);return Api.ok(null);}
 @GetMapping("/audit-logs") @PreAuthorize("@access.has('audit:read')") public Api<Page<Audit>> audits(@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="20") int size){return Api.ok(audits.audits(page,size));}
}
