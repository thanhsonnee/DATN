-- =============================================================================
-- V27 — BỎ 5 BẢNG KHÔNG DÙNG ĐẾN
--
--   · workout_plans, body_metrics  (V24) — giáo án tập luyện và chỉ số cơ thể:
--       backend có API nhưng web/mobile không gọi, nên bỏ cả tính năng.
--   · member_trainers (V3) — không có code nào ghi; việc "PT nào phụ trách"
--       đã nằm ở registrations.assigned_trainer_id và pt_sessions.trainer_id.
--   · audit_logs, password_reset_tokens (V1) — chưa từng có code ghi/đọc.
--       Đặt lại mật khẩu do Admin làm trực tiếp, không qua token.
--
-- Không có bảng nào khác tham chiếu tới 5 bảng này (không có khóa ngoại trỏ vào).
-- =============================================================================

DROP TABLE IF EXISTS workout_plans;
DROP TABLE IF EXISTS body_metrics;
DROP TABLE IF EXISTS member_trainers;
DROP TABLE IF EXISTS audit_logs;
DROP TABLE IF EXISTS password_reset_tokens;
