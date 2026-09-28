package com.acme.admin.system;

import com.acme.admin.auth.Access;
import org.springframework.stereotype.Service;
import static com.acme.admin.system.Models.*;

@Service
public class OverviewService {
    private final OverviewRepository overview;
    private final DepartmentService departments;
    private final AuditQueryRepository audits;
    private final DataScope scope;
    private final Access access;

    public OverviewService(OverviewRepository overview, DepartmentService departments,
                           AuditQueryRepository audits, DataScope scope, Access access) {
        this.overview = overview;
        this.departments = departments;
        this.audits = audits;
        this.scope = scope;
        this.access = access;
    }

    public Overview overview() {
        return new Overview(overview.users(scope.users()), departments.departments().size(),
                access.actor().admin() ? overview.roles() : 0,
                access.has("audit:read") ? audits.count() : 0);
    }
}
