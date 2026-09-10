-- =============================================================================
-- V11 — BỔ SUNG BẢNG LƯƠNG (E4)
-- payroll_runs.paid_by  — ghi lại người xác nhận chi lương (audit trail)
-- payroll_items.manually_edited — đánh dấu dòng lương đã chỉnh tay, để cảnh báo
--   trước khi "Tính lương" lại làm mất chỉnh sửa thủ công
-- =============================================================================

ALTER TABLE payroll_runs
    ADD COLUMN IF NOT EXISTS paid_by BIGINT REFERENCES users(id);

ALTER TABLE payroll_items
    ADD COLUMN IF NOT EXISTS manually_edited BOOLEAN NOT NULL DEFAULT FALSE;

COMMENT ON COLUMN payroll_runs.paid_by IS 'Nguoi xac nhan da chi luong, de biet ai bam nut chi tra';
COMMENT ON COLUMN payroll_items.manually_edited IS 'Da tung duoc chinh thuong/phat thu cong, canh bao truoc khi tinh lai lam mat chinh sua';
