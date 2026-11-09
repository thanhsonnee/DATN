-- V26 — doi cach goi "quet ra" thanh "Checkout" trong mo ta tham so he thong
UPDATE system_settings
SET description = 'Quet lai trong khoang thoi gian nay (phut) sau khi da Checkout bi coi la bat thuong'
WHERE setting_key = 'checkin.duplicate-scan-window-minutes';

UPDATE system_settings
SET description = 'Gio dong cua phong tap, dung cho job dem tu dong dong cac luot quen Checkout'
WHERE setting_key = 'gym.closing-time';
