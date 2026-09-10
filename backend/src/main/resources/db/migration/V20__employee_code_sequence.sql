-- =============================================================================
-- V20 -- Chuoi sinh ma nhan vien, phuc vu tinh nang Admin tao tai khoan nhan
-- vien (PT/Sale/Le tan/Ke toan khong tu dang ky duoc).
--
-- START tu 100 de khong dung voi cac ma nhan vien co dinh da seed san trong
-- DemoAccountSeeder (EM-001, EM-005, EM-012, EM-018, EM-021).
-- =============================================================================

CREATE SEQUENCE employee_code_seq START 100;

COMMENT ON SEQUENCE employee_code_seq IS 'Sinh phan so cua ma nhan vien: EM-100';
