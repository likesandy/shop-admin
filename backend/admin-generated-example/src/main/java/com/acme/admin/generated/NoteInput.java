package com.acme.admin.generated;

import jakarta.validation.constraints.*;

// Generated writable fields only. Identity and ownership always come from the server.
public record NoteInput(
    @NotBlank @Size(max = 64) String title,
    @Size(max = 255) String content
) {}
