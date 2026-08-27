-- =============================================================================
-- V5 — NHÓM 4: BUỔI TẬP PT & SỔ CÁI TÍN DỤNG BUỔI TẬP
--
-- Quyết định module C:
--   · Xác nhận bằng NÚT BẤM, không dùng mã QR (check_ins đã chứng minh có mặt)
--   · BỎ trạng thái IN_PROGRESS — bấm "Bắt đầu" không gây hệ quả nghiệp vụ nào
--   · GỘP NO_SHOW_MEMBER + NO_SHOW_TRAINER thành NO_SHOW + cột no_show_by
--   · GIỮ responded_at
--   → status từ 8 xuống 6 trạng thái
-- =============================================================================


-- =============================================================================
-- pt_sessions — buổi tập cá nhân, gánh luôn vòng đời đặt lịch
-- =============================================================================
CREATE TABLE pt_sessions (
    id                   BIGSERIAL PRIMARY KEY,
    member_id            BIGINT      NOT NULL REFERENCES members(id),

    -- Huấn luyện viên THỰC TẾ dạy buổi này. Tiền công tính cho người này,
    -- KHÔNG phải người được phân công ở registrations.assigned_trainer_id.
    trainer_id           BIGINT      NOT NULL REFERENCES employees(id),
    registration_id      BIGINT      NOT NULL REFERENCES registrations(id),

    session_type         VARCHAR(20) NOT NULL,
    scheduled_start      TIMESTAMPTZ NOT NULL,
    scheduled_end        TIMESTAMPTZ NOT NULL,
    actual_start         TIMESTAMPTZ,
    actual_end           TIMESTAMPTZ,
    room_name            VARCHAR(120),

    status               VARCHAR(20) NOT NULL DEFAULT 'PENDING_TRAINER',

    -- ---- Vòng đời đặt lịch (thay bảng pt_bookings) ----
    requested_by         BIGINT      REFERENCES users(id),
    responded_at         TIMESTAMPTZ,
    reject_reason        VARCHAR(255),

    -- ---- XÁC NHẬN HAI CHIỀU: cốt lõi chống khai khống buổi tập ----
    -- Huấn luyện viên bấm "Kết thúc" → trainer_confirmed_at.
    -- Đây là ĐIỀU KIỆN KHỞI ĐỘNG: không bấm thì không có gì xảy ra, nên
    -- buổi không dạy sẽ không bao giờ tự động được trả công.
    trainer_confirmed_at TIMESTAMPTZ,
    member_confirmed_at  TIMESTAMPTZ,
    auto_confirmed       BOOLEAN     NOT NULL DEFAULT FALSE,

    -- ---- Vắng mặt (gộp 2 trạng thái cũ) ----
    no_show_by           VARCHAR(20),

    -- ---- Hủy buổi ----
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

    -- 6 trạng thái (bỏ IN_PROGRESS, gộp 2 loại NO_SHOW)
    CONSTRAINT chk_pts_status CHECK (status IN
        ('PENDING_TRAINER','REJECTED','SCHEDULED','COMPLETED','NO_SHOW','CANCELLED')),

    CONSTRAINT chk_pts_time  CHECK (scheduled_end > scheduled_start),
    CONSTRAINT chk_pts_actual CHECK (
        actual_end IS NULL OR actual_start IS NULL OR actual_end >= actual_start
    ),

    -- Buổi hoàn thành BẮT BUỘC có đủ hai xác nhận. Ràng buộc đặt ở CSDL để
    -- không thể lách bằng cách gọi thẳng UPDATE.
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
-- Lịch dạy sắp tới của huấn luyện viên
CREATE INDEX idx_pts_upcoming     ON pt_sessions (trainer_id, scheduled_start)
    WHERE status IN ('PENDING_TRAINER','SCHEDULED');
-- Job đêm tìm buổi huấn luyện viên đã xác nhận mà hội viên chưa phản hồi
CREATE INDEX idx_pts_pending_member ON pt_sessions (trainer_confirmed_at)
    WHERE status = 'SCHEDULED' AND trainer_confirmed_at IS NOT NULL
      AND member_confirmed_at IS NULL;

CREATE TRIGGER trg_pt_sessions_updated_at BEFORE UPDATE ON pt_sessions
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

COMMENT ON COLUMN pt_sessions.trainer_id IS
    'Nguoi THUC TE day. Tien cong tinh cho nguoi nay, khong phai PT duoc phan cong';
COMMENT ON COLUMN pt_sessions.auto_confirmed IS
    'TRUE = he thong tu duyet sau 24h hoi vien khong phan hoi. Ty le cao la dau hieu can kiem toan';


-- =============================================================================
-- session_credit_ledger — SỔ CÁI TÍN DỤNG BUỔI TẬP  ⭐ ĐÓNG GÓP HỌC THUẬT 1
--
-- Áp dụng nguyên lý sổ cái kế toán vào quản lý số buổi tập:
--   · CHỈ GHI THÊM — không sửa, không xóa (trigger chặn ở dưới)
--   · Số dư không phải một cột bị ghi đè, mà là TỔNG CỘNG DỒN các bút toán
--   · Mọi biến động đều truy được về nguồn gốc qua source_type/source_id
--
-- Ba bất biến phải luôn đúng:
--   1. SUM(delta) của mỗi hợp đồng == balance_after của bút toán mới nhất
--   2. balance_after không bao giờ âm
--   3. balance_after[i] == balance_after[i-1] + delta[i]
-- =============================================================================
CREATE TABLE session_credit_ledger (
    id              BIGSERIAL PRIMARY KEY,
    registration_id BIGINT      NOT NULL REFERENCES registrations(id),

    entry_type      VARCHAR(20) NOT NULL,

    -- Biến động của bút toán này: dương là cộng buổi, âm là trừ buổi
    delta           INTEGER     NOT NULL,

    -- Số dư SAU khi áp bút toán này. Lưu lại để đối chiếu chéo với SUM(delta),
    -- nhờ vậy phát hiện được sai sót ngay thay vì phải tính lại toàn bộ lịch sử.
    balance_after   INTEGER     NOT NULL,

    -- Bút toán này sinh ra từ đâu: PT_SESSION / REGISTRATION / MANUAL / EXPIRY_JOB
    source_type     VARCHAR(30) NOT NULL,
    source_id       BIGINT,

    reason          VARCHAR(255),
    created_by      BIGINT      REFERENCES users(id),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT chk_scl_type CHECK (entry_type IN
        ('GRANT','CONSUME','REFUND','EXPIRE','ADJUST')),

    -- BẤT BIẾN 2: số dư không bao giờ âm
    CONSTRAINT chk_scl_balance CHECK (balance_after >= 0),

    -- Dấu của delta phải khớp với loại bút toán
    CONSTRAINT chk_scl_delta_sign CHECK (
        (entry_type IN ('GRANT','REFUND')  AND delta > 0) OR
        (entry_type IN ('CONSUME','EXPIRE') AND delta < 0) OR
        (entry_type = 'ADJUST' AND delta <> 0)
    ),

    -- Sửa tay bắt buộc ghi lý do và người thực hiện — đây là loại bút toán
    -- duy nhất do con người quyết định, nên phải truy được trách nhiệm.
    CONSTRAINT chk_scl_adjust CHECK (
        entry_type <> 'ADJUST' OR (reason IS NOT NULL AND created_by IS NOT NULL)
    )
);

CREATE INDEX idx_scl_registration ON session_credit_ledger (registration_id, id);

-- Một buổi tập chỉ được trừ buổi ĐÚNG MỘT LẦN.
-- Chặn ở tầng CSDL nên kể cả hai request chạy song song cũng không trừ hai lần.
CREATE UNIQUE INDEX uq_scl_consume_source ON session_credit_ledger (source_type, source_id)
    WHERE entry_type = 'CONSUME' AND source_id IS NOT NULL;

-- Mỗi hợp đồng chỉ được cấp buổi một lần lúc kích hoạt
CREATE UNIQUE INDEX uq_scl_grant ON session_credit_ledger (registration_id)
    WHERE entry_type = 'GRANT';


-- =============================================================================
-- CHẶN SỬA VÀ XÓA — điều làm nên tính chất "sổ cái"
--
-- Đặt ở tầng CSDL nên vẫn hiệu lực kể cả khi có người mở pgAdmin gõ tay,
-- hoặc lập trình viên vô tình gọi save() trên một bút toán đã tồn tại.
-- Muốn sửa sai thì phải ghi một bút toán ADJUST mới — vết cũ được giữ nguyên.
-- =============================================================================
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

COMMENT ON TABLE session_credit_ledger IS
    'So cai CHI GHI THEM. Trigger chan UPDATE va DELETE. So du = tong cong don cac but toan';
