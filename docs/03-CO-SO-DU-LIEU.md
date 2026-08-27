# Phần 3: Cơ sở dữ liệu

## 1. Đánh giá schema hiện tại (`fix.sql`)

Schema hiện tại là một điểm khởi đầu **tốt cho phần quản lý hội viên cơ bản**: đã có ràng buộc khóa ngoại đầy đủ, `CHECK` constraint hợp lý, `ON DELETE` được cân nhắc, dùng `BIGSERIAL` và `NUMERIC` cho tiền. Tuy nhiên còn thiếu và có một số điểm cần sửa để đáp ứng phạm vi đề bài (6 role + tài chính + bảo lưu + tính lương).

### 1.1 Vấn đề nghiêm trọng (chặn nghiệp vụ — phải sửa)

| # | Vấn đề | Hệ quả | Cách sửa |
|---|---|---|---|
| **P1** | `users.role CHECK IN ('admin','member','trainer')` — **thiếu 3 role** sale, receptionist, accountant | Không lưu được 3 role đã chốt | Mở rộng enum + thêm bảng `staff` |
| **P2** | `CREATE UNIQUE INDEX uq_only_one_admin ... WHERE role='admin'` — chỉ cho **1 admin duy nhất** | Nếu tài khoản admin đó bị khóa hoặc quên mật khẩu thì không còn ai quản trị được hệ thống; cũng không tạo được tài khoản admin dự phòng | **Bỏ index này** |
| **P3** | **`registrations` thiếu `start_date` và `end_date`** | ⚠️ **Không thể biết gói hết hạn khi nào** — đây là lỗ hổng lớn nhất. Không kiểm tra được check-in hợp lệ, không tính được doanh thu phân bổ, không có danh sách sắp hết hạn cho Sale | Thêm `start_date`, `end_date`, `sessions_total`, `sessions_used` |
| **P4** | **`registrations` không lưu giá đã thỏa thuận** | Khi Admin sửa giá gói → giá trị mọi hợp đồng lịch sử thay đổi theo → sai toàn bộ báo cáo tài chính | **Snapshot giá** vào hợp đồng: `list_price`, `discount_amount`, `final_price` |
| **P5** | **Không có bảng thanh toán** (`invoices`, `payments`) | Không làm được module tài chính — mà đây là yêu cầu trung tâm của đề bài | Thêm nhóm bảng billing |
| **P6** | **Không có bảng check-in riêng** | `training_histories` là lịch sử buổi tập PT, khác hoàn toàn với lượt vào phòng gym. Trộn 2 khái niệm → không quản lý được việc "hội viên đến tập" như thầy yêu cầu | Tách `check_ins` riêng |
| **P7** | **Không có cơ chế bảo lưu** | Yêu cầu trực tiếp của thầy chưa được đáp ứng | Thêm `registration_freezes` |
| **P8** | `memberships.training_time VARCHAR` chứa `'1 tháng'`, `'12 buổi'`, `'1 ngày'` | **Không query được**: không lọc "gói ≥ 3 tháng", không tính `end_date`, không so sánh | Tách thành `package_type ENUM`, `duration_days INT`, `session_count INT` |
| **P9** | `registration_type` (annual/monthly/quarterly) **trùng lặp ý nghĩa** với `training_time` | Chính là điểm thầy đã chỉ ra trong mục "Hạn chế". Hai trường mô tả cùng một thứ ở hai nơi → chắc chắn sẽ lệch nhau | **Bỏ `registration_type`**; thời hạn chỉ nằm ở `memberships` và được snapshot sang `registrations` |
| **P10** | `registrations.status` (active/cancel/expired) **chưa đủ và chưa rõ nghĩa** | Cũng là điểm thầy đã chỉ ra. Thiếu các trạng thái: chờ thanh toán, đang bảo lưu, đã chuyển nhượng, đã hoàn tiền | Máy trạng thái 8 giá trị, có tài liệu định nghĩa rõ (mục 3.2) |
| **P11** | `classes` **trộn lẫn "lớp học" và "phòng tập"** | `code`, `class_type`, `maximum_number` mô tả lớp; `location`, `is_occupied` mô tả phòng. Không lập được lịch lớp lặp lại hàng tuần, không biết lớp diễn ra lúc mấy giờ, ai dạy | Tách `rooms` / `class_definitions` / `class_schedules` / `class_sessions` / `class_bookings` |
| **P12** | `facilities` **nhập nhằng giữa "loại thiết bị" và "từng cái thiết bị"** | `total_quantity` gợi ý là loại; nhưng `date_of_purchase`, `warranty_date` gợi ý là từng cái cụ thể. Không truy vết được "máy chạy bộ **số 3**" bị hỏng | Tách `equipment_types` (loại) và `equipment_items` (từng tài sản, có serial) |

### 1.2 Vấn đề mức trung bình

| # | Vấn đề | Cách sửa |
|---|---|---|
| P13 | Thiếu `updated_at` trên mọi bảng | Thêm + trigger tự cập nhật |
| P14 | Không có soft delete | Thêm `deleted_at` cho bảng cần giữ lịch sử |
| P15 | Không có index cho khóa ngoại và cột lọc thường dùng | PostgreSQL **không tự tạo index cho FK** → mọi JOIN đều seq scan khi bảng lớn |
| P16 | `class_has_facilities.quantity` không ràng buộc `Σ quantity ≤ total_quantity` | Thêm trigger hoặc chuyển sang mô hình gán từng `equipment_item` |
| P17 | Không có bảng audit | Thêm `audit_logs` |
| P18 | `members.email UNIQUE` nhưng cho phép `NULL` — nhiều bản ghi NULL vẫn hợp lệ (đúng chuẩn SQL) nhưng dễ gây hiểu nhầm | Ghi rõ trong tài liệu; thêm `UNIQUE(phone_number)` vì SĐT mới là định danh thực tế ở VN |
| P19 | ~~Không có `branch_id`~~ — **đã loại bỏ khỏi phạm vi** | Hệ thống chỉ quản lý **1 phòng gym**, không cần bảng `branches` hay cột `branch_id`. Thông tin phòng gym (tên, địa chỉ, giờ mở/đóng cửa) lưu trong `system_settings`. Quản lý đa chi nhánh nằm ở mục WON'T HAVE |
| P20 | Tiền dùng `NUMERIC(12,2)` = tối đa ~9.999 tỷ | Đủ, nhưng nâng lên `NUMERIC(14,2)` cho an toàn với báo cáo lũy kế nhiều năm |
| P21 | `trainers.trainer_type VARCHAR(80)` tự do | Chuẩn hóa: `FULL_TIME`, `PART_TIME`, `FREELANCE` + bảng `trainer_specialties` riêng cho chuyên môn (yoga, gym, cardio...) |
| P22 | Không lưu ảnh hồ sơ | Thêm `photo_url` — bắt buộc cho nghiệp vụ xác minh check-in |

### 1.3 Trả lời trực tiếp hai câu hỏi trong "Hạn chế"

> **"Chưa làm rõ sự khác biệt giữa các giá trị của thuộc tính status: active, cancel, expired"**

Định nghĩa chuẩn (mục 3.2 bên dưới): 8 trạng thái với điều kiện chuyển đổi rõ ràng và tác động cụ thể tới check-in, ghi nhận doanh thu, credit. `CANCELLED` (hủy trước khi bắt đầu, có thể hoàn tiền) khác hẳn `EXPIRED`/`COMPLETED` (đã dùng hết thời hạn/số buổi, không hoàn tiền) — đây là hai sự kiện kế toán khác nhau hoàn toàn.

> **"registration_type: annual, monthly, quarterly — có liên quan, khác biệt gì so với training_time trong memberships"**

Chúng **trùng lặp** — đây là lỗi thiết kế (vi phạm nguyên tắc một sự thật một chỗ). Thời hạn là **thuộc tính của gói tập**, không phải của lần đăng ký. Giải pháp: bỏ `registration_type`; `memberships` định nghĩa `package_type` + `duration_days` + `session_count`; `registrations` **snapshot** các giá trị này tại thời điểm ký hợp đồng (để hợp đồng cũ không bị ảnh hưởng khi gói thay đổi).

### 1.4 Trả lời hai câu hỏi của cô Trinh

> **1. Phân biệt class và membership — có đăng ký cả 2 cùng lúc được không?**

Câu trả lời trong draft (`membership_id NOT NULL`, `class_id` nullable) **đúng về nguyên tắc nhưng thiết kế chưa tối ưu**. Vấn đề: một hội viên có gói 6 tháng thường tham gia **nhiều lớp khác nhau** (yoga thứ 2, HIIT thứ 5) → nhồi `class_id` vào `registrations` chỉ chứa được **một** lớp.

**Thiết kế đúng — tách hai khái niệm:**

```
membership  = QUYỀN TRUY CẬP có thời hạn (mua bằng tiền)
class       = SỰ KIỆN có lịch cụ thể (tiêu dùng bằng quyền truy cập)

registrations (member_id, membership_id, start_date, end_date, ...)
      │  Hội viên mua gói → có quyền vào tập
      │
      └──► class_bookings (registration_id, class_session_id, status, booked_at)
                Hội viên dùng quyền đó để đăng ký từng buổi lớp cụ thể
```

→ Trả lời: **có, đăng ký cả hai cùng lúc được** — mua gói (tạo `registration`) rồi đặt lớp (tạo nhiều `class_booking`) trong cùng một luồng UI. Về mặt dữ liệu, chúng là hai bản ghi ở hai bảng, quan hệ 1–N. Và **vẫn giữ nguyên nguyên tắc gốc**: không có `registration` hợp lệ thì không đặt được lớp.

> **2. Làm rõ giới hạn thiết bị giữa class và membership**

Câu trả lời trong draft đúng: **thiết bị gắn với không gian/lớp, không gắn với gói tập**. Bổ sung để chặt chẽ hơn:

```
equipment_items  ──►  rooms  ──►  class_sessions  ──►  class_bookings
   (từng máy)       (phòng)      (buổi lớp cụ thể)      (chỗ của hội viên)

memberships ──► access_scopes(membership_id, area_code)
   Gói quy định ĐƯỢC VÀO KHU VỰC NÀO (gym floor / yoga studio / hồ bơi / sauna),
   không quy định "được dùng máy nào"
```

Nghĩa là: `memberships` giới hạn **phạm vi truy cập** (`access_scopes`), `rooms`/`class_sessions` giới hạn **sức chứa và thiết bị sẵn có**. Hai giới hạn khác bản chất, không lẫn vào nhau.

---

## 2. Thiết kế mới — tổng quan

### 2.1 Sơ đồ nhóm bảng

```
┌─ IDENTITY (M1) ───────────────────────────────────────────────────────┐
│ persons · users · user_roles · refresh_tokens                         │
│ members · trainers · staff · trainer_specialties · audit_logs         │
└───────────────────────────────────────────────────────────────────────┘
┌─ CRM & SALES (M2) ────────────────────────────────────────────────────┐
│ leads · lead_activities · quotes · quote_items · promotions           │
│ discount_policies · commission_rules                                  │
└───────────────────────────────────────────────────────────────────────┘
┌─ MEMBERSHIP (M3) ─────────────────────────────────────────────────────┐
│ memberships · membership_prices · access_scopes                       │
│ registrations · registration_freezes · registration_transfers         │
│ member_trainers                                            │
└───────────────────────────────────────────────────────────────────────┘
┌─ ACCESS CONTROL (M4) ─────────────────────────────────────────────────┐
│ access_cards · check_ins · check_in_incidents · qr_secrets            │
└───────────────────────────────────────────────────────────────────────┘
┌─ TRAINING (M5) ───────────────────────────────────────────────────────┐
│ trainer_availability · pt_bookings · pt_sessions                      │
│ session_credit_ledger  ← SỔ CÁI TÍN DỤNG BUỔI TẬP                     │
│ rooms · class_definitions · class_schedules · class_sessions          │
│ class_bookings · class_attendances                                    │
│ exercises · workout_templates · workout_template_items                │
│ workout_plans · workout_plan_items · workout_logs · body_metrics      │
└───────────────────────────────────────────────────────────────────────┘
┌─ BILLING (M6) ────────────────────────────────────────────────────────┐
│ invoices · invoice_items · payments · payment_allocations             │
│ refunds · payment_schedules · cash_shifts · webhook_events            │
└───────────────────────────────────────────────────────────────────────┘
┌─ FINANCE (M7) ────────────────────────────────────────────────────────┐
│ revenue_schedules · revenue_recognition_entries                       │
│ expense_categories · expenses                                         │
│ payroll_rate_cards · payroll_runs · payroll_items                     │
│ accounting_periods · financial_reports                                │
└───────────────────────────────────────────────────────────────────────┘
┌─ FACILITY (M8) ───────────────────────────────────────────────────────┐
│ equipment_types · equipment_items · room_equipment                    │
│ maintenance_work_orders                                               │
└───────────────────────────────────────────────────────────────────────┘
┌─ ENGAGEMENT (M9) ─────────────────────────────────────────────────────┐
│ feedbacks · trainer_ratings · notifications · notification_templates  │
│ churn_scores · retention_tasks                                        │
└───────────────────────────────────────────────────────────────────────┘
┌─ SYSTEM ──────────────────────────────────────────────────────────────┐
│ system_settings · llm_usage_logs · outbox_events                      │
└───────────────────────────────────────────────────────────────────────┘
```

**Tổng 73 bảng.** Nhiều hơn schema hiện tại (9 bảng) nhưng **tương xứng với phạm vi 6 role + module tài chính**. Trong đó chỉ **~40 bảng là bắt buộc** (lõi đồ án), phần còn lại có thể cắt nếu thiếu thời gian — xem phân loại Must/Should/Could trong `db/00-TONG-QUAN-BANG.sql` và `docs/06-LO-TRINH.md`.

Giải thích chi tiết vì sao cần từng bảng: `db/00-TONG-QUAN-BANG.sql`. Giá trị hợp lệ của từng cột + dữ liệu mẫu: `db/01-TU-DIEN-DU-LIEU.md`.

### 2.2 Các quan hệ mới quan trọng (bổ sung cho mục "2. Các quan hệ" trong draft)

| Quan hệ | Kiểu | Ý nghĩa |
|---|---|---|
| `persons` — `users` | 1 – N | **Một người có nhiều tài khoản với role khác nhau** (yêu cầu của thầy) |
| `persons` — `members` | 1 – 0..1 | Một người có tối đa 1 hồ sơ hội viên |
| `persons` — `trainers` | 1 – 0..1 | Một người có tối đa 1 hồ sơ HLV → một người vừa là PT vừa là hội viên được |
| `members` — `trainers` | N – N (`member_trainers`) | **2–3 PT cùng chăm sóc 1 hội viên** (yêu cầu của thầy), có `role` phân biệt chính/phụ/thay thế |
| `registrations` — `session_credit_ledger` | 1 – N | Sổ cái buổi tập, append-only |
| `registrations` — `registration_freezes` | 1 – N | Lịch sử bảo lưu (yêu cầu của thầy) |
| `registrations` — `revenue_schedules` — `revenue_recognition_entries` | 1 – 1 – N | Ghi nhận doanh thu phân bổ theo kỳ |
| `invoices` — `payments` | N – N (`payment_allocations`) | Một khoản thu có thể trả nhiều hóa đơn; một hóa đơn có thể trả nhiều lần (trả góp) |
| `pt_sessions` — `payroll_items` | 1 – 1 | Mỗi buổi dạy sinh một dòng công |
| `class_definitions` — `class_schedules` — `class_sessions` | 1 – N – N | Lớp mẫu → lịch lặp hàng tuần → buổi cụ thể ngày X |
| `equipment_types` — `equipment_items` | 1 – N | Loại "máy chạy bộ" → 5 cái cụ thể, mỗi cái có serial & ngày mua riêng |

---

## 3. Các quyết định thiết kế then chốt

### 3.1 Mô hình gói tập (`memberships`)

Thay `training_time VARCHAR` bằng cấu trúc query được:

```sql
package_type    VARCHAR(20)  -- TIME_BASED | SESSION_BASED | HYBRID | DAY_PASS
duration_days   INTEGER      -- NULL nếu SESSION_BASED thuần
session_count   INTEGER      -- NULL nếu TIME_BASED thuần
includes_trainer BOOLEAN
```

| Gói ví dụ | package_type | duration_days | session_count |
|---|---|---:|---:|
| Fitness 1 tháng | `TIME_BASED` | 30 | NULL |
| Fitness 12 tháng | `TIME_BASED` | 365 | NULL |
| PT 12 buổi | `SESSION_BASED` | 180 (hạn dùng) | 12 |
| Combo 6 tháng + 24 buổi PT | `HYBRID` | 180 | 24 |
| Vé tập 1 ngày | `DAY_PASS` | 1 | NULL |

Bây giờ mới truy vấn được: *"các gói có thời hạn ≥ 90 ngày"* (điều kiện bảo lưu), *"tính end_date = start_date + duration_days"*, *"gói nào có giá/ngày rẻ nhất"*.

**Về `price_per_month` và `price_per_day` trong schema cũ:** đây là **giá trị dẫn xuất**, không nên lưu (dễ lệch với `price`). Thay bằng cột tính toán hoặc tính ở tầng ứng dụng:
```sql
price_per_day NUMERIC(14,2) GENERATED ALWAYS AS
    (CASE WHEN duration_days > 0 THEN price / duration_days END) STORED
```

### 3.2 Máy trạng thái `registrations` — định nghĩa rõ ràng

| Trạng thái | Ý nghĩa | Check-in được? | Ghi nhận DT? | Chuyển sang |
|---|---|:---:|:---:|---|
| `DRAFT` | Sale đang soạn, chưa chốt | ✗ | ✗ | PENDING_PAYMENT, CANCELLED |
| `PENDING_PAYMENT` | Đã chốt, chờ tiền | ✗ | ✗ | ACTIVE (đủ tiền), CANCELLED, EXPIRED (quá 7 ngày) |
| `ACTIVE` | Đang hiệu lực | ✓ | ✓ | FROZEN, COMPLETED, CANCELLED, TRANSFERRED |
| `FROZEN` | Đang bảo lưu | ✗ | **✗ tạm dừng** | ACTIVE (hết hạn bảo lưu hoặc kết thúc sớm) |
| `COMPLETED` | Hết thời hạn / hết buổi — kết thúc bình thường | ✗ | ✓ đã ghi nhận hết | (cuối) |
| `CANCELLED` | Hủy trước hạn theo yêu cầu | ✗ | ✗ dừng, phần chưa ghi nhận → hoàn hoặc ghi nhận thu nhập khác | REFUNDED |
| `TRANSFERRED` | Đã chuyển nhượng cho người khác | ✗ | ✗ | (cuối) |
| `REFUNDED` | Đã hoàn tiền | ✗ | ✗ | (cuối) |

> Đây là câu trả lời đầy đủ cho mục "Hạn chế" mà thầy nêu. Điểm mấu chốt: **`CANCELLED` ≠ `COMPLETED`** — hủy giữa chừng phát sinh nghĩa vụ hoàn tiền và bút toán đảo doanh thu; hết hạn tự nhiên thì không.

### 3.3 Sổ cái tín dụng buổi tập (đóng góp chính)

```sql
CREATE TABLE session_credit_ledger (
    id              BIGSERIAL PRIMARY KEY,
    registration_id BIGINT NOT NULL REFERENCES registrations(id),
    entry_type      VARCHAR(20) NOT NULL,   -- GRANT|CONSUME|REFUND|EXPIRE|ADJUST|TRANSFER
    delta           INTEGER NOT NULL,       -- +N hoặc -N
    balance_after   INTEGER NOT NULL,       -- số dư SAU bút toán này
    source_type     VARCHAR(30),            -- PT_SESSION | REGISTRATION | MANUAL | SYSTEM
    source_id       BIGINT,
    reason          TEXT,
    created_by      BIGINT REFERENCES users(id),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT chk_balance_non_negative CHECK (balance_after >= 0),
    CONSTRAINT chk_delta_nonzero        CHECK (delta <> 0)
);
```

**Không có UPDATE, không có DELETE.** Sửa sai → ghi bút toán `ADJUST` ngược lại, có lý do và người thực hiện.

**Bất biến hệ thống (kiểm chứng bằng property-based test):**
```sql
-- Với mọi registration_id: tổng delta phải bằng balance_after của bút toán mới nhất
SELECT registration_id
FROM (
  SELECT registration_id,
         SUM(delta) AS total,
         (array_agg(balance_after ORDER BY id DESC))[1] AS last_balance
  FROM session_credit_ledger GROUP BY registration_id
) t WHERE total <> last_balance;
-- ↑ PHẢI luôn trả về 0 dòng
```

**Cách ghi an toàn khi có đồng thời** (2 request cùng trừ credit):
```sql
BEGIN;
  SELECT balance_after INTO cur FROM session_credit_ledger
   WHERE registration_id = ? ORDER BY id DESC LIMIT 1 FOR UPDATE;  -- khóa dòng
  -- kiểm tra cur + delta >= 0, rồi INSERT bút toán mới
COMMIT;
```

### 3.4 Ghi nhận doanh thu (đóng góp chính)

```sql
-- Kế hoạch phân bổ, tạo 1 lần khi hợp đồng ACTIVE
revenue_schedules (
    id, registration_id, total_amount, recognition_method,   -- STRAIGHT_LINE | PER_SESSION
    start_date, end_date, total_units,                       -- số ngày hoặc số buổi
    recognized_amount, deferred_amount, status
)

-- Bút toán ghi nhận, sinh bởi job hàng đêm, APPEND-ONLY
revenue_recognition_entries (
    id, schedule_id, registration_id, recognition_date,
    amount, units, source_type, source_id, created_at
)
```

**Hai phương pháp:**

| Loại gói | Phương pháp | Công thức |
|---|---|---|
| `TIME_BASED` | `STRAIGHT_LINE` | `amount_ngày = total_amount / duration_days`, ghi nhận mỗi ngày, **bỏ qua ngày bảo lưu** |
| `SESSION_BASED` | `PER_SESSION` | `amount_buổi = total_amount / session_count`, ghi nhận khi `pt_session` chuyển `COMPLETED` |
| `HYBRID` | Kết hợp | Tách giá trị gói thành 2 phần theo tỷ lệ đã định nghĩa trong `memberships` |

**Bất biến bắt buộc (kiểm tra mỗi ngày):**
```
Với mọi registration:  Σ recognized_amount + deferred_amount == final_price
```

**Ví dụ số cụ thể** (đưa vào báo cáo để minh họa):

> Gói Fitness 12 tháng, 9.000.000đ, bắt đầu 15/01/2026
> - Ghi nhận mỗi ngày: 9.000.000 / 365 = **24.657,53đ/ngày**
> - Tháng 01 (15–31/01, 17 ngày): **419.178đ**
> - Deferred revenue cuối tháng 01: 9.000.000 − 419.178 = **8.580.822đ**
> - Nếu hội viên bảo lưu 15/03–14/04 (30 ngày): 30 ngày đó **không ghi nhận**, `end_date` đẩy từ 14/01/2027 sang 13/02/2027, tổng vẫn đúng 9.000.000đ

### 3.5 Chống trùng lịch bằng ràng buộc DB

Thay vì kiểm tra bằng code (dễ bị race condition), dùng ràng buộc của PostgreSQL:

```sql
CREATE EXTENSION IF NOT EXISTS btree_gist;

ALTER TABLE pt_sessions ADD CONSTRAINT no_trainer_overlap
  EXCLUDE USING gist (
      trainer_id WITH =,
      tstzrange(scheduled_start, scheduled_end) WITH &&
  ) WHERE (status IN ('SCHEDULED','IN_PROGRESS'));
```

→ Hai request đặt lịch cùng lúc cho cùng PT, cùng giờ: **DB tự từ chối request thứ hai**. Đây là cách đúng, không phụ thuộc vào việc code có nhớ kiểm tra hay không. Áp dụng tương tự cho `rooms` (một phòng không có 2 lớp cùng giờ).

---

## 4. Bộ số liệu mô phỏng (đảm bảo "tính hợp lý" theo yêu cầu của thầy)

> Thầy nhắc: *"nên nghiên cứu số liệu thực tế trước, có thể giả định số liệu nhưng vẫn phải giữ được tính hợp lý"*. Dưới đây là bộ tham số cho một **phòng gym tầm trung tại Hà Nội/TP.HCM, diện tích ~500m², 1.000 hội viên hoạt động**. Tất cả là `[GĐ]` — **cần đối chiếu bằng khảo sát 3–5 phòng gym thực tế** trước khi chốt vào báo cáo.

### 4.1 Bảng giá gói tập

| Gói | package_type | duration_days | session_count | Giá niêm yết | Giá/tháng quy đổi |
|---|---|---:|---:|---:|---:|
| Vé tập 1 ngày | DAY_PASS | 1 | – | 80.000 | – |
| Fitness 1 tháng | TIME_BASED | 30 | – | 700.000 | 700.000 |
| Fitness 3 tháng | TIME_BASED | 90 | – | 1.800.000 | 600.000 |
| Fitness 6 tháng | TIME_BASED | 180 | – | 3.000.000 | 500.000 |
| Fitness 12 tháng | TIME_BASED | 365 | – | 4.800.000 | 400.000 |
| Fitness + Yoga 12 tháng | TIME_BASED | 365 | – | 6.500.000 | 542.000 |
| PT 12 buổi | SESSION_BASED | 180 | 12 | 4.200.000 | 350.000đ/buổi |
| PT 24 buổi | SESSION_BASED | 270 | 24 | 7.680.000 | 320.000đ/buổi |
| PT 48 buổi | SESSION_BASED | 365 | 48 | 14.400.000 | 300.000đ/buổi |
| Combo 12 tháng + PT 24 buổi | HYBRID | 365 | 24 | 11.500.000 | – |

**Logic định giá cần giữ nhất quán (và giải thích được trong báo cáo):**
- Gói dài hơn → **giá/tháng thấp hơn** (khuyến khích cam kết dài hạn, cải thiện dòng tiền)
- Gói PT nhiều buổi → **giá/buổi thấp hơn**
- Vé lẻ 80.000đ/ngày ≫ 700.000/30 = 23.300đ/ngày → hợp lý, khuyến khích mua gói
- Combo rẻ hơn mua riêng: 4.800.000 + 7.680.000 = 12.480.000 → combo 11.500.000 (giảm ~8%)

### 4.2 Cơ cấu doanh thu tháng (1.000 hội viên)

| Nguồn | Số lượng/tháng | Đơn giá TB | Doanh thu ghi nhận |
|---|---:|---:|---:|
| Gói fitness (phân bổ từ hợp đồng đang chạy) | 1.000 HV | ~480.000 | 480.000.000 |
| Gói PT (theo buổi tiêu dùng) | 900 buổi | 330.000 | 297.000.000 |
| Vé lẻ / khách vãng lai | 250 lượt | 80.000 | 20.000.000 |
| Dịch vụ phụ (tủ đồ, nước, PT online) | – | – | 15.000.000 |
| **Tổng doanh thu ghi nhận** | | | **812.000.000** |

### 4.3 Cơ cấu chi phí tháng

| Nhóm chi phí | Số tiền | % doanh thu | Ghi chú |
|---|---:|---:|---|
| Thuê mặt bằng (500m²) | 180.000.000 | 22% | Chi phí lớn nhất, cố định |
| Lương nhân sự cố định | 165.000.000 | 20% | 1 QL 20tr, 4 lễ tân 32tr, 1 KT 15tr, 2 sale 24tr, 8 PT lương cứng 64tr, 2 vệ sinh 10tr |
| Tiền công buổi PT (biến phí) | 108.000.000 | 13% | 900 buổi × 120.000đ/buổi |
| Hoa hồng sale | 24.000.000 | 3% | ~3% doanh số mới |
| Điện, nước | 55.000.000 | 7% | Điều hòa + máy móc chạy 14h/ngày |
| Khấu hao thiết bị | 45.000.000 | 6% | Đầu tư 2,7 tỷ / 60 tháng |
| Marketing | 40.000.000 | 5% | Facebook Ads, KOL, sự kiện |
| Vệ sinh, vật tư tiêu hao | 18.000.000 | 2% | |
| Bảo trì, sửa chữa | 15.000.000 | 2% | |
| Phần mềm, internet, khác | 12.000.000 | 1,5% | **gồm ~2,2 triệu chi phí hệ thống này** |
| **Tổng chi phí** | **662.000.000** | **81,5%** | |
| **Lợi nhuận trước thuế** | **150.000.000** | **18,5%** | |

**Kiểm tra tính hợp lý:** biên lợi nhuận 18,5% nằm trong khoảng hợp lý cho phòng gym vận hành ổn định (ngành này thường 10–25%). Chi phí mặt bằng 22% doanh thu là ngưỡng cảnh báo thông thường của ngành. Con số này **giải thích được**, không phải bịa — đó là điều quan trọng khi bảo vệ.

### 4.4 Tham số hành vi để sinh dữ liệu mô phỏng

| Tham số | Giá trị `[GĐ]` |
|---|---|
| Tần suất tập TB | 2,3 lần/tuần/hội viên hoạt động |
| Phân bố giờ cao điểm | 18h–20h: 40% lượt; 6h–8h: 20%; còn lại rải đều |
| Tỷ lệ hội viên có gói PT | 18% |
| Tỷ lệ gia hạn sau gói đầu (1–3 tháng) | 45% |
| Tỷ lệ gia hạn sau gói dài (6–12 tháng) | 68% |
| Tỷ lệ hội viên "ngủ đông" (>30 ngày không đến) | 22% |
| Tỷ lệ yêu cầu bảo lưu | 6%/năm |
| Tỷ lệ hủy & đòi hoàn tiền | 2,5%/năm |
| Tỷ lệ chuyển đổi lead → hội viên | 22% |
| Tỷ lệ khách tập thử → mua gói | 35% |
| Tỷ lệ buổi PT hủy muộn (<4h) | 8% |
| Tỷ lệ thanh toán: tiền mặt / chuyển khoản / POS | 30% / 55% / 15% |
| Điểm đánh giá PT trung bình | 4,3/5 |
| Số sự cố thiết bị/tháng | 12 |

Bộ tham số này dùng để viết **script sinh dữ liệu** (`db/seed/generate.ts` hoặc `.py`) tạo ra golden dataset 6 tháng — vừa để demo, vừa để kiểm thử (xem `docs/05-KIEM-THU.md`).

---

## 5. Chiến lược migration & seed

```
db/migrations/
├── V1__identity.sql                  persons, users, members, trainers, staff
├── V2__membership.sql                memberships, prices, registrations, freezes
├── V3__access_control.sql            access_cards, check_ins, incidents
├── V4__training_core.sql             trainer_availability, pt_bookings, pt_sessions,
│                                     session_credit_ledger
├── V5__facility.sql                  rooms, equipment_types, equipment_items, maintenance
├── V6__classes.sql                   class_definitions, schedules, sessions, bookings
├── V7__billing.sql                   invoices, payments, refunds, cash_shifts
├── V8__finance.sql                   revenue_*, expenses, payroll_*
├── V9__crm_sales.sql                 leads, quotes, promotions, commission_rules
├── V10__workout.sql                  exercises, templates, plans, logs, body_metrics
├── V11__engagement.sql               feedbacks, ratings, notifications, churn, tasks
├── V12__system.sql                   settings, audit_logs, outbox_events, llm_usage_logs
├── V13__indexes.sql                  toàn bộ index (tách riêng để dễ rà soát)
└── V14__constraints_exclusion.sql    btree_gist + EXCLUDE constraints
```

**Nguyên tắc:** Flyway là **nguồn sự thật** của schema. `db/schema.sql` chỉ là bản hợp nhất để đọc hiểu và đưa vào phụ lục báo cáo — **không chạy trực tiếp** trên môi trường có dữ liệu.

**Seed dữ liệu theo tầng:**
```
seed/01_reference.sql    dữ liệu tham chiếu: nhóm chi phí, cấu hình hệ thống, rate card
seed/02_catalog.sql      gói tập, giá, khuyến mãi, phòng, loại thiết bị
seed/03_exercises.sql    thư viện bài tập (import từ Free Exercise DB, đã dịch)
seed/04_demo_small.sql   20 hội viên, 3 PT — cho dev chạy nhanh
seed/05_golden_6m/       6 tháng dữ liệu mô phỏng — cho kiểm thử & demo bảo vệ
```

---

## 6. Danh sách index cần thiết

PostgreSQL **không tự tạo index cho khóa ngoại**. Thiếu index là nguyên nhân số 1 khiến báo cáo tài chính chạy hàng chục giây.

```sql
-- Khóa ngoại truy vấn nhiều
CREATE INDEX idx_registrations_member       ON registrations(member_id);
CREATE INDEX idx_registrations_membership   ON registrations(membership_id);
CREATE INDEX idx_check_ins_member_at        ON check_ins(member_id, checked_in_at DESC);
CREATE INDEX idx_pt_sessions_trainer_start  ON pt_sessions(trainer_id, scheduled_start);
CREATE INDEX idx_pt_sessions_member         ON pt_sessions(member_id);
CREATE INDEX idx_ledger_registration        ON session_credit_ledger(registration_id, id DESC);
CREATE INDEX idx_payments_invoice           ON payment_allocations(invoice_id);
CREATE INDEX idx_rev_entries_date           ON revenue_recognition_entries(recognition_date);
CREATE INDEX idx_payroll_items_run          ON payroll_items(payroll_run_id);

-- Partial index cho truy vấn nóng
CREATE INDEX idx_reg_active_expiring ON registrations(end_date)
    WHERE status = 'ACTIVE';                      -- danh sách sắp hết hạn cho Sale
CREATE INDEX idx_sessions_upcoming   ON pt_sessions(scheduled_start)
    WHERE status = 'SCHEDULED';                   -- lịch sắp tới
CREATE INDEX idx_feedback_open       ON feedbacks(created_at)
    WHERE status IN ('OPEN','IN_PROGRESS');       -- hàng đợi xử lý

-- Tìm kiếm mờ tên (màn hình lễ tân)
CREATE EXTENSION IF NOT EXISTS pg_trgm;
CREATE INDEX idx_persons_name_trgm ON persons USING gin (full_name gin_trgm_ops);

-- Báo cáo theo kỳ
CREATE INDEX idx_expenses_period   ON expenses(period_start, period_end);
-- LƯU Ý: KHÔNG dùng index biểu thức trên date_trunc('day', checked_in_at) —
-- date_trunc(text, timestamptz) là STABLE chứ không IMMUTABLE, PostgreSQL sẽ
-- báo lỗi "functions in index expression must be marked IMMUTABLE".
-- Index (checked_in_at DESC) ở trên đã đủ cho truy vấn theo ngày
-- dạng: WHERE checked_in_at >= :from AND checked_in_at < :to
```

---

## 7. Sơ đồ ERD

ERD đầy đủ 73 bảng sẽ khó đọc trên một trang. Khuyến nghị vẽ **4 sơ đồ theo ngữ cảnh** (dùng dbdiagram.io hoặc DBeaver ER Diagram):

1. **ERD Danh tính & Hội viên** — persons, users, members, trainers, memberships, registrations, freezes
2. **ERD Vận hành** — check_ins, pt_bookings, pt_sessions, session_credit_ledger, classes, workout
3. **ERD Tài chính** — invoices, payments, revenue_*, expenses, payroll_*
4. **ERD Cơ sở vật chất & Gắn kết** — rooms, equipment, maintenance, feedbacks, churn

Kèm **một sơ đồ tổng quan mức module** (như mục 2.1) để người đọc định vị trước khi đi vào chi tiết. Đây là cách trình bày ERD chuyên nghiệp cho hệ thống lớn.

Schema SQL đầy đủ: xem `db/schema.sql`.
