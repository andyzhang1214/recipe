package com.recipe.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("food_calorie")
public class FoodCalorie {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    /** 食品/调料 */
    private String category;
    /** 驼峰转换会成 calories_per100，需显式指定列名 */
    @TableField("calories_per_100")
    private Integer caloriesPer100;
    private String unit;
    private String remark;
    private LocalDateTime createdAt;
}
