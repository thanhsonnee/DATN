-- =============================================================================
-- V10 — NHÓM 9: BÁN HÀNG & CRM (PHÂN ĐOẠN F)
-- leads — khách hàng tiềm năng
-- =============================================================================

CREATE TABLE IF NOT EXISTS leads (
    id                         BIGSERIAL PRIMARY KEY,
    person_id                  BIGINT        NOT NULL REFERENCES persons(id),
    source                     VARCHAR(30)   NOT NULL,
    interested_membership_id   BIGINT        REFERENCES memberships(id),
    assigned_to                BIGINT        REFERENCES employees(id),
    stage                      VARCHAR(20)   NOT NULL DEFAULT 'NEW',
    lost_reason                VARCHAR(30),
    last_contact_at            TIMESTAMPTZ,
    last_contact_note          TEXT,
    next_follow_up             DATE,

    created_at                 TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at                 TIMESTAMPTZ   NOT NULL DEFAULT now(),
    deleted_at                 TIMESTAMPTZ,

    CONSTRAINT chk_lead_source CHECK (source IN
        ('WALK_IN','HOTLINE','WEB_FORM','REFERRAL','APP_SELF')),
    CONSTRAINT chk_lead_stage CHECK (stage IN
        ('NEW','CONTACTED','TRIAL_BOOKED','TRIAL_DONE','WON','LOST')),
    CONSTRAINT chk_lead_lost CHECK (
        stage <> 'LOST' OR lost_reason IS NOT NULL
    ),
    CONSTRAINT chk_lead_lost_reason CHECK (
        lost_reason IS NULL OR lost_reason IN ('PRICE','LOCATION','COMPETITOR','NOT_READY','NO_RESPONSE')
    )
);

CREATE INDEX IF NOT EXISTS idx_leads_person ON leads (person_id);
CREATE INDEX IF NOT EXISTS idx_leads_assigned_stage ON leads (assigned_to, stage) WHERE deleted_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_leads_stage ON leads (stage) WHERE deleted_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_leads_next_follow_up ON leads (next_follow_up)
    WHERE deleted_at IS NULL AND stage NOT IN ('WON', 'LOST');

CREATE TRIGGER trg_leads_updated_at BEFORE UPDATE ON leads
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

COMMENT ON COLUMN leads.person_id IS 'Tiep nhan lead la tao/tim persons ngay de chong trung so dien thoai';
COMMENT ON COLUMN leads.last_contact_at IS 'Lien he gan nhat (thay the bang lead_activities)';
COMMENT ON COLUMN leads.lost_reason IS 'Bat buoc khi stage = LOST de thong ke ly do mat khach';
