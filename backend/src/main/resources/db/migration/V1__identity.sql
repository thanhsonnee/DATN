-- =============================================================================
-- V1 — NHÓM 1: DANH TÍNH
-- persons · users · password_reset_tokens · members · employees · audit_logs
-- Đối chiếu: db/31_bang.md (bản 32 bảng, đã áp dụng quyết định 12–14)
-- =============================================================================

-- Trigger dùng chung: tự cập nhật updated_at mỗi lần UPDATE
CREATE OR REPLACE FUNCTION set_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = now();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;


-- =============================================================================
-- persons — con người thật, 1 dòng = 1 người
-- Là điểm gốc chống trùng danh tính: phone UNIQUE nên dù đến từ kênh nào
-- (hotline, tự tải app, lễ tân nhập) cũng chỉ tồn tại một bản ghi.
-- =============================================================================
CREATE TABLE persons (
    id                      BIGSERIAL PRIMARY KEY,
    full_name               VARCHAR(150)  NOT NULL,
    gender                  VARCHAR(10),
    birthday                DATE,
    national_id             VARCHAR(20),
    phone                   VARCHAR(20)   NOT NULL,
    email                   VARCHAR(150),
    address                 VARCHAR(255),

    -- Key trong MinIO (bucket private), KHÔNG phải URL công khai — tuân thủ NĐ 13/2023
    photo_key               VARCHAR(500),

    -- Thẻ từ + khóa sinh QR động (gánh từ access_cards + qr_secrets)
    card_uid                VARCHAR(64),
    card_issued_at          TIMESTAMPTZ,
    qr_secret_enc           BYTEA,

    emergency_contact_name  VARCHAR(150),
    emergency_contact_phone VARCHAR(20),

    created_at              TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ   NOT NULL DEFAULT now(),
    deleted_at              TIMESTAMPTZ,

    CONSTRAINT chk_persons_gender   CHECK (gender IS NULL OR gender IN ('MALE','FEMALE','OTHER')),
    CONSTRAINT chk_persons_birthday CHECK (birthday IS NULL OR birthday < CURRENT_DATE),
    CONSTRAINT chk_persons_phone    CHECK (phone ~ '^0[35789][0-9]{8}$')
);

-- UNIQUE có điều kiện: chỉ áp dụng cho bản ghi chưa xóa mềm
CREATE UNIQUE INDEX uq_persons_phone       ON persons (phone)       WHERE deleted_at IS NULL;
CREATE UNIQUE INDEX uq_persons_national_id ON persons (national_id) WHERE deleted_at IS NULL AND national_id IS NOT NULL;
CREATE UNIQUE INDEX uq_persons_email       ON persons (email)       WHERE deleted_at IS NULL AND email IS NOT NULL;
CREATE UNIQUE INDEX uq_persons_card_uid    ON persons (card_uid)    WHERE deleted_at IS NULL AND card_uid IS NOT NULL;

CREATE TRIGGER trg_persons_updated_at BEFORE UPDATE ON persons
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

COMMENT ON TABLE  persons IS 'Con người thật — hội viên, nhân viên, khách tiềm năng đều có 1 dòng ở đây';
COMMENT ON COLUMN persons.photo_key IS 'Key trong MinIO bucket private, hiển thị qua presigned URL TTL 5 phút';


-- =============================================================================
-- users — tài khoản đăng nhập
-- 1 person → nhiều users (một người có thể có nhiều vai trò khác nhau)
-- =============================================================================
CREATE TABLE users (
    id                BIGSERIAL PRIMARY KEY,
    person_id         BIGINT       NOT NULL REFERENCES persons(id),
    username          VARCHAR(100) NOT NULL,
    password_hash     VARCHAR(255) NOT NULL,
    primary_role      VARCHAR(20)  NOT NULL,

    -- Chỉ 2 trạng thái (quyết định 6). Hết gói KHÔNG khóa tài khoản.
    status            VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    locked_reason     VARCHAR(255),
    locked_until      DATE,
    locked_by         BIGINT       REFERENCES users(id),

    -- Khóa TỰ ĐỘNG khi sai mật khẩu — tách khỏi locked_until để job mở khóa
    -- tự động không phá lệnh khóa thủ công của Admin (quyết định 7)
    failed_attempts   SMALLINT     NOT NULL DEFAULT 0,
    auto_locked_until TIMESTAMPTZ,

    email_verified_at TIMESTAMPTZ,
    last_login_at     TIMESTAMPTZ,

    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    deleted_at        TIMESTAMPTZ,

    CONSTRAINT chk_users_role   CHECK (primary_role IN ('ADMIN','MEMBER','TRAINER','SALE','RECEPTIONIST','ACCOUNTANT')),
    CONSTRAINT chk_users_status CHECK (status IN ('ACTIVE','LOCKED')),
    -- Khóa thì bắt buộc ghi lý do
    CONSTRAINT chk_users_locked CHECK (status <> 'LOCKED' OR locked_reason IS NOT NULL)
);

CREATE UNIQUE INDEX uq_users_username ON users (username) WHERE deleted_at IS NULL;
CREATE INDEX idx_users_person         ON users (person_id);

CREATE TRIGGER trg_users_updated_at BEFORE UPDATE ON users
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

COMMENT ON COLUMN users.primary_role IS 'API đăng ký công khai TUYỆT ĐỐI không nhận role từ client — luôn gán cứng MEMBER';


-- =============================================================================
-- password_reset_tokens — đặt lại mật khẩu qua email
-- Lưu SHA-256 của token, không lưu token thô. Dùng đúng 1 lần, hết hạn 30 phút.
-- =============================================================================
CREATE TABLE password_reset_tokens (
    id           BIGSERIAL PRIMARY KEY,
    user_id      BIGINT       NOT NULL REFERENCES users(id),
    token_hash   VARCHAR(128) NOT NULL UNIQUE,
    expires_at   TIMESTAMPTZ  NOT NULL,
    used_at      TIMESTAMPTZ,
    requested_ip INET,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_prt_user ON password_reset_tokens (user_id, created_at DESC);


-- =============================================================================
-- members — hồ sơ hội viên
-- Chỉ tạo khi CHỐT MUA gói đầu tiên (phương án A). Người tải app mà chưa mua
-- gói KHÔNG có dòng nào ở đây.
-- status chỉ 2 giá trị (quyết định 14): "còn gói hay hết gói" là dữ liệu suy ra
-- được từ registrations, không lưu ở đây.
-- =============================================================================
CREATE TABLE members (
    id            BIGSERIAL PRIMARY KEY,
    person_id     BIGINT      NOT NULL REFERENCES persons(id),
    member_code   VARCHAR(20) NOT NULL,
    join_date     DATE        NOT NULL,
    source        VARCHAR(30),
    referred_by   BIGINT      REFERENCES members(id),
    health_note   TEXT,
    goal          VARCHAR(30),
    status        VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    last_visit_at TIMESTAMPTZ,

    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at    TIMESTAMPTZ,

    CONSTRAINT chk_members_status CHECK (status IN ('ACTIVE','BLACKLISTED')),
    CONSTRAINT chk_members_source CHECK (source IS NULL OR source IN ('WALK_IN','HOTLINE','WEB_FORM','REFERRAL','APP_SELF')),
    CONSTRAINT chk_members_goal   CHECK (goal   IS NULL OR goal   IN ('LOSE_FAT','GAIN_MUSCLE','ENDURANCE','HEALTH'))
);

CREATE UNIQUE INDEX uq_members_person ON members (person_id)   WHERE deleted_at IS NULL;
CREATE UNIQUE INDEX uq_members_code   ON members (member_code) WHERE deleted_at IS NULL;

CREATE TRIGGER trg_members_updated_at BEFORE UPDATE ON members
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

COMMENT ON COLUMN members.status IS 'CHỈ nói về việc bị cấm cửa hay không. "Còn gói hay hết gói" đọc từ registrations.status';


-- =============================================================================
-- employees — nhân sự, gồm cả huấn luyện viên
-- Gộp trainers + staff + trainer_specialties (quyết định 8) để payroll_items và
-- các bảng phân công chỉ cần MỘT khóa ngoại.
-- =============================================================================
CREATE TABLE employees (
    id              BIGSERIAL PRIMARY KEY,
    person_id       BIGINT        NOT NULL REFERENCES persons(id),
    employee_code   VARCHAR(20)   NOT NULL,
    department      VARCHAR(30)   NOT NULL,
    position        VARCHAR(80),
    employment_type VARCHAR(20),
    base_salary     NUMERIC(14,2),
    start_date      DATE          NOT NULL,
    end_date        DATE,
    status          VARCHAR(20)   NOT NULL DEFAULT 'ACTIVE',

    -- Chỉ có nghĩa khi department = 'TRAINING'
    level           VARCHAR(20),
    specialties     JSONB,
    bio             TEXT,
    max_members     INTEGER,
    rating_avg      NUMERIC(3,2),
    rating_count    INTEGER       NOT NULL DEFAULT 0,

    created_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    deleted_at      TIMESTAMPTZ,

    -- 4 phòng ban, khớp đúng 4 vai trò nhân viên (không có MANAGEMENT/MAINTENANCE)
    CONSTRAINT chk_emp_department CHECK (department IN ('TRAINING','SALES','FRONT_DESK','ACCOUNTING')),
    CONSTRAINT chk_emp_status     CHECK (status IN ('ACTIVE','ON_LEAVE','RESIGNED')),
    CONSTRAINT chk_emp_type       CHECK (employment_type IS NULL OR employment_type IN ('FULL_TIME','PART_TIME','FREELANCE')),
    CONSTRAINT chk_emp_level      CHECK (level IS NULL OR level IN ('JUNIOR','SENIOR','MASTER')),
    CONSTRAINT chk_emp_dates      CHECK (end_date IS NULL OR end_date >= start_date),
    CONSTRAINT chk_emp_rating     CHECK (rating_avg IS NULL OR rating_avg BETWEEN 1.00 AND 5.00),
    -- level chỉ dành cho huấn luyện viên, vì nó quyết định đơn giá buổi tập
    CONSTRAINT chk_emp_level_dept CHECK (level IS NULL OR department = 'TRAINING')
);

CREATE UNIQUE INDEX uq_employees_person ON employees (person_id)     WHERE deleted_at IS NULL;
CREATE UNIQUE INDEX uq_employees_code   ON employees (employee_code) WHERE deleted_at IS NULL;
CREATE INDEX idx_employees_department   ON employees (department) WHERE deleted_at IS NULL;

CREATE TRIGGER trg_employees_updated_at BEFORE UPDATE ON employees
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();


-- =============================================================================
-- audit_logs — nhật ký mọi thao tác thay đổi dữ liệu
-- Bảng kỹ thuật, không vẽ trong sơ đồ thực thể.
-- entity_type/entity_id trỏ tới bản ghi bất kỳ theo kiểu đa hình (không FK cứng).
-- =============================================================================
CREATE TABLE audit_logs (
    id          BIGSERIAL PRIMARY KEY,
    actor_id    BIGINT      REFERENCES users(id),
    action      VARCHAR(50) NOT NULL,
    entity_type VARCHAR(60) NOT NULL,
    entity_id   BIGINT,
    before_data JSONB,
    after_data  JSONB,
    reason      TEXT,
    ip_address  INET,
    user_agent  VARCHAR(500),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT chk_audit_action CHECK (action IN
        ('CREATE','UPDATE','DELETE','APPROVE','REJECT','LOGIN','LOGOUT','EXPORT','ANONYMIZE'))
);

CREATE INDEX idx_audit_entity ON audit_logs (entity_type, entity_id, created_at DESC);
CREATE INDEX idx_audit_actor  ON audit_logs (actor_id, created_at DESC);

COMMENT ON COLUMN audit_logs.before_data IS 'PHẢI lọc bỏ password_hash trước khi ghi';
