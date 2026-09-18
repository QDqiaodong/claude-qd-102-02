SET NAMES utf8mb4;

DROP TABLE IF EXISTS wash_shortage;
DROP TABLE IF EXISTS contaminated_seal;
DROP TABLE IF EXISTS linen_loss;
DROP TABLE IF EXISTS floor_issue;
DROP TABLE IF EXISTS wash_batch;
DROP TABLE IF EXISTS linen;

CREATE TABLE linen (
  id BIGINT NOT NULL AUTO_INCREMENT,
  code VARCHAR(32) NOT NULL,
  name VARCHAR(64) NOT NULL,
  category VARCHAR(16) NOT NULL,
  spec VARCHAR(32) NOT NULL,
  stock INT NOT NULL,
  warn_stock INT NOT NULL,
  status VARCHAR(16) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_linen_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE contaminated_seal (
  id BIGINT NOT NULL AUTO_INCREMENT,
  code VARCHAR(32) NOT NULL,
  linen_id BIGINT NOT NULL,
  floor_code VARCHAR(16) NOT NULL,
  quantity INT NOT NULL,
  remain_qty INT NOT NULL,
  found_at DATETIME NOT NULL,
  founder VARCHAR(32) NOT NULL,
  status VARCHAR(16) NOT NULL,
  released_at DATETIME NULL,
  releaser VARCHAR(32) NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_seal_code (code),
  KEY idx_seal_linen (linen_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE wash_batch (
  id BIGINT NOT NULL AUTO_INCREMENT,
  code VARCHAR(32) NOT NULL,
  linen_id BIGINT NOT NULL,
  quantity INT NOT NULL,
  send_date DATE NOT NULL,
  expect_date DATE NOT NULL,
  wash_type VARCHAR(16) NOT NULL,
  status VARCHAR(16) NOT NULL,
  operator VARCHAR(32) NOT NULL,
  seal_id BIGINT NULL,
  return_qty INT NULL COMMENT '收工时实际回洗件数；未完成为空',
  PRIMARY KEY (id),
  UNIQUE KEY uk_batch_code (code),
  KEY idx_batch_linen_date (linen_id, send_date),
  KEY idx_batch_seal (seal_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 回洗追差：收工时回洗少于送洗，短少的件数挂在这里，结案前不进可领用在库。
CREATE TABLE wash_shortage (
  id BIGINT NOT NULL AUTO_INCREMENT,
  code VARCHAR(32) NOT NULL,
  batch_id BIGINT NOT NULL,
  linen_id BIGINT NOT NULL,
  send_qty INT NOT NULL,
  return_qty INT NOT NULL,
  short_qty INT NOT NULL,
  duty VARCHAR(32) NOT NULL COMMENT '责任楼层或工序',
  founder VARCHAR(32) NOT NULL COMMENT '发现人',
  found_at DATETIME NOT NULL,
  status VARCHAR(16) NOT NULL COMMENT '未结案 / 已结案',
  close_type VARCHAR(16) NULL COMMENT '补回入库 / 转报损',
  closed_at DATETIME NULL,
  loss_id BIGINT NULL COMMENT '转报损生成的报损记录',
  -- 未结案时等于 batch_id，结案后变 NULL；唯一索引保证一张批次同时只挂一张未结案追差。
  active_batch_id BIGINT GENERATED ALWAYS AS (IF(status = '未结案', batch_id, NULL)) STORED,
  PRIMARY KEY (id),
  UNIQUE KEY uk_shortage_code (code),
  UNIQUE KEY uk_shortage_active_batch (active_batch_id),
  KEY idx_shortage_linen (linen_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE floor_issue (
  id BIGINT NOT NULL AUTO_INCREMENT,
  linen_id BIGINT NOT NULL,
  floor_code VARCHAR(16) NOT NULL,
  issue_date DATE NOT NULL,
  send_qty INT NOT NULL,
  back_qty INT NULL,
  receiver VARCHAR(32) NOT NULL,
  status VARCHAR(16) NOT NULL,
  PRIMARY KEY (id),
  KEY idx_issue_linen_floor_date (linen_id, floor_code, issue_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE linen_loss (
  id BIGINT NOT NULL AUTO_INCREMENT,
  linen_id BIGINT NOT NULL,
  loss_date DATE NOT NULL,
  quantity INT NOT NULL,
  reason VARCHAR(16) NOT NULL,
  duty_floor VARCHAR(16) NULL,
  status VARCHAR(16) NOT NULL,
  shortage_id BIGINT NULL COMMENT '从哪张回洗追差转来的；手工登记为空',
  PRIMARY KEY (id),
  KEY idx_loss_linen_date (linen_id, loss_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO linen (id, code, name, category, spec, stock, warn_stock, status) VALUES
(1, 'LN-001', '白色床单', '床单', '200x230', 320, 100, '在用'),
(2, 'LN-002', '白色被套', '被套', '220x240', 280, 100, '在用'),
(3, 'LN-003', '白色枕套', '枕套', '50x80', 60, 80, '在用'),
(4, 'LN-004', '白色浴巾', '浴巾', '70x140', 130, 50, '在用'),
(5, 'LN-005', '米色地巾', '地巾', '50x80', 0, 40, '在用'),
(6, 'LN-006', '旧款被套', '被套', '200x230', 0, 20, '停用');

INSERT INTO contaminated_seal (id, code, linen_id, floor_code, quantity, remain_qty, found_at, founder, status, released_at, releaser) VALUES
(1, 'QZ-0001', 4, '8F', 20, 20, '2026-09-18 08:40:00', '张姐', '未解除', NULL, NULL);

INSERT INTO wash_batch (code, linen_id, quantity, send_date, expect_date, wash_type, status, operator, seal_id, return_qty) VALUES
('WB-0901', 1, 150, '2026-09-16', '2026-09-17', '常规', '待洗', '赵姐', NULL, NULL),
('WB-0902', 2, 100, '2026-09-15', '2026-09-16', '常规', '洗涤中', '赵姐', NULL, NULL),
('WB-0903', 4, 60, '2026-09-14', '2026-09-15', '强化', '已完成', '孙姐', NULL, 60),
('WB-0918', 4, 20, '2026-09-18', '2026-09-18', '专洗', '洗涤中', '孙姐', 1, NULL);

INSERT INTO floor_issue (linen_id, floor_code, issue_date, send_qty, back_qty, receiver, status) VALUES
(1, '8F', '2026-09-16', 40, NULL, '张姐', '已送出'),
(3, '12F', '2026-09-16', 25, 25, '李姐', '已收回'),
(4, '8F', '2026-09-15', 30, 28, '张姐', '已收回');

INSERT INTO linen_loss (linen_id, loss_date, quantity, reason, duty_floor, status) VALUES
(3, '2026-09-16', 5, '污损', '12F', '待确认'),
(2, '2026-09-15', 3, '破损', '8F', '已确认');
