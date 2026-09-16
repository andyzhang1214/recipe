package com.recipe.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("ingredient")
public class Ingredient {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long recipeId;
    private String name;
    private String amount;
    private String unit;
    private Integer sort;
    /** FOOD=食材(计入热量)/SEASONING=调料(不计入) */
    private String type;
    /** 发布时匹配到的热量库条目 */
    private Long foodCalorieId;
    /** 用量(克)，食材必填 */
    private Integer amountGrams;
}
