-- =============================================================================
-- V15 — sua loi: quet vao lien tuc nhieu lan khi hoi vien chua quet ra bi
-- coi la "bat thuong can luu y" nhung VAN CHO VAO — sinh du lieu trung trong
-- "dang o trong phong tap". Them ket qua DENIED_ALREADY_INSIDE de CHAN han.
-- =============================================================================

ALTER TABLE check_ins DROP CONSTRAINT chk_ci_result;
ALTER TABLE check_ins ADD CONSTRAINT chk_ci_result CHECK (result IN
    ('ALLOWED','ALLOWED_OVERRIDE',
     'DENIED_EXPIRED','DENIED_FROZEN','DENIED_UNPAID',
     'DENIED_NOT_FOUND','DENIED_SUSPECT','DENIED_ALREADY_INSIDE'));
