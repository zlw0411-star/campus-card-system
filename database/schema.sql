CREATE TABLE IF NOT EXISTS users (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  username VARCHAR(50) NOT NULL UNIQUE,
  password_hash VARCHAR(255) NOT NULL,
  full_name VARCHAR(100) NOT NULL,
  student_no VARCHAR(30) NULL UNIQUE,
  role VARCHAR(10) NOT NULL,
  enabled BOOLEAN NOT NULL DEFAULT TRUE,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  CONSTRAINT chk_users_role CHECK (role IN ('USER','ADMIN'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS campus_cards (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT NOT NULL UNIQUE,
  card_no VARCHAR(32) NOT NULL UNIQUE,
  balance DECIMAL(12,2) NOT NULL DEFAULT 0.00,
  CONSTRAINT chk_card_balance CHECK (balance >= 0),
  CONSTRAINT fk_cards_user FOREIGN KEY (user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS transaction_records (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  card_id BIGINT NOT NULL,
  type VARCHAR(20) NOT NULL,
  amount DECIMAL(12,2) NOT NULL,
  balance_before DECIMAL(12,2) NOT NULL,
  balance_after DECIMAL(12,2) NOT NULL,
  description VARCHAR(255) NOT NULL,
  actor_id BIGINT NOT NULL,
  operation_id VARCHAR(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL UNIQUE,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  CONSTRAINT chk_transaction_type CHECK (type IN ('RECHARGE','CONSUMPTION')),
  CONSTRAINT chk_transaction_amount CHECK (amount > 0),
  CONSTRAINT fk_transactions_card FOREIGN KEY (card_id) REFERENCES campus_cards(id),
  CONSTRAINT fk_transactions_actor FOREIGN KEY (actor_id) REFERENCES users(id),
  INDEX idx_transaction_created (created_at, id),
  INDEX idx_transaction_card_created (card_id, created_at, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
