package com.acme.admin.audit;
import com.acme.admin.auth.Access;import com.acme.admin.common.Audited;import org.aspectj.lang.ProceedingJoinPoint;import org.aspectj.lang.annotation.*;import org.springframework.stereotype.Component;import org.springframework.core.annotation.Order;
@Aspect @Component @Order(1000)
public class AuditAspect {
 private final AuditWriter writer;private final Access access;
 public AuditAspect(AuditWriter writer,Access access){this.writer=writer;this.access=access;}
 @Around("@annotation(audited)") public Object around(ProceedingJoinPoint point,Audited audited)throws Throwable{long start=System.nanoTime();String outcome="SUCCESS";try{return point.proceed();}catch(Throwable e){outcome="FAILED";throw e;}finally{
 // Intentionally store no request bodies, passwords, tokens or exception messages.
 try{writer.append(access.actor().username(),audited.value(),point.getSignature().toShortString(),outcome,(System.nanoTime()-start)/1000000);}catch(Exception e){org.slf4j.LoggerFactory.getLogger(getClass()).error("AUDIT_WRITE_FAILED action={} outcome={}",audited.value(),outcome);}
 }}
}
