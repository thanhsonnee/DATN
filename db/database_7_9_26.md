# TỪ ĐIỂN DỮ LIỆU — HIỆN TRẠNG THỰC TẾ (07/09/2026)

> **Nguồn duy nhất:** 19 file migration Flyway đã áp dụng (`V1__identity.sql` → `V19__system_settings.sql`).
> `application.yml` đặt `ddl-auto: validate` — **Flyway là nguồn sự thật duy nhất của schema**, không suy từ entity Java. Vì vậy tài liệu này đọc thẳng từ SQL migration, không đọc từ `db/32_bang.md` (đó là bản **kế hoạch**, không phải hiện trạng).
>
> **Số bảng đang thực sự chạy: 23** — đã bao gồm `system_settings` (tạo ở `V19`, **không phải bảng cần thêm mới**).
> **Nếu bổ sung 4 bảng Nhóm G (Bài tập & Chỉ số cơ thể, đang đề xuất, chưa có migration) → tổng 27 bảng.** Đã bỏ `workout_logs` khỏi đề xuất (yêu cầu hội viên tự gõ tay `sets_done`/`reps_done`/`weight_kg` sau mỗi bài — không tiện lợi), xem [Nhóm 10](#nhóm-10--bài-tập--chỉ-số-cơ-thể-đề-xuất-chưa-triển-khai).

## Vì sao 23, không phải 32 như kế hoạch gốc

| Bảng trong kế hoạch 32 bảng | Hiện trạng |
|---|---|
| `registration_freezes` | **Không tồn tại như bảng riêng.** Từ `V3` đã gộp thẳng vào `registrations` (cột `freeze_*`) — quyết định 15: mỗi hợp đồng chỉ bảo lưu đúng 1 lần |
| `class_sessions`, `class_bookings` (Nhóm 5 — Lớp học nhóm) | **Chưa triển khai** — không có migration nào tạo 2 bảng này |
| `revenue_recognition_entries` | **Không tồn tại riêng.** `V9` chỉ tạo `revenue_schedules`, bảng này gánh luôn vai trò ghi nhận doanh thu |
| `exercises`, `workout_plans`, `workout_plan_items`, `workout_logs`, `body_metrics` (Nhóm 10) | **Chưa triển khai** — 5 bảng trong kế hoạch gốc. Nhóm G đang đề xuất hiện chỉ còn **4 bảng** (đã bỏ `workout_logs` — xem [Nhóm 10](#nhóm-10--bài-tập--chỉ-số-cơ-thể-đề-xuất-chưa-triển-khai)) |

`32 − 1 (freeze) − 2 (lớp nhóm) − 1 (revenue_recognition_entries) − 5 (nhóm G theo kế hoạch gốc) = 23`. ✅ khớp con số bạn đưa ra.

---

## Quy ước chung *(áp dụng mọi bảng, không lặp lại bên dưới)*

| Cột | Kiểu | Ý nghĩa |
|---|---|---|
| `id` | `BIGSERIAL` | Khóa chính tự tăng |
| `created_at` | `TIMESTAMPTZ NOT NULL DEFAULT now()` | Thời điểm tạo |
| `updated_at` | `TIMESTAMPTZ NOT NULL DEFAULT now()` | Trigger `set_updated_at()` tự cập nhật mỗi lần `UPDATE` |
| `deleted_at` | `TIMESTAMPTZ` | Xóa mềm, `NULL` = còn dùng — **không phải bảng nào cũng có cột này**, xem ghi chú từng bảng |

---

## Mục lục

| Nhóm | Bảng | Trạng thái |
|---|---|---|
| [1. Danh tính (6)](#nhóm-1--danh-tính) | `persons` · `users` · `password_reset_tokens` · `members` · `employees` · `audit_logs` | ✅ Đã có |
| [2. Gói & Hợp đồng (3)](#nhóm-2--gói-tập--hợp-đồng) | `memberships` · `registrations` · `member_trainers` | ✅ Đã có |
| [3. Check-in (1)](#nhóm-3--check-in) | `check_ins` | ✅ Đã có |
| [4. Buổi tập PT & Sổ cái (2)](#nhóm-4--buổi-tập-pt--sổ-cái) | `pt_sessions` · `session_credit_ledger` | ✅ Đã có |
| 5. Lớp học nhóm (0/2) | `class_sessions` · `class_bookings` | ❌ Chưa triển khai |
| [6. Thiết bị (1)](#nhóm-6--thiết-bị) | `equipment` | ✅ Đã có |
| [7. Thanh toán (3)](#nhóm-7--thanh-toán) | `invoices` · `payments` · `cash_shifts` | ✅ Đã có |
| [8. Tài chính & Lương (4)](#nhóm-8--tài-chính--lương) | `revenue_schedules` · `expenses` · `payroll_runs` · `payroll_items` | ✅ Đã có |
| [9. Bán hàng / CRM (1)](#nhóm-9--bán-hàng--crm) | `leads` | ✅ Đã có |
| [10. Bài tập & Chỉ số cơ thể (0/4)](#nhóm-10--bài-tập--chỉ-số-cơ-thể-đề-xuất-chưa-triển-khai) | `exercises` · `workout_plans` · `workout_plan_items` · `body_metrics` | ❌ Đề xuất (module G) |
| [11. Phản hồi (1)](#nhóm-11--phản-hồi) | `feedbacks` | ✅ Đã có |
| [12. Hệ thống (1)](#nhóm-12--hệ-thống) | `system_settings` | ✅ Đã có |

**23 bảng ✅ + 4 bảng ❌ đề xuất = 27 bảng nếu làm module G.**

---
---

# NHÓM 1 — DANH TÍNH

## `persons` *(V1)*

| Cột | Kiểu | Ghi chú |
|---|---|---|
| `full_name` | `VARCHAR(150) NOT NULL` | |
| `gender` | `VARCHAR(10)` | `CHECK IN ('MALE','FEMALE','OTHER')`, cho phép `NULL` |
| `birthday` | `DATE` | `CHECK < CURRENT_DATE` |
| `national_id` | `VARCHAR(20)` | UNIQUE (partial index, chỉ tính bản ghi chưa xóa mềm) |
| `phone` | `VARCHAR(20) NOT NULL` | UNIQUE (partial), `CHECK ~ '^0[35789][0-9]{8}$'` |
| `email` | `VARCHAR(150)` | UNIQUE (partial) |
| `address` | `VARCHAR(255)` | |
| `photo_key` | `VARCHAR(500)` | Key MinIO bucket private, không phải URL công khai |
| `card_uid` | `VARCHAR(64)` | UID thẻ RFID, UNIQUE (partial) — **cột tồn tại nhưng nghiệp vụ RFID đã bị bỏ ở `check_ins` (V7)**, hiện không có luồng nào ghi vào |
| `card_issued_at` | `TIMESTAMPTZ` | |
| `qr_secret_enc` | `BYTEA` | Khóa sinh QR động |
| `emergency_contact_name` | `VARCHAR(150)` | |
| `emergency_contact_phone` | `VARCHAR(20)` | |

Có `deleted_at`.

## `users` *(V1, sửa V2 + V17)*

| Cột | Kiểu | Ghi chú |
|---|---|---|
| `person_id` | `BIGINT NOT NULL` → `persons.id` | |
| `username` | `VARCHAR(100) NOT NULL` | UNIQUE (partial) |
| `password_hash` | `VARCHAR(255) NOT NULL` | |
| `primary_role` | `VARCHAR(20) NOT NULL` | `CHECK IN ('ADMIN','MEMBER','TRAINER','SALE','RECEPTIONIST','ACCOUNTANT')` |
| `status` | `VARCHAR(20) NOT NULL DEFAULT 'ACTIVE'` | `CHECK IN ('ACTIVE','LOCKED')` |
| `locked_reason` | `VARCHAR(255)` | Bắt buộc khi `status='LOCKED'` |
| `locked_until` | `DATE` | |
| ~~`locked_by`~~ | — | **Đã bị xóa ở `V2`** — không có màn hình nào đọc lại, đã có `audit_logs` |
| `failed_attempts` | `SMALLINT NOT NULL DEFAULT 0` | |
| `auto_locked_until` | `TIMESTAMPTZ` | Tách riêng khỏi `locked_until` để job tự mở khóa không phá khóa thủ công |
| `email_verified_at` | `TIMESTAMPTZ` | |
| `last_login_at` | `TIMESTAMPTZ` | |
| `token_version` | `INTEGER NOT NULL DEFAULT 0` | **Thêm ở `V17`** — tăng lên khi logout thật/đổi mật khẩu để thu hồi mọi refresh token cũ, thay vì phải có bảng `refresh_tokens` riêng |

Có `deleted_at`.

## `password_reset_tokens` *(V1)*

| Cột | Kiểu | Ghi chú |
|---|---|---|
| `user_id` | `BIGINT NOT NULL` → `users.id` | |
| `token_hash` | `VARCHAR(128) NOT NULL UNIQUE` | SHA-256 của token, không lưu token thô |
| `expires_at` | `TIMESTAMPTZ NOT NULL` | |
| `used_at` | `TIMESTAMPTZ` | `NULL` = chưa dùng |
| `requested_ip` | `INET` | |

**Không có `updated_at`/`deleted_at`** — bản ghi bất biến, không sửa không xóa mềm.

## `members` *(V1)*

| Cột | Kiểu | Ghi chú |
|---|---|---|
| `person_id` | `BIGINT NOT NULL UNIQUE` → `persons.id` | |
| `member_code` | `VARCHAR(20) NOT NULL` | UNIQUE (partial), sinh từ `member_code_seq` (V4) |
| `join_date` | `DATE NOT NULL` | Ngày chốt mua gói đầu tiên |
| `source` | `VARCHAR(30)` | `CHECK IN ('WALK_IN','HOTLINE','WEB_FORM','REFERRAL','APP_SELF')` |
| `referred_by` | `BIGINT` → `members.id` | |
| `health_note` | `TEXT` | |
| `goal` | `VARCHAR(30)` | `CHECK IN ('LOSE_FAT','GAIN_MUSCLE','ENDURANCE','HEALTH')` |
| `status` | `VARCHAR(20) NOT NULL DEFAULT 'ACTIVE'` | Chỉ 2 giá trị: `ACTIVE` \| `BLACKLISTED` — **không phản ánh còn/hết gói** |
| `last_visit_at` | `TIMESTAMPTZ` | |

Có `deleted_at`.

## `employees` *(V1)*

| Cột | Kiểu | Ghi chú |
|---|---|---|
| `person_id` | `BIGINT NOT NULL UNIQUE` → `persons.id` | |
| `employee_code` | `VARCHAR(20) NOT NULL` | UNIQUE (partial) |
| `department` | `VARCHAR(30) NOT NULL` | `CHECK IN ('TRAINING','SALES','FRONT_DESK','ACCOUNTING')` |
| `position` | `VARCHAR(80)` | |
| `employment_type` | `VARCHAR(20)` | `CHECK IN ('FULL_TIME','PART_TIME','FREELANCE')` |
| `base_salary` | `NUMERIC(14,2)` | |
| `start_date` | `DATE NOT NULL` | |
| `end_date` | `DATE` | |
| `status` | `VARCHAR(20) NOT NULL DEFAULT 'ACTIVE'` | `CHECK IN ('ACTIVE','ON_LEAVE','RESIGNED')` |
| `level` | `VARCHAR(20)` | Chỉ có nghĩa khi `department='TRAINING'`; `CHECK IN ('JUNIOR','SENIOR','MASTER')` |
| `specialties` | `JSONB` | |
| `bio` | `TEXT` | |
| `max_members` | `INTEGER` | |
| `rating_avg` | `NUMERIC(3,2)` | `CHECK BETWEEN 1.00 AND 5.00` |
| `rating_count` | `INTEGER NOT NULL DEFAULT 0` | |

Có `deleted_at`.

## `audit_logs` *(V1)*

| Cột | Kiểu | Ghi chú |
|---|---|---|
| `actor_id` | `BIGINT` → `users.id` | |
| `action` | `VARCHAR(50) NOT NULL` | `CHECK IN ('CREATE','UPDATE','DELETE','APPROVE','REJECT','LOGIN','LOGOUT','EXPORT','ANONYMIZE')` |
| `entity_type` | `VARCHAR(60) NOT NULL` | Tên bảng, không FK cứng (đa hình) |
| `entity_id` | `BIGINT` | |
| `before_data` / `after_data` | `JSONB` | Phải lọc `password_hash` trước khi ghi |
| `reason` | `TEXT` | |
| `ip_address` | `INET` | |
| `user_agent` | `VARCHAR(500)` | |

Không có `updated_at`/`deleted_at`.

---
---

# NHÓM 2 — GÓI TẬP & HỢP ĐỒNG

## `memberships` *(V3)*

| Cột | Kiểu | Ghi chú |
|---|---|---|
| `code` | `VARCHAR(30) NOT NULL` | UNIQUE (partial) |
| `name` | `VARCHAR(150) NOT NULL` | |
| `package_type` | `VARCHAR(20) NOT NULL` | `CHECK IN ('TIME_BASED','SESSION_BASED','HYBRID','DAY_PASS')` |
| `duration_days` | `INTEGER` | Bắt buộc khi `TIME_BASED`/`HYBRID`/`DAY_PASS` |
| `session_count` | `INTEGER` | Bắt buộc khi `SESSION_BASED`/`HYBRID` |
| `price` | `NUMERIC(14,2) NOT NULL` | `CHECK >= 0` |
| `includes_trainer` | `BOOLEAN NOT NULL DEFAULT FALSE` | |
| `pt_value_ratio` | `NUMERIC(4,3)` | Bắt buộc `0 < x < 1` khi `HYBRID` |
| `area_codes` | `JSONB` | |
| `max_freeze_days` | `INTEGER NOT NULL DEFAULT 0` | |
| `is_refundable` | `BOOLEAN NOT NULL DEFAULT FALSE` | |
| `description` | `TEXT` | |
| `display_order` | `INTEGER NOT NULL DEFAULT 0` | |
| `status` | `VARCHAR(20) NOT NULL DEFAULT 'ACTIVE'` | `CHECK IN ('ACTIVE','ARCHIVED')` |

> ⚠️ **Khác kế hoạch:** cột `max_freeze_times` (giới hạn *số lần* bảo lưu/năm) **không tồn tại trong bảng thật** — chỉ có `max_freeze_days` (giới hạn *số ngày*). Giới hạn "tối đa 1 lần/hợp đồng" hiện được ép cứng bằng cấu trúc dữ liệu: `registrations` chỉ có đúng 1 bộ cột `freeze_*`, không có bảng con cho phép nhiều lần.

Có `deleted_at`.

## `registrations` *(V3, sửa V18)* — bảng trung tâm

Gánh 2 vai trò: khuyến mãi (`discount_*`) và **bảo lưu tối đa 1 lần/hợp đồng** (`freeze_*`, thay cho bảng `registration_freezes` trong kế hoạch gốc).

| Cột | Kiểu | Ghi chú |
|---|---|---|
| `registration_code` | `VARCHAR(30) NOT NULL` | UNIQUE (partial) |
| `member_id` | `BIGINT NOT NULL` → `members.id` | |
| `membership_id` | `BIGINT NOT NULL` → `memberships.id` | |
| `sold_by` | `BIGINT` → `employees.id` | |
| `assigned_trainer_id` | `BIGINT` → `employees.id` | |
| **— Snapshot thương mại (copy lúc ký) —** | | |
| `package_type` | `VARCHAR(20) NOT NULL` | |
| `duration_days` / `sessions_total` | `INTEGER` | |
| `list_price` | `NUMERIC(14,2) NOT NULL` | |
| `discount_amount` | `NUMERIC(14,2) NOT NULL DEFAULT 0` | |
| `discount_reason` | `VARCHAR(255)` | Bắt buộc nếu `discount_amount > 0` |
| `discount_approved_by` | `BIGINT` → `users.id` | |
| `final_price` | `NUMERIC(14,2) NOT NULL` | `CHECK = list_price - discount_amount` |
| **— Thời gian & trạng thái —** | | |
| `contract_date` | `DATE NOT NULL` | |
| `start_date` / `end_date` | `DATE` | |
| `activated_at` / `closed_at` | `TIMESTAMPTZ` | |
| `status` | `VARCHAR(20) NOT NULL DEFAULT 'PENDING_PAYMENT'` | `CHECK IN ('PENDING_PAYMENT','ACTIVE','FROZEN','COMPLETED','CANCELLED','REFUNDED')` |
| `close_reason` | `VARCHAR(255)` | |
| `note` | `TEXT` | |
| **— Bảo lưu (tối đa 1 lần/hợp đồng) —** | | |
| `freeze_from_date` / `freeze_to_date` | `DATE` | |
| `freeze_days` | `INTEGER GENERATED ALWAYS AS (freeze_to_date - freeze_from_date + 1) STORED` | CSDL tự tính |
| `freeze_reason` | `VARCHAR(255)` | |
| `freeze_reason_type` | `VARCHAR(20)` | `CHECK IN ('PERSONAL','MEDICAL','TRAVEL','OTHER')` |
| `freeze_attachment_key` | `VARCHAR(500)` | |
| `freeze_requested_by` / `freeze_approved_by` | `BIGINT` → `users.id` | |
| `freeze_status` | `VARCHAR(20)` | `CHECK IN ('PENDING','APPROVED','REJECTED','ACTIVE','ENDED','CANCELLED')` |
| `freeze_ended_early_at` | `DATE` | |
| **— Gia hạn —** | | |
| `renew_from_id` | `BIGINT` → `registrations.id` | **Thêm ở `V18`** — hợp đồng mới "nối tiếp" hợp đồng nào; nhờ đó `start_date` mới = `max(hôm nay, renew_from.end_date + 1)` thay vì luôn là hôm nay, tránh mua sớm bị chồng ngày |

Có `deleted_at`. Ràng buộc quan trọng: `status='FROZEN'` bắt buộc có `freeze_status='ACTIVE'`.

## `member_trainers` *(V3)*

| Cột | Kiểu | Ghi chú |
|---|---|---|
| `member_id` | `BIGINT NOT NULL` → `members.id` | |
| `trainer_id` | `BIGINT NOT NULL` → `employees.id` | |
| `role` | `VARCHAR(20) NOT NULL DEFAULT 'PRIMARY'` | `CHECK IN ('PRIMARY','SECONDARY','SUBSTITUTE')` |
| `from_date` | `DATE NOT NULL` | |
| `to_date` | `DATE` | `NULL` = đang phụ trách |
| `assigned_by` | `BIGINT` → `users.id` | |
| `note` | `VARCHAR(255)` | |

Mỗi hội viên chỉ có đúng 1 PT `PRIMARY` đang hoạt động (unique index có điều kiện). **Không có `deleted_at`.**

---
---

# NHÓM 3 — CHECK-IN

## `check_ins` *(V7, sửa V15 + V16)*

> So với kế hoạch gốc: **đã bỏ** RFID, nhận diện khuôn mặt, nhập tay (`MANUAL`), cột `employee_id`, `device_id`, `photo_key`. Luồng thật (Hướng A): hội viên hiện QR động trên app → lễ tân quét bằng máy quầy → hiện lại ảnh hồ sơ có sẵn (`persons.photo_key`), không chụp ảnh mới.

| Cột | Kiểu | Ghi chú |
|---|---|---|
| `member_id` | `BIGINT NOT NULL` → `members.id` | |
| `registration_id` | `BIGINT` → `registrations.id` | |
| `checked_in_at` | `TIMESTAMPTZ NOT NULL DEFAULT now()` | |
| `checked_out_at` | `TIMESTAMPTZ` | `NULL` = đang ở trong phòng tập |
| `auto_closed` | `BOOLEAN NOT NULL DEFAULT FALSE` | `TRUE` = job đêm tự điền giờ ra vì quên check-out |
| `method` | `VARCHAR(20) NOT NULL` | Chỉ còn 2 giá trị: `QR_DYNAMIC` \| `DAY_PASS` |
| `result` | `VARCHAR(30) NOT NULL` | 8 giá trị (mở rộng độ dài cột ở `V16` để chứa `DENIED_ALREADY_INSIDE` thêm ở `V15`): `ALLOWED`, `ALLOWED_OVERRIDE`, `DENIED_EXPIRED`, `DENIED_FROZEN`, `DENIED_UNPAID`, `DENIED_NOT_FOUND`, `DENIED_SUSPECT`, `DENIED_ALREADY_INSIDE` |
| `verified_by` | `BIGINT` → `users.id` | |
| `incident_type` | `VARCHAR(30)` | 4 giá trị: `ANTI_PASSBACK`, `SUSPECTED_SHARING`, `REPLAY_ATTEMPT`, `EXPIRED_ATTEMPT` |
| `incident_note` | `TEXT` | |
| `incident_handled_by` | `BIGINT` → `users.id` | |

**Không có `deleted_at`.**

---
---

# NHÓM 4 — BUỔI TẬP PT & SỔ CÁI

## `pt_sessions` *(V5)*

| Cột | Kiểu | Ghi chú |
|---|---|---|
| `member_id` | `BIGINT NOT NULL` → `members.id` | |
| `trainer_id` | `BIGINT NOT NULL` → `employees.id` | PT **thực tế dạy**, không phải PT được phân công ở `registrations` |
| `registration_id` | `BIGINT NOT NULL` → `registrations.id` | |
| `session_type` | `VARCHAR(20) NOT NULL` | `CHECK IN ('PAID_PT','COMPLIMENTARY','TRIAL','ORIENTATION','MAKEUP','ASSESSMENT')` |
| `scheduled_start` / `scheduled_end` | `TIMESTAMPTZ NOT NULL` | |
| `actual_start` / `actual_end` | `TIMESTAMPTZ` | |
| `room_name` | `VARCHAR(120)` | |
| `status` | `VARCHAR(20) NOT NULL DEFAULT 'PENDING_TRAINER'` | 6 giá trị: `PENDING_TRAINER`, `REJECTED`, `SCHEDULED`, `COMPLETED`, `NO_SHOW`, `CANCELLED` |
| `requested_by` | `BIGINT` → `users.id` | |
| `responded_at` | `TIMESTAMPTZ` | |
| `reject_reason` | `VARCHAR(255)` | Bắt buộc khi `REJECTED` |
| `trainer_confirmed_at` | `TIMESTAMPTZ` | Điều kiện khởi động — không bấm thì không tự trả công |
| `member_confirmed_at` | `TIMESTAMPTZ` | |
| `auto_confirmed` | `BOOLEAN NOT NULL DEFAULT FALSE` | `TRUE` = hệ thống tự duyệt sau 24h hội viên không phản hồi |
| `no_show_by` | `VARCHAR(20)` | `MEMBER` \| `TRAINER`, bắt buộc khi `NO_SHOW` |
| `cancelled_by` | `VARCHAR(20)` | `MEMBER` \| `TRAINER` \| `SYSTEM` |
| `cancelled_at` / `cancel_reason` | | |
| `is_late_cancel` | `BOOLEAN NOT NULL DEFAULT FALSE` | |
| `note` | `TEXT` | |

`COMPLETED` bắt buộc có đủ `trainer_confirmed_at` **và** `member_confirmed_at`. Có `deleted_at`.

## `session_credit_ledger` *(V5)* — ⭐ đóng góp học thuật chính

Sổ cái **chỉ ghi thêm**, chặn `UPDATE`/`DELETE` bằng trigger `so_cai_chi_ghi_them()` ở tầng CSDL.

| Cột | Kiểu | Ghi chú |
|---|---|---|
| `registration_id` | `BIGINT NOT NULL` → `registrations.id` | |
| `entry_type` | `VARCHAR(20) NOT NULL` | `CHECK IN ('GRANT','CONSUME','REFUND','EXPIRE','ADJUST')` |
| `delta` | `INTEGER NOT NULL` | Dấu phải khớp `entry_type` (`GRANT`/`REFUND` dương, `CONSUME`/`EXPIRE` âm) |
| `balance_after` | `INTEGER NOT NULL` | `CHECK >= 0` — không bao giờ âm |
| `source_type` | `VARCHAR(30) NOT NULL` | `PT_SESSION` / `REGISTRATION` / `MANUAL` / `EXPIRY_JOB` |
| `source_id` | `BIGINT` | |
| `reason` | `VARCHAR(255)` | Bắt buộc khi `ADJUST` |
| `created_by` | `BIGINT` → `users.id` | Bắt buộc khi `ADJUST` |

Ràng buộc: mỗi buổi (`source_type='PT_SESSION'`) chỉ trừ đúng 1 lần; mỗi hợp đồng chỉ `GRANT` đúng 1 lần. **Không có `updated_at`/`deleted_at`** — đúng bản chất sổ cái, sửa sai bằng ghi thêm bút toán `ADJUST` mới.

---
---

# NHÓM 6 — THIẾT BỊ

## `equipment` *(V12)*

Bản rút gọn — 1 dòng = 1 loại/khu thiết bị, **không** tách `equipment_types`/`equipment_items` như thiết kế đầy đủ.

| Cột | Kiểu | Ghi chú |
|---|---|---|
| `name` | `VARCHAR(120) NOT NULL` | |
| `room_name` | `VARCHAR(120)` | |
| `status` | `VARCHAR(20) NOT NULL DEFAULT 'ACTIVE'` | `CHECK IN ('ACTIVE','NEEDS_REPAIR','UNDER_REPAIR','RETIRED')` |
| `note` | `VARCHAR(255)` | |

Có `deleted_at`.

---
---

# NHÓM 7 — THANH TOÁN

## `invoices` *(V6)*

Chỉ giữ số riêng của hóa đơn — **không có** `subtotal`/`tax_amount` riêng (đã có ở snapshot `registrations`, tránh 2 nguồn sự thật).

| Cột | Kiểu | Ghi chú |
|---|---|---|
| `invoice_no` | `VARCHAR(30) NOT NULL` | UNIQUE (partial), sinh từ `invoice_no_seq` (V8) |
| `member_id` | `BIGINT NOT NULL` → `members.id` | |
| `registration_id` | `BIGINT NOT NULL` → `registrations.id` | |
| `description` | `VARCHAR(255)` | |
| `total_amount` | `NUMERIC(14,2) NOT NULL` | Copy 1 lần từ `registrations.final_price` |
| `paid_amount` | `NUMERIC(14,2) NOT NULL DEFAULT 0` | |
| `balance_due` | `NUMERIC(14,2) GENERATED ALWAYS AS (total_amount - paid_amount) STORED` | |
| `status` | `VARCHAR(20) NOT NULL DEFAULT 'UNPAID'` | `CHECK IN ('UNPAID','PARTIALLY_PAID','PAID','OVERDUE','CANCELLED','REFUNDED')` |
| `issued_at` | `TIMESTAMPTZ NOT NULL DEFAULT now()` | |
| `due_date` | `DATE` | |
| `paid_at` | `TIMESTAMPTZ` | Bắt buộc khi `PAID` |
| `issued_by` | `BIGINT` → `users.id` | |

Có `deleted_at`.

## `payments` *(V6)*

Hoàn tiền = bút toán **âm** (không có bảng `refunds` riêng). Đã bỏ `currency`, `idempotency_key`, `pos_terminal_id`, `pos_card_last4` so với kế hoạch gốc.

| Cột | Kiểu | Ghi chú |
|---|---|---|
| `payment_no` | `VARCHAR(30) NOT NULL UNIQUE` | Sinh từ `payment_no_seq` (V8) |
| `member_id` | `BIGINT NOT NULL` → `members.id` | |
| `invoice_id` | `BIGINT NOT NULL` → `invoices.id` | |
| `cash_shift_id` | `BIGINT` → `cash_shifts.id` | Bắt buộc khi tiền mặt thành công |
| `payment_type` | `VARCHAR(20) NOT NULL DEFAULT 'PAYMENT'` | `PAYMENT` \| `REFUND` |
| `method` | `VARCHAR(20) NOT NULL` | `CASH`, `BANK_TRANSFER`, `VIETQR`, `CARD_POS`, `E_WALLET`, `GATEWAY` |
| `amount` | `NUMERIC(14,2) NOT NULL` | Dương nếu `PAYMENT`, âm nếu `REFUND` |
| `status` | `VARCHAR(20) NOT NULL DEFAULT 'INITIATED'` | `INITIATED`, `PENDING`, `SUCCEEDED`, `FAILED`, `EXPIRED` |
| `provider` / `provider_txn_id` | `VARCHAR(30)` / `VARCHAR(100)` | UNIQUE cặp (partial) chống webhook ghi trùng |
| `transfer_content` / `bank_account` / `raw_payload` | | |
| `refund_of_payment_id` | `BIGINT` → `payments.id` | Bắt buộc khi `REFUND`, kèm `refund_reason` + `approved_by` |
| `refund_reason` / `refund_penalty` / `approved_by` | | |
| `received_by` / `paid_at` / `reconciled_at` | | |

**Không có `deleted_at`.**

## `cash_shifts` *(V6)*

| Cột | Kiểu | Ghi chú |
|---|---|---|
| `employee_id` | `BIGINT NOT NULL` → `employees.id` | Mỗi nhân viên tối đa 1 ca `OPEN` |
| `opened_at` / `closed_at` | `TIMESTAMPTZ` | |
| `opening_balance` | `NUMERIC(14,2) NOT NULL DEFAULT 0` | |
| `counted_cash` | `NUMERIC(14,2)` | Đếm tay lúc đóng ca |
| `expected_cash` | `NUMERIC(14,2)` | Hệ thống tính |
| `difference` | `NUMERIC(14,2) GENERATED ALWAYS AS (counted_cash - expected_cash) STORED` | |
| `difference_reason` | `VARCHAR(255)` | Bắt buộc khi `DISCREPANCY` |
| `status` | `VARCHAR(20) NOT NULL DEFAULT 'OPEN'` | `OPEN` \| `CLOSED` \| `DISCREPANCY` |
| `verified_by` / `note` | | |

**Không có `deleted_at`.**

---
---

# NHÓM 8 — TÀI CHÍNH & LƯƠNG

## `revenue_schedules` *(V9)* — ⭐ đóng góp học thuật thứ hai

Gánh luôn vai trò ghi nhận doanh thu — **không có bảng `revenue_recognition_entries` riêng** như trong kế hoạch gốc.

| Cột | Kiểu | Ghi chú |
|---|---|---|
| `registration_id` | `BIGINT NOT NULL` → `registrations.id` | |
| `invoice_id` | `BIGINT` → `invoices.id` | |
| `schedule_date` | `DATE NOT NULL` | |
| `amount` | `NUMERIC(14,2) NOT NULL` | `CHECK >= 0` |
| `status` | `VARCHAR(20) NOT NULL DEFAULT 'PENDING'` | `PENDING` \| `RECOGNIZED` \| `REVERSED` |
| `recognition_method` | `VARCHAR(20) NOT NULL DEFAULT 'STRAIGHT_LINE'` | `STRAIGHT_LINE` \| `USAGE_BASED` |
| `recognized_at` / `note` | | |

**Không có `deleted_at`.**

## `payroll_runs` *(V9, sửa V11)*

| Cột | Kiểu | Ghi chú |
|---|---|---|
| `payroll_code` | `VARCHAR(30) NOT NULL UNIQUE` | Sinh từ `payroll_code_seq` |
| `period_month` / `period_year` | `INTEGER NOT NULL` | Unique theo cặp (trừ `CANCELLED`) |
| `total_base_salary` / `total_commission` / `total_bonus` / `total_deduction` / `total_net_salary` | `NUMERIC(14,2) NOT NULL DEFAULT 0` | |
| `status` | `VARCHAR(20) NOT NULL DEFAULT 'DRAFT'` | `DRAFT`, `APPROVED`, `PAID`, `CANCELLED` |
| `created_by` / `approved_by` / `approved_at` / `paid_at` | | |
| `paid_by` | `BIGINT` → `users.id` | **Thêm ở `V11`** — ai bấm nút chi lương |
| `note` | `TEXT` | |

**Không có `deleted_at`.**

## `payroll_items` *(V9, sửa V11)*

| Cột | Kiểu | Ghi chú |
|---|---|---|
| `payroll_run_id` | `BIGINT NOT NULL` → `payroll_runs.id` **ON DELETE CASCADE** | |
| `employee_id` | `BIGINT NOT NULL` → `employees.id` | UNIQUE cặp `(payroll_run_id, employee_id)` |
| `base_salary` / `pt_commission` / `sales_commission` / `bonus_amount` / `deduction_amount` / `net_salary` | `NUMERIC(14,2) NOT NULL DEFAULT 0` | |
| `pt_sessions_count` / `sales_contracts_count` | `INTEGER NOT NULL DEFAULT 0` | |
| `manually_edited` | `BOOLEAN NOT NULL DEFAULT FALSE` | **Thêm ở `V11`** — cảnh báo trước khi "Tính lương" lại ghi đè chỉnh tay |
| `note` | `TEXT` | |

**Không có `deleted_at`.**

## `expenses` *(V9)*

| Cột | Kiểu | Ghi chú |
|---|---|---|
| `expense_no` | `VARCHAR(30) NOT NULL` | UNIQUE (partial), sinh từ `expense_no_seq` |
| `category` | `VARCHAR(30) NOT NULL` | `RENT`, `UTILITIES`, `EQUIPMENT_MAINTENANCE`, `SALARY`, `SUPPLIES`, `MARKETING`, `OTHER` |
| `title` | `VARCHAR(255) NOT NULL` | |
| `amount` | `NUMERIC(14,2) NOT NULL` | `CHECK > 0` |
| `spent_at` | `DATE NOT NULL` | |
| `spent_by` / `approved_by` | `BIGINT` → `users.id` | |
| `status` | `VARCHAR(20) NOT NULL DEFAULT 'APPROVED'` | `PENDING` \| `APPROVED` \| `REJECTED` |
| `payment_method` | `VARCHAR(20) NOT NULL DEFAULT 'BANK_TRANSFER'` | `CASH`, `BANK_TRANSFER`, `CARD_POS`, `E_WALLET` |
| `receipt_url` / `note` | | |

Có `deleted_at`.

---
---

# NHÓM 9 — BÁN HÀNG / CRM

## `leads` *(V10)*

| Cột | Kiểu | Ghi chú |
|---|---|---|
| `person_id` | `BIGINT NOT NULL` → `persons.id` | Bắt buộc, chống trùng SĐT |
| `source` | `VARCHAR(30) NOT NULL` | `WALK_IN`, `HOTLINE`, `WEB_FORM`, `REFERRAL`, `APP_SELF` |
| `interested_membership_id` | `BIGINT` → `memberships.id` | |
| `assigned_to` | `BIGINT` → `employees.id` | |
| `stage` | `VARCHAR(20) NOT NULL DEFAULT 'NEW'` | `NEW`, `CONTACTED`, `TRIAL_BOOKED`, `TRIAL_DONE`, `WON`, `LOST` |
| `lost_reason` | `VARCHAR(30)` | Bắt buộc khi `LOST`; `PRICE`, `LOCATION`, `COMPETITOR`, `NOT_READY`, `NO_RESPONSE` |
| `last_contact_at` / `last_contact_note` | | Thay cho bảng `lead_activities` |
| `next_follow_up` | `DATE` | |

Có `deleted_at`.

---
---

# NHÓM 11 — PHẢN HỒI

## `feedbacks` *(V12, sửa V13 + V14)*

> ⚠️ **Bản đã triển khai đơn giản hơn nhiều** so với thiết kế đầy đủ trong `32_bang.md` (không có `title`, `severity`, `sla_due_at`, `is_anonymous`, `expense_id`, không tự động routing theo bảng riêng).

| Cột | Kiểu | Ghi chú |
|---|---|---|
| `member_id` | `BIGINT NOT NULL` → `members.id` | |
| `feedback_type` | `VARCHAR(20) NOT NULL` | `TRAINER`, `FACILITY`, `HYGIENE`, `SERVICE`, `GENERAL` |
| `trainer_id` | `BIGINT` → `employees.id` | Bắt buộc khi `TRAINER` |
| `equipment_id` | `BIGINT` → `equipment.id` | Bắt buộc khi `FACILITY` |
| `rating` | `INTEGER` | `CHECK BETWEEN 1 AND 5` — kiểu đổi từ `SMALLINT` sang `INTEGER` ở `V14` để khớp entity Java |
| `description` | `TEXT NOT NULL` | |
| `status` | `VARCHAR(20) NOT NULL DEFAULT 'OPEN'` | `OPEN`, `IN_PROGRESS`, `WAITING_PARTS`, `RESOLVED`, `CLOSED` |
| `is_urgent` | `BOOLEAN NOT NULL DEFAULT FALSE` | Tự bật khi `TRAINER` + `rating <= 2` |
| `repair_cost` | `NUMERIC(14,2)` | Chỉ nhập khi `FACILITY` đã sửa xong — tự sinh 1 dòng `expenses` |
| `resolution_note` | `VARCHAR(500)` | |
| `resolved_by` / `resolved_at` | | |

`deleted_at` **thêm ở `V13`** (thiếu sót ban đầu — entity `Feedback` kế thừa `BaseEntity` có `deletedAt` nhưng bảng ban đầu quên cột).

---
---

# NHÓM 12 — HỆ THỐNG

## `system_settings` *(V19)* — ⚠️ ĐÃ CÓ, không phải bảng cần thêm mới

> Thiết kế thật **khác kế hoạch gốc**: kế hoạch dùng `key`/`value JSONB`, bản thật dùng `setting_key`/`setting_value VARCHAR` (chuyển kiểu ở tầng service, có fallback về giá trị mặc định nếu thiếu/sai định dạng).

| Cột | Kiểu | Ghi chú |
|---|---|---|
| `setting_key` | `VARCHAR(100) PRIMARY KEY` | |
| `setting_value` | `VARCHAR(500) NOT NULL` | Lưu dạng chuỗi, ép kiểu ở service |
| `description` | `VARCHAR(255)` | |
| `updated_at` | `TIMESTAMPTZ NOT NULL DEFAULT now()` | |
| `updated_by` | `BIGINT` → `users.id` | |

### 8 tham số đã seed sẵn

| `setting_key` | Giá trị mẫu | Ý nghĩa |
|---|---|---|
| `pt.cancel.min-hours-before` | `4` | Hủy buổi tập sát giờ hơn số giờ này → tính hủy muộn, mất buổi |
| `security.login.max-failed-attempts` | `5` | Sai mật khẩu quá số lần này → khóa tạm |
| `security.login.lockout-minutes` | `15` | Số phút khóa tạm |
| `membership.freeze.min-advance-days` | `3` | Phải báo trước ít nhất số ngày này mới được bảo lưu |
| `checkin.duplicate-scan-window-minutes` | `30` | Quét lại trong khoảng này bị coi là bất thường |
| `gym.closing-time` | `22:30` | Giờ đóng cửa — job đêm dùng để tự đóng lượt quên check-out |
| `payroll.pt-commission-per-session` | `100000` | Hoa hồng PT/buổi `COMPLETED` (VND) |
| `payroll.sales-commission-rate` | `0.05` | Tỷ lệ hoa hồng Sales/Lễ tân trên doanh số thu tiền |

---
---

# NHÓM 10 — BÀI TẬP & CHỈ SỐ CƠ THỂ *(ĐỀ XUẤT, CHƯA TRIỂN KHAI)*

> **4 bảng dưới đây chưa có migration nào** — schema đề xuất, kế thừa từ thiết kế trong `db/32_bang.md` (nhóm 10), phục vụ 3 kịch bản chatbot đã thống nhất: (1) chatbot đối chiếu giáo án PT với thể trạng hội viên để cảnh báo an toàn, (2) diễn giải tiến độ tập thay vì chỉ liệt kê số, (3) gợi ý bài tập thay thế có lọc theo chống chỉ định cá nhân.
>
> **Đã bỏ `workout_logs`** khỏi đề xuất — ghi lại `sets_done`/`reps_done`/`weight_kg` sau mỗi bài đòi hỏi hội viên tự nhớ và gõ tay, không tiện lợi. Hệ quả: **kịch bản 2** không còn diễn giải được ở mức "từng bài tập" (ví dụ tiến bộ mức tạ bench press theo tuần), mà lùi về mức thô hơn — kết hợp tần suất đến phòng (`check_ins`) + số buổi PT hoàn thành (`pt_sessions.status='COMPLETED'`) với xu hướng `body_metrics` để chatbot nhận định kiểu *"tháng này bạn đến phòng đều hơn tháng trước nhưng cân nặng chưa đổi"* — không cần hội viên nhập liệu gì thêm.

## `exercises` — thư viện bài tập *(đề xuất)*

| Cột | Kiểu | Ghi chú |
|---|---|---|
| `code` | `VARCHAR(60) UNIQUE` | |
| `name_vi` / `name_en` | `VARCHAR(200)` | |
| `muscle_group` | `VARCHAR(40)` | `CHEST`, `BACK`, `LEGS`, `SHOULDERS`, `ARMS`, `CORE`, `CARDIO` |
| `secondary_muscles` | `VARCHAR(200)` | |
| `equipment` | `VARCHAR(60)` | `BARBELL`, `DUMBBELL`, `MACHINE`, `BODYWEIGHT`, `CABLE`, `KETTLEBELL` |
| `difficulty` | `VARCHAR(20)` | `BEGINNER`, `INTERMEDIATE`, `ADVANCED` |
| `instructions` | `TEXT` | |
| `media_key` | `VARCHAR(500)` | Ảnh minh họa, key MinIO |
| `contraindications` | `JSONB` | **Cột then chốt cho kịch bản 1 và 3** — chống chỉ định, đối chiếu với `members.health_note`/`body_metrics` |
| `source` / `license` | `VARCHAR(60)` | Ghi rõ nguồn để tuân thủ bản quyền |
| `is_reviewed` | `BOOLEAN` | Bản dịch máy bắt buộc người rà soát mới hiển thị |

## `workout_plans` — giáo án *(đề xuất, gánh `workout_templates`)*

| Cột | Kiểu | Ghi chú |
|---|---|---|
| `is_template` | `BOOLEAN` | `TRUE` = giáo án mẫu, chưa gán ai |
| `member_id` | `BIGINT` | `NULL` khi `is_template=TRUE` |
| `trainer_id` | `BIGINT` | |
| `source_template_id` | `BIGINT` | |
| `name` / `goal` / `level` | | |
| `duration_weeks` / `days_per_week` | `SMALLINT` | |
| `start_date` / `end_date` | `DATE` | |
| `status` | `VARCHAR(20)` | `ACTIVE`, `COMPLETED`, `PAUSED`, `CANCELLED` |
| `ai_generated` | `BOOLEAN` | |
| `reviewed_by` | `BIGINT` | Bắt buộc nếu `ai_generated=TRUE` — AI hỗ trợ, PT chịu trách nhiệm |

**Đây là bảng cần cho kịch bản 1**: giáo án PT gán cho hội viên, để chatbot đối chiếu với thể trạng.

## `workout_plan_items` — bài tập trong giáo án *(đề xuất)*

| Cột | Kiểu | Ghi chú |
|---|---|---|
| `plan_id` | `BIGINT` → `workout_plans.id` | |
| `day_index` / `order_index` | `SMALLINT` | UNIQUE cặp `(plan_id, day_index, order_index)` |
| `exercise_id` | `BIGINT` → `exercises.id` | |
| `sets` | `SMALLINT` | |
| `reps` | `VARCHAR(20)` | Kiểu chữ vì có dạng khoảng (`"8-12"`, `"AMRAP"`) |
| `target_weight_kg` | `NUMERIC(6,2)` | |
| `rest_sec` | `SMALLINT` | |
| `note` | `VARCHAR(255)` | |

## `body_metrics` — chỉ số cơ thể *(đề xuất)*

| Cột | Kiểu | Ghi chú |
|---|---|---|
| `member_id` | `BIGINT` | |
| `measured_at` | `TIMESTAMPTZ` | |
| `measured_by` | `BIGINT` | `NULL` = tự nhập |
| `source` | `VARCHAR(20)` | `MANUAL`, `INBODY_OCR`, `DEVICE_SYNC` |
| `height_cm` | `NUMERIC(5,2)` | Hợp lệ `80–250` |
| `weight_kg` | `NUMERIC(5,2)` | Hợp lệ `20–300` |
| `body_fat_pct` | `NUMERIC(4,2)` | Hợp lệ `1–70` |
| `muscle_mass_kg` | `NUMERIC(5,2)` | |
| `visceral_fat` | `NUMERIC(4,1)` | |
| `bmr_kcal` | `INTEGER` | |
| `bmi` | `NUMERIC(5,2)` | Cột tự tính `= kg / m²` |
| `chest_cm`/`waist_cm`/`hip_cm`/`arm_cm`/`thigh_cm` | `NUMERIC(5,2)` | |
| `photo_key` | `VARCHAR(500)` | Ảnh phiếu InBody |
| `ocr_confidence` | `NUMERIC(4,3)` | Độ tin cậy đọc máy, `0–1` |
| `confirmed_by_user` | `BOOLEAN` | AI không bao giờ ghi thẳng vào CSDL — người dùng phải xác nhận |
| `note` | `VARCHAR(255)` | |

**Bảng quan trọng nhất trong nhóm G** — là input duy nhất để cá nhân hóa theo tuổi/thể trạng, trực tiếp phục vụ kịch bản 1 (chatbot kiểm tra an toàn giáo án cho người lớn tuổi).

> ⚠️ Ranh giới đạo đức đã thống nhất: hệ thống chỉ hiển thị số liệu + tham chiếu ngưỡng WHO châu Á, **không chẩn đoán, không kê chế độ dinh dưỡng cá nhân hóa**.
