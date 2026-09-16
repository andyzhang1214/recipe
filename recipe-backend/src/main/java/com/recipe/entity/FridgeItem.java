package com.recipe.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("fridge_item")
public class FridgeItem {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String name;
    private String category;
    private BigDecimal quantity;
    private String unit;
    private LocalDate addedAt;
    private LocalDate expiryDate;
    private Integer reminderDays;
    private Long familyId;
    private String note;
    private String photoUrl;
    private LocalDateTime createdAt;
}
