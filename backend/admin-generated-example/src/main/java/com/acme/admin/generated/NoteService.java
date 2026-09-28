package com.acme.admin.generated;

import com.acme.admin.common.Problem;
import com.acme.admin.auth.Access;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NoteService {
    private final NoteMapper mapper;
    private final Access access;
    public NoteService(NoteMapper mapper, Access access) {
        this.mapper = mapper;
        this.access = access;
    }

    private QueryWrapper<NoteEntity> visible() {
        var query = new QueryWrapper<NoteEntity>();
        if (!access.actor().admin()) query.eq("owner_id", access.actor().id());
        return query;
    }

    public List<NoteEntity> list() {
        access.require("notes:read");
        return mapper.selectList(visible().orderByAsc("id").last("limit 100"));
    }
    public NoteEntity get(long id) {
        access.require("notes:read");
        var value = mapper.selectOne(visible().eq("id", id));
        if (value == null) throw Problem.missing();
        return value;
    }
    @Transactional public long create(NoteInput input) {
        access.require("notes:write");
        visible();
        var value = new NoteEntity();
        value.setTitle(input.title());
        value.setContent(input.content());
        value.setOwnerId(access.actor().id());
        mapper.insert(value);
        return value.getId();
    }
    @Transactional public void update(long id, NoteInput input) {
        access.require("notes:write");
        visible();
        var update = new UpdateWrapper<NoteEntity>().eq("id", id);
        if (!access.actor().admin()) update.eq("owner_id", access.actor().id());
        update.set("title", input.title());
        update.set("content", input.content());
        if (mapper.update(null, update) == 0) throw Problem.missing();
    }
    @Transactional public void delete(long id) {
        access.require("notes:write");
        if (mapper.delete(visible().eq("id", id)) == 0) throw Problem.missing();
    }
}
