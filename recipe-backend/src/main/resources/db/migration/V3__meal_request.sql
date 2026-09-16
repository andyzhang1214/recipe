-- 群员点餐「我想吃」：圈层成员提交想吃的菜，群主在家庭页处理（标记已做）
CREATE TABLE meal_request (
  id          BIGINT PRIMARY KEY AUTO_INCREMENT,
  family_id   BIGINT NOT NULL,                       -- 所属圈层
  user_id     BIGINT NOT NULL,                       -- 点餐人
  dish_name   VARCHAR(64)  NOT NULL,                 -- 想吃啥
  note        VARCHAR(128) NULL,                     -- 备注（口味/忌口，可选）
  status      VARCHAR(16)  NOT NULL DEFAULT 'PENDING',  -- PENDING 待做 / DONE 已完成
  created_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_mr_family_status (family_id, status),
  KEY idx_mr_user (user_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
