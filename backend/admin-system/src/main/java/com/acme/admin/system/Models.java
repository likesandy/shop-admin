package com.acme.admin.system;
import jakarta.validation.constraints.*;import java.util.List;
public class Models {
 public record User(long id,String username,String displayName,long deptId,String deptName,boolean enabled,List<Long> roleIds){}
 public record UserInput(@NotBlank @Pattern(regexp="[a-zA-Z0-9_]{3,32}") String username,@NotBlank @Size(max=64) String displayName,@Positive long deptId,boolean enabled,@Size(max=72) String password){}
 public record Role(long id,String code,String name,String dataScope,boolean enabled,List<Long> permissionIds){}
 public record RoleInput(@NotBlank @Pattern(regexp="[a-z][a-z0-9_]{1,31}") String code,@NotBlank @Size(max=64) String name,@Pattern(regexp="ALL|DEPT_TREE|DEPT|SELF") @NotNull String dataScope,boolean enabled,@NotNull @Size(max=100) List<@Positive Long> permissionIds){}
 public record Roles(@NotNull @Size(max=20) List<@Positive Long> roleIds){}
 public record Dept(long id,Long parentId,String name){}
 public record DeptInput(@Positive Long parentId,@NotBlank @Size(max=64) String name){}
 public record Permission(long id,String code,String name,String type,String path,Long parentId){}
 public record PermissionInput(@Pattern(regexp="[a-z][a-z0-9:_-]{1,100}") @NotBlank String code,@NotBlank @Size(max=64) String name,@Pattern(regexp="MENU|BUTTON|API") @NotNull String type,@Size(max=128) String path,@Positive Long parentId){}
 public record Dict(long id,String type,String label,String value,int sort,boolean enabled){}
 public record DictInput(@NotBlank @Pattern(regexp="[a-z][a-z0-9_]{1,63}") String type,@NotBlank @Size(max=64) String label,@NotBlank @Size(max=64) String value,@Min(0) @Max(10000) int sort,boolean enabled){}
 public record PasswordInput(@NotBlank @Size(max=72) String oldPassword,@NotBlank @Size(min=12,max=72) String newPassword){}
 public record Audit(long id,String username,String action,String method,String outcome,long durationMs,String createdAt){}
 public record Overview(long users,long departments,long roles,long operations){}
}
