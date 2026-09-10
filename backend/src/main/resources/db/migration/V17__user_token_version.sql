-- =============================================================================
-- V17 — thu hoi refresh token that su o server.
--
-- JWT thuan khong "nho" da phat hanh token nao nen khong revoke duoc tung
-- token cu the. Thay vi them han 1 bang refresh_tokens (phai luu hash tung
-- token, don dep token het han...), dung cach re hon: 1 so phien ban tren
-- users. Refresh token nhung so phien ban luc cap; server tang so nay len la
-- MOI refresh token cu (moi thiet bi, moi tab) bi tu choi ngay lap tuc.
-- =============================================================================

ALTER TABLE users ADD COLUMN token_version INTEGER NOT NULL DEFAULT 0;

COMMENT ON COLUMN users.token_version IS
    'Tang len khi dang xuat that (POST /auth/logout) hoac doi mat khau -- refresh token cu nhung version thap hon bi tu choi ngay o lan /refresh ke tiep';
