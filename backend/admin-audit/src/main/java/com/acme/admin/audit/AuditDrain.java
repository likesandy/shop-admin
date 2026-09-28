package com.acme.admin.audit;
import org.springframework.stereotype.Service;import org.springframework.jdbc.core.simple.JdbcClient;import org.springframework.scheduling.annotation.Scheduled;import org.springframework.transaction.annotation.Transactional;
@Service public class AuditDrain {
 private final JdbcClient db;public AuditDrain(JdbcClient db){this.db=db;}
 @Scheduled(fixedDelay=250) @Transactional public void drain(){
 var rows=db.sql("select * from audit_outbox order by id limit 100 for update").query().listOfRows();
 for(var r:rows){db.sql("insert into op_log(username,action,method,outcome,duration_ms,created_at) values(?,?,?,?,?,?)").params(r.get("username"),r.get("action"),r.get("method"),r.get("outcome"),r.get("duration_ms"),r.get("created_at")).update();db.sql("delete from audit_outbox where id=?").param(r.get("id")).update();}
 }
}
