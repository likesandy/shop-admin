package com.acme.admin.system;

import com.acme.admin.common.Page;
import org.springframework.stereotype.Service;
import static com.acme.admin.system.Models.*;

@Service
public class AuditQueryService {
    private final AuditQueryRepository audits;
    public AuditQueryService(AuditQueryRepository audits) { this.audits = audits; }

    public Page<Audit> audits(int page, int size) {
        return audits.page(Math.max(1, page), Math.min(100, Math.max(1, size)));
    }
}
