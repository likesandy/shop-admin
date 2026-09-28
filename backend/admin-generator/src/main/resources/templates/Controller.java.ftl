package com.acme.admin.generated;

import com.acme.admin.common.Api;
import com.acme.admin.common.Audited;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/generated/${resource}")
public class ${className}Controller {
    private final ${className}Service service;
    public ${className}Controller(${className}Service service) { this.service = service; }

    @GetMapping @PreAuthorize("@access.has('${permission}:read')")
    public Api<List<${className}Entity>> list() { return Api.ok(service.list()); }
    @GetMapping("/{id}") @PreAuthorize("@access.has('${permission}:read')")
    public Api<${className}Entity> get(@PathVariable long id) { return Api.ok(service.get(id)); }
    @PostMapping @PreAuthorize("@access.has('${permission}:write')") @Audited("新增${className}")
    public Api<Long> create(@Valid @RequestBody ${className}Input value) { return Api.ok(service.create(value)); }
    @PutMapping("/{id}") @PreAuthorize("@access.has('${permission}:write')") @Audited("修改${className}")
    public Api<Void> update(@PathVariable long id, @Valid @RequestBody ${className}Input value) { service.update(id, value); return Api.ok(null); }
    @DeleteMapping("/{id}") @PreAuthorize("@access.has('${permission}:write')") @Audited("删除${className}")
    public Api<Void> delete(@PathVariable long id) { service.delete(id); return Api.ok(null); }
}
