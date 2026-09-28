package com.acme.admin;

import com.acme.admin.auth.Access;
import com.acme.admin.auth.Actor;
import com.acme.admin.system.DataScope;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DataScopeRulesTest {
    private final Access access = mock(Access.class);
    private final JdbcClient db = mock(JdbcClient.class, RETURNS_DEEP_STUBS);
    private final DataScope scope = new DataScope(db, access);

    private void actor(Set<String> scopes) {
        when(access.actor()).thenReturn(new Actor(42, "manager", 2, Set.of("user:read"), scopes, "test"));
    }

    @Test void missingScopeDeniesEveryRow() {
        actor(Set.of());
        assertEquals("1=0", scope.users().sql());
        assertTrue(scope.users().params().isEmpty());
        verifyNoInteractions(db);
    }

    @Test void selfScopeUsesBoundActorId() {
        actor(Set.of("SELF"));
        assertEquals("(u.id=:actorId)", scope.users().sql());
        assertEquals(Map.of("actorId", 42L), scope.users().params());
        verifyNoInteractions(db);
    }

    @Test void allScopeDoesNotAccidentallyRestrictToSelf() {
        actor(Set.of("ALL", "SELF"));
        assertEquals("1=1", scope.users().sql());
        verifyNoInteractions(db);
    }

    @Test void departmentTreeIncludesDescendantsButNotSiblingsAndUnionsSelf() {
        actor(Set.of("DEPT_TREE", "SELF"));
        when(db.sql("select id,parent_id from sys_dept").query().listOfRows()).thenReturn(List.of(
            Map.of("id", 5L, "parent_id", 3L),
            Map.of("id", 3L, "parent_id", 2L),
            Map.of("id", 4L, "parent_id", 1L)));
        var filter = scope.users();
        assertEquals(Set.of(2L, 3L, 5L), filter.params().get("scopeDepts"));
        assertEquals(42L, filter.params().get("actorId"));
        assertEquals("(u.id=:actorId or u.dept_id in (:scopeDepts))", filter.sql());
    }
}
