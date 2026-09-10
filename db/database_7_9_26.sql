    -- =============================================================================
    -- SCHEMA HIỆN TRẠNG — 23 BẢNG ĐANG THỰC SỰ CHẠY + 4 BẢNG NHÓM G ĐỀ XUẤT (07/09/2026)
    --
    -- File này là bản GỘP trạng thái cuối cùng của 19 migration Flyway đã áp dụng
    -- (V1__identity.sql -> V19__system_settings.sql), dùng để ĐỌC/THAM CHIẾU toàn
    -- bộ schema trong 1 file duy nhất. KHÔNG dùng file này để chạy Flyway — Flyway
    -- vẫn chỉ chạy từ backend/src/main/resources/db/migration/V*.sql, nơi này là
    -- nguồn sự thật duy nhất (application.yml: hibernate.ddl-auto=validate).
    --
    -- Đối chiếu chi tiết cột / lý do thiết kế: db/database_7_9_26.md
    --
    -- 23 bảng đầu (NHÓM 1-9, 11-12) ĐANG CHẠY THẬT — khớp Flyway V1-V19.
    -- 4 bảng cuối (NHÓM 10 — exercises, workout_plans, workout_plan_items,
    -- body_metrics) là ĐỀ XUẤT, CHƯA CÓ MIGRATION — xem cảnh báo ở đầu nhóm đó.
    -- =============================================================================


    -- =============================================================================
    -- Hàm dùng chung: tự cập nhật updated_at mỗi lần UPDATE (V1)
    -- =============================================================================
    CREATE OR REPLACE FUNCTION set_updated_at()
    RETURNS TRIGGER AS $$
    BEGIN
        NEW.updated_at = now();
        RETURN NEW;
    END;
    $$ LANGUAGE plpgsql;


    -- =============================================================================
    -- NHÓM 1 — DANH TÍNH
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
        photo_key               VARCHAR(500),
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

    CREATE UNIQUE INDEX uq_persons_phone       ON persons (phone)       WHERE deleted_at IS NULL;
    CREATE UNIQUE INDEX uq_persons_national_id ON persons (national_id) WHERE deleted_at IS NULL AND national_id IS NOT NULL;
    CREATE UNIQUE INDEX uq_persons_email       ON persons (email)       WHERE deleted_at IS NULL AND email IS NOT NULL;
    CREATE UNIQUE INDEX uq_persons_card_uid    ON persons (card_uid)    WHERE deleted_at IS NULL AND card_uid IS NOT NULL;

    CREATE TRIGGER trg_persons_updated_at BEFORE UPDATE ON persons
        FOR EACH ROW EXECUTE FUNCTION set_updated_at();


    CREATE TABLE users (
        id                BIGSERIAL PRIMARY KEY,
        person_id         BIGINT       NOT NULL REFERENCES persons(id),
        username          VARCHAR(100) NOT NULL,
        password_hash     VARCHAR(255) NOT NULL,
        primary_role      VARCHAR(20)  NOT NULL,

        status            VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
        locked_reason     VARCHAR(255),
        locked_until      DATE,
        -- locked_by KHÔNG tồn tại — đã DROP ở V2 (đã có audit_logs thay thế)

        failed_attempts   SMALLINT     NOT NULL DEFAULT 0,
        auto_locked_until TIMESTAMPTZ,
        -- token_version thêm ở V17: tăng khi logout thật/đổi mật khẩu để thu hồi
        -- mọi refresh token cũ, thay cho việc phải có bảng refresh_tokens riêng
        token_version     INTEGER      NOT NULL DEFAULT 0,

        email_verified_at TIMESTAMPTZ,
        last_login_at     TIMESTAMPTZ,

        created_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
        updated_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
        deleted_at        TIMESTAMPTZ,

        CONSTRAINT chk_users_role   CHECK (primary_role IN ('ADMIN','MEMBER','TRAINER','SALE','RECEPTIONIST','ACCOUNTANT')),
        CONSTRAINT chk_users_status CHECK (status IN ('ACTIVE','LOCKED')),
        CONSTRAINT chk_users_locked CHECK (status <> 'LOCKED' OR locked_reason IS NOT NULL)
    );

    CREATE UNIQUE INDEX uq_users_username ON users (username) WHERE deleted_at IS NULL;
    CREATE INDEX idx_users_person         ON users (person_id);

    CREATE TRIGGER trg_users_updated_at BEFORE UPDATE ON users
        FOR EACH ROW EXECUTE FUNCTION set_updated_at();


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

        CONSTRAINT chk_emp_department CHECK (department IN ('TRAINING','SALES','FRONT_DESK','ACCOUNTING')),
        CONSTRAINT chk_emp_status     CHECK (status IN ('ACTIVE','ON_LEAVE','RESIGNED')),
        CONSTRAINT chk_emp_type       CHECK (employment_type IS NULL OR employment_type IN ('FULL_TIME','PART_TIME','FREELANCE')),
        CONSTRAINT chk_emp_level      CHECK (level IS NULL OR level IN ('JUNIOR','SENIOR','MASTER')),
        CONSTRAINT chk_emp_dates      CHECK (end_date IS NULL OR end_date >= start_date),
        CONSTRAINT chk_emp_rating     CHECK (rating_avg IS NULL OR rating_avg BETWEEN 1.00 AND 5.00),
        CONSTRAINT chk_emp_level_dept CHECK (level IS NULL OR department = 'TRAINING')
    );

    CREATE UNIQUE INDEX uq_employees_person ON employees (person_id)     WHERE deleted_at IS NULL;
    CREATE UNIQUE INDEX uq_employees_code   ON employees (employee_code) WHERE deleted_at IS NULL;
    CREATE INDEX idx_employees_department   ON employees (department) WHERE deleted_at IS NULL;

    CREATE TRIGGER trg_employees_updated_at BEFORE UPDATE ON employees
        FOR EACH ROW EXECUTE FUNCTION set_updated_at();


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


    -- =============================================================================
    -- NHÓM 2 — GÓI TẬP & HỢP ĐỒNG
    -- =============================================================================

    CREATE TABLE memberships (
        id               BIGSERIAL PRIMARY KEY,
        code             VARCHAR(30)   NOT NULL,
        name             VARCHAR(150)  NOT NULL,
        package_type     VARCHAR(20)   NOT NULL,
        duration_days    INTEGER,
        session_count    INTEGER,
        price            NUMERIC(14,2) NOT NULL,
        includes_trainer BOOLEAN       NOT NULL DEFAULT FALSE,
        pt_value_ratio   NUMERIC(4,3),
        area_codes       JSONB,
        max_freeze_days  INTEGER       NOT NULL DEFAULT 0,
        is_refundable    BOOLEAN       NOT NULL DEFAULT FALSE,
        description      TEXT,
        display_order    INTEGER       NOT NULL DEFAULT 0,
        status           VARCHAR(20)   NOT NULL DEFAULT 'ACTIVE',

        created_at       TIMESTAMPTZ   NOT NULL DEFAULT now(),
        updated_at       TIMESTAMPTZ   NOT NULL DEFAULT now(),
        deleted_at       TIMESTAMPTZ,

        CONSTRAINT chk_ms_type   CHECK (package_type IN ('TIME_BASED','SESSION_BASED','HYBRID','DAY_PASS')),
        CONSTRAINT chk_ms_status CHECK (status IN ('ACTIVE','ARCHIVED')),
        CONSTRAINT chk_ms_price  CHECK (price >= 0),
        CONSTRAINT chk_ms_freeze CHECK (max_freeze_days >= 0),
        CONSTRAINT chk_ms_pt_ratio CHECK (
            package_type <> 'HYBRID'
            OR (pt_value_ratio IS NOT NULL AND pt_value_ratio > 0 AND pt_value_ratio < 1)
        ),
        CONSTRAINT chk_ms_sessions CHECK (
            package_type NOT IN ('SESSION_BASED','HYBRID')
            OR (session_count IS NOT NULL AND session_count > 0)
        ),
        CONSTRAINT chk_ms_duration CHECK (
            package_type NOT IN ('TIME_BASED','HYBRID','DAY_PASS')
            OR (duration_days IS NOT NULL AND duration_days > 0)
        )
    );

    CREATE UNIQUE INDEX uq_memberships_code ON memberships (code) WHERE deleted_at IS NULL;
    CREATE INDEX idx_memberships_selling ON memberships (display_order)
        WHERE deleted_at IS NULL AND status = 'ACTIVE';

    CREATE TRIGGER trg_memberships_updated_at BEFORE UPDATE ON memberships
        FOR EACH ROW EXECUTE FUNCTION set_updated_at();


    -- registrations: bảng trung tâm. Gánh khuyến mãi (discount_*) và bảo lưu tối
    -- đa 1 lần/hợp đồng (freeze_*, thay cho bảng registration_freezes trong kế
    -- hoạch gốc) + gia hạn nối tiếp (renew_from_id, thêm ở V18).
    CREATE TABLE registrations (
        id                    BIGSERIAL PRIMARY KEY,
        registration_code     VARCHAR(30)   NOT NULL,
        member_id             BIGINT        NOT NULL REFERENCES members(id),
        membership_id         BIGINT        NOT NULL REFERENCES memberships(id),
        sold_by               BIGINT        REFERENCES employees(id),
        assigned_trainer_id   BIGINT        REFERENCES employees(id),

        -- Snapshot thương mại: sao chép LÚC KÝ, không đọc ngược
        package_type          VARCHAR(20)   NOT NULL,
        duration_days         INTEGER,
        sessions_total        INTEGER,
        list_price            NUMERIC(14,2) NOT NULL,
        discount_amount       NUMERIC(14,2) NOT NULL DEFAULT 0,
        discount_reason       VARCHAR(255),
        discount_approved_by  BIGINT        REFERENCES users(id),
        final_price           NUMERIC(14,2) NOT NULL,

        contract_date         DATE          NOT NULL,
        start_date            DATE,
        end_date              DATE,
        activated_at          TIMESTAMPTZ,
        closed_at             TIMESTAMPTZ,

        status                VARCHAR(20)   NOT NULL DEFAULT 'PENDING_PAYMENT',
        close_reason          VARCHAR(255),
        note                  TEXT,

        -- Bảo lưu (tối đa 1 lần/hợp đồng)
        freeze_from_date      DATE,
        freeze_to_date        DATE,
        freeze_days           INTEGER GENERATED ALWAYS AS (freeze_to_date - freeze_from_date + 1) STORED,
        freeze_reason         VARCHAR(255),
        freeze_reason_type    VARCHAR(20),
        freeze_attachment_key VARCHAR(500),
        freeze_requested_by   BIGINT        REFERENCES users(id),
        freeze_approved_by    BIGINT        REFERENCES users(id),
        freeze_status         VARCHAR(20),
        freeze_ended_early_at DATE,

        -- Gia hạn nối tiếp — thêm ở V18
        renew_from_id         BIGINT        REFERENCES registrations(id),

        created_at            TIMESTAMPTZ   NOT NULL DEFAULT now(),
        updated_at            TIMESTAMPTZ   NOT NULL DEFAULT now(),
        deleted_at            TIMESTAMPTZ,

        CONSTRAINT chk_reg_status CHECK (status IN
            ('PENDING_PAYMENT','ACTIVE','FROZEN','COMPLETED','CANCELLED','REFUNDED')),
        CONSTRAINT chk_reg_type CHECK (package_type IN
            ('TIME_BASED','SESSION_BASED','HYBRID','DAY_PASS')),
        CONSTRAINT chk_reg_price   CHECK (final_price = list_price - discount_amount),
        CONSTRAINT chk_reg_amounts CHECK (list_price >= 0 AND discount_amount >= 0 AND final_price >= 0),
        CONSTRAINT chk_reg_discount CHECK (discount_amount = 0 OR discount_reason IS NOT NULL),
        CONSTRAINT chk_reg_dates CHECK (
            end_date IS NULL OR start_date IS NULL OR end_date >= start_date
        ),
        CONSTRAINT chk_freeze_status CHECK (freeze_status IS NULL OR freeze_status IN
            ('PENDING','APPROVED','REJECTED','ACTIVE','ENDED','CANCELLED')),
        CONSTRAINT chk_freeze_reason_type CHECK (freeze_reason_type IS NULL OR freeze_reason_type IN
            ('PERSONAL','MEDICAL','TRAVEL','OTHER')),
        CONSTRAINT chk_freeze_range CHECK (
            freeze_to_date IS NULL OR freeze_from_date IS NULL OR freeze_to_date >= freeze_from_date
        ),
        CONSTRAINT chk_freeze_complete CHECK (
            freeze_status IS NULL
            OR (freeze_from_date IS NOT NULL AND freeze_to_date IS NOT NULL AND freeze_reason IS NOT NULL)
        ),
        CONSTRAINT chk_reg_frozen CHECK (
            status <> 'FROZEN' OR (freeze_status IS NOT NULL AND freeze_status = 'ACTIVE')
        )
    );

    CREATE UNIQUE INDEX uq_reg_code ON registrations (registration_code) WHERE deleted_at IS NULL;
    CREATE INDEX idx_reg_member     ON registrations (member_id, status);
    CREATE INDEX idx_reg_membership ON registrations (membership_id);
    CREATE INDEX idx_reg_sold_by    ON registrations (sold_by) WHERE sold_by IS NOT NULL;
    CREATE INDEX idx_reg_trainer    ON registrations (assigned_trainer_id) WHERE assigned_trainer_id IS NOT NULL;
    CREATE INDEX idx_reg_expiring   ON registrations (end_date) WHERE status = 'ACTIVE';
    CREATE INDEX idx_reg_pending    ON registrations (created_at) WHERE status = 'PENDING_PAYMENT';

    CREATE TRIGGER trg_registrations_updated_at BEFORE UPDATE ON registrations
        FOR EACH ROW EXECUTE FUNCTION set_updated_at();


    CREATE TABLE member_trainers (
        id          BIGSERIAL PRIMARY KEY,
        member_id   BIGINT      NOT NULL REFERENCES members(id),
        trainer_id  BIGINT      NOT NULL REFERENCES employees(id),
        role        VARCHAR(20) NOT NULL DEFAULT 'PRIMARY',
        from_date   DATE        NOT NULL,
        to_date     DATE,
        assigned_by BIGINT      REFERENCES users(id),
        note        VARCHAR(255),

        created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
        updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),

        CONSTRAINT chk_mt_role  CHECK (role IN ('PRIMARY','SECONDARY','SUBSTITUTE')),
        CONSTRAINT chk_mt_dates CHECK (to_date IS NULL OR to_date >= from_date)
    );

    CREATE UNIQUE INDEX uq_mt_primary ON member_trainers (member_id)
        WHERE role = 'PRIMARY' AND to_date IS NULL;
    CREATE INDEX idx_mt_trainer ON member_trainers (trainer_id) WHERE to_date IS NULL;

    CREATE TRIGGER trg_member_trainers_updated_at BEFORE UPDATE ON member_trainers
        FOR EACH ROW EXECUTE FUNCTION set_updated_at();


    -- Chuỗi sinh mã nghiệp vụ — dùng SEQUENCE thay vì đếm dòng để tránh trùng mã
    -- khi hai request chạy song song
    CREATE SEQUENCE member_code_seq       START 1;
    CREATE SEQUENCE registration_code_seq START 1;


    -- =============================================================================
    -- NHÓM 4 — BUỔI TẬP PT & SỔ CÁI TÍN DỤNG BUỔI TẬP
    -- =============================================================================

    CREATE TABLE pt_sessions (
        id                   BIGSERIAL PRIMARY KEY,
        member_id            BIGINT      NOT NULL REFERENCES members(id),
        trainer_id           BIGINT      NOT NULL REFERENCES employees(id),
        registration_id      BIGINT      NOT NULL REFERENCES registrations(id),

        session_type         VARCHAR(20) NOT NULL,
        scheduled_start      TIMESTAMPTZ NOT NULL,
        scheduled_end        TIMESTAMPTZ NOT NULL,
        actual_start         TIMESTAMPTZ,
        actual_end           TIMESTAMPTZ,
        room_name            VARCHAR(120),

        status               VARCHAR(20) NOT NULL DEFAULT 'PENDING_TRAINER',

        requested_by         BIGINT      REFERENCES users(id),
        responded_at         TIMESTAMPTZ,
        reject_reason        VARCHAR(255),

        trainer_confirmed_at TIMESTAMPTZ,
        member_confirmed_at  TIMESTAMPTZ,
        auto_confirmed       BOOLEAN     NOT NULL DEFAULT FALSE,

        no_show_by           VARCHAR(20),

        cancelled_by         VARCHAR(20),
        cancelled_at         TIMESTAMPTZ,
        cancel_reason        VARCHAR(255),
        is_late_cancel       BOOLEAN     NOT NULL DEFAULT FALSE,

        note                 TEXT,

        created_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
        updated_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
        deleted_at           TIMESTAMPTZ,

        CONSTRAINT chk_pts_type CHECK (session_type IN
            ('PAID_PT','COMPLIMENTARY','TRIAL','ORIENTATION','MAKEUP','ASSESSMENT')),
        CONSTRAINT chk_pts_status CHECK (status IN
            ('PENDING_TRAINER','REJECTED','SCHEDULED','COMPLETED','NO_SHOW','CANCELLED')),
        CONSTRAINT chk_pts_time  CHECK (scheduled_end > scheduled_start),
        CONSTRAINT chk_pts_actual CHECK (
            actual_end IS NULL OR actual_start IS NULL OR actual_end >= actual_start
        ),
        CONSTRAINT chk_pts_completed CHECK (
            status <> 'COMPLETED'
            OR (trainer_confirmed_at IS NOT NULL AND member_confirmed_at IS NOT NULL)
        ),
        CONSTRAINT chk_pts_no_show CHECK (
            status <> 'NO_SHOW'
            OR (no_show_by IS NOT NULL AND no_show_by IN ('MEMBER','TRAINER'))
        ),
        CONSTRAINT chk_pts_rejected CHECK (
            status <> 'REJECTED' OR reject_reason IS NOT NULL
        ),
        CONSTRAINT chk_pts_cancelled CHECK (
            status <> 'CANCELLED'
            OR (cancelled_by IS NOT NULL AND cancelled_by IN ('MEMBER','TRAINER','SYSTEM'))
        )
    );

    CREATE INDEX idx_pts_member       ON pt_sessions (member_id, scheduled_start DESC);
    CREATE INDEX idx_pts_trainer      ON pt_sessions (trainer_id, scheduled_start DESC);
    CREATE INDEX idx_pts_registration ON pt_sessions (registration_id);
    CREATE INDEX idx_pts_upcoming     ON pt_sessions (trainer_id, scheduled_start)
        WHERE status IN ('PENDING_TRAINER','SCHEDULED');
    CREATE INDEX idx_pts_pending_member ON pt_sessions (trainer_confirmed_at)
        WHERE status = 'SCHEDULED' AND trainer_confirmed_at IS NOT NULL
        AND member_confirmed_at IS NULL;

    CREATE TRIGGER trg_pt_sessions_updated_at BEFORE UPDATE ON pt_sessions
        FOR EACH ROW EXECUTE FUNCTION set_updated_at();


    -- session_credit_ledger — SỔ CÁI TÍN DỤNG BUỔI TẬP  (đóng góp học thuật chính)
    -- CHỈ GHI THÊM: trigger bên dưới chặn cứng UPDATE/DELETE ở tầng CSDL.
    CREATE TABLE session_credit_ledger (
        id              BIGSERIAL PRIMARY KEY,
        registration_id BIGINT      NOT NULL REFERENCES registrations(id),
        entry_type      VARCHAR(20) NOT NULL,
        delta           INTEGER     NOT NULL,
        balance_after   INTEGER     NOT NULL,
        source_type     VARCHAR(30) NOT NULL,
        source_id       BIGINT,
        reason          VARCHAR(255),
        created_by      BIGINT      REFERENCES users(id),
        created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),

        CONSTRAINT chk_scl_type CHECK (entry_type IN
            ('GRANT','CONSUME','REFUND','EXPIRE','ADJUST')),
        CONSTRAINT chk_scl_balance CHECK (balance_after >= 0),
        CONSTRAINT chk_scl_delta_sign CHECK (
            (entry_type IN ('GRANT','REFUND')  AND delta > 0) OR
            (entry_type IN ('CONSUME','EXPIRE') AND delta < 0) OR
            (entry_type = 'ADJUST' AND delta <> 0)
        ),
        CONSTRAINT chk_scl_adjust CHECK (
            entry_type <> 'ADJUST' OR (reason IS NOT NULL AND created_by IS NOT NULL)
        )
    );

    CREATE INDEX idx_scl_registration ON session_credit_ledger (registration_id, id);
    CREATE UNIQUE INDEX uq_scl_consume_source ON session_credit_ledger (source_type, source_id)
        WHERE entry_type = 'CONSUME' AND source_id IS NOT NULL;
    CREATE UNIQUE INDEX uq_scl_grant ON session_credit_ledger (registration_id)
        WHERE entry_type = 'GRANT';

    CREATE OR REPLACE FUNCTION so_cai_chi_ghi_them()
    RETURNS TRIGGER AS $$
    BEGIN
        RAISE EXCEPTION
            'So cai buoi tap chi duoc GHI THEM. Khong the % ban ghi id=%. '
            'Muon sua sai thi ghi mot but toan ADJUST moi.',
            TG_OP, OLD.id;
    END;
    $$ LANGUAGE plpgsql;

    CREATE TRIGGER trg_scl_no_update
        BEFORE UPDATE ON session_credit_ledger
        FOR EACH ROW EXECUTE FUNCTION so_cai_chi_ghi_them();

    CREATE TRIGGER trg_scl_no_delete
        BEFORE DELETE ON session_credit_ledger
        FOR EACH ROW EXECUTE FUNCTION so_cai_chi_ghi_them();


    -- =============================================================================
    -- NHÓM 3 — CHECK-IN
    -- =============================================================================

    -- check_ins: Hướng A đã chốt — hội viên hiện QR động, lễ tân quét bằng máy
    -- quầy, hiện lại ảnh hồ sơ có sẵn (persons.photo_key). result đã mở rộng lên
    -- VARCHAR(30) (V16) để chứa giá trị DENIED_ALREADY_INSIDE thêm ở V15.
    CREATE TABLE check_ins (
        id                  BIGSERIAL PRIMARY KEY,
        member_id           BIGINT      NOT NULL REFERENCES members(id),
        registration_id     BIGINT      REFERENCES registrations(id),

        checked_in_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
        checked_out_at      TIMESTAMPTZ,
        auto_closed         BOOLEAN     NOT NULL DEFAULT FALSE,

        method               VARCHAR(20) NOT NULL,
        result               VARCHAR(30) NOT NULL,

        verified_by         BIGINT      REFERENCES users(id),

        incident_type       VARCHAR(30),
        incident_note       TEXT,
        incident_handled_by BIGINT      REFERENCES users(id),

        created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
        updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),

        CONSTRAINT chk_ci_method CHECK (method IN ('QR_DYNAMIC','DAY_PASS')),
        CONSTRAINT chk_ci_result CHECK (result IN
            ('ALLOWED','ALLOWED_OVERRIDE',
            'DENIED_EXPIRED','DENIED_FROZEN','DENIED_UNPAID',
            'DENIED_NOT_FOUND','DENIED_SUSPECT','DENIED_ALREADY_INSIDE')),
        CONSTRAINT chk_ci_incident CHECK (incident_type IS NULL OR incident_type IN
            ('ANTI_PASSBACK','SUSPECTED_SHARING','REPLAY_ATTEMPT','EXPIRED_ATTEMPT')),
        CONSTRAINT chk_ci_times CHECK (
            checked_out_at IS NULL OR checked_out_at >= checked_in_at
        ),
        CONSTRAINT chk_ci_override CHECK (
            result <> 'ALLOWED_OVERRIDE' OR verified_by IS NOT NULL
        ),
        CONSTRAINT chk_ci_registration CHECK (
            result NOT IN ('ALLOWED','ALLOWED_OVERRIDE')
            OR method = 'DAY_PASS'
            OR registration_id IS NOT NULL
        )
    );

    CREATE INDEX idx_ci_member ON check_ins (member_id, checked_in_at DESC);
    CREATE INDEX idx_ci_time   ON check_ins (checked_in_at DESC);
    CREATE INDEX idx_ci_inside ON check_ins (member_id) WHERE checked_out_at IS NULL;
    CREATE INDEX idx_ci_denied ON check_ins (result, checked_in_at DESC) WHERE result LIKE 'DENIED%';
    CREATE INDEX idx_ci_incident ON check_ins (incident_type, checked_in_at DESC)
        WHERE incident_type IS NOT NULL;

    CREATE TRIGGER trg_check_ins_updated_at BEFORE UPDATE ON check_ins
        FOR EACH ROW EXECUTE FUNCTION set_updated_at();


    -- =============================================================================
    -- NHÓM 7 — THANH TOÁN
    -- =============================================================================

    CREATE TABLE invoices (
        id              BIGSERIAL PRIMARY KEY,
        invoice_no      VARCHAR(30)   NOT NULL,
        member_id       BIGINT        NOT NULL REFERENCES members(id),
        registration_id BIGINT        NOT NULL REFERENCES registrations(id),
        description     VARCHAR(255),

        total_amount    NUMERIC(14,2) NOT NULL,
        paid_amount     NUMERIC(14,2) NOT NULL DEFAULT 0,
        balance_due     NUMERIC(14,2) GENERATED ALWAYS AS (total_amount - paid_amount) STORED,

        status          VARCHAR(20)   NOT NULL DEFAULT 'UNPAID',
        issued_at       TIMESTAMPTZ   NOT NULL DEFAULT now(),
        due_date        DATE,
        paid_at         TIMESTAMPTZ,
        issued_by       BIGINT        REFERENCES users(id),

        created_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),
        updated_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),
        deleted_at      TIMESTAMPTZ,

        CONSTRAINT chk_inv_status CHECK (status IN
            ('UNPAID','PARTIALLY_PAID','PAID','OVERDUE','CANCELLED','REFUNDED')),
        CONSTRAINT chk_inv_amounts CHECK (total_amount >= 0 AND paid_amount >= 0),
        CONSTRAINT chk_inv_paid_at CHECK (status <> 'PAID' OR paid_at IS NOT NULL)
    );

    CREATE UNIQUE INDEX uq_inv_no  ON invoices (invoice_no) WHERE deleted_at IS NULL;
    CREATE INDEX idx_inv_member    ON invoices (member_id, issued_at DESC);
    CREATE INDEX idx_inv_reg       ON invoices (registration_id);
    CREATE INDEX idx_inv_unpaid    ON invoices (due_date)
        WHERE status IN ('UNPAID','PARTIALLY_PAID','OVERDUE');

    CREATE TRIGGER trg_invoices_updated_at BEFORE UPDATE ON invoices
        FOR EACH ROW EXECUTE FUNCTION set_updated_at();


    CREATE TABLE cash_shifts (
        id                BIGSERIAL PRIMARY KEY,
        employee_id       BIGINT        NOT NULL REFERENCES employees(id),

        opened_at         TIMESTAMPTZ   NOT NULL DEFAULT now(),
        closed_at         TIMESTAMPTZ,

        opening_balance   NUMERIC(14,2) NOT NULL DEFAULT 0,
        counted_cash      NUMERIC(14,2),
        expected_cash     NUMERIC(14,2),
        difference        NUMERIC(14,2) GENERATED ALWAYS AS (counted_cash - expected_cash) STORED,
        difference_reason VARCHAR(255),

        status            VARCHAR(20)   NOT NULL DEFAULT 'OPEN',
        verified_by       BIGINT        REFERENCES users(id),
        note              TEXT,

        created_at        TIMESTAMPTZ   NOT NULL DEFAULT now(),
        updated_at        TIMESTAMPTZ   NOT NULL DEFAULT now(),

        CONSTRAINT chk_cs_status CHECK (status IN ('OPEN','CLOSED','DISCREPANCY')),
        CONSTRAINT chk_cs_closed CHECK (
            status = 'OPEN'
            OR (closed_at IS NOT NULL AND counted_cash IS NOT NULL AND expected_cash IS NOT NULL)
        ),
        CONSTRAINT chk_cs_discrepancy CHECK (
            status <> 'DISCREPANCY' OR difference_reason IS NOT NULL
        )
    );

    CREATE UNIQUE INDEX uq_cs_open_per_employee ON cash_shifts (employee_id) WHERE status = 'OPEN';
    CREATE INDEX idx_cs_employee ON cash_shifts (employee_id, opened_at DESC);

    CREATE TRIGGER trg_cash_shifts_updated_at BEFORE UPDATE ON cash_shifts
        FOR EACH ROW EXECUTE FUNCTION set_updated_at();


    CREATE TABLE payments (
        id                    BIGSERIAL PRIMARY KEY,
        payment_no            VARCHAR(30)   NOT NULL,
        member_id             BIGINT        NOT NULL REFERENCES members(id),
        invoice_id            BIGINT        NOT NULL REFERENCES invoices(id),
        cash_shift_id         BIGINT        REFERENCES cash_shifts(id),

        payment_type          VARCHAR(20)   NOT NULL DEFAULT 'PAYMENT',
        method                VARCHAR(20)   NOT NULL,
        amount                NUMERIC(14,2) NOT NULL,
        status                VARCHAR(20)   NOT NULL DEFAULT 'INITIATED',

        provider              VARCHAR(30),
        provider_txn_id       VARCHAR(100),
        transfer_content      VARCHAR(255),
        bank_account          VARCHAR(50),
        raw_payload           JSONB,

        refund_of_payment_id  BIGINT        REFERENCES payments(id),
        refund_reason         VARCHAR(255),
        refund_penalty        NUMERIC(14,2),
        approved_by           BIGINT        REFERENCES users(id),

        received_by           BIGINT        REFERENCES users(id),
        paid_at               TIMESTAMPTZ,
        reconciled_at         TIMESTAMPTZ,

        created_at            TIMESTAMPTZ   NOT NULL DEFAULT now(),
        updated_at            TIMESTAMPTZ   NOT NULL DEFAULT now(),

        CONSTRAINT chk_pay_type   CHECK (payment_type IN ('PAYMENT','REFUND')),
        CONSTRAINT chk_pay_method CHECK (method IN
            ('CASH','BANK_TRANSFER','VIETQR','CARD_POS','E_WALLET','GATEWAY')),
        CONSTRAINT chk_pay_status CHECK (status IN
            ('INITIATED','PENDING','SUCCEEDED','FAILED','EXPIRED')),
        CONSTRAINT chk_pay_amount_sign CHECK (
            (payment_type = 'PAYMENT' AND amount > 0) OR
            (payment_type = 'REFUND'  AND amount < 0)
        ),
        CONSTRAINT chk_pay_refund CHECK (
            payment_type <> 'REFUND'
            OR (refund_of_payment_id IS NOT NULL AND refund_reason IS NOT NULL
                AND approved_by IS NOT NULL)
        ),
        CONSTRAINT chk_pay_cash_shift CHECK (
            method <> 'CASH' OR status <> 'SUCCEEDED' OR cash_shift_id IS NOT NULL
        ),
        CONSTRAINT chk_pay_succeeded CHECK (status <> 'SUCCEEDED' OR paid_at IS NOT NULL)
    );

    CREATE UNIQUE INDEX uq_pay_no ON payments (payment_no);
    CREATE UNIQUE INDEX uq_pay_provider_txn ON payments (provider, provider_txn_id)
        WHERE provider_txn_id IS NOT NULL;
    CREATE INDEX idx_pay_invoice ON payments (invoice_id);
    CREATE INDEX idx_pay_member  ON payments (member_id, paid_at DESC);
    CREATE INDEX idx_pay_shift   ON payments (cash_shift_id) WHERE cash_shift_id IS NOT NULL;

    CREATE TRIGGER trg_payments_updated_at BEFORE UPDATE ON payments
        FOR EACH ROW EXECUTE FUNCTION set_updated_at();


    -- Chuỗi sinh mã hóa đơn / khoản thu
    CREATE SEQUENCE invoice_no_seq START 1;
    CREATE SEQUENCE payment_no_seq START 1;


    -- =============================================================================
    -- NHÓM 8 — TÀI CHÍNH & LƯƠNG
    -- =============================================================================

    CREATE SEQUENCE expense_no_seq   START 1;
    CREATE SEQUENCE payroll_code_seq START 1;

    -- revenue_schedules — ghi nhận doanh thu dồn tích (đóng góp học thuật thứ hai)
    -- Gánh luôn vai trò của revenue_recognition_entries trong kế hoạch gốc.
    CREATE TABLE revenue_schedules (
        id                 BIGSERIAL PRIMARY KEY,
        registration_id    BIGINT        NOT NULL REFERENCES registrations(id),
        invoice_id         BIGINT        REFERENCES invoices(id),
        schedule_date      DATE          NOT NULL,
        amount             NUMERIC(14,2) NOT NULL,
        status             VARCHAR(20)   NOT NULL DEFAULT 'PENDING',
        recognition_method VARCHAR(20)   NOT NULL DEFAULT 'STRAIGHT_LINE',
        recognized_at      TIMESTAMPTZ,
        note               VARCHAR(255),

        created_at         TIMESTAMPTZ   NOT NULL DEFAULT now(),
        updated_at         TIMESTAMPTZ   NOT NULL DEFAULT now(),

        CONSTRAINT chk_rs_status CHECK (status IN ('PENDING','RECOGNIZED','REVERSED')),
        CONSTRAINT chk_rs_method CHECK (recognition_method IN ('STRAIGHT_LINE','USAGE_BASED')),
        CONSTRAINT chk_rs_amount CHECK (amount >= 0)
    );

    CREATE INDEX idx_rs_registration ON revenue_schedules (registration_id);
    CREATE INDEX idx_rs_schedule_date ON revenue_schedules (schedule_date);
    CREATE INDEX idx_rs_pending ON revenue_schedules (schedule_date) WHERE status = 'PENDING';

    CREATE TRIGGER trg_revenue_schedules_updated_at BEFORE UPDATE ON revenue_schedules
        FOR EACH ROW EXECUTE FUNCTION set_updated_at();


    CREATE TABLE payroll_runs (
        id                 BIGSERIAL PRIMARY KEY,
        payroll_code       VARCHAR(30)   NOT NULL,
        period_month       INTEGER       NOT NULL,
        period_year        INTEGER       NOT NULL,

        total_base_salary  NUMERIC(14,2) NOT NULL DEFAULT 0,
        total_commission   NUMERIC(14,2) NOT NULL DEFAULT 0,
        total_bonus        NUMERIC(14,2) NOT NULL DEFAULT 0,
        total_deduction    NUMERIC(14,2) NOT NULL DEFAULT 0,
        total_net_salary   NUMERIC(14,2) NOT NULL DEFAULT 0,

        status             VARCHAR(20)   NOT NULL DEFAULT 'DRAFT',
        created_by         BIGINT        REFERENCES users(id),
        approved_by        BIGINT        REFERENCES users(id),
        approved_at        TIMESTAMPTZ,
        paid_at            TIMESTAMPTZ,
        -- paid_by thêm ở V11: ai bấm nút xác nhận chi lương
        paid_by            BIGINT        REFERENCES users(id),
        note               TEXT,

        created_at         TIMESTAMPTZ   NOT NULL DEFAULT now(),
        updated_at         TIMESTAMPTZ   NOT NULL DEFAULT now(),

        CONSTRAINT chk_pr_status CHECK (status IN ('DRAFT','APPROVED','PAID','CANCELLED')),
        CONSTRAINT chk_pr_period CHECK (period_month BETWEEN 1 AND 12 AND period_year >= 2020)
    );

    CREATE UNIQUE INDEX uq_pr_code ON payroll_runs (payroll_code);
    CREATE UNIQUE INDEX uq_pr_period ON payroll_runs (period_month, period_year)
        WHERE status <> 'CANCELLED';

    CREATE TRIGGER trg_payroll_runs_updated_at BEFORE UPDATE ON payroll_runs
        FOR EACH ROW EXECUTE FUNCTION set_updated_at();


    CREATE TABLE payroll_items (
        id                    BIGSERIAL PRIMARY KEY,
        payroll_run_id        BIGINT        NOT NULL REFERENCES payroll_runs(id) ON DELETE CASCADE,
        employee_id           BIGINT        NOT NULL REFERENCES employees(id),

        base_salary           NUMERIC(14,2) NOT NULL DEFAULT 0,
        pt_sessions_count     INTEGER       NOT NULL DEFAULT 0,
        pt_commission         NUMERIC(14,2) NOT NULL DEFAULT 0,
        sales_contracts_count INTEGER       NOT NULL DEFAULT 0,
        sales_commission      NUMERIC(14,2) NOT NULL DEFAULT 0,
        bonus_amount          NUMERIC(14,2) NOT NULL DEFAULT 0,
        deduction_amount      NUMERIC(14,2) NOT NULL DEFAULT 0,
        net_salary            NUMERIC(14,2) NOT NULL DEFAULT 0,
        -- manually_edited thêm ở V11: cảnh báo trước khi "Tính lương" lại ghi đè
        -- chỉnh sửa tay
        manually_edited       BOOLEAN       NOT NULL DEFAULT FALSE,
        note                  TEXT,

        created_at            TIMESTAMPTZ   NOT NULL DEFAULT now(),
        updated_at            TIMESTAMPTZ   NOT NULL DEFAULT now(),

        CONSTRAINT chk_pi_amounts CHECK (
            base_salary >= 0 AND pt_commission >= 0 AND sales_commission >= 0 AND
            bonus_amount >= 0 AND deduction_amount >= 0
        )
    );

    CREATE UNIQUE INDEX uq_pi_emp_run ON payroll_items (payroll_run_id, employee_id);
    CREATE INDEX idx_pi_employee ON payroll_items (employee_id);

    CREATE TRIGGER trg_payroll_items_updated_at BEFORE UPDATE ON payroll_items
        FOR EACH ROW EXECUTE FUNCTION set_updated_at();


    CREATE TABLE expenses (
        id             BIGSERIAL PRIMARY KEY,
        expense_no     VARCHAR(30)   NOT NULL,
        category       VARCHAR(30)   NOT NULL,
        title          VARCHAR(255)  NOT NULL,
        amount         NUMERIC(14,2) NOT NULL,
        spent_at       DATE          NOT NULL,
        spent_by       BIGINT        REFERENCES users(id),
        approved_by    BIGINT        REFERENCES users(id),
        status         VARCHAR(20)   NOT NULL DEFAULT 'APPROVED',
        payment_method VARCHAR(20)   NOT NULL DEFAULT 'BANK_TRANSFER',
        receipt_url    VARCHAR(500),
        note           TEXT,

        created_at     TIMESTAMPTZ   NOT NULL DEFAULT now(),
        updated_at     TIMESTAMPTZ   NOT NULL DEFAULT now(),
        deleted_at     TIMESTAMPTZ,

        CONSTRAINT chk_exp_category CHECK (category IN
            ('RENT','UTILITIES','EQUIPMENT_MAINTENANCE','SALARY','SUPPLIES','MARKETING','OTHER')),
        CONSTRAINT chk_exp_status CHECK (status IN ('PENDING','APPROVED','REJECTED')),
        CONSTRAINT chk_exp_method CHECK (payment_method IN
            ('CASH','BANK_TRANSFER','CARD_POS','E_WALLET')),
        CONSTRAINT chk_exp_amount CHECK (amount > 0)
    );

    CREATE UNIQUE INDEX uq_exp_no ON expenses (expense_no) WHERE deleted_at IS NULL;
    CREATE INDEX idx_exp_spent_at ON expenses (spent_at DESC) WHERE deleted_at IS NULL;
    CREATE INDEX idx_exp_category ON expenses (category) WHERE deleted_at IS NULL;

    CREATE TRIGGER trg_expenses_updated_at BEFORE UPDATE ON expenses
        FOR EACH ROW EXECUTE FUNCTION set_updated_at();


    -- =============================================================================
    -- NHÓM 9 — BÁN HÀNG / CRM
    -- =============================================================================

    CREATE TABLE leads (
        id                         BIGSERIAL PRIMARY KEY,
        person_id                  BIGINT        NOT NULL REFERENCES persons(id),
        source                     VARCHAR(30)   NOT NULL,
        interested_membership_id   BIGINT        REFERENCES memberships(id),
        assigned_to                BIGINT        REFERENCES employees(id),
        stage                      VARCHAR(20)   NOT NULL DEFAULT 'NEW',
        lost_reason                VARCHAR(30),
        last_contact_at            TIMESTAMPTZ,
        last_contact_note          TEXT,
        next_follow_up             DATE,

        created_at                 TIMESTAMPTZ   NOT NULL DEFAULT now(),
        updated_at                 TIMESTAMPTZ   NOT NULL DEFAULT now(),
        deleted_at                 TIMESTAMPTZ,

        CONSTRAINT chk_lead_source CHECK (source IN
            ('WALK_IN','HOTLINE','WEB_FORM','REFERRAL','APP_SELF')),
        CONSTRAINT chk_lead_stage CHECK (stage IN
            ('NEW','CONTACTED','TRIAL_BOOKED','TRIAL_DONE','WON','LOST')),
        CONSTRAINT chk_lead_lost CHECK (
            stage <> 'LOST' OR lost_reason IS NOT NULL
        ),
        CONSTRAINT chk_lead_lost_reason CHECK (
            lost_reason IS NULL OR lost_reason IN ('PRICE','LOCATION','COMPETITOR','NOT_READY','NO_RESPONSE')
        )
    );

    CREATE INDEX idx_leads_person ON leads (person_id);
    CREATE INDEX idx_leads_assigned_stage ON leads (assigned_to, stage) WHERE deleted_at IS NULL;
    CREATE INDEX idx_leads_stage ON leads (stage) WHERE deleted_at IS NULL;
    CREATE INDEX idx_leads_next_follow_up ON leads (next_follow_up)
        WHERE deleted_at IS NULL AND stage NOT IN ('WON', 'LOST');

    CREATE TRIGGER trg_leads_updated_at BEFORE UPDATE ON leads
        FOR EACH ROW EXECUTE FUNCTION set_updated_at();


    -- =============================================================================
    -- NHÓM 6 — THIẾT BỊ & NHÓM 11 — PHẢN HỒI
    -- =============================================================================

    -- equipment: bản rút gọn, 1 dòng = 1 loại/khu thiết bị (không tách
    -- equipment_types/equipment_items như thiết kế đầy đủ)
    CREATE TABLE equipment (
        id           BIGSERIAL PRIMARY KEY,
        name         VARCHAR(120)  NOT NULL,
        room_name    VARCHAR(120),
        status       VARCHAR(20)   NOT NULL DEFAULT 'ACTIVE',
        note         VARCHAR(255),

        created_at   TIMESTAMPTZ   NOT NULL DEFAULT now(),
        updated_at   TIMESTAMPTZ   NOT NULL DEFAULT now(),
        deleted_at   TIMESTAMPTZ,

        CONSTRAINT chk_equipment_status CHECK (status IN
            ('ACTIVE','NEEDS_REPAIR','UNDER_REPAIR','RETIRED'))
    );

    CREATE INDEX idx_equipment_status ON equipment (status) WHERE deleted_at IS NULL;

    CREATE TRIGGER trg_equipment_updated_at BEFORE UPDATE ON equipment
        FOR EACH ROW EXECUTE FUNCTION set_updated_at();


    -- feedbacks: bản rút gọn so với thiết kế đầy đủ (không có title, severity,
    -- sla_due_at, is_anonymous, expense_id). rating là INTEGER (đổi từ SMALLINT
    -- ở V14 để khớp entity Java). deleted_at thêm ở V13 (thiếu sót ban đầu ở V12).
    CREATE TABLE feedbacks (
        id                BIGSERIAL PRIMARY KEY,
        member_id         BIGINT        NOT NULL REFERENCES members(id),
        feedback_type     VARCHAR(20)   NOT NULL,
        trainer_id        BIGINT        REFERENCES employees(id),
        equipment_id      BIGINT        REFERENCES equipment(id),
        rating            INTEGER,
        description       TEXT          NOT NULL,
        status            VARCHAR(20)   NOT NULL DEFAULT 'OPEN',
        is_urgent         BOOLEAN       NOT NULL DEFAULT FALSE,
        repair_cost       NUMERIC(14,2),
        resolution_note   VARCHAR(500),
        resolved_by       BIGINT        REFERENCES users(id),
        resolved_at       TIMESTAMPTZ,

        created_at        TIMESTAMPTZ   NOT NULL DEFAULT now(),
        updated_at        TIMESTAMPTZ   NOT NULL DEFAULT now(),
        deleted_at        TIMESTAMPTZ,

        CONSTRAINT chk_fb_type CHECK (feedback_type IN
            ('TRAINER','FACILITY','HYGIENE','SERVICE','GENERAL')),
        CONSTRAINT chk_fb_status CHECK (status IN
            ('OPEN','IN_PROGRESS','WAITING_PARTS','RESOLVED','CLOSED')),
        CONSTRAINT chk_fb_rating CHECK (rating IS NULL OR rating BETWEEN 1 AND 5),
        CONSTRAINT chk_fb_trainer_required CHECK (feedback_type <> 'TRAINER' OR trainer_id IS NOT NULL),
        CONSTRAINT chk_fb_equipment_required CHECK (feedback_type <> 'FACILITY' OR equipment_id IS NOT NULL),
        CONSTRAINT chk_fb_repair_cost CHECK (repair_cost IS NULL OR repair_cost >= 0)
    );

    CREATE INDEX idx_feedback_member    ON feedbacks (member_id);
    CREATE INDEX idx_feedback_trainer    ON feedbacks (trainer_id) WHERE trainer_id IS NOT NULL;
    CREATE INDEX idx_feedback_equipment  ON feedbacks (equipment_id) WHERE equipment_id IS NOT NULL;
    CREATE INDEX idx_feedback_open       ON feedbacks (created_at)
        WHERE status IN ('OPEN','IN_PROGRESS','WAITING_PARTS');

    CREATE TRIGGER trg_feedbacks_updated_at BEFORE UPDATE ON feedbacks
        FOR EACH ROW EXECUTE FUNCTION set_updated_at();


    -- =============================================================================
    -- NHÓM 12 — HỆ THỐNG
    -- =============================================================================

    -- system_settings: tham số nghiệp vụ chỉnh qua API thay vì hardcode trong
    -- code. Giá trị lưu dạng chuỗi, ép kiểu ở tầng service (SystemSettingService),
    -- có fallback về giá trị mặc định nếu thiếu/sai định dạng.
    CREATE TABLE system_settings (
        setting_key   VARCHAR(100) PRIMARY KEY,
        setting_value VARCHAR(500) NOT NULL,
        description   VARCHAR(255),
        updated_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
        updated_by    BIGINT REFERENCES users(id)
    );

    INSERT INTO system_settings (setting_key, setting_value, description) VALUES
        ('pt.cancel.min-hours-before', '4',
            'Huy buoi tap sat gio hen hon so gio nay thi tinh la huy muon, mat buoi'),
        ('security.login.max-failed-attempts', '5',
            'Sai mat khau qua so lan nay thi khoa tam tai khoan'),
        ('security.login.lockout-minutes', '15',
            'So phut khoa tam tai khoan sau khi sai mat khau qua so lan cho phep'),
        ('membership.freeze.min-advance-days', '3',
            'Phai bao truoc it nhat so ngay nay moi duoc bao luu goi tap'),
        ('checkin.duplicate-scan-window-minutes', '30',
            'Quet lai trong khoang thoi gian nay (phut) sau khi da quet ra bi coi la bat thuong'),
        ('gym.closing-time', '22:30',
            'Gio dong cua phong tap, dung cho job dem tu dong dong cac luot quen quet ra'),
        ('payroll.pt-commission-per-session', '100000',
            'Hoa hong PT tinh theo moi buoi COMPLETED trong thang (VND)'),
        ('payroll.sales-commission-rate', '0.05',
            'Ty le hoa hong Sales/Le tan tren tong doanh so thu tien trong thang');


    -- =============================================================================
    -- NHÓM 10 — BÀI TẬP & CHỈ SỐ CƠ THỂ  ⚠️ ĐỀ XUẤT, CHƯA CÓ MIGRATION FLYWAY THẬT
    --
    -- 4 bảng dưới đây KHÔNG nằm trong V1-V19 đã áp dụng — không được Flyway quản
    -- lý, không được validate bởi hibernate.ddl-auto=validate. Nếu triển khai,
    -- phải tạo thành file VXX__module_g.sql riêng trong
    -- backend/src/main/resources/db/migration/, không chạy trực tiếp khối này.
    --
    -- Phục vụ 3 kịch bản chatbot: (1) đối chiếu giáo án PT với thể trạng hội viên
    -- để cảnh báo an toàn, (2) diễn giải tiến độ tập (kết hợp check_ins +
    -- pt_sessions + body_metrics, KHÔNG cần workout_logs — đã loại khỏi đề xuất
    -- vì bắt hội viên gõ tay sets/reps/weight sau mỗi bài là bất tiện), (3) gợi ý
    -- bài tập thay thế có lọc theo chống chỉ định cá nhân.
    -- =============================================================================

    CREATE TABLE exercises (
        id                 BIGSERIAL PRIMARY KEY,
        code               VARCHAR(60)   NOT NULL,
        name_vi            VARCHAR(200)  NOT NULL,
        name_en            VARCHAR(200),
        muscle_group       VARCHAR(40)   NOT NULL,
        secondary_muscles  VARCHAR(200),
        equipment          VARCHAR(60),
        difficulty         VARCHAR(20)   NOT NULL DEFAULT 'BEGINNER',
        instructions       TEXT,
        media_key          VARCHAR(500),

        -- Cột then chốt cho chatbot: chống chỉ định, đối chiếu với
        -- members.health_note / body_metrics để loại bài không an toàn
        contraindications  JSONB,

        source             VARCHAR(60),
        license            VARCHAR(60),
        -- Bản dịch máy bắt buộc người rà soát mới hiển thị cho hội viên
        is_reviewed        BOOLEAN       NOT NULL DEFAULT FALSE,

        created_at         TIMESTAMPTZ   NOT NULL DEFAULT now(),
        updated_at         TIMESTAMPTZ   NOT NULL DEFAULT now(),
        deleted_at         TIMESTAMPTZ,

        CONSTRAINT chk_ex_muscle_group CHECK (muscle_group IN
            ('CHEST','BACK','LEGS','SHOULDERS','ARMS','CORE','CARDIO')),
        CONSTRAINT chk_ex_equipment CHECK (equipment IS NULL OR equipment IN
            ('BARBELL','DUMBBELL','MACHINE','BODYWEIGHT','CABLE','KETTLEBELL')),
        CONSTRAINT chk_ex_difficulty CHECK (difficulty IN
            ('BEGINNER','INTERMEDIATE','ADVANCED'))
    );

    CREATE UNIQUE INDEX uq_exercises_code ON exercises (code) WHERE deleted_at IS NULL;
    CREATE INDEX idx_exercises_muscle_group ON exercises (muscle_group) WHERE deleted_at IS NULL;
    CREATE INDEX idx_exercises_reviewed ON exercises (is_reviewed) WHERE deleted_at IS NULL;

    CREATE TRIGGER trg_exercises_updated_at BEFORE UPDATE ON exercises
        FOR EACH ROW EXECUTE FUNCTION set_updated_at();


    -- workout_plans — giáo án. is_template=TRUE là giáo án mẫu chưa gán ai.
    -- ai_generated bắt buộc có reviewed_by: AI hỗ trợ soạn, PT chịu trách nhiệm.
    CREATE TABLE workout_plans (
        id                  BIGSERIAL PRIMARY KEY,
        is_template         BOOLEAN       NOT NULL DEFAULT FALSE,
        member_id           BIGINT        REFERENCES members(id),
        trainer_id          BIGINT        REFERENCES employees(id),
        source_template_id  BIGINT        REFERENCES workout_plans(id),

        name                VARCHAR(150)  NOT NULL,
        goal                VARCHAR(30),
        level               VARCHAR(20),
        duration_weeks      SMALLINT,
        days_per_week       SMALLINT,
        start_date          DATE,
        end_date            DATE,
        status              VARCHAR(20)   NOT NULL DEFAULT 'ACTIVE',

        ai_generated        BOOLEAN       NOT NULL DEFAULT FALSE,
        reviewed_by         BIGINT        REFERENCES employees(id),

        created_at          TIMESTAMPTZ   NOT NULL DEFAULT now(),
        updated_at          TIMESTAMPTZ   NOT NULL DEFAULT now(),
        deleted_at          TIMESTAMPTZ,

        CONSTRAINT chk_wp_goal CHECK (goal IS NULL OR goal IN
            ('LOSE_FAT','GAIN_MUSCLE','STRENGTH','ENDURANCE')),
        CONSTRAINT chk_wp_level CHECK (level IS NULL OR level IN
            ('BEGINNER','INTERMEDIATE','ADVANCED')),
        CONSTRAINT chk_wp_status CHECK (status IN
            ('ACTIVE','COMPLETED','PAUSED','CANCELLED')),
        CONSTRAINT chk_wp_dates CHECK (end_date IS NULL OR start_date IS NULL OR end_date >= start_date),
        -- AI sinh bản nháp thì PT PHẢI duyệt trước khi dùng
        CONSTRAINT chk_wp_ai_review CHECK (NOT ai_generated OR reviewed_by IS NOT NULL),
        -- Giáo án mẫu thì không gán cho hội viên nào, ngược lại phải có
        CONSTRAINT chk_wp_template CHECK (is_template = (member_id IS NULL))
    );

    CREATE INDEX idx_wp_member ON workout_plans (member_id) WHERE deleted_at IS NULL AND member_id IS NOT NULL;
    CREATE INDEX idx_wp_trainer ON workout_plans (trainer_id) WHERE deleted_at IS NULL AND trainer_id IS NOT NULL;
    CREATE INDEX idx_wp_templates ON workout_plans (is_template) WHERE deleted_at IS NULL AND is_template = TRUE;

    CREATE TRIGGER trg_workout_plans_updated_at BEFORE UPDATE ON workout_plans
        FOR EACH ROW EXECUTE FUNCTION set_updated_at();


    CREATE TABLE workout_plan_items (
        id                BIGSERIAL PRIMARY KEY,
        plan_id           BIGINT      NOT NULL REFERENCES workout_plans(id),
        day_index         SMALLINT    NOT NULL,
        order_index       SMALLINT    NOT NULL,
        exercise_id       BIGINT      NOT NULL REFERENCES exercises(id),
        sets              SMALLINT,
        -- Kieu chu vi co dang khoang: "8-12", "AMRAP", "30 giay"
        reps              VARCHAR(20),
        target_weight_kg  NUMERIC(6,2),
        rest_sec          SMALLINT,
        note              VARCHAR(255),

        created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
        updated_at        TIMESTAMPTZ NOT NULL DEFAULT now()
    );

    CREATE UNIQUE INDEX uq_wpi_plan_day_order ON workout_plan_items (plan_id, day_index, order_index);
    CREATE INDEX idx_wpi_exercise ON workout_plan_items (exercise_id);

    CREATE TRIGGER trg_workout_plan_items_updated_at BEFORE UPDATE ON workout_plan_items
        FOR EACH ROW EXECUTE FUNCTION set_updated_at();


    -- body_metrics — chỉ số cơ thể. Bảng quan trọng nhất Nhóm G: input duy nhất
    -- để cá nhân hóa theo tuổi/thể trạng (kịch bản 1: cảnh báo giáo án không an
    -- toàn cho người lớn tuổi/có bệnh nền).
    CREATE TABLE body_metrics (
        id                 BIGSERIAL PRIMARY KEY,
        member_id          BIGINT        NOT NULL REFERENCES members(id),
        measured_at        TIMESTAMPTZ   NOT NULL DEFAULT now(),
        -- NULL = hội viên tự nhập
        measured_by        BIGINT        REFERENCES users(id),
        source             VARCHAR(20)   NOT NULL DEFAULT 'MANUAL',

        height_cm          NUMERIC(5,2),
        weight_kg          NUMERIC(5,2),
        body_fat_pct       NUMERIC(4,2),
        muscle_mass_kg     NUMERIC(5,2),
        visceral_fat       NUMERIC(4,1),
        bmr_kcal           INTEGER,
        -- CSDL tự tính, không nhap tay -> khong the lech voi height_cm/weight_kg
        bmi                NUMERIC(5,2) GENERATED ALWAYS AS (
                                weight_kg / POWER(height_cm / 100.0, 2)
                            ) STORED,

        chest_cm           NUMERIC(5,2),
        waist_cm           NUMERIC(5,2),
        hip_cm             NUMERIC(5,2),
        arm_cm             NUMERIC(5,2),
        thigh_cm           NUMERIC(5,2),

        photo_key          VARCHAR(500),
        ocr_confidence     NUMERIC(4,3),
        -- AI (INBODY_OCR) khong bao gio ghi thang vao CSDL - nguoi dung phai xac
        -- nhan so lieu truoc, ke ca khi da co photo_key + ocr_confidence
        confirmed_by_user  BOOLEAN       NOT NULL DEFAULT FALSE,

        note               VARCHAR(255),

        created_at         TIMESTAMPTZ   NOT NULL DEFAULT now(),
        updated_at         TIMESTAMPTZ   NOT NULL DEFAULT now(),

        CONSTRAINT chk_bm_source CHECK (source IN ('MANUAL','INBODY_OCR','DEVICE_SYNC')),
        CONSTRAINT chk_bm_height CHECK (height_cm IS NULL OR height_cm BETWEEN 80 AND 250),
        CONSTRAINT chk_bm_weight CHECK (weight_kg IS NULL OR weight_kg BETWEEN 20 AND 300),
        CONSTRAINT chk_bm_body_fat CHECK (body_fat_pct IS NULL OR body_fat_pct BETWEEN 1 AND 70),
        CONSTRAINT chk_bm_ocr_confidence CHECK (ocr_confidence IS NULL OR ocr_confidence BETWEEN 0 AND 1),
        -- OCR bắt buộc phải có ảnh nguồn để đối chiếu khi hội viên sửa số
        CONSTRAINT chk_bm_ocr_photo CHECK (source <> 'INBODY_OCR' OR photo_key IS NOT NULL)
    );

    CREATE INDEX idx_bm_member ON body_metrics (member_id, measured_at DESC);
    -- Job/chatbot chỉ được đọc số liệu đã hội viên xác nhận
    CREATE INDEX idx_bm_confirmed ON body_metrics (member_id, measured_at DESC) WHERE confirmed_by_user = TRUE;

    CREATE TRIGGER trg_body_metrics_updated_at BEFORE UPDATE ON body_metrics
        FOR EACH ROW EXECUTE FUNCTION set_updated_at();
