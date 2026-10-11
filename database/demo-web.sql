-- Optional local web-demo data. Review before explicit import; never run at startup.
-- Conflicting usernames, student numbers, or card numbers are skipped as a whole.
-- Existing accounts, cards, balances, and transaction history are never changed.
SET time_zone = '+08:00';
START TRANSACTION;

-- Candidate rows are session-local and contain only accounts that were entirely
-- unused before this import. They gate both card creation and demo history.
DROP TEMPORARY TABLE IF EXISTS tmp_campus_web_demo_candidates;
CREATE TEMPORARY TABLE tmp_campus_web_demo_candidates (
  username VARCHAR(50) NOT NULL,
  password_hash VARCHAR(255) NOT NULL,
  full_name VARCHAR(100) NOT NULL,
  student_no VARCHAR(30) NOT NULL,
  card_no VARCHAR(32) NOT NULL,
  PRIMARY KEY (username),
  UNIQUE KEY uq_tmp_web_demo_student_no (student_no),
  UNIQUE KEY uq_tmp_web_demo_card_no (card_no)
) ENGINE=MEMORY;

INSERT INTO tmp_campus_web_demo_candidates(username,password_hash,full_name,student_no,card_no)
VALUES
('demo2026','pbkdf2$210000$m+94i2T6u5uU9bYNtX5s8A==$P+DpN5syMdYs4RU73K+ecx6IwmnZnbExpQnVvhZ2Fqg=','林知夏','WEB20260001','CC-WEB-demo2026'),
('webdemo01','pbkdf2$210000$WGLo4TlGDQSumMBgvdM0Jw==$uyrllnHhkGBcUQdM5VNC1LWLYr9ffiADF2jCmw1lg7c=','陈予安','WEB20260002','CC-WEB-webdemo01'),
('webdemo02','pbkdf2$210000$DIJ8caaBxtyjZGqdlQx06g==$K7/oyB6nm9I8myTzu4lkOWZS9eJQsfgP6/pdgc5x86E=','周语桐','WEB20260003','CC-WEB-webdemo02'),
('webdemo03','pbkdf2$210000$l8XNqzIcYjG2bHylAI33TQ==$BqkjJdmmNpaaBnDQnTpiHYKa1odD5z68BYZ7K+Nwt/0=','许明川','WEB20260004','CC-WEB-webdemo03'),
('webdemo04','pbkdf2$210000$IGAW8dvn9Jk6Tn19FqCsJg==$j79QI+khWgFgMhbLi0Ipe7+Sx9K1q/iXFctbvyEj+yk=','沈星遥','WEB20260005','CC-WEB-webdemo04'),
('webdemo05','pbkdf2$210000$+YWy/ADWPiz2+MvGmHZYsg==$eAq1kDFBJMXTMG49KfvXHpyiz5zS6PulMuWzj10ZVDE=','顾嘉宁','WEB20260006','CC-WEB-webdemo05'),
('webdemo06','pbkdf2$210000$i/Weopccl4Jh+qBfwZvdqQ==$rtTylyiZhK81F7C3446Qgj3yacpShyhueNLOsUsAvc8=','唐以诺','WEB20260007','CC-WEB-webdemo06'),
('webdemo07','pbkdf2$210000$xW/yLPUFGOphBVD6Uln6fA==$lnnCXvsT69KHJh08n5awbqqFkIW2V8GbFgfQtM4hIf0=','宋清越','WEB20260008','CC-WEB-webdemo07'),
('webdemo08','pbkdf2$210000$gt4l/ORgBrascbF/b9ZXyQ==$AZUZSlG1R/whdgTBV9hP4YW0Zetcwd1+f2QkBydeA3U=','叶书瑶','WEB20260009','CC-WEB-webdemo08'),
('webdemo09','pbkdf2$210000$VB5093cO8c7T71czF8/ung==$HPC4rYbVZwodeFKKHjC25pjklc/DUi4k/Symj09mSRk=','陆景行','WEB20260010','CC-WEB-webdemo09'),
('webdemo10','pbkdf2$210000$RzTUvZYo2DolI8Z/Rpbymw==$Hcy+gDX6c5xSYNOvhh6TtNFmZdmSJleSmZlYFiiP1p0=','温若曦','WEB20260011','CC-WEB-webdemo10');

DELETE candidate
FROM tmp_campus_web_demo_candidates candidate
WHERE EXISTS (
  SELECT 1 FROM users u
  WHERE u.username=candidate.username OR u.student_no=candidate.student_no
) OR EXISTS (
  SELECT 1 FROM campus_cards cc WHERE cc.card_no=candidate.card_no
);

INSERT INTO users(username,password_hash,full_name,student_no,role,enabled)
SELECT username,password_hash,full_name,student_no,'USER',TRUE
FROM tmp_campus_web_demo_candidates;

SET @web_demo_user_id := NULL;
SELECT u.id INTO @web_demo_user_id
FROM users u
JOIN tmp_campus_web_demo_candidates candidate ON candidate.username=u.username
WHERE candidate.username='demo2026' AND u.role='USER'
FOR UPDATE;

-- New owner rows are inserted above and locked before creating their cards.
INSERT INTO campus_cards(user_id,card_no,balance)
SELECT u.id,candidate.card_no,0.00
FROM users u
JOIN tmp_campus_web_demo_candidates candidate ON candidate.username=u.username
WHERE u.role='USER';

SET @web_demo_card_id := NULL;
SELECT cc.id INTO @web_demo_card_id
FROM campus_cards cc
WHERE cc.user_id=@web_demo_user_id
FOR UPDATE;

-- Seed history only for a newly-created, untouched zero-balance demo card, and
-- only if all fixed operation IDs are unused. Balance + records commit atomically.
SET @web_demo_can_seed := COALESCE((
  SELECT cc.balance=0.00
    AND NOT EXISTS (SELECT 1 FROM transaction_records tr WHERE tr.card_id=cc.id)
    AND NOT EXISTS (SELECT 1 FROM transaction_records tr WHERE tr.operation_id IN (
      '6a7a1b90-04f2-4d0a-8c01-000000000001','6a7a1b90-04f2-4d0a-8c01-000000000002',
      '6a7a1b90-04f2-4d0a-8c01-000000000003','6a7a1b90-04f2-4d0a-8c01-000000000004',
      '6a7a1b90-04f2-4d0a-8c01-000000000005','6a7a1b90-04f2-4d0a-8c01-000000000006',
      '6a7a1b90-04f2-4d0a-8c01-000000000007','6a7a1b90-04f2-4d0a-8c01-000000000008',
      '6a7a1b90-04f2-4d0a-8c01-000000000009','6a7a1b90-04f2-4d0a-8c01-000000000010',
      '6a7a1b90-04f2-4d0a-8c01-000000000011','6a7a1b90-04f2-4d0a-8c01-000000000012',
      '6a7a1b90-04f2-4d0a-8c01-000000000013','6a7a1b90-04f2-4d0a-8c01-000000000014'
    ))
  FROM campus_cards cc WHERE cc.id=@web_demo_card_id
),0);

UPDATE campus_cards SET balance=210.00
WHERE id=@web_demo_card_id AND @web_demo_can_seed=1;

INSERT INTO transaction_records(card_id,type,amount,balance_before,balance_after,description,actor_id,operation_id,created_at)
SELECT @web_demo_card_id, events.type, events.amount, events.balance_before, events.balance_after,
       events.description, @web_demo_user_id, events.operation_id, events.created_at
FROM (
  SELECT 'RECHARGE' AS type,100.00 AS amount,0.00 AS balance_before,100.00 AS balance_after,'食堂账户充值' AS description,'6a7a1b90-04f2-4d0a-8c01-000000000001' AS operation_id,TIMESTAMP(DATE_SUB(CURDATE(),INTERVAL 6 DAY),'08:10:00') AS created_at
  UNION ALL SELECT 'CONSUMPTION',12.50,100.00,87.50,'早餐','6a7a1b90-04f2-4d0a-8c01-000000000002',TIMESTAMP(DATE_SUB(CURDATE(),INTERVAL 6 DAY),'08:35:00')
  UNION ALL SELECT 'RECHARGE',50.00,87.50,137.50,'校园卡充值','6a7a1b90-04f2-4d0a-8c01-000000000003',TIMESTAMP(DATE_SUB(CURDATE(),INTERVAL 5 DAY),'09:05:00')
  UNION ALL SELECT 'CONSUMPTION',22.50,137.50,115.00,'午餐','6a7a1b90-04f2-4d0a-8c01-000000000004',TIMESTAMP(DATE_SUB(CURDATE(),INTERVAL 5 DAY),'12:20:00')
  UNION ALL SELECT 'CONSUMPTION',18.00,115.00,97.00,'咖啡与点心','6a7a1b90-04f2-4d0a-8c01-000000000005',TIMESTAMP(DATE_SUB(CURDATE(),INTERVAL 4 DAY),'15:10:00')
  UNION ALL SELECT 'RECHARGE',80.00,97.00,177.00,'校园卡充值','6a7a1b90-04f2-4d0a-8c01-000000000006',TIMESTAMP(DATE_SUB(CURDATE(),INTERVAL 4 DAY),'18:00:00')
  UNION ALL SELECT 'CONSUMPTION',9.50,177.00,167.50,'早餐','6a7a1b90-04f2-4d0a-8c01-000000000007',TIMESTAMP(DATE_SUB(CURDATE(),INTERVAL 3 DAY),'08:25:00')
  UNION ALL SELECT 'CONSUMPTION',30.00,167.50,137.50,'午餐','6a7a1b90-04f2-4d0a-8c01-000000000008',TIMESTAMP(DATE_SUB(CURDATE(),INTERVAL 3 DAY),'12:15:00')
  UNION ALL SELECT 'RECHARGE',60.00,137.50,197.50,'校园卡充值','6a7a1b90-04f2-4d0a-8c01-000000000009',TIMESTAMP(DATE_SUB(CURDATE(),INTERVAL 2 DAY),'10:00:00')
  UNION ALL SELECT 'CONSUMPTION',16.00,197.50,181.50,'晚餐','6a7a1b90-04f2-4d0a-8c01-000000000010',TIMESTAMP(DATE_SUB(CURDATE(),INTERVAL 2 DAY),'18:30:00')
  UNION ALL SELECT 'CONSUMPTION',21.00,181.50,160.50,'午餐','6a7a1b90-04f2-4d0a-8c01-000000000011',TIMESTAMP(DATE_SUB(CURDATE(),INTERVAL 1 DAY),'12:25:00')
  UNION ALL SELECT 'RECHARGE',100.00,160.50,260.50,'校园卡充值','6a7a1b90-04f2-4d0a-8c01-000000000012',TIMESTAMP(DATE_SUB(CURDATE(),INTERVAL 1 DAY),'17:40:00')
  UNION ALL SELECT 'CONSUMPTION',35.00,260.50,225.50,'晚餐','6a7a1b90-04f2-4d0a-8c01-000000000013',TIMESTAMP(CURDATE(),'18:10:00')
  UNION ALL SELECT 'CONSUMPTION',15.50,225.50,210.00,'夜间饮品','6a7a1b90-04f2-4d0a-8c01-000000000014',TIMESTAMP(CURDATE(),'20:05:00')
) AS events
WHERE @web_demo_can_seed=1;

DROP TEMPORARY TABLE tmp_campus_web_demo_candidates;
COMMIT;
