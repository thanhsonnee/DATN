-- =============================================================================
-- V9 — NHÓM TÀI CHÍNH NÂNG CAO (E3, E4, E5)
-- revenue_schedules · payroll_runs · payroll_items · expenses
-- =============================================================================

-- Sequence sinh mã chi phí: EXP-2026-000001
CREATE SEQUENCE IF NOT EXISTS expense_no_seq START 1;
COMMENT ON SEQUENCE expense_no_seq IS 'Sinh phan so cua ma chi phi: EXP-2026-000001';

-- Sequence sinh mã bảng lương: PAY-2026-08-001
CREATE SEQUENCE IF NOT EXISTS payroll_code_seq START 1;
COMMENT ON SEQUENCE payroll_code_seq IS 'Sinh phan so cua ma bang luong: PAY-2026-08-001';


-- =============================================================================
-- E3: revenue_schedules — Lịch ghi nhận doanh thu dồn tích (Accrual Accounting)
-- Phân bổ giá trị hợp đồng đều theo các tháng/kỳ trong thời hạn hợp đồng.
-- =============================================================================
CREATE TABLE IF NOT EXISTS revenue_schedules (
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

CREATE INDEX IF NOT EXISTS idx_rs_registration ON revenue_schedules (registration_id);
CREATE INDEX IF NOT EXISTS idx_rs_schedule_date ON revenue_schedules (schedule_date);
CREATE INDEX IF NOT EXISTS idx_rs_pending ON revenue_schedules (schedule_date) WHERE status = 'PENDING';

CREATE TRIGGER trg_revenue_schedules_updated_at BEFORE UPDATE ON revenue_schedules
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();


-- =============================================================================
-- E4: payroll_runs — Đợt chạy lương hàng tháng
-- =============================================================================
CREATE TABLE IF NOT EXISTS payroll_runs (
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
    note               TEXT,

    created_at         TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ   NOT NULL DEFAULT now(),

    CONSTRAINT chk_pr_status CHECK (status IN ('DRAFT','APPROVED','PAID','CANCELLED')),
    CONSTRAINT chk_pr_period CHECK (period_month BETWEEN 1 AND 12 AND period_year >= 2020)
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_pr_code ON payroll_runs (payroll_code);
CREATE UNIQUE INDEX IF NOT EXISTS uq_pr_period ON payroll_runs (period_month, period_year)
    WHERE status <> 'CANCELLED';

CREATE TRIGGER trg_payroll_runs_updated_at BEFORE UPDATE ON payroll_runs
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();


-- =============================================================================
-- E4: payroll_items — Chi tiết lương từng nhân viên trong đợt chạy lương
-- =============================================================================
CREATE TABLE IF NOT EXISTS payroll_items (
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
    note                  TEXT,

    created_at            TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ   NOT NULL DEFAULT now(),

    CONSTRAINT chk_pi_amounts CHECK (
        base_salary >= 0 AND pt_commission >= 0 AND sales_commission >= 0 AND
        bonus_amount >= 0 AND deduction_amount >= 0
    )
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_pi_emp_run ON payroll_items (payroll_run_id, employee_id);
CREATE INDEX IF NOT EXISTS idx_pi_employee ON payroll_items (employee_id);

CREATE TRIGGER trg_payroll_items_updated_at BEFORE UPDATE ON payroll_items
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();


-- =============================================================================
-- E5: expenses — Chi phí vận hành phòng gym
-- =============================================================================
CREATE TABLE IF NOT EXISTS expenses (
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

CREATE UNIQUE INDEX IF NOT EXISTS uq_exp_no ON expenses (expense_no) WHERE deleted_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_exp_spent_at ON expenses (spent_at DESC) WHERE deleted_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_exp_category ON expenses (category) WHERE deleted_at IS NULL;

CREATE TRIGGER trg_expenses_updated_at BEFORE UPDATE ON expenses
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
