-- =============================================================================
-- V13 — sua sot V12: Feedback extends BaseEntity (co deletedAt) nhung bang
-- feedbacks lai thieu cot deleted_at. Them cot rieng thay vi sua V12 da apply.
-- =============================================================================

ALTER TABLE feedbacks ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMPTZ;
