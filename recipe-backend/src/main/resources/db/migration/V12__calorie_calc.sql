-- 热量计算：别名表 + recipe 冗余热量 + ingredient 类型/匹配/克数
-- 1. 食材别名（如 西红柿→番茄），同一食材不同叫法归一到 food_calorie 条目
CREATE TABLE `food_alias` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `alias` VARCHAR(64) NOT NULL COMMENT '别名/俗称',
  `food_calorie_id` BIGINT NOT NULL COMMENT '指向 food_calorie.id',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_alias` (`alias`),
  KEY `idx_food` (`food_calorie_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='食材别名';

-- 2. recipe 冗余热量（发布/编辑时计算回填；NULL=未计算，老数据不展示）
ALTER TABLE `recipe`
  ADD COLUMN `total_calories` INT DEFAULT NULL COMMENT '总热量(千卡)，NULL=未计算',
  ADD COLUMN `calories_per_serving` INT DEFAULT NULL COMMENT '每份热量(千卡)',
  ADD COLUMN `calories_approx` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '1=存在未匹配/未填克数的食材，热量为估算值';

-- 3. ingredient 类型与匹配结果
ALTER TABLE `ingredient`
  ADD COLUMN `type` VARCHAR(16) NOT NULL DEFAULT 'FOOD' COMMENT 'FOOD=食材(计入热量)/SEASONING=调料(不计入)',
  ADD COLUMN `food_calorie_id` BIGINT DEFAULT NULL COMMENT '发布时匹配到的热量库条目',
  ADD COLUMN `amount_grams` INT DEFAULT NULL COMMENT '用量(克)，食材必填';

-- 4. 常用食材补充（《中国食物成分表》常见值，后台可继续维护）
INSERT INTO food_calorie (name, category, calories_per_100, unit, remark) VALUES
('红薯', '食品', 86, '100g', NULL),
('玉米(鲜)', '食品', 112, '根', '1 根约 200g'),
('燕麦片', '食品', 377, '100g', NULL),
('豆腐(北)', '食品', 116, '100g', NULL),
('豆腐(南)', '食品', 87, '100g', NULL),
('豆浆(无糖)', '食品', 31, '100ml', NULL),
('牛里脊', '食品', 107, '100g', '生重'),
('牛腩', '食品', 332, '100g', '生重'),
('五花肉', '食品', 568, '100g', '生重'),
('排骨', '食品', 278, '100g', '生重'),
('鸡翅', '食品', 194, '100g', '生重'),
('鸡腿', '食品', 181, '100g', '生重'),
('鸭肉', '食品', 240, '100g', '生重'),
('羊肉', '食品', 203, '100g', '生重'),
('带鱼', '食品', 127, '100g', NULL),
('鲫鱼', '食品', 108, '100g', NULL),
('草鱼', '食品', 113, '100g', NULL),
('虾(基围虾)', '食品', 101, '100g', NULL),
('虾仁', '食品', 48, '100g', NULL),
('三文鱼', '食品', 139, '100g', NULL),
('金枪鱼', '食品', 132, '100g', NULL),
('鸡蛋清', '食品', 60, '100g', NULL),
('鸡蛋黄', '食品', 328, '100g', NULL),
('大米(生)', '食品', 346, '100g', NULL),
('面粉(小麦粉)', '食品', 366, '100g', NULL),
('挂面(生)', '食品', 348, '100g', NULL),
('饺子皮', '食品', 254, '张', '1 张约 30g'),
('土豆', '食品', 77, '100g', NULL),
('山药', '食品', 57, '100g', NULL),
('芋头', '食品', 79, '100g', NULL),
('莲藕', '食品', 47, '100g', NULL),
('胡萝卜', '食品', 39, '100g', NULL),
('白萝卜', '食品', 23, '100g', NULL),
('洋葱', '食品', 40, '100g', NULL),
('大白菜', '食品', 20, '100g', NULL),
('小白菜', '食品', 15, '100g', NULL),
('生菜', '食品', 16, '100g', NULL),
('菠菜', '食品', 28, '100g', NULL),
('芹菜', '食品', 22, '100g', NULL),
('韭菜', '食品', 26, '100g', NULL),
('黄瓜', '食品', 16, '100g', NULL),
('冬瓜', '食品', 12, '100g', NULL),
('南瓜', '食品', 23, '100g', NULL),
('茄子', '食品', 23, '100g', NULL),
('青椒', '食品', 22, '100g', NULL),
('蘑菇(鲜)', '食品', 24, '100g', NULL),
('香菇(鲜)', '食品', 26, '100g', NULL),
('金针菇', '食品', 32, '100g', NULL),
('木耳(水发)', '食品', 27, '100g', NULL),
('海带(鲜)', '食品', 13, '100g', NULL),
('豆芽(绿豆芽)', '食品', 19, '100g', NULL),
('四季豆', '食品', 31, '100g', NULL),
('豇豆', '食品', 33, '100g', NULL),
('豌豆(鲜)', '食品', 111, '100g', NULL),
('花生米', '食品', 574, '100g', NULL),
('核桃仁', '食品', 646, '100g', NULL),
('芝麻', '食品', 559, '100g', NULL),
('葡萄', '食品', 45, '100g', NULL),
('橙子', '食品', 48, '个', '1 个约 180g'),
('草莓', '食品', 32, '100g', NULL),
('西瓜', '食品', 31, '100g', NULL),
('猕猴桃', '食品', 61, '个', '1 个约 100g'),
('瘦牛肉', '食品', 106, '100g', '生重'),
('鸡胸肉末', '食品', 121, '100g', '生重');

-- 5. 常用别名归一
INSERT INTO food_alias (alias, food_calorie_id)
SELECT a.alias, f.id FROM food_calorie f
JOIN (
  SELECT '西红柿' AS alias, '番茄' AS name UNION ALL
  SELECT '洋柿子', '番茄' UNION ALL
  SELECT '马铃薯', '土豆' UNION ALL
  SELECT '薯仔', '土豆' UNION ALL
  SELECT '地瓜', '红薯' UNION ALL
  SELECT '番薯', '红薯' UNION ALL
  SELECT '甘薯', '红薯' UNION ALL
  SELECT '苞米', '玉米(鲜)' UNION ALL
  SELECT '棒子', '玉米(鲜)' UNION ALL
  SELECT '卷心菜', '大白菜' UNION ALL
  SELECT '包菜', '大白菜' UNION ALL
  SELECT '圆白菜', '大白菜' UNION ALL
  SELECT '元葱', '洋葱' UNION ALL
  SELECT '青蒜', '韭菜' UNION ALL
  SELECT '胡瓜', '黄瓜' UNION ALL
  SELECT '胡萝白', '胡萝卜' UNION ALL
  SELECT '红苕', '红薯' UNION ALL
  SELECT '米', '大米(生)' UNION ALL
  SELECT '白米', '大米(生)' UNION ALL
  SELECT '面', '面粉(小麦粉)' UNION ALL
  SELECT '白面', '面粉(小麦粉)' UNION ALL
  SELECT '小麦粉', '面粉(小麦粉)' UNION ALL
  SELECT '鱼肉', '草鱼' UNION ALL
  SELECT '瘦肉', '瘦猪肉' UNION ALL
  SELECT '里脊', '瘦猪肉' UNION ALL
  SELECT '棒棒腿', '鸡腿' UNION ALL
  SELECT '大虾', '虾(基围虾)' UNION ALL
  SELECT '基围虾', '虾(基围虾)' UNION ALL
  SELECT '明虾', '虾(基围虾)' UNION ALL
  SELECT '北豆腐', '豆腐(北)' UNION ALL
  SELECT '老豆腐', '豆腐(北)' UNION ALL
  SELECT '嫩豆腐', '豆腐(南)' UNION ALL
  SELECT '南豆腐', '豆腐(南)' UNION ALL
  SELECT '清水白菇', '蘑菇(鲜)' UNION ALL
  SELECT '口蘑', '蘑菇(鲜)' UNION ALL
  SELECT '平菇', '蘑菇(鲜)' UNION ALL
  SELECT '香芋', '芋头' UNION ALL
  SELECT '藕', '莲藕' UNION ALL
  SELECT '莲菜', '莲藕' UNION ALL
  SELECT '四季青椒', '青椒' UNION ALL
  SELECT '甜椒', '青椒' UNION ALL
  SELECT '海虾', '虾(基围虾)' UNION ALL
  SELECT '雪梨', '苹果' UNION ALL
  SELECT '起司', '芝士' UNION ALL
  SELECT '番瓜', '南瓜' UNION ALL
  SELECT '倭瓜', '南瓜'
) a ON f.name = a.name;
