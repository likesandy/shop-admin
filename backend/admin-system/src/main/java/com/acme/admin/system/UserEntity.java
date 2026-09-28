package com.acme.admin.system;
import com.baomidou.mybatisplus.annotation.*;
@TableName("sys_user")
public class UserEntity {
 @TableId(type=IdType.AUTO) private Long id; private String username;private String password;private String displayName;private Long deptId;private Boolean enabled;
 @TableLogic private Boolean deleted=false;
 public Long getId(){return id;}public void setId(Long v){id=v;}
 public String getUsername(){return username;}public void setUsername(String v){username=v;}
 public String getPassword(){return password;}public void setPassword(String v){password=v;}
 public String getDisplayName(){return displayName;}public void setDisplayName(String v){displayName=v;}
 public Long getDeptId(){return deptId;}public void setDeptId(Long v){deptId=v;}
 public Boolean getEnabled(){return enabled;}public void setEnabled(Boolean v){enabled=v;}
 public Boolean getDeleted(){return deleted;}public void setDeleted(Boolean v){deleted=v;}
}
