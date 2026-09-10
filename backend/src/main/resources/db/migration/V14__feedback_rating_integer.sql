-- =============================================================================
-- V14 — sua sot V12: entity Feedback.rating la Integer, khong phai Short,
-- nen Hibernate mong cot kieu INTEGER chu khong phai SMALLINT.
-- =============================================================================

ALTER TABLE feedbacks ALTER COLUMN rating TYPE INTEGER;
