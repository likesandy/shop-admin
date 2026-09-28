package com.acme.admin;
import org.junit.jupiter.api.*;import static org.junit.jupiter.api.Assertions.*;
import org.springframework.boot.test.context.SpringBootTest;import org.springframework.boot.test.web.server.LocalServerPort;import org.springframework.beans.factory.annotation.Autowired;import org.springframework.jdbc.core.simple.JdbcClient;import tools.jackson.databind.ObjectMapper;import tools.jackson.databind.JsonNode;
import java.net.*;import java.net.http.*;import java.util.*;
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={"spring.profiles.active=local","spring.datasource.url=jdbc:h2:mem:integration;MODE=MySQL;DATABASE_TO_LOWER=TRUE;NON_KEYWORDS=VALUE;DB_CLOSE_DELAY=-1"})
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class FullStackTest {
 @LocalServerPort int port;@Autowired ObjectMapper json;@Autowired JdbcClient db;@Autowired com.acme.admin.audit.AuditDrain drain;
 final HttpClient http=HttpClient.newHttpClient();
 record Result(int status,JsonNode body){}
 Result call(String method,String path,String token,Object body)throws Exception{var b=HttpRequest.newBuilder(URI.create("http://localhost:"+port+path)).header("Content-Type","application/json");if(token!=null)b.header("Authorization","Bearer "+token);b.method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)));var r=http.send(b.build(),HttpResponse.BodyHandlers.ofString());return new Result(r.statusCode(),json.readTree(r.body()));}
 String login(String user,String password)throws Exception{var r=call("POST","/api/auth/login",null,Map.of("username",user,"password",password));assertEquals(200,r.status(),r.body().toString());return r.body().path("data").path("token").asText();}
 String admin()throws Exception{return login("admin","Admin-local-2026!");}
 Map<String,Object> user(String name,long dept){return Map.of("username",name,"displayName","测试用户","deptId",dept,"enabled",true,"password","Secure-pass-2026!");}
 @Test @Order(1) void authenticationAndRevocation()throws Exception{
 assertEquals(401,call("GET","/api/users",null,null).status());assertEquals(401,call("GET","/api/users","invalid",null).status());
 var t=admin();assertEquals(200,call("GET","/api/auth/me",t,null).status());assertEquals(200,call("POST","/api/auth/logout",t,null).status());assertEquals(401,call("GET","/api/users",t,null).status());
 assertEquals(401,call("POST","/api/auth/login",null,Map.of("username","missing","password","wrong")).status());}
 @Test @Order(2) void rowLevelAccessAndPrivilegeEscalation()throws Exception{
 var m=login("manager","Manager-local-2026!");var r=call("GET","/api/users",m,null);assertEquals(200,r.status());assertEquals(2,r.body().path("data").path("total").asInt());
 assertEquals(403,call("GET","/api/users/4",m,null).status());assertEquals(403,call("PUT","/api/users/4",m,user("finance",4)).status());assertEquals(403,call("DELETE","/api/users/4",m,null).status());assertEquals(403,call("POST","/api/users",m,user("outside",4)).status());
 assertEquals(403,call("PUT","/api/users/3/roles",m,Map.of("roleIds",List.of(1))).status());assertEquals(403,call("GET","/api/roles",m,null).status());assertEquals(403,call("PUT","/api/users/2",m,user("manager",2)).status());
 var e=login("employee","Employee-local-2026!");assertEquals(1,call("GET","/api/users",e,null).body().path("data").path("total").asInt());assertEquals(403,call("POST","/api/users",e,user("forbidden",3)).status());assertEquals(403,call("GET","/api/audit-logs",e,null).status());}
 @Test @Order(3) void crudLogicalDeleteAndDisable()throws Exception{
 var t=admin();var created=call("POST","/api/users",t,user("crud_user",3));assertEquals(200,created.status(),created.body().toString());long id=created.body().path("data").asLong();var u=login("crud_user","Secure-pass-2026!");
 assertEquals(409,call("POST","/api/users",t,user("crud_user",3)).status());
 var updated=new HashMap<>(user("crud_user",3));updated.put("enabled",false);assertEquals(200,call("PUT","/api/users/"+id,t,updated).status());assertEquals(401,call("GET","/api/auth/me",u,null).status());
 assertEquals(200,call("DELETE","/api/users/"+id,t,null).status());assertEquals(403,call("GET","/api/users/"+id,t,null).status());assertEquals(0,call("GET","/api/users?q=crud_user",t,null).body().path("data").path("total").asInt());assertTrue(db.sql("select deleted from sys_user where id=?").param(id).query(Boolean.class).single());
 assertEquals(400,call("POST","/api/users",t,Map.of("username","!","displayName","a","deptId",1,"enabled",true,"password","short")).status());}
 @Test @Order(4) void roleChangeIsImmediateAndScopeUnion()throws Exception{
 var t=admin();var created=call("POST","/api/users",t,user("roles_user",2));long id=created.body().path("data").asLong();var u=login("roles_user","Secure-pass-2026!");assertEquals(1,call("GET","/api/users",u,null).body().path("data").path("total").asInt());
 assertEquals(200,call("POST","/api/roles",t,Map.of("code","test_role","name","测试角色","dataScope","DEPT_TREE","enabled",true,"permissionIds",List.of(10,20))).status());long role=db.sql("select id from sys_role where code='test_role'").query(Long.class).single();
 assertEquals(200,call("PUT","/api/users/"+id+"/roles",t,Map.of("roleIds",List.of(3,role))).status());assertTrue(call("GET","/api/users",u,null).body().path("data").path("total").asInt()>=3);
 assertEquals(400,call("DELETE","/api/roles/"+role,t,null).status());
 assertEquals(200,call("PUT","/api/roles/"+role,t,Map.of("code","test_role","name","测试角色","dataScope","DEPT_TREE","enabled",false,"permissionIds",List.of(10,20))).status());assertEquals(1,call("GET","/api/users",u,null).body().path("data").path("total").asInt());
 assertEquals(400,call("PUT","/api/users/"+id+"/roles",t,Map.of("roleIds",List.of(1))).status());assertEquals(200,call("PUT","/api/users/"+id+"/roles",t,Map.of("roleIds",List.of(3))).status());assertEquals(200,call("DELETE","/api/roles/"+role,t,null).status());}
 @Test @Order(5) void departmentsRejectCyclesAndReferencedDeletes()throws Exception{var t=admin();assertEquals(400,call("PUT","/api/departments/2",t,Map.of("name","研发","parentId",3)).status());assertEquals(400,call("DELETE","/api/departments/2",t,null).status());assertEquals(200,call("POST","/api/departments",t,Map.of("name","临时部门","parentId",1)).status());long id=db.sql("select id from sys_dept where name='临时部门'").query(Long.class).single();assertEquals(200,call("PUT","/api/departments/"+id,t,Map.of("name","新部门","parentId",2)).status());assertEquals(200,call("DELETE","/api/departments/"+id,t,null).status());}
 @Test @Order(6) void dictionaryChangesAndAuditRedaction()throws Exception{var t=admin();var input=new HashMap<String,Object>(Map.of("type","test_dict","label","测试","value","one","sort",1,"enabled",true));assertEquals(200,call("POST","/api/dictionaries",t,input).status());long id=db.sql("select id from sys_dict where type='test_dict'").query(Long.class).single();assertEquals(1,call("GET","/api/dictionaries/type/test_dict",t,null).body().path("data").size());input.put("type","test_other");assertEquals(200,call("PUT","/api/dictionaries/"+id,t,input).status());assertEquals(0,call("GET","/api/dictionaries/type/test_dict",t,null).body().path("data").size());assertTrue(db.sql("select count(*) from cache_invalidation").query(Long.class).single()>0);assertEquals(200,call("DELETE","/api/dictionaries/"+id,t,null).status());
 drain.drain();var logs=call("GET","/api/audit-logs",t,null);assertEquals(200,logs.status());assertFalse(logs.body().toString().contains("Secure-pass"));assertFalse(logs.body().toString().contains(t));assertTrue(db.sql("select count(*) from op_log where outcome='FAILED'").query(Long.class).single()>0);}
 @Test @Order(7) void permissionCrudAndProtectedBuiltins()throws Exception{var t=admin();var body=Map.of("code","custom:read","name","自定义","type","API","path","");assertEquals(200,call("POST","/api/permissions",t,body).status());long id=db.sql("select id from sys_perm where code='custom:read'").query(Long.class).single();assertTrue(id>=1000);assertEquals(200,call("PUT","/api/permissions/"+id,t,body).status());assertEquals(200,call("DELETE","/api/permissions/"+id,t,null).status());assertEquals(400,call("DELETE","/api/permissions/20",t,null).status());assertEquals(400,call("DELETE","/api/users/1",t,null).status());}
 @Test @Order(8) void passwordChangeRevokesAllSessions()throws Exception{var t=admin();long id=call("POST","/api/users",t,user("password_user",3)).body().path("data").asLong();var u=login("password_user","Secure-pass-2026!");assertEquals(400,call("POST","/api/account/password",u,Map.of("oldPassword","wrong","newPassword","New-password-2026!")).status());assertEquals(200,call("POST","/api/account/password",u,Map.of("oldPassword","Secure-pass-2026!","newPassword","New-password-2026!")).status());assertEquals(401,call("GET","/api/auth/me",u,null).status());assertFalse(login("password_user","New-password-2026!").isBlank());}
 @Test @Order(9) void loginLockout()throws Exception{var t=admin();call("POST","/api/users",t,user("lockout_user",3));for(int i=0;i<5;i++)assertEquals(401,call("POST","/api/auth/login",null,Map.of("username","lockout_user","password","wrong")).status());assertEquals(429,call("POST","/api/auth/login",null,Map.of("username","lockout_user","password","Secure-pass-2026!")).status());}
 @Test @Order(10) void contractAndHealth()throws Exception{var contract=call("GET","/v3/api-docs",null,null);
 assertEquals(200,contract.status());
 var root=java.nio.file.Path.of("").toAbsolutePath();
 while(root!=null&&!java.nio.file.Files.isRegularFile(root.resolve("docs/openapi.json")))root=root.getParent();
 assertNotNull(root,"Repository OpenAPI snapshot must exist");
 var expected=(tools.jackson.databind.node.ObjectNode)json.readTree(java.nio.file.Files.readString(root.resolve("docs/openapi.json")));
 var actual=(tools.jackson.databind.node.ObjectNode)contract.body();
 // RANDOM_PORT changes only the server URL; all paths, schemas and security metadata must match.
 expected.remove("servers");actual.remove("servers");
 assertEquals(expected,actual,"Live OpenAPI differs from docs/openapi.json");assertEquals(200,call("GET","/actuator/health",null,null).status());assertEquals(401,call("GET","/actuator/prometheus",null,null).status());}

 @Test @Order(12) void overviewAndMenusRespectTheCurrentUserScope() throws Exception {
     for (String token : List.of(admin(), login("manager", "Manager-local-2026!"), login("employee", "Employee-local-2026!"))) {
         var overview = call("GET", "/api/overview", token, null);
         assertEquals(200, overview.status());
         assertEquals(call("GET", "/api/users", token, null).body().path("data").path("total").asLong(),
                 overview.body().path("data").path("users").asLong());
         assertEquals(call("GET", "/api/departments", token, null).body().path("data").size(),
                 overview.body().path("data").path("departments").asInt());
         var menus = call("GET", "/api/menus", token, null);
         assertEquals(200, menus.status());
         assertTrue(menus.body().path("data").size() > 0);
     }
     var employee = login("employee", "Employee-local-2026!");
     var stats = call("GET", "/api/overview", employee, null).body().path("data");
     assertEquals(0, stats.path("roles").asInt());
     assertEquals(0, stats.path("operations").asInt());
     var menus = call("GET", "/api/menus", employee, null).body().path("data");
     for (var menu : menus) assertTrue(Set.of("menu:users", "menu:dictionaries").contains(menu.path("code").asText()));
     var adminStats = call("GET", "/api/overview", admin(), null).body().path("data");
     assertEquals(db.sql("select count(*) from sys_role").query(Long.class).single(), adminStats.path("roles").asLong());
 }

 @Test @Order(13) void permissionHierarchyRejectsInvalidParentsAndReferencedDeletion() throws Exception {
     String token = admin();
     assertEquals(200, call("POST", "/api/permissions", token,
             Map.of("code", "menu:split_test", "name", "测试菜单", "type", "MENU", "path", "/split-test")).status());
     long parent = db.sql("select id from sys_perm where code='menu:split_test'").query(Long.class).single();
     var child = new HashMap<String, Object>(Map.of("code", "split:read", "name", "测试权限", "type", "API", "parentId", parent));
     assertEquals(200, call("POST", "/api/permissions", token, child).status());
     long id = db.sql("select id from sys_perm where code='split:read'").query(Long.class).single();
     var parentUpdate = new HashMap<String, Object>(Map.of("code", "menu:split_test",
             "name", "更新后的菜单", "type", "MENU", "path", "/split-updated"));
     assertEquals(200, call("PUT", "/api/permissions/" + parent, token, parentUpdate).status());
     assertEquals("更新后的菜单", db.sql("select name from sys_perm where id=?").param(parent).query(String.class).single());
     assertEquals("/split-updated", db.sql("select path from sys_perm where id=?").param(parent).query(String.class).single());
     for (String type : List.of("API", "BUTTON")) {
         parentUpdate.put("type", type);
         parentUpdate.put("name", "不应保存的名称");
         assertEquals(400, call("PUT", "/api/permissions/" + parent, token, parentUpdate).status(),
                 "A parent with children must remain a menu");
         assertEquals("MENU", db.sql("select type from sys_perm where id=?").param(parent).query(String.class).single());
         assertEquals("更新后的菜单", db.sql("select name from sys_perm where id=?").param(parent).query(String.class).single());
         assertEquals(parent, db.sql("select parent_id from sys_perm where id=?").param(id).query(Long.class).single());
     }
     assertEquals(400, call("DELETE", "/api/permissions/" + parent, token, null).status());
     child.put("parentId", id);
     assertEquals(400, call("PUT", "/api/permissions/" + id, token, child).status());
     child.put("parentId", 20L);
     assertEquals(400, call("PUT", "/api/permissions/" + id, token, child).status());
     child.put("parentId", parent);
     child.put("type", "MENU");
     assertEquals(400, call("PUT", "/api/permissions/" + id, token, child).status());
     assertEquals(200, call("DELETE", "/api/permissions/" + id, token, null).status());
     for (String type : List.of("API", "BUTTON")) {
         parentUpdate.put("type", type);
         assertEquals(200, call("PUT", "/api/permissions/" + parent, token, parentUpdate).status());
         assertEquals(type, db.sql("select type from sys_perm where id=?").param(parent).query(String.class).single());
     }
     assertEquals(200, call("DELETE", "/api/permissions/" + parent, token, null).status());
 }

 @Test @Order(11) void failedRoleAssignmentRollsBackAndKeepsAudit() throws Exception {
     String token = admin();
     long id = call("POST", "/api/users", token, user("rollback_roles_user", 3)).body().path("data").asLong();
     drain.drain();
     long failures = db.sql("select count(*) from op_log where action='分配角色' and outcome='FAILED'").query(Long.class).single();
     // The service deletes the old associations before inserting these: the FK failure must undo both writes.
     assertEquals(409, call("PUT", "/api/users/" + id + "/roles", token,
             Map.of("roleIds", List.of(2L, 999999L))).status());
     assertEquals(List.of(3L), db.sql("select role_id from sys_user_role where user_id=? order by role_id")
             .param(id).query(Long.class).list());
     drain.drain();
     assertEquals(failures + 1, db.sql("select count(*) from op_log where action='分配角色' and outcome='FAILED'").query(Long.class).single());
     String audit = json.writeValueAsString(db.sql("select * from op_log").query().listOfRows());
     assertFalse(audit.contains(token));
     assertFalse(audit.contains("Secure-pass-2026!"));
     assertFalse(audit.contains("rollback_roles_user"));
 }
}
