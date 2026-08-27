-- =============================================================================
-- V7 — NHÓM 3: CHECK-IN
--
-- Quyết định module E:
--   · BỎ thẻ từ RFID       — cần mua đầu đọc thật mới demo được
--   · BỎ nhận diện khuôn mặt — ngoài phạm vi
--   · BỎ nhập tay (MANUAL)
--   · BỎ employee_id       — chấm công là mục đích khác, thêm lại sau nếu cần
--   · BỎ device_id         — chỉ có ý nghĩa khi có nhiều quầy cùng lúc
--   · BỎ photo_key         — Hướng A dùng lại ẢNH HỒ SƠ (persons.photo_key),
--                            không chụp ảnh mới lúc quét
--   · THÊM auto_closed     — xử lý lượt quên check-out
--
--   → method còn 2 giá trị, incident_type còn 4
--
-- LUỒNG ĐÃ CHỐT (Hướng A):
--   Hội viên mở app hiện mã QR động → lễ tân quét bằng máy quầy
--   → hệ thống tra Redis xem mã đã dùng chưa (chống quét lại)
--   → lấy persons.photo_key, sinh liên kết tạm từ MinIO
--   → màn hình quầy hiện ẢNH + tên + số ngày còn lại
--   → LỄ TÂN NHÌN, đối chiếu người thật, quyết định cho vào
-- =============================================================================

CREATE TABLE check_ins (
    id                  BIGSERIAL PRIMARY KEY,
    member_id           BIGINT      NOT NULL REFERENCES members(id),
    registration_id     BIGINT      REFERENCES registrations(id),

    checked_in_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    checked_out_at      TIMESTAMPTZ,

    -- TRUE = giờ ra do job đêm tự điền vì hội viên quên check-out.
    -- Cần cờ này để thống kê "thời lượng tập trung bình" biết đường loại ra,
    -- nếu không số liệu sẽ bị kéo theo giờ đóng cửa chứ không phải hành vi thật.
    auto_closed         BOOLEAN     NOT NULL DEFAULT FALSE,

    method              VARCHAR(20) NOT NULL,
    result              VARCHAR(20) NOT NULL,

    -- Lễ tân nào duyệt lượt vào này
    verified_by         BIGINT      REFERENCES users(id),

    -- ---- Sự cố (gộp từ bảng check_in_incidents) ----
    incident_type       VARCHAR(30),
    incident_note       TEXT,
    incident_handled_by BIGINT      REFERENCES users(id),

    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),

    -- Chỉ còn 2 cách vào: quét mã QR động, hoặc vé lẻ cho khách vãng lai
    CONSTRAINT chk_ci_method CHECK (method IN ('QR_DYNAMIC','DAY_PASS')),

    CONSTRAINT chk_ci_result CHECK (result IN
        ('ALLOWED','ALLOWED_OVERRIDE',
         'DENIED_EXPIRED','DENIED_FROZEN','DENIED_UNPAID',
         'DENIED_NOT_FOUND','DENIED_SUSPECT')),

    -- Còn 4 loại sự cố (bỏ FACE_MISMATCH vì đã bỏ nhận diện khuôn mặt)
    CONSTRAINT chk_ci_incident CHECK (incident_type IS NULL OR incident_type IN
        ('ANTI_PASSBACK','SUSPECTED_SHARING','REPLAY_ATTEMPT','EXPIRED_ATTEMPT')),

    CONSTRAINT chk_ci_times CHECK (
        checked_out_at IS NULL OR checked_out_at >= checked_in_at
    ),
    -- Lễ tân bỏ qua cảnh báo cho vào thì phải biết ai chịu trách nhiệm
    CONSTRAINT chk_ci_override CHECK (
        result <> 'ALLOWED_OVERRIDE' OR verified_by IS NOT NULL
    ),
    -- Lượt được phép vào phải gắn với hợp đồng nào đó, trừ khách vé lẻ
    CONSTRAINT chk_ci_registration CHECK (
        result NOT IN ('ALLOWED','ALLOWED_OVERRIDE')
        OR method = 'DAY_PASS'
        OR registration_id IS NOT NULL
    )
);

CREATE INDEX idx_ci_member ON check_ins (member_id, checked_in_at DESC);
CREATE INDEX idx_ci_time   ON check_ins (checked_in_at DESC);

-- Ai đang ở TRONG phòng tập. Cũng là dữ liệu để job đêm tìm lượt quên check-out.
CREATE INDEX idx_ci_inside ON check_ins (member_id)
    WHERE checked_out_at IS NULL;

-- Thống kê hiệu quả chống thất thoát: bao nhiêu lượt bị chặn, vì lý do gì
CREATE INDEX idx_ci_denied ON check_ins (result, checked_in_at DESC)
    WHERE result LIKE 'DENIED%';
CREATE INDEX idx_ci_incident ON check_ins (incident_type, checked_in_at DESC)
    WHERE incident_type IS NOT NULL;

CREATE TRIGGER trg_check_ins_updated_at BEFORE UPDATE ON check_ins
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

COMMENT ON TABLE check_ins IS
    'Ghi CA luot duoc phep VA luot bi tu choi. Luot bi tu choi la du lieu do hieu qua chong that thoat';
COMMENT ON COLUMN check_ins.auto_closed IS
    'TRUE = job dem tu dong gio ra vi hoi vien quen check-out. Loai khoi thong ke thoi luong tap';
