CREATE TABLE app_users (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  email VARCHAR(190) NOT NULL UNIQUE,
  password_hash VARCHAR(255) NOT NULL,
  full_name VARCHAR(100) NOT NULL,
  phone VARCHAR(20) NOT NULL,
  role VARCHAR(20) NOT NULL,
  enabled BIT NOT NULL DEFAULT 1,
  created_at DATETIME(6) NOT NULL
);
CREATE TABLE academic_offers (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  grade VARCHAR(25) NOT NULL,
  school_year INT NOT NULL,
  campus VARCHAR(100) NOT NULL,
  capacity INT NOT NULL,
  active BIT NOT NULL DEFAULT 1,
  CONSTRAINT uk_offer_grade_year_campus UNIQUE (grade,school_year,campus),
  CONSTRAINT ck_offer_capacity CHECK (capacity >= 0)
);
CREATE TABLE school_applications (
  id VARCHAR(36) NOT NULL PRIMARY KEY,
  version BIGINT NOT NULL DEFAULT 0,
  reference VARCHAR(30) NOT NULL UNIQUE,
  guardian_id BIGINT NOT NULL,
  offer_id BIGINT NOT NULL,
  student_first_names VARCHAR(80) NOT NULL,
  student_last_names VARCHAR(100) NOT NULL,
  student_document VARCHAR(8) NOT NULL,
  birth_date DATE NOT NULL,
  guardian_message VARCHAR(1200),
  review_note VARCHAR(1200),
  status VARCHAR(20) NOT NULL,
  created_at DATETIME(6) NOT NULL,
  updated_at DATETIME(6) NOT NULL,
  CONSTRAINT fk_application_guardian FOREIGN KEY (guardian_id) REFERENCES app_users(id),
  CONSTRAINT fk_application_offer FOREIGN KEY (offer_id) REFERENCES academic_offers(id),
  INDEX idx_application_guardian (guardian_id),
  INDEX idx_application_status (status)
);
CREATE TABLE application_events (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  application_id VARCHAR(36) NOT NULL,
  actor_id BIGINT NOT NULL,
  from_status VARCHAR(20),
  to_status VARCHAR(20) NOT NULL,
  note VARCHAR(1200),
  created_at DATETIME(6) NOT NULL,
  CONSTRAINT fk_event_application FOREIGN KEY (application_id) REFERENCES school_applications(id),
  CONSTRAINT fk_event_actor FOREIGN KEY (actor_id) REFERENCES app_users(id),
  INDEX idx_event_application_date (application_id,created_at)
);
CREATE TABLE audit_events (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  actor VARCHAR(190) NOT NULL,
  action VARCHAR(50) NOT NULL,
  target VARCHAR(100),
  detail VARCHAR(250),
  created_at DATETIME(6) NOT NULL,
  INDEX idx_audit_date (created_at)
);
