DROP TABLE IF EXISTS xmldb_suiyue.gotchas;
CREATE TABLE xmldb_suiyue.gotchas (
    `id` VARCHAR(255) PRIMARY KEY COMMENT 'id',
    `__order_index` INT NOT NULL DEFAULT 0 COMMENT '顺序索引',
    `name` VARCHAR(27) COMMENT 'name',
    `type` VARCHAR(7) COMMENT 'type',
    `success_max_count` VARCHAR(1) COMMENT 'success_max_count',
    `fail_result_point` VARCHAR(1) COMMENT 'fail_result_point',
    `gotcha_stages` VARCHAR(64) COMMENT 'gotcha_stages',
    `s_retry_coin` VARCHAR(1) COMMENT 's_retry_coin',
    `s_score_point` VARCHAR(1) COMMENT 's_score_point',
    `s_score_reward` VARCHAR(17) COMMENT 's_score_reward',
    `a_retry_coin` VARCHAR(1) COMMENT 'a_retry_coin',
    `a_score_point` VARCHAR(1) COMMENT 'a_score_point',
    `a_score_reward` VARCHAR(17) COMMENT 'a_score_reward',
    `b_retry_coin` VARCHAR(1) COMMENT 'b_retry_coin',
    `b_score_point` VARCHAR(1) COMMENT 'b_score_point',
    `b_score_reward` VARCHAR(17) COMMENT 'b_score_reward',
    `c_retry_coin` VARCHAR(1) COMMENT 'c_retry_coin',
    `c_score_point` VARCHAR(1) COMMENT 'c_score_point',
    `c_score_reward` VARCHAR(17) COMMENT 'c_score_reward'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ROW_FORMAT=DYNAMIC COMMENT = 'gotchas';

DROP TABLE IF EXISTS xmldb_suiyue.gotchas__gotcha_stages__data;
CREATE TABLE xmldb_suiyue.gotchas__gotcha_stages__data (
    `id` VARCHAR(255) COMMENT '继承父id',
    `__order_index` INT NOT NULL DEFAULT 0 COMMENT '顺序索引',
    `image` VARCHAR(16) COMMENT 'image',
    `prob` VARCHAR(4) COMMENT 'prob',
    `prob_in_fever` VARCHAR(4) COMMENT 'prob_in_fever'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ROW_FORMAT=DYNAMIC COMMENT = 'gotchas__gotcha_stages__data';

