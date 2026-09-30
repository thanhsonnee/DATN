-- =============================================================================
-- V23 — Ghi nhận nhân viên đã tạo Lead (vd. lễ tân tiếp nhận khách vãng lai)
-- =============================================================================

ALTER TABLE leads ADD COLUMN created_by BIGINT REFERENCES users(id);

COMMENT ON COLUMN leads.created_by IS 'Nguoi dung (le tan/sale) da tao lead nay - null cho lead tu web form cong khai';
