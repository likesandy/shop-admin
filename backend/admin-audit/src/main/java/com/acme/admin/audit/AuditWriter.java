package com.acme.admin.audit;
import org.springframework.stereotype.Service;import org.springframework.jdbc.core.simple.JdbcClient;import org.springframework.transaction.annotation.*;
@Service public class AuditWriter {
 private final JdbcClient db;public AuditWriter(JdbcClient db){this.db=db;}
 @Transactional(propagation=Propagation.REQUIRES_NEW) public void append(String username,String action,String method,String outcome,long duration){db.sql("insert into audit_outbox(username,action,method,outcome,duration_ms) values(?,?,?,?,?)").params(username,action,method,outcome,duration).update();}
}
