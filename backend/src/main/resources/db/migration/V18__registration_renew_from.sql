-- =============================================================================
-- V18 — "Gia han goi tap" truoc day chi la link sang mua goi moi doc lap, khong
-- noi gi voi hop dong cu: mua som khi con han se bi CHONG NGAY, khong tinh tiep
-- noi ma bat dau tu hom nay. Them renew_from_id de hop dong moi biet no dang
-- "noi tiep" hop dong nao, tu do tinh duoc ngay bat dau dung = het han cu + 1.
-- =============================================================================

ALTER TABLE registrations ADD COLUMN renew_from_id BIGINT REFERENCES registrations(id);

COMMENT ON COLUMN registrations.renew_from_id IS
    'Hop dong duoc gia han tiep noi -- khi kich hoat, start_date se la max(hom nay, renew_from.end_date + 1) thay vi luon la hom nay';
