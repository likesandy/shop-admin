package com.acme.admin.system;
import com.acme.admin.common.*;import static com.acme.admin.system.Models.*;import org.springframework.stereotype.Service;import org.springframework.jdbc.core.simple.JdbcClient;import org.springframework.data.redis.core.StringRedisTemplate;import org.springframework.beans.factory.annotation.Value;import org.springframework.transaction.annotation.Transactional;import org.springframework.transaction.event.*;import org.springframework.context.ApplicationEventPublisher;import tools.jackson.databind.ObjectMapper;import java.util.*;import java.time.Duration;
@Service
public class DictService {
 private final JdbcClient db;private final StringRedisTemplate redis;private final ApplicationEventPublisher events;private final ObjectMapper json;private final boolean cache;
 public DictService(JdbcClient db,StringRedisTemplate redis,ApplicationEventPublisher events,ObjectMapper json,@Value("${app.cache-enabled:true}") boolean cache){this.db=db;this.redis=redis;this.events=events;this.json=json;this.cache=cache;}
 public record Changed(String type){}
 @org.springframework.context.event.EventListener(org.springframework.boot.context.event.ApplicationReadyEvent.class)
 public void warmup(){if(!cache)return;for(String type:db.sql("select distinct type from sys_dict").query(String.class).list())byType(type);}
 public List<Dict> all(){return db.sql("select * from sys_dict order by type,sort,id").query(Dict.class).list();}
 public List<Dict> byType(String type){String key="dict:type:"+type;if(cache)try{var value=redis.opsForValue().get(key);if(value!=null)return Arrays.asList(json.readValue(value,Dict[].class));}catch(Exception e){org.slf4j.LoggerFactory.getLogger(getClass()).warn("Dictionary cache read unavailable");}
 var items=db.sql("select * from sys_dict where type=? and enabled=true order by sort,id").param(type).query(Dict.class).list();if(cache)try{redis.opsForValue().set(key,json.writeValueAsString(items),Duration.ofMinutes(5+new Random().nextInt(2)));}catch(Exception e){org.slf4j.LoggerFactory.getLogger(getClass()).warn("Dictionary cache write unavailable");}return items;}
 @Transactional public void save(Long id,DictInput in){String old=null;if(id!=null)old=db.sql("select type from sys_dict where id=?").param(id).query(String.class).optional().orElseThrow(Problem::missing);
 if(id==null)db.sql("insert into sys_dict(type,label,value,sort,enabled) values(?,?,?,?,?)").params(in.type(),in.label(),in.value(),in.sort(),in.enabled()).update();else db.sql("update sys_dict set type=?,label=?,value=?,sort=?,enabled=? where id=?").params(in.type(),in.label(),in.value(),in.sort(),in.enabled(),id).update();
 invalidate(in.type());if(old!=null&&!old.equals(in.type()))invalidate(old);}
 @Transactional public void delete(long id){String type=db.sql("select type from sys_dict where id=?").param(id).query(String.class).optional().orElseThrow(Problem::missing);db.sql("delete from sys_dict where id=?").param(id).update();invalidate(type);}
 private void invalidate(String type){db.sql("insert into cache_invalidation(dict_type) values(?)").param(type).update();events.publishEvent(new Changed(type));}
 @TransactionalEventListener(phase=TransactionPhase.AFTER_COMMIT) public void afterCommit(Changed e){evict(e.type());}
 private boolean evict(String type){if(!cache)return true;try{redis.delete("dict:type:"+type);return true;}catch(Exception e){return false;}}
 // Durable retries survive crashes. A delayed second eviction covers common in-flight refill races; TTL bounds residual staleness.
 @org.springframework.scheduling.annotation.Scheduled(fixedDelay=10000) public void retry(){var rows=db.sql("select id,dict_type from cache_invalidation where created_at < ? order by id limit 100").param(java.sql.Timestamp.from(java.time.Instant.now().minusSeconds(5))).query().listOfRows();for(var row:rows)if(evict((String)row.get("dict_type")))db.sql("delete from cache_invalidation where id=?").param(row.get("id")).update();}
}
