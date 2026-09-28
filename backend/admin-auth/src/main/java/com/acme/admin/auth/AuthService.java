package com.acme.admin.auth;
import com.acme.admin.common.*;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import java.time.*;
import java.util.*;
@Service
public class AuthService {
 private final JdbcClient db;private final Tokens tokens;private final PasswordEncoder passwords;
 private final String dummy;
 public AuthService(JdbcClient db,Tokens tokens,PasswordEncoder passwords){this.db=db;this.tokens=tokens;this.passwords=passwords;dummy=passwords.encode("timing-padding");}
 public record LoginResult(String token,long expiresIn){}
 // Only credential rejection commits failure counters. Infrastructure errors still roll back.
 static final class LoginRejected extends Problem {
     LoginRejected(HttpStatus status,String message){super(status,message);}
 }
 @Transactional(noRollbackFor=LoginRejected.class)
 public LoginResult login(String username,String password){
 var rows=db.sql("select id,password,enabled,failed_logins,locked_until from sys_user where username=? and deleted=false for update").param(username).query().listOfRows();
 if(rows.isEmpty()){passwords.matches(password,dummy);throw new LoginRejected(HttpStatus.UNAUTHORIZED,"用户名或密码错误");}
 var u=rows.getFirst();long id=((Number)u.get("id")).longValue();
 var now=Instant.now();
 if(u.get("locked_until")!=null && ((java.sql.Timestamp)u.get("locked_until")).toInstant().isAfter(now))throw new LoginRejected(HttpStatus.TOO_MANY_REQUESTS,"登录尝试过多，请稍后重试");
 if(!Boolean.TRUE.equals(u.get("enabled")) || !passwords.matches(password,(String)u.get("password"))){
     int failures=u.get("locked_until")!=null?1:((Number)u.get("failed_logins")).intValue()+1;
     db.sql("update sys_user set failed_logins=?,locked_until=? where id=?")
             .params(Arrays.asList(failures,failures>=5?java.sql.Timestamp.from(now.plusSeconds(300)):null,id)).update();
     throw new LoginRejected(HttpStatus.UNAUTHORIZED,"用户名或密码错误");
 }
 db.sql("update sys_user set failed_logins=0,locked_until=null where id=?").param(id).update();
 String sid=UUID.randomUUID().toString();var expiry=Instant.now().plusSeconds(7200);
 db.sql("insert into auth_session(id,user_id,expires_at) values(?,?,?)").params(sid,id,java.sql.Timestamp.from(expiry)).update();
 return new LoginResult(tokens.sign(id,sid,expiry),7200);
 }
 public Actor resolve(String token){var c=tokens.verify(token);if(c==null)return null;long id;try{id=Long.parseLong(c.getSubject());}catch(Exception e){return null;}
 var rows=db.sql("select u.id,u.username,u.dept_id from sys_user u join auth_session s on s.user_id=u.id where u.id=? and s.id=? and s.expires_at>CURRENT_TIMESTAMP and u.enabled=true and u.deleted=false").params(id,c.getJWTID()).query().listOfRows();if(rows.isEmpty())return null;
 var p=new HashSet<>(db.sql("select distinct p.code from sys_perm p join sys_role_perm rp on rp.perm_id=p.id join sys_user_role ur on ur.role_id=rp.role_id join sys_role r on r.id=ur.role_id where ur.user_id=? and r.enabled=true").param(id).query(String.class).list());
 var scopes=new HashSet<>(db.sql("select r.data_scope from sys_role r join sys_user_role ur on ur.role_id=r.id where ur.user_id=? and r.enabled=true").param(id).query(String.class).list());
 var u=rows.getFirst();return new Actor(id,(String)u.get("username"),((Number)u.get("dept_id")).longValue(),p,scopes,c.getJWTID());
 }
 public void logout(Actor a){db.sql("delete from auth_session where id=?").param(a.sessionId()).update();}
}
