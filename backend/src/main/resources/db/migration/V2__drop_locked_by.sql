-- =============================================================================
-- V2 — Bỏ users.locked_by
-- Lý do: không có màn hình hay nghiệp vụ nào đọc lại "Admin nào đã khóa".
-- Thông tin này đã có đầy đủ trong audit_logs (actor_id + before/after_data).
-- =============================================================================

ALTER TABLE users DROP COLUMN locked_by;
