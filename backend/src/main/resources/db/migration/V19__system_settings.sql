-- =============================================================================
-- V19 -- system_settings: tham so nghiep vu chinh duoc qua API thay vi hardcode.
--
-- Cac gia tri nay truoc day la hang so trong code (private static final), moi
-- lan doi phai sua code roi deploy lai. Tu bang nay, Admin chinh truc tiep qua
-- API; gia tri luu duoi dang chuoi, tang dan kieu du lieu (Integer, BigDecimal,
-- LocalTime...) o tang service, co fallback ve gia tri mac dinh neu thieu hoac
-- sai dinh dang.
-- =============================================================================

CREATE TABLE system_settings (
    setting_key   VARCHAR(100) PRIMARY KEY,
    setting_value VARCHAR(500) NOT NULL,
    description   VARCHAR(255),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by    BIGINT REFERENCES users(id)
);

INSERT INTO system_settings (setting_key, setting_value, description) VALUES
    ('pt.cancel.min-hours-before', '4',
        'Huy buoi tap sat gio hen hon so gio nay thi tinh la huy muon, mat buoi'),
    ('security.login.max-failed-attempts', '5',
        'Sai mat khau qua so lan nay thi khoa tam tai khoan'),
    ('security.login.lockout-minutes', '15',
        'So phut khoa tam tai khoan sau khi sai mat khau qua so lan cho phep'),
    ('membership.freeze.min-advance-days', '3',
        'Phai bao truoc it nhat so ngay nay moi duoc bao luu goi tap'),
    ('checkin.duplicate-scan-window-minutes', '30',
        'Quet lai trong khoang thoi gian nay (phut) sau khi da quet ra bi coi la bat thuong'),
    ('gym.closing-time', '22:30',
        'Gio dong cua phong tap, dung cho job dem tu dong dong cac luot quen quet ra'),
    ('payroll.pt-commission-per-session', '100000',
        'Hoa hong PT tinh theo moi buoi COMPLETED trong thang (VND)'),
    ('payroll.sales-commission-rate', '0.05',
        'Ty le hoa hong Sales/Le tan tren tong doanh so thu tien trong thang');

COMMENT ON TABLE system_settings IS
    'Tham so nghiep vu chinh duoc qua API (khong can deploy lai) -- xem SystemSettingService';
