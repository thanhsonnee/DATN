-- =============================================================================
-- V16 — sua sot V15: gia tri moi 'DENIED_ALREADY_INSIDE' dai 21 ky tu,
-- vuot qua check_ins.result VARCHAR(20) hien tai.
-- =============================================================================

ALTER TABLE check_ins ALTER COLUMN result TYPE VARCHAR(30);
