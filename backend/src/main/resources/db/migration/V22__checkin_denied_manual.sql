-- =============================================================================
-- V22 — them ket qua DENIED_MANUAL: le tan tu choi thu cong mot yeu cau tu
-- check-in tu hang doi (khong quet vao that), luon kem ly do (incident_note).
-- Thay the hanh vi cu: xoa yeu cau khoi hang doi ma khong ghi lai gi ca, khien
-- app hoi vien khong co cach nao phan biet "bi tu choi" voi cac lan check-in
-- that khac trong 5 phut gan nhat.
-- =============================================================================

ALTER TABLE check_ins DROP CONSTRAINT chk_ci_result;
ALTER TABLE check_ins ADD CONSTRAINT chk_ci_result CHECK (result IN
    ('ALLOWED','ALLOWED_OVERRIDE',
     'DENIED_EXPIRED','DENIED_FROZEN','DENIED_UNPAID',
     'DENIED_NOT_FOUND','DENIED_SUSPECT','DENIED_ALREADY_INSIDE','DENIED_MANUAL'));
