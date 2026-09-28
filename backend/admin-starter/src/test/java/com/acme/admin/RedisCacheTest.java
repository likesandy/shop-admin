package com.acme.admin;

import static org.junit.jupiter.api.Assertions.*;

import com.acme.admin.auth.Actor;
import com.acme.admin.system.DeptCache;
import com.acme.admin.system.DictService;
import com.acme.admin.system.Models.DeptInput;
import com.acme.admin.system.Models.DictInput;
import com.acme.admin.system.DepartmentService;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.mysql.MySQLContainer;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest(properties = {
        "spring.profiles.active=local",
        "app.cache-enabled=true"
})
@EnabledIfEnvironmentVariable(named = "RUN_MYSQL_TESTS", matches = "true")
class RedisCacheTest {
    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7.4-alpine").withExposedPorts(6379);
    static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4");

    @DynamicPropertySource
    static void redis(DynamicPropertyRegistry properties) {
        REDIS.start();
        MYSQL.start();
        properties.add("spring.datasource.url", MYSQL::getJdbcUrl);
        properties.add("spring.datasource.username", MYSQL::getUsername);
        properties.add("spring.datasource.password", MYSQL::getPassword);
        properties.add("spring.data.redis.host", REDIS::getHost);
        properties.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    }

    @Autowired DictService dictionaries;
    @Autowired DeptCache departments;
    @Autowired DepartmentService system;
    @Autowired StringRedisTemplate redis;
    @Autowired JdbcClient db;
    @Autowired PlatformTransactionManager transactions;

    @Test void rollbackPreservesDatabaseAndWarmCaches() {
        String type = "rollback_cache";
        dictionaries.save(null, new DictInput(type, "原值", "original", 0, true));
        dictionaries.byType(type);
        departments.all();
        String dictCache = redis.opsForValue().get("dict:type:" + type);
        String deptCache = redis.opsForValue().get("dept:tree:v1");
        long pending = pending(type);

        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            dictionaries.save(null, new DictInput(type, "回滚值", "rolled_back", 1, true));
            system.saveDept(null, new DeptInput(1L, "必须回滚的部门"));
            assertEquals(dictCache, redis.opsForValue().get("dict:type:" + type));
            assertEquals(deptCache, redis.opsForValue().get("dept:tree:v1"));
            status.setRollbackOnly();
        });

        assertEquals(1L, db.sql("select count(*) from sys_dict where type=?").param(type).query(Long.class).single());
        assertEquals(0L, db.sql("select count(*) from sys_dept where name=?").param("必须回滚的部门").query(Long.class).single());
        assertEquals(pending, pending(type));
        assertEquals(dictCache, redis.opsForValue().get("dict:type:" + type));
        assertEquals(deptCache, redis.opsForValue().get("dept:tree:v1"));
    }

    @Test void changingTypeInvalidatesOldAndNewCacheKeys() {
        dictionaries.save(null, new DictInput("rename_old", "迁移值", "one", 0, true));
        long id = db.sql("select id from sys_dict where type='rename_old'").query(Long.class).single();
        assertEquals(1, dictionaries.byType("rename_old").size());
        assertTrue(dictionaries.byType("rename_new").isEmpty());
        dictionaries.save(id, new DictInput("rename_new", "迁移值", "one", 0, true));
        assertNull(redis.opsForValue().get("dict:type:rename_old"));
        assertNull(redis.opsForValue().get("dict:type:rename_new"));
        assertTrue(dictionaries.byType("rename_old").isEmpty());
        assertEquals(1, dictionaries.byType("rename_new").size());
    }

    @Test void redisOutageRetainsInvalidationAndRetryClearsStaleRefill() {
        String type = "outage_cache";
        String key = "dict:type:" + type;
        assertTrue(dictionaries.byType(type).isEmpty());
        String stale = redis.opsForValue().get(key);
        // Pause only this test's Redis container. Always restore it even on assertion failure.
        REDIS.getDockerClient().pauseContainerCmd(REDIS.getContainerId()).exec();
        try {
            dictionaries.save(null, new DictInput(type, "故障期间提交", "one", 0, true));
            assertEquals(1L, pending(type));
            assertEquals(1, dictionaries.byType(type).size()); // Cache failure falls back to MySQL.
            db.sql("update cache_invalidation set created_at=? where dict_type=?")
                    .params(java.sql.Timestamp.from(java.time.Instant.now().minusSeconds(60)), type).update();
            dictionaries.retry();
            assertEquals(1L, pending(type)); // Failed deletion must not consume durable work.
        } finally {
            REDIS.getDockerClient().unpauseContainerCmd(REDIS.getContainerId()).exec();
        }
        // Model an in-flight reader refilling an old value after the commit-time deletion.
        redis.opsForValue().set(key, stale);
        dictionaries.retry();
        assertEquals(0L, pending(type));
        assertNull(redis.opsForValue().get(key));
        assertEquals(1, dictionaries.byType(type).size());
        long ttl = redis.getExpire(key);
        assertTrue(ttl > 0 && ttl <= 360, "Residual cache staleness must be bounded by TTL");
    }

    private long pending(String type) {
        return db.sql("select count(*) from cache_invalidation where dict_type=?")
                .param(type).query(Long.class).single();
    }

    @Test void committedChangesEvictBothCaches() {
        assertEquals(2, dictionaries.byType("order_status").size());
        assertNotNull(redis.opsForValue().get("dict:type:order_status"));
        dictionaries.save(null, new DictInput("order_status", "已取消", "cancelled", 3, true));
        assertNull(redis.opsForValue().get("dict:type:order_status"));
        assertEquals(3, dictionaries.byType("order_status").size());

        assertEquals(4, departments.all().size());
        assertNotNull(redis.opsForValue().get("dept:tree:v1"));
        var admin = new Actor(1, "admin", 1, Set.of("*"), Set.of("ALL"), "test-session");
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(admin, null));
        try {
            system.saveDept(null, new DeptInput(1L, "缓存验证部门"));
            assertNull(redis.opsForValue().get("dept:tree:v1"));
            assertEquals(5, departments.all().size());
            long id = db.sql("select id from sys_dept where name=?").param("缓存验证部门").query(Long.class).single();
            system.deleteDept(id);
            assertNull(redis.opsForValue().get("dept:tree:v1"));
        } finally {
            SecurityContextHolder.clearContext();
        }
    }
}
