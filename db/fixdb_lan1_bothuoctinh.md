# ĐỀ XUẤT CẮT GIẢM THUỘC TÍNH — LẦN 1

> Cơ sở: `db/31_bang.md` (32 bảng). File này **không đổi số bảng**, chỉ rà từng cột trong 32 bảng đó và đề xuất **bỏ / gộp / rút gọn** những thuộc tính:
> - Chỉ phục vụ **trường hợp ngoại lệ, hiếm gặp** mà hệ thống không có nghiệp vụ nào thật sự dùng đến,
> - Hoặc **trùng lặp dữ liệu** đã có ở bảng khác,
> - Hoặc thêm vào **"cho chắc"** (phòng thủ kiểu production) chứ không phục vụ nghiệp vụ chính của đồ án.
>
> **Nguyên tắc giữ:** 3 bảng đóng góp học thuật (`session_credit_ledger`, `revenue_schedules`, `revenue_recognition_entries`) và các bảng đã tối giản sẵn — **không đụng vào**.

---

## Tóm tắt nhanh (đọc trong 1 phút)

| Bảng | Số cột cắt/gộp | Mức độ |
|---|---:|---|
| `equipment` | 5 cột bỏ + gộp `brand`/`model` | 🔴 Cao |
| `invoices` | 3 cột bỏ (trùng `registrations`) | 🔴 Cao |
| `payments` | 4 cột bỏ | 🔴 Cao |
| `body_metrics` | 3 cột bỏ | 🟡 Trung bình |
| `expenses` | 3 cột → gộp thành 2 | 🟡 Trung bình |
| `members` | 2 cột bỏ | 🟡 Trung bình |
| `check_ins` | 1–2 cột bỏ/cân nhắc | 🟡 Trung bình |
| `class_bookings` | 1 cột bỏ | 🟢 Thấp |
| `password_reset_tokens` | 1 cột bỏ | 🟢 Thấp |
| `pt_sessions` | 1 cột bỏ | 🟢 Thấp |
| `employees` | 1 cột bỏ | 🟢 Thấp |

Các bảng còn lại (`registrations`, `registration_freezes`, `member_trainers`, `session_credit_ledger`, `revenue_schedules`, `revenue_recognition_entries`, `cash_shifts`, `payroll_runs`, `payroll_items`, `leads`, `exercises`, `workout_plans`, `workout_plan_items`, `workout_logs`, `feedbacks`, `system_settings`, `persons`, `users`, `audit_logs`) — **giữ nguyên**, mọi cột đều gắn với một nghiệp vụ hoặc bất biến đã mô tả rõ.

---

## 🔴 NHÓM 6 — `equipment` (mức cắt cao nhất)

| Cột hiện tại | Đề xuất | Vì sao |
|---|---|---|
| `serial_number` | **Bỏ** | Không nghiệp vụ nào tra cứu theo số seri (không có bảo hành, không có đối soát hãng) |
| `origin` | **Bỏ** | Chỉ mang tính mô tả, không phục vụ tính năng nào |
| `brand`, `model` | **Gộp thành 1 cột `model` (VD: "Technogym MyRun")** | Tách 2 cột chỉ để hiển thị, không có logic lọc theo hãng riêng |
| `warranty_until` | **Bỏ** | Không có tính năng nhắc hết bảo hành / claim bảo hành |
| `last_maintained_at` | **Bỏ** | Không có lịch bảo trì định kỳ đi kèm — hệ thống chỉ có `status` (OPERATIONAL/NEEDS_REPAIR/...) đủ để biết tình trạng hiện tại |
| `disposed_at` | **Bỏ** | `status = RETIRED` + `updated_at` đã đủ biết máy ngừng dùng từ lúc nào |

**Còn lại:** `asset_code`, `name`, `category`, `model`, `location`, `purchase_price`, `date_of_purchase`, `useful_life_months`, `status` — vẫn đủ để tính khấu hao (`purchase_price / useful_life_months`) và hiển thị danh sách thiết bị, đúng nghiệp vụ chính.

---

## 🔴 NHÓM 7 — `invoices` (trùng dữ liệu với `registrations`)

| Cột hiện tại | Đề xuất | Vì sao |
|---|---|---|
| `subtotal` | **Bỏ** | Đã có `registrations.list_price` |
| `discount_amount` | **Bỏ** | Đã có `registrations.discount_amount` + `discount_reason` |
| `tax_amount` | **Bỏ** | Chính tài liệu cũ ghi "thường 0 với dịch vụ gym" — cột gần như luôn rỗng, không nghiệp vụ VAT nào dùng |

> Vì `1 invoice = 1 registration`, giá trị hóa đơn **luôn bằng** `registrations.final_price` — không cần lưu lại `subtotal`/`discount_amount`/`tax_amount` riêng. Chỉ cần `total_amount` (copy từ `final_price` lúc xuất hóa đơn).

**Còn lại:** `invoice_no`, `member_id`, `person_id`, `registration_id`, `description`, `total_amount`, `paid_amount`, `balance_due`, `status`, `issued_at`/`due_date`/`paid_at`, `issued_by`.

---

## 🔴 NHÓM 7 — `payments`

| Cột hiện tại | Đề xuất | Vì sao |
|---|---|---|
| `currency` | **Bỏ** | Luôn là `VND`, không có nghiệp vụ đa tiền tệ — cột hằng số |
| `idempotency_key` | **Bỏ** | `provider_txn_id` đã **UNIQUE(provider, txn_id)** chống trùng webhook. Trùng do client bấm nút 2 lần chỉ xảy ra khi có luồng retry offline — hệ thống chưa có luồng đó |
| `pos_terminal_id` | **Bỏ** | Cần máy POS thật để có dữ liệu — giống vấn đề phần cứng của RFID, không demo được |
| `pos_card_last4` | **Bỏ** | Cùng lý do — gắn với `CARD_POS`, không có phần cứng |

> Nếu sau này thật sự cần `CARD_POS`, có thể thêm lại; hiện tại giữ cũng không kiểm thử được vì không có thiết bị.

**Còn lại:** `payment_no`, `member_id`, `invoice_id`, `cash_shift_id`, `payment_type`, `method`, `amount`, `status`, `provider`, `provider_txn_id`, `transfer_content`, `bank_account`, `refund_reason`, `refund_of_payment_id`, `refund_penalty`, `approved_by`, `received_by`, `paid_at`, `reconciled_at`, `raw_payload`.

---

## 🟡 NHÓM 10 — `body_metrics`

| Cột hiện tại | Đề xuất | Vì sao |
|---|---|---|
| `chest_cm` | **Bỏ** | Không công thức/tính năng nào trong hệ thống dùng đến |
| `arm_cm` | **Bỏ** | Tương tự |
| `thigh_cm` | **Bỏ** | Tương tự |

> Giữ `waist_cm`/`hip_cm` vì phục vụ công thức WHR (`waist/hip`) đã có trong tài liệu. Giữ `height_cm`, `weight_kg`, `bmi`, `bmr_kcal`, `body_fat_pct`, `muscle_mass_kg`, `visceral_fat` vì là số liệu InBody gốc + input trực tiếp cho chatbot tư vấn.

---

## 🟡 NHÓM 8 — `expenses`

| Cột hiện tại | Đề xuất | Vì sao |
|---|---|---|
| `payroll_run_id`, `feedback_id`, `equipment_id` (3 cột FK riêng) | **Gộp thành `source_type` (VARCHAR) + `source_id` (BIGINT)** | Các bảng khác (`payments`, `payroll_items`, `session_credit_ledger`, `revenue_recognition_entries`) đều dùng pattern `source_type`+`source_id` cho nguồn gốc bút toán. `expenses` đang lệch pattern, tạo ra 3 cột mà mỗi dòng chỉ 1 cột khác NULL |

---

## 🟡 NHÓM 1 — `members`

| Cột hiện tại | Đề xuất | Vì sao |
|---|---|---|
| `referred_by` | **Bỏ** | Không có bảng/luồng thưởng giới thiệu đi kèm. `source = 'REFERRAL'` đã đủ ghi nhận kênh đến |
| `last_visit_at` | **Bỏ** | Suy ra được bằng `MAX(checked_in_at)` từ `check_ins` — lưu thêm là dữ liệu phái sinh không đồng bộ tự động (không có trigger cập nhật được mô tả) |

---

## 🟡 NHÓM 3 — `check_ins`

| Cột hiện tại | Đề xuất | Vì sao |
|---|---|---|
| `device_id` | **Bỏ** | Chỉ có ý nghĩa khi có nhiều thiết bị quét cùng lúc cần phân biệt — 1 phòng gym, 1 quầy lễ tân là đủ |
| `photo_key` | **Cân nhắc bỏ** | Ảnh hồ sơ đã có sẵn ở `persons.photo_key` để lễ tân đối chiếu; chụp thêm ảnh **tại lúc check-in** cần camera tích hợp — phần cứng chưa có, giống vấn đề RFID |

---

## 🟢 Các cắt giảm nhỏ (1 cột/bảng)

| Bảng | Cột | Vì sao |
|---|---|---|
| `password_reset_tokens` | `requested_ip` | Ghi lại nhưng không có màn hình/luồng nào đọc để phát hiện lạm dụng |
| `class_bookings` | `waitlist_position` | Cơ chế hàng chờ (xếp lại khi có người hủy) là tính năng phụ, tăng độ phức tạp không tương xứng với lợi ích |
| `pt_sessions` | `responded_at` | Chỉ mang tính thống kê — không có luật nghiệp vụ nào ràng buộc "PT phải trả lời trong X giờ" (khác với `auto_confirmed` có hạn 24h rõ ràng) |
| `employees` | `max_members` | Không có logic nào kiểm tra giới hạn khi phân công ở `member_trainers` |

---

## Việc cần làm sau khi xác nhận

1. Với mỗi mục ở trên, xác nhận **Đồng ý bỏ / Giữ lại / Có ý kiến khác**.
2. Sau khi chốt, áp dụng sửa trực tiếp vào `db/31_bang.md` (bảng, Phụ lục A, mẫu dữ liệu liên quan).
3. Các quyết định treo trước đó (RFID, FACE, churn, `refresh_tokens`) xử lý **riêng**, không nằm trong phạm vi file này.
