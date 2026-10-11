-- Run explicitly after schema.sql to add the demo accounts. Safe to run repeatedly.
START TRANSACTION;

INSERT INTO users(username,password_hash,full_name,student_no,role,enabled)
VALUES
('admin','pbkdf2$210000$8hbSbHqfPUsPDZusi/9pQw==$2ZnIggHiZWvepqh79ej/B8+4FgJIVZUmgrWYOEhfnFw=','系统管理员',NULL,'ADMIN',TRUE),
('student','pbkdf2$210000$vKazBIO70BFE8hRMyhCz9Q==$FqJnkIyVv15L6HVr0WGNnCZ9w+E1B9/jxVb5OAMllGU=','演示学生','DEMO0001','USER',TRUE)
ON DUPLICATE KEY UPDATE id=LAST_INSERT_ID(id);

-- Keep the lock order used by application writes: owning user first, then card.
SELECT id INTO @seed_student_id FROM users WHERE username='student' FOR UPDATE;

INSERT INTO campus_cards(user_id,card_no,balance)
VALUES(@seed_student_id,'CC-DEMO-0001',0.00)
ON DUPLICATE KEY UPDATE id=id;

SELECT id INTO @seed_card_id FROM campus_cards WHERE user_id=@seed_student_id FOR UPDATE;

-- Initialize only a zero-balance card that has no prior transactions. The balance
-- update and its matching record are committed together below.
UPDATE campus_cards
SET balance=100.00
WHERE id=@seed_card_id AND balance=0.00
  AND NOT EXISTS (SELECT 1 FROM transaction_records tr WHERE tr.card_id=@seed_card_id);
SET @seed_balance_initialized = ROW_COUNT();

INSERT INTO transaction_records(card_id,type,amount,balance_before,balance_after,description,actor_id,operation_id)
SELECT @seed_card_id,'RECHARGE',100.00,0.00,100.00,'演示账户初始充值',
       (SELECT admin.id FROM users admin WHERE admin.username='admin'),
       'seed-student-initial-recharge-v1'
WHERE @seed_balance_initialized=1
  AND NOT EXISTS (SELECT 1 FROM transaction_records tr WHERE tr.operation_id='seed-student-initial-recharge-v1')
ON DUPLICATE KEY UPDATE id=id;

COMMIT;
