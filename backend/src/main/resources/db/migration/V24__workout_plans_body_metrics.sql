-- =============================================================================
-- V24 — NGHIỆP VỤ G RÚT GỌN: GIÁO ÁN TẬP LUYỆN & CHỈ SỐ CƠ THỂ
--
-- Đơn giản hóa so với kế hoạch gốc (db/32_bang.md nhóm 10):
--   · KHÔNG tách bảng exercises/workout_plan_items — PT viết giáo án bằng TEXT
--     TỰ DO, không tách bảng bài tập/sets/reps.
--   · KHÔNG OCR/AI sinh nháp cho chỉ số cơ thể — hội viên tự gõ tay cân nặng/
--     chiều cao, hệ thống tự tính BMI/BMR/WHR ngay trong service khi lưu.
-- =============================================================================

CREATE TABLE workout_plans (
    id               BIGSERIAL PRIMARY KEY,
    registration_id  BIGINT      NOT NULL REFERENCES registrations(id),
    trainer_id       BIGINT      NOT NULL REFERENCES employees(id),

    title            VARCHAR(200) NOT NULL,
    content          TEXT         NOT NULL,   -- nội dung giáo án, PT viết tự do

    -- Mảng ngày (ISO yyyy-MM-dd) hội viên đã đánh dấu tập, vd ["2026-01-10"].
    -- Giữ nguyên khi PT sửa title/content — sửa nội dung không xóa lịch sử đã tập.
    completed_dates  JSONB       NOT NULL DEFAULT '[]',

    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_workout_plans_registration ON workout_plans (registration_id);

CREATE TRIGGER trg_workout_plans_updated_at BEFORE UPDATE ON workout_plans
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

COMMENT ON COLUMN workout_plans.content IS
    'Text tu do PT go tay. Khong tach bang bai tap/sets/reps (quyet dinh don gian hoa nghiep vu G)';


CREATE TABLE body_metrics (
    id          BIGSERIAL PRIMARY KEY,
    member_id   BIGINT       NOT NULL REFERENCES members(id),

    weight_kg   NUMERIC(5,2) NOT NULL,
    height_cm   NUMERIC(5,2) NOT NULL,
    waist_cm    NUMERIC(5,2),
    hip_cm      NUMERIC(5,2),

    -- Ba cột dưới do SERVICE tự tính lúc lưu, không phải hội viên tự nhập:
    bmi         NUMERIC(5,2) NOT NULL,  -- cân nặng(kg) / chiều cao(m)², ngưỡng châu Á
    bmr_kcal    INTEGER,                -- Mifflin-St Jeor, NULL nếu thiếu gender/birthday
    whr         NUMERIC(4,3),           -- vòng eo/vòng hông, NULL nếu thiếu một trong hai

    measured_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT chk_bm_weight CHECK (weight_kg BETWEEN 20 AND 300),
    CONSTRAINT chk_bm_height CHECK (height_cm BETWEEN 80 AND 250)
);

CREATE INDEX idx_body_metrics_member ON body_metrics (member_id, measured_at);

COMMENT ON TABLE body_metrics IS
    'Chi so co the hoi vien tu nhap. BMI/BMR/WHR tinh san o service, FE khong tu tinh';
