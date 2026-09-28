package com.acme.admin.generated;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

@TableName("${table}")
public class ${className}Entity {
<#list columns as column>
    <#if column.column == "id">@TableId(type = IdType.AUTO)
    </#if>private ${column.type} ${column.field};
</#list>
<#list columns as column>
    public ${column.type} get${column.getter}() { return ${column.field}; }
    public void set${column.getter}(${column.type} value) { this.${column.field} = value; }
</#list>
}
