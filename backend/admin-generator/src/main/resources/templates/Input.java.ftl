package com.acme.admin.generated;

import jakarta.validation.constraints.*;

// Generated writable fields only. Identity and ownership always come from the server.
public record ${className}Input(
<#list inputs as column>
    <#if column.required><#if column.type == "String">@NotBlank<#else>@NotNull</#if> </#if><#if column.type == "String">@Size(max = ${column.length?c}) </#if>${column.type} ${column.field}<#sep>,</#sep>
</#list>
) {}
