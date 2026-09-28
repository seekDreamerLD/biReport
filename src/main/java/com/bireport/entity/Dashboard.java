package com.bireport.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("bi_dashboard")
public class Dashboard {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;

    private String configJson;

    private String shareToken;

    /** 1 已发布 0 未发布 */
    private Integer shareEnabled;

    private Long ownerId;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
