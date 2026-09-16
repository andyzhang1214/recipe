package com.recipe.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 食材别名：同一食材不同叫法（西红柿→番茄）归一到 food_calorie 条目 */
@Data
@TableName("food_alias")
public class FoodAlias {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String alias;
    @TableField("food_calorie_id")
    private Long foodCalorieId;
    private LocalDateTime createdAt;
}
