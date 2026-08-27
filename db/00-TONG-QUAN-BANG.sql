-- =====================================================================
--  TỔNG QUAN CÁC BẢNG — ĐỌC TRONG 5 PHÚT
--  Mục đích: giải thích VÌ SAO cần từng bảng, mỗi bảng có gì.
--
--  • Muốn biết TỪNG CỘT nhận giá trị nào + DỮ LIỆU MẪU
--      → db/01-TU-DIEN-DU-LIEU.md
--  • Muốn xem DDL đầy đủ (kiểu dữ liệu, CHECK, index, trigger)
--      → db/schema.sql
--
--  PHẠM VI: quản lý MỘT phòng gym. Không có bảng `branches`, không có
--  cột `branch_id`. Thông tin phòng gym nằm trong `system_settings`.
-- =====================================================================


-- =====================================================================
--  TẠI SAO TỪ 9 BẢNG THÀNH 73 BẢNG?
-- =====================================================================
--
--  Schema cũ (9 bảng) chỉ phủ được "quản lý hội viên cơ bản".
--  Đề bài yêu cầu rộng hơn nhiều. Số bảng tăng vì 6 lý do:
--
--  LÝ DO 1 — Đề bài có 6 role, schema cũ mới phục vụ 3          (+12)
--            Sale, Lễ tân, Kế toán chưa có bảng nào:
--            staff, leads, lead_activities, quotes, quote_items,
--            discount_policies, cash_shifts, persons, user_roles,
--            refresh_tokens, audit_logs, trainer_specialties
--
--  LÝ DO 2 — Module tài chính — schema cũ KHÔNG CÓ BẢNG TIỀN NÀO (+17)
--            Không có invoices/payments thì không làm được gì cả:
--            invoices, invoice_items, payments, payment_allocations,
--            payment_schedules, refunds, webhook_events,
--            revenue_schedules, revenue_recognition_entries,
--            expense_categories, expenses, payroll_rate_cards,
--            commission_rules, payroll_runs, payroll_items,
--            accounting_periods, financial_reports
--
--  LÝ DO 3 — Yêu cầu cụ thể của thầy chưa được đáp ứng           (+9)
--            • bảo lưu gói tập       → registration_freezes
--            • 2-3 PT chăm 1 hội viên→ member_trainers
--            • quản lý việc đến tập  → check_ins, check_in_incidents,
--                                      access_cards, qr_secrets
--            • chống khai khống buổi → session_credit_ledger,
--                                      pt_bookings, trainer_availability
--            (buổi miễn phí vẫn tính công dùng payroll_rate_cards,
--             đã đếm ở LÝ DO 2)
--
--  LÝ DO 4 — Tách các khái niệm đang bị TRỘN trong schema cũ     (+7)
--            • classes trộn "lớp học" và "phòng tập"
--                → rooms / class_definitions / class_schedules /
--                  class_sessions / class_bookings
--            • facilities trộn "loại thiết bị" và "từng cái"
--                → equipment_types / equipment_items
--            (thay cho 3 bảng cũ: classes, facilities,
--             class_has_facilities)
--
--  LÝ DO 5 — Tính năng của đề bài chưa có bảng                  (+19)
--            bài tập & giáo án (6) · chỉ số cơ thể (1) ·
--            phản hồi & đánh giá PT (2) · thông báo (2) ·
--            giữ chân hội viên (2) · bảo trì thiết bị (1) ·
--            giá & khuyến mãi (4) · chuyển nhượng gói (1)
--
--  LÝ DO 6 — An toàn & vận hành                                  (+3)
--            system_settings, outbox_events, llm_usage_logs
--
--  ------------------------------------------------------------------
--  CỘNG LẠI:  6 bảng giữ nguyên tên (users, members, trainers,
--             memberships, registrations, pt_sessions←training_histories)
--             + 67 bảng mới hoặc tách ra  =  73 bảng
--
--  Trong 73 bảng, chỉ ~40 bảng gắn ⭐ là BẮT BUỘC (lõi đồ án).
--  Phần còn lại đánh (S)/(C) — cắt được nếu thiếu thời gian.
--
--  ---------------------------------------------------------------
--  Ghi chú:  [CŨ]  = đã có trong fix.sql (có thể đã sửa)
--            [MỚI] = bổ sung
--            ⭐    = bắt buộc, thuộc lõi đồ án
--            (S)   = Should have — làm sau
--            (C)   = Could have  — làm nếu còn thời gian
-- =====================================================================



-- ####################################################################
-- ## NHÓM 1. DANH TÍNH & CON NGƯỜI            (9 bảng, LÝ DO 1)
-- ####################################################################
-- Vì sao đổi: schema cũ gắn thẳng users → members/trainers, nên
-- KHÔNG mô tả được "1 người có nhiều tài khoản khác role" như thầy
-- chốt, và không mô tả được "PT đồng thời là hội viên".
-- Giải pháp: thêm 1 lớp `persons` = CON NGƯỜI THẬT ở giữa.

persons             [MỚI] ⭐ CON NGƯỜI THẬT — 1 dòng = 1 người, duy nhất.
                           Đây là bảng gốc để nối mọi vai trò của cùng 1 người.
                           → full_name, gender, birthday, national_id, phone,
                             email, photo_url, address

                           ► photo_url = ẢNH CHÂN DUNG, dùng để XÁC MINH
                             KHI CHECK-IN. Đúng như bạn hiểu, luồng là:
                               1. Lúc đăng ký hội viên, lễ tân chụp 1 ảnh
                                  chân dung → upload → lưu link vào photo_url
                               2. Khi hội viên quẹt thẻ/quét QR, màn hình quầy
                                  BẬT ẢNH NÀY LÊN CỠ LỚN kèm tên + trạng thái gói
                               3. LỄ TÂN NHÌN, đối chiếu ảnh với người đứng
                                  trước mặt → bấm "Cho vào" hoặc "Từ chối"
                             Đây là xác minh THỦ CÔNG (con người quyết định),
                             chi phí gần bằng 0 và hiệu quả cao vì lễ tân
                             nhận mặt khách quen rất nhanh.
                             Nhận diện khuôn mặt TỰ ĐỘNG là tính năng nâng cao
                             tùy chọn (mục (C)), không bắt buộc.
                             Ví dụ giá trị:
                               "https://s3.../persons/12/photo_2026-07-15.jpg"

users               [CŨ]  ⭐ TÀI KHOẢN ĐĂNG NHẬP. 1 person → N users.
                           Sửa: thêm person_id; mở role từ 3 lên 6;
                                BỎ index "chỉ 1 admin" (nếu admin duy nhất bị
                                khóa/quên mật khẩu thì không ai quản trị được)
                           → person_id, username, password_hash, primary_role,
                             status, last_login_at, failed_attempts, locked_until

                           ► primary_role = VAI TRÒ CHÍNH của tài khoản này.
                             Quyết định người dùng đăng nhập vào thì thấy giao
                             diện nào và gọi được API nào.
                             Nhận ĐÚNG 1 trong 6 giá trị:
                               'ADMIN'         chủ phòng gym / quản trị
                               'MEMBER'        hội viên
                               'TRAINER'       huấn luyện viên (PT)
                               'SALE'          nhân viên kinh doanh
                               'RECEPTIONIST'  lễ tân
                               'ACCOUNTANT'    kế toán
                             Ví dụ: anh Nguyễn Văn An vừa là PT vừa bán gói tập
                             → tạo 2 tài khoản, CÙNG person_id:
                               (username='an.pt',   primary_role='TRAINER')
                               (username='an.sale', primary_role='SALE')

                           ► status = TÌNH TRẠNG TÀI KHOẢN.
                             Nhận ĐÚNG 1 trong 3 giá trị:
                               'ACTIVE'    bình thường, đăng nhập được
                               'SUSPENDED' tạm khóa (nghỉ phép dài, nghi ngờ
                                           bảo mật) — mở lại được
                               'DISABLED'  vô hiệu vĩnh viễn (đã nghỉ việc)
                             KHÔNG xóa tài khoản mà chuyển sang 'DISABLED',
                             vì các bản ghi cũ (ai duyệt phiếu, ai thu tiền)
                             vẫn phải trỏ tới tài khoản này.

                           ► Phân biệt status với locked_until:
                               status         = do Admin chủ động đặt
                               locked_until   = do HỆ THỐNG tự đặt khi nhập sai
                                                mật khẩu > 5 lần, tự hết sau
                                                15 phút

user_roles          [MỚI]  (Tùy chọn — có thể BỎ ở giai đoạn đầu)
                           ► Bảng này để làm gì:
                             `users.primary_role` chỉ cho mỗi tài khoản ĐÚNG 1
                             vai trò. Nhưng có trường hợp một tài khoản cần
                             KIÊM NHIỆM 2 vai trò mà không muốn tạo tài khoản
                             thứ hai. Ví dụ phòng gym nhỏ, chị kế toán kiêm
                             luôn lễ tân ca sáng:
                               users:      (id=7, username='hoa.kt',
                                            primary_role='ACCOUNTANT')
                               user_roles: (user_id=7, role='RECEPTIONIST')
                             → Chị Hoa đăng nhập 1 lần, thấy CẢ menu Kế toán
                               LẪN menu Lễ tân.

                           ► Quy tắc phân quyền:
                             Quyền của tài khoản = quyền của primary_role
                                                 HỢP quyền của mọi dòng
                                                      trong user_roles

                           ► KHUYẾN NGHỊ: giai đoạn đầu BỎ bảng này, chỉ dùng
                             primary_role. Ai kiêm nhiệm thì cấp 2 tài khoản —
                             đúng như thầy đã chốt ("1 người có thể có nhiều
                             hơn 2 tài khoản với các role khác nhau"). Đơn giản
                             hơn, dễ giải thích khi bảo vệ, và đủ dùng.
                           → user_id, role (6 giá trị như primary_role),
                             granted_by, granted_at

refresh_tokens      [MỚI] ⭐ Phục vụ refresh token rotation + phát hiện đánh cắp
                           token. Lưu HASH, không lưu token thô.
                           → user_id, token_hash, expires_at, revoked_at,
                             replaced_by (để phát hiện tái sử dụng)

members             [CŨ]  ⭐ Hồ sơ hội viên. Sửa: bỏ các cột cá nhân
                           (đã chuyển sang persons), thêm mã hội viên & churn.
                           → person_id, member_code, join_date, source, goal,
                             last_visit_at (dữ liệu cho cảnh báo bỏ tập)

trainers            [CŨ]  ⭐ Hồ sơ HLV. Sửa: chuẩn hóa trainer_type; thêm
                           lương cứng và điểm đánh giá.
                           → person_id, trainer_code, employment_type, level,
                             base_salary, start_date, rating_avg

trainer_specialties [MỚI]  Chuyên môn của PT (yoga/gym/boxing...). Tách ra vì
                           1 PT có nhiều chuyên môn.
                           → trainer_id, specialty

staff               [MỚI] ⭐ Nhân sự KHÔNG phải PT: sale, lễ tân, kế toán.
                           Schema cũ không có chỗ nào lưu 3 role này.
                           → person_id, staff_code, department, base_salary

audit_logs          [MỚI] ⭐ Ai sửa gì, lúc nào, trước/sau ra sao.
                           Bắt buộc khi hệ thống chạm vào tiền và quyền.
                           → actor_id, action, entity_type, entity_id,
                             before_data, after_data, reason, ip_address



-- ####################################################################
-- ## NHÓM 2. GÓI TẬP & HỢP ĐỒNG               (9 bảng, LÝ DO 2+3)
-- ####################################################################
-- Đây là nhóm sửa nhiều nhất, vì `registrations` cũ thiếu 3 thứ
-- chí mạng: (1) không có ngày hết hạn, (2) không lưu giá đã chốt,
-- (3) không có cơ chế bảo lưu.

memberships         [CŨ]  ⭐ Danh mục gói tập.
                           Sửa quan trọng: training_time VARCHAR ('1 tháng',
                           '12 buổi') KHÔNG query được → tách thành
                           package_type + duration_days + session_count.
                           Bây giờ mới lọc được "gói ≥ 90 ngày" (điều kiện
                           bảo lưu) và tính được ngày hết hạn.
                           → code, name, package_type, duration_days,
                             session_count, includes_trainer,
                             max_freeze_days, is_refundable

membership_prices   [MỚI] ⭐ PHIÊN BẢN GIÁ theo thời gian.
                           Vì sao cần: nếu giá nằm trong memberships, khi Admin
                           tăng giá thì GIÁ TRỊ MỌI HỢP ĐỒNG CŨ đổi theo
                           → sai toàn bộ báo cáo lịch sử.
                           Trả lời góp ý "chính sách tăng giá, hạ giá".
                           → membership_id, price, valid_from, valid_to

access_scopes       [MỚI]  Gói được vào KHU VỰC nào (gym floor / yoga / hồ bơi).
                           Trả lời câu hỏi của cô Trinh về giới hạn thiết bị:
                           gói giới hạn KHU VỰC, phòng/lớp giới hạn SỨC CHỨA.
                           → membership_id, area_code

promotions          [MỚI]  Chương trình khuyến mãi.
                           → code, discount_type, discount_value,
                             valid_from, valid_to, usage_limit

promotion_memberships [MỚI] Khuyến mãi áp cho gói nào (N-N).

discount_policies   [MỚI] ⭐ Hạn mức chiết khấu theo vai trò.
                           Vì sao cần: chặn sale tự ý giảm giá để chốt đơn.
                           → role, max_discount_percent, requires_approval_above

registrations       [CŨ]  ⭐⭐ HỢP ĐỒNG ĐĂNG KÝ GÓI — bảng quan trọng nhất.
                           4 sửa đổi lớn:
                           (1) THÊM start_date/end_date — schema cũ thiếu, nên
                               không biết gói hết hạn khi nào → không kiểm được
                               check-in, không tính được doanh thu
                           (2) SNAPSHOT giá vào hợp đồng (list_price,
                               discount_amount, final_price) → sửa giá gói
                               không ảnh hưởng hợp đồng cũ
                           (3) BỎ registration_type — trùng lặp với
                               training_time (đúng như thầy đã chỉ ra)
                           (4) status từ 3 → 8 trạng thái có định nghĩa rõ:
                               DRAFT, PENDING_PAYMENT, ACTIVE, FROZEN,
                               COMPLETED, CANCELLED, TRANSFERRED, REFUNDED
                               (CANCELLED ≠ COMPLETED: hủy giữa chừng phải
                                hoàn tiền, hết hạn tự nhiên thì không)
                           → member_id, membership_id, sold_by (để tính hoa
                             hồng), assigned_trainer_id, start_date, end_date,
                             sessions_total, list_price, discount_amount,
                             final_price, status

registration_freezes [MỚI] ⭐ BẢO LƯU GÓI TẬP — yêu cầu trực tiếp của thầy.
                           Mỗi lần bảo lưu 1 dòng; end_date của hợp đồng được
                           đẩy lùi đúng số ngày; doanh thu tạm dừng ghi nhận.
                           → registration_id, from_date, to_date, days,
                             reason_type, attachment_url, approved_by, status

registration_transfers [MỚI] (S) Chuyển nhượng gói cho người khác.
                           → from_member_id, to_member_id, remaining_value,
                             transfer_fee

member_trainers [MỚI] ⭐ 2-3 PT CÙNG CHĂM 1 HỘI VIÊN — yêu cầu
                           của thầy. role phân biệt chính/phụ/thay thế.
                           Công vẫn tính cho PT THỰC TẾ dạy buổi đó.
                           → member_id, trainer_id, role (PRIMARY/SECONDARY/
                             SUBSTITUTE), from_date, to_date



-- ####################################################################
-- ## NHÓM 3. CHECK-IN                         (4 bảng, LÝ DO 3)
-- ####################################################################
-- Vì sao tách riêng: schema cũ dùng training_histories cho cả hai việc,
-- nhưng "hội viên vào phòng tập" và "buổi tập với PT" là HAI SỰ KIỆN
-- KHÁC NHAU (vào tập 20 lần/tháng nhưng chỉ tập PT 8 buổi).
-- Thầy yêu cầu "việc hội viên đến tập phải được quản lý" → cần bảng riêng.

access_cards        [MỚI] ⭐ Thẻ RFID/NFC của từng người.
                           → person_id, card_uid, issued_at, revoked_at

qr_secrets          [MỚI] ⭐ Khóa bí mật để app sinh QR động (đổi mỗi 30 giây).
                           Lưu dạng mã hóa. Đây là cơ sở chống chụp màn hình
                           QR gửi cho người khác.
                           → person_id, secret_enc, period_sec

check_ins           [MỚI] ⭐⭐ LƯỢT VÀO/RA PHÒNG TẬP.
                           Ghi cả kết quả (cho vào hay từ chối vì lý do gì)
                           chứ không chỉ ghi lượt thành công — đây là dữ liệu
                           để đo hiệu quả chống thất thoát.
                           → member_id, checked_in_at, checked_out_at,
                             method (QR/RFID/MANUAL/FACE), result (ALLOWED /
                             DENIED_EXPIRED / DENIED_FROZEN...), verified_by,
                             manual_reason (bắt buộc khi check-in tay)

check_in_incidents  [MỚI] ⭐ Sự cố nghi vấn: dùng chung thẻ, quét lại QR cũ,
                           khuôn mặt không khớp.
                           → incident_type, severity, handled_by, resolution



-- ####################################################################
-- ## NHÓM 4. BUỔI TẬP PT & SỔ CÁI             (4 bảng, LÝ DO 3)
-- ####################################################################

trainer_availability [MỚI] ⭐ PT khai báo khung giờ nhận học viên & ngày nghỉ.
                           Là nguồn để hội viên thấy "slot trống".
                           → trainer_id, day_of_week, start_time, end_time,
                             is_available

pt_bookings         [MỚI] ⭐ Yêu cầu đặt lịch của hội viên, chờ PT duyệt.
                           → member_id, trainer_id, requested_start,
                             status (PENDING_TRAINER/CONFIRMED/REJECTED)

pt_sessions         [CŨ→] ⭐⭐ BUỔI TẬP THỰC TẾ (thay thế training_histories).
                           Hai điểm mới quan trọng:
                           (1) XÁC NHẬN HAI CHIỀU: chỉ COMPLETED khi CẢ PT
                               VÀ HỘI VIÊN cùng xác nhận → chống khai khống
                           (2) session_type: PAID_PT / COMPLIMENTARY / TRIAL /
                               ORIENTATION / MAKEUP
                               → buổi COMPLIMENTARY trừ 0 buổi của hội viên
                                 NHƯNG PT VẪN ĐƯỢC TÍNH CÔNG (yêu cầu thầy)
                           → member_id, trainer_id, session_type,
                             scheduled_start/end, actual_start/end, status,
                             trainer_confirmed_at, member_confirmed_at,
                             auto_confirmed (cờ kiểm toán)

session_credit_ledger [MỚI] ⭐⭐⭐ SỔ CÁI TÍN DỤNG BUỔI TẬP — ĐÓNG GÓP CHÍNH.
                           Vì sao không dùng 1 cột `sessions_remaining`:
                           cột đó bị UPDATE liên tục, sai thì không biết sai
                           từ đâu, tranh chấp không có bằng chứng.
                           Thay bằng sổ cái APPEND-ONLY kiểu kế toán:
                             GRANT  +12  khi kích hoạt gói
                             CONSUME -1  khi xác nhận buổi tập
                             REFUND  +1  khi hủy đúng hạn
                             EXPIRE  -n  khi gói hết hạn còn dư
                             ADJUST  ±n  sửa tay, bắt buộc có lý do + người duyệt
                           Không UPDATE, không DELETE. Sai thì ghi bút toán đảo.
                           Bất biến kiểm chứng được:
                             SUM(delta) == balance_after của dòng mới nhất
                           → registration_id, entry_type, delta, balance_after,
                             source_type, source_id, reason, created_by



-- ####################################################################
-- ## NHÓM 5. LỚP HỌC & PHÒNG TẬP              (5 bảng, LÝ DO 4)
-- ####################################################################
-- Vì sao tách 1 bảng `classes` cũ thành 5:
-- bảng cũ trộn 2 khái niệm — code/class_type/maximum_number mô tả LỚP,
-- còn location/is_occupied mô tả PHÒNG. Hệ quả: không lập được lịch lớp
-- lặp hàng tuần, không biết lớp diễn ra lúc mấy giờ, ai dạy.

rooms               [CŨ→] ⭐ KHÔNG GIAN VẬT LÝ (Phòng Yoga 1, Tầng 2 Studio A).
                           → code, name, area_code, capacity, floor, status

class_definitions   [CŨ→] ⭐ ĐỊNH NGHĨA LỚP (Yoga buổi sáng là lớp gì).
                           → code, name, class_type, default_duration_min,
                             default_capacity, level

class_schedules     [MỚI]  (S) LỊCH LẶP HÀNG TUẦN (thứ 2 & 4, 19h, phòng Yoga 1).
                           → class_def_id, room_id, trainer_id, day_of_week,
                             start_time, capacity

class_sessions      [MỚI]  (S) BUỔI LỚP CỤ THỂ ngày 12/08/2026 19:00.
                           → starts_at, ends_at, capacity, booked_count, status

class_bookings      [MỚI]  (S) HỘI VIÊN ĐẶT CHỖ trong 1 buổi lớp.
                           ĐÂY LÀ CÂU TRẢ LỜI CHO CÔ TRINH: hội viên mua gói
                           (registration) rồi DÙNG gói đó đặt nhiều lớp.
                           Nhồi class_id vào registrations như draft chỉ chứa
                           được MỘT lớp, trong khi gói 6 tháng thường học
                           nhiều lớp khác nhau.
                           → class_session_id, member_id, registration_id,
                             status, waitlist_position



-- ####################################################################
-- ## NHÓM 6. THIẾT BỊ & BẢO TRÌ               (3 bảng, LÝ DO 4)
-- ####################################################################
-- Vì sao tách facilities cũ làm 2: bảng cũ nhập nhằng — total_quantity
-- gợi ý là LOẠI thiết bị, nhưng date_of_purchase/warranty_date gợi ý là
-- TỪNG CÁI. Hệ quả: không truy vết được "máy chạy bộ SỐ 3 bị hỏng".

equipment_types     [CŨ→] ⭐ LOẠI thiết bị (máy chạy bộ, tạ đòn).
                           → code, name, category, brand, useful_life_months
                             (cơ sở tính khấu hao)

equipment_items     [CŨ→] ⭐ TỪNG CÁI CỤ THỂ, có mã tài sản riêng.
                           Nhờ vậy hội viên báo hỏng đúng máy nào.
                           → equipment_type_id, room_id, asset_code (TM-003),
                             serial_number, purchase_price, date_of_purchase,
                             warranty_until, status

maintenance_work_orders [MỚI] ⭐ Phiếu sửa chữa. Chi phí sửa tự động thành
                           một dòng chi phí trong expenses.
                           → equipment_item_id, feedback_id, priority,
                             description, status, resolution, cost

-- Ghi chú: bảng class_has_facilities cũ được thay bằng equipment_items.room_id
-- (gán thiết bị cho PHÒNG, không gán cho LỚP) — đúng với câu trả lời của
-- draft: "thiết bị được cung cấp cho classes chứ không khai trong memberships".



-- ####################################################################
-- ## NHÓM 7. THANH TOÁN                       (8 bảng, LÝ DO 2)
-- ####################################################################
-- Schema cũ KHÔNG CÓ BẢNG NÀO VỀ TIỀN. Không có nhóm này thì không làm
-- được module tài chính — mà đó là yêu cầu trung tâm của đề bài.

invoices            [MỚI] ⭐ HÓA ĐƠN — khách nợ bao nhiêu.
                           → invoice_no, member_id, registration_id,
                             total_amount, paid_amount, balance_due, status

invoice_items       [MỚI] ⭐ Chi tiết từng dòng hóa đơn.
                           → item_type, description, quantity, unit_price

payments            [MỚI] ⭐⭐ KHOẢN THU — tiền thực nhận.
                           Tách khỏi invoices vì 1 hóa đơn có thể trả nhiều
                           lần (trả góp), 1 khoản thu có thể trả nhiều hóa đơn.
                           → payment_no, method (CASH/VIETQR/CARD_POS/GATEWAY),
                             amount, status, idempotency_key (chống trừ tiền
                             2 lần khi client retry), provider_txn_id,
                             transfer_content, pos_card_last4, raw_payload

payment_allocations [MỚI] ⭐ Nối payments ↔ invoices (N-N).
                           → payment_id, invoice_id, amount

cash_shifts         [MỚI] ⭐ CA LÀM VIỆC CỦA LỄ TÂN — trả lời "luồng nghiệp vụ
                           của receptionist". Vấn đề thật của tiền mặt không
                           phải ghi nhận, mà là ĐỐI SOÁT.
                           Mở ca ghi số dư đầu → thu tiền trong ca →
                           đóng ca đếm két, lệch thì BẮT BUỘC ghi lý do.
                           → staff_id, opening_balance, expected_cash,
                             counted_cash, difference, difference_reason, status

payment_schedules   [MỚI]  (S) Kỳ hạn trả góp.
                           → registration_id, installment_no, due_date, amount

refunds             [MỚI] ⭐ Hoàn tiền: giá trị còn lại − phí hủy.
                           → gross_amount, penalty_amount, net_amount,
                             reason, approved_by

webhook_events      [MỚI] ⭐ Lưu webhook thô từ ngân hàng/cổng thanh toán.
                           UNIQUE(provider, event_id) chống xử lý trùng khi
                           webhook gửi lại.
                           → provider, event_id, payload, signature_valid,
                             processed



-- ####################################################################
-- ## NHÓM 8. TÀI CHÍNH & LƯƠNG                (10 bảng, LÝ DO 2)
-- ####################################################################
-- Đây là nhóm tạo ra giá trị cho Admin và Kế toán.

revenue_schedules   [MỚI] ⭐⭐ KẾ HOẠCH PHÂN BỔ DOANH THU cho mỗi hợp đồng.
                           Vì sao cần: bán gói 12 tháng thu 9 triệu tháng 1
                           KHÔNG PHẢI là doanh thu tháng 1 — đó là nghĩa vụ
                           phải phục vụ 12 tháng. Nếu ghi hết vào tháng 1 thì
                           chủ phòng gym thấy tháng 1 lãi to, tháng 6 lỗ nặng
                           mà không hiểu vì sao.
                           → registration_id, total_amount,
                             recognition_method (theo NGÀY hay theo BUỔI),
                             recognized_amount, deferred_amount (chưa thực hiện)
                           Bất biến: recognized + deferred == giá trị hợp đồng

revenue_recognition_entries [MỚI] ⭐⭐ BÚT TOÁN GHI NHẬN từng ngày/từng buổi,
                           sinh tự động bởi job chạy đêm. Append-only.
                           → recognition_date, amount, revenue_category,
                             source_type

expense_categories  [MỚI] ⭐ Nhóm chi phí: mặt bằng, điện nước, lương,
                           khấu hao, vệ sinh, marketing, bảo trì.
                           → code, name, is_fixed_cost

expenses            [MỚI] ⭐ CHI PHÍ VẬN HÀNH. Chi phí có kỳ (thuê mặt bằng
                           theo quý) được phân bổ theo tháng giống doanh thu,
                           nếu không thì P&L vô nghĩa.
                           → category_id, amount, expense_date,
                             period_start/end, allocation_method, vendor,
                             attachment_url

payroll_rate_cards  [MỚI] ⭐ ĐƠN GIÁ CÔNG THEO LOẠI BUỔI TẬP.
                           Đây là chỗ trả lời yêu cầu "PT hỗ trợ tập miễn phí
                           vẫn phải được trả lương": PAID_PT 120.000đ/buổi,
                           COMPLIMENTARY 60.000đ/buổi, TRIAL 80.000đ/buổi.
                           → trainer_level, session_type, rate_per_session,
                             valid_from

commission_rules    [MỚI] ⭐ Bậc hoa hồng cho Sale/PT theo doanh số.
                           → role, tier_from_amount, commission_percent

payroll_runs        [MỚI] ⭐ Kỳ chạy lương (tháng 7/2026).
                           → period_year, period_month, status, total_amount,
                             approved_by

payroll_items       [MỚI] ⭐⭐ CHI TIẾT TỪNG DÒNG LƯƠNG.
                           Vì sao cần chi tiết: PT xem được trên app từng buổi
                           mình dạy được tính bao nhiêu → minh bạch, giảm
                           tranh chấp cuối tháng (vấn đề thật của ngành).
                           Toàn bộ dữ liệu lấy từ pt_sessions, KHÔNG nhập tay.
                           → item_type (BASE_SALARY/SESSION_FEE/
                             SALES_COMMISSION/KPI_BONUS/DEDUCTION),
                             quantity, unit_amount, amount, source_id

accounting_periods  [MỚI]  (S) Khóa sổ kỳ kế toán, chống sửa dữ liệu quá khứ.
                           → period_year, period_month, status, closed_by

financial_reports   [MỚI] ⭐ Báo cáo đã sinh (P&L, dòng tiền, deferred revenue).
                           → report_type, period_from/to, data (JSON),
                             narrative, file_url



-- ####################################################################
-- ## NHÓM 9. BÁN HÀNG (CRM)                   (5 bảng, LÝ DO 1)
-- ####################################################################
-- Toàn bộ nhóm này phục vụ role Sale — role chưa có gì trong schema cũ.

leads               [MỚI] ⭐ KHÁCH TIỀM NĂNG chưa phải hội viên.
                           → full_name, phone, source (FB_ADS/HOTLINE/
                             WALK_IN/REFERRAL), assigned_to,
                             stage (NEW→CONTACTED→TRIAL_BOOKED→...→WON/LOST),
                             lost_reason (để phân tích vì sao mất khách)

lead_activities     [MỚI] ⭐ Nhật ký chăm sóc: gọi, nhắn, hẹn.
                           → activity_type, outcome, performed_by

quotes              [MỚI] ⭐ BÁO GIÁ có chiết khấu.
                           Vượt hạn mức trong discount_policies thì
                           requires_approval = true, phải chờ Admin duyệt.
                           → created_by, discount_percent, total_amount,
                             requires_approval, approved_by, status

quote_items         [MỚI] ⭐ Dòng chi tiết báo giá.

-- Ghi chú: chương trình "tập thử 1-2 buổi" (góp ý của thầy) không cần bảng
-- riêng — dùng pt_sessions với session_type = TRIAL, và PT vẫn được tính công
-- theo rate card. Đây là ví dụ cho thấy thiết kế đúng thì không phải đẻ bảng.



-- ####################################################################
-- ## NHÓM 10. BÀI TẬP & CHỈ SỐ CƠ THỂ         (8 bảng, LÝ DO 5)
-- ####################################################################

exercises           [MỚI] ⭐ THƯ VIỆN BÀI TẬP (~870 bài nhập từ Free Exercise
                           DB, public domain, có ảnh). Trả lời "phải có nguồn
                           dữ liệu bài tập có sẵn".
                           → name_vi, name_en, muscle_group, equipment,
                             difficulty, instructions, media_url,
                             source + license (ghi rõ để tuân thủ bản quyền),
                             is_reviewed (bản dịch đã có người rà soát)

workout_templates   [MỚI] ⭐ GIÁO ÁN MẪU ("Tăng cơ 8 tuần cho người mới").
                           → name, goal, level, duration_weeks, days_per_week

workout_template_items [MỚI] ⭐ Bài tập trong giáo án mẫu.
                           → day_index, exercise_id, sets, reps, rest_sec

workout_plans       [MỚI] ⭐ GIÁO ÁN GÁN CHO 1 HỘI VIÊN cụ thể.
                           ai_generated + reviewed_by: nếu AI sinh thì PT
                           BẮT BUỘC phải duyệt (ràng buộc ở tầng DB).
                           → member_id, trainer_id, template_id, start_date,
                             ai_generated, reviewed_by

workout_plan_items  [MỚI] ⭐ Bài tập trong giáo án đã gán.

workout_logs        [MỚI] ⭐ HỘI VIÊN TICK HOÀN THÀNH + ghi kg/reps thực tế.
                           Đây là dữ liệu để hội viên thấy mình tiến bộ.
                           → member_id, exercise_id, performed_at, sets_done,
                             reps_done, weight_kg, rpe

body_metrics        [MỚI] ⭐ CHỈ SỐ CƠ THỂ theo thời gian.
                           bmi là cột TÍNH TỰ ĐỘNG, không nhập tay.
                           source = INBODY_OCR khi chụp phiếu InBody cho LLM
                           đọc — nhưng phải confirmed_by_user mới lưu.
                           → member_id, measured_at, source, height_cm,
                             weight_kg, body_fat_pct, muscle_mass_kg, bmi,
                             waist_cm/hip_cm/..., ocr_confidence,
                             confirmed_by_user



-- ####################################################################
-- ## NHÓM 11. PHẢN HỒI & GIỮ CHÂN             (6 bảng, LÝ DO 5)
-- ####################################################################

feedbacks           [MỚI] ⭐ PHẢN ÁNH của hội viên. Điểm hay: ĐỊNH TUYẾN
                           TỰ ĐỘNG theo loại — báo hỏng thiết bị sinh work
                           order; chê PT vào điểm đánh giá ảnh hưởng lương KPI.
                           Và có thông báo NGƯỢC LẠI cho hội viên khi xử lý
                           xong — đây là thứ tạo niềm tin.
                           → feedback_type (TRAINER/FACILITY/HYGIENE/SERVICE),
                             target_trainer_id, target_equipment_id, rating,
                             content, photo_url, status, sla_due_at, resolution

trainer_ratings     [MỚI] ⭐ Điểm đánh giá PT sau buổi tập (1-5 sao).
                           Admin xem được (yêu cầu đề bài), và là đầu vào KPI.
                           → trainer_id, member_id, pt_session_id, rating

notification_templates [MỚI] (S) Mẫu thông báo.

notifications       [MỚI]  (S) Thông báo gửi tới người dùng.
                           → user_id, channel, title, body, deep_link, status

churn_scores        [MỚI]  (S) ĐIỂM RỦI RO BỎ TẬP tính hàng đêm.
                           factors lưu đóng góp của từng yếu tố → GIẢI THÍCH
                           ĐƯỢC vì sao hội viên này rủi ro cao.
                           → member_id, scored_date, score, risk_level, factors

retention_tasks     [MỚI]  (S) Việc cần làm để giữ chân, giao cho PT hoặc Sale.
                           is_control_group: nhóm đối chứng để ĐO xem can thiệp
                           có thật sự hiệu quả không → thí nghiệm định lượng
                           cho báo cáo.
                           → member_id, task_type, assigned_to, outcome,
                             is_control_group



-- ####################################################################
-- ## NHÓM 12. HỆ THỐNG                        (3 bảng, LÝ DO 6)
-- ####################################################################

system_settings     [MỚI] ⭐ CẤU HÌNH THAY VÌ HARD-CODE.
                           Quy tắc bảo lưu, hạn mức chiết khấu, ngưỡng
                           anti-passback, trọng số churn, ngân sách LLM —
                           Admin sửa được mà không cần sửa code.
                           → key, value (JSON), updated_by

outbox_events       [MỚI] ⭐ OUTBOX PATTERN: ghi DB và phát sự kiện trong
                           CÙNG transaction. Nếu không có, sẽ xảy ra tình
                           trạng "đã thu tiền nhưng hợp đồng chưa kích hoạt".
                           → aggregate_type, event_type, payload, status

llm_usage_logs      [MỚI]  (C) Ghi token & chi phí từng lần gọi LLM.
                           "Không đo được thì không quản trị được" — đây là
                           cơ sở cho phần tính chi phí LLM trong báo cáo.
                           → feature, model, input_tokens, output_tokens,
                             cache_read_tokens, cost_usd, user_id



-- =====================================================================
--  TÓM TẮT: NẾU THỜI GIAN GẤP THÌ LÀM GÌ TRƯỚC?
-- =====================================================================
--
--  BẮT BUỘC (⭐) — ~40 bảng, là lõi đồ án:
--    persons, users, members, trainers, staff, memberships,
--    membership_prices, registrations, registration_freezes,
--    member_trainers, check_ins, access_cards, qr_secrets,
--    pt_sessions, session_credit_ledger, invoices, payments,
--    cash_shifts, revenue_schedules, revenue_recognition_entries,
--    expenses, payroll_rate_cards, payroll_runs, payroll_items,
--    leads, quotes, exercises, workout_plans, body_metrics,
--    feedbacks, equipment_items, audit_logs, system_settings ...
--
--  LÀM SAU (S) — lớp học nhóm, churn, thông báo, trả góp, khóa sổ
--  LÀM NẾU CÒN THỜI GIAN (C) — llm_usage_logs và các tính năng AI
--
--  BA BẢNG QUAN TRỌNG NHẤT, nếu chỉ đọc 3 bảng thì đọc:
--    1. registrations          — hợp đồng, nơi mọi thứ bắt đầu
--    2. session_credit_ledger  — đóng góp học thuật chính
--    3. revenue_schedules      — thứ biến module kế toán thành nghiệp vụ thật
-- =====================================================================
