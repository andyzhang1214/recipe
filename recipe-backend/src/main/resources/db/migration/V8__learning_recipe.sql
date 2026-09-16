-- 待学习菜谱：心愿单点「我也不会」后移入的个人学习清单
CREATE TABLE IF NOT EXISTS learning_recipe (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT NOT NULL COMMENT '归属用户',
  dish_name VARCHAR(100) NOT NULL COMMENT '菜名',
  source_family_id BIGINT NULL COMMENT '来源队伍',
  source_family_name VARCHAR(100) NULL COMMENT '来源队伍名（冗余，防改名后失联）',
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  KEY idx_lr_user (user_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '待学习菜谱';
