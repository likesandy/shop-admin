package com.acme.admin.generated;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

@TableName("sys_note")
public class NoteEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String title;
    private String content;
    private Long ownerId;
    public Long getId() { return id; }
    public void setId(Long value) { this.id = value; }
    public String getTitle() { return title; }
    public void setTitle(String value) { this.title = value; }
    public String getContent() { return content; }
    public void setContent(String value) { this.content = value; }
    public Long getOwnerId() { return ownerId; }
    public void setOwnerId(Long value) { this.ownerId = value; }
}
