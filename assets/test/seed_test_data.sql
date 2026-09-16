-- ============================================================
-- 拾味星球 测试数据（覆盖 广场/详情/圈层/冰箱/点餐/Banner）
-- 适用：recipe 库（云托管 CynosDB，MySQL 5.7，utf8mb4）
-- 执行：云托管 → 数据库(MySQL) → SQL 执行 → 全选粘贴运行
--       或 mysql -h <DB_HOST> -P 3306 -u recipe -p recipe < seed_test_data.sql
-- 说明：可重复执行会因主键冲突报错，跑一次即可
-- ============================================================

-- ---------- 1. 用户（4 个） ----------
INSERT INTO `user` (`id`,`openid`,`nickname`,`avatar`) VALUES
(1,'test_openid_0001','小暖',NULL),
(2,'test_openid_0002','阿泽',NULL),
(3,'test_openid_0003','团子',NULL),
(4,'test_openid_0004','果果',NULL);

-- ---------- 2. 圈层（注意：V5 已删除 type 字段，不要带） ----------
INSERT INTO `family` (`id`,`name`,`owner_id`,`join_code`,`max_members`,`member_count`,`approval_required`) VALUES
(1,'温馨小家',1,'WXJG001',8,3,1),
(2,'干饭联盟',2,'GFLM001',20,2,0);

-- ---------- 3. 圈层成员 ----------
INSERT INTO `family_member` (`family_id`,`user_id`,`role`,`status`,`joined_at`) VALUES
(1,1,'OWNER','APPROVED',NOW()),
(1,2,'MEMBER','APPROVED',NOW()),
(1,3,'MEMBER','PENDING',NULL),
(2,2,'OWNER','APPROVED',NOW()),
(2,4,'MEMBER','APPROVED',NOW());

-- ---------- 4. 菜谱（6 道公开，广场可刷出） ----------
INSERT INTO `recipe` (`id`,`author_id`,`title`,`cover`,`covers`,`visibility`,`difficulty`,`servings`,`status`,`avg_stars`,`rating_count`) VALUES
(1,1,'番茄炒蛋',NULL,NULL,'PUBLIC','简单',2,'PUBLISHED',4.50,2),
(2,2,'红烧肉',NULL,NULL,'PUBLIC','中等',4,'PUBLISHED',5.00,2),
(3,3,'清炒西兰花',NULL,NULL,'PUBLIC','简单',2,'PUBLISHED',4.00,1),
(4,4,'可乐鸡翅',NULL,NULL,'PUBLIC','简单',3,'PUBLISHED',5.00,1),
(5,1,'皮蛋瘦肉粥',NULL,NULL,'PUBLIC','简单',3,'PUBLISHED',4.00,1),
(6,2,'蒜蓉粉丝蒸虾',NULL,NULL,'PUBLIC','中等',4,'PUBLISHED',5.00,1);

-- ---------- 5. 食材清单 ----------
INSERT INTO `ingredient` (`recipe_id`,`name`,`amount`,`unit`,`sort`) VALUES
(1,'番茄','2','个',0),
(1,'鸡蛋','3','个',1),
(1,'葱花','1','小把',2),
(1,'盐','1','勺',3),
(1,'糖','1','勺',4),
(2,'五花肉','500','克',0),
(2,'冰糖','20','克',1),
(2,'生抽','2','勺',2),
(2,'老抽','1','勺',3),
(2,'姜片','3','片',4),
(3,'西兰花','1','颗',0),
(3,'蒜末','2','瓣',1),
(3,'盐','1','勺',2),
(4,'鸡翅中','10','个',0),
(4,'可乐','1','罐',1),
(4,'生抽','2','勺',2),
(4,'姜','3','片',3),
(5,'大米','100','克',0),
(5,'皮蛋','2','个',1),
(5,'猪里脊','100','克',2),
(5,'姜丝','少许','',3),
(6,'基围虾','500','克',0),
(6,'粉丝','1','把',1),
(6,'蒜','6','瓣',2),
(6,'蒸鱼豉油','2','勺',3);

-- ---------- 6. 步骤（seasoning 为 V6 新字段） ----------
INSERT INTO `step` (`recipe_id`,`ord`,`text`,`seasoning`,`image`,`duration_sec`) VALUES
(1,0,'番茄切块，鸡蛋打散加少许盐。','盐',NULL,120),
(1,1,'热油下蛋液炒至凝固盛出。','油',NULL,180),
(1,2,'下番茄炒出汁，加糖调味。','糖',NULL,240),
(1,3,'倒回鸡蛋翻炒均匀即可出锅。',NULL,NULL,120),
(2,0,'五花肉切块焯水去浮沫。',NULL,NULL,300),
(2,1,'小火炒冰糖至琥珀色，下肉块上色。','冰糖',NULL,300),
(2,2,'加生抽老抽姜片，倒热水没过肉。','生抽/老抽',NULL,120),
(2,3,'小火炖 40 分钟，大火收汁。',NULL,NULL,2400),
(3,0,'西兰花掰小朵，盐水浸泡 10 分钟。','盐',NULL,600),
(3,1,'沸水焯 1 分钟捞出过凉。',NULL,NULL,60),
(3,2,'热油爆香蒜末，下西兰花翻炒调味。','蒜/盐',NULL,180),
(4,0,'鸡翅两面划刀，焯水去腥。',NULL,NULL,300),
(4,1,'少油煎至两面金黄。','油',NULL,300),
(4,2,'倒入可乐没过鸡翅，加生抽姜片。','可乐/生抽',NULL,120),
(4,3,'中小火煮 20 分钟，大火收汁。',NULL,NULL,1200),
(5,0,'大米洗净，皮蛋切丁，里脊切丝腌制。','盐/淀粉',NULL,300),
(5,1,'米加水煮开花，转小火。',NULL,NULL,1200),
(5,2,'下皮蛋和肉丝煮 5 分钟，加姜丝调味。','姜/盐',NULL,300),
(6,0,'粉丝温水泡软垫盘底。',NULL,NULL,300),
(6,1,'虾开背去虾线，摆盘。',NULL,NULL,300),
(6,2,'蒜末爆香铺在虾上，蒸 6 分钟。','蒜/油',NULL,360),
(6,3,'淋蒸鱼豉油，撒葱花热油激香。','蒸鱼豉油',NULL,120);

-- ---------- 7. 评分（与 recipe.avg_stars/rating_count 对齐） ----------
INSERT INTO `rating` (`recipe_id`,`user_id`,`stars`) VALUES
(1,2,5),(1,3,4),
(2,1,5),(2,3,5),
(3,1,4),
(4,4,5),
(5,2,4),
(6,1,5);

-- ---------- 8. 评论（含 1 条楼中楼回复） ----------
INSERT INTO `comment` (`id`,`recipe_id`,`user_id`,`content`,`parent_id`) VALUES
(1,1,2,'太香了，超级下饭！',NULL),
(2,1,3,'确实，我能吃两碗饭。',1),
(3,2,1,'肥而不腻，绝了！',NULL),
(4,4,2,'孩子最爱吃这个。',NULL),
(5,5,4,'早餐绝配，暖胃。',NULL);

-- ---------- 9. 收藏 ----------
INSERT INTO `favorite` (`user_id`,`recipe_id`) VALUES
(2,1),(3,2),(4,3),(1,6);

-- ---------- 10. 冰箱（日期用相对日期，数据永不过期；临期演示：牛奶明天到期） ----------
INSERT INTO `fridge_item` (`user_id`,`name`,`category`,`quantity`,`unit`,`added_at`,`expiry_date`,`reminder_days`) VALUES
(1,'西红柿','蔬菜',2.00,'个',DATE_SUB(CURDATE(),INTERVAL 2 DAY),DATE_ADD(CURDATE(),INTERVAL 3 DAY),3),
(1,'鸡蛋','其他',6.00,'个',DATE_SUB(CURDATE(),INTERVAL 5 DAY),DATE_ADD(CURDATE(),INTERVAL 5 DAY),3),
(1,'牛奶','乳品',1.00,'盒',DATE_SUB(CURDATE(),INTERVAL 10 DAY),DATE_ADD(CURDATE(),INTERVAL 1 DAY),3),
(1,'五花肉','肉类',500.00,'克',DATE_SUB(CURDATE(),INTERVAL 1 DAY),DATE_ADD(CURDATE(),INTERVAL 7 DAY),3),
(1,'西兰花','蔬菜',1.00,'颗',DATE_SUB(CURDATE(),INTERVAL 3 DAY),DATE_ADD(CURDATE(),INTERVAL 4 DAY),3);

-- ---------- 11. 群员点餐「我想吃」 ----------
INSERT INTO `meal_request` (`family_id`,`user_id`,`dish_name`,`note`,`status`) VALUES
(1,2,'红烧肉','少放糖','PENDING'),
(1,3,'酸辣土豆丝',NULL,'PENDING'),
(2,4,'麻辣香锅','微辣','DONE');

-- ---------- 12. 首页 Banner（默认 OFF 不展示；image_url 是占位，
--          等真实图传 COS 后把 URL 换掉、status 改 ON 即可） ----------
INSERT INTO `banner` (`title`,`image_url`,`link_type`,`link_value`,`status`,`sort`,`start_at`,`end_at`) VALUES
('春日食谱上新','https://recipe-1257238458.cos.ap-guangzhou.myqcloud.com/banner/spring.png','NONE',NULL,'OFF',1,NOW(),DATE_ADD(NOW(),INTERVAL 30 DAY)),
('家庭聚餐推荐','https://recipe-1257238458.cos.ap-guangzhou.myqcloud.com/banner/family.png','NONE',NULL,'OFF',2,NOW(),DATE_ADD(NOW(),INTERVAL 30 DAY));
