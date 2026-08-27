-- =============================================================================
-- V8 — Chuỗi sinh mã hóa đơn và mã khoản thu
-- Dùng SEQUENCE thay vì đếm số dòng, để hai người thu tiền cùng lúc không
-- sinh ra mã trùng nhau.
-- =============================================================================

CREATE SEQUENCE invoice_no_seq START 1;
CREATE SEQUENCE payment_no_seq START 1;

COMMENT ON SEQUENCE invoice_no_seq IS 'Sinh phan so cua ma hoa don: INV-2026-000451';
COMMENT ON SEQUENCE payment_no_seq IS 'Sinh phan so cua ma khoan thu: PAY-2026-000988';
