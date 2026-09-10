# Thuộc tính & Quan hệ giữa các bảng — hiện trạng (09/09/2026)

> Nguồn: `db/database_7_9_26.sql` (23 bảng đang chạy thật, khớp Flyway V1-V19) + 4 bảng Nhóm G đang đề xuất (`exercises`, `workout_plans`, `workout_plan_items`, `body_metrics` — đánh dấu ⚠️ ĐỀ XUẤT, chưa có migration). Viết theo đúng format thuộc tính/quan hệ bạn đã dùng ở bản nháp cũ.

---

## 1. Các thuộc tính của từng thực thể

### Nhóm 1 — Danh tính

1. persons (id, full_name, gender, birthday, national_id, phone, email, address, photo_key, card_uid, card_issued_at, qr_secret_enc, emergency_contact_name, emergency_contact_phone, created_at, updated_at, deleted_at)
2. users (id, person_id, username, password_hash, primary_role, status, locked_reason, locked_until, failed_attempts, auto_locked_until, token_version, email_verified_at, last_login_at, created_at, updated_at, deleted_at)
3. password_reset_tokens (id, user_id, token_hash, expires_at, used_at, requested_ip, created_at)
4. members (id, person_id, member_code, join_date, source, referred_by, health_note, goal, status, last_visit_at, created_at, updated_at, deleted_at)
5. employees (id, person_id, employee_code, department, position, employment_type, base_salary, start_date, end_date, status, level, specialties, bio, max_members, rating_avg, rating_count, created_at, updated_at, deleted_at)
6. audit_logs (id, actor_id, action, entity_type, entity_id, before_data, after_data, reason, ip_address, user_agent, created_at)

### Nhóm 2 — Gói tập & Hợp đồng

7. memberships (id, code, name, package_type, duration_days, session_count, price, includes_trainer, pt_value_ratio, area_codes, max_freeze_days, is_refundable, description, display_order, status, created_at, updated_at, deleted_at)
8. registrations (id, registration_code, member_id, membership_id, sold_by, assigned_trainer_id, package_type, duration_days, sessions_total, list_price, discount_amount, discount_reason, discount_approved_by, final_price, contract_date, start_date, end_date, activated_at, closed_at, status, close_reason, note, freeze_from_date, freeze_to_date, freeze_days, freeze_reason, freeze_reason_type, freeze_attachment_key, freeze_requested_by, freeze_approved_by, freeze_status, freeze_ended_early_at, renew_from_id, created_at, updated_at, deleted_at)
9. member_trainers (id, member_id, trainer_id, role, from_date, to_date, assigned_by, note, created_at, updated_at)

### Nhóm 3 — Check-in

10. check_ins (id, member_id, registration_id, checked_in_at, checked_out_at, auto_closed, method, result, verified_by, incident_type, incident_note, incident_handled_by, created_at, updated_at)

### Nhóm 4 — Buổi tập PT & Sổ cái

11. pt_sessions (id, member_id, trainer_id, registration_id, session_type, scheduled_start, scheduled_end, actual_start, actual_end, room_name, status, requested_by, responded_at, reject_reason, trainer_confirmed_at, member_confirmed_at, auto_confirmed, no_show_by, cancelled_by, cancelled_at, cancel_reason, is_late_cancel, note, created_at, updated_at, deleted_at)
12. session_credit_ledger (id, registration_id, entry_type, delta, balance_after, source_type, source_id, reason, created_by, created_at)

### Nhóm 6 — Thiết bị

13. equipment (id, name, room_name, status, note, created_at, updated_at, deleted_at)

### Nhóm 7 — Thanh toán

14. invoices (id, invoice_no, member_id, registration_id, description, total_amount, paid_amount, balance_due, status, issued_at, due_date, paid_at, issued_by, created_at, updated_at, deleted_at)
15. cash_shifts (id, employee_id, opened_at, closed_at, opening_balance, counted_cash, expected_cash, difference, difference_reason, status, verified_by, note, created_at, updated_at)
16. payments (id, payment_no, member_id, invoice_id, cash_shift_id, payment_type, method, amount, status, provider, provider_txn_id, transfer_content, bank_account, raw_payload, refund_of_payment_id, refund_reason, refund_penalty, approved_by, received_by, paid_at, reconciled_at, created_at, updated_at)

### Nhóm 8 — Tài chính & Lương

17. revenue_schedules (id, registration_id, invoice_id, schedule_date, amount, status, recognition_method, recognized_at, note, created_at, updated_at)
18. payroll_runs (id, payroll_code, period_month, period_year, total_base_salary, total_commission, total_bonus, total_deduction, total_net_salary, status, created_by, approved_by, approved_at, paid_at, paid_by, note, created_at, updated_at)
19. payroll_items (id, payroll_run_id, employee_id, base_salary, pt_sessions_count, pt_commission, sales_contracts_count, sales_commission, bonus_amount, deduction_amount, net_salary, manually_edited, note, created_at, updated_at)
20. expenses (id, expense_no, category, title, amount, spent_at, spent_by, approved_by, status, payment_method, receipt_url, note, created_at, updated_at, deleted_at)

### Nhóm 9 — Bán hàng / CRM

21. leads (id, person_id, source, interested_membership_id, assigned_to, stage, lost_reason, last_contact_at, last_contact_note, next_follow_up, created_at, updated_at, deleted_at)

### Nhóm 11 — Phản hồi

22. feedbacks (id, member_id, feedback_type, trainer_id, equipment_id, rating, description, status, is_urgent, repair_cost, resolution_note, resolved_by, resolved_at, created_at, updated_at, deleted_at)

### Nhóm 12 — Hệ thống

23. system_settings (setting_key, setting_value, description, updated_at, updated_by)

### Nhóm 10 — Bài tập & Chỉ số cơ thể ⚠️ ĐỀ XUẤT, CHƯA TRIỂN KHAI

24. exercises (id, code, name_vi, name_en, muscle_group, secondary_muscles, equipment, difficulty, instructions, media_key, contraindications, source, license, is_reviewed, created_at, updated_at, deleted_at)
25. workout_plans (id, is_template, member_id, trainer_id, source_template_id, name, goal, level, duration_weeks, days_per_week, start_date, end_date, status, ai_generated, reviewed_by, created_at, updated_at, deleted_at)
26. workout_plan_items (id, plan_id, day_index, order_index, exercise_id, sets, reps, target_weight_kg, rest_sec, note, created_at, updated_at)
27. body_metrics (id, member_id, measured_at, measured_by, source, height_cm, weight_kg, body_fat_pct, muscle_mass_kg, visceral_fat, bmr_kcal, bmi, chest_cm, waist_cm, hip_cm, arm_cm, thigh_cm, photo_key, ocr_confidence, confirmed_by_user, note, created_at, updated_at)

---

## 2. Các quan hệ

### Danh tính

- **Persons - Users (1 - N):** Một người có thể có nhiều tài khoản đăng nhập với vai trò khác nhau (đáp ứng "1 người, nhiều vai trò").
- **Persons - Members (1 - 1):** Một người chỉ trở thành đúng 1 hồ sơ hội viên, và chỉ được tạo khi chốt mua gói đầu tiên (không phải lúc đăng ký tài khoản).
- **Persons - Employees (1 - 1):** Một người chỉ có đúng 1 hồ sơ nhân sự.
- **Members - Members [referred_by] (1 - N):** Một hội viên có thể giới thiệu (referral) nhiều hội viên khác.
- **Users - Password_reset_tokens (1 - N):** Một tài khoản có thể có nhiều lần yêu cầu đặt lại mật khẩu theo thời gian.
- **Users - Audit_logs (1 - N):** Một tài khoản có thể thực hiện nhiều thao tác được ghi log (before/after data).

### Gói tập, Hợp đồng & PT

- **Members - Registrations (1 - N):** Một hội viên có thể có nhiều hợp đồng (mua mới, gia hạn) theo thời gian.
- **Memberships - Registrations (1 - N):** Một gói tập có thể được nhiều hợp đồng mua — giá/điều khoản được sao chép (snapshot) vào từng hợp đồng lúc ký.
- **Employees - Registrations [sold_by] (1 - N):** Một nhân viên sale có thể chốt nhiều hợp đồng.
- **Employees - Registrations [assigned_trainer_id] (1 - N):** Một PT có thể được phân công phụ trách nhiều hợp đồng.
- **Registrations - Registrations [renew_from_id] (1 - N):** Một hợp đồng cũ là điểm "nối tiếp" cho hợp đồng gia hạn mới (quy ước nghiệp vụ: mỗi hợp đồng cũ chỉ nên được gia hạn đúng 1 lần).
- **Members - Member_trainers (1 - N):** Một hội viên có thể có nhiều PT phụ trách cùng lúc (chính/phụ/thay thế).
- **Employees - Member_trainers (1 - N):** Một PT có thể phụ trách nhiều hội viên.
- **Members - Pt_sessions (1 - N):** Một hội viên có thể có nhiều buổi tập PT.
- **Employees - Pt_sessions (1 - N):** Một PT có thể dạy nhiều buổi tập (là PT thực tế dạy, không nhất thiết là PT được phân công ở hợp đồng).
- **Registrations - Pt_sessions (1 - N):** Một hợp đồng PT có thể phát sinh nhiều buổi tập, mỗi buổi hoàn thành trừ dần số buổi còn lại.
- **Registrations - Session_credit_ledger (1 - N):** Một hợp đồng có nhiều bút toán ghi nhận biến động số buổi tập (GRANT/CONSUME/REFUND/EXPIRE/ADJUST) — sổ cái chỉ ghi thêm, không sửa/xóa.

### Check-in

- **Members - Check_ins (1 - N):** Một hội viên có nhiều lượt check-in/check-out.
- **Registrations - Check_ins (1 - N):** Một hợp đồng được dùng làm căn cứ cho nhiều lượt check-in.

### Thanh toán & Tài chính

- **Members - Invoices (1 - N):** Một hội viên có thể có nhiều hóa đơn.
- **Registrations - Invoices (1 - N):** Một hợp đồng có thể phát sinh nhiều hóa đơn.
- **Invoices - Payments (1 - N):** Một hóa đơn có thể được thanh toán thành nhiều lần (trả góp/nhiều đợt/hoàn tiền một phần).
- **Employees - Cash_shifts (1 - N):** Một nhân viên (lễ tân) có thể mở nhiều ca làm việc theo thời gian, nhưng chỉ được mở tối đa 1 ca `OPEN` cùng lúc.
- **Cash_shifts - Payments (1 - N):** Một ca làm việc ghi nhận nhiều khoản thu tiền mặt trong ca đó.
- **Payments - Payments [refund_of_payment_id] (1 - N):** Một khoản thu gốc có thể bị hoàn tiền (bút toán âm) nhiều lần — ví dụ hoàn nhiều đợt.
- **Registrations - Revenue_schedules (1 - N):** Một hợp đồng được phân bổ thành nhiều kỳ ghi nhận doanh thu dồn tích theo tháng/theo buổi.
- **Invoices - Revenue_schedules (1 - N):** Một hóa đơn có thể liên kết nhiều kỳ ghi nhận doanh thu.
- **Payroll_runs - Payroll_items (1 - N):** Một đợt chạy lương có nhiều dòng lương chi tiết, mỗi dòng ứng với 1 nhân viên.
- **Employees - Payroll_items (1 - N):** Một nhân viên có nhiều dòng lương qua các tháng.

### Bán hàng / CRM & Thiết bị / Phản hồi

- **Persons - Leads (1 - N):** Một người có thể được ghi nhận là khách tiềm năng nhiều lần (qua các đợt tiếp cận khác nhau).
- **Memberships - Leads (1 - N):** Một gói tập có thể là gói được nhiều lead quan tâm.
- **Employees - Leads (1 - N):** Một nhân viên sale phụ trách chăm sóc nhiều lead.
- **Members - Feedbacks (1 - N):** Một hội viên có thể gửi nhiều phản ánh/đánh giá.
- **Employees - Feedbacks (1 - N):** Một PT có thể nhận nhiều đánh giá từ các hội viên khác nhau.
- **Equipment - Feedbacks (1 - N):** Một thiết bị có thể có nhiều phản ánh hỏng hóc theo thời gian.

### Vai trò "người thực hiện thao tác" (Users → nhiều bảng)

Nhiều bảng nghiệp vụ có cột trỏ về `users.id` để ghi nhận **ai duyệt/xác nhận/thực hiện thao tác** (phục vụ truy vết trách nhiệm, không phải quan hệ nghiệp vụ chính). Tất cả đều là quan hệ **1 - N** (1 tài khoản → N thao tác):

- Users → Registrations (`discount_approved_by`, `freeze_requested_by`, `freeze_approved_by`)
- Users → Member_trainers (`assigned_by`)
- Users → Pt_sessions (`requested_by`)
- Users → Session_credit_ledger (`created_by`)
- Users → Check_ins (`verified_by`, `incident_handled_by`)
- Users → Invoices (`issued_by`)
- Users → Cash_shifts (`verified_by`)
- Users → Payments (`approved_by`, `received_by`)
- Users → Payroll_runs (`created_by`, `approved_by`, `paid_by`)
- Users → Expenses (`spent_by`, `approved_by`)
- Users → Feedbacks (`resolved_by`)
- Users → System_settings (`updated_by`)

### Nhóm 10 — Bài tập & Chỉ số cơ thể ⚠️ ĐỀ XUẤT, CHƯA TRIỂN KHAI

- **Members - Workout_plans (1 - N):** Một hội viên có thể có nhiều giáo án theo thời gian (`member_id = NULL` khi là giáo án mẫu).
- **Employees - Workout_plans (1 - N):** Một PT có thể soạn nhiều giáo án.
- **Workout_plans - Workout_plans [source_template_id] (1 - N):** Một giáo án mẫu có thể được dùng làm gốc cho nhiều giáo án cá nhân hóa.
- **Employees - Workout_plans [reviewed_by] (1 - N):** Một PT có thể duyệt nhiều giáo án do AI soạn nháp.
- **Workout_plans - Workout_plan_items (1 - N):** Một giáo án gồm nhiều bài tập, chia theo từng ngày trong chu kỳ.
- **Exercises - Workout_plan_items (1 - N):** Một bài tập trong thư viện có thể xuất hiện trong nhiều giáo án khác nhau.
- **Members - Body_metrics (1 - N):** Một hội viên có nhiều lần đo chỉ số cơ thể theo thời gian, tạo thành dữ liệu để chatbot diễn giải tiến độ.
- **Users - Body_metrics [measured_by] (1 - N):** Một tài khoản (PT/lễ tân) có thể đo hộ chỉ số cho nhiều hội viên; `NULL` = hội viên tự nhập.
