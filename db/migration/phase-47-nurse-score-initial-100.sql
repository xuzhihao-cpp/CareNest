USE smart_nursing;
SET NAMES utf8mb4;

-- 已有真实扣分结果保持不变；只修改默认值并为缺少评分行的护理员补齐 100 分初始记录。
ALTER TABLE nurse_score
  MODIFY COLUMN total_score DECIMAL(5,2) NOT NULL DEFAULT 100.00
  COMMENT 'Recommendation score source; nurses start at 100';

INSERT INTO nurse_score (
  nurse_id, total_score, service_count, positive_rate, complaint_count,
  last_service_at, updated_by
)
SELECT np.nurse_id, 100.00, 0, NULL, 0, NULL, NULL
FROM nurse_profile np
LEFT JOIN nurse_score ns ON ns.nurse_id = np.nurse_id
WHERE ns.nurse_id IS NULL;
