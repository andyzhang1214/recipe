package com.recipe.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 群员点餐「我想吃」：成员提交想吃的菜，群主标记完成
 */
@Data
@TableName("meal_request")
public class MealRequest {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long familyId;
    private Long userId;
    private String dishName;
    private String note;
    /** 完成心愿时发布的菜谱 id（展示「菜谱已发布」并跳详情） */
    private Long recipeId;
    /** PENDING 待做 / DONE 已完成 */
    private String status;
    private LocalDateTime createdAt;
}
