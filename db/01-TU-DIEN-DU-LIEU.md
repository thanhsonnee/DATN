# ⚠️ KẾ HOẠCH TỐI GIẢN DATABASE: 73 → 31 BẢNG

> **Đọc 5 phút.** Phần này là **kế hoạch sửa đổi**, chưa thực hiện.
> Toàn bộ nội dung phía dưới (từ điển 73 bảng) **giữ nguyên** để đối chiếu.

## Kết quả

| | Trước | Sau | |
|---|---:|---:|---|
| Tổng số bảng | 73 | **31** | −58% |
| Bảng vẽ trong ERD nghiệp vụ | 73 | **29** | *(bỏ 2 bảng kỹ thuật)* |
| Đóng góp học thuật giữ được | 6 | **5** | mất ĐG4 (churn) |

**29 bảng vẽ vừa một trang A3** — đủ để in vào báo cáo và trình bày trên slide.

---

## 4 nguyên tắc cắt *(dùng để trả lời hội đồng "sao em cắt bảng này")*

| # | Nguyên tắc | Ví dụ |
|---|---|---|
| **1** | **Cùng một sự vật ở hai giai đoạn → một bảng, khác `status`** | Báo giá và hợp đồng là một; đặt lịch và buổi tập là một |
| **2** | **Bảng con chỉ có quan hệ 1–1 → gộp thành cột** | Sự cố check-in luôn thuộc đúng 1 lượt check-in |
| **3** | **Bảng cấu hình không phải thực thể nghiệp vụ → `system_settings`** | Đơn giá công PT, hạn mức chiết khấu, bậc hoa hồng |
| **4** | **Tính năng Should/Could have → cắt, ghi vào "hướng phát triển"** | Trả góp, chuyển nhượng, thông báo push, churn |

---

## 9 phép gộp đáng nói *(đây là phần thể hiện tư duy thiết kế khi bảo vệ)*

| Bỏ bảng | Gộp vào đâu | Vì sao hợp lý |
|---|---|---|
| `quotes` + `quote_items` | `registrations` với `status='DRAFT'` | **Báo giá chính là hợp đồng chưa chốt.** Sale sửa giá trên bản nháp, khách đồng ý thì chuyển `PENDING_PAYMENT` | (chưa hiểu)
| `pt_bookings` | `pt_sessions` với `status='PENDING_TRAINER'` | **Yêu cầu đặt lịch chính là buổi tập chưa được duyệt.** Cùng một buổi tập, khác giai đoạn |
| `refunds` | `payments` với `amount` **âm** | Đây đúng là cách kế toán làm: hoàn tiền = **bút toán đảo**, không phải loại chứng từ khác |
| `workout_templates` + `..._items` | `workout_plans` với `is_template=TRUE` | **Giáo án mẫu = giáo án chưa gán cho ai** (`member_id IS NULL`) |
| `trainers` + `staff` | `employees` | **PT cũng là nhân viên.** Bỏ được cặp khóa ngoại `trainer_id`/`staff_id` lặp ở `payroll_items`, `check_ins` |
| `trainer_ratings` | `feedbacks` | Đánh giá PT **là một loại** phản hồi. `feedbacks` đã có sẵn `rating` + `target_employee_id` |
| `maintenance_work_orders` | `feedbacks` + cột `repair_cost` | Báo hỏng và phiếu sửa là **một vòng đời**: mở → xử lý → xong |
| `check_in_incidents` | `check_ins` + 2 cột | Sự cố **luôn 1–1** với một lượt check-in |
| `membership_prices` | Snapshot sẵn có trong `registrations` | **Snapshot chính là bản ghi lịch sử giá.** Bảng versioning chỉ cần khi muốn hẹn giờ đổi giá trước |

---

## 31 bảng giữ lại

oke, đi từ bảng members đi - nếu mình là member thì sẽ quan tâm những gì?
+ quan tâm cơ sở vật chất (thiết bị tập có mới ko, ko gian phòng tập có đảm bảo vệ sinh ko, trông ko gian đó tnao, có điều hòa ko, liệu sau khi tập thì có thể nghỉ ngơi ko (ví dụ: sauna, phòng tắm có sạch sẽ không))
+ chương trình ưu đãi (ví dụ so sánh với các phòng gym khác, thì giá của gói tập này có rẻ hơn ko, dịch vụ đi kèm có tốt hơn ko, nếu muốn bảo lưu thì có ok ko)
+ gói tập nào phù hợp (với budget, với thời gian biểu cá nhân)
+ chỉ số cơ thể của mình ()

ok, cái này mình đưa cho GPT thì nó liệt kê cho mình còn nhiều vcl
nhưng câu hỏi đặt ra là: liệu với cái nhu cầu nào của người dùng - ứng với tính năng nào mà mình sẽ làm (liệu tính năng đấy mình có cần highly invest ko, nghiên cứu chuyên sâu ko)
ví dụ: như cô Giang đã bảo:
+ multi tenant, RAG, knowledge graph

+ đối với admin: yêu cầu có biểu đồ thể hiện doanh thu phòng tập, list cụ thể những cái feedback của user, CRUD như mình đã viết


| Nhóm | Bảng | Ghi chú |
|---|---|---|
| **Danh tính (5)** | `persons` | Con người thật |
| | `users` | Tài khoản. **Bỏ `user_roles`** — 1 tài khoản 1 role |
| | `members` | Hội viên |
| | `employees` ⭐mới | **Gộp `trainers` + `staff`**. `department` = `TRAINING`/`SALES`/`FRONT_DESK`/`ACCOUNTING` |
| | `audit_logs` | *Kỹ thuật — không vẽ ERD* |
| **Gói & Hợp đồng (4)** | `memberships` | Thêm cột `price`, `area_codes JSONB` |
| | `registrations` ⭐ | **Gánh luôn vai trò báo giá** (`status='DRAFT'`) |
| | `registration_freezes` ⭐ | Bảo lưu — yêu cầu của thầy |
| | `member_trainers` ⭐ | 2–3 PT / 1 hội viên — yêu cầu của thầy |
| **Check-in (1)** | `check_ins` ⭐ | Gộp `access_cards`→`persons.card_uid`, `qr_secrets`→`persons.qr_secret_enc`, `check_in_incidents`→2 cột |
| **Buổi tập PT (2)** | `pt_sessions` ⭐ | **Gánh luôn `pt_bookings`** |
| | `session_credit_ledger` ⭐⭐⭐ | **Đóng góp chính — không đụng tới** |
| **Lớp nhóm (2)** | `class_sessions` | Gộp 3 bảng: định nghĩa + lịch + phòng ghi thẳng vào đây |
| | `class_bookings` | Trả lời câu hỏi cô Trinh về class ↔ membership |
| **Thiết bị (1)** | `equipment` | Gộp `equipment_types`; `location` là chuỗi, bỏ bảng `rooms` |
| **Thanh toán (3)** | `invoices` ⭐ | Bỏ `invoice_items` — 1 hóa đơn 1 gói tập |
| | `payments` ⭐ | **Gánh `refunds`** (amount âm) + `webhook_events` (`raw_payload`) |
| | `cash_shifts` ⭐ | Đối soát tiền mặt — nghiệp vụ lễ tân |
| **Tài chính (5)** | `revenue_schedules` ⭐⭐ | **Đóng góp chính — không đụng tới** |
| | `revenue_recognition_entries` ⭐⭐ | |
| | `expenses` ⭐ | `category` là enum trong cột, bỏ bảng `expense_categories` |
| | `payroll_runs` ⭐ | |
| | `payroll_items` ⭐ | Đơn giá công lấy từ `system_settings` |
| **Bán hàng (1)** | `leads` | Sale ghi nhật ký vào cột `last_contact_note` |
| **Bài tập (5)** | `exercises` | Thư viện ~870 bài |
| | `workout_plans` | **Gánh luôn giáo án mẫu** (`is_template`) |
| | `workout_plan_items` | |
| | `workout_logs` | Hội viên tick hoàn thành |
| | `body_metrics` | Chỉ số cơ thể + BMI |
| **Phản hồi (1)** | `feedbacks` ⭐ | **Gánh `trainer_ratings` + `maintenance_work_orders`** |
| **Hệ thống (1)** | `system_settings` | *Kỹ thuật — không vẽ ERD.* **Gánh 4 bảng cấu hình** (rate card, hoa hồng, hạn mức chiết khấu, nhóm chi phí) |

---

tìm hiểu thêm về scope dự án:
ví dụ:
thuế: tìm hiểu xem thuế hiện tại là thế nào, thì sau khi tính dc doanh thu phòng gym, trừ đi thuế, trừ đi các chi phí khác
+ xác định xem cái con chatbot trong hệ thống của mình nó làm đc những gì?
+ luồng nghiệp vụ của body_metrics: cho user nhập thông tin/ chỉ số của họ vào
nghĩ thêm: liệu mình có thể khai thác gì thêm cho tính năng này ko?
Ví dụ: train con RAG chatbot để nó trả lời được với chỉ số cơ thể thế này thì người dùng nên có chế độ luyện tập thế nào, ăn uống thế nào chẳng hạn
+ 

## Cái gì mất đi — và có chấp nhận được không

| Mất | Ảnh hưởng | Đánh giá |
|---|---|:---:|
| Quản lý chiến dịch khuyến mãi | Vẫn ghi được chiết khấu + lý do + người duyệt trên hợp đồng | ✅ Chấp nhận |
| Lịch trống của PT | Hội viên **đề xuất giờ**, PT duyệt/từ chối (thay vì chọn slot có sẵn) | ✅ Chấp nhận |
| 1 khoản thu trả nhiều hóa đơn | Mua 2 gói → tạo 2 hóa đơn, 2 lần thu | ✅ Chấp nhận |
| Trả góp, chuyển nhượng gói | Cắt khỏi phạm vi | ✅ Vốn là *Should have* |
| Thông báo push | Cắt | ✅ Thầy đã nói *"độ ưu tiên thấp"* |
| **Điểm rủi ro bỏ tập (churn)** | **Mất đóng góp ĐG4** | ⚠️ **Cân nhắc** |
| Khóa sổ kỳ kế toán | Cắt | ✅ *Should have* |

> **Về ĐG4 (giữ chân hội viên):** nếu muốn giữ đóng góp này, thêm lại **2 bảng** (`churn_scores`, `retention_tasks`) → **33 bảng**. Cột `members.last_visit_at` đã có sẵn nên phần tính điểm không tốn thêm gì. Đây là quyết định của bạn: 5 hay 6 đóng góp.

---

## Vẽ ERD thế nào

29 bảng nghiệp vụ vẫn hơi dày cho một trang. Cách trình bày trong báo cáo:

1. **Sơ đồ tổng quan mức nhóm** (12 khối, mũi tên giữa các khối) — đặt đầu chương 4
2. **ERD đầy đủ 29 bảng** — 1 trang A3 gấp, hoặc phụ lục
3. **3 ERD chi tiết theo ngữ cảnh** — dùng khi thuyết minh từng module:
   - **Vận hành:** `persons` `users` `members` `employees` `registrations` `check_ins` `pt_sessions` `session_credit_ledger` `member_trainers` `registration_freezes` (10 bảng)
   - **Tài chính:** `registrations` `invoices` `payments` `cash_shifts` `revenue_schedules` `revenue_recognition_entries` `expenses` `payroll_runs` `payroll_items` (9 bảng)
   - **Dịch vụ:** `memberships` `exercises` `workout_plans` `workout_plan_items` `workout_logs` `body_metrics` `class_sessions` `class_bookings` `equipment` `feedbacks` `leads` (11 bảng)

---

## Việc cần làm nếu chốt phương án này

| # | Việc | Ước lượng |
|---|---|---|
| 1 | Viết lại `schema.sql` còn 31 bảng | 1 buổi |
| 2 | Cập nhật `00-TONG-QUAN-BANG.sql` | 1 giờ |
| 3 | Cập nhật từ điển dữ liệu bên dưới | 2 giờ |
| 4 | Sửa `docs/03-CO-SO-DU-LIEU.md` (sơ đồ nhóm, migration) | 1 giờ |
| 5 | Vẽ lại ERD (dbdiagram.io) | 2 giờ |

**Xác nhận giúp tôi 2 điều rồi tôi làm:**
1. Đồng ý phương án **31 bảng** — hay muốn cắt sâu hơn nữa?
2. Có giữ **churn** (ĐG4) không → 31 hay 33 bảng?

---
---

# TỪ ĐIỂN DỮ LIỆU — Bản 73 bảng *(bản cũ, giữ để đối chiếu)*

> ⚠️ **TOÀN BỘ PHẦN DƯỚI ĐÂY LÀ BẢN CŨ 73 BẢNG.**
> **Bản chính thức 32 bảng nằm ở file riêng: [`31_bang.md`](./31_bang.md)** — dùng file đó khi viết code.
>
> Mỗi bảng dưới đây đã được **đánh dấu số phận** ngay dưới tiêu đề:
>
> | Ký hiệu | Nghĩa |
> |---|---|
> | **✅ GIỮ** | Có mặt trong bản 32 bảng (có thể được bổ sung thêm cột) |
> | **❌ BỎ →** | Không còn trong bản 32 bảng; mũi tên chỉ nội dung đó chuyển đi đâu |
> | **⏳ CHƯA QUYẾT** | Đang chờ quyết định về tính năng cảnh báo bỏ tập (churn) |
>
> **Thống kê trên 73 bảng cũ:** 30 ✅ giữ · 41 ❌ bỏ · 2 ⏳ chưa quyết
>
> **Cách ra con số 32 bảng:**
> ```
>   30  bảng cũ được giữ lại
> +  1  employees            ← bảng MỚI, thay cho trainers + staff + trainer_specialties
> +  1  password_reset_tokens ← bảng MỚI, cho luồng quên mật khẩu qua email
> = 32  bảng
> ```

> Tài liệu **tra cứu** (không đọc một mạch). Dùng `Ctrl+F` tìm tên bảng hoặc tên cột.
> Ba file bổ trợ nhau:
> | File | Dùng khi nào |
> |---|---|
> | `00-TONG-QUAN-BANG.sql` | Muốn hiểu **vì sao** cần bảng đó — đọc 5 phút |
> | `01-TU-DIEN-DU-LIEU.md` | ← **Bạn đang ở đây.** Cột này nhận giá trị nào, ví dụ ra sao |
> | `schema.sql` | Viết code, chạy migration — DDL đầy đủ |

**Phạm vi:** hệ thống quản lý **một phòng gym**. Không có bảng `branches`, không có cột `branch_id`.

---

## Quy ước chung (áp dụng cho MỌI bảng — không lặp lại ở dưới)

| Cột | Kiểu | Ý nghĩa | Mẫu |
|---|---|---|---|
| `id` | `BIGSERIAL` | Khóa chính, tự tăng | `1`, `2`, `123` |
| `created_at` | `TIMESTAMPTZ` | Thời điểm tạo, tự động | `2026-07-15 09:23:41+07` |
| `updated_at` | `TIMESTAMPTZ` | Thời điểm sửa cuối, trigger tự cập nhật | `2026-08-01 14:05:00+07` |
| `deleted_at` | `TIMESTAMPTZ` | Xóa mềm. `NULL` = còn dùng | `NULL` hoặc `2026-09-01 10:00:00+07` |
| `*_by`, `*_id` | `BIGINT` | Khóa ngoại. `NULL` = chưa có/không áp dụng | `7` (trỏ tới `users.id = 7`) |

**Ba quy tắc dữ liệu bắt buộc:**
1. **Tiền luôn `NUMERIC(14,2)`**, đơn vị **VND**, không dùng `float`/`double`. `4200000.00` = 4,2 triệu đồng.
2. **Enum viết HOA, gạch dưới**, lưu bằng `VARCHAR` + ràng buộc `CHECK` (dễ thêm giá trị mới hơn `ENUM` gốc của PostgreSQL).
3. **Thời điểm luôn có timezone** (`TIMESTAMPTZ`), giờ Việt Nam `+07`.

**Nhân vật dùng xuyên suốt các ví dụ:**

| Người | Vai trò | Mã |
|---|---|---|
| Nguyễn Văn An | Hội viên | `MB-000123` |
| Trần Bình | Huấn luyện viên (PT) | `TR-005` |
| Phạm Dũng | Nhân viên sale | `ST-002` |
| Vũ Thị Mai | Lễ tân | `ST-004` |
| Lê Thị Hoa | Kế toán | `ST-007` |

---

## Mục lục

| Nhóm | Bảng |
|---|---|
| [1. Danh tính](#nhóm-1--danh-tính--con-người) | persons · users · user_roles · refresh_tokens · members · trainers · trainer_specialties · staff · audit_logs |
| [2. Gói tập & Hợp đồng](#nhóm-2--gói-tập--hợp-đồng) | memberships · membership_prices · access_scopes · promotions · promotion_memberships · discount_policies · registrations · registration_freezes · registration_transfers · member_trainers |
| [3. Check-in](#nhóm-3--check-in) | access_cards · qr_secrets · check_ins · check_in_incidents |
| [4. Buổi tập PT & Sổ cái](#nhóm-4--buổi-tập-pt--sổ-cái) | trainer_availability · pt_bookings · pt_sessions · session_credit_ledger |
| [5. Lớp học & Phòng](#nhóm-5--lớp-học--phòng-tập) | rooms · class_definitions · class_schedules · class_sessions · class_bookings |
| [6. Thiết bị](#nhóm-6--thiết-bị--bảo-trì) | equipment_types · equipment_items · maintenance_work_orders |
| [7. Thanh toán](#nhóm-7--thanh-toán) | invoices · invoice_items · cash_shifts · payments · payment_allocations · payment_schedules · refunds · webhook_events |
| [8. Tài chính & Lương](#nhóm-8--tài-chính--lương) | revenue_schedules · revenue_recognition_entries · expense_categories · expenses · payroll_rate_cards · commission_rules · payroll_runs · payroll_items · accounting_periods · financial_reports |
| [9. Bán hàng](#nhóm-9--bán-hàng-crm) | leads · lead_activities · quotes · quote_items |
| [10. Bài tập & Cơ thể](#nhóm-10--bài-tập--chỉ-số-cơ-thể) | exercises · workout_templates · workout_template_items · workout_plans · workout_plan_items · workout_logs · body_metrics |
| [11. Phản hồi & Giữ chân](#nhóm-11--phản-hồi--giữ-chân) | feedbacks · trainer_ratings · notification_templates · notifications · churn_scores · retention_tasks |
| [12. Hệ thống](#nhóm-12--hệ-thống) | system_settings · outbox_events · llm_usage_logs |

---
---

# NHÓM 1 — DANH TÍNH & CON NGƯỜI

## `persons` — con người thật, 1 dòng = 1 người  
> **[✅ GIỮ]**

| Cột | Kiểu | Giá trị nhận / Ý nghĩa | Mẫu |
|---|---|---|---|
| `full_name` | `VARCHAR(150)` | Họ tên đầy đủ. Bắt buộc | `Nguyễn Văn An` |
| `gender` | `VARCHAR(10)` | `MALE` \| `FEMALE` \| `OTHER` \| `NULL` | `MALE` |
| `birthday` | `DATE` | Ngày sinh. Phải < hôm nay | `1998-03-22` |
| `national_id` | `VARCHAR(20)` | Số CCCD. **Duy nhất**, mã hóa ở tầng ứng dụng | `001098012345` |
| `phone` | `VARCHAR(20)` | SĐT. **Duy nhất** — định danh thực tế phổ biến nhất ở VN | `0912345678` |
| `email` | `VARCHAR(150)` | Email. **Duy nhất**, có thể `NULL` | `an.nguyen@gmail.com` |
| `address` | `VARCHAR(255)` | Địa chỉ | `56 Trần Duy Hưng, Cầu Giấy, Hà Nội` |
| `photo_url` | `VARCHAR(500)` | **Link ảnh chân dung** — lễ tân đối chiếu khi check-in | `https://s3.../persons/12/photo.jpg` |
| `emergency_contact_name` | `VARCHAR(150)` | Người liên hệ khẩn cấp | `Nguyễn Thị Lan` |
| `emergency_contact_phone` | `VARCHAR(20)` | SĐT liên hệ khẩn cấp | `0987654321` |

> **`photo_url` hoạt động thế nào:** (1) lúc đăng ký, lễ tân chụp 1 ảnh chân dung, upload lên MinIO/S3, lưu link vào cột này → (2) khi hội viên quẹt thẻ/quét QR, màn hình quầy bật ảnh này cỡ lớn kèm tên + trạng thái gói → (3) **lễ tân nhìn bằng mắt**, đối chiếu ảnh với người đứng trước mặt, bấm "Cho vào" hoặc "Từ chối". Đây là xác minh **thủ công có con người quyết định**. Nhận diện khuôn mặt tự động là tính năng nâng cao tùy chọn, không bắt buộc.

## `users` — tài khoản đăng nhập. 1 person → nhiều users  
> **[✅ GIỮ]**

| Cột | Kiểu | Giá trị nhận / Ý nghĩa | Mẫu |
|---|---|---|---|
| `person_id` | `BIGINT` | Trỏ tới `persons.id`. **Bắt buộc** | `12` |
| `username` | `VARCHAR(100)` | Tên đăng nhập. **Duy nhất** | `an.nguyen` |
| `password_hash` | `VARCHAR(255)` | BCrypt cost 12. **Không bao giờ lưu mật khẩu thô** | `$2a$12$N9qo8uLOickgx2ZM...` |
| `primary_role` | `VARCHAR(20)` | **Vai trò chính** — xem bảng giá trị bên dưới | `MEMBER` |
| `status` | `VARCHAR(20)` | **Tình trạng tài khoản** — xem bảng bên dưới | `ACTIVE` |
| `last_login_at` | `TIMESTAMPTZ` | Lần đăng nhập gần nhất | `2026-08-01 18:02:11+07` |
| `failed_attempts` | `SMALLINT` | Số lần nhập sai mật khẩu liên tiếp. Về `0` khi đăng nhập đúng | `0` |
| `locked_until` | `TIMESTAMPTZ` | Khóa tạm tới thời điểm này. `NULL` = không khóa | `NULL` |
| `must_change_password` | `BOOLEAN` | `TRUE` = bắt đổi mật khẩu lần đăng nhập tới | `FALSE` |

### `primary_role` — 6 giá trị

| Giá trị | Ai | Thấy giao diện gì |
|---|---|---|
| `ADMIN` | Chủ phòng gym / quản trị viên | Dashboard tổng, quản lý gói tập, nhân sự, cấu hình |
| `MEMBER` | Hội viên | App: gói của tôi, QR check-in, đặt lịch PT, giáo án |
| `TRAINER` | Huấn luyện viên (PT) | App: lịch dạy, xác nhận buổi tập, bảng lương của tôi |
| `SALE` | Nhân viên kinh doanh | Web: lead, pipeline, báo giá, KPI hoa hồng |
| `RECEPTIONIST` | Lễ tân | Web: màn hình quầy, thu tiền, ca làm việc |
| `ACCOUNTANT` | Kế toán | Web: doanh thu, chi phí, chạy lương, báo cáo |

**Ví dụ 1 người có nhiều tài khoản** — anh Nguyễn Văn An (`persons.id = 12`) vừa là PT vừa bán gói tập:

| id | person_id | username | primary_role | status |
|---|---|---|---|---|
| 30 | 12 | `an.pt` | `TRAINER` | `ACTIVE` |
| 31 | 12 | `an.sale` | `SALE` | `ACTIVE` |

→ Hai bảng lương/KPI riêng, nhưng cùng một con người, cùng một khuôn mặt để nhận diện, cùng một số CCCD.

### `status` — 3 giá trị

| Giá trị | Nghĩa | Đăng nhập được? | Ai đặt |
|---|---|:---:|---|
| `ACTIVE` | Bình thường | ✅ | Mặc định khi tạo |
| `SUSPENDED` | Tạm khóa — nghỉ phép dài, nghi ngờ bảo mật. **Mở lại được** | ❌ | Admin chủ động |
| `DISABLED` | Vô hiệu vĩnh viễn — đã nghỉ việc | ❌ | Admin chủ động |

> **Vì sao không xóa tài khoản mà chuyển `DISABLED`:** các bản ghi cũ (`approved_by`, `received_by`, `created_by`…) vẫn phải trỏ tới tài khoản này để truy vết được "ai đã duyệt phiếu này". Xóa đi thì mất dấu vết kiểm toán.

> **Phân biệt `status` với `locked_until`:** `status` do **Admin chủ động** đặt. `locked_until` do **hệ thống tự** đặt khi nhập sai mật khẩu quá 5 lần, tự hết hiệu lực sau 15 phút — Admin không phải làm gì.

## `user_roles` — vai trò kiêm nhiệm *(tùy chọn, nên bỏ ở giai đoạn đầu)*  
> **[❌ BỎ → 1 tài khoản chỉ 1 vai trò]**

| Cột | Kiểu | Giá trị nhận / Ý nghĩa | Mẫu |
|---|---|---|---|
| `user_id` | `BIGINT` | Trỏ tới `users.id` | `7` |
| `role` | `VARCHAR(20)` | 6 giá trị y hệt `primary_role` | `RECEPTIONIST` |
| `granted_by` | `BIGINT` | Ai cấp quyền này | `1` (tài khoản Admin) |
| `granted_at` | `TIMESTAMPTZ` | Cấp lúc nào | `2026-07-20 08:00:00+07` |

**Bảng này giải quyết việc gì:** phòng gym nhỏ, chị Lê Thị Hoa làm kế toán nhưng kiêm luôn lễ tân ca sáng. Thay vì tạo 2 tài khoản, cấp thêm 1 vai trò cho tài khoản hiện có:

```
users:       (id=7, username='hoa.kt', primary_role='ACCOUNTANT', status='ACTIVE')
user_roles:  (user_id=7, role='RECEPTIONIST', granted_by=1)
```

→ Chị Hoa đăng nhập **một lần**, thấy **cả menu Kế toán lẫn menu Lễ tân**.

**Quy tắc phân quyền:** `quyền của tài khoản = quyền của primary_role ∪ quyền của mọi dòng trong user_roles`.

> **Khuyến nghị:** giai đoạn đầu **bỏ bảng này**, chỉ dùng `primary_role`. Ai kiêm nhiệm thì cấp 2 tài khoản riêng — đúng như thầy đã chốt, đơn giản hơn và dễ giải thích khi bảo vệ hơn.

## `refresh_tokens` — phiên đăng nhập dài hạn  
> **[❌ BỎ → chuyển sang Redis (⚠️ cần xác nhận lại)]**

| Cột | Kiểu | Giá trị nhận / Ý nghĩa | Mẫu |
|---|---|---|---|
| `user_id` | `BIGINT` | Của tài khoản nào | `30` |
| `token_hash` | `VARCHAR(128)` | **SHA-256 của token**. Không bao giờ lưu token thô | `a3f5b2c8...` (64 ký tự hex) |
| `issued_at` | `TIMESTAMPTZ` | Cấp lúc nào | `2026-08-01 18:02:11+07` |
| `expires_at` | `TIMESTAMPTZ` | Hết hạn (thường +30 ngày) | `2026-08-31 18:02:11+07` |
| `revoked_at` | `TIMESTAMPTZ` | Bị thu hồi lúc nào. `NULL` = còn hiệu lực | `NULL` |
| `replaced_by` | `BIGINT` | Token mới thay thế token này (khi refresh) | `892` |
| `device_info` | `VARCHAR(255)` | Thiết bị nào | `iPhone 14 / iOS 18.2 / GymApp 1.0` |
| `ip_address` | `INET` | IP lúc cấp | `113.161.45.22` |

> **`replaced_by` dùng để làm gì:** mỗi lần refresh, hệ thống cấp token mới và ghi `replaced_by` vào token cũ. Nếu ai đó dùng lại **token cũ đã bị thay thế** → chắc chắn token đã bị đánh cắp → **thu hồi toàn bộ phiên của người dùng đó** và cảnh báo. Đây gọi là *refresh token rotation + reuse detection*.

## `members` — hồ sơ hội viên  
> **[✅ GIỮ]**

| Cột | Kiểu | Giá trị nhận / Ý nghĩa | Mẫu |
|---|---|---|---|
| `person_id` | `BIGINT` | Trỏ `persons.id`. **Duy nhất** (1 người ≤ 1 hồ sơ hội viên) | `12` |
| `member_code` | `VARCHAR(20)` | Mã hội viên in trên thẻ. **Duy nhất** | `MB-000123` |
| `join_date` | `DATE` | Ngày trở thành hội viên | `2026-01-15` |
| `source` | `VARCHAR(30)` | `WALK_IN` \| `FB_ADS` \| `REFERRAL` \| `HOTLINE` \| `GOOGLE` \| `EVENT` | `FB_ADS` |
| `referred_by` | `BIGINT` | Hội viên nào giới thiệu (`members.id`) | `45` |
| `health_note` | `TEXT` | Bệnh nền, chấn thương — PT cần biết | `Đau lưng dưới, tránh deadlift nặng` |
| `goal` | `VARCHAR(30)` | `LOSE_FAT` \| `GAIN_MUSCLE` \| `ENDURANCE` \| `HEALTH` | `LOSE_FAT` |
| `status` | `VARCHAR(20)` | `ACTIVE` \| `INACTIVE` (hết gói lâu) \| `BLACKLISTED` (vi phạm nội quy) | `ACTIVE` |
| `last_visit_at` | `TIMESTAMPTZ` | Lần check-in gần nhất. Cập nhật mỗi lần vào tập | `2026-08-01 18:05:00+07` |

> **`last_visit_at` là dữ liệu trùng lặp có chủ đích** (denormalized): tính được từ `check_ins` nhưng lưu sẵn ở đây để engine cảnh báo bỏ tập không phải quét cả bảng `check_ins` mỗi đêm.

## `trainers` — hồ sơ huấn luyện viên  
> **[❌ BỎ → gộp vào bảng mới `employees`]**

| Cột | Kiểu | Giá trị nhận / Ý nghĩa | Mẫu |
|---|---|---|---|
| `person_id` | `BIGINT` | Trỏ `persons.id`. **Duy nhất** | `18` |
| `trainer_code` | `VARCHAR(20)` | Mã PT. **Duy nhất** | `TR-005` |
| `employment_type` | `VARCHAR(20)` | `FULL_TIME` \| `PART_TIME` \| `FREELANCE` | `FULL_TIME` |
| `level` | `VARCHAR(20)` | `JUNIOR` \| `SENIOR` \| `MASTER` — ảnh hưởng đơn giá buổi tập | `SENIOR` |
| `bio` | `TEXT` | Giới thiệu, hội viên đọc khi chọn PT | `8 năm kinh nghiệm, chứng chỉ NASM-CPT...` |
| `base_salary` | `NUMERIC(14,2)` | Lương cứng/tháng (VND) | `8000000.00` |
| `start_date` | `DATE` | Ngày vào làm | `2024-03-01` |
| `finish_contract_date` | `DATE` | Ngày hết hợp đồng. `NULL` = vô thời hạn | `2027-03-01` |
| `max_members` | `INTEGER` | Nhận tối đa bao nhiêu học viên | `30` |
| `rating_avg` | `NUMERIC(3,2)` | Điểm đánh giá TB (1.00–5.00). Tính từ `trainer_ratings` | `4.60` |
| `rating_count` | `INTEGER` | Số lượt đánh giá | `87` |
| `status` | `VARCHAR(20)` | `ACTIVE` \| `ON_LEAVE` \| `RESIGNED` | `ACTIVE` |

## `trainer_specialties` — chuyên môn của PT (1 PT nhiều chuyên môn)  
> **[❌ BỎ → cột `employees.specialties JSONB`]**

| Cột | Kiểu | Giá trị nhận | Mẫu |
|---|---|---|---|
| `trainer_id` | `BIGINT` | Trỏ `trainers.id` | `5` |
| `specialty` | `VARCHAR(40)` | `FITNESS` \| `YOGA` \| `CROSSFIT` \| `BOXING` \| `REHAB` \| `NUTRITION` | `FITNESS` |

Ví dụ PT Trần Bình: 2 dòng `(5, 'FITNESS')` và `(5, 'REHAB')`.

## `staff` — nhân sự không phải PT (sale, lễ tân, kế toán)  
> **[❌ BỎ → gộp vào bảng mới `employees`]**

| Cột | Kiểu | Giá trị nhận / Ý nghĩa | Mẫu |
|---|---|---|---|
| `person_id` | `BIGINT` | Trỏ `persons.id` | `22` |
| `staff_code` | `VARCHAR(20)` | Mã nhân viên. **Duy nhất** | `ST-002` |
| `department` | `VARCHAR(30)` | `SALES` \| `FRONT_DESK` \| `ACCOUNTING` \| `MANAGEMENT` \| `MAINTENANCE` | `SALES` |
| `position` | `VARCHAR(80)` | Chức danh cụ thể | `Nhân viên tư vấn` |
| `base_salary` | `NUMERIC(14,2)` | Lương cứng/tháng | `7000000.00` |
| `start_date` | `DATE` | Ngày vào làm | `2025-06-01` |
| `end_date` | `DATE` | Ngày nghỉ. `NULL` = còn làm | `NULL` |
| `status` | `VARCHAR(20)` | `ACTIVE` \| `ON_LEAVE` \| `RESIGNED` | `ACTIVE` |

## `audit_logs` — nhật ký thao tác  
> **[✅ GIỮ]**

| Cột | Kiểu | Giá trị nhận / Ý nghĩa | Mẫu |
|---|---|---|---|
| `actor_id` | `BIGINT` | Ai thao tác (`users.id`) | `7` |
| `action` | `VARCHAR(50)` | `CREATE` \| `UPDATE` \| `DELETE` \| `APPROVE` \| `REJECT` \| `LOGIN` \| `LOGOUT` \| `EXPORT` | `UPDATE` |
| `entity_type` | `VARCHAR(60)` | Tên bảng bị tác động | `registrations` |
| `entity_id` | `BIGINT` | ID bản ghi bị tác động | `4521` |
| `before_data` | `JSONB` | Giá trị **trước** khi sửa | `{"status":"ACTIVE","end_date":"2026-12-31"}` |
| `after_data` | `JSONB` | Giá trị **sau** khi sửa | `{"status":"FROZEN","end_date":"2027-01-30"}` |
| `reason` | `TEXT` | Lý do (bắt buộc với thao tác nhạy cảm) | `Hội viên xin bảo lưu 30 ngày vì công tác` |
| `ip_address` | `INET` | IP người thao tác | `192.168.1.45` |
| `user_agent` | `VARCHAR(500)` | Trình duyệt / app | `Mozilla/5.0 ... Chrome/128.0` |

---
---

# NHÓM 2 — GÓI TẬP & HỢP ĐỒNG

## `memberships` — danh mục gói tập  
> **[✅ GIỮ (thêm cột `price`, `area_codes`)]**

| Cột | Kiểu | Giá trị nhận / Ý nghĩa | Mẫu |
|---|---|---|---|
| `code` | `VARCHAR(30)` | Mã gói. **Duy nhất** | `FIT-12M` |
| `name` | `VARCHAR(150)` | Tên hiển thị | `Gói Fitness 12 tháng` |
| `package_type` | `VARCHAR(20)` | **4 giá trị** — xem bảng dưới | `TIME_BASED` |
| `duration_days` | `INTEGER` | Thời hạn tính bằng **ngày** | `365` |
| `session_count` | `INTEGER` | Số **buổi PT** trong gói | `NULL` |
| `includes_trainer` | `BOOLEAN` | Gói có kèm PT không | `FALSE` |
| `pt_value_ratio` | `NUMERIC(4,3)` | Chỉ dùng cho `HYBRID`: % giá trị thuộc phần PT | `0.650` |
| `max_freeze_days` | `INTEGER` | Bảo lưu tối đa bao nhiêu ngày/năm. `0` = không cho bảo lưu | `30` |
| `max_freeze_times` | `SMALLINT` | Bảo lưu tối đa mấy lần | `2` |
| `is_transferable` | `BOOLEAN` | Có được chuyển nhượng không | `TRUE` |
| `is_refundable` | `BOOLEAN` | Có được hoàn tiền không | `FALSE` |
| `description` | `TEXT` | Mô tả chi tiết | `Tập không giới hạn mọi khung giờ...` |
| `display_order` | `INTEGER` | Thứ tự hiển thị trên app | `3` |
| `status` | `VARCHAR(20)` | `ACTIVE` (đang bán) \| `ARCHIVED` (ngừng bán, hợp đồng cũ vẫn chạy) | `ACTIVE` |

### `package_type` — 4 giá trị

| Giá trị | Nghĩa | `duration_days` | `session_count` |
|---|---|---|---|
| `TIME_BASED` | Tập không giới hạn trong X ngày | Bắt buộc | `NULL` |
| `SESSION_BASED` | Mua N buổi PT, có hạn dùng | Hạn dùng | Bắt buộc |
| `HYBRID` | Combo: vừa thời hạn vừa số buổi | Bắt buộc | Bắt buộc |
| `DAY_PASS` | Vé lẻ 1 ngày | `= 1` | `NULL` |

### Dữ liệu mẫu đầy đủ

| code | name | package_type | duration_days | session_count | includes_trainer | max_freeze_days | is_refundable |
|---|---|---|---:|---:|:---:|---:|:---:|
| `DAY-01` | Vé tập 1 ngày | `DAY_PASS` | 1 | – | `FALSE` | 0 | `FALSE` |
| `FIT-01M` | Gói Fitness 1 tháng | `TIME_BASED` | 30 | – | `FALSE` | 0 | `FALSE` |
| `FIT-03M` | Gói Fitness 3 tháng | `TIME_BASED` | 90 | – | `FALSE` | 14 | `FALSE` |
| `FIT-06M` | Gói Fitness 6 tháng | `TIME_BASED` | 180 | – | `FALSE` | 30 | `TRUE` |
| `FIT-12M` | Gói Fitness 12 tháng | `TIME_BASED` | 365 | – | `FALSE` | 30 | `TRUE` |
| `PT-12` | Gói PT 12 buổi | `SESSION_BASED` | 180 | 12 | `TRUE` | 0 | `TRUE` |
| `PT-24` | Gói PT 24 buổi | `SESSION_BASED` | 270 | 24 | `TRUE` | 0 | `TRUE` |
| `COMBO-12M-PT24` | Combo 12 tháng + PT 24 buổi | `HYBRID` | 365 | 24 | `TRUE` | 30 | `TRUE` |

> **Vì sao tách `training_time` cũ thành 3 cột:** schema cũ lưu `'1 tháng'`, `'12 buổi'` dạng chữ → không lọc được *"các gói ≥ 90 ngày"* (điều kiện bảo lưu), không tính được `end_date = start_date + duration_days`, không so sánh được gói nào dài hơn.

## `membership_prices` — phiên bản giá theo thời gian  
> **[❌ BỎ → `memberships.price` + snapshot sẵn trong `registrations`]**

| Cột | Kiểu | Giá trị nhận / Ý nghĩa | Mẫu |
|---|---|---|---|
| `membership_id` | `BIGINT` | Của gói nào | `5` |
| `price` | `NUMERIC(14,2)` | Giá niêm yết (VND) | `4800000.00` |
| `valid_from` | `DATE` | Có hiệu lực từ ngày | `2026-01-01` |
| `valid_to` | `DATE` | Đến ngày. `NULL` = **đang áp dụng** | `NULL` |
| `created_by` | `BIGINT` | Ai đặt giá này | `1` |
| `note` | `VARCHAR(255)` | Lý do đổi giá | `Tăng giá theo lạm phát 2026` |

**Ví dụ tăng giá gói `FIT-12M` từ 4,8 triệu lên 5,2 triệu vào 01/09/2026:**

| id | membership_id | price | valid_from | valid_to | note |
|---|---|---:|---|---|---|
| 12 | 5 | `4800000.00` | 2026-01-01 | **2026-08-31** | Giá 2026 |
| 18 | 5 | `5200000.00` | **2026-09-01** | `NULL` | Tăng giá theo lạm phát 2026 |

> **Vì sao không để giá trong `memberships`:** nếu chỉ có 1 cột `price` và Admin sửa nó, thì **giá trị của mọi hợp đồng đã ký trước đó cũng đổi theo** → toàn bộ báo cáo doanh thu lịch sử sai. Hợp đồng ký tháng 3 phải mãi mãi là 4,8 triệu.

## `access_scopes` — gói được vào khu vực nào  
> **[❌ BỎ → cột `memberships.area_codes JSONB`]**

| Cột | Kiểu | Giá trị nhận | Mẫu |
|---|---|---|---|
| `membership_id` | `BIGINT` | Của gói nào | `5` |
| `area_code` | `VARCHAR(30)` | `GYM_FLOOR` \| `YOGA_STUDIO` \| `POOL` \| `SAUNA` \| `GROUP_CLASS` | `GYM_FLOOR` |

Gói `FIT-12M` có 3 dòng: `GYM_FLOOR`, `GROUP_CLASS`, `SAUNA`. Gói `FIT-01M` chỉ có `GYM_FLOOR`.

## `promotions` — chương trình khuyến mãi  
> **[❌ BỎ → ghi thẳng vào `registrations.discount_*`]**

| Cột | Kiểu | Giá trị nhận / Ý nghĩa | Mẫu |
|---|---|---|---|
| `code` | `VARCHAR(30)` | Mã KM. **Duy nhất** | `SUMMER2026` |
| `name` | `VARCHAR(150)` | Tên chương trình | `Khuyến mãi hè 2026 — giảm 15%` |
| `discount_type` | `VARCHAR(20)` | `PERCENT` \| `FIXED_AMOUNT` \| `BONUS_DAYS` | `PERCENT` |
| `discount_value` | `NUMERIC(14,2)` | 15 (=15%) hoặc 500000 (=500k) hoặc 30 (=30 ngày) | `15.00` |
| `applies_to` | `VARCHAR(20)` | `ALL` (mọi gói) \| `SPECIFIC` (theo `promotion_memberships`) | `SPECIFIC` |
| `min_contract_value` | `NUMERIC(14,2)` | Áp dụng khi hợp đồng ≥ số này | `3000000.00` |
| `valid_from` / `valid_to` | `DATE` | Khoảng thời gian áp dụng | `2026-06-01` / `2026-08-31` |
| `usage_limit` | `INTEGER` | Tối đa bao nhiêu lượt. `NULL` = không giới hạn | `200` |
| `usage_count` | `INTEGER` | Đã dùng bao nhiêu lượt | `47` |
| `status` | `VARCHAR(20)` | `ACTIVE` \| `PAUSED` \| `EXPIRED` | `ACTIVE` |

## `promotion_memberships` — KM áp cho gói nào  
> **[❌ BỎ → theo `promotions`]**

| Cột | Kiểu | Mẫu |
|---|---|---|
| `promotion_id` | `BIGINT` | `3` |
| `membership_id` | `BIGINT` | `5` |

## `discount_policies` — hạn mức chiết khấu theo vai trò  
> **[❌ BỎ → `system_settings`]**

| Cột | Kiểu | Giá trị nhận / Ý nghĩa | Mẫu |
|---|---|---|---|
| `role` | `VARCHAR(20)` | Vai trò nào. **Duy nhất** | `SALE` |
| `max_discount_percent` | `NUMERIC(5,2)` | Giảm tối đa bao nhiêu % | `20.00` |
| `requires_approval_above` | `NUMERIC(5,2)` | Trên mức này phải xin duyệt | `10.00` |

**Dữ liệu mẫu:**

| role | max_discount_percent | requires_approval_above | Nghĩa là |
|---|---:|---:|---|
| `SALE` | 20.00 | 10.00 | Sale tự quyết ≤10%; 10–20% phải xin Admin duyệt; >20% không được phép |
| `RECEPTIONIST` | 5.00 | 0.00 | Lễ tân giảm ≤5% nhưng **mọi mức đều phải xin duyệt** |
| `ADMIN` | 100.00 | 100.00 | Admin tự quyết mọi mức |

## `registrations` — HỢP ĐỒNG ĐĂNG KÝ GÓI ⭐ *(bảng quan trọng nhất)*  
> **[✅ GIỮ (gánh thêm vai trò BÁO GIÁ, status=DRAFT)]**

| Cột | Kiểu | Giá trị nhận / Ý nghĩa | Mẫu |
|---|---|---|---|
| `registration_code` | `VARCHAR(30)` | Mã hợp đồng. **Duy nhất** | `REG-2026-000451` |
| `member_id` | `BIGINT` | Hội viên nào | `123` |
| `membership_id` | `BIGINT` | Mua gói nào | `5` |
| `sold_by` | `BIGINT` | Sale nào bán (`staff.id`) — **cơ sở tính hoa hồng** | `2` |
| `assigned_trainer_id` | `BIGINT` | PT chính phụ trách. `NULL` nếu gói không kèm PT | `5` |
| **— Snapshot điều khoản —** | | *Sao chép từ `memberships` lúc ký, không đọc ngược* | |
| `package_type` | `VARCHAR(20)` | Bản sao của gói lúc ký | `SESSION_BASED` |
| `duration_days` | `INTEGER` | Bản sao | `180` |
| `sessions_total` | `INTEGER` | Tổng số buổi được cấp | `12` |
| `list_price` | `NUMERIC(14,2)` | Giá niêm yết lúc ký | `4200000.00` |
| `discount_amount` | `NUMERIC(14,2)` | Số tiền giảm | `420000.00` |
| `discount_reason` | `VARCHAR(255)` | Lý do giảm | `Khuyến mãi hè 2026 (SUMMER2026)` |
| `discount_approved_by` | `BIGINT` | Ai duyệt (nếu vượt hạn mức) | `NULL` |
| `promotion_id` | `BIGINT` | Áp KM nào | `3` |
| `final_price` | `NUMERIC(14,2)` | **Giá cuối** = `list_price − discount_amount` | `3780000.00` |
| **— Thời gian —** | | | |
| `contract_date` | `DATE` | Ngày ký | `2026-07-15` |
| `start_date` | `DATE` | Ngày bắt đầu hiệu lực. `NULL` khi chưa kích hoạt | `2026-07-15` |
| `end_date` | `DATE` | Ngày hết hạn. **Đẩy lùi khi bảo lưu** | `2027-01-11` |
| `activated_at` | `TIMESTAMPTZ` | Thời điểm chuyển sang `ACTIVE` | `2026-07-15 10:30:00+07` |
| `closed_at` | `TIMESTAMPTZ` | Thời điểm kết thúc | `NULL` |
| `status` | `VARCHAR(20)` | **8 trạng thái** — xem bảng dưới | `ACTIVE` |
| `close_reason` | `VARCHAR(255)` | Lý do kết thúc | `NULL` |
| `note` | `TEXT` | Ghi chú | `Khách yêu cầu tập buổi tối` |

### `status` — 8 trạng thái và tác động

| Giá trị | Nghĩa | Check-in được? | Ghi nhận doanh thu? | Chuyển tiếp sang |
|---|---|:---:|:---:|---|
| `DRAFT` | Sale đang soạn, chưa chốt | ❌ | ❌ | `PENDING_PAYMENT`, `CANCELLED` |
| `PENDING_PAYMENT` | Đã chốt, chờ khách trả tiền | ❌ | ❌ | `ACTIVE`, `CANCELLED` |
| `ACTIVE` | **Đang hiệu lực** | ✅ | ✅ | `FROZEN`, `COMPLETED`, `CANCELLED`, `TRANSFERRED` |
| `FROZEN` | Đang bảo lưu | ❌ | ⏸️ **tạm dừng** | `ACTIVE` |
| `COMPLETED` | Hết hạn / hết buổi — **kết thúc bình thường** | ❌ | ✅ đã ghi hết | *(cuối)* |
| `CANCELLED` | **Hủy giữa chừng** theo yêu cầu | ❌ | ❌ dừng | `REFUNDED` |
| `TRANSFERRED` | Đã chuyển nhượng cho người khác | ❌ | ❌ | *(cuối)* |
| `REFUNDED` | Đã hoàn tiền | ❌ | ❌ | *(cuối)* |

> **`CANCELLED` khác `COMPLETED` thế nào (câu hỏi thầy nêu trong mục "Hạn chế"):** `COMPLETED` = khách dùng hết dịch vụ đã mua, phòng gym đã hoàn thành nghĩa vụ, **không phải hoàn tiền**. `CANCELLED` = khách bỏ giữa chừng, phòng gym **còn nợ dịch vụ**, phải tính giá trị còn lại và có thể phải hoàn tiền. Hai sự kiện kế toán hoàn toàn khác nhau — gộp chung là sai.

### Dữ liệu mẫu

```
registration_code : REG-2026-000451
member_id         : 123          (Nguyễn Văn An, MB-000123)
membership_id     : 7            (Gói PT 12 buổi)
sold_by           : 2            (Phạm Dũng, ST-002)
assigned_trainer_id: 5           (Trần Bình, TR-005)
package_type      : SESSION_BASED
duration_days     : 180
sessions_total    : 12
list_price        : 4,200,000
discount_amount   :   420,000    (KM hè 2026, giảm 10% — trong hạn mức Sale)
final_price       : 3,780,000
contract_date     : 2026-07-15
start_date        : 2026-07-15
end_date          : 2027-01-11   (= start_date + 180 ngày)
status            : ACTIVE
```

## `registration_freezes` — bảo lưu gói tập  
> **[✅ GIỮ]**

| Cột | Kiểu | Giá trị nhận / Ý nghĩa | Mẫu |
|---|---|---|---|
| `registration_id` | `BIGINT` | Bảo lưu hợp đồng nào | `451` |
| `from_date` | `DATE` | Bảo lưu từ ngày | `2026-09-01` |
| `to_date` | `DATE` | Đến ngày | `2026-09-30` |
| `days` | `INTEGER` | Số ngày = `to_date − from_date + 1` | `30` |
| `reason` | `VARCHAR(255)` | Lý do cụ thể | `Đi công tác nước ngoài 1 tháng` |
| `reason_type` | `VARCHAR(20)` | `PERSONAL` \| `MEDICAL` \| `TRAVEL` \| `OTHER` | `TRAVEL` |
| `attachment_url` | `VARCHAR(500)` | File đính kèm (giấy y tế) | `NULL` |
| `requested_by` | `BIGINT` | Ai gửi yêu cầu | `30` (chính hội viên) |
| `approved_by` | `BIGINT` | Ai duyệt | `1` |
| `status` | `VARCHAR(20)` | 6 giá trị — xem dưới | `APPROVED` |
| `ended_early_at` | `DATE` | Kết thúc sớm ngày nào | `NULL` |

### `status` của bảo lưu

| Giá trị | Nghĩa |
|---|---|
| `PENDING` | Chờ duyệt (vượt điều kiện tự động) |
| `APPROVED` | Đã duyệt, chưa tới ngày bắt đầu |
| `REJECTED` | Từ chối (kèm lý do) |
| `ACTIVE` | Đang trong kỳ bảo lưu |
| `ENDED` | Đã hết kỳ bảo lưu |
| `CANCELLED` | Hội viên hủy yêu cầu |

**Tác động khi `APPROVED`:** `registrations.status → FROZEN` · `end_date` **cộng thêm 30 ngày** (từ `2027-01-11` thành `2027-02-10`) · dừng ghi nhận doanh thu 30 ngày đó · check-in trong kỳ bị từ chối.

## `registration_transfers` — chuyển nhượng gói *(Should have)*  
> **[❌ BỎ → bỏ tính năng chuyển nhượng]**

| Cột | Kiểu | Ý nghĩa | Mẫu |
|---|---|---|---|
| `registration_id` | `BIGINT` | Hợp đồng gốc | `451` |
| `from_member_id` | `BIGINT` | Người chuyển | `123` |
| `to_member_id` | `BIGINT` | Người nhận | `456` |
| `new_registration_id` | `BIGINT` | Hợp đồng mới sinh ra | `612` |
| `transfer_fee` | `NUMERIC(14,2)` | Phí chuyển nhượng | `200000.00` |
| `remaining_value` | `NUMERIC(14,2)` | Giá trị còn lại được chuyển | `2100000.00` |
| `approved_by` | `BIGINT` | Ai duyệt | `1` |

## `member_trainers` — 2–3 PT cùng chăm 1 hội viên  
> **[✅ GIỮ]**

| Cột | Kiểu | Giá trị nhận / Ý nghĩa | Mẫu |
|---|---|---|---|
| `member_id` | `BIGINT` | Hội viên nào | `123` |
| `trainer_id` | `BIGINT` | PT nào | `5` |
| `role` | `VARCHAR(20)` | `PRIMARY` \| `SECONDARY` \| `SUBSTITUTE` | `PRIMARY` |
| `from_date` | `DATE` | Phụ trách từ ngày | `2026-07-15` |
| `to_date` | `DATE` | Đến ngày. `NULL` = **đang phụ trách** | `NULL` |
| `assigned_by` | `BIGINT` | Ai phân công | `1` |
| `note` | `VARCHAR(255)` | Ghi chú | `PT chính, phụ trách giáo án` |

**Ví dụ hội viên An có 3 PT:**

| member_id | trainer_id | role | from_date | to_date | note |
|---|---|---|---|---|---|
| 123 | 5 | `PRIMARY` | 2026-07-15 | `NULL` | Trần Bình — PT chính, soạn giáo án |
| 123 | 9 | `SECONDARY` | 2026-07-15 | `NULL` | Lê Cường — chuyên phục hồi chấn thương lưng |
| 123 | 11 | `SUBSTITUTE` | 2026-08-01 | `NULL` | Hoàng Đạt — dạy thay khi PT chính nghỉ |

> **Ràng buộc:** mỗi hội viên chỉ có **đúng 1** PT `PRIMARY` tại một thời điểm (unique index có điều kiện).
> **Quan trọng:** tiền công buổi tập tính cho **PT thực tế dạy buổi đó** (`pt_sessions.trainer_id`), không phải PT `PRIMARY`.

---
---

# NHÓM 3 — CHECK-IN

## `access_cards` — thẻ từ RFID/NFC  
> **[❌ BỎ → cột `persons.card_uid`]**

| Cột | Kiểu | Giá trị nhận / Ý nghĩa | Mẫu |
|---|---|---|---|
| `person_id` | `BIGINT` | Thẻ của ai | `12` |
| `card_uid` | `VARCHAR(64)` | UID vật lý của thẻ. **Duy nhất** | `04A3B2C1D5E680` |
| `card_type` | `VARCHAR(20)` | `RFID` \| `NFC` \| `QR_STATIC` | `RFID` |
| `issued_at` | `TIMESTAMPTZ` | Cấp lúc nào | `2026-07-15 10:35:00+07` |
| `revoked_at` | `TIMESTAMPTZ` | Thu hồi lúc nào. `NULL` = còn dùng | `NULL` |
| `revoke_reason` | `VARCHAR(255)` | Lý do thu hồi | `NULL` |

Khi khách báo mất thẻ: đặt `revoked_at` cho thẻ cũ, cấp thẻ mới với `card_uid` khác.

## `qr_secrets` — khóa sinh mã QR động  
> **[❌ BỎ → cột `persons.qr_secret_enc`]**

| Cột | Kiểu | Giá trị nhận / Ý nghĩa | Mẫu |
|---|---|---|---|
| `person_id` | `BIGINT` | Của ai. **Là khóa chính** | `12` |
| `secret_enc` | `BYTEA` | Khóa bí mật 32 byte, **mã hóa AES-256-GCM** | `\x8f3a2b...` (nhị phân) |
| `algorithm` | `VARCHAR(20)` | Thuật toán | `HMAC-SHA256` |
| `period_sec` | `SMALLINT` | Mã đổi mỗi bao nhiêu giây | `30` |
| `rotated_at` | `TIMESTAMPTZ` | Lần đổi khóa gần nhất | `2026-07-15 10:35:00+07` |

> **Cách hoạt động:** khóa này được gửi cho app **một lần duy nhất** lúc cài đặt, app lưu vào Keychain/Keystore. Sau đó app tự sinh mã QR **offline** (không cần mạng — quan trọng vì sóng trong phòng gym thường yếu), mã đổi mỗi 30 giây. Chụp màn hình gửi cho bạn thì đã hết hạn.

## `check_ins` — lượt vào/ra phòng tập ⭐  
> **[✅ GIỮ (gánh thêm sự cố check-in)]**

| Cột | Kiểu | Giá trị nhận / Ý nghĩa | Mẫu |
|---|---|---|---|
| `member_id` | `BIGINT` | Hội viên vào tập | `123` |
| `trainer_id` | `BIGINT` | PT chấm công vào ca | `NULL` |
| `staff_id` | `BIGINT` | Nhân viên chấm công | `NULL` |
| `registration_id` | `BIGINT` | Dùng hợp đồng nào để vào | `451` |
| `checked_in_at` | `TIMESTAMPTZ` | Giờ vào | `2026-08-01 18:05:12+07` |
| `checked_out_at` | `TIMESTAMPTZ` | Giờ ra. `NULL` = **đang ở trong phòng tập** | `2026-08-01 19:47:30+07` |
| `method` | `VARCHAR(20)` | **Cách check-in** — xem bảng dưới | `QR_DYNAMIC` |
| `result` | `VARCHAR(20)` | **Kết quả** — xem bảng dưới | `ALLOWED` |
| `verified_by` | `BIGINT` | Lễ tân nào xác nhận | `4` |
| `face_match_score` | `NUMERIC(4,3)` | Điểm khớp khuôn mặt 0–1 *(tính năng nâng cao)* | `NULL` |
| `photo_url` | `VARCHAR(500)` | Ảnh chụp lúc check-in | `NULL` |
| `device_id` | `VARCHAR(60)` | Thiết bị nào quét | `FRONTDESK-01` |
| `manual_reason` | `VARCHAR(255)` | **Bắt buộc** khi `method = MANUAL` | `NULL` |

### `method` — 5 cách check-in

| Giá trị | Nghĩa |
|---|---|
| `QR_DYNAMIC` | Quét QR động từ app hội viên (khuyến khích nhất) |
| `RFID` | Chạm thẻ từ lên đầu đọc |
| `MANUAL` | Lễ tân nhập tay (khách quên cả thẻ lẫn điện thoại) — **bắt buộc ghi lý do** |
| `FACE` | Nhận diện khuôn mặt *(tùy chọn nâng cao)* |
| `DAY_PASS` | Khách vãng lai mua vé lẻ |

### `result` — 7 kết quả

| Giá trị | Nghĩa | Màn hình quầy hiện gì |
|---|---|---|
| `ALLOWED` | Hợp lệ, cho vào | 🟢 Xanh, ảnh + tên + số ngày còn lại |
| `ALLOWED_OVERRIDE` | Lễ tân bỏ qua cảnh báo, vẫn cho vào | 🟡 Vàng + ghi audit |
| `DENIED_EXPIRED` | Gói đã hết hạn | 🔴 Đỏ + nút "Gia hạn ngay" |
| `DENIED_FROZEN` | Đang trong kỳ bảo lưu | 🟡 Vàng + nút "Kết thúc bảo lưu sớm" |
| `DENIED_UNPAID` | Còn nợ tiền (trả góp) | 🟡 Vàng + số tiền còn nợ + nút thu tiền |
| `DENIED_NOT_FOUND` | Không tìm thấy thẻ/mã | 🔴 Đỏ + ô tìm theo SĐT |
| `DENIED_SUSPECT` | Nghi mượn thẻ | 🔴 Đỏ + yêu cầu xuất trình CCCD |

> **Vì sao ghi cả lượt bị từ chối:** đây chính là **dữ liệu để đo hiệu quả chống thất thoát**. Nếu chỉ ghi lượt thành công thì không biết đã chặn được bao nhiêu ca gian lận — mà đó là chỉ số quan trọng nhất của đóng góp ĐG2.

**Dữ liệu mẫu — một buổi tối của hội viên An:**

| checked_in_at | checked_out_at | method | result | verified_by | manual_reason |
|---|---|---|---|---|---|
| `2026-08-01 18:05:12+07` | `2026-08-01 19:47:30+07` | `QR_DYNAMIC` | `ALLOWED` | 4 | `NULL` |
| `2026-08-03 18:12:00+07` | `NULL` | `MANUAL` | `ALLOWED` | 4 | `Khách quên thẻ và điện thoại, đã xác minh CCCD` |

## `check_in_incidents` — sự cố check-in nghi vấn  
> **[❌ BỎ → 2 cột trong `check_ins`]**

| Cột | Kiểu | Giá trị nhận / Ý nghĩa | Mẫu |
|---|---|---|---|
| `check_in_id` | `BIGINT` | Gắn với lượt check-in nào | `88921` |
| `member_id` | `BIGINT` | Của hội viên nào | `123` |
| `incident_type` | `VARCHAR(30)` | **5 loại** — xem dưới | `ANTI_PASSBACK` |
| `severity` | `VARCHAR(10)` | `LOW` \| `MEDIUM` \| `HIGH` | `MEDIUM` |
| `detail` | `TEXT` | Mô tả | `Check-in lần 2 sau 12 phút kể từ lần trước` |
| `handled_by` | `BIGINT` | Lễ tân/Admin xử lý | `4` |
| `handled_at` | `TIMESTAMPTZ` | Xử lý lúc nào | `2026-08-01 18:18:00+07` |
| `resolution` | `TEXT` | Kết luận | `Khách ra ngoài mua nước rồi vào lại — hợp lệ` |

### `incident_type` — 5 loại

| Giá trị | Khi nào phát sinh | Mức |
|---|---|---|
| `ANTI_PASSBACK` | Check-in lần 2 trong vòng 30 phút | `MEDIUM` |
| `SUSPECTED_SHARING` | Người này **đang ở trong phòng tập** (chưa check-out) mà lại có lượt check-in mới → gần như chắc chắn có người thứ 2 dùng thẻ | `HIGH` |
| `REPLAY_ATTEMPT` | Dùng lại mã QR đã quét rồi | `HIGH` |
| `FACE_MISMATCH` | Khuôn mặt không khớp ảnh hồ sơ *(nếu bật tính năng nâng cao)* | `HIGH` |
| `EXPIRED_ATTEMPT` | Gói hết hạn nhưng vẫn cố quét nhiều lần | `LOW` |

---
---

# NHÓM 4 — BUỔI TẬP PT & SỔ CÁI

## `trainer_availability` — khung giờ PT nhận học viên  
> **[❌ BỎ → hội viên đề xuất giờ, PT duyệt]**

| Cột | Kiểu | Giá trị nhận / Ý nghĩa | Mẫu |
|---|---|---|---|
| `trainer_id` | `BIGINT` | PT nào | `5` |
| `day_of_week` | `SMALLINT` | `0`=CN, `1`=T2, … `6`=T7. `NULL` nếu dùng `specific_date` | `4` (thứ Năm) |
| `specific_date` | `DATE` | Ngày cụ thể (dùng khai báo nghỉ). `NULL` nếu dùng `day_of_week` | `NULL` |
| `start_time` | `TIME` | Từ mấy giờ | `17:00` |
| `end_time` | `TIME` | Đến mấy giờ | `21:00` |
| `is_available` | `BOOLEAN` | `TRUE` = nhận học viên · `FALSE` = **khai báo nghỉ** | `TRUE` |
| `valid_from` / `valid_to` | `DATE` | Lịch này áp dụng trong khoảng nào | `2026-07-01` / `NULL` |

**Ví dụ lịch PT Trần Bình:**

| day_of_week | specific_date | start_time | end_time | is_available | Nghĩa |
|---|---|---|---|:---:|---|
| 1 | `NULL` | 17:00 | 21:00 | `TRUE` | Thứ 2 nhận học viên 17–21h |
| 4 | `NULL` | 17:00 | 21:00 | `TRUE` | Thứ 5 nhận học viên 17–21h |
| `NULL` | `2026-09-02` | 00:00 | 23:59 | **`FALSE`** | **Nghỉ cả ngày 02/09** (lễ Quốc khánh) |

## `pt_bookings` — yêu cầu đặt lịch từ hội viên  
> **[❌ BỎ → `pt_sessions` với status=PENDING_TRAINER]**

| Cột | Kiểu | Giá trị nhận / Ý nghĩa | Mẫu |
|---|---|---|---|
| `member_id` | `BIGINT` | Ai đặt | `123` |
| `trainer_id` | `BIGINT` | Đặt với PT nào | `5` |
| `registration_id` | `BIGINT` | Dùng gói nào để trừ buổi | `451` |
| `requested_start` | `TIMESTAMPTZ` | Xin tập từ lúc | `2026-08-06 19:00:00+07` |
| `requested_end` | `TIMESTAMPTZ` | Đến lúc | `2026-08-06 20:00:00+07` |
| `status` | `VARCHAR(20)` | **6 giá trị** — xem dưới | `CONFIRMED` |
| `requested_by` | `BIGINT` | Tài khoản gửi yêu cầu | `30` |
| `responded_at` | `TIMESTAMPTZ` | PT trả lời lúc nào | `2026-08-04 21:15:00+07` |
| `reject_reason` | `VARCHAR(255)` | Lý do từ chối | `NULL` |

| `status` | Nghĩa |
|---|---|
| `PENDING_TRAINER` | Đã gửi, chờ PT duyệt |
| `CONFIRMED` | PT đã duyệt → sinh `pt_sessions` |
| `REJECTED` | PT từ chối (kèm lý do, thường đề xuất giờ khác) |
| `CANCELLED_BY_MEMBER` | Hội viên hủy |
| `CANCELLED_BY_TRAINER` | PT hủy |
| `EXPIRED` | Quá giờ mà PT không phản hồi |

## `pt_sessions` — buổi tập thực tế ⭐  
> **[✅ GIỮ (gánh thêm đặt lịch)]**

| Cột | Kiểu | Giá trị nhận / Ý nghĩa | Mẫu |
|---|---|---|---|
| `booking_id` | `BIGINT` | Sinh ra từ yêu cầu nào | `7712` |
| `member_id` | `BIGINT` | Học viên | `123` |
| `trainer_id` | `BIGINT` | **PT THỰC TẾ dạy** (có thể khác PT `PRIMARY`) | `5` |
| `registration_id` | `BIGINT` | Trừ buổi từ hợp đồng nào | `451` |
| `room_id` | `BIGINT` | Tập ở phòng nào | `2` |
| `session_type` | `VARCHAR(20)` | **6 loại** — quyết định trừ buổi & tính công | `PAID_PT` |
| `scheduled_start` / `scheduled_end` | `TIMESTAMPTZ` | Lịch dự kiến | `2026-08-06 19:00` / `20:00` |
| `actual_start` / `actual_end` | `TIMESTAMPTZ` | Thực tế bắt đầu/kết thúc | `19:05` / `20:02` |
| `status` | `VARCHAR(20)` | **6 trạng thái** — xem dưới | `COMPLETED` |
| `trainer_confirmed_at` | `TIMESTAMPTZ` | **PT xác nhận lúc nào** | `2026-08-06 20:03:00+07` |
| `member_confirmed_at` | `TIMESTAMPTZ` | **Hội viên xác nhận lúc nào** | `2026-08-06 20:04:15+07` |
| `auto_confirmed` | `BOOLEAN` | `TRUE` = hội viên không xác nhận trong 24h, hệ thống tự duyệt | `FALSE` |
| `cancelled_by` | `VARCHAR(20)` | `MEMBER` \| `TRAINER` \| `SYSTEM` | `NULL` |
| `cancel_reason` | `VARCHAR(255)` | Lý do hủy | `NULL` |
| `is_late_cancel` | `BOOLEAN` | `TRUE` = hủy muộn (<4h) → **mất buổi** | `FALSE` |
| `note` | `TEXT` | Ghi chú buổi tập | `Tập ngực + tay sau, tăng 5kg bench press` |

### `session_type` — 6 loại và tác động ⭐

| Giá trị | Nghĩa | Trừ buổi của hội viên? | **PT được tính công?** | Đơn giá công |
|---|---|:---:|:---:|---:|
| `PAID_PT` | Buổi PT có trả phí | ✅ −1 | ✅ | 120.000đ |
| `COMPLIMENTARY` | **PT hỗ trợ tập miễn phí** | ❌ **0** | ✅ **CÓ** | 60.000đ |
| `TRIAL` | Buổi tập thử cho khách tiềm năng | ❌ 0 | ✅ | 80.000đ |
| `ORIENTATION` | Hướng dẫn làm quen máy móc cho hội viên mới | ❌ 0 | ✅ | 50.000đ |
| `MAKEUP` | Tập bù buổi đã hủy đúng hạn | ❌ 0 | ✅ | 120.000đ |
| `ASSESSMENT` | Buổi đo chỉ số cơ thể, đánh giá thể trạng | ❌ 0 | ✅ | 70.000đ |

> **Đây là chỗ trả lời yêu cầu của thầy:** *"PT trợ giúp tập miễn phí — tính công. Khi PT trợ giúp hội viên tập miễn phí trong 1 buổi thì buổi đó PT vẫn phải được trả lương."* Loại `COMPLIMENTARY` **trừ 0 buổi** của hội viên nhưng **vẫn sinh 1 dòng công** trong `payroll_items`. Admin đặt hạn mức số buổi `COMPLIMENTARY`/PT/tháng trong `system_settings` để tránh lạm dụng.

### `status` — 6 trạng thái

| Giá trị | Nghĩa |
|---|---|
| `SCHEDULED` | Đã lên lịch, chưa tới giờ |
| `IN_PROGRESS` | Đang tập (PT đã bấm "Bắt đầu") |
| `COMPLETED` | **Hoàn thành + đã xác nhận 2 chiều** → trừ buổi + tính công + ghi nhận doanh thu |
| `NO_SHOW_MEMBER` | Hội viên không đến |
| `NO_SHOW_TRAINER` | PT không đến |
| `CANCELLED` | Đã hủy |

> **Xác nhận hai chiều:** buổi tập chỉ chuyển `COMPLETED` khi **cả `trainer_confirmed_at` và `member_confirmed_at` đều có giá trị**. PT bấm "Kết thúc" → sinh QR → hội viên quét QR đó để xác nhận. Nếu hội viên không xác nhận trong 24h thì hệ thống tự duyệt nhưng đặt `auto_confirmed = TRUE` để kiểm toán được — **tỷ lệ `auto_confirmed` cao là dấu hiệu bất thường cần điều tra**.
>
> **Ràng buộc chống trùng lịch ở tầng CSDL:** một PT không thể có 2 buổi tập chồng giờ; một hội viên cũng vậy. Dùng `EXCLUDE` constraint của PostgreSQL → 2 request đặt lịch cùng lúc thì DB tự từ chối request thứ hai, không phụ thuộc vào việc code có nhớ kiểm tra hay không.

## `session_credit_ledger` — SỔ CÁI TÍN DỤNG BUỔI TẬP ⭐⭐⭐  
> **[✅ GIỮ ⭐ đóng góp chính]**

> **Đóng góp học thuật chính của đồ án.** Bảng **chỉ ghi thêm** (append-only): không `UPDATE`, không `DELETE`. Trigger ở tầng CSDL chặn cứng.

| Cột | Kiểu | Giá trị nhận / Ý nghĩa | Mẫu |
|---|---|---|---|
| `registration_id` | `BIGINT` | Sổ cái của hợp đồng nào | `451` |
| `entry_type` | `VARCHAR(20)` | **7 loại bút toán** — xem dưới | `CONSUME` |
| `delta` | `INTEGER` | Thay đổi bao nhiêu buổi. **Khác 0**, có thể âm | `-1` |
| `balance_after` | `INTEGER` | **Số dư SAU bút toán này**. Không bao giờ âm | `11` |
| `source_type` | `VARCHAR(30)` | `PT_SESSION` \| `REGISTRATION` \| `TRANSFER` \| `MANUAL` \| `SYSTEM` | `PT_SESSION` |
| `source_id` | `BIGINT` | ID của nguồn (VD `pt_sessions.id`) | `9931` |
| `reason` | `TEXT` | Lý do. **Bắt buộc** với `ADJUST` | `NULL` |
| `created_by` | `BIGINT` | Ai gây ra. **Bắt buộc** với `ADJUST` | `NULL` |

### `entry_type` — 7 loại bút toán

| Giá trị | Khi nào | `delta` |
|---|---|---|
| `GRANT` | Hợp đồng chuyển `ACTIVE` → cấp buổi | `+12` |
| `CONSUME` | Buổi tập `PAID_PT` chuyển `COMPLETED` | `−1` |
| `REFUND` | Hủy buổi **đúng hạn** (trước 4h) → trả lại buổi | `+1` |
| `EXPIRE` | Hợp đồng hết hạn còn dư buổi | `−n` |
| `ADJUST` | Sửa sai thủ công — **bắt buộc có `reason` + `created_by`** | `±n` |
| `TRANSFER_OUT` | Chuyển buổi sang hợp đồng khác | `−n` |
| `TRANSFER_IN` | Nhận buổi từ hợp đồng khác | `+n` |

### Dữ liệu mẫu — toàn bộ vòng đời gói PT 12 buổi

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
      Kiểm: 12−1−1+1−1+1−…−2 = 0  ✓ khớp balance_after dòng 20

2. balance_after >= 0 ở MỌI dòng      (không bao giờ âm buổi)

3. balance_after[i] == balance_after[i−1] + delta[i]   (liên tục, không nhảy)
```

> **Vì sao không dùng 1 cột `sessions_remaining`:** cột đó bị `UPDATE` liên tục. Khi số liệu sai, không biết sai từ bút toán nào, sai lúc nào, ai gây ra. Tranh chấp giữa PT và hội viên về "còn mấy buổi" không có bằng chứng. Với sổ cái, mọi thay đổi đều có dấu vết và **cộng lại phải khớp** — sai là phát hiện ngay.
>
> **Xử lý 2 request trừ buổi cùng lúc:** dùng `SELECT ... FOR UPDATE` khóa dòng cuối trước khi ghi bút toán mới → chỉ 1 request thành công, request kia bị từ chối vì không đủ buổi.

---
---

# NHÓM 5 — LỚP HỌC & PHÒNG TẬP

> Tách 1 bảng `classes` cũ thành 5 vì bảng cũ **trộn 2 khái niệm**: `code`/`class_type`/`maximum_number` mô tả LỚP, còn `location`/`is_occupied` mô tả PHÒNG.

## `rooms` — không gian vật lý  
> **[❌ BỎ → `class_sessions.room_name` + `equipment.location`]**

| Cột | Kiểu | Giá trị nhận / Ý nghĩa | Mẫu |
|---|---|---|---|
| `code` | `VARCHAR(30)` | Mã phòng. **Duy nhất** | `YOGA-01` |
| `name` | `VARCHAR(120)` | Tên hiển thị | `Phòng Yoga 1` |
| `area_code` | `VARCHAR(30)` | Khớp với `access_scopes.area_code` | `YOGA_STUDIO` |
| `capacity` | `INTEGER` | Sức chứa tối đa. `> 0` | `20` |
| `floor` | `VARCHAR(20)` | Tầng | `Tầng 2` |
| `status` | `VARCHAR(20)` | `AVAILABLE` \| `MAINTENANCE` \| `CLOSED` | `AVAILABLE` |

## `class_definitions` — định nghĩa lớp  
> **[❌ BỎ → gộp vào `class_sessions`]**

| Cột | Kiểu | Giá trị nhận / Ý nghĩa | Mẫu |
|---|---|---|---|
| `code` | `VARCHAR(30)` | Mã lớp. **Duy nhất** | `YOGA-MORNING` |
| `name` | `VARCHAR(120)` | Tên lớp | `Yoga buổi sáng` |
| `class_type` | `VARCHAR(30)` | `YOGA` \| `FITNESS` \| `HIIT` \| `ZUMBA` \| `SPINNING` \| `BOXING` \| … | `YOGA` |
| `description` | `TEXT` | Mô tả | `Yoga cơ bản, phù hợp người mới bắt đầu` |
| `default_duration_min` | `INTEGER` | Mặc định kéo dài bao lâu (phút) | `60` |
| `default_capacity` | `INTEGER` | Sức chứa mặc định | `20` |
| `level` | `VARCHAR(20)` | `BEGINNER` \| `INTERMEDIATE` \| `ADVANCED` | `BEGINNER` |
| `required_area` | `VARCHAR(30)` | Gói phải có khu vực này mới đăng ký được | `YOGA_STUDIO` |
| `status` | `VARCHAR(20)` | `ACTIVE` \| `ARCHIVED` | `ACTIVE` |

> **Vì sao `class_type` không dùng `CHECK IN ('fitness','yoga')` như schema cũ:** phòng gym mở thêm lớp Zumba, Boxing thì phải chạy migration đổi ràng buộc. Ở đây kiểm tra danh sách hợp lệ ở tầng ứng dụng (lấy từ `system_settings`), thêm loại lớp mới chỉ cần sửa cấu hình.

## `class_schedules` — lịch lặp hàng tuần *(Should have)*  
> **[❌ BỎ → gộp vào `class_sessions`]**

| Cột | Kiểu | Giá trị nhận / Ý nghĩa | Mẫu |
|---|---|---|---|
| `class_def_id` | `BIGINT` | Lớp nào | `1` |
| `room_id` | `BIGINT` | Ở phòng nào | `2` |
| `trainer_id` | `BIGINT` | Ai dạy | `9` |
| `day_of_week` | `SMALLINT` | `0`=CN … `6`=T7 | `1` (thứ 2) |
| `start_time` | `TIME` | Bắt đầu lúc | `06:00` |
| `duration_min` | `INTEGER` | Kéo dài (phút) | `60` |
| `capacity` | `INTEGER` | Sức chứa buổi này | `20` |
| `valid_from` / `valid_to` | `DATE` | Lịch áp dụng trong khoảng nào | `2026-07-01` / `NULL` |

## `class_sessions` — buổi lớp cụ thể *(Should have)*  
> **[✅ GIỮ (gánh định nghĩa lớp + lịch + phòng)]**

| Cột | Kiểu | Giá trị nhận / Ý nghĩa | Mẫu |
|---|---|---|---|
| `schedule_id` | `BIGINT` | Sinh từ lịch nào. `NULL` = tạo thủ công | `3` |
| `class_def_id` | `BIGINT` | Lớp gì | `1` |
| `room_id` | `BIGINT` | Phòng nào | `2` |
| `trainer_id` | `BIGINT` | Ai dạy | `9` |
| `starts_at` / `ends_at` | `TIMESTAMPTZ` | Buổi cụ thể | `2026-08-10 06:00` / `07:00` |
| `capacity` | `INTEGER` | Sức chứa | `20` |
| `booked_count` | `INTEGER` | Đã có bao nhiêu người đăng ký. `0 ≤ booked_count ≤ capacity` | `17` |
| `status` | `VARCHAR(20)` | `SCHEDULED` \| `IN_PROGRESS` \| `COMPLETED` \| `CANCELLED` | `SCHEDULED` |
| `cancel_reason` | `VARCHAR(255)` | Lý do hủy buổi | `NULL` |

## `class_bookings` — hội viên đặt chỗ trong buổi lớp *(Should have)*  
> **[✅ GIỮ]**

| Cột | Kiểu | Giá trị nhận / Ý nghĩa | Mẫu |
|---|---|---|---|
| `class_session_id` | `BIGINT` | Buổi lớp nào | `1204` |
| `member_id` | `BIGINT` | Ai đặt | `123` |
| `registration_id` | `BIGINT` | **Dùng gói nào để đặt** | `451` |
| `status` | `VARCHAR(20)` | `BOOKED` \| `WAITLISTED` \| `ATTENDED` \| `NO_SHOW` \| `CANCELLED` | `BOOKED` |
| `waitlist_position` | `SMALLINT` | Vị trí trong hàng chờ (nếu lớp đầy) | `NULL` |
| `booked_at` | `TIMESTAMPTZ` | Đặt lúc nào | `2026-08-08 21:30:00+07` |
| `cancelled_at` | `TIMESTAMPTZ` | Hủy lúc nào | `NULL` |

> **Đây là câu trả lời cho cô Trinh:** hội viên **mua gói** (tạo 1 `registration`) rồi **dùng gói đó đặt nhiều lớp** (tạo nhiều `class_booking`). Cách của draft — nhồi `class_id` vào `registrations` — chỉ chứa được **một** lớp, trong khi hội viên gói 6 tháng thường học yoga thứ 2 và HIIT thứ 5. Nguyên tắc gốc vẫn giữ: **không có `registration` hợp lệ thì không đặt được lớp** (`registration_id` là `NOT NULL`).

---
---

# NHÓM 6 — THIẾT BỊ & BẢO TRÌ

> Tách `facilities` cũ làm 2 vì bảng cũ nhập nhằng: `total_quantity` gợi ý là **LOẠI** thiết bị, nhưng `date_of_purchase`/`warranty_date` gợi ý là **TỪNG CÁI**. Hệ quả: không truy vết được *"máy chạy bộ **số 3** bị hỏng"*.

## `equipment_types` — loại thiết bị  
> **[❌ BỎ → gộp vào `equipment`]**

| Cột | Kiểu | Giá trị nhận / Ý nghĩa | Mẫu |
|---|---|---|---|
| `code` | `VARCHAR(30)` | Mã loại. **Duy nhất** | `TREADMILL` |
| `name` | `VARCHAR(150)` | Tên loại | `Máy chạy bộ` |
| `category` | `VARCHAR(40)` | `CARDIO` \| `STRENGTH` \| `FREE_WEIGHT` \| `ACCESSORY` \| `YOGA` | `CARDIO` |
| `brand` | `VARCHAR(80)` | Hãng | `Technogym` |
| `model` | `VARCHAR(80)` | Model | `MyRun` |
| `useful_life_months` | `SMALLINT` | Tuổi thọ — **cơ sở tính khấu hao** | `60` |

## `equipment_items` — từng cái thiết bị cụ thể  
> **[✅ GIỮ → đổi tên thành `equipment`]**

| Cột | Kiểu | Giá trị nhận / Ý nghĩa | Mẫu |
|---|---|---|---|
| `equipment_type_id` | `BIGINT` | Thuộc loại nào | `1` |
| `room_id` | `BIGINT` | Đặt ở phòng nào | `1` |
| `asset_code` | `VARCHAR(40)` | **Mã tài sản dán trên máy**. Duy nhất | `TM-003` |
| `serial_number` | `VARCHAR(80)` | Số seri của hãng | `TG-MR-2024-88192` |
| `origin` | `VARCHAR(100)` | Xuất xứ | `Italy` |
| `purchase_price` | `NUMERIC(14,2)` | Giá mua — cơ sở khấu hao | `85000000.00` |
| `date_of_purchase` | `DATE` | Ngày mua | `2024-05-20` |
| `warranty_until` | `DATE` | Bảo hành đến | `2027-05-20` |
| `status` | `VARCHAR(20)` | `OPERATIONAL` \| `NEEDS_REPAIR` \| `UNDER_MAINTENANCE` \| `RETIRED` | `NEEDS_REPAIR` |
| `last_maintained_at` | `DATE` | Bảo dưỡng lần cuối | `2026-06-01` |
| `disposed_at` | `DATE` | Ngày thanh lý | `NULL` |

**Khấu hao hàng tháng** = `purchase_price / useful_life_months` = `85.000.000 / 60` = **1.416.667đ/tháng** → tự động sinh 1 dòng trong `expenses` mỗi tháng.

## `maintenance_work_orders` — phiếu sửa chữa  
> **[❌ BỎ → `feedbacks` + cột `repair_cost`]**

| Cột | Kiểu | Giá trị nhận / Ý nghĩa | Mẫu |
|---|---|---|---|
| `equipment_item_id` | `BIGINT` | Sửa máy nào | `12` |
| `room_id` | `BIGINT` | Hoặc sửa phòng nào | `NULL` |
| `reported_by` | `BIGINT` | Ai báo | `30` |
| `feedback_id` | `BIGINT` | Sinh từ phản ánh nào của hội viên | `882` |
| `wo_type` | `VARCHAR(20)` | `PREVENTIVE` (bảo dưỡng định kỳ) \| `CORRECTIVE` (sửa hỏng) | `CORRECTIVE` |
| `priority` | `VARCHAR(10)` | `LOW` \| `MEDIUM` \| `HIGH` \| `URGENT` | `HIGH` |
| `description` | `TEXT` | Mô tả hỏng hóc | `Máy chạy bộ TM-003 kêu to khi chạy trên 8km/h` |
| `status` | `VARCHAR(20)` | `OPEN` \| `IN_PROGRESS` \| `WAITING_PARTS` \| `RESOLVED` \| `CANCELLED` | `RESOLVED` |
| `assigned_to` | `BIGINT` | Giao cho ai | `15` |
| `resolution` | `TEXT` | Đã xử lý thế nào | `Thay dây curoa, tra dầu trục lăn` |
| `cost` | `NUMERIC(14,2)` | Chi phí sửa | `1200000.00` |
| `expense_id` | `BIGINT` | Dòng chi phí tự động sinh ra | `3391` |
| `opened_at` / `resolved_at` | `TIMESTAMPTZ` | Mở / đóng lúc nào | `2026-08-01 19:20` / `2026-08-03 10:00` |

---
---

# NHÓM 7 — THANH TOÁN

## `invoices` — hóa đơn  
> **[✅ GIỮ]**

| Cột | Kiểu | Giá trị nhận / Ý nghĩa | Mẫu |
|---|---|---|---|
| `invoice_no` | `VARCHAR(30)` | Số hóa đơn. **Duy nhất** | `INV-2026-000451` |
| `member_id` | `BIGINT` | Của hội viên nào | `123` |
| `person_id` | `BIGINT` | Hoặc của khách vãng lai chưa là hội viên | `NULL` |
| `registration_id` | `BIGINT` | Cho hợp đồng nào | `451` |
| `subtotal` | `NUMERIC(14,2)` | Tổng trước giảm giá | `4200000.00` |
| `discount_amount` | `NUMERIC(14,2)` | Giảm giá | `420000.00` |
| `tax_amount` | `NUMERIC(14,2)` | Thuế (thường 0 cho dịch vụ gym) | `0.00` |
| `total_amount` | `NUMERIC(14,2)` | **Phải thu** | `3780000.00` |
| `paid_amount` | `NUMERIC(14,2)` | Đã thu. `≤ total_amount` | `3780000.00` |
| `balance_due` | `NUMERIC(14,2)` | **Còn nợ** — cột tự tính = `total − paid` | `0.00` |
| `status` | `VARCHAR(20)` | `UNPAID` \| `PARTIALLY_PAID` \| `PAID` \| `OVERDUE` \| `CANCELLED` \| `REFUNDED` | `PAID` |
| `issued_at` | `TIMESTAMPTZ` | Xuất lúc nào | `2026-07-15 10:20:00+07` |
| `due_date` | `DATE` | Hạn thanh toán | `2026-07-22` |
| `paid_at` | `TIMESTAMPTZ` | Trả đủ lúc nào | `2026-07-15 10:28:00+07` |
| `issued_by` | `BIGINT` | Ai xuất | `4` |

## `invoice_items` — dòng chi tiết hóa đơn  
> **[❌ BỎ → 1 hóa đơn = 1 gói tập]**

| Cột | Kiểu | Giá trị nhận / Ý nghĩa | Mẫu |
|---|---|---|---|
| `invoice_id` | `BIGINT` | Thuộc hóa đơn nào | `451` |
| `item_type` | `VARCHAR(30)` | `MEMBERSHIP` \| `PT_PACKAGE` \| `DAY_PASS` \| `LOCKER` \| `PRODUCT` \| `FEE` | `PT_PACKAGE` |
| `reference_id` | `BIGINT` | Trỏ tới `memberships.id` / `registrations.id` … | `7` |
| `description` | `VARCHAR(255)` | Mô tả in trên phiếu | `Gói PT 12 buổi (KM hè 2026 -10%)` |
| `quantity` | `INTEGER` | Số lượng. `> 0` | `1` |
| `unit_price` | `NUMERIC(14,2)` | Đơn giá | `4200000.00` |
| `discount` | `NUMERIC(14,2)` | Giảm giá dòng này | `420000.00` |
| `line_total` | `NUMERIC(14,2)` | Cột tự tính = `qty × unit_price − discount` | `3780000.00` |

## `cash_shifts` — ca làm việc của lễ tân ⭐  
> **[✅ GIỮ]**

| Cột | Kiểu | Giá trị nhận / Ý nghĩa | Mẫu |
|---|---|---|---|
| `staff_id` | `BIGINT` | Lễ tân nào | `4` |
| `opened_at` | `TIMESTAMPTZ` | Mở ca lúc | `2026-08-01 06:00:00+07` |
| `closed_at` | `TIMESTAMPTZ` | Đóng ca lúc | `2026-08-01 14:00:00+07` |
| `opening_balance` | `NUMERIC(14,2)` | Tiền lẻ đầu ca | `500000.00` |
| `expected_cash` | `NUMERIC(14,2)` | **Hệ thống tính** = đầu ca + Σ thu tiền mặt | `2300000.00` |
| `counted_cash` | `NUMERIC(14,2)` | **Lễ tân đếm thực tế** trong két | `2250000.00` |
| `difference` | `NUMERIC(14,2)` | Cột tự tính = `counted − expected` | `-50000.00` |
| `difference_reason` | `TEXT` | **Bắt buộc** khi lệch | `Trả nhầm tiền thừa cho khách lúc 11h20` |
| `status` | `VARCHAR(20)` | `OPEN` \| `CLOSED` (khớp) \| `DISCREPANCY` (lệch) | `DISCREPANCY` |
| `verified_by` | `BIGINT` | Kế toán xác nhận | `7` |

**Ràng buộc quan trọng:** mỗi lễ tân chỉ có **tối đa 1 ca đang `OPEN`**; mọi khoản thu tiền mặt thành công **bắt buộc** phải gắn với một ca → không có tiền mặt nào lọt ngoài sổ.

**Chỉ số quản trị rút ra:** tỷ lệ ca có chênh lệch, giá trị lệch trung bình theo từng nhân viên → phát hiện vấn đề vận hành hoặc gian lận.

## `payments` — khoản thu ⭐  
> **[✅ GIỮ (gánh hoàn tiền + webhook)]**

| Cột | Kiểu | Giá trị nhận / Ý nghĩa | Mẫu |
|---|---|---|---|
| `payment_no` | `VARCHAR(30)` | Số phiếu thu. **Duy nhất** | `PAY-2026-001102` |
| `member_id` | `BIGINT` | Của ai | `123` |
| `cash_shift_id` | `BIGINT` | Thuộc ca nào (bắt buộc nếu tiền mặt) | `88` |
| `method` | `VARCHAR(20)` | **6 phương thức** — xem dưới | `VIETQR` |
| `amount` | `NUMERIC(14,2)` | Số tiền. `> 0` | `3780000.00` |
| `currency` | `CHAR(3)` | Luôn `VND` | `VND` |
| `status` | `VARCHAR(20)` | **7 trạng thái** — xem dưới | `SUCCEEDED` |
| `idempotency_key` | `VARCHAR(64)` | **Chống trừ tiền 2 lần** khi client gửi lại | `9f2a1c88-...-b3e1` |
| `provider` | `VARCHAR(30)` | `VNPAY` \| `MOMO` \| `ZALOPAY` \| `SEPAY` \| `BANK_TRANSFER` | `SEPAY` |
| `provider_txn_id` | `VARCHAR(100)` | Mã giao dịch bên cung cấp | `FT26072812345678` |
| `transfer_content` | `VARCHAR(255)` | **Nội dung chuyển khoản** — cơ sở đối soát tự động | `GYM INV2026000451` |
| `bank_account` | `VARCHAR(50)` | Tài khoản nhận | `0123456789 - MB Bank` |
| `pos_terminal_id` | `VARCHAR(50)` | Mã máy POS | `NULL` |
| `pos_card_last4` | `CHAR(4)` | **4 số cuối thẻ** (tuân thủ PCI-DSS, không lưu số đầy đủ) | `NULL` |
| `received_by` | `BIGINT` | Ai thu | `4` |
| `paid_at` | `TIMESTAMPTZ` | Trả lúc nào | `2026-07-15 10:28:00+07` |
| `reconciled_at` | `TIMESTAMPTZ` | Đối soát xong lúc nào | `2026-07-15 23:00:00+07` |
| `raw_payload` | `JSONB` | Payload webhook thô — để điều tra khi tranh chấp | `{"id":"...","amount":3780000,...}` |

### `method` — 6 phương thức

| Giá trị | Nghĩa | Có API tự động? |
|---|---|---|
| `CASH` | Tiền mặt tại quầy | ❌ — đối soát bằng `cash_shifts` |
| `BANK_TRANSFER` | Chuyển khoản thường | ✅ webhook biến động số dư |
| `VIETQR` | Quét mã VietQR | ✅ webhook biến động số dư |
| `CARD_POS` | Quẹt thẻ máy POS | ❌ — nhập tay + đối soát sao kê |
| `E_WALLET` | Ví điện tử (MoMo, ZaloPay) | ✅ IPN webhook |
| `GATEWAY` | Cổng thanh toán (VNPay) | ✅ IPN webhook |

> **Máy POS không có API cho bên thứ ba** (lý do bảo mật PCI-DSS). Quy trình thực tế: lễ tân quẹt thẻ trên máy POS → máy in biên lai → lễ tân nhập `provider_txn_id` + `pos_card_last4` vào hệ thống → cuối ngày kế toán tải sao kê POS từ ngân hàng và khớp tự động theo mã giao dịch. Đây là **giới hạn hạ tầng ngân hàng, không phải giới hạn thiết kế** — nên nêu rõ trong báo cáo.

### `status` — 7 trạng thái

`INITIATED` (vừa tạo) → `PENDING` (chờ khách trả) → `SUCCEEDED` ✅ / `FAILED` / `EXPIRED`
`SUCCEEDED` → `REFUNDED` / `PARTIALLY_REFUNDED`

## `payment_allocations` — nối khoản thu với hóa đơn (N–N)  
> **[❌ BỎ → cột `payments.invoice_id`]**

| Cột | Kiểu | Ý nghĩa | Mẫu |
|---|---|---|---|
| `payment_id` | `BIGINT` | Khoản thu nào | `1102` |
| `invoice_id` | `BIGINT` | Trả cho hóa đơn nào | `451` |
| `amount` | `NUMERIC(14,2)` | Phân bổ bao nhiêu. `> 0` | `3780000.00` |

> **Vì sao cần bảng trung gian:** 1 hóa đơn có thể trả **nhiều lần** (trả góp 3 kỳ → 3 `payments` cùng trỏ 1 `invoice`); 1 khoản thu có thể trả **nhiều hóa đơn** (khách chuyển 1 lần 5 triệu cho cả gói tập lẫn tiền tủ đồ).

## `payment_schedules` — kỳ hạn trả góp *(Should have)*  
> **[❌ BỎ → bỏ tính năng trả góp]**

| Cột | Kiểu | Giá trị nhận / Ý nghĩa | Mẫu |
|---|---|---|---|
| `registration_id` | `BIGINT` | Của hợp đồng nào | `451` |
| `installment_no` | `SMALLINT` | Kỳ thứ mấy | `2` |
| `due_date` | `DATE` | Đến hạn ngày | `2026-08-15` |
| `amount` | `NUMERIC(14,2)` | Phải trả kỳ này | `1260000.00` |
| `paid_amount` | `NUMERIC(14,2)` | Đã trả | `1260000.00` |
| `status` | `VARCHAR(20)` | `PENDING` \| `PAID` \| `OVERDUE` \| `WAIVED` (miễn) | `PAID` |

## `refunds` — hoàn tiền  
> **[❌ BỎ → `payments` với `amount` ÂM]**

| Cột | Kiểu | Giá trị nhận / Ý nghĩa | Mẫu |
|---|---|---|---|
| `registration_id` | `BIGINT` | Hoàn cho hợp đồng nào | `451` |
| `invoice_id` | `BIGINT` | Hóa đơn gốc | `451` |
| `payment_id` | `BIGINT` | Khoản thu gốc | `1102` |
| `gross_amount` | `NUMERIC(14,2)` | **Giá trị còn lại** chưa sử dụng | `1890000.00` |
| `penalty_amount` | `NUMERIC(14,2)` | Phí hủy hợp đồng | `378000.00` |
| `net_amount` | `NUMERIC(14,2)` | **Thực trả khách** = `gross − penalty` | `1512000.00` |
| `reason` | `TEXT` | Lý do. **Bắt buộc** | `Hội viên chuyển công tác vào TP.HCM` |
| `method` | `VARCHAR(20)` | Hoàn bằng gì | `BANK_TRANSFER` |
| `requested_by` / `approved_by` | `BIGINT` | Ai xin / ai duyệt | `4` / `1` |
| `status` | `VARCHAR(20)` | `PENDING` \| `APPROVED` \| `REJECTED` \| `PROCESSED` | `PROCESSED` |
| `processed_at` | `TIMESTAMPTZ` | Chuyển tiền lúc nào | `2026-10-05 15:00:00+07` |

## `webhook_events` — lưu webhook thô  
> **[❌ BỎ → `payments.raw_payload` + UNIQUE(provider, txn_id)]**

| Cột | Kiểu | Giá trị nhận / Ý nghĩa | Mẫu |
|---|---|---|---|
| `provider` | `VARCHAR(30)` | Từ đâu gửi tới | `SEPAY` |
| `event_id` | `VARCHAR(120)` | ID sự kiện bên gửi. **`UNIQUE(provider, event_id)`** | `evt_88192734` |
| `event_type` | `VARCHAR(60)` | Loại sự kiện | `transaction.credited` |
| `signature` | `VARCHAR(500)` | Chữ ký để xác thực | `sha256=a3f5...` |
| `payload` | `JSONB` | Nội dung thô | `{"amount":3780000,"content":"GYM INV2026000451",...}` |
| `signature_valid` | `BOOLEAN` | Chữ ký có hợp lệ không | `TRUE` |
| `processed` | `BOOLEAN` | Đã xử lý chưa | `TRUE` |
| `error_message` | `TEXT` | Lỗi nếu xử lý thất bại | `NULL` |

> **`UNIQUE(provider, event_id)` giải quyết vấn đề gì:** ngân hàng/cổng thanh toán **gửi lại webhook** nếu không nhận được phản hồi 200. Không có ràng buộc này thì 1 lần chuyển khoản bị ghi nhận 2 lần → hóa đơn trả thừa.

---
---

# NHÓM 8 — TÀI CHÍNH & LƯƠNG

## `revenue_schedules` — kế hoạch phân bổ doanh thu ⭐⭐  
> **[✅ GIỮ ⭐ đóng góp chính]**

| Cột | Kiểu | Giá trị nhận / Ý nghĩa | Mẫu |
|---|---|---|---|
| `registration_id` | `BIGINT` | Của hợp đồng nào. **Duy nhất** (1 hợp đồng 1 kế hoạch) | `451` |
| `total_amount` | `NUMERIC(14,2)` | Tổng giá trị hợp đồng | `3780000.00` |
| `recognition_method` | `VARCHAR(20)` | **3 phương pháp** — xem dưới | `PER_SESSION` |
| `start_date` | `DATE` | Bắt đầu ghi nhận | `2026-07-15` |
| `end_date` | `DATE` | Kết thúc | `2027-01-11` |
| `total_units` | `INTEGER` | Tổng đơn vị (số ngày **hoặc** số buổi) | `12` |
| `recognized_units` | `INTEGER` | Đã ghi nhận bao nhiêu đơn vị | `3` |
| `recognized_amount` | `NUMERIC(14,2)` | **Đã ghi nhận** bao nhiêu tiền | `945000.00` |
| `deferred_amount` | `NUMERIC(14,2)` | **Chưa thực hiện** — còn nợ dịch vụ | `2835000.00` |
| `status` | `VARCHAR(20)` | `IN_PROGRESS` \| `COMPLETED` \| `TERMINATED` (hủy giữa chừng) | `IN_PROGRESS` |

### `recognition_method` — 3 phương pháp

| Giá trị | Dùng cho | Công thức |
|---|---|---|
| `STRAIGHT_LINE` | Gói `TIME_BASED` | `total_amount / duration_days` mỗi ngày, **bỏ qua ngày bảo lưu** |
| `PER_SESSION` | Gói `SESSION_BASED` | `total_amount / session_count` mỗi khi 1 buổi `COMPLETED` |
| `IMMEDIATE` | Vé lẻ `DAY_PASS` | Ghi nhận toàn bộ ngay |

### ⭐ Bất biến cốt lõi

```
recognized_amount + deferred_amount  ==  total_amount   (LUÔN LUÔN, sai số 0đ)
```

Ví dụ trên: `945.000 + 2.835.000 = 3.780.000` ✓

### Ví dụ minh họa cho báo cáo — gói Fitness 12 tháng

```
Gói FIT-12M, giá 4.800.000đ, bắt đầu 15/01/2026, phương pháp STRAIGHT_LINE

Ghi nhận mỗi ngày = 4.800.000 / 365 = 13.150,68đ/ngày

Tháng 01 (15→31/01, 17 ngày):  223.561đ  ghi nhận
Deferred cuối tháng 01      : 4.576.439đ  còn nợ dịch vụ

→ Sổ sách tháng 1 KHÔNG hiện 4,8 triệu doanh thu, chỉ hiện 223.561đ.
  Phần còn lại là NGHĨA VỤ PHẢI PHỤC VỤ 11,5 tháng nữa.

Nếu hội viên bảo lưu 15/03 → 13/04 (30 ngày):
  30 ngày đó KHÔNG ghi nhận đồng nào
  end_date đẩy từ 14/01/2027 → 13/02/2027
  Tổng vẫn đúng 4.800.000đ
```

> **Vì sao đây là đóng góp học thuật:** phần lớn phòng gym ghi hết 4,8 triệu vào tháng 1 → chủ phòng gym nhìn báo cáo thấy tháng 1 lãi lớn, tháng 6 lỗ nặng, **không hiểu vì sao**. Tách bạch "tiền thu được" và "doanh thu ghi nhận" là thứ biến module kế toán từ CRUD thành nghiệp vụ thật.

## `revenue_recognition_entries` — bút toán ghi nhận *(append-only)*  
> **[✅ GIỮ ⭐ đóng góp chính]**

| Cột | Kiểu | Giá trị nhận / Ý nghĩa | Mẫu |
|---|---|---|---|
| `schedule_id` | `BIGINT` | Thuộc kế hoạch nào | `451` |
| `registration_id` | `BIGINT` | Hợp đồng nào | `451` |
| `recognition_date` | `DATE` | Ghi nhận cho ngày nào | `2026-08-06` |
| `amount` | `NUMERIC(14,2)` | Số tiền. **Khác 0**, âm = bút toán đảo | `315000.00` |
| `units` | `INTEGER` | Bao nhiêu đơn vị | `1` |
| `revenue_category` | `VARCHAR(30)` | `MEMBERSHIP` \| `PT` \| `DAY_PASS` \| `OTHER` | `PT` |
| `source_type` | `VARCHAR(30)` | `DAILY_ACCRUAL` (job đêm) \| `PT_SESSION` \| `REVERSAL` (đảo) | `PT_SESSION` |
| `source_id` | `BIGINT` | ID nguồn | `9931` |

## `expense_categories` — nhóm chi phí  
> **[❌ BỎ → cột `expenses.category` dạng enum]**

| Cột | Kiểu | Giá trị nhận / Ý nghĩa | Mẫu |
|---|---|---|---|
| `code` | `VARCHAR(30)` | Mã nhóm. **Duy nhất** | `RENT` |
| `name` | `VARCHAR(120)` | Tên hiển thị | `Thuê mặt bằng` |
| `parent_id` | `BIGINT` | Nhóm cha (phân cấp) | `NULL` |
| `is_fixed_cost` | `BOOLEAN` | Chi phí cố định hay biến đổi | `TRUE` |
| `display_order` | `INTEGER` | Thứ tự trong báo cáo | `1` |

**Bộ nhóm chi phí chuẩn cho phòng gym:**

| code | name | is_fixed_cost | Số tiền mẫu/tháng |
|---|---|:---:|---:|
| `RENT` | Thuê mặt bằng | `TRUE` | 180.000.000 |
| `SALARY` | Lương nhân sự cố định | `TRUE` | 165.000.000 |
| `PT_SESSION_FEE` | Tiền công buổi PT | `FALSE` | 108.000.000 |
| `COMMISSION` | Hoa hồng sale | `FALSE` | 24.000.000 |
| `UTILITIES` | Điện, nước | `FALSE` | 55.000.000 |
| `DEPRECIATION` | Khấu hao thiết bị | `TRUE` | 45.000.000 |
| `MARKETING` | Marketing, quảng cáo | `FALSE` | 40.000.000 |
| `CLEANING` | Vệ sinh, vật tư tiêu hao | `TRUE` | 18.000.000 |
| `MAINTENANCE` | Bảo trì, sửa chữa | `FALSE` | 15.000.000 |
| `SOFTWARE` | Phần mềm, internet | `TRUE` | 12.000.000 |

## `expenses` — chi phí vận hành  
> **[✅ GIỮ]**

| Cột | Kiểu | Giá trị nhận / Ý nghĩa | Mẫu |
|---|---|---|---|
| `category_id` | `BIGINT` | Thuộc nhóm nào | `1` |
| `description` | `VARCHAR(255)` | Nội dung chi | `Thuê mặt bằng quý III/2026` |
| `amount` | `NUMERIC(14,2)` | Số tiền | `540000000.00` |
| `expense_date` | `DATE` | Ngày phát sinh/thanh toán | `2026-07-01` |
| `period_start` / `period_end` | `DATE` | **Kỳ chi phí** — để phân bổ theo tháng | `2026-07-01` / `2026-09-30` |
| `allocation_method` | `VARCHAR(20)` | `IMMEDIATE` \| `STRAIGHT_LINE` \| `DEPRECIATION` | `STRAIGHT_LINE` |
| `vendor` | `VARCHAR(150)` | Nhà cung cấp | `Công ty CP BĐS Minh Khang` |
| `invoice_ref` | `VARCHAR(80)` | Số hóa đơn của họ | `HD-2026-0712` |
| `attachment_url` | `VARCHAR(500)` | Ảnh chứng từ | `https://s3.../expenses/3390.pdf` |
| `payroll_run_id` | `BIGINT` | Nếu là chi phí lương | `NULL` |
| `work_order_id` | `BIGINT` | Nếu là chi phí sửa chữa | `NULL` |
| `equipment_item_id` | `BIGINT` | Nếu là khấu hao thiết bị | `NULL` |
| `status` | `VARCHAR(20)` | `DRAFT` \| `RECORDED` \| `APPROVED` \| `VOID` | `APPROVED` |
| `created_by` / `approved_by` | `BIGINT` | Ai nhập / ai duyệt | `7` / `1` |

> **`allocation_method` quan trọng thế nào:** thuê mặt bằng trả 540 triệu 1 lần cho cả quý. Nếu ghi hết vào tháng 7 thì tháng 7 lỗ nặng, tháng 8–9 lãi ảo. Với `STRAIGHT_LINE` + `period_start/end`, hệ thống phân bổ **180 triệu/tháng** cho tháng 7, 8, 9 → P&L mới phản ánh đúng.

## `payroll_rate_cards` — đơn giá công theo loại buổi tập ⭐  
> **[❌ BỎ → `system_settings`]**

| Cột | Kiểu | Giá trị nhận / Ý nghĩa | Mẫu |
|---|---|---|---|
| `trainer_level` | `VARCHAR(20)` | `JUNIOR` \| `SENIOR` \| `MASTER`. `NULL` = mọi cấp | `SENIOR` |
| `session_type` | `VARCHAR(20)` | Loại buổi tập (7 giá trị, gồm cả `CLASS`) | `PAID_PT` |
| `rate_per_session` | `NUMERIC(14,2)` | Trả PT bao nhiêu 1 buổi | `120000.00` |
| `valid_from` / `valid_to` | `DATE` | Áp dụng trong khoảng nào | `2026-01-01` / `NULL` |
| `created_by` | `BIGINT` | Ai đặt | `1` |

**Bảng giá mẫu đầy đủ:**

| trainer_level | session_type | rate_per_session | Ghi chú |
|---|---|---:|---|
| `JUNIOR` | `PAID_PT` | 90.000 | |
| `SENIOR` | `PAID_PT` | 120.000 | |
| `MASTER` | `PAID_PT` | 160.000 | |
| `NULL` | **`COMPLIMENTARY`** | **60.000** | ⭐ **Buổi hỗ trợ miễn phí — PT VẪN được trả công** |
| `NULL` | `TRIAL` | 80.000 | Buổi tập thử cho khách tiềm năng |
| `NULL` | `ORIENTATION` | 50.000 | Hướng dẫn hội viên mới làm quen máy |
| `NULL` | `ASSESSMENT` | 70.000 | Đo chỉ số cơ thể |
| `NULL` | `CLASS` | 200.000 | Dạy 1 buổi lớp nhóm |

## `commission_rules` — bậc hoa hồng  
> **[❌ BỎ → `system_settings`]**

| Cột | Kiểu | Giá trị nhận / Ý nghĩa | Mẫu |
|---|---|---|---|
| `role` | `VARCHAR(20)` | `SALE` \| `TRAINER` | `SALE` |
| `tier_from_amount` | `NUMERIC(14,2)` | Bậc từ doanh số | `50000000.00` |
| `tier_to_amount` | `NUMERIC(14,2)` | Đến doanh số. `NULL` = không giới hạn | `100000000.00` |
| `commission_percent` | `NUMERIC(5,2)` | Hưởng bao nhiêu % | `3.50` |
| `valid_from` / `valid_to` | `DATE` | Áp dụng khi nào | `2026-01-01` / `NULL` |

**Bậc thang mẫu cho Sale:**

| tier_from | tier_to | commission_percent |
|---:|---:|---:|
| 0 | 50.000.000 | 2,00% |
| 50.000.000 | 100.000.000 | 3,50% |
| 100.000.000 | `NULL` | 5,00% |

## `payroll_runs` — kỳ chạy lương  
> **[✅ GIỮ]**

| Cột | Kiểu | Giá trị nhận / Ý nghĩa | Mẫu |
|---|---|---|---|
| `period_year` | `SMALLINT` | Năm | `2026` |
| `period_month` | `SMALLINT` | Tháng, `1–12`. **`UNIQUE(year, month)`** | `8` |
| `status` | `VARCHAR(20)` | `DRAFT` → `REVIEW` → `APPROVED` → `PAID`; hoặc `VOID` | `APPROVED` |
| `total_amount` | `NUMERIC(14,2)` | Tổng lương phải trả | `297000000.00` |
| `calculated_at` | `TIMESTAMPTZ` | Chạy tính lúc nào | `2026-09-01 09:00:00+07` |
| `approved_by` / `approved_at` | | Kế toán chốt | `7` / `2026-09-04 16:00+07` |
| `paid_at` | `TIMESTAMPTZ` | Chuyển lương lúc nào | `2026-09-05 10:00:00+07` |
| `note` | `TEXT` | Ghi chú | `Đã điều chỉnh 2 khiếu nại của PT` |

> **`status = REVIEW` để làm gì:** sau khi tính xong, PT và Sale **xem chi tiết lương của mình trên app trong 3 ngày** và phản hồi nếu thấy sai. Minh bạch → giảm hẳn tranh chấp cuối tháng, vốn là vấn đề thật của ngành.

## `payroll_items` — chi tiết từng dòng lương ⭐  
> **[✅ GIỮ]**

| Cột | Kiểu | Giá trị nhận / Ý nghĩa | Mẫu |
|---|---|---|---|
| `payroll_run_id` | `BIGINT` | Thuộc kỳ lương nào | `8` |
| `person_id` | `BIGINT` | Của ai (dùng `persons` để gộp được người kiêm nhiệm) | `18` |
| `trainer_id` / `staff_id` | `BIGINT` | Vai trò nào phát sinh dòng này | `5` / `NULL` |
| `item_type` | `VARCHAR(30)` | **8 loại** — xem dưới | `SESSION_FEE` |
| `description` | `VARCHAR(255)` | Diễn giải hiện trên app | `Buổi PT ngày 06/08 với Nguyễn Văn An` |
| `quantity` | `NUMERIC(10,2)` | Số lượng | `1.00` |
| `unit_amount` | `NUMERIC(14,2)` | Đơn giá | `120000.00` |
| `amount` | `NUMERIC(14,2)` | Thành tiền. Âm nếu là khấu trừ | `120000.00` |
| `source_type` / `source_id` | | Sinh từ đâu — **truy vết được** | `PT_SESSION` / `9931` |

### `item_type` — 8 loại

| Giá trị | Nghĩa | Dấu |
|---|---|:---:|
| `BASE_SALARY` | Lương cứng | + |
| `SESSION_FEE` | Tiền công buổi tập (1 dòng/buổi) | + |
| `SALES_COMMISSION` | Hoa hồng bán gói | + |
| `KPI_BONUS` | Thưởng KPI (số buổi, điểm đánh giá, tỷ lệ gia hạn) | + |
| `ALLOWANCE` | Phụ cấp (xăng xe, ăn trưa) | + |
| `DEDUCTION` | Khấu trừ (nghỉ không phép) | − |
| `PENALTY` | Phạt (hủy buổi muộn, đến muộn) | − |
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

> **Toàn bộ dữ liệu này sinh tự động từ `pt_sessions` — không nhập tay dòng nào.** Đó là lý do bảng lương đúng 100%. PT mở app xem được từng dòng, bấm vào dòng nào cũng thấy buổi tập gốc.
>
> **Chú ý dòng in đậm:** 6 buổi hỗ trợ miễn phí, hội viên **không bị trừ buổi nào**, nhưng PT **vẫn nhận 360.000đ** — đúng yêu cầu của thầy.

## `accounting_periods` — kỳ kế toán *(Should have)*  
> **[❌ BỎ → bỏ tính năng khóa sổ]**

| Cột | Kiểu | Giá trị nhận / Ý nghĩa | Mẫu |
|---|---|---|---|
| `period_year` / `period_month` | `SMALLINT` | Kỳ nào. **`UNIQUE`** | `2026` / `8` |
| `status` | `VARCHAR(20)` | `OPEN` (đang ghi) \| `CLOSING` (đang chốt) \| `CLOSED` (**khóa, cấm sửa**) | `CLOSED` |
| `closed_by` / `closed_at` | | Ai khóa, lúc nào | `7` / `2026-09-05 17:00+07` |

## `financial_reports` — báo cáo đã sinh  
> **[❌ BỎ → sinh báo cáo tại chỗ, không lưu]**

| Cột | Kiểu | Giá trị nhận / Ý nghĩa | Mẫu |
|---|---|---|---|
| `report_type` | `VARCHAR(30)` | `PNL` \| `CASHFLOW` \| `DEFERRED_REVENUE` \| `SALES` \| `PAYROLL` | `PNL` |
| `period_from` / `period_to` | `DATE` | Kỳ báo cáo | `2026-08-01` / `2026-08-31` |
| `data` | `JSONB` | Số liệu đã tổng hợp | `{"revenue":812000000,"expense":662000000,...}` |
| `narrative` | `TEXT` | Phần diễn giải bằng lời | `Doanh thu tháng 8 tăng 6,2% so với tháng 7...` |
| `narrative_source` | `VARCHAR(20)` | `HUMAN` \| `LLM` — **minh bạch nguồn gốc** | `LLM` |
| `file_url` | `VARCHAR(500)` | File PDF/Excel đã xuất | `https://s3.../reports/pnl-2026-08.pdf` |
| `generated_by` / `generated_at` | | Ai sinh, lúc nào | `7` / `2026-09-01 09:15+07` |

> **`narrative_source` vì sao cần:** nếu LLM viết phần diễn giải thì phải ghi rõ. LLM **chỉ viết lời từ số liệu đã có trong `data`**, tuyệt đối **không tự tính số** — số do hệ thống tính.

---
---

# NHÓM 9 — BÁN HÀNG (CRM)

## `leads` — khách tiềm năng  
> **[✅ GIỮ (thêm `person_id`, gánh nhật ký chăm sóc)]**

| Cột | Kiểu | Giá trị nhận / Ý nghĩa | Mẫu |
|---|---|---|---|
| `full_name` | `VARCHAR(150)` | Tên khách | `Đỗ Thị Hương` |
| `phone` | `VARCHAR(20)` | SĐT. **Bắt buộc** | `0938112233` |
| `email` | `VARCHAR(150)` | Email | `NULL` |
| `source` | `VARCHAR(30)` | `FB_ADS` \| `HOTLINE` \| `WALK_IN` \| `REFERRAL` \| `GOOGLE` \| `EVENT` | `FB_ADS` |
| `interest` | `VARCHAR(60)` | Quan tâm gì | `Giảm cân, gói 6 tháng` |
| `assigned_to` | `BIGINT` | Sale nào phụ trách | `2` |
| `stage` | `VARCHAR(20)` | **7 giai đoạn** — xem dưới | `TRIAL_DONE` |
| `lost_reason` | `VARCHAR(30)` | `PRICE` \| `LOCATION` \| `COMPETITOR` \| `NOT_READY` \| `NO_RESPONSE` | `NULL` |
| `converted_member_id` | `BIGINT` | Trở thành hội viên nào | `NULL` |
| `next_follow_up` | `DATE` | Hẹn liên hệ lại ngày | `2026-08-05` |

### `stage` — phễu bán hàng 7 bước

```
NEW → CONTACTED → TRIAL_BOOKED → TRIAL_DONE → NEGOTIATING → WON
                                                          ↘ LOST (+ lost_reason)
```

> **`lost_reason` để làm gì:** thống kê cuối tháng thấy 40% mất khách vì `PRICE` thì cần xem lại chính sách giá; nếu vì `NO_RESPONSE` thì vấn đề nằm ở quy trình chăm sóc của Sale. Đây là dữ liệu ra quyết định, không phải ghi cho có.

## `lead_activities` — nhật ký chăm sóc  
> **[❌ BỎ → `leads.last_contact_note`]**

| Cột | Kiểu | Giá trị nhận / Ý nghĩa | Mẫu |
|---|---|---|---|
| `lead_id` | `BIGINT` | Của khách nào | `88` |
| `activity_type` | `VARCHAR(20)` | `CALL` \| `SMS` \| `ZALO` \| `EMAIL` \| `MEETING` \| `TRIAL` | `CALL` |
| `outcome` | `VARCHAR(30)` | Kết quả | `INTERESTED` |
| `note` | `TEXT` | Nội dung trao đổi | `Khách quan tâm gói 6 tháng, xin thêm thời gian suy nghĩ` |
| `performed_by` | `BIGINT` | Ai thực hiện | `35` |
| `performed_at` | `TIMESTAMPTZ` | Lúc nào | `2026-08-02 15:20:00+07` |

## `quotes` — báo giá  
> **[❌ BỎ → `registrations` với status=DRAFT]**

| Cột | Kiểu | Giá trị nhận / Ý nghĩa | Mẫu |
|---|---|---|---|
| `quote_no` | `VARCHAR(30)` | Số báo giá. **Duy nhất** | `QT-2026-000210` |
| `lead_id` | `BIGINT` | Báo giá cho khách tiềm năng nào | `88` |
| `member_id` | `BIGINT` | Hoặc cho hội viên cũ (gia hạn) | `NULL` |
| `created_by` | `BIGINT` | Sale nào tạo (`staff.id`) | `2` |
| `subtotal` | `NUMERIC(14,2)` | Tổng trước giảm | `3000000.00` |
| `discount_amount` | `NUMERIC(14,2)` | Giảm bao nhiêu tiền | `450000.00` |
| `discount_percent` | `NUMERIC(5,2)` | Giảm bao nhiêu % | `15.00` |
| `total_amount` | `NUMERIC(14,2)` | Giá cuối | `2550000.00` |
| `requires_approval` | `BOOLEAN` | Vượt hạn mức → cần duyệt | `TRUE` |
| `approved_by` / `approved_at` | | Ai duyệt, lúc nào | `1` / `2026-08-03 09:10+07` |
| `status` | `VARCHAR(20)` | **7 trạng thái** — xem dưới | `CONVERTED` |
| `valid_until` | `DATE` | Báo giá có giá trị đến | `2026-08-10` |
| `converted_registration_id` | `BIGINT` | Chuyển thành hợp đồng nào | `452` |

| `status` | Nghĩa |
|---|---|
| `DRAFT` | Sale đang soạn |
| `PENDING_APPROVAL` | Chiết khấu vượt hạn mức, chờ Admin duyệt |
| `SENT` | Đã gửi khách |
| `ACCEPTED` | Khách đồng ý |
| `REJECTED` | Khách từ chối / Admin không duyệt |
| `EXPIRED` | Quá `valid_until` |
| `CONVERTED` | Đã thành hợp đồng |

> **Luồng kiểm soát chiết khấu:** Sale giảm 15% > `requires_approval_above = 10%` của vai trò `SALE` → hệ thống tự đặt `requires_approval = TRUE`, `status = PENDING_APPROVAL`, gửi thông báo cho Admin. Sale **không thể** tự chuyển sang `ACCEPTED` khi chưa có `approved_by`.

## `quote_items` — dòng chi tiết báo giá  
> **[❌ BỎ → theo `quotes`]**

| Cột | Kiểu | Ý nghĩa | Mẫu |
|---|---|---|---|
| `quote_id` | `BIGINT` | Thuộc báo giá nào | `210` |
| `membership_id` | `BIGINT` | Gói nào | `4` |
| `list_price` | `NUMERIC(14,2)` | Giá niêm yết | `3000000.00` |
| `discount` | `NUMERIC(14,2)` | Giảm | `450000.00` |
| `final_price` | `NUMERIC(14,2)` | Cột tự tính = `list_price − discount` | `2550000.00` |
| `promotion_id` | `BIGINT` | Áp KM nào | `NULL` |

---
---

# NHÓM 10 — BÀI TẬP & CHỈ SỐ CƠ THỂ

## `exercises` — thư viện bài tập  
> **[✅ GIỮ]**

| Cột | Kiểu | Giá trị nhận / Ý nghĩa | Mẫu |
|---|---|---|---|
| `code` | `VARCHAR(60)` | Mã bài. **Duy nhất** | `barbell-bench-press` |
| `name_vi` | `VARCHAR(200)` | **Tên tiếng Việt** | `Đẩy ngực với tạ đòn` |
| `name_en` | `VARCHAR(200)` | Tên gốc tiếng Anh | `Barbell Bench Press` |
| `muscle_group` | `VARCHAR(40)` | `CHEST` \| `BACK` \| `LEGS` \| `SHOULDERS` \| `ARMS` \| `CORE` \| `CARDIO` | `CHEST` |
| `secondary_muscles` | `VARCHAR(200)` | Nhóm cơ phụ | `TRICEPS, SHOULDERS` |
| `equipment` | `VARCHAR(60)` | `BARBELL` \| `DUMBBELL` \| `MACHINE` \| `BODYWEIGHT` \| `CABLE` \| `KETTLEBELL` | `BARBELL` |
| `difficulty` | `VARCHAR(20)` | `BEGINNER` \| `INTERMEDIATE` \| `ADVANCED` | `INTERMEDIATE` |
| `instructions` | `TEXT` | Hướng dẫn thực hiện | `1. Nằm ngửa trên ghế, hai chân đặt vững...` |
| `media_url` | `VARCHAR(500)` | Ảnh/GIF minh họa | `https://s3.../exercises/bench-press.jpg` |
| `source` | `VARCHAR(60)` | `FREE_EXERCISE_DB` \| `WGER` \| `CUSTOM` | `FREE_EXERCISE_DB` |
| `license` | `VARCHAR(60)` | **Giấy phép — ghi rõ để tuân thủ bản quyền** | `Unlicense` |
| `is_reviewed` | `BOOLEAN` | **Bản dịch đã có người rà soát chưa** | `TRUE` |

> **`is_reviewed` quan trọng:** ~870 bài tập được dịch sang tiếng Việt bằng LLM (Batch API, tốn ~1 USD một lần). Bản dịch máy **bắt buộc có người đọc lại** trước khi phát hành. Bài `is_reviewed = FALSE` **không hiển thị** cho hội viên.

## `workout_templates` — giáo án mẫu  
> **[❌ BỎ → `workout_plans.is_template`]**

| Cột | Kiểu | Giá trị nhận / Ý nghĩa | Mẫu |
|---|---|---|---|
| `name` | `VARCHAR(150)` | Tên giáo án | `Tăng cơ 8 tuần cho người mới` |
| `goal` | `VARCHAR(30)` | `LOSE_FAT` \| `GAIN_MUSCLE` \| `STRENGTH` \| `ENDURANCE` | `GAIN_MUSCLE` |
| `level` | `VARCHAR(20)` | `BEGINNER` \| `INTERMEDIATE` \| `ADVANCED` | `BEGINNER` |
| `duration_weeks` | `SMALLINT` | Kéo dài mấy tuần | `8` |
| `days_per_week` | `SMALLINT` | Mấy buổi/tuần | `4` |
| `description` | `TEXT` | Mô tả | `Chương trình Upper/Lower split...` |
| `created_by` | `BIGINT` | Ai soạn | `30` |
| `is_public` | `BOOLEAN` | Mọi PT dùng được hay riêng người soạn | `TRUE` |

## `workout_template_items` — bài tập trong giáo án mẫu  
> **[❌ BỎ → `workout_plan_items`]**

| Cột | Kiểu | Giá trị nhận / Ý nghĩa | Mẫu |
|---|---|---|---|
| `template_id` | `BIGINT` | Thuộc giáo án nào | `3` |
| `day_index` | `SMALLINT` | Ngày thứ mấy trong chu kỳ | `1` |
| `order_index` | `SMALLINT` | Bài thứ mấy trong ngày | `1` |
| `exercise_id` | `BIGINT` | Bài tập nào | `112` |
| `sets` | `SMALLINT` | Số hiệp | `4` |
| `reps` | `VARCHAR(20)` | Số lần — **kiểu chữ** vì có dạng khoảng | `8-12` |
| `rest_sec` | `SMALLINT` | Nghỉ giữa hiệp (giây) | `90` |
| `note` | `VARCHAR(255)` | Ghi chú kỹ thuật | `Giữ lưng phẳng, khuỷu tay 45°` |

> **`reps` là `VARCHAR` chứ không phải số** vì thực tế PT ghi `"8-12"`, `"AMRAP"` (tối đa có thể), `"30 giây"`.

## `workout_plans` — giáo án gán cho hội viên  
> **[✅ GIỮ (gánh giáo án mẫu)]**

| Cột | Kiểu | Giá trị nhận / Ý nghĩa | Mẫu |
|---|---|---|---|
| `member_id` | `BIGINT` | Của hội viên nào | `123` |
| `trainer_id` | `BIGINT` | PT nào soạn | `5` |
| `template_id` | `BIGINT` | Dựa trên mẫu nào. `NULL` = soạn mới hoàn toàn | `3` |
| `name` | `VARCHAR(150)` | Tên | `Giáo án tăng cơ - An - T8/2026` |
| `start_date` / `end_date` | `DATE` | Áp dụng từ / đến | `2026-08-01` / `2026-09-26` |
| `status` | `VARCHAR(20)` | `ACTIVE` \| `COMPLETED` \| `PAUSED` \| `CANCELLED` | `ACTIVE` |
| `ai_generated` | `BOOLEAN` | **Do AI sinh bản nháp?** | `TRUE` |
| `reviewed_by` | `BIGINT` | **PT đã duyệt** — bắt buộc nếu `ai_generated` | `30` |

> **Ràng buộc ở tầng CSDL:** `CHECK (NOT ai_generated OR reviewed_by IS NOT NULL)` — giáo án do AI sinh **không thể** lưu nếu chưa có PT duyệt. Đây là ranh giới an toàn: AI hỗ trợ, con người chịu trách nhiệm.

## `workout_plan_items` — bài tập trong giáo án đã gán  
> **[✅ GIỮ]**

| Cột | Kiểu | Giá trị nhận / Ý nghĩa | Mẫu |
|---|---|---|---|
| `plan_id` | `BIGINT` | Thuộc giáo án nào | `77` |
| `day_index` / `order_index` | `SMALLINT` | Ngày thứ mấy / bài thứ mấy | `1` / `1` |
| `exercise_id` | `BIGINT` | Bài tập nào | `112` |
| `sets` | `SMALLINT` | Số hiệp | `4` |
| `reps` | `VARCHAR(20)` | Số lần | `8-12` |
| `target_weight_kg` | `NUMERIC(6,2)` | Mức tạ mục tiêu | `60.00` |
| `rest_sec` | `SMALLINT` | Nghỉ (giây) | `90` |
| `note` | `VARCHAR(255)` | Ghi chú riêng cho hội viên này | `An đau lưng, giảm tạ 20% tuần đầu` |

## `workout_logs` — hội viên ghi lại buổi tập  
> **[✅ GIỮ]**

| Cột | Kiểu | Giá trị nhận / Ý nghĩa | Mẫu |
|---|---|---|---|
| `plan_item_id` | `BIGINT` | Theo bài nào trong giáo án | `340` |
| `member_id` | `BIGINT` | Ai tập | `123` |
| `exercise_id` | `BIGINT` | Bài gì | `112` |
| `pt_session_id` | `BIGINT` | Trong buổi PT nào. `NULL` = tự tập | `9931` |
| `performed_at` | `TIMESTAMPTZ` | Tập lúc nào | `2026-08-06 19:15:00+07` |
| `sets_done` | `SMALLINT` | Làm được mấy hiệp | `4` |
| `reps_done` | `VARCHAR(40)` | Số lần từng hiệp | `12,10,10,8` |
| `weight_kg` | `NUMERIC(6,2)` | Mức tạ thực tế | `62.50` |
| `rpe` | `SMALLINT` | Mức gắng sức tự đánh giá `1–10` | `8` |
| `note` | `VARCHAR(255)` | Ghi chú | `Tăng 2,5kg so với tuần trước` |

> **Đây là dữ liệu để hội viên thấy mình tiến bộ** — biểu đồ "mức tạ bench press theo tuần" là thứ tạo động lực mạnh nhất, và cũng là dữ liệu đầu vào cho điểm rủi ro bỏ tập.

## `body_metrics` — chỉ số cơ thể  
> **[✅ GIỮ]**

| Cột | Kiểu | Giá trị nhận / Ý nghĩa | Mẫu |
|---|---|---|---|
| `member_id` | `BIGINT` | Của ai | `123` |
| `measured_at` | `TIMESTAMPTZ` | Đo lúc nào | `2026-08-01 18:00:00+07` |
| `measured_by` | `BIGINT` | Ai đo. `NULL` = hội viên tự nhập | `30` |
| `source` | `VARCHAR(20)` | `MANUAL` \| `INBODY_OCR` \| `DEVICE_SYNC` | `INBODY_OCR` |
| `height_cm` | `NUMERIC(5,2)` | Chiều cao. Hợp lệ `80–250` | `175.00` |
| `weight_kg` | `NUMERIC(5,2)` | Cân nặng. Hợp lệ `20–300` | `72.50` |
| `body_fat_pct` | `NUMERIC(4,2)` | % mỡ. Hợp lệ `1–70` | `18.40` |
| `muscle_mass_kg` | `NUMERIC(5,2)` | Khối lượng cơ | `33.20` |
| `visceral_fat` | `NUMERIC(4,1)` | Mỡ nội tạng | `7.0` |
| `bmr_kcal` | `INTEGER` | Trao đổi chất cơ bản | `1659` |
| `bmi` | `NUMERIC(5,2)` | **Cột TỰ TÍNH** = `kg / (m)²`, không nhập tay | `23.67` |
| `chest_cm` / `waist_cm` / `hip_cm` / `arm_cm` / `thigh_cm` | `NUMERIC(5,2)` | Số đo các vòng | `98.00` / `84.00` / `95.00` / `33.50` / `56.00` |
| `photo_url` | `VARCHAR(500)` | Ảnh phiếu InBody hoặc ảnh tiến trình | `https://s3.../inbody/123-20260801.jpg` |
| `ocr_confidence` | `NUMERIC(4,3)` | Độ tin cậy đọc máy `0–1` | `0.940` |
| `confirmed_by_user` | `BOOLEAN` | **Người dùng đã xác nhận số liệu chưa** | `TRUE` |
| `note` | `VARCHAR(255)` | Ghi chú | `Đo sau khi nhịn ăn 3h` |

### Các chỉ số tính toán

```
BMI  = weight_kg / (height_cm/100)²             → 72,5 / 1,75² = 23,67
BMR  (Mifflin-St Jeor, nam) = 10×kg + 6,25×cm − 5×tuổi + 5
     = 10×72,5 + 6,25×175 − 5×28 + 5 = 1.658,75 kcal
TDEE = BMR × hệ số vận động (ít 1,2 · nhẹ 1,375 · vừa 1,55 · nhiều 1,725)
WHR  = waist_cm / hip_cm                         → 84/95 = 0,88
```

### Ngưỡng BMI cho người châu Á (WHO Asia-Pacific) — **khác ngưỡng quốc tế**

| Phân loại | BMI châu Á | BMI quốc tế |
|---|---|---|
| Thiếu cân | < 18,5 | < 18,5 |
| Bình thường | 18,5 – 22,9 | 18,5 – 24,9 |
| **Thừa cân** | **23,0 – 24,9** | 25,0 – 29,9 |
| Béo phì độ I | 25,0 – 29,9 | 30,0 – 34,9 |
| Béo phì độ II | ≥ 30,0 | ≥ 35,0 |

> BMI 23,67 của An là **"thừa cân" theo ngưỡng châu Á** nhưng vẫn "bình thường" theo ngưỡng quốc tế. Phải dùng đúng ngưỡng châu Á.
>
> ⚠️ **Ranh giới đạo đức bắt buộc nêu trong báo cáo:** hệ thống **chỉ hiển thị số liệu và tham chiếu ngưỡng WHO**, **không chẩn đoán, không kê chế độ dinh dưỡng cá nhân hóa**. Mọi màn hình phải có dòng *"Thông tin mang tính tham khảo. Vui lòng tham vấn bác sĩ hoặc chuyên gia dinh dưỡng trước khi thay đổi chế độ tập luyện/ăn uống."*
>
> **`confirmed_by_user` với `INBODY_OCR`:** hội viên chụp ảnh phiếu InBody → LLM đọc số → **hiển thị form đã điền sẵn, ô nào `ocr_confidence` thấp thì tô vàng** → người dùng kiểm tra, sửa nếu cần → mới lưu. AI không bao giờ ghi thẳng vào CSDL.

---
---

# NHÓM 11 — PHẢN HỒI & GIỮ CHÂN

## `feedbacks` — phản ánh của hội viên  
> **[✅ GIỮ (gánh đánh giá PT + phiếu sửa chữa)]**

| Cột | Kiểu | Giá trị nhận / Ý nghĩa | Mẫu |
|---|---|---|---|
| `member_id` | `BIGINT` | Ai phản ánh | `123` |
| `feedback_type` | `VARCHAR(20)` | `TRAINER` \| `FACILITY` \| `HYGIENE` \| `SERVICE` \| `GENERAL` | `FACILITY` |
| `target_trainer_id` | `BIGINT` | Về PT nào | `NULL` |
| `target_equipment_id` | `BIGINT` | Về thiết bị nào | `12` |
| `target_room_id` | `BIGINT` | Về phòng nào | `NULL` |
| `rating` | `SMALLINT` | Điểm `1–5` (nếu là đánh giá) | `NULL` |
| `title` | `VARCHAR(200)` | Tiêu đề | `Máy chạy bộ TM-003 kêu to` |
| `content` | `TEXT` | Nội dung. **Bắt buộc** | `Chạy trên 8km/h thì máy rung và kêu rất to` |
| `photo_url` | `VARCHAR(500)` | Ảnh kèm theo | `https://s3.../feedback/882.jpg` |
| `severity` | `VARCHAR(10)` | `LOW` \| `MEDIUM` \| `HIGH` | `MEDIUM` |
| `status` | `VARCHAR(20)` | `OPEN` \| `IN_PROGRESS` \| `RESOLVED` \| `CLOSED` \| `REJECTED` | `RESOLVED` |
| `assigned_to` | `BIGINT` | Giao cho ai xử lý | `15` |
| `resolution` | `TEXT` | Đã xử lý thế nào | `Đã thay dây curoa ngày 03/08` |
| `resolved_at` | `TIMESTAMPTZ` | Xong lúc nào | `2026-08-03 10:00:00+07` |
| `sla_due_at` | `TIMESTAMPTZ` | Hạn phải xử lý xong | `2026-08-04 19:20:00+07` |
| `is_anonymous` | `BOOLEAN` | Ẩn danh (dám nói thật hơn) | `FALSE` |

### Định tuyến tự động theo `feedback_type`

| Loại | Hệ thống tự động làm gì |
|---|---|
| `FACILITY` | → Tạo `maintenance_work_orders` · đổi `equipment_items.status = NEEDS_REPAIR` · ẩn thiết bị khỏi lịch lớp |
| `TRAINER` | → Ghi vào `trainer_ratings` → ảnh hưởng `KPI_BONUS` trong lương. Nếu ≤ 2 sao → tạo task cho Admin xử lý trong 24h |
| `HYGIENE` | → Task cho quản lý ca |
| `SERVICE` / `GENERAL` | → Hàng đợi Admin |

> **Điểm tạo niềm tin:** khi xử lý xong, hệ thống **gửi thông báo ngược lại cho hội viên đã báo** — *"Máy chạy bộ TM-003 đã được sửa. Cảm ơn bạn đã phản ánh!"*. Đây là thứ khiến hội viên tiếp tục phản ánh thay vì im lặng rồi bỏ đi.

## `trainer_ratings` — đánh giá PT  
> **[❌ BỎ → `feedbacks` đã có `rating`]**

| Cột | Kiểu | Giá trị nhận / Ý nghĩa | Mẫu |
|---|---|---|---|
| `trainer_id` | `BIGINT` | Đánh giá PT nào | `5` |
| `member_id` | `BIGINT` | Ai đánh giá | `123` |
| `pt_session_id` | `BIGINT` | Sau buổi tập nào. **`UNIQUE(session, member)`** | `9931` |
| `rating` | `SMALLINT` | `1–5` sao. **Bắt buộc** | `5` |
| `comment` | `TEXT` | Nhận xét | `Anh Bình hướng dẫn rất kỹ, sửa tư thế liên tục` |

`UNIQUE(pt_session_id, member_id)` → mỗi buổi tập chỉ đánh giá được **1 lần**, chống spam điểm.

## `notification_templates` — mẫu thông báo *(Should have)*  
> **[❌ BỎ]**

| Cột | Kiểu | Giá trị nhận / Ý nghĩa | Mẫu |
|---|---|---|---|
| `code` | `VARCHAR(50)` | Mã mẫu. **Duy nhất** | `MEMBERSHIP_EXPIRING_7D` |
| `channel` | `VARCHAR(20)` | `PUSH` \| `EMAIL` \| `SMS` \| `IN_APP` | `PUSH` |
| `title_tpl` | `VARCHAR(200)` | Mẫu tiêu đề, có biến | `Gói tập sắp hết hạn` |
| `body_tpl` | `TEXT` | Mẫu nội dung | `Chào {{name}}, gói {{package}} còn {{days}} ngày...` |
| `is_active` | `BOOLEAN` | Đang bật không | `TRUE` |

## `notifications` — thông báo đã gửi *(Should have)*  
> **[❌ BỎ]**

| Cột | Kiểu | Giá trị nhận / Ý nghĩa | Mẫu |
|---|---|---|---|
| `user_id` | `BIGINT` | Gửi cho ai | `30` |
| `template_id` | `BIGINT` | Dùng mẫu nào | `4` |
| `channel` | `VARCHAR(20)` | Qua kênh nào | `PUSH` |
| `title` | `VARCHAR(200)` | Tiêu đề đã điền biến | `Gói tập sắp hết hạn` |
| `body` | `TEXT` | Nội dung đã điền biến | `Chào An, gói PT 12 buổi còn 7 ngày...` |
| `deep_link` | `VARCHAR(255)` | Bấm vào mở màn hình nào | `gymapp://registrations/451` |
| `status` | `VARCHAR(20)` | `PENDING` \| `SENT` \| `FAILED` \| `READ` | `READ` |
| `read_at` / `sent_at` | `TIMESTAMPTZ` | Đọc / gửi lúc nào | `2027-01-04 20:11+07` |

## `churn_scores` — điểm rủi ro bỏ tập *(Should have)*  
> **[⏳ CHƯA QUYẾT]**

| Cột | Kiểu | Giá trị nhận / Ý nghĩa | Mẫu |
|---|---|---|---|
| `member_id` | `BIGINT` | Của ai | `123` |
| `scored_date` | `DATE` | Tính cho ngày nào. **`UNIQUE(member, date)`** | `2026-09-15` |
| `score` | `NUMERIC(5,4)` | Điểm `0.0000–1.0000` | `0.7200` |
| `risk_level` | `VARCHAR(10)` | `LOW` (<0,4) \| `MEDIUM` (0,4–0,7) \| `HIGH` (>0,7) | `HIGH` |
| `factors` | `JSONB` | **Đóng góp của từng yếu tố → GIẢI THÍCH ĐƯỢC** | xem dưới |
| `model_version` | `VARCHAR(20)` | Phiên bản mô hình | `rule-v1.2` |

**Ví dụ `factors`:**

```json
{
  "days_since_last_visit":  {"value": 24, "weight": 0.35, "contribution": 0.28},
  "frequency_drop_4w":      {"value": 0.62,"weight": 0.25, "contribution": 0.16},
  "pt_sessions_left":       {"value": 1,  "weight": 0.20, "contribution": 0.14},
  "expiring_in_days":       {"value": 12, "weight": 0.15, "contribution": 0.10},
  "unresolved_complaint":   {"value": true,"weight":0.05, "contribution": 0.04}
}
```

> **Vì sao lưu `factors` chứ không chỉ lưu điểm số:** PT nhận task chăm sóc cần biết **vì sao** hội viên này rủi ro cao để nói đúng chuyện — *"24 ngày rồi anh chưa đến, còn 1 buổi PT và gói hết hạn sau 12 ngày"* thuyết phục hơn nhiều so với *"hệ thống báo anh có nguy cơ bỏ tập"*. Mô hình **giải thích được** cũng dễ bảo vệ trước hội đồng hơn hộp đen.

## `retention_tasks` — việc cần làm để giữ chân *(Should have)*  
> **[⏳ CHƯA QUYẾT]**

| Cột | Kiểu | Giá trị nhận / Ý nghĩa | Mẫu |
|---|---|---|---|
| `member_id` | `BIGINT` | Chăm sóc ai | `123` |
| `churn_score_id` | `BIGINT` | Sinh từ điểm rủi ro nào | `5521` |
| `task_type` | `VARCHAR(30)` | `CHECK_IN_CALL` \| `RENEWAL_OFFER` \| `PT_FOLLOWUP` | `PT_FOLLOWUP` |
| `assigned_to` | `BIGINT` | Giao cho ai | `30` (PT phụ trách) |
| `priority` | `VARCHAR(10)` | `LOW` \| `MEDIUM` \| `HIGH` | `HIGH` |
| `suggested_action` | `TEXT` | Gợi ý cụ thể | `Gọi hỏi thăm, đề xuất đổi khung giờ tập buổi sáng` |
| `status` | `VARCHAR(20)` | `OPEN` \| `IN_PROGRESS` \| `DONE` \| `SKIPPED` | `DONE` |
| `outcome` | `VARCHAR(30)` | `CONTACTED` \| `NO_ANSWER` \| `RENEWED` \| `REFUSED` \| `WRONG_NUMBER` | `RENEWED` |
| `outcome_note` | `TEXT` | Chi tiết | `Anh An bận đổi việc, đã đổi sang khung 6h sáng và gia hạn 6 tháng` |
| `is_control_group` | `BOOLEAN` | **Nhóm đối chứng — KHÔNG can thiệp** | `FALSE` |
| `due_date` | `DATE` | Hạn thực hiện | `2026-09-18` |
| `completed_at` | `TIMESTAMPTZ` | Xong lúc nào | `2026-09-17 14:30+07` |

> **`is_control_group` — đây là thiết kế thí nghiệm, không phải cột thừa:** trong số hội viên rủi ro cao, hệ thống **cố ý không can thiệp** với ~20% (nhóm đối chứng). Cuối kỳ so tỷ lệ gia hạn của nhóm **được can thiệp** với nhóm **không can thiệp** → chứng minh được bằng **số liệu định lượng** rằng vòng lặp giữ chân thật sự có tác dụng, chứ không phải hội viên đó dù sao cũng gia hạn. Đây là bằng chứng mạnh cho đóng góp ĐG4 trong báo cáo.

---
---

# NHÓM 12 — HỆ THỐNG

## `system_settings` — cấu hình thay vì hard-code ⭐  
> **[✅ GIỮ]**

| Cột | Kiểu | Ý nghĩa | Mẫu |
|---|---|---|---|
| `key` | `VARCHAR(80)` | Tên tham số. **Là khóa chính** | `freeze.max_days_per_year` |
| `value` | `JSONB` | Giá trị (số, chuỗi, mảng, object) | `30` |
| `description` | `VARCHAR(255)` | Giải thích cho Admin | `Số ngày bảo lưu tối đa trong 1 năm hợp đồng` |
| `updated_by` / `updated_at` | | Ai sửa, lúc nào | `1` / `2026-07-01 09:00+07` |

**Danh sách cấu hình đầy đủ:**

| key | value mẫu | Ý nghĩa |
|---|---|---|
| `gym.name` | `"Fitness Center ABC"` | Tên phòng gym |
| `gym.address` | `"123 Nguyễn Trãi, Thanh Xuân, Hà Nội"` | Địa chỉ |
| `gym.opening_time` | `"05:30"` | Giờ mở cửa |
| `gym.closing_time` | `"22:30"` | Giờ đóng cửa |
| `freeze.max_days_per_year` | `30` | Bảo lưu tối đa 30 ngày/năm |
| `freeze.max_times_per_year` | `2` | Tối đa 2 lần/năm |
| `freeze.min_advance_days` | `3` | Phải báo trước 3 ngày |
| `freeze.min_package_days` | `90` | Chỉ gói ≥ 90 ngày được bảo lưu |
| `checkin.antipassback_minutes` | `30` | Cảnh báo nếu check-in lại trong 30 phút |
| `checkin.qr_period_sec` | `30` | Mã QR đổi mỗi 30 giây |
| `booking.late_cancel_hours` | `4` | Hủy dưới 4h coi là hủy muộn, mất buổi |
| `booking.auto_confirm_hours` | `24` | Hội viên không xác nhận trong 24h thì tự duyệt |
| `pt.max_complimentary_per_month` | `10` | Mỗi PT tối đa 10 buổi miễn phí/tháng |
| `churn.weights` | `{"days_since_visit":0.35,...}` | Trọng số tính điểm rủi ro |
| `churn.high_threshold` | `0.70` | Trên mức này là rủi ro cao |
| `llm.monthly_budget_usd` | `60` | Ngân sách LLM/tháng |
| `llm.max_chat_per_member_per_day` | `20` | Mỗi hội viên tối đa 20 câu hỏi/ngày |

> **Vì sao không hard-code:** khi phòng gym đổi chính sách bảo lưu từ 30 ngày lên 45 ngày, Admin sửa 1 dòng cấu hình — **không cần lập trình viên, không cần deploy lại**. Đây là điểm phân biệt hệ thống làm nghiêm túc với đồ án làm cho xong.

## `outbox_events` — đảm bảo không mất sự kiện ⭐  
> **[❌ BỎ → monolith 1 tiến trình, transaction là đủ]**

| Cột | Kiểu | Giá trị nhận / Ý nghĩa | Mẫu |
|---|---|---|---|
| `aggregate_type` | `VARCHAR(60)` | Loại đối tượng | `registrations` |
| `aggregate_id` | `BIGINT` | ID đối tượng | `451` |
| `event_type` | `VARCHAR(80)` | Loại sự kiện | `RegistrationActivated` |
| `payload` | `JSONB` | Nội dung sự kiện | `{"registrationId":451,"memberId":123,"sessions":12}` |
| `status` | `VARCHAR(20)` | `PENDING` \| `PUBLISHED` \| `FAILED` \| `DEAD` (bỏ sau nhiều lần lỗi) | `PUBLISHED` |
| `attempts` | `SMALLINT` | Đã thử phát mấy lần | `1` |
| `last_error` | `TEXT` | Lỗi lần cuối | `NULL` |
| `published_at` | `TIMESTAMPTZ` | Phát thành công lúc nào | `2026-07-15 10:28:03+07` |

> **Bảng này giải quyết vấn đề gì:** khi thu tiền xong, hệ thống phải làm 3 việc: ghi `payments`, kích hoạt hợp đồng, cấp buổi tập. Nếu ghi DB xong rồi mới gửi sự kiện mà server **chết giữa chừng** → tiền đã thu nhưng hợp đồng chưa kích hoạt, khách không vào tập được. Outbox pattern ghi sự kiện vào **cùng một transaction** với dữ liệu → hoặc cả hai cùng thành công, hoặc cả hai cùng thất bại. Một tiến trình riêng đọc bảng này và phát sự kiện, thử lại nếu lỗi.

## `llm_usage_logs` — theo dõi chi phí AI *(Could have)*  
> **[❌ BỎ → tùy chọn (C)]**

| Cột | Kiểu | Giá trị nhận / Ý nghĩa | Mẫu |
|---|---|---|---|
| `feature` | `VARCHAR(40)` | `CHATBOT` \| `WORKOUT_DRAFT` \| `INBODY_OCR` \| `FEEDBACK_SUMMARY` \| `REPORT_NARRATIVE` | `INBODY_OCR` |
| `model` | `VARCHAR(40)` | Model nào | `claude-haiku-4-5` |
| `input_tokens` | `INTEGER` | Token đầu vào | `1800` |
| `output_tokens` | `INTEGER` | Token đầu ra | `400` |
| `cache_read_tokens` | `INTEGER` | Token đọc từ cache (rẻ hơn ~10 lần) | `0` |
| `cache_write_tokens` | `INTEGER` | Token ghi vào cache | `0` |
| `is_batch` | `BOOLEAN` | Dùng Batch API (giảm 50%)? | `FALSE` |
| `cost_usd` | `NUMERIC(10,6)` | Chi phí lần gọi này | `0.003800` |
| `latency_ms` | `INTEGER` | Mất bao lâu | `2340` |
| `user_id` | `BIGINT` | Ai gọi | `30` |
| `success` | `BOOLEAN` | Thành công không | `TRUE` |
| `error_message` | `TEXT` | Lỗi nếu có | `NULL` |

**Cách tính `cost_usd`** (đơn giá USD/1 triệu token, cập nhật 07/2026):

| Model | Input | Output |
|---|---:|---:|
| Claude Haiku 4.5 | 1,00 | 5,00 |
| Claude Sonnet 5 | 3,00 | 15,00 |

`0,0018M × 1,00 + 0,0004M × 5,00 = 0,0018 + 0,0020 = 0,0038 USD ≈ 100đ/lần đọc phiếu InBody`

> **"Không đo được thì không quản trị được."** Bảng này là cơ sở cho dashboard chi phí AI theo tính năng, và cho phần tính chi phí LLM trong báo cáo (~55 USD/tháng cho 1.000 hội viên ≈ 0,2% doanh thu/hội viên).

---
---

## Phụ lục — Bảng tra nhanh các enum

| Bảng.Cột | Giá trị hợp lệ |
|---|---|
| `users.primary_role`, `user_roles.role` | `ADMIN` `MEMBER` `TRAINER` `SALE` `RECEPTIONIST` `ACCOUNTANT` |
| `users.status` | `ACTIVE` `SUSPENDED` `DISABLED` |
| `persons.gender` | `MALE` `FEMALE` `OTHER` |
| `members.status` | `ACTIVE` `INACTIVE` `BLACKLISTED` |
| `members.goal` | `LOSE_FAT` `GAIN_MUSCLE` `ENDURANCE` `HEALTH` |
| `members.source`, `leads.source` | `WALK_IN` `FB_ADS` `REFERRAL` `HOTLINE` `GOOGLE` `EVENT` |
| `trainers.employment_type` | `FULL_TIME` `PART_TIME` `FREELANCE` |
| `trainers.level` | `JUNIOR` `SENIOR` `MASTER` |
| `staff.department` | `SALES` `FRONT_DESK` `ACCOUNTING` `MANAGEMENT` `MAINTENANCE` |
| `memberships.package_type` | `TIME_BASED` `SESSION_BASED` `HYBRID` `DAY_PASS` |
| `registrations.status` | `DRAFT` `PENDING_PAYMENT` `ACTIVE` `FROZEN` `COMPLETED` `CANCELLED` `TRANSFERRED` `REFUNDED` |
| `registration_freezes.status` | `PENDING` `APPROVED` `REJECTED` `ACTIVE` `ENDED` `CANCELLED` |
| `registration_freezes.reason_type` | `PERSONAL` `MEDICAL` `TRAVEL` `OTHER` |
| `member_trainers.role` | `PRIMARY` `SECONDARY` `SUBSTITUTE` |
| `check_ins.method` | `QR_DYNAMIC` `RFID` `MANUAL` `FACE` `DAY_PASS` |
| `check_ins.result` | `ALLOWED` `ALLOWED_OVERRIDE` `DENIED_EXPIRED` `DENIED_FROZEN` `DENIED_UNPAID` `DENIED_NOT_FOUND` `DENIED_SUSPECT` |
| `check_in_incidents.incident_type` | `ANTI_PASSBACK` `SUSPECTED_SHARING` `REPLAY_ATTEMPT` `FACE_MISMATCH` `EXPIRED_ATTEMPT` |
| `pt_sessions.session_type` | `PAID_PT` `COMPLIMENTARY` `TRIAL` `ORIENTATION` `MAKEUP` `ASSESSMENT` |
| `pt_sessions.status` | `SCHEDULED` `IN_PROGRESS` `COMPLETED` `NO_SHOW_MEMBER` `NO_SHOW_TRAINER` `CANCELLED` |
| `pt_bookings.status` | `PENDING_TRAINER` `CONFIRMED` `REJECTED` `CANCELLED_BY_MEMBER` `CANCELLED_BY_TRAINER` `EXPIRED` |
| `session_credit_ledger.entry_type` | `GRANT` `CONSUME` `REFUND` `EXPIRE` `ADJUST` `TRANSFER_IN` `TRANSFER_OUT` |
| `rooms.status` | `AVAILABLE` `MAINTENANCE` `CLOSED` |
| `class_sessions.status` | `SCHEDULED` `IN_PROGRESS` `COMPLETED` `CANCELLED` |
| `class_bookings.status` | `BOOKED` `WAITLISTED` `ATTENDED` `NO_SHOW` `CANCELLED` |
| `equipment_items.status` | `OPERATIONAL` `NEEDS_REPAIR` `UNDER_MAINTENANCE` `RETIRED` |
| `maintenance_work_orders.status` | `OPEN` `IN_PROGRESS` `WAITING_PARTS` `RESOLVED` `CANCELLED` |
| `invoices.status` | `UNPAID` `PARTIALLY_PAID` `PAID` `OVERDUE` `CANCELLED` `REFUNDED` |
| `payments.method` | `CASH` `BANK_TRANSFER` `VIETQR` `CARD_POS` `E_WALLET` `GATEWAY` |
| `payments.status` | `INITIATED` `PENDING` `SUCCEEDED` `FAILED` `EXPIRED` `REFUNDED` `PARTIALLY_REFUNDED` |
| `cash_shifts.status` | `OPEN` `CLOSED` `DISCREPANCY` |
| `refunds.status` | `PENDING` `APPROVED` `REJECTED` `PROCESSED` |
| `revenue_schedules.recognition_method` | `STRAIGHT_LINE` `PER_SESSION` `IMMEDIATE` |
| `expenses.allocation_method` | `IMMEDIATE` `STRAIGHT_LINE` `DEPRECIATION` |
| `payroll_runs.status` | `DRAFT` `REVIEW` `APPROVED` `PAID` `VOID` |
| `payroll_items.item_type` | `BASE_SALARY` `SESSION_FEE` `SALES_COMMISSION` `KPI_BONUS` `ALLOWANCE` `DEDUCTION` `PENALTY` `ADJUSTMENT` |
| `leads.stage` | `NEW` `CONTACTED` `TRIAL_BOOKED` `TRIAL_DONE` `NEGOTIATING` `WON` `LOST` |
| `leads.lost_reason` | `PRICE` `LOCATION` `COMPETITOR` `NOT_READY` `NO_RESPONSE` |
| `quotes.status` | `DRAFT` `PENDING_APPROVAL` `SENT` `ACCEPTED` `REJECTED` `EXPIRED` `CONVERTED` |
| `exercises.muscle_group` | `CHEST` `BACK` `LEGS` `SHOULDERS` `ARMS` `CORE` `CARDIO` |
| `exercises.difficulty` | `BEGINNER` `INTERMEDIATE` `ADVANCED` |
| `body_metrics.source` | `MANUAL` `INBODY_OCR` `DEVICE_SYNC` |
| `feedbacks.feedback_type` | `TRAINER` `FACILITY` `HYGIENE` `SERVICE` `GENERAL` |
| `feedbacks.status` | `OPEN` `IN_PROGRESS` `RESOLVED` `CLOSED` `REJECTED` |
| `churn_scores.risk_level` | `LOW` `MEDIUM` `HIGH` |
| `retention_tasks.outcome` | `CONTACTED` `NO_ANSWER` `RENEWED` `REFUSED` `WRONG_NUMBER` |
