-- =============================================================================
-- V4 — Chuỗi sinh mã nghiệp vụ
--
-- Dùng SEQUENCE của PostgreSQL thay vì đếm số dòng (SELECT count(*) + 1):
-- cách đếm dòng sẽ sinh MÃ TRÙNG khi hai người mua gói cùng lúc, còn sequence
-- bảo đảm mỗi lần gọi trả về một số khác nhau kể cả khi chạy song song.
-- =============================================================================

CREATE SEQUENCE member_code_seq       START 1;
CREATE SEQUENCE registration_code_seq START 1;

COMMENT ON SEQUENCE member_code_seq       IS 'Sinh phan so cua ma hoi vien: MB-000123';
COMMENT ON SEQUENCE registration_code_seq IS 'Sinh phan so cua ma hop dong: REG-2026-000451';
