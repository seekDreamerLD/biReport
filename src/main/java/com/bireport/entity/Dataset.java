package com.bireport.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("bi_dataset")
public class Dataset {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;

    private Long datasourceId;

    private String sqlText;

    private String fieldsJson;

    private Long ownerId;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
