package com.acme.admin;
import org.springframework.boot.CommandLineRunner;import org.springframework.stereotype.Component;import org.springframework.jdbc.core.simple.JdbcClient;import org.springframework.security.crypto.password.PasswordEncoder;import org.springframework.beans.factory.annotation.Value;import org.springframework.transaction.annotation.Transactional;import org.springframework.scheduling.annotation.Scheduled;
@Component public class Bootstrap implements CommandLineRunner {
 private final JdbcClient db;private final PasswordEncoder passwords;private final String password;private final boolean demo;
 public Bootstrap(JdbcClient db,PasswordEncoder passwords,@Value("${app.admin-password}")String password,@Value("${app.demo:false}")boolean demo){this.db=db;this.passwords=passwords;this.password=password;this.demo=demo;}
 @Override @Transactional public void run(String...args){if(db.sql("select count(*) from sys_user where id=1").query(Long.class).single()==0){if(password.length()<12)throw new IllegalArgumentException("ADMIN_PASSWORD must contain 12+ characters");insert(1,"admin","系统管理员",1,1,password);if(demo){insert(2,"manager","林主管",2,2,"Manager-local-2026!");insert(3,"employee","陈同学",3,3,"Employee-local-2026!");insert(4,"finance","财务同事",4,3,"Finance-local-2026!");}}}
 private void insert(long id,String user,String name,long dept,long role,String pass){db.sql("insert into sys_user(id,username,password,display_name,dept_id) values(?,?,?,?,?)").params(id,user,passwords.encode(pass),name,dept).update();db.sql("insert into sys_user_role(user_id,role_id) values(?,?)").params(id,role).update();}
 @Scheduled(fixedDelay=3600000) public void cleanup(){db.sql("delete from auth_session where expires_at<CURRENT_TIMESTAMP").update();}
}
