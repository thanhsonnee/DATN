# CÁC QUAN HỆ GIỮA CÁC BẢNG

> Đối chiếu với `db/31_bang.md` (32 bảng), đã cập nhật theo quyết định 12 & 13
> (`registrations` bỏ `lead_id`, `member_id` là `NOT NULL`; `leads` bắt buộc liên kết `persons`).

---

## 1. Nhóm danh tính

**Persons - Users (1 - N):** Một con người có thể có nhiều tài khoản đăng nhập với vai trò khác nhau (thông qua `person_id`). Ví dụ một huấn luyện viên vừa có tài khoản `TRAINER` để dạy, vừa có tài khoản `MEMBER` để tự tập.

**Persons - Members (1 - 1):** Mỗi hồ sơ hội viên gắn với duy nhất một con người (thông qua `person_id`, ràng buộc `UNIQUE`). Chỉ tạo khi người đó chốt mua gói đầu tiên.

**Persons - Employees (1 - 1):** Mỗi hồ sơ nhân sự gắn với duy nhất một con người (thông qua `person_id`, ràng buộc `UNIQUE`).

**Persons - Leads (1 - 1):** Mỗi khách hàng tiềm năng là một con người thật (thông qua `person_id`, `NOT NULL`). Nhờ `persons.phone` là `UNIQUE`, cùng một người gọi hotline hôm nay rồi tự tải app hôm sau chỉ tồn tại một dòng duy nhất.

**Users - Password_reset_tokens (1 - N):** Một tài khoản có thể yêu cầu đặt lại mật khẩu nhiều lần, mỗi lần sinh một token riêng (thông qua `user_id`).

(BỎ)**Users - Users (1 - N):** Quan hệ tự tham chiếu — một Admin có thể khóa nhiều tài khoản khác (thông qua `locked_by`).

**Users - Audit_logs (1 - N):** Một tài khoản thực hiện nhiều thao tác được ghi nhật ký (thông qua `actor_id`).

**Members - Members (1 - N):** Quan hệ tự tham chiếu — một hội viên có thể giới thiệu nhiều hội viên khác (thông qua `referred_by`).

---

## 2. Nhóm gói tập và hợp đồng

**Members - Registrations (1 - N):** Một hội viên có thể ký nhiều hợp đồng khác nhau qua thời gian (gia hạn, mua thêm gói PT). Thông qua `member_id`, bắt buộc `NOT NULL` — mọi hợp đồng đều thuộc về một hội viên thật.

**Memberships - Registrations (1 - N):** Một gói tập trong danh mục có thể được rất nhiều hội viên đăng ký (thông qua `membership_id`). Các điều khoản thương mại được sao chép (snapshot) sang hợp đồng lúc ký, nên đổi giá gói không làm thay đổi hợp đồng cũ.

**Employees - Registrations (1 - N):** Một nhân viên Sale có thể bán nhiều hợp đồng, là cơ sở tính hoa hồng (thông qua `sold_by`).

**Employees - Registrations (1 - N):** Một huấn luyện viên có thể được phân công phụ trách nhiều hợp đồng gói PT (thông qua `assigned_trainer_id`).

(BỎ - DỰ KIẾN SỬA LẠI: 1-1 - 1 hợp đồng chỉ được bảo lưu 1 lần)**Registrations - Registration_freezes (1 - N):** Một hợp đồng có thể được bảo lưu nhiều lần, tối đa 2 lần mỗi năm (thông qua `registration_id`). Không cho phép hai khoảng bảo lưu chồng nhau.

**Members - Member_trainers - Employees (N - N):** Một hội viên có thể được nhiều huấn luyện viên cùng chăm sóc (PT chính, PT phụ, PT dạy thay), và một huấn luyện viên phụ trách nhiều hội viên. Bảng `member_trainers` giải quyết quan hệ nhiều-nhiều này, kèm theo vai trò (`role`) và khoảng thời gian phụ trách (`from_date`/`to_date`).

---

## 3. Nhóm check-in

**Members - Check_ins (1 - N):** Một hội viên có nhiều lượt vào/ra phòng tập (thông qua `member_id`).

**Employees - Check_ins (1 - N):** Một nhân viên có nhiều lượt chấm công vào/ra ca làm việc (thông qua `employee_id`).

**Registrations - Check_ins (1 - N):** Mỗi lượt check-in được ghi nhận là sử dụng hợp đồng nào để vào tập (thông qua `registration_id`).

**Users - Check_ins (1 - N):** Một lễ tân xác nhận nhiều lượt check-in thủ công hoặc xử lý nhiều sự cố (thông qua `verified_by` và `incident_handled_by`).

---

## 4. Nhóm buổi tập PT và sổ cái

**Members - PT_sessions (1 - N):** Một hội viên có nhiều buổi tập với huấn luyện viên (thông qua `member_id`).

**Employees - PT_sessions (1 - N):** Một huấn luyện viên dạy nhiều buổi tập khác nhau (thông qua `trainer_id`). Tiền công được tính cho huấn luyện viên thực tế dạy buổi đó, không phải huấn luyện viên chính được phân công.

**Registrations - PT_sessions (1 - N):** Một hợp đồng gói PT được sử dụng cho nhiều buổi tập, mỗi buổi trừ đi một buổi trong tổng số đã mua (thông qua `registration_id`).

**Registrations - Session_credit_ledger (1 - N):** Một hợp đồng có một sổ cái gồm nhiều bút toán ghi nhận biến động số buổi tập (cấp buổi, tiêu thụ, hoàn lại, hết hạn, điều chỉnh). Đây là bảng chỉ ghi thêm, không cho phép sửa hay xóa.

**PT_sessions - Session_credit_ledger (1 - 1):** Mỗi buổi tập đã hoàn thành sinh đúng một bút toán trừ buổi, liên kết qua cặp `source_type` = `PT_SESSION` và `source_id`. Ràng buộc `UNIQUE` bảo đảm một buổi tập không bị trừ credit hai lần.

---

## 5. Nhóm lớp học nhóm

**Employees - Class_sessions (1 - N):** Một huấn luyện viên đứng lớp nhiều buổi học nhóm khác nhau (thông qua `trainer_id`).

**Members - Class_bookings - Class_sessions (N - N):** Một hội viên đặt chỗ nhiều buổi lớp khác nhau, và một buổi lớp có nhiều hội viên tham gia trong giới hạn sức chứa. Bảng `class_bookings` giải quyết quan hệ nhiều-nhiều này, kèm trạng thái đặt chỗ và vị trí hàng chờ.

**Registrations - Class_bookings (1 - N):** Một hợp đồng được dùng để đặt nhiều lớp học khác nhau (thông qua `registration_id`, `NOT NULL`). Không có hợp đồng hợp lệ thì không đặt được lớp.

---

## 6. Nhóm thanh toán

**Registrations - Invoices (1 - N):** Một hợp đồng sinh ra hóa đơn tương ứng (thông qua `registration_id`). Thực tế mỗi hợp đồng thường chỉ có một hóa đơn — mua hai gói thì tạo hai hóa đơn riêng.

**Members - Invoices (1 - N):** Một hội viên có nhiều hóa đơn qua thời gian (thông qua `member_id`).

**Invoices - Payments (1 - N):** Một hóa đơn có thể được thanh toán làm nhiều lần, và cũng ghi nhận cả bút toán hoàn tiền (thông qua `invoice_id`). Tổng các khoản thu trừ đi hoàn tiền chính là số tiền phòng gym thực sự giữ lại.

**Cash_shifts - Payments (1 - N):** Một ca làm việc của lễ tân bao gồm nhiều khoản thu tiền mặt (thông qua `cash_shift_id`). Mọi khoản thu tiền mặt bắt buộc phải gắn với một ca, bảo đảm không có tiền mặt nào lọt ngoài sổ.

**Employees - Cash_shifts (1 - N):** Một lễ tân mở nhiều ca làm việc qua thời gian, nhưng tại một thời điểm chỉ được có tối đa một ca đang mở (thông qua `employee_id`).

**Payments - Payments (1 - N):** Quan hệ tự tham chiếu — một khoản thu có thể bị hoàn lại một phần hoặc toàn bộ, khoản hoàn tiền trỏ ngược về khoản thu gốc (thông qua `refund_of_payment_id`).

---

## 7. Nhóm tài chính và lương

**Registrations - Revenue_schedules (1 - 1):** Mỗi hợp đồng có đúng một kế hoạch phân bổ doanh thu (thông qua `registration_id`, ràng buộc `UNIQUE`), xác định cách ghi nhận doanh thu theo thời gian hay theo số buổi tiêu thụ.

**Revenue_schedules - Revenue_recognition_entries (1 - N):** Một kế hoạch phân bổ sinh ra nhiều bút toán ghi nhận doanh thu — mỗi ngày một bút toán với gói theo thời gian, hoặc mỗi buổi tập một bút toán với gói theo buổi (thông qua `schedule_id`).

**Payroll_runs - Payroll_items (1 - N):** Một kỳ chạy lương gồm nhiều dòng lương chi tiết của toàn bộ nhân sự (thông qua `payroll_run_id`).

**Employees - Payroll_items (1 - N):** Một nhân viên có nhiều dòng lương trong cùng một kỳ: lương cứng, tiền công từng buổi tập, hoa hồng, thưởng KPI, khấu trừ, phạt (thông qua `employee_id`).

**PT_sessions - Payroll_items (1 - 1):** Mỗi buổi tập đã hoàn thành sinh đúng một dòng tiền công cho huấn luyện viên, liên kết qua `source_type` = `PT_SESSION` và `source_id`. Ràng buộc `UNIQUE` bảo đảm một buổi tập không được tính công hai lần. Kể cả buổi hỗ trợ miễn phí — hội viên không bị trừ buổi nhưng huấn luyện viên vẫn được trả công.

**Payroll_runs - Expenses (1 - N):** Một kỳ lương sinh ra các dòng chi phí lương tương ứng trong sổ chi phí (thông qua `payroll_run_id`).

**Equipment - Expenses (1 - N):** Mỗi thiết bị sinh ra một dòng chi phí khấu hao mỗi tháng, tính bằng giá mua chia cho tuổi thọ tính bằng tháng (thông qua `equipment_id`).

**Feedbacks - Expenses (1 - 1):** Một phản ánh về thiết bị khi được xử lý xong và có chi phí sửa chữa sẽ tự động sinh một dòng chi phí bảo trì (thông qua `feedback_id` và `expense_id` trỏ ngược lại nhau).

---

## 8. Nhóm bán hàng

**Memberships - Leads (1 - N):** Một gói tập có thể được nhiều khách hàng tiềm năng quan tâm (thông qua `interested_membership_id`). Đây là cơ sở thống kê gói nào thu hút nhiều khách nhưng tỷ lệ chốt thấp.

**Employees - Leads (1 - N):** Một nhân viên Sale phụ trách chăm sóc nhiều khách hàng tiềm năng (thông qua `assigned_to`).

---

## 9. Nhóm bài tập và chỉ số cơ thể

**Members - Workout_plans (1 - N):** Một hội viên có thể được giao nhiều giáo án qua các giai đoạn tập luyện khác nhau (thông qua `member_id`).

**Employees - Workout_plans (1 - N):** Một huấn luyện viên soạn nhiều giáo án cho các học viên khác nhau (thông qua `trainer_id`).

**Workout_plans - Workout_plans (1 - N):** Quan hệ tự tham chiếu — một giáo án mẫu có thể được dùng làm cơ sở để tạo ra nhiều giáo án cá nhân hóa (thông qua `source_template_id`). Giáo án mẫu chính là giáo án chưa gán cho hội viên nào.

**Workout_plans - Workout_plan_items (1 - N):** Một giáo án gồm nhiều bài tập, sắp xếp theo ngày trong chu kỳ và thứ tự trong ngày (thông qua `plan_id`).

**Exercises - Workout_plan_items (1 - N):** Một bài tập trong thư viện có thể xuất hiện trong rất nhiều giáo án khác nhau (thông qua `exercise_id`).

**Workout_plan_items - Workout_logs (1 - N):** Một bài tập trong giáo án được hội viên thực hiện nhiều lần qua các buổi tập, mỗi lần ghi lại một nhật ký (thông qua `plan_item_id`).

**Members - Workout_logs (1 - N):** Một hội viên có rất nhiều bản ghi nhật ký tập luyện, là cơ sở vẽ biểu đồ tiến bộ theo thời gian (thông qua `member_id`).

**PT_sessions - Workout_logs (1 - N):** Một buổi tập với huấn luyện viên sinh ra nhiều bản ghi nhật ký, mỗi bài tập một bản ghi (thông qua `pt_session_id`). Nếu để trống nghĩa là hội viên tự tập.

**Members - Body_metrics (1 - N):** Một hội viên có nhiều lần đo chỉ số cơ thể qua thời gian, là cơ sở theo dõi tiến trình giảm mỡ hoặc tăng cơ (thông qua `member_id`).

**Employees - Body_metrics (1 - N):** Một huấn luyện viên thực hiện đo chỉ số cho nhiều hội viên (thông qua `measured_by`). Để trống nghĩa là hội viên tự nhập.

---

## 10. Nhóm phản hồi

**Members - Feedbacks (1 - N):** Một hội viên gửi nhiều phản ánh về huấn luyện viên, cơ sở vật chất, vệ sinh hoặc dịch vụ (thông qua `member_id`).

**Employees - Feedbacks (1 - N):** Một huấn luyện viên hoặc nhân viên nhận nhiều đánh giá từ hội viên (thông qua `target_employee_id`). Điểm trung bình được cập nhật vào `employees.rating_avg` và ảnh hưởng tới thưởng KPI trong bảng lương.

**Equipment - Feedbacks (1 - N):** Một thiết bị có thể bị phản ánh hỏng hóc nhiều lần qua thời gian (thông qua `target_equipment_id`). Khi có phản ánh, trạng thái thiết bị tự chuyển sang cần sửa chữa và thiết bị bị ẩn khỏi lịch lớp.

**PT_sessions - Feedbacks (1 - 1):** Mỗi buổi tập chỉ được hội viên đánh giá đúng một lần (thông qua `pt_session_id`, ràng buộc `UNIQUE` với `member_id`) nhằm chống spam điểm.

---

## 11. Bảng độc lập

**System_settings:** Không có quan hệ khóa ngoại với bảng nào. Đây là kho cấu hình dạng khóa–giá trị, chứa toàn bộ tham số vận hành: điều kiện bảo lưu, đơn giá công theo loại buổi tập, bậc hoa hồng, hạn mức chiết khấu, ngân sách chatbot, chính sách bảo mật. Cho phép Admin đổi chính sách mà không cần lập trình viên can thiệp.

**Audit_logs:** Chỉ liên kết với `users` qua `actor_id`. Các cột `entity_type` và `entity_id` trỏ tới bản ghi bất kỳ trong hệ thống theo kiểu đa hình, không dùng khóa ngoại cứng.
