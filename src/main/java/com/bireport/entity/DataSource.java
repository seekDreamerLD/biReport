package com.bireport.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("bi_datasource")
public class DataSource {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;

    /** builtin_demo / mysql / upload */
    private String type;

    private String configJson;

    private Long ownerId;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
