# TỪ ĐIỂN DỮ LIỆU — BẢN CHÍNH THỨC (32 bảng)

> **Đây là bản dùng để viết code.** File `01-TU-DIEN-DU-LIEU.md` là bản cũ 73 bảng, chỉ giữ để đối chiếu.
>
> **Số bảng: 32** = 31 (kế hoạch tối giản) + 1 (`password_reset_tokens`)
> **Phạm vi:** một phòng gym. Không có `branches`, không có `tenant_id`.

## Các quyết định đã chốt

| # | Quyết định | Kết quả |
|---|---|---|
| 1 | Hội viên **tự đăng ký** trên app, không cần lễ tân tạo | `persons` + `users` tạo lúc đăng ký |
| 2 | **Phương án A** — `members` chỉ tạo khi **chốt mua gói đầu tiên** *(lúc tạo hợp đồng `PENDING_PAYMENT`, chưa cần trả xong tiền)* | Có tài khoản ≠ là hội viên |
| 3 | Bỏ `must_change_password` | Người dùng tự đặt mật khẩu ngay từ đầu |
| 4 | Quên mật khẩu qua **email** | Thêm bảng `password_reset_tokens` |
| 5 | `photo_url` → **`photo_key`**, bucket **private** + presigned URL | Tuân thủ NĐ 13/2023 |
| 6 | `users.status`: 3 giá trị → **2** (`ACTIVE` / `LOCKED`) | Thêm `locked_reason`, `locked_until` |
| 7 | Tách khóa thủ công và khóa tự động | `locked_until` (Admin) vs `auto_locked_until` (hệ thống) |
| 8 | Gộp `trainers` + `staff` → **`employees`** | Bỏ cặp khóa ngoại lặp ở `payroll_items` |
| 9 | ~~`registrations` gánh vai trò báo giá~~ | **Đã hủy bởi quyết định 12** |
| 10 | `pt_sessions` **gánh luôn đặt lịch** | `status = 'PENDING_TRAINER'`, bỏ `pt_bookings` |
| 11 | Hoàn tiền = `payments` với `amount` **âm** | Bỏ bảng `refunds` |
| 12 | **Bỏ hẳn vai trò báo giá** — giá và khuyến mãi đều **công khai trên web**, Sale không thương lượng giá riêng | `registrations`: bỏ `lead_id`, `valid_until`, trạng thái `DRAFT` + `EXPIRED_QUOTE`, 2 ràng buộc; `member_id` thành `NOT NULL` |
| 13 | `leads` **bắt buộc liên kết `persons`** | Bỏ `full_name`/`phone`/`email` trùng lặp; `interest` → `interested_membership_id`; bỏ `converted_member_id` |
| 14 | `members.status`: 3 giá trị → **2** (`ACTIVE` / `BLACKLISTED`) | Bỏ `INACTIVE` vì là dữ liệu suy ra được từ `registrations` — hết một job đêm và một nguồn sai lệch |
| ⏳ | **Churn (cảnh báo bỏ tập)** — chưa quyết | Nếu làm → +2 bảng = **34** |

---

## Quy ước chung *(áp dụng mọi bảng, không lặp lại bên dưới)*

| Cột | Kiểu | Ý nghĩa | Mẫu |
|---|---|---|---|
| `id` | `BIGSERIAL` | Khóa chính tự tăng | `1`, `123` |
| `created_at` | `TIMESTAMPTZ` | Thời điểm tạo | `2026-07-15 09:23:41+07` |
| `updated_at` | `TIMESTAMPTZ` | Sửa cuối, trigger tự cập nhật | `2026-08-01 14:05:00+07` |
| `deleted_at` | `TIMESTAMPTZ` | Xóa mềm. `NULL` = còn dùng | `NULL` |

**Ba quy tắc bắt buộc:**
1. **Tiền luôn `NUMERIC(14,2)`**, đơn vị VND. `4200000.00` = 4,2 triệu. Không dùng `float`.
2. **Enum viết HOA_GẠCH_DƯỚI**, lưu bằng `VARCHAR` + `CHECK`.
3. **Thời điểm luôn `TIMESTAMPTZ`**, giờ Việt Nam `+07`.

**Nhân vật dùng xuyên suốt ví dụ:**

| Người | Vai trò | Mã |
|---|---|---|
| Nguyễn Văn An | Hội viên | `MB-000123` |
| Trần Bình | Huấn luyện viên | `EM-005` |
| Phạm Dũng | Nhân viên sale | `EM-012` |
| Vũ Thị Mai | Lễ tân | `EM-018` |
| Lê Thị Hoa | Kế toán | `EM-021` |

---

## Mục lục — 32 bảng

| Nhóm | Bảng |
|---|---|
| [1. Danh tính (6)](#nhóm-1--danh-tính) | `persons` · `users` · `password_reset_tokens` · `members` · `employees` · `audit_logs` |
| [2. Gói & Hợp đồng (4)](#nhóm-2--gói-tập--hợp-đồng) | `memberships` · `registrations` · `registration_freezes` · `member_trainers` |
| [3. Check-in (1)](#nhóm-3--check-in) | `check_ins` |
| [4. Buổi tập PT (2)](#nhóm-4--buổi-tập-pt--sổ-cái) | `pt_sessions` · `session_credit_ledger` |
| [5. Lớp nhóm (2)](#nhóm-5--lớp-học-nhóm) | `class_sessions` · `class_bookings` |
| [6. Thiết bị (1)](#nhóm-6--thiết-bị) | `equipment` |
| [7. Thanh toán (3)](#nhóm-7--thanh-toán) | `invoices` · `payments` · `cash_shifts` |
| [8. Tài chính (5)](#nhóm-8--tài-chính--lương) | `revenue_schedules` · `revenue_recognition_entries` · `expenses` · `payroll_runs` · `payroll_items` |
| [9. Bán hàng (1)](#nhóm-9--bán-hàng) | `leads` |
| [10. Bài tập & Cơ thể (5)](#nhóm-10--bài-tập--chỉ-số-cơ-thể) | `exercises` · `workout_plans` · `workout_plan_items` · `workout_logs` · `body_metrics` |
| [11. Phản hồi (1)](#nhóm-11--phản-hồi) | `feedbacks` |
| [12. Hệ thống (1)](#nhóm-12--hệ-thống) | `system_settings` |

---
---

# NHÓM 1 — DANH TÍNH

## `persons` — con người thật, 1 dòng = 1 người

Gánh thêm nội dung của 2 bảng cũ: `access_cards` (→ `card_uid`) và `qr_secrets` (→ `qr_secret_enc`).

| Cột | Kiểu | Giá trị / Ý nghĩa | Mẫu |
|---|---|---|---|
| `full_name` | `VARCHAR(150)` | Họ tên. Bắt buộc | `Nguyễn Văn An` |
| `gender` | `VARCHAR(10)` | `MALE` \| `FEMALE` \| `OTHER` \| `NULL` | `MALE` |
| `birthday` | `DATE` | Phải < hôm nay | `1998-03-22` |
| `national_id` | `VARCHAR(20)` | CCCD. **UNIQUE**, mã hóa ở tầng ứng dụng | `001098012345` |
| `phone` | `VARCHAR(20)` | **UNIQUE** — định danh chính ở VN. Định dạng `0[3\|5\|7\|8\|9]xxxxxxxx` | `0912345678` |
| `email` | `VARCHAR(150)` | **UNIQUE**, có thể `NULL` | `an.nguyen@gmail.com` |
| `address` | `VARCHAR(255)` | | `56 Trần Duy Hưng, Cầu Giấy, Hà Nội` |
| **`photo_key`** | `VARCHAR(500)` | **Key trong MinIO**, KHÔNG phải URL công khai. Bucket private | `persons/12/photo_20260715.jpg` |
| **`card_uid`** | `VARCHAR(64)` | UID thẻ RFID. **UNIQUE**, `NULL` = chưa phát thẻ | `04A3B2C1D5E680` |
| `card_issued_at` | `TIMESTAMPTZ` | Ngày phát thẻ | `2026-07-15 10:35:00+07` |
| **`qr_secret_enc`** | `BYTEA` | Khóa 32 byte sinh QR động, **mã hóa AES-256-GCM** | `\x8f3a2b...` |
| `emergency_contact_name` | `VARCHAR(150)` | Người liên hệ khi có sự cố y tế | `Nguyễn Thị Lan` |
| `emergency_contact_phone` | `VARCHAR(20)` | | `0987654321` |

### `photo_key` hoạt động thế nào

```
1. Lễ tân chụp ảnh chân dung khi hội viên MUA GÓI ĐẦU TIÊN
   → backend kiểm định dạng thật (magic bytes), resize 800×800
   → PUT lên MinIO, bucket PRIVATE
   → CSDL lưu ĐÚNG chuỗi key (~40 byte)

2. Khi cần hiển thị: backend kiểm quyền → sinh presigned URL TTL 5 phút
   → trình duyệt tải THẲNG từ MinIO, không qua backend

3. Khi hội viên quẹt thẻ/quét QR → màn hình quầy bật ảnh cỡ lớn
   → LỄ TÂN NHÌN, đối chiếu với người đứng trước mặt → quyết định
```

> **Vì sao lưu key chứ không lưu bytes ảnh trong CSDL:** 1.000 hội viên × 200KB = 200MB làm `pg_dump` chậm; mọi `SELECT *` kéo theo bytes ảnh; và nghiêm trọng nhất — truyền ảnh qua backend **giữ connection CSDL**, giờ cao điểm 20 lượt check-in cùng lúc là hết pool.
>
> **Vì sao là `key` chứ không phải `url` công khai:** ảnh chân dung là dữ liệu cá nhân. URL công khai = ai có link cũng xem được → vi phạm NĐ 13/2023.

---

## `users` — tài khoản đăng nhập

1 `person` → nhiều `users` (đáp ứng yêu cầu *"1 người có thể có nhiều tài khoản với vai trò khác nhau"*).

| Cột | Kiểu | Giá trị / Ý nghĩa | Mẫu |
|---|---|---|---|
| `person_id` | `BIGINT` | Trỏ `persons.id`. Bắt buộc | `12` |
| `username` | `VARCHAR(100)` | **UNIQUE**. Thường dùng SĐT | `0912345678` |
| `password_hash` | `VARCHAR(255)` | BCrypt cost 12 hoặc Argon2id | `$2a$12$N9qo8uLOickgx2ZM...` |
| `primary_role` | `VARCHAR(20)` | **6 giá trị** — xem bảng dưới | `MEMBER` |
| `status` | `VARCHAR(20)` | **`ACTIVE`** \| **`LOCKED`** — chỉ 2 giá trị | `ACTIVE` |
| `locked_reason` | `VARCHAR(255)` | **Bắt buộc** khi `status='LOCKED'` | `NULL` |
| `locked_until` | `DATE` | Admin khóa đến ngày. `NULL` = vô thời hạn | `NULL` |
| `locked_by` | `BIGINT` | Admin nào khóa | `NULL` |
| `failed_attempts` | `SMALLINT` | Số lần nhập sai liên tiếp. Về `0` khi đăng nhập đúng | `0` |
| `auto_locked_until` | `TIMESTAMPTZ` | **Hệ thống tự đặt** khi sai >5 lần, tự hết sau 15 phút | `NULL` |
| `email_verified_at` | `TIMESTAMPTZ` | Đã xác minh email chưa (cần cho quên mật khẩu) | `2026-07-15 10:05:00+07` |
| `last_login_at` | `TIMESTAMPTZ` | | `2026-08-01 18:02:11+07` |

### `primary_role` — 6 giá trị

| Giá trị | Ai | Thấy giao diện gì |
|---|---|---|
| `ADMIN` | Chủ phòng gym | Dashboard tổng, quản lý gói tập, nhân sự, cấu hình |
| `MEMBER` | Khách hàng | App: gói của tôi, QR check-in, đặt lịch PT, giáo án, chatbot |
| `TRAINER` | Huấn luyện viên | App: lịch dạy, xác nhận buổi tập, bảng lương |
| `SALE` | Nhân viên kinh doanh | Web: lead, tạo hợp đồng, KPI hoa hồng |
| `RECEPTIONIST` | Lễ tân | Web: màn hình quầy, thu tiền, ca làm việc |
| `ACCOUNTANT` | Kế toán | Web: doanh thu, chi phí, chạy lương, báo cáo |

> ⚠️ **API đăng ký công khai TUYỆT ĐỐI không nhận tham số `role` từ client** — luôn gán cứng `MEMBER` trong code. Tài khoản nhân viên chỉ Admin tạo được. Đây là lỗ hổng leo thang đặc quyền kinh điển.

### `status` — chỉ 2 giá trị

| Giá trị | Nghĩa | Đăng nhập? |
|---|---|:---:|
| `ACTIVE` | Bình thường | ✅ |
| `LOCKED` | Bị khóa — lý do và thời hạn ghi ở `locked_reason` / `locked_until` | ❌ |

Một trạng thái `LOCKED` biểu diễn được mọi tình huống:

| Tình huống | `locked_reason` | `locked_until` |
|---|---|---|
| PT nghỉ thai sản | `"Nghỉ thai sản, dự kiến trở lại 01/2027"` | `2027-01-15` |
| Hội viên vi phạm nội quy | `"Vi phạm nội quy ngày 12/08"` | `2026-09-12` |
| Nhân viên nghỉ việc | `"Đã nghỉ việc từ 30/09/2026"` | `NULL` (vô thời hạn) |
| Nghi tài khoản bị chiếm | `"Đăng nhập bất thường, đang điều tra"` | `NULL` |

> **`LOCKED` luôn mở lại được** — Admin đặt `status='ACTIVE'` bất cứ lúc nào. Job đêm tự mở khóa khi `locked_until` đã qua.
>
> **Vì sao tách `locked_until` và `auto_locked_until`:** nếu dùng chung một cột, PT bị Admin khóa đến 01/2027 mà ai đó thử sai mật khẩu 5 lần → hệ thống ghi đè thành `now()+15 phút` → **15 phút sau tự mở, phá lệnh khóa của Admin**.

### ⚠️ Bảo mật `password_hash`

Bắt buộc loại khỏi 4 nơi:

```java
1. DTO          → không có trường password trong record trả về API
2. @JsonIgnore  → phòng thủ lớp 2 nếu lỡ trả entity
3. toString()   → override, chỉ in id + username (chống lọt vào log)
4. audit_logs   → lọc khỏi before_data/after_data trước khi ghi
```

### ⚠️ Ba trạng thái ĐỘC LẬP — chỗ hay nhầm nhất 

| Tình huống | `users.status` | `members.status` | `registrations.status` | Đăng nhập? | Check-in? |
|---|---|---|---|:---:|:---:|
| Tải app, chưa mua gói | `ACTIVE` | **không có dòng** | – | ✅ | ❌ |
| Hội viên đang có gói | `ACTIVE` | `ACTIVE` | `ACTIVE` | ✅ | ✅ |
| **Hết gói, chưa gia hạn** | **`ACTIVE`** | **`ACTIVE`** | `COMPLETED` | ✅ | ❌ |
| Đang bảo lưu | `ACTIVE` | `ACTIVE` | `FROZEN` | ✅ | ❌ |
| Bị cấm cửa vì vi phạm nội quy | `LOCKED` | **`BLACKLISTED`** | `CANCELLED` | ❌ | ❌ |
| Nhân viên nghỉ việc | `LOCKED` | – | – | ❌ | – |
| Sai mật khẩu 6 lần | `ACTIVE` + `auto_locked_until` | `ACTIVE` | `ACTIVE` | ❌ 15 phút | ✅ |

> **Hết gói KHÔNG khóa tài khoản.** Hội viên vẫn phải đăng nhập được để xem lịch sử tập luyện và **gia hạn online** — đó là kênh bán lại quan trọng nhất. Khóa tài khoản khi hết gói là tự cắt đứt cơ hội bán hàng.
>
> **Hết gói cũng KHÔNG đổi `members.status`** *(quyết định 14)*. Cột `members.status` chỉ trả lời *"có bị cấm cửa không"* — còn *"có đang tập không"* đọc thẳng từ `registrations.status='ACTIVE'`. Xem giải thích ở bảng `members`.
>
> Dòng cuối cũng đáng chú ý: khóa đăng nhập **không** chặn check-in bằng thẻ. Quên mật khẩu app thì vẫn phải vào tập được.

---

## `password_reset_tokens` — đặt lại mật khẩu qua email - triển khai sau

| Cột | Kiểu | Giá trị / Ý nghĩa | Mẫu |
|---|---|---|---|
| `user_id` | `BIGINT` | Của tài khoản nào | `205` |
| `token_hash` | `VARCHAR(128)` | **SHA-256 của token**. **UNIQUE**. Không lưu token thô | `a3f5b2c8...` (64 ký tự hex) |
| `expires_at` | `TIMESTAMPTZ` | Hết hạn, `now() + 30 phút` | `2026-08-05 14:30:00+07` |
| `used_at` | `TIMESTAMPTZ` | Đã dùng lúc nào. `NULL` = chưa dùng. **Dùng đúng 1 lần** | `NULL` |
| `requested_ip` | `INET` | IP người yêu cầu (phát hiện lạm dụng) | `113.161.45.22` |

**Luồng đặt lại mật khẩu:**

```
1. POST /auth/forgot-password { email }
   → Tìm user theo email. KHÔNG tiết lộ email có tồn tại hay không
     (luôn trả 200 "Nếu email tồn tại, chúng tôi đã gửi hướng dẫn")
     → chống dò email
   → Sinh token ngẫu nhiên 32 byte
   → Lưu SHA-256(token) vào bảng, expires_at = now()+30 phút
   → Gửi email chứa link: https://app.gym.vn/reset?token=<token thô>

2. POST /auth/reset-password { token, newPassword }
   → Tra SHA-256(token) trong bảng
   → Kiểm: used_at IS NULL  VÀ  expires_at > now()
   → Đổi mật khẩu → đặt used_at = now()
   → THU HỒI TOÀN BỘ phiên đăng nhập cũ của user đó
```

> **Rate limit bắt buộc:** tối đa 3 yêu cầu/email/giờ và 10 yêu cầu/IP/giờ — nếu không, ai đó có thể spam email nạn nhân.
>
> **Phương án dự phòng cho người không dùng email:** đến quầy, lễ tân xác minh CCCD rồi kích hoạt đặt lại mật khẩu trực tiếp.

---

## `members` — hồ sơ hội viên

> ⭐ **Chỉ tạo khi CHỐT MUA GÓI ĐẦU TIÊN** (phương án A đã chốt) — tạo cùng lúc với hợp đồng `PENDING_PAYMENT`, **trước** khi khách trả tiền, vì `registrations.member_id` là `NOT NULL`. Người tự đăng ký app mà chưa mua gói **không có dòng nào** trong bảng này.

| Cột | Kiểu | Giá trị / Ý nghĩa | Mẫu |
|---|---|---|---|
| `person_id` | `BIGINT` | Trỏ `persons.id`. **UNIQUE** | `12` |
| `member_code` | `VARCHAR(20)` | Mã in trên thẻ. **UNIQUE**. Chỉ cấp khi mua gói | `MB-000123` |
| `join_date` | `DATE` | **Ngày chốt mua gói đầu tiên** (không phải ngày tạo tài khoản) | `2026-07-15` |
| `source` | `VARCHAR(30)` | **5 giá trị:** `WALK_IN` \| `HOTLINE` \| `WEB_FORM` \| `REFERRAL` \| `APP_SELF` | `APP_SELF` |
| `referred_by` | `BIGINT` | Hội viên nào giới thiệu | `45` |
| `health_note` | `TEXT` | Bệnh nền, chấn thương. **PT và chatbot dùng để loại bài chống chỉ định** | `Đau lưng dưới, tránh deadlift nặng` |
| `goal` | `VARCHAR(30)` | `LOSE_FAT` \| `GAIN_MUSCLE` \| `ENDURANCE` \| `HEALTH` | `GAIN_MUSCLE` |
| `status` | `VARCHAR(20)` | **2 giá trị:** `ACTIVE` (bình thường) \| `BLACKLISTED` (bị cấm cửa) — **không phản ánh còn gói hay hết gói** | `ACTIVE` |
| `last_visit_at` | `TIMESTAMPTZ` | Lần check-in gần nhất. Cập nhật mỗi lượt vào | `2026-08-01 18:05:00+07` |

**Hệ quả với code:** mọi API cần hội viên thật phải gọi một hàm dùng chung:

```java
public Member requireMember(Long personId) {
    return memberRepo.findByPersonId(personId)
        .orElseThrow(() -> new MembershipRequiredException(
            "Bạn cần mua gói tập để sử dụng tính năng này"));   // HTTP 403
}
```

| API | Cần `members`? |
|---|:---:|
| Xem gói tập, bảng giá · **Chat với bot** · Xem thư viện bài tập | ❌ |
| Check-in · Đặt lịch PT · Chỉ số cơ thể · Giáo án · Lịch sử check-in | ✅ |

### ⚠️ `status` chỉ có 2 giá trị — vì sao bỏ `INACTIVE` *(quyết định 14)*

| Giá trị | Nghĩa | Ai đặt |
|---|---|---|
| `ACTIVE` | Bình thường — **kể cả khi đã hết gói** | Mặc định lúc tạo |
| `BLACKLISTED` | Bị cấm cửa vì vi phạm nội quy | Admin quyết định thủ công |

> **`INACTIVE` là dữ liệu suy ra được, không nên lưu.** "Hội viên còn gói hay không" đã có câu trả lời chính xác ở `registrations` — có hợp đồng nào `status='ACTIVE'` hay không. Lưu thêm ở `members.status` nghĩa là **cùng một sự thật nằm ở hai chỗ**, phải có job đêm quét toàn bộ để lật `ACTIVE ↔ INACTIVE` mỗi khi hợp đồng hết hạn. Job lỗi một hôm là dữ liệu sai lệch mà không ai biết.
>
> `BLACKLISTED` thì ngược lại — **không suy ra được từ đâu cả**, đó là quyết định của Admin, nên bắt buộc phải lưu.

**Truy vấn thay thế** cho `WHERE members.status = 'ACTIVE'` cũ:

```sql
-- Danh sách hội viên ĐANG TẬP (còn hợp đồng hiệu lực)
SELECT m.*
FROM   members m
JOIN   registrations r ON r.member_id = m.id AND r.status = 'ACTIVE'
WHERE  m.status <> 'BLACKLISTED';

-- Danh sách hội viên ĐÃ HẾT GÓI (dữ liệu để chăm sóc gia hạn)
SELECT m.*
FROM   members m
WHERE  m.status <> 'BLACKLISTED'
  AND  NOT EXISTS (SELECT 1 FROM registrations r
                   WHERE r.member_id = m.id AND r.status IN ('ACTIVE','FROZEN'));
```

> Người chưa mua gói **vẫn chat được với bot tư vấn** — đó chính là kênh chuyển đổi khách tiềm năng thành hội viên.
>
> Lợi ích của phương án A: bảng `members` chỉ chứa người **đã thật sự mua gói**, không lẫn với người chỉ tải app xem thử. Lưu ý nhỏ: vì dòng được tạo ngay lúc chốt mua (chưa trả tiền), **số hội viên đang hoạt động phải đếm theo hợp đồng `ACTIVE`**, không dùng `COUNT(*) FROM members`.

---

## `employees` — nhân sự *(gộp `trainers` + `staff` + `trainer_specialties`)*

| Cột | Kiểu | Giá trị / Ý nghĩa | Mẫu |
|---|---|---|---|
| `person_id` | `BIGINT` | Trỏ `persons.id`. **UNIQUE** | `18` |
| `employee_code` | `VARCHAR(20)` | **UNIQUE** | `EM-005` |
| `department` | `VARCHAR(30)` | **4 giá trị** — xem dưới | `TRAINING` |
| `position` | `VARCHAR(80)` | Chức danh cụ thể | `Huấn luyện viên cá nhân` |
| `employment_type` | `VARCHAR(20)` | `FULL_TIME` \| `PART_TIME` \| `FREELANCE` | `FULL_TIME` |
| `base_salary` | `NUMERIC(14,2)` | Lương cứng/tháng | `8000000.00` |
| `start_date` / `end_date` | `DATE` | Vào làm / nghỉ. `end_date NULL` = còn làm | `2024-03-01` / `NULL` |
| `status` | `VARCHAR(20)` | `ACTIVE` \| `ON_LEAVE` \| `RESIGNED` | `ACTIVE` |
| **— Chỉ có nghĩa khi `department='TRAINING'` —** | | | |
| `level` | `VARCHAR(20)` | `JUNIOR` \| `SENIOR` \| `MASTER` — quyết định đơn giá buổi tập | `SENIOR` |
| `specialties` | `JSONB` | Mảng chuyên môn | `["FITNESS","REHAB"]` |
| `bio` | `TEXT` | Hội viên đọc khi chọn PT | `8 năm kinh nghiệm, chứng chỉ NASM-CPT` |
| `max_members` | `INTEGER` | Nhận tối đa bao nhiêu học viên | `30` |
| `rating_avg` | `NUMERIC(3,2)` | Điểm TB 1.00–5.00, tính từ `feedbacks` | `4.60` |
| `rating_count` | `INTEGER` | Số lượt đánh giá | `87` |

### `department` — 4 giá trị, khớp đúng 4 role nhân viên

| Giá trị | Nghĩa | Ứng với `users.primary_role` | Có dùng cột PT không? |
|---|---|---|:---:|
| `TRAINING` | **Huấn luyện viên (PT)** | `TRAINER` | ✅ |
| `SALES` | Nhân viên kinh doanh | `SALE` | ❌ |
| `FRONT_DESK` | Lễ tân | `RECEPTIONIST` | ❌ |
| `ACCOUNTING` | Kế toán | `ACCOUNTANT` | ❌ |

> Mỗi `employees` luôn đi kèm đúng 1 `users` có `primary_role` tương ứng — hai giá trị này khớp nhau 1-1, không lệch pha.
>
> **Không có `MANAGEMENT` hay `MAINTENANCE`:** đề bài chỉ chốt 6 role (Admin, Member, PT, Sale, Receptionist, Accountant) — không có role "Quản lý" hay "Kỹ thuật" riêng. Người điều phối chung chính là **Admin**. Sửa chữa thiết bị xử lý qua `feedbacks.assigned_to` (giao cho bất kỳ `users.id` nào — Admin, lễ tân, hoặc đơn vị thuê ngoài ghi ở `feedbacks.repair_cost`), không cần nhân sự cơ hữu có department riêng.

**Giá trị `specialties`:** `FITNESS` · `YOGA` · `CROSSFIT` · `BOXING` · `REHAB` · `NUTRITION`

> **Vì sao gộp `trainers` + `staff`:** PT cũng là nhân viên, cũng nhận lương, cũng có KPI. Tách 2 bảng buộc `payroll_items` và `check_ins` phải có **cặp khóa ngoại** `trainer_id` / `staff_id` với ràng buộc "đúng một cái khác NULL" — rườm rà và dễ sai. Gộp lại chỉ còn `employee_id`.
>
> Cái giá: 6 cột `NULL` với nhân viên không phải PT (`level`, `specialties`, `bio`, `max_members`, `rating_avg`, `rating_count`). Chấp nhận được với quy mô ~20 nhân sự.

---

## `audit_logs` — nhật ký thao tác *(bảng kỹ thuật, không vẽ ERD)*

| Cột | Kiểu | Giá trị / Ý nghĩa | Mẫu |
|---|---|---|---|
| `actor_id` | `BIGINT` | Ai thao tác (`users.id`) | `7` |
| `action` | `VARCHAR(50)` | `CREATE` \| `UPDATE` \| `DELETE` \| `APPROVE` \| `REJECT` \| `LOGIN` \| `LOGOUT` \| `EXPORT` \| `ANONYMIZE` | `UPDATE` |
| `entity_type` | `VARCHAR(60)` | Tên bảng | `registrations` |
| `entity_id` | `BIGINT` | ID bản ghi | `4521` |
| `before_data` | `JSONB` | Giá trị trước. **Đã lọc `password_hash`** | `{"status":"ACTIVE","end_date":"2026-12-31"}` |
| `after_data` | `JSONB` | Giá trị sau | `{"status":"FROZEN","end_date":"2027-01-30"}` |
| `reason` | `TEXT` | Bắt buộc với thao tác nhạy cảm | `Hội viên xin bảo lưu 30 ngày` |
| `ip_address` | `INET` | | `192.168.1.45` |
| `user_agent` | `VARCHAR(500)` | | `Mozilla/5.0 ... Chrome/128.0` |

---
---

# NHÓM 2 — GÓI TẬP & HỢP ĐỒNG

## `memberships` — danh mục gói tập

Gánh thêm `membership_prices` (→ cột `price`) và `access_scopes` (→ cột `area_codes`).

| Cột | Kiểu | Giá trị / Ý nghĩa | Mẫu |
|---|---|---|---|
| `code` | `VARCHAR(30)` | **UNIQUE** | `FIT-12M` |
| `name` | `VARCHAR(150)` | Tên hiển thị | `Gói Fitness 12 tháng` |
| `package_type` | `VARCHAR(20)` | **4 giá trị** — xem dưới | `TIME_BASED` |
| `duration_days` | `INTEGER` | Thời hạn tính bằng **ngày** | `365` |
| `session_count` | `INTEGER` | Số **buổi PT** trong gói | `NULL` |
| **`price`** | `NUMERIC(14,2)` | **Giá niêm yết hiện tại** | `4800000.00` |
| `includes_trainer` | `BOOLEAN` | Có kèm PT không | `FALSE` |
| `pt_value_ratio` | `NUMERIC(4,3)` | Chỉ dùng cho `HYBRID`: % giá trị thuộc phần PT | `NULL` |
| **`area_codes`** | `JSONB` | Được vào khu vực nào | `["GYM_FLOOR","GROUP_CLASS","SAUNA"]` |
| `max_freeze_days` | `INTEGER` | Bảo lưu tối đa bao nhiêu ngày/năm. `0` = không cho | `30` |
| `max_freeze_times` | `SMALLINT` | Tối đa mấy lần | `2` |
| `is_refundable` | `BOOLEAN` | Được hoàn tiền không | `TRUE` |
| `description` | `TEXT` | | `Tập không giới hạn mọi khung giờ...` |
| `display_order` | `INTEGER` | Thứ tự hiển thị trên app | `5` |
| `status` | `VARCHAR(20)` | `ACTIVE` (đang bán) \| `ARCHIVED` (ngừng bán, hợp đồng cũ vẫn chạy) | `ACTIVE` |

### `package_type` — 4 giá trị

| Giá trị | Nghĩa | `duration_days` | `session_count` |
|---|---|---|---|
| `TIME_BASED` | Tập không giới hạn trong X ngày | Bắt buộc | `NULL` |
| `SESSION_BASED` | Mua N buổi PT, có hạn dùng | Hạn dùng | Bắt buộc |
| `HYBRID` | Combo: vừa thời hạn vừa số buổi | Bắt buộc | Bắt buộc |
| `DAY_PASS` | Vé lẻ 1 ngày | `= 1` | `NULL` |

**Giá trị `area_codes`:** `GYM_FLOOR` · `YOGA_STUDIO` · `POOL` · `SAUNA` · `GROUP_CLASS`

### Dữ liệu mẫu

| code | name | package_type | duration_days | session_count | price | max_freeze_days |
|---|---|---|---:|---:|---:|---:|
| `DAY-01` | Vé tập 1 ngày | `DAY_PASS` | 1 | – | 80.000 | 0 |
| `FIT-01M` | Gói Fitness 1 tháng | `TIME_BASED` | 30 | – | 700.000 | 0 |
| `FIT-03M` | Gói Fitness 3 tháng | `TIME_BASED` | 90 | – | 1.800.000 | 14 |
| `FIT-06M` | Gói Fitness 6 tháng | `TIME_BASED` | 180 | – | 3.000.000 | 30 |
| `FIT-12M` | Gói Fitness 12 tháng | `TIME_BASED` | 365 | – | 4.800.000 | 30 |
| `PT-12` | Gói PT 12 buổi | `SESSION_BASED` | 180 | 12 | 4.200.000 | 0 |
| `PT-24` | Gói PT 24 buổi | `SESSION_BASED` | 270 | 24 | 7.680.000 | 0 |
| `COMBO-12M-PT24` | Combo 12 tháng + PT 24 buổi | `HYBRID` | 365 | 24 | 11.500.000 | 30 |

> **Vì sao tách `training_time` cũ thành 3 cột:** chuỗi `'1 tháng'`, `'12 buổi'` **không query được** — không lọc được *"gói ≥ 90 ngày"*, không tính được `end_date = start_date + duration_days`, và so sánh chuỗi cho ra thứ tự sai (`'1 năm'` < `'3 tháng'` vì `'1' < '3'`).
>
> **Vì sao bỏ bảng `membership_prices`:** lịch sử giá **đã nằm sẵn trong snapshot của `registrations`** (`list_price`, `final_price`). Bảng versioning chỉ cần khi muốn *hẹn giờ đổi giá trước* — tính năng không thiết yếu.

---

## `registrations` — HỢP ĐỒNG ⭐

Bảng quan trọng nhất hệ thống. Gánh thêm `promotions` (qua `discount_amount` + `discount_reason`).

> **Không còn vai trò báo giá** *(quyết định 12)*: giá gói và chương trình khuyến mãi đều **công khai trên web**, khách xem trước khi quyết định — không có bước Sale thương lượng giá riêng nên không cần lưu báo giá thành dữ liệu. Mỗi dòng ở đây là **một hợp đồng đã chốt**, luôn thuộc về một hội viên thật.

| Cột | Kiểu | Giá trị / Ý nghĩa | Mẫu |
|---|---|---|---|
| `registration_code` | `VARCHAR(30)` | **UNIQUE** | `REG-2026-000451` |
| **`member_id`** | `BIGINT` | Hội viên. **`NOT NULL`** — mọi hợp đồng đều thuộc một hội viên thật | `123` |
| `membership_id` | `BIGINT` | Mua gói nào | `7` |
| `sold_by` | `BIGINT` | Nhân viên sale (`employees.id`) — cơ sở tính hoa hồng | `12` |
| `assigned_trainer_id` | `BIGINT` | PT chính phụ trách | `5` |
| **— Snapshot điều khoản (sao chép lúc ký, KHÔNG đọc ngược) —** | | | |
| `package_type` | `VARCHAR(20)` | | `SESSION_BASED` |
| `duration_days` | `INTEGER` | | `180` |
| `sessions_total` | `INTEGER` | Tổng số buổi được cấp | `12` |
| `list_price` | `NUMERIC(14,2)` | Giá niêm yết lúc ký | `4200000.00` |
| `discount_amount` | `NUMERIC(14,2)` | Số tiền giảm | `420000.00` |
| `discount_reason` | `VARCHAR(255)` | **Bắt buộc khi có giảm giá** | `Khuyến mãi hè 2026, giảm 10%` |
| `discount_approved_by` | `BIGINT` | Ai duyệt (khi vượt hạn mức) | `NULL` |
| `final_price` | `NUMERIC(14,2)` | `= list_price − discount_amount` | `3780000.00` |
| **— Thời gian —** | | | |
| `contract_date` | `DATE` | Ngày ký | `2026-07-15` |
| `start_date` | `DATE` | Bắt đầu hiệu lực. `NULL` khi chưa kích hoạt | `2026-07-15` |
| `end_date` | `DATE` | Hết hạn. **Đẩy lùi khi bảo lưu** | `2027-01-11` |
| `activated_at` | `TIMESTAMPTZ` | Thời điểm chuyển `ACTIVE` | `2026-07-15 10:30:00+07` |
| `closed_at` | `TIMESTAMPTZ` | Thời điểm kết thúc | `NULL` |
| `status` | `VARCHAR(20)` | **6 trạng thái** — xem dưới | `ACTIVE` |
| `close_reason` | `VARCHAR(255)` | Lý do kết thúc | `NULL` |
| `note` | `TEXT` | | `Khách yêu cầu tập buổi tối` |

### `status` — 6 trạng thái

| Giá trị | Nghĩa | Check-in? | Ghi nhận DT? | Chuyển sang |
|---|---|:---:|:---:|---|
| `PENDING_PAYMENT` | **Khách đã chốt mua, chờ trả tiền** | ❌ | ❌ | `ACTIVE`, `CANCELLED` |
| `ACTIVE` | Đang hiệu lực | ✅ | ✅ | `FROZEN`, `COMPLETED`, `CANCELLED` |
| `FROZEN` | Đang bảo lưu | ❌ | ⏸️ tạm dừng | `ACTIVE` |
| `COMPLETED` | Hết hạn / hết buổi — **kết thúc bình thường** | ❌ | ✅ đã ghi hết | *(cuối)* |
| `CANCELLED` | **Hủy giữa chừng** | ❌ | ❌ dừng | `REFUNDED` |
| `REFUNDED` | Đã hoàn tiền | ❌ | ❌ | *(cuối)* |

> **Hợp đồng bị bỏ ngang:** khách chốt mua rồi không trả tiền → job đêm chuyển `PENDING_PAYMENT` quá 48h sang `CANCELLED`. Vì thế **số hội viên thật đếm theo hợp đồng `ACTIVE`**, không đếm `COUNT(*) FROM members`.

> **`CANCELLED` khác `COMPLETED` thế nào:** `COMPLETED` = khách dùng hết dịch vụ đã mua, phòng gym hoàn thành nghĩa vụ, **không hoàn tiền**. `CANCELLED` = khách bỏ giữa chừng, phòng gym **còn nợ dịch vụ** → phải tính giá trị còn lại và có thể hoàn tiền. Hai sự kiện kế toán khác hẳn nhau.

### Ràng buộc quan trọng

```sql
-- Giá phải nhất quán
CONSTRAINT chk_reg_price CHECK (final_price = list_price - discount_amount)

-- Có giảm giá thì bắt buộc ghi lý do (tên chương trình khuyến mãi)
CONSTRAINT chk_reg_discount CHECK (discount_amount = 0 OR discount_reason IS NOT NULL)
```

> **Vì sao `member_id` là `NOT NULL`:** một dòng ở đây chỉ sinh ra khi khách **đã chốt mua**. Người chưa mua gói thì **không có dòng nào** trong bảng này — không phải "có dòng với `member_id` rỗng". Chính vì thế mà `members` được tạo **ngay lúc chốt mua**, trước bước thanh toán.

### Hai luồng bán gói

```
LUỒNG 1 — Khách đến trực tiếp phòng gym
  Khách xem bảng giá + chương trình khuyến mãi đang chạy → quyết định mua
  → Lễ tân/Sale tạo hợp đồng
  → Thanh toán NGAY tại quầy, HOẶC chuyển khoản sau

LUỒNG 2 — Khách mua trên web/app
  Khách tự chọn gói + khuyến mãi trên app → bấm mua
  → Hệ thống tạo hợp đồng → chuyển sang cổng thanh toán
```

**Cả hai luồng dùng chung một trình tự:**

```
Khách chốt mua
  → TẠO members nếu chưa có (cấp member_code, join_date)   ← lần đầu tiên duy nhất
  → registrations(member_id=123, status='PENDING_PAYMENT')
     discount_amount/discount_reason lấy theo chương trình khuyến mãi đang áp dụng
  → tạo invoices trỏ tới hợp đồng này

Khách trả đủ tiền (ngay lập tức hoặc vài giờ/ngày sau)
  → registrations: status='ACTIVE', activated_at=now()
  → sổ cái credit: GRANT +12 buổi
  → revenue_schedules: khởi tạo kế hoạch ghi nhận doanh thu
  → leads (nếu khách đến từ phễu bán hàng): stage='WON'

Khách bỏ ngang không trả tiền
  → job đêm: status='CANCELLED' sau 48h
```

### Dữ liệu mẫu

```
registration_code : REG-2026-000451
member_id         : 123          (Nguyễn Văn An, MB-000123)
membership_id     : 7            (Gói PT 12 buổi)
sold_by           : 12           (Phạm Dũng, EM-012)
assigned_trainer_id: 5           (Trần Bình, EM-005)
package_type      : SESSION_BASED
sessions_total    : 12
list_price        : 4.200.000
discount_amount   :   420.000    → discount_reason: "Khuyến mãi hè 2026, giảm 10%"
final_price       : 3.780.000
start_date        : 2026-07-15
end_date          : 2027-01-11   (= start_date + 180 ngày)
status            : ACTIVE
```

---

## `registration_freezes` — bảo lưu gói tập

| Cột | Kiểu | Giá trị / Ý nghĩa | Mẫu |
|---|---|---|---|
| `registration_id` | `BIGINT` | Bảo lưu hợp đồng nào | `451` |
| `from_date` / `to_date` | `DATE` | Khoảng bảo lưu | `2026-09-01` / `2026-09-30` |
| `days` | `INTEGER` | `= to_date − from_date + 1` | `30` |
| `reason` | `VARCHAR(255)` | Lý do cụ thể | `Đi công tác nước ngoài 1 tháng` |
| `reason_type` | `VARCHAR(20)` | `PERSONAL` \| `MEDICAL` \| `TRAVEL` \| `OTHER` | `TRAVEL` |
| `attachment_key` | `VARCHAR(500)` | Giấy tờ y tế (key MinIO) | `NULL` |
| `requested_by` | `BIGINT` | Ai gửi yêu cầu | `205` |
| `approved_by` | `BIGINT` | Ai duyệt | `1` |
| `status` | `VARCHAR(20)` | **6 giá trị** — xem dưới | `APPROVED` |
| `ended_early_at` | `DATE` | Kết thúc sớm ngày nào | `NULL` |

| `status` | Nghĩa |
|---|---|
| `PENDING` | Chờ duyệt (vượt điều kiện tự động) |
| `APPROVED` | Đã duyệt, chưa tới ngày bắt đầu |
| `REJECTED` | Từ chối, kèm lý do |
| `ACTIVE` | Đang trong kỳ bảo lưu |
| `ENDED` | Đã hết kỳ |
| `CANCELLED` | Hội viên hủy yêu cầu |

**Điều kiện tự động duyệt** *(đọc từ `system_settings`)*: gói ≥ 90 ngày · tổng ngày bảo lưu trong năm ≤ 30 · số lần < 2 · báo trước ≥ 3 ngày.

**Tác động khi `APPROVED`:** `registrations.status → FROZEN` · **`end_date` cộng thêm 30 ngày** (từ `2027-01-11` → `2027-02-10`) · **dừng ghi nhận doanh thu** 30 ngày đó · check-in bị từ chối.

**Ràng buộc:** một hợp đồng không thể có 2 khoảng bảo lưu chồng nhau — dùng `EXCLUDE USING gist` với `daterange`.

---

## `member_trainers` — 2–3 PT cùng chăm 1 hội viên

| Cột | Kiểu | Giá trị / Ý nghĩa | Mẫu |
|---|---|---|---|
| `member_id` | `BIGINT` | Hội viên nào | `123` |
| `trainer_id` | `BIGINT` | PT nào (`employees.id`, `department='TRAINING'`) | `5` |
| `role` | `VARCHAR(20)` | `PRIMARY` \| `SECONDARY` \| `SUBSTITUTE` | `PRIMARY` |
| `from_date` / `to_date` | `DATE` | `to_date NULL` = đang phụ trách | `2026-07-15` / `NULL` |
| `assigned_by` | `BIGINT` | Ai phân công | `1` |
| `note` | `VARCHAR(255)` | | `PT chính, phụ trách giáo án` |

**Ví dụ hội viên An có 3 PT:**

| trainer_id | role | note |
|---|---|---|
| 5 | `PRIMARY` | Trần Bình — PT chính, soạn giáo án |
| 9 | `SECONDARY` | Lê Cường — chuyên phục hồi chấn thương lưng |
| 11 | `SUBSTITUTE` | Hoàng Đạt — dạy thay khi PT chính nghỉ |

> **Ràng buộc:** mỗi hội viên chỉ có **đúng 1** PT `PRIMARY` tại một thời điểm (unique index có điều kiện).
> **Quan trọng:** tiền công tính cho **PT thực tế dạy buổi đó** (`pt_sessions.trainer_id`), không phải PT `PRIMARY`.

---
---

# NHÓM 3 — CHECK-IN

## `check_ins` — lượt vào/ra phòng tập ⭐

Gánh thêm `check_in_incidents` (→ 3 cột sự cố).

| Cột | Kiểu | Giá trị / Ý nghĩa | Mẫu |
|---|---|---|---|
| `member_id` | `BIGINT` | Hội viên vào tập | `123` |
| `employee_id` | `BIGINT` | Hoặc nhân viên chấm công | `NULL` |
| `registration_id` | `BIGINT` | Dùng hợp đồng nào để vào | `451` |
| `checked_in_at` | `TIMESTAMPTZ` | Giờ vào | `2026-08-01 18:05:12+07` |
| `checked_out_at` | `TIMESTAMPTZ` | Giờ ra. **`NULL` = đang ở trong phòng tập** | `2026-08-01 19:47:30+07` |
| `method` | `VARCHAR(20)` | **5 cách** — xem dưới | `QR_DYNAMIC` |
| `result` | `VARCHAR(20)` | **7 kết quả** — xem dưới | `ALLOWED` |
| `verified_by` | `BIGINT` | Lễ tân nào xác nhận | `18` |
| `manual_reason` | `VARCHAR(255)` | **Bắt buộc** khi `method='MANUAL'` | `NULL` |
| `device_id` | `VARCHAR(60)` | Thiết bị nào quét | `FRONTDESK-01` |
| `photo_key` | `VARCHAR(500)` | Ảnh chụp lúc check-in | `NULL` |
| **— Sự cố (gộp từ `check_in_incidents`) —** | | | |
| `incident_type` | `VARCHAR(30)` | `NULL` = không có sự cố. **5 loại** — xem dưới | `NULL` |
| `incident_note` | `TEXT` | Mô tả + cách xử lý | `NULL` |
| `incident_handled_by` | `BIGINT` | Ai xử lý | `NULL` |

### `method` — 5 cách check-in

| Giá trị | Nghĩa |
|---|---|
| `QR_DYNAMIC` | Quét QR động từ app (mã đổi mỗi 30 giây) |
| `RFID` | Chạm thẻ từ lên đầu đọc |
| `MANUAL` | Lễ tân nhập tay (quên cả thẻ lẫn điện thoại) — **bắt buộc ghi lý do** |
| `FACE` | Nhận diện khuôn mặt *(tính năng nâng cao, tùy chọn)* |
| `DAY_PASS` | Khách vãng lai mua vé lẻ |

### `result` — 7 kết quả

| Giá trị | Nghĩa | Màn hình quầy |
|---|---|---|
| `ALLOWED` | Hợp lệ | 🟢 Ảnh + tên + số ngày còn lại |
| `ALLOWED_OVERRIDE` | Lễ tân bỏ qua cảnh báo, vẫn cho vào | 🟡 + ghi audit |
| `DENIED_EXPIRED` | Gói hết hạn | 🔴 + nút "Gia hạn ngay" |
| `DENIED_FROZEN` | Đang bảo lưu | 🟡 + nút "Kết thúc bảo lưu sớm" |
| `DENIED_UNPAID` | Còn nợ tiền | 🟡 + số tiền nợ + nút thu tiền |
| `DENIED_NOT_FOUND` | Không tìm thấy thẻ/mã | 🔴 + ô tìm theo SĐT |
| `DENIED_SUSPECT` | Nghi mượn thẻ | 🔴 + yêu cầu xuất trình CCCD |

### `incident_type` — 5 loại

| Giá trị | Khi nào |
|---|---|
| `ANTI_PASSBACK` | Check-in lần 2 trong vòng 30 phút |
| `SUSPECTED_SHARING` | **Người này đang ở TRONG phòng tập** (chưa check-out) mà lại có lượt check-in mới → gần như chắc chắn người khác dùng thẻ |
| `REPLAY_ATTEMPT` | Dùng lại mã QR đã quét rồi |
| `FACE_MISMATCH` | Khuôn mặt không khớp ảnh hồ sơ |
| `EXPIRED_ATTEMPT` | Gói hết hạn nhưng vẫn cố quét nhiều lần |

> **Vì sao ghi cả lượt bị từ chối:** đây là **dữ liệu để đo hiệu quả chống thất thoát**. Chỉ ghi lượt thành công thì không biết đã chặn được bao nhiêu ca gian lận — mà đó là chỉ số quan trọng nhất của đóng góp về check-in.
>
> **Vì sao gộp sự cố vào đây:** mỗi sự cố **luôn 1–1** với đúng một lượt check-in. Bảng riêng chỉ để lưu 3 cột là thừa.

### Hai kiến trúc quét QR

```
HƯỚNG A — Lễ tân quét QR trên điện thoại hội viên
  Hội viên mở app → QR động
  → Lễ tân quét bằng webcam máy quầy HOẶC đầu đọc QR USB
  → Chuỗi token vào ô input trên trang quầy → POST /check-ins
  → Response trả thẳng về trang đó → hiện ảnh + thông tin
  ✅ KHÔNG cần WebSocket

HƯỚNG B — Hội viên quét QR do phòng gym hiển thị
  Máy tính bảng ở cửa hiện QR CỦA PHÒNG GYM (cũng đổi 30 giây)
  → Hội viên quét bằng camera điện thoại
  → App gửi { gymToken, memberToken } → server xác thực CẢ HAI
  → Đẩy WebSocket lên màn hình quầy
  ✅ KHÔNG cần phần cứng gì — chỉ laptop + điện thoại
```

> **Đầu đọc RFID giả lập bàn phím (HID keyboard emulation)** — chạm thẻ thì nó "gõ" mã UID rồi Enter. Nghĩa là **gõ tay chuỗi đó vào ô input cho kết quả y hệt quẹt thẻ thật** → kiểm thử tự động và demo được mà **không cần mua phần cứng**.

---
---

# NHÓM 4 — BUỔI TẬP PT & SỔ CÁI

## `pt_sessions` — buổi tập ⭐ *(gánh luôn `pt_bookings`)*

| Cột | Kiểu | Giá trị / Ý nghĩa | Mẫu |
|---|---|---|---|
| `member_id` | `BIGINT` | Học viên | `123` |
| `trainer_id` | `BIGINT` | **PT THỰC TẾ dạy** (`employees.id`) | `5` |
| `registration_id` | `BIGINT` | Trừ buổi từ hợp đồng nào | `451` |
| `session_type` | `VARCHAR(20)` | **6 loại** — quyết định trừ buổi & tính công | `PAID_PT` |
| `scheduled_start` / `scheduled_end` | `TIMESTAMPTZ` | Lịch dự kiến | `2026-08-06 19:00` / `20:00` |
| `actual_start` / `actual_end` | `TIMESTAMPTZ` | Thực tế | `19:05` / `20:02` |
| `room_name` | `VARCHAR(120)` | Tập ở đâu | `Khu tạ tự do` |
| `status` | `VARCHAR(20)` | **8 trạng thái** — xem dưới | `COMPLETED` |
| `requested_by` | `BIGINT` | Ai đặt lịch | `205` |
| `responded_at` | `TIMESTAMPTZ` | PT trả lời lúc nào | `2026-08-04 21:15:00+07` |
| `reject_reason` | `VARCHAR(255)` | Lý do PT từ chối | `NULL` |
| `trainer_confirmed_at` | `TIMESTAMPTZ` | **PT xác nhận đã dạy** | `2026-08-06 20:03:00+07` |
| `member_confirmed_at` | `TIMESTAMPTZ` | **Hội viên xác nhận** | `2026-08-06 20:04:15+07` |
| `auto_confirmed` | `BOOLEAN` | `TRUE` = hội viên không xác nhận trong 24h, hệ thống tự duyệt | `FALSE` |
| `cancelled_by` | `VARCHAR(20)` | `MEMBER` \| `TRAINER` \| `SYSTEM` | `NULL` |
| `cancelled_at` | `TIMESTAMPTZ` | | `NULL` |
| `cancel_reason` | `VARCHAR(255)` | | `NULL` |
| `is_late_cancel` | `BOOLEAN` | `TRUE` = hủy < 4h → **mất buổi** | `FALSE` |
| `note` | `TEXT` | Ghi chú buổi tập | `Tập ngực + tay sau, tăng 5kg bench press` |

### `status` — 8 trạng thái *(gộp cả vòng đời đặt lịch)*

| Giá trị | Nghĩa |
|---|---|
| **`PENDING_TRAINER`** | **Hội viên đã đặt, chờ PT duyệt** *(vai trò cũ của `pt_bookings`)* |
| **`REJECTED`** | **PT từ chối, kèm lý do** |
| `SCHEDULED` | PT đã duyệt, chưa tới giờ |
| `IN_PROGRESS` | Đang tập |
| `COMPLETED` | **Hoàn thành + đã xác nhận 2 chiều** → trừ buổi + tính công + ghi nhận doanh thu |
| `NO_SHOW_MEMBER` | Hội viên không đến |
| `NO_SHOW_TRAINER` | PT không đến |
| `CANCELLED` | Đã hủy |

> **Vì sao gộp `pt_bookings` vào đây:** yêu cầu đặt lịch **chính là buổi tập ở giai đoạn chưa được duyệt** — cùng một sự vật, khác giai đoạn. Tách 2 bảng buộc phải copy dữ liệu từ booking sang session khi duyệt, dễ lệch.

### `session_type` — 6 loại và tác động ⭐

| Giá trị | Nghĩa | Trừ buổi hội viên? | **PT được công?** | Đơn giá *(SENIOR)* |
|---|---|:---:|:---:|---:|
| `PAID_PT` | Buổi PT có trả phí | ✅ **−1** | ✅ | 120.000đ |
| **`COMPLIMENTARY`** | **PT hỗ trợ tập miễn phí** | ❌ **0** | ✅ **CÓ** | **60.000đ** |
| `TRIAL` | Buổi tập thử cho khách tiềm năng | ❌ 0 | ✅ | 80.000đ |
| `ORIENTATION` | Hướng dẫn hội viên mới làm quen máy | ❌ 0 | ✅ | 50.000đ |
| `MAKEUP` | Tập bù buổi đã hủy đúng hạn | ❌ 0 | ✅ | 120.000đ |
| `ASSESSMENT` | Đo chỉ số cơ thể, đánh giá thể trạng | ❌ 0 | ✅ | 70.000đ |

> **Đây là chỗ trả lời yêu cầu của thầy:** *"PT trợ giúp tập miễn phí — buổi đó PT vẫn phải được trả lương."* Loại `COMPLIMENTARY` trừ **0** buổi của hội viên nhưng **vẫn sinh 1 dòng công** trong `payroll_items`.
>
> Đơn giá đọc từ `system_settings.payroll.rate_card` (không còn bảng `payroll_rate_cards`). Hạn mức số buổi `COMPLIMENTARY`/PT/tháng cũng nằm trong `system_settings` để tránh lạm dụng.

### Xác nhận hai chiều

Buổi tập chỉ chuyển `COMPLETED` khi **cả `trainer_confirmed_at` và `member_confirmed_at` đều có giá trị**. PT bấm "Kết thúc" → sinh QR → hội viên quét để xác nhận. Không xác nhận trong 24h → hệ thống tự duyệt nhưng đặt `auto_confirmed = TRUE` để kiểm toán.

> **Tỷ lệ `auto_confirmed` cao là dấu hiệu bất thường** — cần điều tra xem PT có khai khống không.

### Ràng buộc chống trùng lịch *(ở tầng CSDL)*

```sql
-- Một PT không thể có 2 buổi chồng giờ
EXCLUDE USING gist (trainer_id WITH =, tstzrange(scheduled_start, scheduled_end) WITH &&)
  WHERE (status IN ('SCHEDULED','IN_PROGRESS'))

-- Một hội viên cũng vậy
EXCLUDE USING gist (member_id WITH =, tstzrange(scheduled_start, scheduled_end) WITH &&)
  WHERE (status IN ('SCHEDULED','IN_PROGRESS'))
```

Hai request đặt lịch cùng lúc → **CSDL tự từ chối request thứ hai**, không phụ thuộc code có nhớ kiểm tra hay không.

---

## `session_credit_ledger` — SỔ CÁI TÍN DỤNG BUỔI TẬP ⭐⭐⭐

> **Đóng góp học thuật chính.** Bảng **chỉ ghi thêm** (append-only): không `UPDATE`, không `DELETE` — trigger CSDL chặn cứng.

| Cột | Kiểu | Giá trị / Ý nghĩa | Mẫu |
|---|---|---|---|
| `registration_id` | `BIGINT` | Sổ cái của hợp đồng nào | `451` |
| `entry_type` | `VARCHAR(20)` | **5 loại bút toán** — xem dưới | `CONSUME` |
| `delta` | `INTEGER` | Thay đổi bao nhiêu buổi. **Khác 0**, có thể âm | `-1` |
| `balance_after` | `INTEGER` | **Số dư SAU bút toán này**. Không bao giờ âm | `11` |
| `source_type` | `VARCHAR(30)` | `PT_SESSION` \| `REGISTRATION` \| `MANUAL` \| `SYSTEM` | `PT_SESSION` |
| `source_id` | `BIGINT` | ID nguồn | `9931` |
| `reason` | `TEXT` | **Bắt buộc** với `ADJUST` | `NULL` |
| `created_by` | `BIGINT` | **Bắt buộc** với `ADJUST` | `NULL` |

### `entry_type` — 5 loại

| Giá trị | Khi nào | `delta` |
|---|---|---|
| `GRANT` | Hợp đồng chuyển `ACTIVE` → cấp buổi | `+12` |
| `CONSUME` | Buổi `PAID_PT` chuyển `COMPLETED` | `−1` |
| `REFUND` | Hủy buổi **đúng hạn** (trước 4h) → trả lại | `+1` |
| `EXPIRE` | Hợp đồng hết hạn còn dư buổi | `−n` |
| `ADJUST` | Sửa sai thủ công — **bắt buộc `reason` + `created_by`** | `±n` |

### Dữ liệu mẫu — vòng đời gói PT 12 buổi

| id | entry_type | delta | balance_after | source_type | source_id | reason |
|---:|---|---:|---:|---|---:|---|
| 1 | `GRANT` | +12 | **12** | `REGISTRATION` | 451 | Kích hoạt gói PT 12 buổi |
| 2 | `CONSUME` | −1 | **11** | `PT_SESSION` | 9931 | – |
| 3 | `CONSUME` | −1 | **10** | `PT_SESSION` | 9977 | – |
| 4 | `REFUND` | +1 | **11** | `PT_SESSION` | 9977 | Hủy đúng hạn, trả lại buổi |
| 5 | `CONSUME` | −1 | **10** | `PT_SESSION` | 10002 | – |
| 6 | `ADJUST` | +1 | **11** | `MANUAL` | `NULL` | Buổi 10002 PT ghi nhầm, Admin điều chỉnh |
| … | | | | | | |
| 20 | `EXPIRE` | −2 | **0** | `SYSTEM` | `NULL` | Gói hết hạn 11/01/2027, còn dư 2 buổi |

### Ba bất biến phải luôn đúng *(kiểm chứng bằng property-based test)*

```
1. SUM(delta) của 1 hợp đồng  ==  balance_after của dòng mới nhất
2. balance_after >= 0 ở MỌI dòng                    (không bao giờ âm buổi)
3. balance_after[i] == balance_after[i−1] + delta[i]  (liên tục, không nhảy)
```

Thêm ràng buộc: **một buổi tập chỉ được trừ credit đúng MỘT lần** —
`UNIQUE(source_type, source_id) WHERE entry_type='CONSUME'`.

> **Vì sao không dùng 1 cột `sessions_remaining`:** cột đó bị `UPDATE` liên tục. Khi sai, không biết sai từ bút toán nào, lúc nào, ai gây ra. Tranh chấp PT–hội viên về *"còn mấy buổi"* không có bằng chứng. Với sổ cái, mọi thay đổi có dấu vết và **cộng lại phải khớp**.
>
> **Xử lý 2 request trừ buổi đồng thời:** `SELECT ... FOR UPDATE` khóa dòng cuối trước khi ghi bút toán mới.

---
---

# NHÓM 5 — LỚP HỌC NHÓM

## `class_sessions` — buổi lớp cụ thể *(gộp `class_definitions` + `class_schedules` + `rooms`)*

| Cột | Kiểu | Giá trị / Ý nghĩa | Mẫu |
|---|---|---|---|
| `code` | `VARCHAR(30)` | Mã buổi lớp | `YOGA-20260810-0600` |
| `name` | `VARCHAR(120)` | Tên lớp | `Yoga buổi sáng` |
| `class_type` | `VARCHAR(30)` | `YOGA` \| `FITNESS` \| `HIIT` \| `ZUMBA` \| `SPINNING` \| `BOXING` | `YOGA` |
| `level` | `VARCHAR(20)` | `BEGINNER` \| `INTERMEDIATE` \| `ADVANCED` | `BEGINNER` |
| `trainer_id` | `BIGINT` | Ai dạy (`employees.id`) | `9` |
| `room_name` | `VARCHAR(120)` | Phòng nào *(chuỗi, không còn bảng `rooms`)* | `Phòng Yoga 1 — Tầng 2` |
| `required_area` | `VARCHAR(30)` | Gói phải có khu vực này mới đăng ký được | `YOGA_STUDIO` |
| `starts_at` / `ends_at` | `TIMESTAMPTZ` | Buổi cụ thể | `2026-08-10 06:00` / `07:00` |
| `capacity` | `INTEGER` | Sức chứa. `> 0` | `20` |
| `booked_count` | `INTEGER` | Đã đăng ký. `0 ≤ booked_count ≤ capacity` | `17` |
| `status` | `VARCHAR(20)` | `SCHEDULED` \| `IN_PROGRESS` \| `COMPLETED` \| `CANCELLED` | `SCHEDULED` |
| `cancel_reason` | `VARCHAR(255)` | | `NULL` |

> **Vì sao gộp 3 bảng:** bản 73 bảng có `class_definitions` (lớp là gì) → `class_schedules` (lặp hàng tuần) → `class_sessions` (buổi ngày X). Với **một** phòng gym và vài lớp cố định, tạo buổi lớp bằng script sinh sẵn 3 tháng là đủ — không cần tầng lịch lặp riêng.
>
> Cái giá: đổi giờ lớp cố định phải sửa nhiều buổi cùng lúc thay vì sửa 1 dòng lịch.

**Ràng buộc:** một phòng không có 2 lớp cùng giờ — `EXCLUDE USING gist (room_name WITH =, tstzrange(starts_at, ends_at) WITH &&)`.

---

## `class_bookings` — hội viên đặt chỗ

| Cột | Kiểu | Giá trị / Ý nghĩa | Mẫu |
|---|---|---|---|
| `class_session_id` | `BIGINT` | Buổi lớp nào | `1204` |
| `member_id` | `BIGINT` | Ai đặt | `123` |
| `registration_id` | `BIGINT` | **Dùng gói nào để đặt**. `NOT NULL` | `451` |
| `status` | `VARCHAR(20)` | `BOOKED` \| `WAITLISTED` \| `ATTENDED` \| `NO_SHOW` \| `CANCELLED` | `BOOKED` |
| `waitlist_position` | `SMALLINT` | Vị trí hàng chờ (khi lớp đầy) | `NULL` |
| `booked_at` / `cancelled_at` | `TIMESTAMPTZ` | | `2026-08-08 21:30:00+07` / `NULL` |

`UNIQUE(class_session_id, member_id)` — mỗi buổi lớp đặt 1 lần.

> **Đây là câu trả lời cho cô Trinh:** hội viên **mua gói** (1 `registration`) rồi **dùng gói đó đặt nhiều lớp** (nhiều `class_booking`). Nhồi `class_id` vào `registrations` chỉ chứa được **một** lớp, trong khi hội viên gói 6 tháng thường học yoga thứ 2 và HIIT thứ 5.
>
> Nguyên tắc gốc vẫn giữ: **không có `registration` hợp lệ thì không đặt được lớp**.

---
---

# NHÓM 6 — THIẾT BỊ

## `equipment` — từng thiết bị cụ thể *(gộp `equipment_types` + `equipment_items`)*

| Cột | Kiểu | Giá trị / Ý nghĩa | Mẫu |
|---|---|---|---|
| `asset_code` | `VARCHAR(40)` | **Mã tài sản dán trên máy**. **UNIQUE** | `TM-003` |
| `name` | `VARCHAR(150)` | Tên thiết bị | `Máy chạy bộ` |
| `category` | `VARCHAR(40)` | `CARDIO` \| `STRENGTH` \| `FREE_WEIGHT` \| `ACCESSORY` \| `YOGA` | `CARDIO` |
| `brand` / `model` | `VARCHAR(80)` | | `Technogym` / `MyRun` |
| `serial_number` | `VARCHAR(80)` | Số seri của hãng | `TG-MR-2024-88192` |
| `origin` | `VARCHAR(100)` | Xuất xứ | `Italy` |
| `location` | `VARCHAR(120)` | Đặt ở đâu *(chuỗi, không còn bảng `rooms`)* | `Khu cardio — Tầng 1` |
| `purchase_price` | `NUMERIC(14,2)` | Giá mua — cơ sở khấu hao | `85000000.00` |
| `date_of_purchase` | `DATE` | | `2024-05-20` |
| `warranty_until` | `DATE` | | `2027-05-20` |
| `useful_life_months` | `SMALLINT` | Tuổi thọ — cơ sở khấu hao | `60` |
| `status` | `VARCHAR(20)` | `OPERATIONAL` \| `NEEDS_REPAIR` \| `UNDER_MAINTENANCE` \| `RETIRED` | `NEEDS_REPAIR` |
| `last_maintained_at` | `DATE` | | `2026-06-01` |
| `disposed_at` | `DATE` | Ngày thanh lý | `NULL` |

**Khấu hao hàng tháng** = `purchase_price / useful_life_months` = `85.000.000 / 60` = **1.416.667đ/tháng** → job đêm tự sinh 1 dòng trong `expenses`.

> **Vì sao gộp:** bản cũ tách loại/từng cái để tránh lặp `brand`, `model` cho 5 máy chạy bộ cùng loại. Với ~100–200 thiết bị của **một** phòng gym, lặp vài chuỗi text là chấp nhận được — đổi lại bớt một JOIN ở mọi truy vấn.

---
---

# NHÓM 7 — THANH TOÁN

## `invoices` — hóa đơn

| Cột | Kiểu | Giá trị / Ý nghĩa | Mẫu |
|---|---|---|---|
| `invoice_no` | `VARCHAR(30)` | **UNIQUE** | `INV-2026-000451` |
| `member_id` | `BIGINT` | Của hội viên nào | `123` |
| `person_id` | `BIGINT` | Hoặc khách vãng lai chưa là hội viên | `NULL` |
| `registration_id` | `BIGINT` | Cho hợp đồng nào | `451` |
| `description` | `VARCHAR(255)` | **Nội dung in trên phiếu** *(thay cho bảng `invoice_items`)* | `Gói PT 12 buổi (KM hè 2026 −10%)` |
| `subtotal` | `NUMERIC(14,2)` | Trước giảm giá | `4200000.00` |
| `discount_amount` | `NUMERIC(14,2)` | | `420000.00` |
| `tax_amount` | `NUMERIC(14,2)` | Thường 0 với dịch vụ gym | `0.00` |
| `total_amount` | `NUMERIC(14,2)` | **Phải thu** | `3780000.00` |
| `paid_amount` | `NUMERIC(14,2)` | Đã thu. `≤ total_amount` | `3780000.00` |
| `balance_due` | `NUMERIC(14,2)` | Cột **tự tính** `= total − paid` | `0.00` |
| `status` | `VARCHAR(20)` | `UNPAID` \| `PARTIALLY_PAID` \| `PAID` \| `OVERDUE` \| `CANCELLED` \| `REFUNDED` | `PAID` |
| `issued_at` / `due_date` / `paid_at` | | Xuất / hạn / trả đủ | `2026-07-15 10:20+07` / `2026-07-22` / `2026-07-15 10:28+07` |
| `issued_by` | `BIGINT` | Ai xuất | `18` |

> **Vì sao bỏ `invoice_items`:** một hóa đơn phòng gym gần như luôn chỉ có **1 dòng** (một gói tập). Mua 2 gói thì tạo 2 hóa đơn. Đổi lại bớt một bảng và một JOIN.

---

## `payments` — khoản thu ⭐ *(gánh `refunds` + `webhook_events` + `payment_allocations`)*

| Cột | Kiểu | Giá trị / Ý nghĩa | Mẫu |
|---|---|---|---|
| `payment_no` | `VARCHAR(30)` | **UNIQUE** | `PAY-2026-001102` |
| `member_id` | `BIGINT` | Của ai | `123` |
| **`invoice_id`** | `BIGINT` | Trả cho hóa đơn nào *(thay bảng `payment_allocations`)* | `451` |
| `cash_shift_id` | `BIGINT` | Thuộc ca nào — **bắt buộc nếu tiền mặt** | `88` |
| **`payment_type`** | `VARCHAR(20)` | **`PAYMENT`** \| **`REFUND`** | `PAYMENT` |
| `method` | `VARCHAR(20)` | **6 phương thức** — xem dưới | `VIETQR` |
| `amount` | `NUMERIC(14,2)` | **ÂM nếu là hoàn tiền** | `3780000.00` |
| `currency` | `CHAR(3)` | Luôn `VND` | `VND` |
| `status` | `VARCHAR(20)` | **5 trạng thái** — xem dưới | `SUCCEEDED` |
| `idempotency_key` | `VARCHAR(64)` | **UNIQUE** — chống trừ tiền 2 lần khi client gửi lại | `9f2a1c88-...-b3e1` |
| `provider` | `VARCHAR(30)` | `VNPAY` \| `MOMO` \| `ZALOPAY` \| `SEPAY` \| `BANK_TRANSFER` | `SEPAY` |
| `provider_txn_id` | `VARCHAR(100)` | **UNIQUE(provider, txn_id)** — chống webhook trùng | `FT26072812345678` |
| `transfer_content` | `VARCHAR(255)` | **Nội dung CK** — cơ sở đối soát tự động | `GYM INV2026000451` |
| `bank_account` | `VARCHAR(50)` | Tài khoản nhận | `0123456789 - MB Bank` |
| `pos_terminal_id` | `VARCHAR(50)` | Mã máy POS | `NULL` |
| `pos_card_last4` | `CHAR(4)` | **4 số cuối thẻ** (PCI-DSS: không lưu số đầy đủ) | `NULL` |
| **— Chỉ dùng khi `payment_type='REFUND'` —** | | | |
| `refund_reason` | `TEXT` | Lý do hoàn. Bắt buộc | `NULL` |
| `refund_of_payment_id` | `BIGINT` | Hoàn cho khoản thu nào | `NULL` |
| `refund_penalty` | `NUMERIC(14,2)` | Phí hủy đã trừ | `NULL` |
| `approved_by` | `BIGINT` | Ai duyệt hoàn tiền | `NULL` |
| **— Chung —** | | | |
| `received_by` | `BIGINT` | Ai thu | `18` |
| `paid_at` / `reconciled_at` | `TIMESTAMPTZ` | Trả / đối soát xong | `2026-07-15 10:28+07` / `23:00+07` |
| `raw_payload` | `JSONB` | **Payload webhook thô** *(thay bảng `webhook_events`)* | `{"id":"...","amount":3780000,...}` |

### `method` — 6 phương thức

| Giá trị | Có API tự động? | Cách đối soát |
|---|:---:|---|
| `CASH` | ❌ | Qua `cash_shifts` — đếm tiền cuối ca |
| `BANK_TRANSFER` | ✅ | Webhook biến động số dư |
| `VIETQR` | ✅ | Webhook + khớp `transfer_content` |
| `CARD_POS` | ❌ | Nhập tay mã giao dịch + đối soát sao kê POS cuối ngày |
| `E_WALLET` | ✅ | IPN webhook |
| `GATEWAY` | ✅ | IPN webhook |

> **Máy POS không có API cho bên thứ ba** (bảo mật PCI-DSS). Đây là **giới hạn hạ tầng ngân hàng, không phải giới hạn thiết kế** — nên nêu rõ trong báo cáo.

### `status` — 5 trạng thái

`INITIATED` → `PENDING` → `SUCCEEDED` ✅ / `FAILED` / `EXPIRED`

### Hoàn tiền = bút toán đảo

```
Khoản thu gốc:
  id=1102, payment_type=PAYMENT, amount= 3.780.000, invoice_id=451

Hoàn tiền (hội viên chuyển công tác, còn 5 tháng chưa dùng):
  id=1580, payment_type=REFUND,  amount= −1.512.000, invoice_id=451
           refund_of_payment_id=1102
           refund_penalty=378.000       (phí hủy 10% giá trị còn lại)
           refund_reason="Hội viên chuyển công tác vào TP.HCM"
           approved_by=1

→ SUM(amount) WHERE invoice_id=451  =  3.780.000 − 1.512.000 = 2.268.000
  = số tiền phòng gym THỰC SỰ giữ lại
```

> **Vì sao bỏ bảng `refunds`:** hoàn tiền **chính là một bút toán âm** — đây đúng là cách kế toán làm. Tách bảng riêng khiến phải cộng trừ giữa 2 bảng mới ra số thực thu.

---

## `cash_shifts` — ca làm việc của lễ tân ⭐

| Cột | Kiểu | Giá trị / Ý nghĩa | Mẫu |
|---|---|---|---|
| `employee_id` | `BIGINT` | Lễ tân nào | `18` |
| `opened_at` / `closed_at` | `TIMESTAMPTZ` | Mở / đóng ca | `2026-08-01 06:00+07` / `14:00+07` |
| `opening_balance` | `NUMERIC(14,2)` | Tiền lẻ đầu ca | `500000.00` |
| `expected_cash` | `NUMERIC(14,2)` | **Hệ thống tính** = đầu ca + Σ thu tiền mặt | `2300000.00` |
| `counted_cash` | `NUMERIC(14,2)` | **Lễ tân đếm thực tế** | `2250000.00` |
| `difference` | `NUMERIC(14,2)` | Cột **tự tính** `= counted − expected` | `-50000.00` |
| `difference_reason` | `TEXT` | **Bắt buộc khi lệch** | `Trả nhầm tiền thừa cho khách lúc 11h20` |
| `status` | `VARCHAR(20)` | `OPEN` \| `CLOSED` (khớp) \| `DISCREPANCY` (lệch) | `DISCREPANCY` |
| `verified_by` | `BIGINT` | Kế toán xác nhận | `21` |

**Ràng buộc:** mỗi lễ tân tối đa **1 ca `OPEN`**; mọi khoản thu tiền mặt `SUCCEEDED` **bắt buộc** gắn với một ca → không có tiền mặt nào lọt ngoài sổ.

**Chỉ số quản trị:** tỷ lệ ca có chênh lệch và giá trị lệch TB theo từng nhân viên → phát hiện vấn đề vận hành hoặc gian lận.

---
---

# NHÓM 8 — TÀI CHÍNH & LƯƠNG

## `revenue_schedules` — kế hoạch phân bổ doanh thu ⭐⭐

| Cột | Kiểu | Giá trị / Ý nghĩa | Mẫu |
|---|---|---|---|
| `registration_id` | `BIGINT` | **UNIQUE** — 1 hợp đồng 1 kế hoạch | `451` |
| `total_amount` | `NUMERIC(14,2)` | Tổng giá trị hợp đồng | `3780000.00` |
| `recognition_method` | `VARCHAR(20)` | **3 phương pháp** — xem dưới | `PER_SESSION` |
| `start_date` / `end_date` | `DATE` | Khoảng ghi nhận | `2026-07-15` / `2027-01-11` |
| `total_units` | `INTEGER` | Tổng đơn vị (số ngày **hoặc** số buổi) | `12` |
| `recognized_units` | `INTEGER` | Đã ghi nhận bao nhiêu đơn vị | `3` |
| `recognized_amount` | `NUMERIC(14,2)` | **Đã ghi nhận** | `945000.00` |
| `deferred_amount` | `NUMERIC(14,2)` | **Chưa thực hiện** — còn nợ dịch vụ | `2835000.00` |
| `status` | `VARCHAR(20)` | `IN_PROGRESS` \| `COMPLETED` \| `TERMINATED` | `IN_PROGRESS` |

### `recognition_method` — 3 phương pháp

| Giá trị | Dùng cho | Công thức |
|---|---|---|
| `STRAIGHT_LINE` | Gói `TIME_BASED` | `total_amount / duration_days` mỗi ngày, **bỏ qua ngày bảo lưu** |
| `PER_SESSION` | Gói `SESSION_BASED` | `total_amount / session_count` mỗi khi 1 buổi `COMPLETED` |
| `IMMEDIATE` | Vé lẻ `DAY_PASS` | Ghi nhận toàn bộ ngay |

### ⭐ Bất biến cốt lõi

```
recognized_amount + deferred_amount  ==  total_amount    (LUÔN LUÔN, sai số 0đ)
```

Ví dụ trên: `945.000 + 2.835.000 = 3.780.000` ✓

### Ví dụ minh họa — gói Fitness 12 tháng

```
FIT-12M, giá 4.800.000đ, bắt đầu 15/01/2026, STRAIGHT_LINE

Ghi nhận mỗi ngày = 4.800.000 / 365 = 13.150,68đ/ngày

Tháng 01 (15→31/01, 17 ngày):    223.561đ  ghi nhận
Deferred cuối tháng 01       :  4.576.439đ  còn nợ dịch vụ

→ Sổ sách tháng 1 KHÔNG hiện 4,8 triệu doanh thu, chỉ hiện 223.561đ.
  Phần còn lại là NGHĨA VỤ PHẢI PHỤC VỤ 11,5 tháng nữa.

Nếu hội viên bảo lưu 15/03 → 13/04 (30 ngày):
  30 ngày đó KHÔNG ghi nhận đồng nào
  end_date đẩy từ 14/01/2027 → 13/02/2027
  Tổng vẫn đúng 4.800.000đ
```

> **Vì sao đây là đóng góp học thuật:** phần lớn phòng gym ghi hết 4,8 triệu vào tháng 1 → chủ phòng gym thấy tháng 1 lãi lớn, tháng 6 lỗ nặng, **không hiểu vì sao**. Tách bạch *"tiền thu được"* và *"doanh thu ghi nhận"* là thứ biến module kế toán từ CRUD thành nghiệp vụ thật.

---

## `revenue_recognition_entries` — bút toán ghi nhận *(append-only)*

| Cột | Kiểu | Giá trị / Ý nghĩa | Mẫu |
|---|---|---|---|
| `schedule_id` | `BIGINT` | Thuộc kế hoạch nào | `451` |
| `registration_id` | `BIGINT` | Hợp đồng nào | `451` |
| `recognition_date` | `DATE` | Ghi nhận cho ngày nào | `2026-08-06` |
| `amount` | `NUMERIC(14,2)` | **Khác 0**, âm = bút toán đảo | `315000.00` |
| `units` | `INTEGER` | Bao nhiêu đơn vị | `1` |
| `revenue_category` | `VARCHAR(30)` | `MEMBERSHIP` \| `PT` \| `DAY_PASS` \| `OTHER` | `PT` |
| `source_type` | `VARCHAR(30)` | `DAILY_ACCRUAL` (job đêm) \| `PT_SESSION` \| `REVERSAL` | `PT_SESSION` |
| `source_id` | `BIGINT` | ID nguồn | `9931` |

**Ràng buộc:** `UNIQUE(schedule_id, recognition_date) WHERE source_type='DAILY_ACCRUAL'` — mỗi hợp đồng chỉ ghi nhận 1 lần cho 1 ngày.

---

## `expenses` — chi phí vận hành

| Cột | Kiểu | Giá trị / Ý nghĩa | Mẫu |
|---|---|---|---|
| `category` | `VARCHAR(30)` | **10 nhóm** *(enum, không còn bảng riêng)* — xem dưới | `RENT` |
| `description` | `VARCHAR(255)` | | `Thuê mặt bằng quý III/2026` |
| `amount` | `NUMERIC(14,2)` | | `540000000.00` |
| `expense_date` | `DATE` | Ngày phát sinh/thanh toán | `2026-07-01` |
| `period_start` / `period_end` | `DATE` | **Kỳ chi phí** — để phân bổ theo tháng | `2026-07-01` / `2026-09-30` |
| `allocation_method` | `VARCHAR(20)` | `IMMEDIATE` \| `STRAIGHT_LINE` \| `DEPRECIATION` | `STRAIGHT_LINE` |
| `vendor` | `VARCHAR(150)` | Nhà cung cấp | `Công ty CP BĐS Minh Khang` |
| `invoice_ref` | `VARCHAR(80)` | Số hóa đơn của họ | `HD-2026-0712` |
| `attachment_key` | `VARCHAR(500)` | Chứng từ (key MinIO) | `expenses/3390/hoadon.pdf` |
| `payroll_run_id` | `BIGINT` | Nếu là chi phí lương | `NULL` |
| `feedback_id` | `BIGINT` | Nếu là chi phí sửa chữa | `NULL` |
| `equipment_id` | `BIGINT` | Nếu là khấu hao thiết bị | `NULL` |
| `status` | `VARCHAR(20)` | `DRAFT` \| `RECORDED` \| `APPROVED` \| `VOID` | `APPROVED` |
| `created_by` / `approved_by` | `BIGINT` | | `21` / `1` |

### `category` — 10 nhóm chi phí *(kèm số liệu mẫu/tháng)*

| Giá trị | Tên | Cố định? | Mẫu/tháng |
|---|---|:---:|---:|
| `RENT` | Thuê mặt bằng | ✅ | 180.000.000 |
| `SALARY` | Lương nhân sự cố định | ✅ | 165.000.000 |
| `PT_SESSION_FEE` | Tiền công buổi PT | ❌ | 108.000.000 |
| `COMMISSION` | Hoa hồng sale | ❌ | 24.000.000 |
| `UTILITIES` | Điện, nước | ❌ | 55.000.000 |
| `DEPRECIATION` | Khấu hao thiết bị | ✅ | 45.000.000 |
| `MARKETING` | Quảng cáo | ❌ | 40.000.000 |
| `CLEANING` | Vệ sinh, vật tư | ✅ | 18.000.000 |
| `MAINTENANCE` | Bảo trì, sửa chữa | ❌ | 15.000.000 |
| `SOFTWARE` | Phần mềm, internet | ✅ | 12.000.000 |
| | **Tổng** | | **662.000.000** |

> **`allocation_method` quan trọng thế nào:** thuê mặt bằng trả 540 triệu 1 lần cho cả quý. Ghi hết vào tháng 7 thì tháng 7 lỗ nặng, tháng 8–9 lãi ảo. Với `STRAIGHT_LINE` + `period_start/end`, hệ thống phân bổ **180 triệu/tháng** → P&L mới phản ánh đúng.

---

## `payroll_runs` — kỳ chạy lương

| Cột | Kiểu | Giá trị / Ý nghĩa | Mẫu |
|---|---|---|---|
| `period_year` / `period_month` | `SMALLINT` | **UNIQUE(year, month)** | `2026` / `8` |
| `status` | `VARCHAR(20)` | `DRAFT` → `REVIEW` → `APPROVED` → `PAID`; hoặc `VOID` | `APPROVED` |
| `total_amount` | `NUMERIC(14,2)` | Tổng lương phải trả | `297000000.00` |
| `calculated_at` | `TIMESTAMPTZ` | Chạy tính lúc nào | `2026-09-01 09:00+07` |
| `approved_by` / `approved_at` | | Kế toán chốt | `21` / `2026-09-04 16:00+07` |
| `paid_at` | `TIMESTAMPTZ` | Chuyển lương lúc nào | `2026-09-05 10:00+07` |
| `note` | `TEXT` | | `Đã điều chỉnh 2 khiếu nại của PT` |

> **`status = REVIEW` để làm gì:** sau khi tính xong, PT và Sale **xem chi tiết lương của mình trên app trong 3 ngày** và phản hồi nếu thấy sai. Minh bạch → giảm hẳn tranh chấp cuối tháng.

---

## `payroll_items` — chi tiết từng dòng lương ⭐

| Cột | Kiểu | Giá trị / Ý nghĩa | Mẫu |
|---|---|---|---|
| `payroll_run_id` | `BIGINT` | Kỳ lương nào | `8` |
| **`employee_id`** | `BIGINT` | Của ai *(một khóa ngoại duy nhất, nhờ gộp `employees`)* | `5` |
| `item_type` | `VARCHAR(30)` | **8 loại** — xem dưới | `SESSION_FEE` |
| `description` | `VARCHAR(255)` | Hiện trên app | `Buổi PT ngày 06/08 với Nguyễn Văn An` |
| `quantity` | `NUMERIC(10,2)` | | `1.00` |
| `unit_amount` | `NUMERIC(14,2)` | Đơn giá | `120000.00` |
| `amount` | `NUMERIC(14,2)` | Thành tiền. **Âm nếu khấu trừ** | `120000.00` |
| `source_type` / `source_id` | | Sinh từ đâu — **truy vết được** | `PT_SESSION` / `9931` |

### `item_type` — 8 loại

| Giá trị | Nghĩa | Dấu |
|---|---|:---:|
| `BASE_SALARY` | Lương cứng | + |
| `SESSION_FEE` | Tiền công buổi tập (1 dòng/buổi) | + |
| `SALES_COMMISSION` | Hoa hồng bán gói | + |
| `KPI_BONUS` | Thưởng KPI | + |
| `ALLOWANCE` | Phụ cấp | + |
| `DEDUCTION` | Khấu trừ (nghỉ không phép) | − |
| `PENALTY` | Phạt (hủy buổi muộn) | − |
| `ADJUSTMENT` | Điều chỉnh thủ công (kèm lý do) | ± |

### Dữ liệu mẫu — bảng lương tháng 8/2026 của PT Trần Bình

| item_type | description | qty | unit_amount | amount |
|---|---|---:|---:|---:|
| `BASE_SALARY` | Lương cứng tháng 8/2026 | 1 | 8.000.000 | **8.000.000** |
| `SESSION_FEE` | 42 buổi PT có phí (SENIOR) | 42 | 120.000 | **5.040.000** |
| `SESSION_FEE` | **6 buổi hỗ trợ miễn phí** | 6 | **60.000** | **360.000** ⭐ |
| `SESSION_FEE` | 3 buổi tập thử cho khách tiềm năng | 3 | 80.000 | **240.000** |
| `KPI_BONUS` | Điểm đánh giá TB 4,6/5 (≥4,5) | 1 | 1.000.000 | **1.000.000** |
| `PENALTY` | Hủy 1 buổi muộn (báo trước <4h) | 1 | −200.000 | **−200.000** |
| | | | **TỔNG** | **14.440.000** |

> **Toàn bộ sinh tự động từ `pt_sessions` — không nhập tay dòng nào.** PT mở app xem được từng dòng, bấm vào dòng nào cũng thấy buổi tập gốc.
>
> **Dòng in đậm:** 6 buổi miễn phí — hội viên **không bị trừ buổi nào**, PT **vẫn nhận 360.000đ**. Đúng yêu cầu của thầy.

**Ràng buộc:** `UNIQUE(source_type, source_id) WHERE item_type='SESSION_FEE'` — một buổi tập chỉ sinh công đúng 1 lần.

---
---

# NHÓM 9 — BÁN HÀNG

## `leads` — khách tiềm năng *(gánh `lead_activities`)*

| Cột | Kiểu | Giá trị / Ý nghĩa | Mẫu |
|---|---|---|---|
| **`person_id`** | `BIGINT` | Trỏ `persons.id`. **`NOT NULL`** — tiếp nhận lead là tạo `persons` ngay | `88` |
| `source` | `VARCHAR(30)` | **5 giá trị** — xem dưới | `WEB_FORM` |
| **`interested_membership_id`** | `BIGINT` | **Quan tâm gói nào** (`memberships.id`) | `7` |
| `assigned_to` | `BIGINT` | Sale nào phụ trách (`employees.id`) | `12` |
| `stage` | `VARCHAR(20)` | **6 giai đoạn** — xem dưới | `TRIAL_DONE` |
| `lost_reason` | `VARCHAR(30)` | `PRICE` \| `LOCATION` \| `COMPETITOR` \| `NOT_READY` \| `NO_RESPONSE` | `NULL` |
| `last_contact_at` | `TIMESTAMPTZ` | Liên hệ gần nhất *(thay bảng `lead_activities`)* | `2026-08-02 15:20+07` |
| `last_contact_note` | `TEXT` | Nội dung trao đổi gần nhất | `Khách quan tâm gói 6 tháng, xin thêm thời gian suy nghĩ` |
| `next_follow_up` | `DATE` | Hẹn liên hệ lại | `2026-08-05` |

### `source` — 5 nguồn, khách đều **tự để lại thông tin**

| Giá trị | Khách để lại thông tin bằng cách nào | Ai ghi |
|---|---|---|
| `WALK_IN` | Vào tận nơi tham quan, lễ tân xin số để tư vấn tiếp | Lễ tân / Sale |
| `HOTLINE` | Chủ động gọi tới hỏi giá | Sale |
| `WEB_FORM` | Điền form *"Để lại số để nhận tư vấn miễn phí"* trên website | Hệ thống tự tạo |
| `REFERRAL` | Hội viên cũ giới thiệu và cho số bạn mình | Sale |
| `APP_SELF` | **Đã tự đăng ký tài khoản app** nhưng chưa mua gói | Hệ thống tự gán |

> **Không có kênh quảng cáo trả phí** (Facebook Ads, Google Ads) — phòng gym trong phạm vi đồ án chỉ có website riêng, không chạy quảng cáo trên nền tảng ngoài. Cũng không có nguồn sự kiện vì không tổ chức gian hàng hội chợ.
>
> **`WEB_FORM` khác `APP_SELF` ở chỗ:** `WEB_FORM` chỉ có `persons` (tên + số điện thoại), còn `APP_SELF` đã có cả `users` (tài khoản đăng nhập). Nhóm `APP_SELF` giá trị hơn vì họ đã chủ động tải app và xem bảng giá.

### `stage` — phễu bán hàng

```
NEW → CONTACTED → TRIAL_BOOKED → TRIAL_DONE → WON
                                            ↘ LOST (+ lost_reason)
```

> **Bỏ `NEGOTIATING`:** giá và khuyến mãi đều công khai, không có bước thương lượng giá riêng với từng khách *(quyết định 12)*.

### Vì sao bỏ `full_name` / `phone` / `email` / `converted_member_id`

| Cột cũ | Thay bằng | Lý do |
|---|---|---|
| `full_name`, `phone`, `email` | `persons.full_name`, `persons.phone`, `persons.email` | Lead **là một con người thật**. Tiếp nhận lead thì tạo luôn `persons` — nhờ `persons.phone` **UNIQUE**, cùng một người gọi hotline rồi hôm sau tự tải app **chỉ có một dòng duy nhất**, không thể trùng |
| `converted_member_id` | `person_id` → `members.person_id` | `members.person_id` đã **UNIQUE** → truy ra được ngay, không cần lưu lại |

> Trước đây `person_id` cho phép `NULL` ("chưa có tài khoản") — chỉ vá được một nửa vấn đề trùng người vì lúc tạo lead vẫn có thể bỏ trống. Giờ **bắt buộc**, chống trùng ngay tại tầng CSDL.
>
> **`lost_reason` để làm gì:** thống kê cuối tháng thấy 40% mất khách vì `PRICE` → xem lại chính sách giá; vì `NO_RESPONSE` → vấn đề ở quy trình chăm sóc của Sale. Đây là dữ liệu ra quyết định.
>
> **`interested_membership_id` thay `interest`:** chữ tự do `"Giảm cân, gói 6 tháng"` không thống kê được. Khóa ngoại cho phép trả lời *"gói nào được quan tâm nhiều nhất nhưng tỷ lệ chốt thấp nhất"*.

### Danh sách "đã đăng ký app, chưa mua gói"

Không cần bảng riêng — một câu truy vấn:

```sql
SELECT p.full_name, p.phone, p.email, u.created_at AS ngay_dang_ky,
       EXTRACT(DAY FROM now() - u.created_at) AS so_ngay_da_qua
FROM   users u
JOIN   persons p ON p.id = u.person_id
LEFT   JOIN members m ON m.person_id = p.id
WHERE  u.primary_role = 'MEMBER'
  AND  m.id IS NULL                              -- chưa từng là hội viên
  AND  u.created_at < now() - INTERVAL '3 days'
ORDER BY u.created_at DESC;
```

---
---

# NHÓM 10 — BÀI TẬP & CHỈ SỐ CƠ THỂ

## `exercises` — thư viện bài tập

| Cột | Kiểu | Giá trị / Ý nghĩa | Mẫu |
|---|---|---|---|
| `code` | `VARCHAR(60)` | **UNIQUE** | `barbell-bench-press` |
| `name_vi` | `VARCHAR(200)` | **Tên tiếng Việt** | `Đẩy ngực với tạ đòn` |
| `name_en` | `VARCHAR(200)` | Tên gốc | `Barbell Bench Press` |
| `muscle_group` | `VARCHAR(40)` | `CHEST` \| `BACK` \| `LEGS` \| `SHOULDERS` \| `ARMS` \| `CORE` \| `CARDIO` | `CHEST` |
| `secondary_muscles` | `VARCHAR(200)` | Nhóm cơ phụ | `TRICEPS, SHOULDERS` |
| `equipment` | `VARCHAR(60)` | `BARBELL` \| `DUMBBELL` \| `MACHINE` \| `BODYWEIGHT` \| `CABLE` \| `KETTLEBELL` | `BARBELL` |
| `difficulty` | `VARCHAR(20)` | `BEGINNER` \| `INTERMEDIATE` \| `ADVANCED` | `INTERMEDIATE` |
| `instructions` | `TEXT` | Hướng dẫn thực hiện | `1. Nằm ngửa trên ghế, hai chân đặt vững...` |
| `media_key` | `VARCHAR(500)` | Ảnh minh họa (key MinIO) | `exercises/bench-press.jpg` |
| `contraindications` | `JSONB` | **Chống chỉ định** — cơ sở để chatbot loại bài không an toàn | `["LOWER_BACK_PAIN","SHOULDER_INJURY"]` |
| `source` | `VARCHAR(60)` | `FREE_EXERCISE_DB` \| `WGER` \| `CUSTOM` | `FREE_EXERCISE_DB` |
| `license` | `VARCHAR(60)` | **Ghi rõ để tuân thủ bản quyền** | `Unlicense` |
| `is_reviewed` | `BOOLEAN` | **Bản dịch đã có người rà soát chưa** | `TRUE` |

> **`is_reviewed` quan trọng:** ~870 bài được dịch sang tiếng Việt bằng LLM theo lô (Batch API, ~1 USD một lần). Bản dịch máy **bắt buộc có người đọc lại**. Bài `is_reviewed = FALSE` **không hiển thị** cho hội viên.
>
> **`contraindications`:** nếu **không** làm Knowledge Graph (Neo4j) thì cột JSONB này là phương án thay thế — đủ để loại bài tập không an toàn theo `members.health_note`.

---

## `workout_plans` — giáo án *(gánh `workout_templates`)*

| Cột | Kiểu | Giá trị / Ý nghĩa | Mẫu |
|---|---|---|---|
| **`is_template`** | `BOOLEAN` | **`TRUE` = giáo án MẪU** (chưa gán cho ai) | `FALSE` |
| `member_id` | `BIGINT` | Của hội viên nào. **`NULL` khi `is_template=TRUE`** | `123` |
| `trainer_id` | `BIGINT` | PT nào soạn | `5` |
| `source_template_id` | `BIGINT` | Dựa trên giáo án mẫu nào | `3` |
| `name` | `VARCHAR(150)` | | `Giáo án tăng cơ - An - T8/2026` |
| `goal` | `VARCHAR(30)` | `LOSE_FAT` \| `GAIN_MUSCLE` \| `STRENGTH` \| `ENDURANCE` | `GAIN_MUSCLE` |
| `level` | `VARCHAR(20)` | `BEGINNER` \| `INTERMEDIATE` \| `ADVANCED` | `BEGINNER` |
| `duration_weeks` | `SMALLINT` | | `8` |
| `days_per_week` | `SMALLINT` | | `4` |
| `start_date` / `end_date` | `DATE` | | `2026-08-01` / `2026-09-26` |
| `status` | `VARCHAR(20)` | `ACTIVE` \| `COMPLETED` \| `PAUSED` \| `CANCELLED` | `ACTIVE` |
| `ai_generated` | `BOOLEAN` | Do AI sinh bản nháp? | `TRUE` |
| `reviewed_by` | `BIGINT` | **PT đã duyệt** — bắt buộc nếu `ai_generated` | `5` |

**Ràng buộc:**
```sql
CHECK (NOT ai_generated OR reviewed_by IS NOT NULL)   -- AI sinh thì PT PHẢI duyệt
CHECK (is_template = (member_id IS NULL))             -- mẫu thì không gán ai
```

> **Vì sao gộp `workout_templates`:** giáo án mẫu **chính là giáo án chưa gán cho ai**. Một cờ boolean thay được cả một cặp bảng.
>
> **`ai_generated` + `reviewed_by`:** ranh giới an toàn — AI hỗ trợ, con người chịu trách nhiệm. Ràng buộc ở tầng CSDL, không thể lách bằng code.

---

## `workout_plan_items` — bài tập trong giáo án

| Cột | Kiểu | Giá trị / Ý nghĩa | Mẫu |
|---|---|---|---|
| `plan_id` | `BIGINT` | Thuộc giáo án nào | `77` |
| `day_index` | `SMALLINT` | Ngày thứ mấy trong chu kỳ | `1` |
| `order_index` | `SMALLINT` | Bài thứ mấy trong ngày | `1` |
| `exercise_id` | `BIGINT` | Bài tập nào | `112` |
| `sets` | `SMALLINT` | Số hiệp | `4` |
| `reps` | `VARCHAR(20)` | **Kiểu chữ** vì có dạng khoảng | `8-12` |
| `target_weight_kg` | `NUMERIC(6,2)` | Mức tạ mục tiêu | `60.00` |
| `rest_sec` | `SMALLINT` | Nghỉ giữa hiệp | `90` |
| `note` | `VARCHAR(255)` | Ghi chú riêng | `An đau lưng, giảm tạ 20% tuần đầu` |

`UNIQUE(plan_id, day_index, order_index)`

> **`reps` là `VARCHAR` chứ không phải số** vì PT ghi `"8-12"`, `"AMRAP"` (tối đa có thể), `"30 giây"`.

---

## `workout_logs` — hội viên ghi lại buổi tập

| Cột | Kiểu | Giá trị / Ý nghĩa | Mẫu |
|---|---|---|---|
| `plan_item_id` | `BIGINT` | Theo bài nào trong giáo án | `340` |
| `member_id` | `BIGINT` | Ai tập | `123` |
| `exercise_id` | `BIGINT` | Bài gì | `112` |
| `pt_session_id` | `BIGINT` | Trong buổi PT nào. `NULL` = tự tập | `9931` |
| `performed_at` | `TIMESTAMPTZ` | | `2026-08-06 19:15+07` |
| `sets_done` | `SMALLINT` | Làm được mấy hiệp | `4` |
| `reps_done` | `VARCHAR(40)` | Số lần từng hiệp | `12,10,10,8` |
| `weight_kg` | `NUMERIC(6,2)` | Mức tạ thực tế | `62.50` |
| `rpe` | `SMALLINT` | Mức gắng sức tự đánh giá `1–10` | `8` |
| `note` | `VARCHAR(255)` | | `Tăng 2,5kg so với tuần trước` |

> Đây là dữ liệu để hội viên **thấy mình tiến bộ** — biểu đồ *"mức tạ bench press theo tuần"* là thứ tạo động lực mạnh nhất.

---

## `body_metrics` — chỉ số cơ thể

| Cột | Kiểu | Giá trị / Ý nghĩa | Mẫu |
|---|---|---|---|
| `member_id` | `BIGINT` | Của ai | `123` |
| `measured_at` | `TIMESTAMPTZ` | Đo lúc nào | `2026-08-01 18:00+07` |
| `measured_by` | `BIGINT` | Ai đo. `NULL` = tự nhập | `5` |
| `source` | `VARCHAR(20)` | `MANUAL` \| `INBODY_OCR` \| `DEVICE_SYNC` | `INBODY_OCR` |
| `height_cm` | `NUMERIC(5,2)` | Hợp lệ `80–250` | `175.00` |
| `weight_kg` | `NUMERIC(5,2)` | Hợp lệ `20–300` | `72.50` |
| `body_fat_pct` | `NUMERIC(4,2)` | Hợp lệ `1–70` | `18.40` |
| `muscle_mass_kg` | `NUMERIC(5,2)` | | `33.20` |
| `visceral_fat` | `NUMERIC(4,1)` | Mỡ nội tạng | `7.0` |
| `bmr_kcal` | `INTEGER` | Trao đổi chất cơ bản | `1659` |
| `bmi` | `NUMERIC(5,2)` | **Cột TỰ TÍNH** `= kg / (m)²` | `23.67` |
| `chest_cm` / `waist_cm` / `hip_cm` / `arm_cm` / `thigh_cm` | `NUMERIC(5,2)` | Số đo vòng | `98` / `84` / `95` / `33.5` / `56` |
| `photo_key` | `VARCHAR(500)` | Ảnh phiếu InBody hoặc ảnh tiến trình | `inbody/123-20260801.jpg` |
| `ocr_confidence` | `NUMERIC(4,3)` | Độ tin cậy đọc máy `0–1` | `0.940` |
| `confirmed_by_user` | `BOOLEAN` | **Người dùng đã xác nhận số liệu chưa** | `TRUE` |
| `note` | `VARCHAR(255)` | | `Đo sau khi nhịn ăn 3h` |

### Công thức tính

```
BMI  = weight_kg / (height_cm/100)²           → 72,5 / 1,75² = 23,67
BMR  (Mifflin-St Jeor, nam) = 10×kg + 6,25×cm − 5×tuổi + 5
     = 10×72,5 + 6,25×175 − 5×28 + 5 = 1.658,75 kcal
TDEE = BMR × hệ số vận động (ít 1,2 · nhẹ 1,375 · vừa 1,55 · nhiều 1,725)
WHR  = waist_cm / hip_cm                      → 84/95 = 0,88
```

### Ngưỡng BMI người châu Á (WHO Asia-Pacific) — **khác ngưỡng quốc tế**

| Phân loại | BMI châu Á | BMI quốc tế |
|---|---|---|
| Thiếu cân | < 18,5 | < 18,5 |
| Bình thường | 18,5 – 22,9 | 18,5 – 24,9 |
| **Thừa cân** | **23,0 – 24,9** | 25,0 – 29,9 |
| Béo phì độ I | 25,0 – 29,9 | 30,0 – 34,9 |
| Béo phì độ II | ≥ 30,0 | ≥ 35,0 |

> BMI 23,67 của An là **"thừa cân" theo ngưỡng châu Á** nhưng "bình thường" theo ngưỡng quốc tế. **Phải dùng ngưỡng châu Á.**
>
> ⚠️ **Ranh giới đạo đức:** hệ thống **chỉ hiển thị số liệu và tham chiếu ngưỡng WHO**, **không chẩn đoán, không kê chế độ dinh dưỡng cá nhân hóa**. Mọi màn hình phải có dòng *"Thông tin mang tính tham khảo. Vui lòng tham vấn bác sĩ hoặc chuyên gia dinh dưỡng trước khi thay đổi chế độ tập luyện/ăn uống."*
>
> **`confirmed_by_user` với `INBODY_OCR`:** hội viên chụp ảnh phiếu InBody → LLM đọc số → **hiển thị form đã điền sẵn, ô nào `ocr_confidence` thấp thì tô vàng** → người dùng kiểm tra và sửa → mới lưu. **AI không bao giờ ghi thẳng vào CSDL.**

---
---

# NHÓM 11 — PHẢN HỒI

## `feedbacks` — phản ánh ⭐ *(gánh `trainer_ratings` + `maintenance_work_orders`)*

| Cột | Kiểu | Giá trị / Ý nghĩa | Mẫu |
|---|---|---|---|
| `member_id` | `BIGINT` | Ai phản ánh | `123` |
| `feedback_type` | `VARCHAR(20)` | `TRAINER` \| `FACILITY` \| `HYGIENE` \| `SERVICE` \| `GENERAL` | `FACILITY` |
| `target_employee_id` | `BIGINT` | Về nhân viên/PT nào | `NULL` |
| `target_equipment_id` | `BIGINT` | Về thiết bị nào | `12` |
| `pt_session_id` | `BIGINT` | Đánh giá sau buổi tập nào | `NULL` |
| `rating` | `SMALLINT` | Điểm `1–5` *(thay bảng `trainer_ratings`)* | `NULL` |
| `title` | `VARCHAR(200)` | | `Máy chạy bộ TM-003 kêu to` |
| `content` | `TEXT` | Bắt buộc | `Chạy trên 8km/h thì máy rung và kêu rất to` |
| `photo_key` | `VARCHAR(500)` | Ảnh kèm theo | `feedback/882.jpg` |
| `severity` | `VARCHAR(10)` | `LOW` \| `MEDIUM` \| `HIGH` | `MEDIUM` |
| `status` | `VARCHAR(20)` | `OPEN` \| `IN_PROGRESS` \| `WAITING_PARTS` \| `RESOLVED` \| `CLOSED` \| `REJECTED` | `RESOLVED` |
| `assigned_to` | `BIGINT` | Giao cho ai xử lý | `25` |
| `resolution` | `TEXT` | Đã xử lý thế nào | `Đã thay dây curoa ngày 03/08` |
| **`repair_cost`** | `NUMERIC(14,2)` | Chi phí sửa *(thay `maintenance_work_orders`)* | `1200000.00` |
| `expense_id` | `BIGINT` | Dòng chi phí tự động sinh ra | `3391` |
| `resolved_at` | `TIMESTAMPTZ` | | `2026-08-03 10:00+07` |
| `sla_due_at` | `TIMESTAMPTZ` | Hạn phải xử lý xong | `2026-08-04 19:20+07` |
| `is_anonymous` | `BOOLEAN` | Ẩn danh (dám nói thật hơn) | `FALSE` |

### Định tuyến tự động theo `feedback_type`

| Loại | Hệ thống tự động làm gì |
|---|---|
| `FACILITY` | → `equipment.status = NEEDS_REPAIR` · ẩn thiết bị khỏi lịch lớp · khi `RESOLVED` + có `repair_cost` → **tự sinh dòng `expenses`** |
| `TRAINER` | → Cập nhật `employees.rating_avg` → ảnh hưởng `KPI_BONUS` trong lương. Nếu `rating ≤ 2` → tạo task cho Admin xử lý trong 24h |
| `HYGIENE` | → Task cho quản lý ca |
| `SERVICE` / `GENERAL` | → Hàng đợi Admin |

**Ràng buộc:** `UNIQUE(pt_session_id, member_id) WHERE pt_session_id IS NOT NULL` — mỗi buổi tập đánh giá 1 lần, chống spam điểm.

> **Vì sao gộp 3 bảng:** đánh giá PT **là một loại phản hồi** (`feedbacks` vốn đã có `rating`). Phiếu sửa chữa **là vòng đời tiếp theo của phản ánh về thiết bị** — cùng dòng `OPEN → IN_PROGRESS → RESOLVED`. Tách ra buộc phải copy dữ liệu qua lại.
>
> **Điểm tạo niềm tin:** khi xử lý xong, hệ thống **gửi thông báo ngược lại cho hội viên đã báo** — *"Máy chạy bộ TM-003 đã được sửa. Cảm ơn bạn đã phản ánh!"*.

---
---

# NHÓM 12 — HỆ THỐNG

## `system_settings` — cấu hình *(gánh 4 bảng cũ)*

| Cột | Kiểu | Ý nghĩa | Mẫu |
|---|---|---|---|
| `key` | `VARCHAR(80)` | **Khóa chính** | `freeze.max_days_per_year` |
| `value` | `JSONB` | Giá trị (số, chuỗi, mảng, object) | `30` |
| `description` | `VARCHAR(255)` | Giải thích cho Admin | `Số ngày bảo lưu tối đa trong 1 năm hợp đồng` |
| `updated_by` / `updated_at` | | | `1` / `2026-07-01 09:00+07` |

### Danh sách cấu hình đầy đủ

| key | value mẫu | Ý nghĩa |
|---|---|---|
| **— Thông tin phòng gym —** | | |
| `gym.name` | `"Fitness Center ABC"` | Tên phòng gym |
| `gym.address` | `"123 Nguyễn Trãi, Thanh Xuân, Hà Nội"` | |
| `gym.opening_time` / `gym.closing_time` | `"05:30"` / `"22:30"` | Giờ mở/đóng cửa |
| **— Bảo lưu —** | | |
| `freeze.max_days_per_year` | `30` | Bảo lưu tối đa 30 ngày/năm |
| `freeze.max_times_per_year` | `2` | Tối đa 2 lần |
| `freeze.min_advance_days` | `3` | Báo trước 3 ngày |
| `freeze.min_package_days` | `90` | Chỉ gói ≥ 90 ngày được bảo lưu |
| **— Check-in —** | | |
| `checkin.antipassback_minutes` | `30` | Cảnh báo nếu check-in lại trong 30 phút |
| `checkin.qr_period_sec` | `30` | Mã QR đổi mỗi 30 giây |
| **— Buổi tập PT —** | | |
| `booking.late_cancel_hours` | `4` | Hủy < 4h là hủy muộn, mất buổi |
| `booking.auto_confirm_hours` | `24` | HV không xác nhận trong 24h thì tự duyệt |
| `pt.max_complimentary_per_month` | `10` | Mỗi PT tối đa 10 buổi miễn phí/tháng |
| **— Lương *(thay bảng `payroll_rate_cards`)* —** | | |
| `payroll.rate_card` | `{"PAID_PT":{"JUNIOR":90000,"SENIOR":120000,"MASTER":160000},"COMPLIMENTARY":60000,"TRIAL":80000,"ORIENTATION":50000,"ASSESSMENT":70000,"MAKEUP":120000,"CLASS":200000}` | Đơn giá công theo loại buổi |
| `payroll.commission_tiers` | `[{"from":0,"to":50000000,"pct":2.0},{"from":50000000,"to":100000000,"pct":3.5},{"from":100000000,"to":null,"pct":5.0}]` | Bậc hoa hồng sale *(thay `commission_rules`)* |
| **— Chiết khấu *(thay bảng `discount_policies`)* —** | | |
| `discount.max_percent` | `{"SALE":20,"RECEPTIONIST":5,"ADMIN":100}` | Hạn mức giảm giá theo vai trò |
| `discount.approval_above` | `{"SALE":10,"RECEPTIONIST":0}` | Trên mức này phải xin duyệt |
| **— Chatbot & LLM —** | | |
| `llm.monthly_budget_usd` | `60` | Ngân sách LLM/tháng |
| `llm.max_chat_per_day.member` | `20` | Hội viên: 20 lượt/ngày |
| `llm.max_chat_per_day.non_member` | `5` | Chưa mua gói: 5 lượt/ngày |
| **— Bảo mật & lưu trữ —** | | |
| `auth.max_failed_attempts` | `5` | Sai quá số này thì khóa tạm |
| `auth.lockout_minutes` | `15` | Khóa tạm bao lâu |
| `auth.reset_token_ttl_minutes` | `30` | Link đặt lại mật khẩu sống bao lâu |
| `account.inactive_delete_months` | `24` | Xóa tài khoản chưa từng mua gói sau 24 tháng không hoạt động |

> **Vì sao không hard-code:** khi phòng gym đổi chính sách bảo lưu từ 30 lên 45 ngày, Admin sửa 1 dòng cấu hình — **không cần lập trình viên, không cần deploy lại**. Đây là điểm phân biệt hệ thống làm nghiêm túc với đồ án làm cho xong.

---
---

# Phụ lục A — Bảng tra nhanh toàn bộ enum

| Bảng.Cột | Giá trị hợp lệ |
|---|---|
| `users.primary_role` | `ADMIN` `MEMBER` `TRAINER` `SALE` `RECEPTIONIST` `ACCOUNTANT` |
| `users.status` | `ACTIVE` `LOCKED` |
| `persons.gender` | `MALE` `FEMALE` `OTHER` |
| `members.status` | `ACTIVE` `BLACKLISTED` |
| `members.goal`, `workout_plans.goal` | `LOSE_FAT` `GAIN_MUSCLE` `ENDURANCE` `HEALTH` *(+`STRENGTH` cho giáo án)* |
| `members.source`, `leads.source` | `WALK_IN` `HOTLINE` `WEB_FORM` `REFERRAL` `APP_SELF` |
| `employees.department` | `TRAINING` `SALES` `FRONT_DESK` `ACCOUNTING` |
| `employees.employment_type` | `FULL_TIME` `PART_TIME` `FREELANCE` |
| `employees.level` | `JUNIOR` `SENIOR` `MASTER` |
| `employees.specialties` | `FITNESS` `YOGA` `CROSSFIT` `BOXING` `REHAB` `NUTRITION` |
| `memberships.package_type` | `TIME_BASED` `SESSION_BASED` `HYBRID` `DAY_PASS` |
| `memberships.area_codes` | `GYM_FLOOR` `YOGA_STUDIO` `POOL` `SAUNA` `GROUP_CLASS` |
| `registrations.status` | `PENDING_PAYMENT` `ACTIVE` `FROZEN` `COMPLETED` `CANCELLED` `REFUNDED` |
| `registration_freezes.status` | `PENDING` `APPROVED` `REJECTED` `ACTIVE` `ENDED` `CANCELLED` |
| `registration_freezes.reason_type` | `PERSONAL` `MEDICAL` `TRAVEL` `OTHER` |
| `member_trainers.role` | `PRIMARY` `SECONDARY` `SUBSTITUTE` |
| `check_ins.method` | `QR_DYNAMIC` `RFID` `MANUAL` `FACE` `DAY_PASS` |
| `check_ins.result` | `ALLOWED` `ALLOWED_OVERRIDE` `DENIED_EXPIRED` `DENIED_FROZEN` `DENIED_UNPAID` `DENIED_NOT_FOUND` `DENIED_SUSPECT` |
| `check_ins.incident_type` | `ANTI_PASSBACK` `SUSPECTED_SHARING` `REPLAY_ATTEMPT` `FACE_MISMATCH` `EXPIRED_ATTEMPT` |
| `pt_sessions.session_type` | `PAID_PT` `COMPLIMENTARY` `TRIAL` `ORIENTATION` `MAKEUP` `ASSESSMENT` |
| `pt_sessions.status` | `PENDING_TRAINER` `REJECTED` `SCHEDULED` `IN_PROGRESS` `COMPLETED` `NO_SHOW_MEMBER` `NO_SHOW_TRAINER` `CANCELLED` |
| `session_credit_ledger.entry_type` | `GRANT` `CONSUME` `REFUND` `EXPIRE` `ADJUST` |
| `class_sessions.class_type` | `YOGA` `FITNESS` `HIIT` `ZUMBA` `SPINNING` `BOXING` |
| `class_sessions.status` | `SCHEDULED` `IN_PROGRESS` `COMPLETED` `CANCELLED` |
| `class_bookings.status` | `BOOKED` `WAITLISTED` `ATTENDED` `NO_SHOW` `CANCELLED` |
| `equipment.category` | `CARDIO` `STRENGTH` `FREE_WEIGHT` `ACCESSORY` `YOGA` |
| `equipment.status` | `OPERATIONAL` `NEEDS_REPAIR` `UNDER_MAINTENANCE` `RETIRED` |
| `invoices.status` | `UNPAID` `PARTIALLY_PAID` `PAID` `OVERDUE` `CANCELLED` `REFUNDED` |
| `payments.payment_type` | `PAYMENT` `REFUND` |
| `payments.method` | `CASH` `BANK_TRANSFER` `VIETQR` `CARD_POS` `E_WALLET` `GATEWAY` |
| `payments.status` | `INITIATED` `PENDING` `SUCCEEDED` `FAILED` `EXPIRED` |
| `cash_shifts.status` | `OPEN` `CLOSED` `DISCREPANCY` |
| `revenue_schedules.recognition_method` | `STRAIGHT_LINE` `PER_SESSION` `IMMEDIATE` |
| `expenses.category` | `RENT` `SALARY` `PT_SESSION_FEE` `COMMISSION` `UTILITIES` `DEPRECIATION` `MARKETING` `CLEANING` `MAINTENANCE` `SOFTWARE` |
| `expenses.allocation_method` | `IMMEDIATE` `STRAIGHT_LINE` `DEPRECIATION` |
| `payroll_runs.status` | `DRAFT` `REVIEW` `APPROVED` `PAID` `VOID` |
| `payroll_items.item_type` | `BASE_SALARY` `SESSION_FEE` `SALES_COMMISSION` `KPI_BONUS` `ALLOWANCE` `DEDUCTION` `PENALTY` `ADJUSTMENT` |
| `leads.stage` | `NEW` `CONTACTED` `TRIAL_BOOKED` `TRIAL_DONE` `WON` `LOST` |
| `leads.lost_reason` | `PRICE` `LOCATION` `COMPETITOR` `NOT_READY` `NO_RESPONSE` |
| `exercises.muscle_group` | `CHEST` `BACK` `LEGS` `SHOULDERS` `ARMS` `CORE` `CARDIO` |
| `exercises.equipment` | `BARBELL` `DUMBBELL` `MACHINE` `BODYWEIGHT` `CABLE` `KETTLEBELL` |
| `exercises.difficulty`, `workout_plans.level` | `BEGINNER` `INTERMEDIATE` `ADVANCED` |
| `body_metrics.source` | `MANUAL` `INBODY_OCR` `DEVICE_SYNC` |
| `feedbacks.feedback_type` | `TRAINER` `FACILITY` `HYGIENE` `SERVICE` `GENERAL` |
| `feedbacks.status` | `OPEN` `IN_PROGRESS` `WAITING_PARTS` `RESOLVED` `CLOSED` `REJECTED` |
| `audit_logs.action` | `CREATE` `UPDATE` `DELETE` `APPROVE` `REJECT` `LOGIN` `LOGOUT` `EXPORT` `ANONYMIZE` |

---

# Phụ lục B — Truy vấn kiểm tra bất biến

> Cả ba truy vấn dưới đây **phải luôn trả về 0 dòng**. Dùng trong property-based test và job giám sát hàng đêm.

```sql
-- BẤT BIẾN 1: Tổng delta của sổ cái == balance_after của bút toán mới nhất
SELECT registration_id FROM (
  SELECT registration_id, SUM(delta) AS total,
         (array_agg(balance_after ORDER BY id DESC))[1] AS last_balance
  FROM session_credit_ledger GROUP BY registration_id) t
WHERE total <> last_balance;

-- BẤT BIẾN 2: Doanh thu đã ghi nhận + chưa ghi nhận == giá trị hợp đồng
SELECT rs.id FROM revenue_schedules rs
JOIN registrations r ON r.id = rs.registration_id
WHERE rs.recognized_amount + rs.deferred_amount <> r.final_price;

-- BẤT BIẾN 3: Tổng bút toán ghi nhận == recognized_amount trên schedule
SELECT rs.id FROM revenue_schedules rs
LEFT JOIN (SELECT schedule_id, SUM(amount) s
           FROM revenue_recognition_entries GROUP BY schedule_id) e
       ON e.schedule_id = rs.id
WHERE COALESCE(e.s, 0) <> rs.recognized_amount;
```

---

# Phụ lục C — Việc còn lại

| # | Việc | Trạng thái |
|---|---|---|
| 1 | **Quyết định về churn** — giữ (34 bảng) hay bỏ (32 bảng) | ⏳ Chờ bạn |
| 2 | **`refresh_tokens`** — kế hoạch 31 bảng đưa sang Redis, nhưng giờ đã có `password_reset_tokens` là bảng. Có nên đưa `refresh_tokens` về CSDL cho nhất quán? *(→ 33 bảng)* | ⏳ **Cần bạn xác nhận** |
| 3 | Viết lại `schema.sql` theo 32 bảng | Chưa làm |
| 4 | Cập nhật `00-TONG-QUAN-BANG.sql` | Chưa làm |
| 5 | Cập nhật `docs/03-CO-SO-DU-LIEU.md` (sơ đồ nhóm, migration) | Chưa làm |
| 6 | Vẽ lại ERD | Chưa làm |

### Về mục 2 — lập luận cho việc đưa `refresh_tokens` về CSDL

| | Redis | CSDL |
|---|---|---|
| Reuse detection (`replaced_by` chain) | Làm được nhưng cồng kềnh | **Tự nhiên** — một cột khóa ngoại |
| Xem "các thiết bị đang đăng nhập" | Cần cấu trúc set phụ | Một câu `SELECT` |
| Trình bày trong báo cáo | Key-value khó vẽ | **Bảng có quan hệ, dễ minh họa** |
| Thầy yêu cầu *"tự làm, hiểu quy trình login/phân quyền"* | Khó thể hiện | **Dễ chứng minh** |
| Chi phí | 0 | +1 bảng + 1 job dọn dẹp |

**Khuyến nghị: đưa `refresh_tokens` về CSDL** → **33 bảng**. Redis vẫn dùng cho QR nonce, rate limit, cache — không bỏ Redis.
