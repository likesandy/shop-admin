package com.acme.admin;
import org.junit.jupiter.api.*;import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;import org.springframework.boot.test.context.SpringBootTest;import org.springframework.test.context.DynamicPropertyRegistry;import org.springframework.test.context.DynamicPropertySource;import org.testcontainers.mysql.MySQLContainer;
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={"spring.profiles.active=local"})
@EnabledIfEnvironmentVariable(named="RUN_MYSQL_TESTS",matches="true")
class MySqlTest extends FullStackTest {
 @org.springframework.beans.factory.annotation.Autowired com.acme.admin.auth.AuthService auth;
 @org.springframework.test.context.bean.override.mockito.MockitoSpyBean org.springframework.security.crypto.password.PasswordEncoder passwords;
 @org.springframework.beans.factory.annotation.Autowired com.acme.admin.auth.Access access;
 static final MySQLContainer MYSQL=new MySQLContainer("mysql:8.4");
 @DynamicPropertySource static void mysql(DynamicPropertyRegistry r){MYSQL.start();r.add("spring.datasource.url",MYSQL::getJdbcUrl);r.add("spring.datasource.username",MYSQL::getUsername);r.add("spring.datasource.password",MYSQL::getPassword);}

 @Test void rejectedLoginCommitsCounterBeforeReturning() throws Exception {
     var token=admin();
     call("POST","/api/users",token,user("atomic_login",3));
     org.junit.jupiter.api.Assertions.assertThrows(com.acme.admin.common.Problem.class,
             () -> auth.login("atomic_login","wrong"));
     org.junit.jupiter.api.Assertions.assertEquals(1, db.sql("select failed_logins from sys_user where username='atomic_login'").query(Integer.class).single());
 }

 @Test void expiredLockStartsNewAttemptWindow() throws Exception {
     var token=admin();
     call("POST","/api/users",token,user("expired_lock",3));
     db.sql("update sys_user set failed_logins=5,locked_until=? where username='expired_lock'")
             .param(java.sql.Timestamp.from(java.time.Instant.now().minusSeconds(1))).update();
     org.junit.jupiter.api.Assertions.assertEquals(401,call("POST","/api/auth/login",null,
             java.util.Map.of("username","expired_lock","password","wrong")).status());
     org.junit.jupiter.api.Assertions.assertEquals(1,db.sql("select failed_logins from sys_user where username='expired_lock'").query(Integer.class).single());
     org.junit.jupiter.api.Assertions.assertEquals(200,call("POST","/api/auth/login",null,
             java.util.Map.of("username","expired_lock","password","Secure-pass-2026!")).status());
 }

 @Test void concurrentFailuresStopAtFiveAndLockedRequestsDoNotExtendDeadline() throws Exception {
     call("POST","/api/users",admin(),user("parallel_login",3));
     var start=new java.util.concurrent.CountDownLatch(1);
     try(var executor=java.util.concurrent.Executors.newFixedThreadPool(8)) {
         var results=new java.util.ArrayList<java.util.concurrent.Future<Integer>>();
         for(int i=0;i<8;i++) results.add(executor.submit(() -> {
             start.await();
             return call("POST","/api/auth/login",null,java.util.Map.of("username","parallel_login","password","wrong")).status();
         }));
         start.countDown();
         var statuses=new java.util.ArrayList<Integer>();
         for(var result:results) statuses.add(result.get(20,java.util.concurrent.TimeUnit.SECONDS));
         org.junit.jupiter.api.Assertions.assertEquals(5,java.util.Collections.frequency(statuses,401));
         org.junit.jupiter.api.Assertions.assertEquals(3,java.util.Collections.frequency(statuses,429));
     }
     var deadline=db.sql("select locked_until from sys_user where username='parallel_login'").query(java.sql.Timestamp.class).single();
     org.junit.jupiter.api.Assertions.assertEquals(429,call("POST","/api/auth/login",null,
             java.util.Map.of("username","parallel_login","password","Secure-pass-2026!")).status());
     org.junit.jupiter.api.Assertions.assertEquals(deadline,db.sql("select locked_until from sys_user where username='parallel_login'").query(java.sql.Timestamp.class).single());
     org.junit.jupiter.api.Assertions.assertEquals(5,db.sql("select failed_logins from sys_user where username='parallel_login'").query(Integer.class).single());
 }

 @Test void roleAssignmentCannotOvertakeAnAuthorizedUserMutation() throws Exception {
     String administrator=admin();
     long id=call("POST","/api/users",administrator,user("race_target",3)).body().path("data").asLong();
     String manager=login("manager","Manager-local-2026!");
     var checked=new java.util.concurrent.CountDownLatch(1);
     var release=new java.util.concurrent.CountDownLatch(1);
     org.mockito.Mockito.doAnswer(invocation -> {
         Object encoded=invocation.callRealMethod();
         if(access.actor().username().equals("manager")) {
             checked.countDown();
             if(!release.await(10,java.util.concurrent.TimeUnit.SECONDS)) throw new AssertionError("Mutation was not released");
         }
         return encoded;
     }).when(passwords).encode("Secure-pass-2026!");
     try(var executor=java.util.concurrent.Executors.newFixedThreadPool(2)) {
         var mutation=executor.submit(() -> call("PUT","/api/users/"+id,manager,user("race_target",3)));
         try {
             org.junit.jupiter.api.Assertions.assertTrue(checked.await(10,java.util.concurrent.TimeUnit.SECONDS));
             var assigned=executor.submit(() -> call("PUT","/api/users/"+id+"/roles",administrator,java.util.Map.of("roleIds",java.util.List.of(2L))));
             try {
                 org.junit.jupiter.api.Assertions.assertThrows(java.util.concurrent.TimeoutException.class,
                         () -> assigned.get(750,java.util.concurrent.TimeUnit.MILLISECONDS),
                         "Role assignment must wait until the target user's current mutation commits");
             } finally { release.countDown(); }
             org.junit.jupiter.api.Assertions.assertEquals(200,mutation.get(10,java.util.concurrent.TimeUnit.SECONDS).status());
             org.junit.jupiter.api.Assertions.assertEquals(200,assigned.get(10,java.util.concurrent.TimeUnit.SECONDS).status());
         } finally { release.countDown(); }
     } finally { org.mockito.Mockito.reset(passwords); }
     org.junit.jupiter.api.Assertions.assertEquals(403,call("PUT","/api/users/"+id,manager,user("race_target",3)).status());
 }
}
