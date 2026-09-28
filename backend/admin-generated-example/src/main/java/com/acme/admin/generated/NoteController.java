package com.acme.admin.generated;

import com.acme.admin.common.Api;
import com.acme.admin.common.Audited;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/generated/notes")
public class NoteController {
    private final NoteService service;
    public NoteController(NoteService service) { this.service = service; }

    @GetMapping @PreAuthorize("@access.has('notes:read')")
    public Api<List<NoteEntity>> list() { return Api.ok(service.list()); }
    @GetMapping("/{id}") @PreAuthorize("@access.has('notes:read')")
    public Api<NoteEntity> get(@PathVariable long id) { return Api.ok(service.get(id)); }
    @PostMapping @PreAuthorize("@access.has('notes:write')") @Audited("新增Note")
    public Api<Long> create(@Valid @RequestBody NoteInput value) { return Api.ok(service.create(value)); }
    @PutMapping("/{id}") @PreAuthorize("@access.has('notes:write')") @Audited("修改Note")
    public Api<Void> update(@PathVariable long id, @Valid @RequestBody NoteInput value) { service.update(id, value); return Api.ok(null); }
    @DeleteMapping("/{id}") @PreAuthorize("@access.has('notes:write')") @Audited("删除Note")
    public Api<Void> delete(@PathVariable long id) { service.delete(id); return Api.ok(null); }
}
