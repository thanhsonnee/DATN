-- =============================================================================
-- V3 — NHÓM 2: GÓI TẬP & HỢP ĐỒNG
-- memberships · registrations (gánh luôn BẢO LƯU) · member_trainers
--
-- Quyết định 15: mỗi hợp đồng chỉ được bảo lưu ĐÚNG MỘT LẦN
--   → bảng registration_freezes bị bỏ, các cột chuyển thẳng vào registrations
--   → 32 bảng còn 31
-- =============================================================================


-- =============================================================================
-- memberships — danh mục gói tập đang bán
-- Gánh thêm membership_prices (→ cột price) và access_scopes (→ area_codes)
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

    -- Chỉ dùng cho gói HYBRID: bao nhiêu phần giá trị thuộc phần PT.
    -- Cần để chia doanh thu: phần PT ghi nhận theo buổi, phần còn lại theo thời gian.
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

    -- Gói HYBRID BẮT BUỘC khai tỷ lệ giá trị phần PT, nếu không sẽ không chia
    -- được doanh thu. Ràng buộc đặt ở CSDL để không thể quên.
    CONSTRAINT chk_ms_pt_ratio CHECK (
        package_type <> 'HYBRID'
        OR (pt_value_ratio IS NOT NULL AND pt_value_ratio > 0 AND pt_value_ratio < 1)
    ),
    -- Gói tính theo buổi thì bắt buộc có số buổi
    CONSTRAINT chk_ms_sessions CHECK (
        package_type NOT IN ('SESSION_BASED','HYBRID')
        OR (session_count IS NOT NULL AND session_count > 0)
    ),
    -- Gói tính theo thời gian thì bắt buộc có thời hạn
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

COMMENT ON COLUMN memberships.price IS 'Gia niem yet HIEN TAI. Lich su gia nam o snapshot trong registrations';


-- =============================================================================
-- registrations — HỢP ĐỒNG (bảng trung tâm của hệ thống)
--
-- Gánh 2 vai trò phụ:
--   · promotions      → discount_amount + discount_reason
--   · bảo lưu (1 lần) → nhóm cột freeze_*
--
-- KHÔNG còn vai trò báo giá (quyết định 12): giá và khuyến mãi công khai trên
-- web nên member_id luôn NOT NULL, không có trạng thái DRAFT.
-- =============================================================================
CREATE TABLE registrations (
    id                    BIGSERIAL PRIMARY KEY,
    registration_code     VARCHAR(30)   NOT NULL,
    member_id             BIGINT        NOT NULL REFERENCES members(id),
    membership_id         BIGINT        NOT NULL REFERENCES memberships(id),
    sold_by               BIGINT        REFERENCES employees(id),
    assigned_trainer_id   BIGINT        REFERENCES employees(id),

    -- ---- Snapshot điều khoản thương mại: sao chép LÚC KÝ, không đọc ngược ----
    -- Nhờ vậy đổi giá gói về sau không làm thay đổi hợp đồng đã ký.
    package_type          VARCHAR(20)   NOT NULL,
    duration_days         INTEGER,
    sessions_total        INTEGER,
    list_price            NUMERIC(14,2) NOT NULL,
    discount_amount       NUMERIC(14,2) NOT NULL DEFAULT 0,
    discount_reason       VARCHAR(255),
    discount_approved_by  BIGINT        REFERENCES users(id),
    final_price           NUMERIC(14,2) NOT NULL,

    -- ---- Thời gian ----
    contract_date         DATE          NOT NULL,
    start_date            DATE,
    end_date              DATE,
    activated_at          TIMESTAMPTZ,
    closed_at             TIMESTAMPTZ,

    status                VARCHAR(20)   NOT NULL DEFAULT 'PENDING_PAYMENT',
    close_reason          VARCHAR(255),
    note                  TEXT,

    -- ---- BẢO LƯU (thay bảng registration_freezes, tối đa 1 lần/hợp đồng) ----
    freeze_from_date      DATE,
    freeze_to_date        DATE,
    -- Cột TỰ TÍNH: CSDL tự sinh, không ai nhập được nên không thể lệch
    freeze_days           INTEGER GENERATED ALWAYS AS (freeze_to_date - freeze_from_date + 1) STORED,
    freeze_reason         VARCHAR(255),
    freeze_reason_type    VARCHAR(20),
    freeze_attachment_key VARCHAR(500),
    freeze_requested_by   BIGINT        REFERENCES users(id),
    freeze_approved_by    BIGINT        REFERENCES users(id),
    freeze_status         VARCHAR(20),
    freeze_ended_early_at DATE,

    created_at            TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ   NOT NULL DEFAULT now(),
    deleted_at            TIMESTAMPTZ,

    -- 6 trạng thái (bỏ DRAFT và EXPIRED_QUOTE theo quyết định 12)
    CONSTRAINT chk_reg_status CHECK (status IN
        ('PENDING_PAYMENT','ACTIVE','FROZEN','COMPLETED','CANCELLED','REFUNDED')),
    CONSTRAINT chk_reg_type CHECK (package_type IN
        ('TIME_BASED','SESSION_BASED','HYBRID','DAY_PASS')),

    -- Giá phải nhất quán
    CONSTRAINT chk_reg_price   CHECK (final_price = list_price - discount_amount),
    CONSTRAINT chk_reg_amounts CHECK (list_price >= 0 AND discount_amount >= 0 AND final_price >= 0),
    -- Có giảm giá thì bắt buộc ghi lý do (tên chương trình khuyến mãi)
    CONSTRAINT chk_reg_discount CHECK (discount_amount = 0 OR discount_reason IS NOT NULL),
    CONSTRAINT chk_reg_dates CHECK (
        end_date IS NULL OR start_date IS NULL OR end_date >= start_date
    ),

    -- ---- Ràng buộc bảo lưu ----
    CONSTRAINT chk_freeze_status CHECK (freeze_status IS NULL OR freeze_status IN
        ('PENDING','APPROVED','REJECTED','ACTIVE','ENDED','CANCELLED')),
    CONSTRAINT chk_freeze_reason_type CHECK (freeze_reason_type IS NULL OR freeze_reason_type IN
        ('PERSONAL','MEDICAL','TRAVEL','OTHER')),
    CONSTRAINT chk_freeze_range CHECK (
        freeze_to_date IS NULL OR freeze_from_date IS NULL OR freeze_to_date >= freeze_from_date
    ),
    -- Có yêu cầu bảo lưu thì phải đủ: khoảng thời gian, lý do, trạng thái
    CONSTRAINT chk_freeze_complete CHECK (
        freeze_status IS NULL
        OR (freeze_from_date IS NOT NULL AND freeze_to_date IS NOT NULL AND freeze_reason IS NOT NULL)
    ),
    -- Hợp đồng đang FROZEN thì bắt buộc phải có kỳ bảo lưu đang hiệu lực
    CONSTRAINT chk_reg_frozen CHECK (
        status <> 'FROZEN' OR (freeze_status IS NOT NULL AND freeze_status = 'ACTIVE')
    )
);

CREATE UNIQUE INDEX uq_reg_code ON registrations (registration_code) WHERE deleted_at IS NULL;
CREATE INDEX idx_reg_member     ON registrations (member_id, status);
CREATE INDEX idx_reg_membership ON registrations (membership_id);
CREATE INDEX idx_reg_sold_by    ON registrations (sold_by) WHERE sold_by IS NOT NULL;
CREATE INDEX idx_reg_trainer    ON registrations (assigned_trainer_id) WHERE assigned_trainer_id IS NOT NULL;
-- Truy vấn chạy thường xuyên: danh sách hợp đồng sắp hết hạn để mời gia hạn
CREATE INDEX idx_reg_expiring   ON registrations (end_date) WHERE status = 'ACTIVE';
-- Job đêm quét hợp đồng chốt mua nhưng bỏ không trả tiền quá 48h
CREATE INDEX idx_reg_pending    ON registrations (created_at) WHERE status = 'PENDING_PAYMENT';

CREATE TRIGGER trg_registrations_updated_at BEFORE UPDATE ON registrations
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

COMMENT ON TABLE  registrations IS 'Hop dong da chot. Ganh luon bao luu (1 lan/hop dong) va khuyen mai';
COMMENT ON COLUMN registrations.freeze_days IS 'CSDL tu tinh tu freeze_to_date - freeze_from_date + 1, khong nhap tay';
COMMENT ON COLUMN registrations.end_date IS 'Bi DAY LUI them freeze_days khi ky bao luu duoc duyet';


-- =============================================================================
-- member_trainers — phân công huấn luyện viên cho hội viên
-- =============================================================================
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

-- Mỗi hội viên chỉ có ĐÚNG MỘT huấn luyện viên chính tại một thời điểm
CREATE UNIQUE INDEX uq_mt_primary ON member_trainers (member_id)
    WHERE role = 'PRIMARY' AND to_date IS NULL;

CREATE INDEX idx_mt_trainer ON member_trainers (trainer_id) WHERE to_date IS NULL;

CREATE TRIGGER trg_member_trainers_updated_at BEFORE UPDATE ON member_trainers
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
