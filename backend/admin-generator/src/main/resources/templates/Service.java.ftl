package com.acme.admin.generated;

import com.acme.admin.common.Problem;
import com.acme.admin.auth.Access;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ${className}Service {
    private final ${className}Mapper mapper;
    private final Access access;
    public ${className}Service(${className}Mapper mapper, Access access) {
        this.mapper = mapper;
        this.access = access;
    }

    private QueryWrapper<${className}Entity> visible() {
        var query = new QueryWrapper<${className}Entity>();
<#if ownerScoped>
        if (!access.actor().admin()) query.eq("owner_id", access.actor().id());
<#else>
        // No ownership metadata: deny non-admin access until a business scope is supplied.
        if (!access.actor().admin()) throw Problem.forbidden();
</#if>
        return query;
    }

    public List<${className}Entity> list() {
        access.require("${permission}:read");
        return mapper.selectList(visible().orderByAsc("id").last("limit 100"));
    }
    public ${className}Entity get(long id) {
        access.require("${permission}:read");
        var value = mapper.selectOne(visible().eq("id", id));
        if (value == null) throw Problem.missing();
        return value;
    }
    @Transactional public long create(${className}Input input) {
        access.require("${permission}:write");
        visible();
        var value = new ${className}Entity();
<#list inputs as column>
        value.set${column.getter}(input.${column.field}());
</#list>
<#if ownerScoped>
        value.setOwnerId(access.actor().id());
</#if>
        mapper.insert(value);
        return value.getId();
    }
    @Transactional public void update(long id, ${className}Input input) {
        access.require("${permission}:write");
        visible();
        var update = new UpdateWrapper<${className}Entity>().eq("id", id);
<#if ownerScoped>
        if (!access.actor().admin()) update.eq("owner_id", access.actor().id());
</#if>
<#list inputs as column>
        update.set("${column.column}", input.${column.field}());
</#list>
        if (mapper.update(null, update) == 0) throw Problem.missing();
    }
    @Transactional public void delete(long id) {
        access.require("${permission}:write");
        if (mapper.delete(visible().eq("id", id)) == 0) throw Problem.missing();
    }
}
