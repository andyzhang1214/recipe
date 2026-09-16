package com.recipe.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 冰箱操作记录：谁添加/修改/删除了食材 */
@Data
@TableName("fridge_log")
public class FridgeLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 队伍冰箱时记录 */
    private Long familyId;
    /** 个人/共享冰箱主人 */
    private Long ownerUserId;
    private Long operatorId;
    /** ADD/UPDATE/DELETE */
    private String action;
    private String itemName;
    private String detail;
    private LocalDateTime createdAt;
}
