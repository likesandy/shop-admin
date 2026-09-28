package com.acme.admin.system;

import static com.acme.admin.system.Models.Dept;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import tools.jackson.databind.ObjectMapper;

@Service
public class DeptCache {
    private static final String KEY = "dept:tree:v1";
    private final JdbcClient db;
    private final StringRedisTemplate redis;
    private final ObjectMapper json;
    private final boolean enabled;

    public record Changed() {}

    public DeptCache(JdbcClient db, StringRedisTemplate redis, ObjectMapper json,
                     @Value("${app.cache-enabled:true}") boolean enabled) {
        this.db = db;
        this.redis = redis;
        this.json = json;
        this.enabled = enabled;
    }

    // This cache feeds the organization UI only. DataScope always reads the database.
    public List<Dept> all() {
        if (enabled) {
            try {
                String cached = redis.opsForValue().get(KEY);
                if (cached != null) return Arrays.asList(json.readValue(cached, Dept[].class));
            } catch (Exception e) {
                LoggerFactory.getLogger(getClass()).warn("Department cache read unavailable");
            }
        }
        List<Dept> departments = db.sql("select id,parent_id,name from sys_dept order by id").query(Dept.class).list();
        if (enabled) {
            try {
                redis.opsForValue().set(KEY, json.writeValueAsString(departments), Duration.ofMinutes(2));
            } catch (Exception e) {
                LoggerFactory.getLogger(getClass()).warn("Department cache write unavailable");
            }
        }
        return departments;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void invalidate(Changed event) {
        if (enabled) {
            try {
                redis.delete(KEY);
            } catch (Exception e) {
                LoggerFactory.getLogger(getClass()).warn("Department cache invalidation unavailable");
            }
        }
    }
}
