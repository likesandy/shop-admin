package com.acme.admin.generated;

import com.acme.admin.common.Problem;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ${className}Service {
    private final ${className}Mapper mapper;
    public ${className}Service(${className}Mapper mapper) { this.mapper = mapper; }

    public List<${className}Entity> list() {
        return mapper.selectList(new QueryWrapper<${className}Entity>().last("limit 100"));
    }
    public ${className}Entity get(long id) {
        ${className}Entity value = mapper.selectById(id);
        if (value == null) throw Problem.missing();
        return value;
    }
    @Transactional public long create(${className}Entity value) {
        value.setId(null);
        mapper.insert(value);
        return value.getId();
    }
    @Transactional public void update(long id, ${className}Entity value) {
        get(id);
        value.setId(id);
        mapper.updateById(value);
    }
    @Transactional public void delete(long id) {
        if (mapper.deleteById(id) == 0) throw Problem.missing();
    }
}
