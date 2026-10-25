-- =============================================================================
-- V25 — Đối soát doanh thu theo ngày (E6)
--
-- `payments.reconciled_at` đã có sẵn từ V6 nhưng chưa từng được dùng — thiếu
-- đúng 1 cột để biết AI xác nhận. Bổ sung `reconciled_by` để Kế toán xác nhận
-- gộp 1 lần/ngày: "đã nhận đủ tiền hôm nay từ các khoản lễ tân/sale thu" —
-- áp dụng mọi hình thức thanh toán, không riêng tiền mặt (khác cơ chế đối soát
-- ca CASH đã có ở cash_shifts, vốn chỉ có ý nghĩa với tiền mặt trong két).
-- =============================================================================

ALTER TABLE payments ADD COLUMN reconciled_by BIGINT REFERENCES users(id);

CREATE INDEX idx_payments_reconcile_lookup ON payments (status, paid_at)
    WHERE status = 'SUCCEEDED';
