-- =============================================================================
-- V6 — NHÓM 7: THANH TOÁN
-- invoices · payments · cash_shifts
--
-- Quyết định module D:
--   · invoices BỎ subtotal, discount_amount, tax_amount
--     → 1 hợp đồng = 1 hóa đơn, nên các số này chỉ là bản sao của
--       registrations.list_price / discount_amount. Giữ lại là hai nguồn sự thật
--       cho cùng một con số, lệch nhau thì không biết bên nào đúng.
--   · payments BỎ currency, idempotency_key, pos_terminal_id, pos_card_last4
-- =============================================================================


-- =============================================================================
-- invoices — hóa đơn phải thu
-- Chỉ giữ các số RIÊNG của hóa đơn: phải thu bao nhiêu, đã thu, còn nợ.
-- =============================================================================
CREATE TABLE invoices (
    id              BIGSERIAL PRIMARY KEY,
    invoice_no      VARCHAR(30)   NOT NULL,
    member_id       BIGINT        NOT NULL REFERENCES members(id),
    registration_id BIGINT        NOT NULL REFERENCES registrations(id),
    description     VARCHAR(255),

    -- Sao chép từ registrations.final_price ĐÚNG MỘT LẦN lúc xuất hóa đơn
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
    -- Đã thanh toán thì bắt buộc có mốc thời gian
    CONSTRAINT chk_inv_paid_at CHECK (status <> 'PAID' OR paid_at IS NOT NULL)
);

CREATE UNIQUE INDEX uq_inv_no  ON invoices (invoice_no) WHERE deleted_at IS NULL;
CREATE INDEX idx_inv_member    ON invoices (member_id, issued_at DESC);
CREATE INDEX idx_inv_reg       ON invoices (registration_id);
-- Danh sách công nợ: hóa đơn chưa thu đủ
CREATE INDEX idx_inv_unpaid    ON invoices (due_date)
    WHERE status IN ('UNPAID','PARTIALLY_PAID','OVERDUE');

CREATE TRIGGER trg_invoices_updated_at BEFORE UPDATE ON invoices
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

COMMENT ON COLUMN invoices.total_amount IS
    'Sao chep tu registrations.final_price. Khong luu lai list_price/discount vi da co o hop dong';
COMMENT ON COLUMN invoices.balance_due IS 'CSDL tu tinh: total_amount - paid_amount';


-- =============================================================================
-- cash_shifts — ca làm việc và đối soát tiền mặt của lễ tân
--
-- Bảng DUY NHẤT trong nhóm này nói về NHÂN VIÊN chứ không phải khách hàng.
-- Mọi khoản thu tiền mặt bắt buộc gắn với một ca, nhờ vậy cuối ca đếm được
-- và phát hiện ngay nếu thiếu hụt.
-- =============================================================================
CREATE TABLE cash_shifts (
    id                BIGSERIAL PRIMARY KEY,
    employee_id       BIGINT        NOT NULL REFERENCES employees(id),

    opened_at         TIMESTAMPTZ   NOT NULL DEFAULT now(),
    closed_at         TIMESTAMPTZ,

    opening_balance   NUMERIC(14,2) NOT NULL DEFAULT 0,
    -- Số tiền mặt lễ tân ĐẾM ĐƯỢC lúc đóng ca
    counted_cash      NUMERIC(14,2),
    -- Số hệ thống tính ra: đầu ca + tổng thu tiền mặt trong ca
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
    -- Lệch tiền BẮT BUỘC giải trình. Đây là điểm kiểm soát thất thoát chính.
    CONSTRAINT chk_cs_discrepancy CHECK (
        status <> 'DISCREPANCY' OR difference_reason IS NOT NULL
    )
);

-- Mỗi lễ tân chỉ được mở TỐI ĐA MỘT ca tại một thời điểm
CREATE UNIQUE INDEX uq_cs_open_per_employee ON cash_shifts (employee_id)
    WHERE status = 'OPEN';

CREATE INDEX idx_cs_employee ON cash_shifts (employee_id, opened_at DESC);

CREATE TRIGGER trg_cash_shifts_updated_at BEFORE UPDATE ON cash_shifts
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();


-- =============================================================================
-- payments — các khoản thu và hoàn tiền
-- Hoàn tiền ghi là bút toán ÂM (bỏ bảng refunds riêng), nhờ vậy cộng dồn
-- ra ngay số tiền phòng gym thực sự giữ lại.
-- =============================================================================
CREATE TABLE payments (
    id                    BIGSERIAL PRIMARY KEY,
    payment_no            VARCHAR(30)   NOT NULL,
    member_id             BIGINT        NOT NULL REFERENCES members(id),
    invoice_id            BIGINT        NOT NULL REFERENCES invoices(id),

    -- Bắt buộc với tiền mặt, để trống với chuyển khoản (không nằm trong két)
    cash_shift_id         BIGINT        REFERENCES cash_shifts(id),

    payment_type          VARCHAR(20)   NOT NULL DEFAULT 'PAYMENT',
    method                VARCHAR(20)   NOT NULL,
    amount                NUMERIC(14,2) NOT NULL,
    status                VARCHAR(20)   NOT NULL DEFAULT 'INITIATED',

    -- ---- Cổng thanh toán ----
    provider              VARCHAR(30),
    -- Mã giao dịch phía cổng trả về. UNIQUE để webhook gửi lại không ghi hai lần.
    provider_txn_id       VARCHAR(100),
    transfer_content      VARCHAR(255),
    bank_account          VARCHAR(50),
    raw_payload           JSONB,

    -- ---- Hoàn tiền ----
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

    -- Khoản thu dương, khoản hoàn ÂM — cộng dồn ra ngay số thực giữ lại
    CONSTRAINT chk_pay_amount_sign CHECK (
        (payment_type = 'PAYMENT' AND amount > 0) OR
        (payment_type = 'REFUND'  AND amount < 0)
    ),
    -- Hoàn tiền phải trỏ về khoản thu gốc, ghi lý do và có người duyệt
    CONSTRAINT chk_pay_refund CHECK (
        payment_type <> 'REFUND'
        OR (refund_of_payment_id IS NOT NULL AND refund_reason IS NOT NULL
            AND approved_by IS NOT NULL)
    ),
    -- Tiền mặt BẮT BUỘC gắn ca làm việc, nếu không sẽ có tiền lọt ngoài sổ
    CONSTRAINT chk_pay_cash_shift CHECK (
        method <> 'CASH' OR status <> 'SUCCEEDED' OR cash_shift_id IS NOT NULL
    ),
    CONSTRAINT chk_pay_succeeded CHECK (status <> 'SUCCEEDED' OR paid_at IS NOT NULL)
);

CREATE UNIQUE INDEX uq_pay_no ON payments (payment_no);
-- Chống ghi trùng khi cổng thanh toán gửi lại cùng một webhook
CREATE UNIQUE INDEX uq_pay_provider_txn ON payments (provider, provider_txn_id)
    WHERE provider_txn_id IS NOT NULL;

CREATE INDEX idx_pay_invoice ON payments (invoice_id);
CREATE INDEX idx_pay_member  ON payments (member_id, paid_at DESC);
CREATE INDEX idx_pay_shift   ON payments (cash_shift_id) WHERE cash_shift_id IS NOT NULL;

CREATE TRIGGER trg_payments_updated_at BEFORE UPDATE ON payments
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

COMMENT ON COLUMN payments.amount IS 'Duong = thu vao. AM = hoan tra. Cong don ra so thuc giu lai';
COMMENT ON COLUMN payments.cash_shift_id IS 'Bat buoc voi tien mat. Chuyen khoan de trong vi khong nam trong ket';
