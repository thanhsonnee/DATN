-- =============================================================================
-- V12 — NHÓM 8: PHẢN HỒI & SỰ CỐ THIẾT BỊ (PHÂN ĐOẠN H)
-- equipment — thiết bị phòng tập (bản rút gọn, không tách types/items)
-- feedbacks — hội viên đánh giá PT / báo hỏng thiết bị / góp ý chung
-- =============================================================================

CREATE TABLE IF NOT EXISTS equipment (
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

CREATE INDEX IF NOT EXISTS idx_equipment_status ON equipment (status) WHERE deleted_at IS NULL;

CREATE TRIGGER trg_equipment_updated_at BEFORE UPDATE ON equipment
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

COMMENT ON TABLE equipment IS
    'Ban rut gon: 1 dong = 1 loai/khu thiet bi, khong tach equipment_types/equipment_items nhu thiet ke day du';


CREATE TABLE IF NOT EXISTS feedbacks (
    id                BIGSERIAL PRIMARY KEY,
    member_id         BIGINT        NOT NULL REFERENCES members(id),
    feedback_type     VARCHAR(20)   NOT NULL,
    trainer_id        BIGINT        REFERENCES employees(id),
    equipment_id      BIGINT        REFERENCES equipment(id),
    rating            SMALLINT,
    description       TEXT          NOT NULL,
    status            VARCHAR(20)   NOT NULL DEFAULT 'OPEN',
    is_urgent         BOOLEAN       NOT NULL DEFAULT FALSE,
    repair_cost       NUMERIC(14,2),
    resolution_note   VARCHAR(500),
    resolved_by       BIGINT        REFERENCES users(id),
    resolved_at       TIMESTAMPTZ,

    created_at        TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ   NOT NULL DEFAULT now(),

    CONSTRAINT chk_fb_type CHECK (feedback_type IN
        ('TRAINER','FACILITY','HYGIENE','SERVICE','GENERAL')),
    CONSTRAINT chk_fb_status CHECK (status IN
        ('OPEN','IN_PROGRESS','WAITING_PARTS','RESOLVED','CLOSED')),
    CONSTRAINT chk_fb_rating CHECK (rating IS NULL OR rating BETWEEN 1 AND 5),
    CONSTRAINT chk_fb_trainer_required CHECK (feedback_type <> 'TRAINER' OR trainer_id IS NOT NULL),
    CONSTRAINT chk_fb_equipment_required CHECK (feedback_type <> 'FACILITY' OR equipment_id IS NOT NULL),
    CONSTRAINT chk_fb_repair_cost CHECK (repair_cost IS NULL OR repair_cost >= 0)
);

CREATE INDEX IF NOT EXISTS idx_feedback_member    ON feedbacks (member_id);
CREATE INDEX IF NOT EXISTS idx_feedback_trainer    ON feedbacks (trainer_id) WHERE trainer_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_feedback_equipment  ON feedbacks (equipment_id) WHERE equipment_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_feedback_open       ON feedbacks (created_at)
    WHERE status IN ('OPEN','IN_PROGRESS','WAITING_PARTS');

CREATE TRIGGER trg_feedbacks_updated_at BEFORE UPDATE ON feedbacks
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

COMMENT ON COLUMN feedbacks.is_urgent IS
    'TRAINER voi rating<=2 -> tu dong bat, de hang doi Admin loc uu tien (thay cho bang task rieng chua build)';
COMMENT ON COLUMN feedbacks.repair_cost IS
    'Chi nhap khi dong FACILITY va da sua xong — tu dong sinh 1 dong expenses(EQUIPMENT_MAINTENANCE)';
