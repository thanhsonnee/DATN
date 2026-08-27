-- =====================================================================
--  HỆ THỐNG QUẢN LÝ PHÒNG GYM — SCHEMA POSTGRESQL 16
--  Phiên bản hợp nhất để đọc hiểu / đưa vào phụ lục báo cáo ĐATN.
--  NGUỒN SỰ THẬT khi triển khai là các file Flyway trong db/migrations/.
--
--  Quy ước:
--    • Tiền tệ: NUMERIC(14,2)  — TUYỆT ĐỐI không dùng float/double
--    • Thời điểm: TIMESTAMPTZ  — luôn có timezone
--    • Enum: dùng VARCHAR + CHECK (dễ migrate hơn native ENUM của PG)
--    • Bảng tài chính & sổ cái: APPEND-ONLY, sửa bằng bút toán đảo
-- =====================================================================

CREATE EXTENSION IF NOT EXISTS btree_gist;   -- cho EXCLUDE constraint chống trùng lịch
CREATE EXTENSION IF NOT EXISTS pg_trgm;      -- tìm kiếm mờ tên hội viên
CREATE EXTENSION IF NOT EXISTS pgcrypto;     -- gen_random_uuid()

-- Trigger dùng chung: tự cập nhật updated_at
CREATE OR REPLACE FUNCTION set_updated_at() RETURNS TRIGGER AS $$
BEGIN NEW.updated_at = now(); RETURN NEW; END;
$$ LANGUAGE plpgsql;


-- =====================================================================
-- M1. IDENTITY — Con người, Tài khoản, Vai trò
-- =====================================================================
--
-- PHẠM VI: hệ thống quản lý MỘT phòng gym duy nhất.
-- → KHÔNG có bảng `branches`, không có cột `branch_id` ở bất kỳ bảng nào.
-- Thông tin phòng gym (tên, địa chỉ, giờ mở/đóng cửa) lưu trong
-- `system_settings` dạng key-value, ví dụ:
--     gym.name          = "Fitness Center ABC"
--     gym.address       = "123 Nguyễn Trãi, Thanh Xuân, Hà Nội"
--     gym.opening_time  = "05:30"
--     gym.closing_time  = "22:30"

-- MỘT CON NGƯỜI THẬT. Một person có thể có nhiều users (nhiều role khác nhau),
-- đồng thời có thể vừa là member vừa là trainer.
CREATE TABLE persons (
    id              BIGSERIAL PRIMARY KEY,
    full_name       VARCHAR(150) NOT NULL,
    gender          VARCHAR(10)  CHECK (gender IN ('MALE','FEMALE','OTHER')),
    birthday        DATE,
    national_id     VARCHAR(20)  UNIQUE,        -- CCCD, mã hóa ở tầng ứng dụng
    phone           VARCHAR(20)  UNIQUE,        -- định danh thực tế phổ biến nhất ở VN
    email           VARCHAR(150) UNIQUE,
    address         VARCHAR(255),
    photo_url       VARCHAR(500),               -- BẮT BUỘC cho xác minh check-in
    emergency_contact_name  VARCHAR(150),
    emergency_contact_phone VARCHAR(20),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at      TIMESTAMPTZ,

    CONSTRAINT chk_birthday_past CHECK (birthday IS NULL OR birthday < CURRENT_DATE)
);
CREATE TRIGGER trg_persons_updated BEFORE UPDATE ON persons
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE INDEX idx_persons_name_trgm ON persons USING gin (full_name gin_trgm_ops);

-- TÀI KHOẢN ĐĂNG NHẬP. 1 person → N users (yêu cầu: 1 người nhiều tài khoản khác role)
CREATE TABLE users (
    id              BIGSERIAL PRIMARY KEY,
    person_id       BIGINT NOT NULL REFERENCES persons(id) ON DELETE RESTRICT,
    username        VARCHAR(100) NOT NULL UNIQUE,
    password_hash   VARCHAR(255) NOT NULL,           -- BCrypt cost 12
    primary_role    VARCHAR(20)  NOT NULL,
    status          VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    last_login_at   TIMESTAMPTZ,
    failed_attempts SMALLINT NOT NULL DEFAULT 0,
    locked_until    TIMESTAMPTZ,
    must_change_password BOOLEAN NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT chk_users_role   CHECK (primary_role IN
        ('ADMIN','MEMBER','TRAINER','SALE','RECEPTIONIST','ACCOUNTANT')),
    CONSTRAINT chk_users_status CHECK (status IN ('ACTIVE','SUSPENDED','DISABLED'))
);
CREATE INDEX idx_users_person ON users(person_id);
CREATE TRIGGER trg_users_updated BEFORE UPDATE ON users
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
-- LƯU Ý: KHÔNG tạo unique index "chỉ 1 admin" như schema cũ — cần nhiều admin.

-- Quyền bổ sung cho 1 tài khoản (ngoài primary_role)
CREATE TABLE user_roles (
    user_id     BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role        VARCHAR(20) NOT NULL,
    granted_by  BIGINT REFERENCES users(id),
    granted_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, role),
    CONSTRAINT chk_user_roles CHECK (role IN
        ('ADMIN','MEMBER','TRAINER','SALE','RECEPTIONIST','ACCOUNTANT'))
);

CREATE TABLE refresh_tokens (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash  VARCHAR(128) NOT NULL UNIQUE,   -- SHA-256, KHÔNG lưu token thô
    issued_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at  TIMESTAMPTZ NOT NULL,
    revoked_at  TIMESTAMPTZ,
    replaced_by BIGINT REFERENCES refresh_tokens(id),  -- phục vụ reuse detection
    device_info VARCHAR(255),
    ip_address  INET
);
CREATE INDEX idx_refresh_user ON refresh_tokens(user_id) WHERE revoked_at IS NULL;

CREATE TABLE members (
    id              BIGSERIAL PRIMARY KEY,
    person_id       BIGINT NOT NULL UNIQUE REFERENCES persons(id) ON DELETE RESTRICT,
    member_code     VARCHAR(20) NOT NULL UNIQUE,     -- MB-000123
    join_date       DATE NOT NULL DEFAULT CURRENT_DATE,
    source          VARCHAR(30),                     -- WALK_IN|FB_ADS|REFERRAL|HOTLINE
    referred_by     BIGINT REFERENCES members(id),
    health_note     TEXT,                            -- bệnh nền, chấn thương
    goal            VARCHAR(30),                     -- LOSE_FAT|GAIN_MUSCLE|ENDURANCE|HEALTH
    status          VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    last_visit_at   TIMESTAMPTZ,                     -- denormalized cho churn engine
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT chk_member_status CHECK (status IN ('ACTIVE','INACTIVE','BLACKLISTED'))
);
CREATE INDEX idx_members_last_visit ON members(last_visit_at);

CREATE TABLE trainers (
    id                   BIGSERIAL PRIMARY KEY,
    person_id            BIGINT NOT NULL UNIQUE REFERENCES persons(id) ON DELETE RESTRICT,
    trainer_code         VARCHAR(20) NOT NULL UNIQUE,
    employment_type      VARCHAR(20) NOT NULL,
    level                VARCHAR(20),                -- JUNIOR|SENIOR|MASTER (ảnh hưởng giá)
    bio                  TEXT,
    base_salary          NUMERIC(14,2) NOT NULL DEFAULT 0,
    start_date           DATE NOT NULL,
    finish_contract_date DATE,
    max_members          INTEGER DEFAULT 30,         -- sức chứa học viên
    rating_avg           NUMERIC(3,2),               -- denormalized từ trainer_ratings
    rating_count         INTEGER NOT NULL DEFAULT 0,
    status               VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT chk_trainer_emp  CHECK (employment_type IN ('FULL_TIME','PART_TIME','FREELANCE')),
    CONSTRAINT chk_trainer_stat CHECK (status IN ('ACTIVE','ON_LEAVE','RESIGNED')),
    CONSTRAINT chk_trainer_dates CHECK (finish_contract_date IS NULL
                                        OR finish_contract_date > start_date),
    CONSTRAINT chk_base_salary  CHECK (base_salary >= 0)
);

CREATE TABLE trainer_specialties (
    trainer_id  BIGINT NOT NULL REFERENCES trainers(id) ON DELETE CASCADE,
    specialty   VARCHAR(40) NOT NULL,   -- FITNESS|YOGA|CROSSFIT|BOXING|REHAB|NUTRITION
    PRIMARY KEY (trainer_id, specialty)
);

-- Nhân sự không phải PT (sale, lễ tân, kế toán, quản lý)
CREATE TABLE staff (
    id              BIGSERIAL PRIMARY KEY,
    person_id       BIGINT NOT NULL REFERENCES persons(id) ON DELETE RESTRICT,
    staff_code      VARCHAR(20) NOT NULL UNIQUE,
    department      VARCHAR(30) NOT NULL,   -- SALES|FRONT_DESK|ACCOUNTING|MANAGEMENT
    position        VARCHAR(80),
    base_salary     NUMERIC(14,2) NOT NULL DEFAULT 0,
    start_date      DATE NOT NULL,
    end_date        DATE,
    status          VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT chk_staff_dept   CHECK (department IN
        ('SALES','FRONT_DESK','ACCOUNTING','MANAGEMENT','MAINTENANCE')),
    CONSTRAINT chk_staff_status CHECK (status IN ('ACTIVE','ON_LEAVE','RESIGNED')),
    UNIQUE (person_id, department)
);

CREATE TABLE audit_logs (
    id          BIGSERIAL PRIMARY KEY,
    actor_id    BIGINT REFERENCES users(id),
    action      VARCHAR(50)  NOT NULL,       -- CREATE|UPDATE|DELETE|APPROVE|LOGIN|...
    entity_type VARCHAR(60)  NOT NULL,
    entity_id   BIGINT,
    before_data JSONB,
    after_data  JSONB,
    reason      TEXT,
    ip_address  INET,
    user_agent  VARCHAR(500),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_audit_entity ON audit_logs(entity_type, entity_id, created_at DESC);
CREATE INDEX idx_audit_actor  ON audit_logs(actor_id, created_at DESC);


-- =====================================================================
-- M3. MEMBERSHIP — Gói tập, Giá, Hợp đồng, Bảo lưu
-- =====================================================================

CREATE TABLE memberships (
    id                  BIGSERIAL PRIMARY KEY,
    code                VARCHAR(30)  NOT NULL UNIQUE,
    name                VARCHAR(150) NOT NULL,
    package_type        VARCHAR(20)  NOT NULL,
    duration_days       INTEGER,          -- NULL nếu thuần SESSION_BASED
    session_count       INTEGER,          -- NULL nếu thuần TIME_BASED
    includes_trainer    BOOLEAN NOT NULL DEFAULT FALSE,
    -- tỷ lệ tách giá trị cho gói HYBRID: bao nhiêu % giá thuộc phần PT
    pt_value_ratio      NUMERIC(4,3),
    max_freeze_days     INTEGER NOT NULL DEFAULT 0,   -- 0 = không cho bảo lưu
    max_freeze_times    SMALLINT NOT NULL DEFAULT 0,
    is_transferable     BOOLEAN NOT NULL DEFAULT FALSE,
    is_refundable       BOOLEAN NOT NULL DEFAULT FALSE,
    description         TEXT,
    display_order       INTEGER NOT NULL DEFAULT 0,
    status              VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT chk_pkg_type CHECK (package_type IN
        ('TIME_BASED','SESSION_BASED','HYBRID','DAY_PASS')),
    CONSTRAINT chk_pkg_status CHECK (status IN ('ACTIVE','ARCHIVED')),
    CONSTRAINT chk_pkg_duration CHECK (duration_days IS NULL OR duration_days > 0),
    CONSTRAINT chk_pkg_sessions CHECK (session_count IS NULL OR session_count > 0),
    -- ràng buộc tính nhất quán của package_type
    CONSTRAINT chk_pkg_shape CHECK (
        (package_type = 'TIME_BASED'    AND duration_days IS NOT NULL AND session_count IS NULL)
     OR (package_type = 'SESSION_BASED' AND session_count IS NOT NULL)
     OR (package_type = 'HYBRID'        AND duration_days IS NOT NULL AND session_count IS NOT NULL
                                        AND pt_value_ratio IS NOT NULL)
     OR (package_type = 'DAY_PASS'      AND duration_days = 1)
    )
);

-- PHIÊN BẢN GIÁ — không sửa giá trực tiếp trên memberships, tạo phiên bản mới.
-- Cho phép chính sách tăng/giảm giá theo thời gian mà không phá dữ liệu lịch sử.
CREATE TABLE membership_prices (
    id              BIGSERIAL PRIMARY KEY,
    membership_id   BIGINT NOT NULL REFERENCES memberships(id) ON DELETE CASCADE,
    price           NUMERIC(14,2) NOT NULL,
    valid_from      DATE NOT NULL,
    valid_to        DATE,                              -- NULL = còn hiệu lực
    created_by      BIGINT REFERENCES users(id),
    note            VARCHAR(255),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT chk_price_positive CHECK (price >= 0),
    CONSTRAINT chk_price_range    CHECK (valid_to IS NULL OR valid_to >= valid_from)
);
CREATE INDEX idx_mprices_lookup ON membership_prices(membership_id, valid_from DESC);

-- Gói cho phép vào khu vực nào (trả lời câu hỏi giới hạn giữa class và membership)
CREATE TABLE access_scopes (
    membership_id BIGINT NOT NULL REFERENCES memberships(id) ON DELETE CASCADE,
    area_code     VARCHAR(30) NOT NULL,   -- GYM_FLOOR|YOGA_STUDIO|POOL|SAUNA|GROUP_CLASS
    PRIMARY KEY (membership_id, area_code)
);

CREATE TABLE promotions (
    id                  BIGSERIAL PRIMARY KEY,
    code                VARCHAR(30) NOT NULL UNIQUE,
    name                VARCHAR(150) NOT NULL,
    discount_type       VARCHAR(20) NOT NULL,      -- PERCENT | FIXED_AMOUNT | BONUS_DAYS
    discount_value      NUMERIC(14,2) NOT NULL,
    applies_to          VARCHAR(20) NOT NULL DEFAULT 'ALL',  -- ALL|SPECIFIC
    min_contract_value  NUMERIC(14,2),
    valid_from          DATE NOT NULL,
    valid_to            DATE NOT NULL,
    usage_limit         INTEGER,
    usage_count         INTEGER NOT NULL DEFAULT 0,
    status              VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT chk_promo_type  CHECK (discount_type IN ('PERCENT','FIXED_AMOUNT','BONUS_DAYS')),
    CONSTRAINT chk_promo_range CHECK (valid_to >= valid_from),
    CONSTRAINT chk_promo_usage CHECK (usage_limit IS NULL OR usage_count <= usage_limit)
);

CREATE TABLE promotion_memberships (
    promotion_id  BIGINT NOT NULL REFERENCES promotions(id) ON DELETE CASCADE,
    membership_id BIGINT NOT NULL REFERENCES memberships(id) ON DELETE CASCADE,
    PRIMARY KEY (promotion_id, membership_id)
);

-- Hạn mức chiết khấu theo vai trò — chính sách giá do Admin cấu hình
CREATE TABLE discount_policies (
    id                       BIGSERIAL PRIMARY KEY,
    role                     VARCHAR(20) NOT NULL UNIQUE,
    max_discount_percent     NUMERIC(5,2) NOT NULL DEFAULT 0,
    requires_approval_above  NUMERIC(5,2) NOT NULL DEFAULT 0,
    updated_by               BIGINT REFERENCES users(id),
    updated_at               TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_disc_pct CHECK (max_discount_percent BETWEEN 0 AND 100)
);

-- ================== HỢP ĐỒNG ĐĂNG KÝ GÓI TẬP ==================
-- Điểm khác biệt lớn nhất so với schema cũ:
--   • có start_date / end_date  → biết gói hết hạn khi nào
--   • SNAPSHOT toàn bộ điều khoản thương mại → sửa giá gói KHÔNG ảnh hưởng hợp đồng cũ
--   • bỏ registration_type (trùng lặp với training_time)
--   • status là máy trạng thái 8 giá trị có định nghĩa rõ ràng
CREATE TABLE registrations (
    id                  BIGSERIAL PRIMARY KEY,
    registration_code   VARCHAR(30) NOT NULL UNIQUE,        -- REG-2026-000123
    member_id           BIGINT NOT NULL REFERENCES members(id) ON DELETE RESTRICT,
    membership_id       BIGINT NOT NULL REFERENCES memberships(id) ON DELETE RESTRICT,
    sold_by             BIGINT REFERENCES staff(id),         -- Sale — cơ sở tính hoa hồng
    assigned_trainer_id BIGINT REFERENCES trainers(id),      -- PT chính (nếu gói có PT)

    -- SNAPSHOT điều khoản tại thời điểm ký (KHÔNG đọc ngược về memberships khi báo cáo)
    package_type        VARCHAR(20)   NOT NULL,
    duration_days       INTEGER,
    sessions_total      INTEGER,
    list_price          NUMERIC(14,2) NOT NULL,
    discount_amount     NUMERIC(14,2) NOT NULL DEFAULT 0,
    discount_reason     VARCHAR(255),
    discount_approved_by BIGINT REFERENCES users(id),
    promotion_id        BIGINT REFERENCES promotions(id),
    final_price         NUMERIC(14,2) NOT NULL,

    contract_date       DATE NOT NULL DEFAULT CURRENT_DATE,
    start_date          DATE,                 -- NULL khi chưa kích hoạt
    end_date            DATE,                 -- start_date + duration_days, đẩy lùi khi bảo lưu
    activated_at        TIMESTAMPTZ,
    closed_at           TIMESTAMPTZ,

    status              VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    close_reason        VARCHAR(255),
    note                TEXT,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT chk_reg_status CHECK (status IN
        ('DRAFT','PENDING_PAYMENT','ACTIVE','FROZEN','COMPLETED',
         'CANCELLED','TRANSFERRED','REFUNDED')),
    CONSTRAINT chk_reg_prices CHECK (
        list_price >= 0 AND discount_amount >= 0
        AND discount_amount <= list_price
        AND final_price = list_price - discount_amount),
    CONSTRAINT chk_reg_dates CHECK (end_date IS NULL OR start_date IS NULL
                                    OR end_date >= start_date),
    -- ACTIVE bắt buộc phải có ngày bắt đầu
    CONSTRAINT chk_reg_active_dates CHECK (
        status <> 'ACTIVE' OR start_date IS NOT NULL)
);
CREATE INDEX idx_reg_member       ON registrations(member_id, status);
CREATE INDEX idx_reg_membership   ON registrations(membership_id);
CREATE INDEX idx_reg_sold_by      ON registrations(sold_by, contract_date);
CREATE INDEX idx_reg_active_exp   ON registrations(end_date) WHERE status = 'ACTIVE';
CREATE TRIGGER trg_reg_updated BEFORE UPDATE ON registrations
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- BẢO LƯU — yêu cầu trực tiếp của thầy
CREATE TABLE registration_freezes (
    id              BIGSERIAL PRIMARY KEY,
    registration_id BIGINT NOT NULL REFERENCES registrations(id) ON DELETE CASCADE,
    from_date       DATE NOT NULL,
    to_date         DATE NOT NULL,
    days            INTEGER NOT NULL,
    reason          VARCHAR(255) NOT NULL,
    reason_type     VARCHAR(20) NOT NULL DEFAULT 'PERSONAL',  -- PERSONAL|MEDICAL|TRAVEL|OTHER
    attachment_url  VARCHAR(500),                             -- giấy tờ y tế
    requested_by    BIGINT REFERENCES users(id),
    approved_by     BIGINT REFERENCES users(id),
    status          VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    ended_early_at  DATE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT chk_freeze_status CHECK (status IN
        ('PENDING','APPROVED','REJECTED','ACTIVE','ENDED','CANCELLED')),
    CONSTRAINT chk_freeze_dates  CHECK (to_date >= from_date),
    CONSTRAINT chk_freeze_days   CHECK (days = (to_date - from_date) + 1)
);
CREATE INDEX idx_freeze_reg ON registration_freezes(registration_id);
-- Một hợp đồng không thể có 2 khoảng bảo lưu chồng nhau
ALTER TABLE registration_freezes ADD CONSTRAINT no_overlap_freeze
    EXCLUDE USING gist (
        registration_id WITH =,
        daterange(from_date, to_date, '[]') WITH &&
    ) WHERE (status IN ('APPROVED','ACTIVE'));

CREATE TABLE registration_transfers (
    id                  BIGSERIAL PRIMARY KEY,
    registration_id     BIGINT NOT NULL REFERENCES registrations(id),
    from_member_id      BIGINT NOT NULL REFERENCES members(id),
    to_member_id        BIGINT NOT NULL REFERENCES members(id),
    new_registration_id BIGINT REFERENCES registrations(id),
    transfer_fee        NUMERIC(14,2) NOT NULL DEFAULT 0,
    remaining_value     NUMERIC(14,2) NOT NULL,
    approved_by         BIGINT REFERENCES users(id),
    transferred_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_transfer_diff CHECK (from_member_id <> to_member_id)
);

-- 2–3 PT cùng chăm sóc 1 hội viên (yêu cầu của thầy)
CREATE TABLE member_trainers (
    id          BIGSERIAL PRIMARY KEY,
    member_id   BIGINT NOT NULL REFERENCES members(id) ON DELETE CASCADE,
    trainer_id  BIGINT NOT NULL REFERENCES trainers(id) ON DELETE CASCADE,
    role        VARCHAR(20) NOT NULL DEFAULT 'PRIMARY',
    from_date   DATE NOT NULL DEFAULT CURRENT_DATE,
    to_date     DATE,
    assigned_by BIGINT REFERENCES users(id),
    note        VARCHAR(255),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT chk_mta_role  CHECK (role IN ('PRIMARY','SECONDARY','SUBSTITUTE')),
    CONSTRAINT chk_mta_dates CHECK (to_date IS NULL OR to_date >= from_date)
);
CREATE INDEX idx_mta_member  ON member_trainers(member_id)  WHERE to_date IS NULL;
CREATE INDEX idx_mta_trainer ON member_trainers(trainer_id) WHERE to_date IS NULL;
-- Mỗi hội viên chỉ có 1 PT PRIMARY tại một thời điểm
CREATE UNIQUE INDEX uq_one_primary_trainer
    ON member_trainers(member_id)
    WHERE role = 'PRIMARY' AND to_date IS NULL;


-- =====================================================================
-- M4. ACCESS CONTROL — Thẻ, Check-in, Sự cố
-- =====================================================================

CREATE TABLE access_cards (
    id          BIGSERIAL PRIMARY KEY,
    person_id   BIGINT NOT NULL REFERENCES persons(id) ON DELETE CASCADE,
    card_uid    VARCHAR(64) NOT NULL UNIQUE,      -- UID thẻ Mifare/RFID
    card_type   VARCHAR(20) NOT NULL DEFAULT 'RFID',
    issued_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    revoked_at  TIMESTAMPTZ,
    revoke_reason VARCHAR(255),
    CONSTRAINT chk_card_type CHECK (card_type IN ('RFID','NFC','QR_STATIC'))
);
CREATE INDEX idx_cards_person ON access_cards(person_id) WHERE revoked_at IS NULL;

-- Secret dùng để sinh QR động HMAC-TOTP phía client
CREATE TABLE qr_secrets (
    person_id     BIGINT PRIMARY KEY REFERENCES persons(id) ON DELETE CASCADE,
    secret_enc    BYTEA NOT NULL,          -- mã hóa AES-256-GCM ở tầng ứng dụng
    algorithm     VARCHAR(20) NOT NULL DEFAULT 'HMAC-SHA256',
    period_sec    SMALLINT NOT NULL DEFAULT 30,
    rotated_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE check_ins (
    id              BIGSERIAL PRIMARY KEY,
    member_id       BIGINT REFERENCES members(id),
    trainer_id      BIGINT REFERENCES trainers(id),   -- PT chấm công vào ca
    staff_id        BIGINT REFERENCES staff(id),      -- nhân viên chấm công
    registration_id BIGINT REFERENCES registrations(id),

    checked_in_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    checked_out_at  TIMESTAMPTZ,
    method          VARCHAR(20) NOT NULL,     -- QR_DYNAMIC|RFID|MANUAL|FACE|DAY_PASS
    result          VARCHAR(20) NOT NULL,     -- ALLOWED|DENIED_EXPIRED|DENIED_FROZEN|
                                              -- DENIED_UNPAID|DENIED_NOT_FOUND|ALLOWED_OVERRIDE
    verified_by     BIGINT REFERENCES users(id),   -- lễ tân xác nhận
    face_match_score NUMERIC(4,3),                 -- lớp 3 tùy chọn
    photo_url       VARCHAR(500),                  -- ảnh chụp lúc check-in
    device_id       VARCHAR(60),
    manual_reason   VARCHAR(255),                  -- BẮT BUỘC khi method = MANUAL
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT chk_ci_method CHECK (method IN
        ('QR_DYNAMIC','RFID','MANUAL','FACE','DAY_PASS')),
    CONSTRAINT chk_ci_result CHECK (result IN
        ('ALLOWED','ALLOWED_OVERRIDE','DENIED_EXPIRED','DENIED_FROZEN',
         'DENIED_UNPAID','DENIED_NOT_FOUND','DENIED_SUSPECT')),
    CONSTRAINT chk_ci_manual_reason CHECK (method <> 'MANUAL' OR manual_reason IS NOT NULL),
    CONSTRAINT chk_ci_out CHECK (checked_out_at IS NULL OR checked_out_at >= checked_in_at),
    CONSTRAINT chk_ci_subject CHECK (
        num_nonnulls(member_id, trainer_id, staff_id) >= 1)
);
CREATE INDEX idx_ci_member_at ON check_ins(member_id, checked_in_at DESC);
CREATE INDEX idx_ci_at        ON check_ins(checked_in_at DESC);
-- Ai đang có mặt trong phòng tập (đã check-in, chưa check-out)
CREATE INDEX idx_ci_inside    ON check_ins(checked_in_at) WHERE checked_out_at IS NULL;

CREATE TABLE check_in_incidents (
    id              BIGSERIAL PRIMARY KEY,
    check_in_id     BIGINT REFERENCES check_ins(id),
    member_id       BIGINT REFERENCES members(id),
    incident_type   VARCHAR(30) NOT NULL,  -- SUSPECTED_SHARING|ANTI_PASSBACK|
                                           -- FACE_MISMATCH|EXPIRED_ATTEMPT|REPLAY_ATTEMPT
    severity        VARCHAR(10) NOT NULL DEFAULT 'MEDIUM',
    detail          TEXT,
    handled_by      BIGINT REFERENCES users(id),
    handled_at      TIMESTAMPTZ,
    resolution      TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_inc_sev CHECK (severity IN ('LOW','MEDIUM','HIGH'))
);


-- =====================================================================
-- M5. TRAINING — Buổi PT, Sổ cái tín dụng, Lớp học, Giáo án
-- =====================================================================

CREATE TABLE trainer_availability (
    id          BIGSERIAL PRIMARY KEY,
    trainer_id  BIGINT NOT NULL REFERENCES trainers(id) ON DELETE CASCADE,
    day_of_week SMALLINT,               -- 0=CN..6=T7; NULL nếu là ngày cụ thể
    specific_date DATE,                 -- dùng cho ngày nghỉ / lịch đặc biệt
    start_time  TIME NOT NULL,
    end_time    TIME NOT NULL,
    is_available BOOLEAN NOT NULL DEFAULT TRUE,   -- FALSE = khai báo nghỉ
    valid_from  DATE NOT NULL DEFAULT CURRENT_DATE,
    valid_to    DATE,
    CONSTRAINT chk_avail_time CHECK (end_time > start_time),
    CONSTRAINT chk_avail_dow  CHECK (day_of_week IS NULL OR day_of_week BETWEEN 0 AND 6),
    CONSTRAINT chk_avail_kind CHECK (num_nonnulls(day_of_week, specific_date) = 1)
);
CREATE INDEX idx_avail_trainer ON trainer_availability(trainer_id);

CREATE TABLE pt_bookings (
    id              BIGSERIAL PRIMARY KEY,
    member_id       BIGINT NOT NULL REFERENCES members(id),
    trainer_id      BIGINT NOT NULL REFERENCES trainers(id),
    registration_id BIGINT REFERENCES registrations(id),
    requested_start TIMESTAMPTZ NOT NULL,
    requested_end   TIMESTAMPTZ NOT NULL,
    status          VARCHAR(20) NOT NULL DEFAULT 'PENDING_TRAINER',
    requested_by    BIGINT REFERENCES users(id),
    responded_at    TIMESTAMPTZ,
    reject_reason   VARCHAR(255),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT chk_bk_status CHECK (status IN
        ('PENDING_TRAINER','CONFIRMED','REJECTED','CANCELLED_BY_MEMBER',
         'CANCELLED_BY_TRAINER','EXPIRED')),
    CONSTRAINT chk_bk_time CHECK (requested_end > requested_start)
);
CREATE INDEX idx_bk_trainer ON pt_bookings(trainer_id, requested_start);
CREATE INDEX idx_bk_member  ON pt_bookings(member_id, requested_start DESC);

-- BUỔI TẬP PT — thay thế/mở rộng training_histories của schema cũ
CREATE TABLE pt_sessions (
    id                  BIGSERIAL PRIMARY KEY,
    booking_id          BIGINT REFERENCES pt_bookings(id),
    member_id           BIGINT NOT NULL REFERENCES members(id),
    trainer_id          BIGINT NOT NULL REFERENCES trainers(id),  -- PT THỰC TẾ dạy
    registration_id     BIGINT REFERENCES registrations(id),
    room_id             BIGINT,                                   -- FK khai báo bên dưới

    session_type        VARCHAR(20) NOT NULL DEFAULT 'PAID_PT',
    scheduled_start     TIMESTAMPTZ NOT NULL,
    scheduled_end       TIMESTAMPTZ NOT NULL,
    actual_start        TIMESTAMPTZ,
    actual_end          TIMESTAMPTZ,

    status              VARCHAR(20) NOT NULL DEFAULT 'SCHEDULED',
    -- XÁC NHẬN HAI CHIỀU — chống khai khống buổi tập
    trainer_confirmed_at TIMESTAMPTZ,
    member_confirmed_at  TIMESTAMPTZ,
    auto_confirmed       BOOLEAN NOT NULL DEFAULT FALSE,  -- cờ kiểm toán

    cancelled_by        VARCHAR(20),        -- MEMBER|TRAINER|SYSTEM
    cancelled_at        TIMESTAMPTZ,
    cancel_reason       VARCHAR(255),
    is_late_cancel      BOOLEAN NOT NULL DEFAULT FALSE,   -- hủy <4h → mất buổi
    note                TEXT,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT chk_pts_type CHECK (session_type IN
        ('PAID_PT','COMPLIMENTARY','TRIAL','ORIENTATION','MAKEUP','ASSESSMENT')),
    CONSTRAINT chk_pts_status CHECK (status IN
        ('SCHEDULED','IN_PROGRESS','COMPLETED','NO_SHOW_MEMBER',
         'NO_SHOW_TRAINER','CANCELLED')),
    CONSTRAINT chk_pts_time CHECK (scheduled_end > scheduled_start)
);
CREATE INDEX idx_pts_trainer ON pt_sessions(trainer_id, scheduled_start);
CREATE INDEX idx_pts_member  ON pt_sessions(member_id, scheduled_start DESC);
CREATE INDEX idx_pts_upcoming ON pt_sessions(scheduled_start) WHERE status = 'SCHEDULED';

-- CHỐNG TRÙNG LỊCH Ở TẦNG DB — không phụ thuộc code nhớ kiểm tra
ALTER TABLE pt_sessions ADD CONSTRAINT no_trainer_overlap
    EXCLUDE USING gist (
        trainer_id WITH =,
        tstzrange(scheduled_start, scheduled_end) WITH &&
    ) WHERE (status IN ('SCHEDULED','IN_PROGRESS'));

ALTER TABLE pt_sessions ADD CONSTRAINT no_member_overlap
    EXCLUDE USING gist (
        member_id WITH =,
        tstzrange(scheduled_start, scheduled_end) WITH &&
    ) WHERE (status IN ('SCHEDULED','IN_PROGRESS'));

-- ============ SỔ CÁI TÍN DỤNG BUỔI TẬP — ĐÓNG GÓP CHÍNH ============
-- APPEND-ONLY. Không UPDATE, không DELETE. Sửa sai bằng bút toán ADJUST.
-- Bất biến: với mọi registration_id,
--           SUM(delta) == balance_after của bút toán mới nhất
CREATE TABLE session_credit_ledger (
    id              BIGSERIAL PRIMARY KEY,
    registration_id BIGINT NOT NULL REFERENCES registrations(id) ON DELETE RESTRICT,
    entry_type      VARCHAR(20) NOT NULL,
    delta           INTEGER NOT NULL,
    balance_after   INTEGER NOT NULL,
    source_type     VARCHAR(30),      -- PT_SESSION|REGISTRATION|TRANSFER|MANUAL|SYSTEM
    source_id       BIGINT,
    reason          TEXT,
    created_by      BIGINT REFERENCES users(id),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT chk_scl_type CHECK (entry_type IN
        ('GRANT','CONSUME','REFUND','EXPIRE','ADJUST','TRANSFER_IN','TRANSFER_OUT')),
    CONSTRAINT chk_scl_delta   CHECK (delta <> 0),
    CONSTRAINT chk_scl_balance CHECK (balance_after >= 0),
    -- ADJUST bắt buộc có lý do và người thực hiện
    CONSTRAINT chk_scl_adjust  CHECK (entry_type <> 'ADJUST'
                                      OR (reason IS NOT NULL AND created_by IS NOT NULL))
);
CREATE INDEX idx_scl_reg ON session_credit_ledger(registration_id, id DESC);
-- Một buổi tập chỉ được trừ credit đúng MỘT lần
CREATE UNIQUE INDEX uq_scl_consume_once
    ON session_credit_ledger(source_type, source_id)
    WHERE entry_type = 'CONSUME' AND source_type = 'PT_SESSION';

-- Chặn UPDATE/DELETE ở tầng DB
CREATE OR REPLACE FUNCTION forbid_mutation() RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION 'Bảng % là append-only, không cho phép % ',
                    TG_TABLE_NAME, TG_OP;
END; $$ LANGUAGE plpgsql;

CREATE TRIGGER trg_scl_append_only
    BEFORE UPDATE OR DELETE ON session_credit_ledger
    FOR EACH ROW EXECUTE FUNCTION forbid_mutation();

-- ================== LỚP HỌC & PHÒNG TẬP ==================
-- Tách bạch: phòng (không gian) ≠ lớp (định nghĩa) ≠ lịch ≠ buổi cụ thể
CREATE TABLE rooms (
    id          BIGSERIAL PRIMARY KEY,
    code        VARCHAR(30) NOT NULL,
    name        VARCHAR(120) NOT NULL,
    area_code   VARCHAR(30) NOT NULL,          -- khớp với access_scopes.area_code
    capacity    INTEGER NOT NULL,
    floor       VARCHAR(20),
    status      VARCHAR(20) NOT NULL DEFAULT 'AVAILABLE',
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_room_cap    CHECK (capacity > 0),
    CONSTRAINT chk_room_status CHECK (status IN ('AVAILABLE','MAINTENANCE','CLOSED'))
);
ALTER TABLE pt_sessions ADD CONSTRAINT fk_pts_room
    FOREIGN KEY (room_id) REFERENCES rooms(id);

CREATE TABLE class_definitions (
    id              BIGSERIAL PRIMARY KEY,
    code            VARCHAR(30) NOT NULL UNIQUE,
    name            VARCHAR(120) NOT NULL,
    class_type      VARCHAR(30) NOT NULL,   -- YOGA|FITNESS|HIIT|ZUMBA|SPINNING|BOXING...
    description     TEXT,
    default_duration_min INTEGER NOT NULL DEFAULT 60,
    default_capacity     INTEGER NOT NULL DEFAULT 20,
    level           VARCHAR(20),            -- BEGINNER|INTERMEDIATE|ADVANCED
    required_area   VARCHAR(30),            -- khu vực gói phải bao gồm
    status          VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_cls_status CHECK (status IN ('ACTIVE','ARCHIVED'))
    -- Ghi chú: class_type là VARCHAR có CHECK ở tầng ứng dụng thay vì
    -- CHECK IN ('fitness','yoga') như schema cũ — để mở rộng loại lớp mới
    -- mà không cần migration.
);

CREATE TABLE class_schedules (
    id              BIGSERIAL PRIMARY KEY,
    class_def_id    BIGINT NOT NULL REFERENCES class_definitions(id) ON DELETE CASCADE,
    room_id         BIGINT NOT NULL REFERENCES rooms(id),
    trainer_id      BIGINT REFERENCES trainers(id),
    day_of_week     SMALLINT NOT NULL,
    start_time      TIME NOT NULL,
    duration_min    INTEGER NOT NULL,
    capacity        INTEGER NOT NULL,
    valid_from      DATE NOT NULL,
    valid_to        DATE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_csch_dow CHECK (day_of_week BETWEEN 0 AND 6),
    CONSTRAINT chk_csch_cap CHECK (capacity > 0)
);

-- Buổi lớp cụ thể (được sinh từ schedule bởi job, hoặc tạo thủ công)
CREATE TABLE class_sessions (
    id              BIGSERIAL PRIMARY KEY,
    schedule_id     BIGINT REFERENCES class_schedules(id),
    class_def_id    BIGINT NOT NULL REFERENCES class_definitions(id),
    room_id         BIGINT NOT NULL REFERENCES rooms(id),
    trainer_id      BIGINT REFERENCES trainers(id),
    starts_at       TIMESTAMPTZ NOT NULL,
    ends_at         TIMESTAMPTZ NOT NULL,
    capacity        INTEGER NOT NULL,
    booked_count    INTEGER NOT NULL DEFAULT 0,
    status          VARCHAR(20) NOT NULL DEFAULT 'SCHEDULED',
    cancel_reason   VARCHAR(255),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT chk_clsess_status CHECK (status IN
        ('SCHEDULED','IN_PROGRESS','COMPLETED','CANCELLED')),
    CONSTRAINT chk_clsess_time   CHECK (ends_at > starts_at),
    CONSTRAINT chk_clsess_booked CHECK (booked_count BETWEEN 0 AND capacity)
);
CREATE INDEX idx_clsess_time ON class_sessions(starts_at);
-- Một phòng không có 2 lớp cùng giờ
ALTER TABLE class_sessions ADD CONSTRAINT no_room_overlap
    EXCLUDE USING gist (
        room_id WITH =,
        tstzrange(starts_at, ends_at) WITH &&
    ) WHERE (status IN ('SCHEDULED','IN_PROGRESS'));

-- Hội viên dùng QUYỀN TRUY CẬP (registration) để đặt chỗ trong buổi lớp
-- → trả lời câu hỏi của cô Trinh về quan hệ class ↔ membership
CREATE TABLE class_bookings (
    id               BIGSERIAL PRIMARY KEY,
    class_session_id BIGINT NOT NULL REFERENCES class_sessions(id) ON DELETE CASCADE,
    member_id        BIGINT NOT NULL REFERENCES members(id),
    registration_id  BIGINT NOT NULL REFERENCES registrations(id),
    status           VARCHAR(20) NOT NULL DEFAULT 'BOOKED',
    waitlist_position SMALLINT,
    booked_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    cancelled_at     TIMESTAMPTZ,

    CONSTRAINT chk_cb_status CHECK (status IN
        ('BOOKED','WAITLISTED','ATTENDED','NO_SHOW','CANCELLED')),
    UNIQUE (class_session_id, member_id)
);
CREATE INDEX idx_cb_member ON class_bookings(member_id, booked_at DESC);

-- ================== THƯ VIỆN BÀI TẬP & GIÁO ÁN ==================
CREATE TABLE exercises (
    id                BIGSERIAL PRIMARY KEY,
    code              VARCHAR(60) NOT NULL UNIQUE,
    name_vi           VARCHAR(200) NOT NULL,
    name_en           VARCHAR(200),
    muscle_group      VARCHAR(40) NOT NULL,   -- CHEST|BACK|LEGS|SHOULDERS|ARMS|CORE|CARDIO
    secondary_muscles VARCHAR(200),
    equipment         VARCHAR(60),            -- BARBELL|DUMBBELL|MACHINE|BODYWEIGHT|CABLE
    difficulty        VARCHAR(20) NOT NULL DEFAULT 'BEGINNER',
    instructions      TEXT,
    media_url         VARCHAR(500),
    source            VARCHAR(60),            -- FREE_EXERCISE_DB | WGER | CUSTOM
    license           VARCHAR(60),            -- ghi rõ để tuân thủ bản quyền
    is_reviewed       BOOLEAN NOT NULL DEFAULT FALSE,  -- bản dịch đã có người rà soát
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_ex_diff CHECK (difficulty IN ('BEGINNER','INTERMEDIATE','ADVANCED'))
);
CREATE INDEX idx_ex_muscle ON exercises(muscle_group, equipment);

CREATE TABLE workout_templates (
    id              BIGSERIAL PRIMARY KEY,
    name            VARCHAR(150) NOT NULL,
    goal            VARCHAR(30),            -- LOSE_FAT|GAIN_MUSCLE|STRENGTH|ENDURANCE
    level           VARCHAR(20),
    duration_weeks  SMALLINT,
    days_per_week   SMALLINT,
    description     TEXT,
    created_by      BIGINT REFERENCES users(id),
    is_public       BOOLEAN NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE workout_template_items (
    id           BIGSERIAL PRIMARY KEY,
    template_id  BIGINT NOT NULL REFERENCES workout_templates(id) ON DELETE CASCADE,
    day_index    SMALLINT NOT NULL,
    order_index  SMALLINT NOT NULL,
    exercise_id  BIGINT NOT NULL REFERENCES exercises(id),
    sets         SMALLINT,
    reps         VARCHAR(20),          -- "8-12" hoặc "AMRAP"
    rest_sec     SMALLINT,
    note         VARCHAR(255),
    UNIQUE (template_id, day_index, order_index)
);

CREATE TABLE workout_plans (
    id           BIGSERIAL PRIMARY KEY,
    member_id    BIGINT NOT NULL REFERENCES members(id) ON DELETE CASCADE,
    trainer_id   BIGINT REFERENCES trainers(id),
    template_id  BIGINT REFERENCES workout_templates(id),
    name         VARCHAR(150) NOT NULL,
    start_date   DATE NOT NULL,
    end_date     DATE,
    status       VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    ai_generated BOOLEAN NOT NULL DEFAULT FALSE,   -- minh bạch nguồn gốc
    reviewed_by  BIGINT REFERENCES users(id),      -- PT PHẢI duyệt nếu ai_generated
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_wp_status CHECK (status IN ('ACTIVE','COMPLETED','PAUSED','CANCELLED')),
    CONSTRAINT chk_wp_ai_reviewed CHECK (NOT ai_generated OR reviewed_by IS NOT NULL)
);

CREATE TABLE workout_plan_items (
    id           BIGSERIAL PRIMARY KEY,
    plan_id      BIGINT NOT NULL REFERENCES workout_plans(id) ON DELETE CASCADE,
    day_index    SMALLINT NOT NULL,
    order_index  SMALLINT NOT NULL,
    exercise_id  BIGINT NOT NULL REFERENCES exercises(id),
    sets         SMALLINT,
    reps         VARCHAR(20),
    target_weight_kg NUMERIC(6,2),
    rest_sec     SMALLINT,
    note         VARCHAR(255)
);

CREATE TABLE workout_logs (
    id            BIGSERIAL PRIMARY KEY,
    plan_item_id  BIGINT REFERENCES workout_plan_items(id),
    member_id     BIGINT NOT NULL REFERENCES members(id) ON DELETE CASCADE,
    exercise_id   BIGINT NOT NULL REFERENCES exercises(id),
    pt_session_id BIGINT REFERENCES pt_sessions(id),
    performed_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    sets_done     SMALLINT,
    reps_done     VARCHAR(40),
    weight_kg     NUMERIC(6,2),
    rpe           SMALLINT,                -- Rate of Perceived Exertion 1-10
    note          VARCHAR(255),
    CONSTRAINT chk_wl_rpe CHECK (rpe IS NULL OR rpe BETWEEN 1 AND 10)
);
CREATE INDEX idx_wl_member ON workout_logs(member_id, performed_at DESC);

CREATE TABLE body_metrics (
    id              BIGSERIAL PRIMARY KEY,
    member_id       BIGINT NOT NULL REFERENCES members(id) ON DELETE CASCADE,
    measured_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    measured_by     BIGINT REFERENCES users(id),
    source          VARCHAR(20) NOT NULL DEFAULT 'MANUAL',  -- MANUAL|INBODY_OCR|DEVICE_SYNC

    height_cm       NUMERIC(5,2),
    weight_kg       NUMERIC(5,2),
    body_fat_pct    NUMERIC(4,2),
    muscle_mass_kg  NUMERIC(5,2),
    visceral_fat    NUMERIC(4,1),
    bmr_kcal        INTEGER,
    -- BMI là giá trị dẫn xuất → cột tính toán, không lưu tay
    bmi             NUMERIC(5,2) GENERATED ALWAYS AS (
                        CASE WHEN height_cm > 0
                             THEN weight_kg / ((height_cm/100) * (height_cm/100))
                        END) STORED,

    chest_cm        NUMERIC(5,2),
    waist_cm        NUMERIC(5,2),
    hip_cm          NUMERIC(5,2),
    arm_cm          NUMERIC(5,2),
    thigh_cm        NUMERIC(5,2),

    photo_url       VARCHAR(500),          -- ảnh phiếu InBody hoặc ảnh tiến trình
    ocr_confidence  NUMERIC(4,3),          -- độ tin cậy khi source = INBODY_OCR
    confirmed_by_user BOOLEAN NOT NULL DEFAULT TRUE,  -- OCR phải được xác nhận
    note            VARCHAR(255),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT chk_bm_source  CHECK (source IN ('MANUAL','INBODY_OCR','DEVICE_SYNC')),
    CONSTRAINT chk_bm_weight  CHECK (weight_kg IS NULL OR weight_kg BETWEEN 20 AND 300),
    CONSTRAINT chk_bm_height  CHECK (height_cm IS NULL OR height_cm BETWEEN 80 AND 250),
    CONSTRAINT chk_bm_fat     CHECK (body_fat_pct IS NULL OR body_fat_pct BETWEEN 1 AND 70)
);
CREATE INDEX idx_bm_member ON body_metrics(member_id, measured_at DESC);


-- =====================================================================
-- M8. FACILITY — Thiết bị & Bảo trì
-- =====================================================================

-- Tách LOẠI thiết bị và TỪNG CÁI cụ thể (schema cũ nhập nhằng hai khái niệm này)
CREATE TABLE equipment_types (
    id              BIGSERIAL PRIMARY KEY,
    code            VARCHAR(30) NOT NULL UNIQUE,
    name            VARCHAR(150) NOT NULL,
    category        VARCHAR(40) NOT NULL,   -- CARDIO|STRENGTH|FREE_WEIGHT|ACCESSORY|YOGA
    brand           VARCHAR(80),
    model           VARCHAR(80),
    useful_life_months SMALLINT NOT NULL DEFAULT 60,   -- cơ sở tính khấu hao
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE equipment_items (
    id                  BIGSERIAL PRIMARY KEY,
    equipment_type_id   BIGINT NOT NULL REFERENCES equipment_types(id),
    room_id             BIGINT REFERENCES rooms(id),
    asset_code          VARCHAR(40) NOT NULL UNIQUE,   -- TM-003 = máy chạy bộ số 3
    serial_number       VARCHAR(80),
    origin              VARCHAR(100),
    purchase_price      NUMERIC(14,2),
    date_of_purchase    DATE,
    warranty_until      DATE,
    status              VARCHAR(20) NOT NULL DEFAULT 'OPERATIONAL',
    last_maintained_at  DATE,
    disposed_at         DATE,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT chk_eq_status CHECK (status IN
        ('OPERATIONAL','NEEDS_REPAIR','UNDER_MAINTENANCE','RETIRED')),
    CONSTRAINT chk_eq_price  CHECK (purchase_price IS NULL OR purchase_price >= 0),
    CONSTRAINT chk_eq_warranty CHECK (warranty_until IS NULL OR date_of_purchase IS NULL
                                      OR warranty_until >= date_of_purchase)
);
CREATE INDEX idx_eq_room   ON equipment_items(room_id);
CREATE INDEX idx_eq_status ON equipment_items(status) WHERE status <> 'OPERATIONAL';

CREATE TABLE maintenance_work_orders (
    id                BIGSERIAL PRIMARY KEY,
    equipment_item_id BIGINT REFERENCES equipment_items(id),
    room_id           BIGINT REFERENCES rooms(id),
    reported_by       BIGINT REFERENCES users(id),
    feedback_id       BIGINT,                  -- FK khai báo sau khi có bảng feedbacks
    wo_type           VARCHAR(20) NOT NULL DEFAULT 'CORRECTIVE',  -- PREVENTIVE|CORRECTIVE
    priority          VARCHAR(10) NOT NULL DEFAULT 'MEDIUM',
    description       TEXT NOT NULL,
    status            VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    assigned_to       BIGINT REFERENCES users(id),
    resolution        TEXT,
    cost              NUMERIC(14,2),
    expense_id        BIGINT,                  -- FK khai báo sau
    opened_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    resolved_at       TIMESTAMPTZ,

    CONSTRAINT chk_wo_status CHECK (status IN
        ('OPEN','IN_PROGRESS','WAITING_PARTS','RESOLVED','CANCELLED')),
    CONSTRAINT chk_wo_prio   CHECK (priority IN ('LOW','MEDIUM','HIGH','URGENT'))
);


-- =====================================================================
-- M6. BILLING — Hóa đơn, Thanh toán, Ca lễ tân
-- =====================================================================

CREATE TABLE invoices (
    id              BIGSERIAL PRIMARY KEY,
    invoice_no      VARCHAR(30) NOT NULL UNIQUE,     -- INV-2026-000123
    member_id       BIGINT REFERENCES members(id),
    person_id       BIGINT REFERENCES persons(id),   -- khách vãng lai chưa là hội viên
    registration_id BIGINT REFERENCES registrations(id),

    subtotal        NUMERIC(14,2) NOT NULL DEFAULT 0,
    discount_amount NUMERIC(14,2) NOT NULL DEFAULT 0,
    tax_amount      NUMERIC(14,2) NOT NULL DEFAULT 0,
    total_amount    NUMERIC(14,2) NOT NULL,
    paid_amount     NUMERIC(14,2) NOT NULL DEFAULT 0,
    balance_due     NUMERIC(14,2) GENERATED ALWAYS AS (total_amount - paid_amount) STORED,

    status          VARCHAR(20) NOT NULL DEFAULT 'UNPAID',
    issued_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    due_date        DATE,
    paid_at         TIMESTAMPTZ,
    issued_by       BIGINT REFERENCES users(id),
    note            TEXT,

    CONSTRAINT chk_inv_status CHECK (status IN
        ('UNPAID','PARTIALLY_PAID','PAID','OVERDUE','CANCELLED','REFUNDED')),
    CONSTRAINT chk_inv_amounts CHECK (
        total_amount >= 0 AND paid_amount >= 0 AND paid_amount <= total_amount)
);
CREATE INDEX idx_inv_member ON invoices(member_id, issued_at DESC);
CREATE INDEX idx_inv_unpaid ON invoices(due_date) WHERE status IN ('UNPAID','PARTIALLY_PAID');

CREATE TABLE invoice_items (
    id          BIGSERIAL PRIMARY KEY,
    invoice_id  BIGINT NOT NULL REFERENCES invoices(id) ON DELETE CASCADE,
    item_type   VARCHAR(30) NOT NULL,   -- MEMBERSHIP|PT_PACKAGE|DAY_PASS|LOCKER|PRODUCT|FEE
    reference_id BIGINT,
    description VARCHAR(255) NOT NULL,
    quantity    INTEGER NOT NULL DEFAULT 1,
    unit_price  NUMERIC(14,2) NOT NULL,
    discount    NUMERIC(14,2) NOT NULL DEFAULT 0,
    line_total  NUMERIC(14,2) GENERATED ALWAYS AS
                    (quantity * unit_price - discount) STORED,
    CONSTRAINT chk_ii_qty CHECK (quantity > 0)
);

-- CA LÀM VIỆC CỦA LỄ TÂN — đối soát tiền mặt cuối ca
CREATE TABLE cash_shifts (
    id                BIGSERIAL PRIMARY KEY,
    staff_id          BIGINT NOT NULL REFERENCES staff(id),
    opened_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    closed_at         TIMESTAMPTZ,
    opening_balance   NUMERIC(14,2) NOT NULL DEFAULT 0,
    expected_cash     NUMERIC(14,2),      -- opening + Σ thu tiền mặt trong ca
    counted_cash      NUMERIC(14,2),      -- số đếm thực tế
    difference        NUMERIC(14,2) GENERATED ALWAYS AS
                          (counted_cash - expected_cash) STORED,
    difference_reason TEXT,
    status            VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    verified_by       BIGINT REFERENCES users(id),

    CONSTRAINT chk_shift_status CHECK (status IN ('OPEN','CLOSED','DISCREPANCY')),
    -- Có chênh lệch thì BẮT BUỘC ghi lý do
    CONSTRAINT chk_shift_diff_reason CHECK (
        status <> 'DISCREPANCY' OR difference_reason IS NOT NULL)
);
CREATE UNIQUE INDEX uq_open_shift_per_staff
    ON cash_shifts(staff_id) WHERE status = 'OPEN';

CREATE TABLE payments (
    id                  BIGSERIAL PRIMARY KEY,
    payment_no          VARCHAR(30) NOT NULL UNIQUE,     -- PAY-2026-000123
    member_id           BIGINT REFERENCES members(id),
    cash_shift_id       BIGINT REFERENCES cash_shifts(id),

    method              VARCHAR(20) NOT NULL,
    amount              NUMERIC(14,2) NOT NULL,
    currency            CHAR(3) NOT NULL DEFAULT 'VND',
    status              VARCHAR(20) NOT NULL DEFAULT 'INITIATED',

    -- Chống double-charge khi client retry
    idempotency_key     VARCHAR(64) UNIQUE,
    -- Đối soát chuyển khoản / cổng thanh toán
    provider            VARCHAR(30),       -- VNPAY|MOMO|ZALOPAY|SEPAY|BANK_TRANSFER
    provider_txn_id     VARCHAR(100),
    transfer_content    VARCHAR(255),      -- nội dung CK: "GYM INV12345"
    bank_account        VARCHAR(50),
    pos_terminal_id     VARCHAR(50),
    pos_card_last4      CHAR(4),

    received_by         BIGINT REFERENCES users(id),
    paid_at             TIMESTAMPTZ,
    reconciled_at       TIMESTAMPTZ,
    raw_payload         JSONB,             -- payload webhook thô (để truy vết)
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT chk_pay_method CHECK (method IN
        ('CASH','BANK_TRANSFER','VIETQR','CARD_POS','E_WALLET','GATEWAY')),
    CONSTRAINT chk_pay_status CHECK (status IN
        ('INITIATED','PENDING','SUCCEEDED','FAILED','EXPIRED',
         'REFUNDED','PARTIALLY_REFUNDED')),
    CONSTRAINT chk_pay_amount CHECK (amount > 0),
    -- Tiền mặt phải gắn với một ca làm việc → đối soát được
    CONSTRAINT chk_pay_cash_shift CHECK (
        method <> 'CASH' OR status <> 'SUCCEEDED' OR cash_shift_id IS NOT NULL)
);
CREATE INDEX idx_pay_member ON payments(member_id, created_at DESC);
CREATE INDEX idx_pay_shift  ON payments(cash_shift_id);
CREATE UNIQUE INDEX uq_pay_provider_txn
    ON payments(provider, provider_txn_id)
    WHERE provider_txn_id IS NOT NULL;

-- N-N: một khoản thu có thể trả nhiều hóa đơn, một hóa đơn trả nhiều lần (trả góp)
CREATE TABLE payment_allocations (
    id          BIGSERIAL PRIMARY KEY,
    payment_id  BIGINT NOT NULL REFERENCES payments(id) ON DELETE CASCADE,
    invoice_id  BIGINT NOT NULL REFERENCES invoices(id),
    amount      NUMERIC(14,2) NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_alloc_amount CHECK (amount > 0),
    UNIQUE (payment_id, invoice_id)
);
CREATE INDEX idx_alloc_invoice ON payment_allocations(invoice_id);

CREATE TABLE payment_schedules (
    id              BIGSERIAL PRIMARY KEY,
    registration_id BIGINT NOT NULL REFERENCES registrations(id) ON DELETE CASCADE,
    installment_no  SMALLINT NOT NULL,
    due_date        DATE NOT NULL,
    amount          NUMERIC(14,2) NOT NULL,
    paid_amount     NUMERIC(14,2) NOT NULL DEFAULT 0,
    status          VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    CONSTRAINT chk_ps_status CHECK (status IN ('PENDING','PAID','OVERDUE','WAIVED')),
    UNIQUE (registration_id, installment_no)
);

CREATE TABLE refunds (
    id              BIGSERIAL PRIMARY KEY,
    registration_id BIGINT REFERENCES registrations(id),
    invoice_id      BIGINT REFERENCES invoices(id),
    payment_id      BIGINT REFERENCES payments(id),
    gross_amount    NUMERIC(14,2) NOT NULL,   -- giá trị còn lại chưa sử dụng
    penalty_amount  NUMERIC(14,2) NOT NULL DEFAULT 0,
    net_amount      NUMERIC(14,2) NOT NULL,   -- thực trả cho khách
    reason          TEXT NOT NULL,
    method          VARCHAR(20) NOT NULL,
    requested_by    BIGINT REFERENCES users(id),
    approved_by     BIGINT REFERENCES users(id),
    status          VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    processed_at    TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT chk_rf_status CHECK (status IN ('PENDING','APPROVED','REJECTED','PROCESSED')),
    CONSTRAINT chk_rf_amounts CHECK (
        gross_amount >= 0 AND penalty_amount >= 0
        AND net_amount = gross_amount - penalty_amount AND net_amount >= 0)
);

-- Lưu webhook thô để chống replay và phục vụ điều tra
CREATE TABLE webhook_events (
    id              BIGSERIAL PRIMARY KEY,
    provider        VARCHAR(30) NOT NULL,
    event_id        VARCHAR(120) NOT NULL,
    event_type      VARCHAR(60),
    signature       VARCHAR(500),
    payload         JSONB NOT NULL,
    signature_valid BOOLEAN,
    processed       BOOLEAN NOT NULL DEFAULT FALSE,
    processed_at    TIMESTAMPTZ,
    error_message   TEXT,
    received_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (provider, event_id)      -- chống xử lý trùng
);


-- =====================================================================
-- M7. FINANCE — Ghi nhận doanh thu, Chi phí, Lương
-- =====================================================================

-- Kế hoạch phân bổ doanh thu — tạo 1 lần khi hợp đồng chuyển ACTIVE
CREATE TABLE revenue_schedules (
    id                  BIGSERIAL PRIMARY KEY,
    registration_id     BIGINT NOT NULL UNIQUE REFERENCES registrations(id),
    total_amount        NUMERIC(14,2) NOT NULL,
    recognition_method  VARCHAR(20) NOT NULL,   -- STRAIGHT_LINE | PER_SESSION | IMMEDIATE
    start_date          DATE NOT NULL,
    end_date            DATE,
    total_units         INTEGER NOT NULL,       -- số ngày hoặc số buổi
    recognized_units    INTEGER NOT NULL DEFAULT 0,
    recognized_amount   NUMERIC(14,2) NOT NULL DEFAULT 0,
    deferred_amount     NUMERIC(14,2) NOT NULL,
    status              VARCHAR(20) NOT NULL DEFAULT 'IN_PROGRESS',
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT chk_rs_method CHECK (recognition_method IN
        ('STRAIGHT_LINE','PER_SESSION','IMMEDIATE')),
    CONSTRAINT chk_rs_status CHECK (status IN ('IN_PROGRESS','COMPLETED','TERMINATED')),
    -- BẤT BIẾN CỐT LÕI: đã ghi nhận + chưa ghi nhận == tổng giá trị hợp đồng
    CONSTRAINT chk_rs_invariant CHECK (
        recognized_amount + deferred_amount = total_amount),
    CONSTRAINT chk_rs_units CHECK (recognized_units BETWEEN 0 AND total_units)
);

-- Bút toán ghi nhận doanh thu — APPEND-ONLY, sinh bởi job hàng đêm
CREATE TABLE revenue_recognition_entries (
    id                  BIGSERIAL PRIMARY KEY,
    schedule_id         BIGINT NOT NULL REFERENCES revenue_schedules(id),
    registration_id     BIGINT NOT NULL REFERENCES registrations(id),
    recognition_date    DATE NOT NULL,
    amount              NUMERIC(14,2) NOT NULL,
    units               INTEGER NOT NULL DEFAULT 1,
    revenue_category    VARCHAR(30) NOT NULL,   -- MEMBERSHIP|PT|DAY_PASS|OTHER
    source_type         VARCHAR(30),            -- DAILY_ACCRUAL | PT_SESSION | REVERSAL
    source_id           BIGINT,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT chk_rre_amount CHECK (amount <> 0)   -- cho phép âm = bút toán đảo
);
CREATE INDEX idx_rre_date   ON revenue_recognition_entries(recognition_date);
CREATE INDEX idx_rre_sched  ON revenue_recognition_entries(schedule_id);
-- Mỗi hợp đồng chỉ ghi nhận 1 lần cho 1 ngày (với phương pháp phân bổ theo ngày)
CREATE UNIQUE INDEX uq_rre_daily
    ON revenue_recognition_entries(schedule_id, recognition_date)
    WHERE source_type = 'DAILY_ACCRUAL';

CREATE TRIGGER trg_rre_append_only
    BEFORE UPDATE OR DELETE ON revenue_recognition_entries
    FOR EACH ROW EXECUTE FUNCTION forbid_mutation();

CREATE TABLE expense_categories (
    id              BIGSERIAL PRIMARY KEY,
    code            VARCHAR(30) NOT NULL UNIQUE,
    name            VARCHAR(120) NOT NULL,
    parent_id       BIGINT REFERENCES expense_categories(id),
    is_fixed_cost   BOOLEAN NOT NULL DEFAULT TRUE,
    display_order   INTEGER NOT NULL DEFAULT 0
);

CREATE TABLE expenses (
    id                BIGSERIAL PRIMARY KEY,
    category_id       BIGINT NOT NULL REFERENCES expense_categories(id),
    description       VARCHAR(255) NOT NULL,
    amount            NUMERIC(14,2) NOT NULL,
    expense_date      DATE NOT NULL,
    -- Chi phí có kỳ (thuê mặt bằng quý, bảo hiểm năm) → phân bổ theo tháng
    period_start      DATE,
    period_end        DATE,
    allocation_method VARCHAR(20) NOT NULL DEFAULT 'IMMEDIATE',
    vendor            VARCHAR(150),
    invoice_ref       VARCHAR(80),
    attachment_url    VARCHAR(500),
    payroll_run_id    BIGINT,               -- FK khai báo sau
    work_order_id     BIGINT REFERENCES maintenance_work_orders(id),
    equipment_item_id BIGINT REFERENCES equipment_items(id),  -- cho khấu hao
    status            VARCHAR(20) NOT NULL DEFAULT 'RECORDED',
    created_by        BIGINT REFERENCES users(id),
    approved_by       BIGINT REFERENCES users(id),
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT chk_exp_amount CHECK (amount >= 0),
    CONSTRAINT chk_exp_alloc  CHECK (allocation_method IN
        ('IMMEDIATE','STRAIGHT_LINE','DEPRECIATION')),
    CONSTRAINT chk_exp_status CHECK (status IN ('DRAFT','RECORDED','APPROVED','VOID')),
    CONSTRAINT chk_exp_period CHECK (period_end IS NULL OR period_start IS NULL
                                     OR period_end >= period_start)
);
CREATE INDEX idx_exp_date   ON expenses(expense_date);
CREATE INDEX idx_exp_period ON expenses(period_start, period_end);
CREATE INDEX idx_exp_cat    ON expenses(category_id, expense_date);

ALTER TABLE maintenance_work_orders ADD CONSTRAINT fk_wo_expense
    FOREIGN KEY (expense_id) REFERENCES expenses(id);

-- BẢNG ĐƠN GIÁ CÔNG — trả lời yêu cầu "buổi hỗ trợ miễn phí vẫn tính công"
CREATE TABLE payroll_rate_cards (
    id              BIGSERIAL PRIMARY KEY,
    trainer_level   VARCHAR(20),           -- NULL = áp dụng mọi cấp
    session_type    VARCHAR(20) NOT NULL,
    rate_per_session NUMERIC(14,2) NOT NULL,
    valid_from      DATE NOT NULL,
    valid_to        DATE,
    created_by      BIGINT REFERENCES users(id),
    CONSTRAINT chk_prc_rate CHECK (rate_per_session >= 0),
    CONSTRAINT chk_prc_type CHECK (session_type IN
        ('PAID_PT','COMPLIMENTARY','TRIAL','ORIENTATION','MAKEUP','ASSESSMENT','CLASS'))
);
-- Ví dụ dữ liệu: PAID_PT = 120.000đ/buổi, COMPLIMENTARY = 60.000đ/buổi,
--                TRIAL = 80.000đ/buổi  → PT hỗ trợ miễn phí VẪN được trả công

CREATE TABLE commission_rules (
    id                  BIGSERIAL PRIMARY KEY,
    role                VARCHAR(20) NOT NULL,       -- SALE | TRAINER
    tier_from_amount    NUMERIC(14,2) NOT NULL DEFAULT 0,
    tier_to_amount      NUMERIC(14,2),
    commission_percent  NUMERIC(5,2) NOT NULL,
    valid_from          DATE NOT NULL,
    valid_to            DATE,
    CONSTRAINT chk_cr_pct CHECK (commission_percent BETWEEN 0 AND 100)
);

CREATE TABLE payroll_runs (
    id              BIGSERIAL PRIMARY KEY,
    period_year     SMALLINT NOT NULL,
    period_month    SMALLINT NOT NULL,
    status          VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    total_amount    NUMERIC(14,2) NOT NULL DEFAULT 0,
    calculated_at   TIMESTAMPTZ,
    approved_by     BIGINT REFERENCES users(id),
    approved_at     TIMESTAMPTZ,
    paid_at         TIMESTAMPTZ,
    note            TEXT,

    CONSTRAINT chk_pr_status CHECK (status IN ('DRAFT','REVIEW','APPROVED','PAID','VOID')),
    CONSTRAINT chk_pr_month  CHECK (period_month BETWEEN 1 AND 12),
    UNIQUE (period_year, period_month)      -- mỗi tháng chỉ 1 kỳ lương
);
ALTER TABLE expenses ADD CONSTRAINT fk_exp_payroll
    FOREIGN KEY (payroll_run_id) REFERENCES payroll_runs(id);

-- CHI TIẾT TỪNG DÒNG LƯƠNG — PT/Sale xem được trên app → minh bạch, giảm tranh chấp
CREATE TABLE payroll_items (
    id              BIGSERIAL PRIMARY KEY,
    payroll_run_id  BIGINT NOT NULL REFERENCES payroll_runs(id) ON DELETE CASCADE,
    person_id       BIGINT NOT NULL REFERENCES persons(id),
    trainer_id      BIGINT REFERENCES trainers(id),
    staff_id        BIGINT REFERENCES staff(id),

    item_type       VARCHAR(30) NOT NULL,
    description     VARCHAR(255) NOT NULL,
    quantity        NUMERIC(10,2) NOT NULL DEFAULT 1,
    unit_amount     NUMERIC(14,2) NOT NULL,
    amount          NUMERIC(14,2) NOT NULL,
    source_type     VARCHAR(30),        -- PT_SESSION|REGISTRATION|MANUAL|SYSTEM
    source_id       BIGINT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT chk_pi_type CHECK (item_type IN
        ('BASE_SALARY','SESSION_FEE','SALES_COMMISSION','KPI_BONUS',
         'ALLOWANCE','DEDUCTION','PENALTY','ADJUSTMENT'))
);
CREATE INDEX idx_pi_run    ON payroll_items(payroll_run_id);
CREATE INDEX idx_pi_person ON payroll_items(person_id);
-- Một buổi tập chỉ sinh công đúng MỘT lần
CREATE UNIQUE INDEX uq_pi_session_once
    ON payroll_items(source_type, source_id)
    WHERE item_type = 'SESSION_FEE' AND source_type = 'PT_SESSION';

CREATE TABLE accounting_periods (
    id           BIGSERIAL PRIMARY KEY,
    period_year  SMALLINT NOT NULL,
    period_month SMALLINT NOT NULL,
    status       VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    closed_by    BIGINT REFERENCES users(id),
    closed_at    TIMESTAMPTZ,
    CONSTRAINT chk_ap_status CHECK (status IN ('OPEN','CLOSING','CLOSED')),
    UNIQUE (period_year, period_month)      -- mỗi tháng chỉ 1 kỳ kế toán
);

CREATE TABLE financial_reports (
    id            BIGSERIAL PRIMARY KEY,
    report_type   VARCHAR(30) NOT NULL,   -- PNL|CASHFLOW|DEFERRED_REVENUE|SALES|PAYROLL
    period_from   DATE NOT NULL,
    period_to     DATE NOT NULL,
    data          JSONB NOT NULL,         -- số liệu đã tổng hợp
    narrative     TEXT,                   -- phần diễn giải (có thể do LLM sinh)
    narrative_source VARCHAR(20),         -- HUMAN | LLM — minh bạch nguồn gốc
    file_url      VARCHAR(500),           -- PDF/Excel đã xuất
    generated_by  BIGINT REFERENCES users(id),
    generated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_fr_period CHECK (period_to >= period_from)
);


-- =====================================================================
-- M2. CRM & SALES
-- =====================================================================

CREATE TABLE leads (
    id              BIGSERIAL PRIMARY KEY,
    full_name       VARCHAR(150) NOT NULL,
    phone           VARCHAR(20) NOT NULL,
    email           VARCHAR(150),
    source          VARCHAR(30) NOT NULL,   -- FB_ADS|HOTLINE|WALK_IN|REFERRAL|GOOGLE|EVENT
    interest        VARCHAR(60),
    assigned_to     BIGINT REFERENCES staff(id),
    stage           VARCHAR(20) NOT NULL DEFAULT 'NEW',
    lost_reason     VARCHAR(30),            -- PRICE|LOCATION|COMPETITOR|NOT_READY|NO_RESPONSE
    converted_member_id BIGINT REFERENCES members(id),
    next_follow_up  DATE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT chk_lead_stage CHECK (stage IN
        ('NEW','CONTACTED','TRIAL_BOOKED','TRIAL_DONE','NEGOTIATING','WON','LOST'))
);
CREATE INDEX idx_lead_assigned ON leads(assigned_to, stage);
CREATE INDEX idx_lead_followup ON leads(next_follow_up)
    WHERE stage NOT IN ('WON','LOST');

CREATE TABLE lead_activities (
    id          BIGSERIAL PRIMARY KEY,
    lead_id     BIGINT NOT NULL REFERENCES leads(id) ON DELETE CASCADE,
    activity_type VARCHAR(20) NOT NULL,   -- CALL|SMS|ZALO|EMAIL|MEETING|TRIAL
    outcome     VARCHAR(30),
    note        TEXT,
    performed_by BIGINT REFERENCES users(id),
    performed_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE quotes (
    id                  BIGSERIAL PRIMARY KEY,
    quote_no            VARCHAR(30) NOT NULL UNIQUE,
    lead_id             BIGINT REFERENCES leads(id),
    member_id           BIGINT REFERENCES members(id),
    created_by          BIGINT NOT NULL REFERENCES staff(id),
    subtotal            NUMERIC(14,2) NOT NULL DEFAULT 0,
    discount_amount     NUMERIC(14,2) NOT NULL DEFAULT 0,
    discount_percent    NUMERIC(5,2)  NOT NULL DEFAULT 0,
    total_amount        NUMERIC(14,2) NOT NULL DEFAULT 0,
    requires_approval   BOOLEAN NOT NULL DEFAULT FALSE,
    approved_by         BIGINT REFERENCES users(id),
    approved_at         TIMESTAMPTZ,
    status              VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    valid_until         DATE,
    converted_registration_id BIGINT REFERENCES registrations(id),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT chk_q_status CHECK (status IN
        ('DRAFT','PENDING_APPROVAL','SENT','ACCEPTED','REJECTED','EXPIRED','CONVERTED')),
    -- Vượt hạn mức thì bắt buộc phải có người duyệt trước khi chuyển ACCEPTED
    CONSTRAINT chk_q_approval CHECK (
        NOT requires_approval OR status IN ('DRAFT','PENDING_APPROVAL','REJECTED')
        OR approved_by IS NOT NULL)
);

CREATE TABLE quote_items (
    id            BIGSERIAL PRIMARY KEY,
    quote_id      BIGINT NOT NULL REFERENCES quotes(id) ON DELETE CASCADE,
    membership_id BIGINT NOT NULL REFERENCES memberships(id),
    list_price    NUMERIC(14,2) NOT NULL,
    discount      NUMERIC(14,2) NOT NULL DEFAULT 0,
    final_price   NUMERIC(14,2) GENERATED ALWAYS AS (list_price - discount) STORED,
    promotion_id  BIGINT REFERENCES promotions(id)
);


-- =====================================================================
-- M9. ENGAGEMENT — Phản hồi, Đánh giá, Thông báo, Giữ chân
-- =====================================================================

CREATE TABLE feedbacks (
    id              BIGSERIAL PRIMARY KEY,
    member_id       BIGINT REFERENCES members(id),
    feedback_type   VARCHAR(20) NOT NULL,   -- TRAINER|FACILITY|HYGIENE|SERVICE|GENERAL
    target_trainer_id  BIGINT REFERENCES trainers(id),
    target_equipment_id BIGINT REFERENCES equipment_items(id),
    target_room_id  BIGINT REFERENCES rooms(id),
    rating          SMALLINT,
    title           VARCHAR(200),
    content         TEXT NOT NULL,
    photo_url       VARCHAR(500),
    severity        VARCHAR(10) NOT NULL DEFAULT 'MEDIUM',
    status          VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    assigned_to     BIGINT REFERENCES users(id),
    resolution      TEXT,
    resolved_at     TIMESTAMPTZ,
    sla_due_at      TIMESTAMPTZ,
    is_anonymous    BOOLEAN NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT chk_fb_type   CHECK (feedback_type IN
        ('TRAINER','FACILITY','HYGIENE','SERVICE','GENERAL')),
    CONSTRAINT chk_fb_status CHECK (status IN
        ('OPEN','IN_PROGRESS','RESOLVED','CLOSED','REJECTED')),
    CONSTRAINT chk_fb_rating CHECK (rating IS NULL OR rating BETWEEN 1 AND 5)
);
CREATE INDEX idx_fb_open ON feedbacks(created_at)
    WHERE status IN ('OPEN','IN_PROGRESS');
CREATE INDEX idx_fb_trainer ON feedbacks(target_trainer_id);

ALTER TABLE maintenance_work_orders ADD CONSTRAINT fk_wo_feedback
    FOREIGN KEY (feedback_id) REFERENCES feedbacks(id);

CREATE TABLE trainer_ratings (
    id              BIGSERIAL PRIMARY KEY,
    trainer_id      BIGINT NOT NULL REFERENCES trainers(id) ON DELETE CASCADE,
    member_id       BIGINT NOT NULL REFERENCES members(id),
    pt_session_id   BIGINT REFERENCES pt_sessions(id),
    rating          SMALLINT NOT NULL,
    comment         TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_tr_rating CHECK (rating BETWEEN 1 AND 5),
    UNIQUE (pt_session_id, member_id)     -- mỗi buổi đánh giá 1 lần
);

CREATE TABLE notification_templates (
    id          BIGSERIAL PRIMARY KEY,
    code        VARCHAR(50) NOT NULL UNIQUE,
    channel     VARCHAR(20) NOT NULL,   -- PUSH|EMAIL|SMS|IN_APP
    title_tpl   VARCHAR(200) NOT NULL,
    body_tpl    TEXT NOT NULL,
    is_active   BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE notifications (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    template_id BIGINT REFERENCES notification_templates(id),
    channel     VARCHAR(20) NOT NULL,
    title       VARCHAR(200) NOT NULL,
    body        TEXT NOT NULL,
    deep_link   VARCHAR(255),
    status      VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    read_at     TIMESTAMPTZ,
    sent_at     TIMESTAMPTZ,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_notif_status CHECK (status IN ('PENDING','SENT','FAILED','READ'))
);
CREATE INDEX idx_notif_user ON notifications(user_id, created_at DESC);

CREATE TABLE churn_scores (
    id              BIGSERIAL PRIMARY KEY,
    member_id       BIGINT NOT NULL REFERENCES members(id) ON DELETE CASCADE,
    scored_date     DATE NOT NULL,
    score           NUMERIC(5,4) NOT NULL,     -- 0..1
    risk_level      VARCHAR(10) NOT NULL,
    factors         JSONB NOT NULL,            -- đóng góp của từng yếu tố → GIẢI THÍCH ĐƯỢC
    model_version   VARCHAR(20) NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_cs_level CHECK (risk_level IN ('LOW','MEDIUM','HIGH')),
    CONSTRAINT chk_cs_score CHECK (score BETWEEN 0 AND 1),
    UNIQUE (member_id, scored_date)
);
CREATE INDEX idx_cs_high ON churn_scores(scored_date, risk_level)
    WHERE risk_level = 'HIGH';

CREATE TABLE retention_tasks (
    id              BIGSERIAL PRIMARY KEY,
    member_id       BIGINT NOT NULL REFERENCES members(id) ON DELETE CASCADE,
    churn_score_id  BIGINT REFERENCES churn_scores(id),
    task_type       VARCHAR(30) NOT NULL,   -- CHECK_IN_CALL|RENEWAL_OFFER|PT_FOLLOWUP
    assigned_to     BIGINT REFERENCES users(id),
    priority        VARCHAR(10) NOT NULL DEFAULT 'MEDIUM',
    suggested_action TEXT,
    status          VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    outcome         VARCHAR(30),   -- CONTACTED|NO_ANSWER|RENEWED|REFUSED|WRONG_NUMBER
    outcome_note    TEXT,
    -- Nhóm đối chứng để đo hiệu quả can thiệp (thí nghiệm định lượng)
    is_control_group BOOLEAN NOT NULL DEFAULT FALSE,
    due_date        DATE,
    completed_at    TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_rt_status CHECK (status IN ('OPEN','IN_PROGRESS','DONE','SKIPPED'))
);
CREATE INDEX idx_rt_assignee ON retention_tasks(assigned_to, status);


-- =====================================================================
-- SYSTEM — Cấu hình, Outbox, Theo dõi chi phí LLM
-- =====================================================================

CREATE TABLE system_settings (
    key           VARCHAR(80) PRIMARY KEY,
    value         JSONB NOT NULL,
    description   VARCHAR(255),
    updated_by    BIGINT REFERENCES users(id),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);
-- Ví dụ các key: freeze.max_days_per_year, freeze.min_advance_days,
--   checkin.antipassback_minutes, booking.late_cancel_hours,
--   churn.weights, llm.monthly_budget_usd, llm.max_chat_per_member_per_day

-- Outbox pattern: ghi DB và phát sự kiện trong CÙNG transaction
CREATE TABLE outbox_events (
    id            BIGSERIAL PRIMARY KEY,
    aggregate_type VARCHAR(60) NOT NULL,
    aggregate_id  BIGINT NOT NULL,
    event_type    VARCHAR(80) NOT NULL,
    payload       JSONB NOT NULL,
    status        VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    attempts      SMALLINT NOT NULL DEFAULT 0,
    last_error    TEXT,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    published_at  TIMESTAMPTZ,
    CONSTRAINT chk_ob_status CHECK (status IN ('PENDING','PUBLISHED','FAILED','DEAD'))
);
CREATE INDEX idx_outbox_pending ON outbox_events(created_at) WHERE status = 'PENDING';

-- Theo dõi chi phí LLM theo tính năng — cơ sở cho dashboard chi phí AI
CREATE TABLE llm_usage_logs (
    id                  BIGSERIAL PRIMARY KEY,
    feature             VARCHAR(40) NOT NULL,   -- CHATBOT|WORKOUT_DRAFT|INBODY_OCR|
                                                -- FEEDBACK_SUMMARY|REPORT_NARRATIVE
    model               VARCHAR(40) NOT NULL,
    input_tokens        INTEGER NOT NULL DEFAULT 0,
    output_tokens       INTEGER NOT NULL DEFAULT 0,
    cache_read_tokens   INTEGER NOT NULL DEFAULT 0,
    cache_write_tokens  INTEGER NOT NULL DEFAULT 0,
    is_batch            BOOLEAN NOT NULL DEFAULT FALSE,
    cost_usd            NUMERIC(10,6) NOT NULL DEFAULT 0,
    latency_ms          INTEGER,
    user_id             BIGINT REFERENCES users(id),
    success             BOOLEAN NOT NULL DEFAULT TRUE,
    error_message       TEXT,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_llm_feature_date ON llm_usage_logs(feature, created_at);
CREATE INDEX idx_llm_user_date    ON llm_usage_logs(user_id, created_at);


-- =====================================================================
-- VIEW HỖ TRỢ BÁO CÁO
-- =====================================================================

-- Số dư buổi tập hiện tại của từng hợp đồng (đọc từ sổ cái)
CREATE OR REPLACE VIEW v_registration_credit_balance AS
SELECT DISTINCT ON (registration_id)
       registration_id,
       balance_after AS current_balance,
       created_at    AS last_entry_at
FROM   session_credit_ledger
ORDER BY registration_id, id DESC;

-- Doanh thu chưa thực hiện tại một thời điểm
CREATE OR REPLACE VIEW v_deferred_revenue AS
SELECT SUM(rs.deferred_amount) AS total_deferred,
       COUNT(*)                AS contract_count
FROM   revenue_schedules rs
WHERE  rs.status = 'IN_PROGRESS';

-- Hội viên đang có mặt trong phòng tập
CREATE OR REPLACE VIEW v_members_inside AS
SELECT c.member_id, p.full_name, p.photo_url, c.checked_in_at
FROM   check_ins c
JOIN   members m ON m.id = c.member_id
JOIN   persons p ON p.id = m.person_id
WHERE  c.checked_out_at IS NULL
  AND  c.result IN ('ALLOWED','ALLOWED_OVERRIDE')
  AND  c.checked_in_at > now() - INTERVAL '12 hours';


-- =====================================================================
-- TRUY VẤN KIỂM TRA BẤT BIẾN (dùng trong test & job giám sát hàng đêm)
-- Cả ba truy vấn dưới đây PHẢI luôn trả về 0 dòng.
-- =====================================================================

-- BẤT BIẾN 1: Tổng delta của sổ cái == balance_after của bút toán mới nhất
--   SELECT registration_id FROM (
--     SELECT registration_id, SUM(delta) AS total,
--            (array_agg(balance_after ORDER BY id DESC))[1] AS last_balance
--     FROM session_credit_ledger GROUP BY registration_id) t
--   WHERE total <> last_balance;

-- BẤT BIẾN 2: Doanh thu đã ghi nhận + chưa ghi nhận == giá trị hợp đồng
--   SELECT rs.id FROM revenue_schedules rs
--   JOIN registrations r ON r.id = rs.registration_id
--   WHERE rs.recognized_amount + rs.deferred_amount <> r.final_price;

-- BẤT BIẾN 3: Tổng bút toán ghi nhận == recognized_amount trên schedule
--   SELECT rs.id FROM revenue_schedules rs
--   LEFT JOIN (SELECT schedule_id, SUM(amount) s
--              FROM revenue_recognition_entries GROUP BY schedule_id) e
--          ON e.schedule_id = rs.id
--   WHERE COALESCE(e.s, 0) <> rs.recognized_amount;
