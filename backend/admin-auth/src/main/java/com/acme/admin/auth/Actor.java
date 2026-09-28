package com.acme.admin.auth;
import java.util.Set;
public record Actor(long id,String username,long deptId,Set<String> permissions,Set<String> scopes,String sessionId) {
 public boolean admin(){return permissions.contains("*");}
}
