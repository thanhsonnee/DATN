# Phần 1: Nghiệp vụ, Tính năng theo Role & Phân quyền

## 1. Mô hình danh tính: một người — nhiều tài khoản

Thầy chốt: *"1 người có thể có nhiều hơn 2 tài khoản với các role khác nhau"*. Thiết kế đáp ứng yêu cầu này mà vẫn sạch:

```
persons (id, full_name, gender, birthday, national_id, phone, email, photo_url, ...)
   │  1 con người thật, duy nhất trong hệ thống
   │
   ├──► users (id, person_id, username, password_hash, primary_role, status, ...)
   │       nhiều tài khoản đăng nhập, mỗi tài khoản 1 role chính
   │       VD: person "Nguyễn Văn A" có users: a.pt (role=trainer), a.sale (role=sale)
   │
   ├──► members   (id, person_id, member_code, join_date, ...)   ← hồ sơ hội viên
   └──► trainers  (id, person_id, trainer_code, specialty, ...)  ← hồ sơ HLV
```

**Vì sao tách `persons` ra?**
- Một PT hoàn toàn có thể **đồng thời là hội viên** của chính phòng gym đó → 2 hồ sơ, 1 con người, 1 khuôn mặt để nhận diện, 1 số CCCD.
- Sale kiêm lễ tân ca tối → 2 tài khoản, 2 bảng lương/KPI riêng, nhưng khi tính lương tổng phải gộp về 1 người.
- Chống trùng lặp: một số điện thoại / CCCD chỉ ứng với 1 `person`.

**Bảng `user_roles` — vì sao cần (tùy chọn):** `users.primary_role` cho mỗi tài khoản **đúng 1 vai trò**. Nhưng thực tế có trường hợp một tài khoản cần **kiêm nhiệm 2 vai trò** mà không muốn tạo tài khoản thứ hai — ví dụ phòng gym nhỏ, kế toán kiêm luôn việc lễ tân ca sáng. Khi đó `users.primary_role = 'ACCOUNTANT'` và thêm 1 dòng `user_roles(user_id, 'RECEPTIONIST')`.

Quy tắc phân quyền: **quyền của tài khoản = quyền của `primary_role` HỢP với quyền của tất cả dòng trong `user_roles`.** Cụ thể, tài khoản trên đăng nhập vào sẽ thấy cả menu Kế toán lẫn menu Lễ tân.

> **Nếu thấy phức tạp, hoàn toàn có thể bỏ `user_roles`** và bắt buộc mỗi người kiêm nhiệm phải có 2 tài khoản (đúng như thầy chốt: *"1 người có thể có nhiều hơn 2 tài khoản với các role khác nhau"*). Cách này đơn giản hơn, dễ giải thích hơn khi bảo vệ, và đủ dùng. Khuyến nghị: **giai đoạn đầu bỏ `user_roles`**, chỉ dùng `primary_role`.

**So với schema hiện tại:** `users.role CHECK IN ('admin','member','trainer')` thiếu 3 role, và `CREATE UNIQUE INDEX uq_only_one_admin` giới hạn chỉ 1 admin — cần bỏ, vì nếu tài khoản admin duy nhất bị khóa/quên mật khẩu thì không còn ai quản trị được hệ thống.

---

## 2. Tính năng theo Role

Ký hiệu độ ưu tiên: **[M]** Must have — bắt buộc | **[S]** Should have — nên có | **[C]** Could have — làm nếu còn thời gian
Ký hiệu độ khó: `⬤` đơn giản (CRUD, form, list) · `⬤⬤` trung bình (có logic nghiệp vụ) · `⬤⬤⬤` nâng cao (thuật toán, tích hợp ngoài, real-time)

### 2.1 ADMIN / Chủ phòng gym — *Web*

| # | Tính năng | Ưu tiên | Khó | Ghi chú |
|---|---|:---:|:---:|---|
| A1 | Dashboard tổng quan: doanh thu, hội viên active, check-in hôm nay, tỷ lệ gia hạn, top PT | M | ⬤⬤ | Biểu đồ: doanh thu 12 tháng, cơ cấu gói, churn theo tháng |
| A2 | CRUD gói tập (`memberships`) + quản lý **phiên bản giá** & lịch hiệu lực | M | ⬤⬤ | Không sửa giá trực tiếp — tạo phiên bản giá mới |
| A3 | CRUD nhân sự tất cả role (PT, Sale, Lễ tân, Kế toán) + gán/thu hồi quyền | M | ⬤ | |
| A4 | CRUD hội viên (thay mặt, khi lễ tân nghỉ) | M | ⬤ | |
| A5 | Xem toàn bộ đánh giá của hội viên về PT & cơ sở vật chất | M | ⬤ | Có bộ lọc, xếp hạng PT theo điểm TB |
| A6 | Quản lý check-in/check-out của hội viên & PT (xem, tra cứu, sửa có audit) | M | ⬤⬤ | Sửa phải ghi lý do + audit log |
| A7 | Cấu hình chính sách: quy tắc bảo lưu, hạn mức chiết khấu, rate card lương, trọng số churn | M | ⬤⬤ | **Không hard-code** — đây là điểm phân biệt hệ thống tốt |
| A8 | CRUD lớp học / phòng tập / lịch lớp | M | ⬤⬤ | Có kiểm tra xung đột phòng & PT |
| A9 | Duyệt các yêu cầu vượt hạn mức: chiết khấu lớn, hoàn tiền, bảo lưu ngoại lệ | S | ⬤⬤ | Workflow duyệt |
| A10 | Xem báo cáo tài chính (chỉ đọc, do Kế toán lập) | M | ⬤ | |
| A11 | Cấu hình thông tin phòng gym (tên, địa chỉ, giờ mở/đóng cửa) | S | ⬤ | Lưu trong `system_settings` |
| A12 | Xem audit log toàn hệ thống | S | ⬤ | |
| A13 | Gửi thông báo tới nhóm hội viên/PT | C | ⬤⬤ | Push notification |

### 2.2 MEMBER / Hội viên — *Mobile app (chính) + Web (phụ)*

| # | Tính năng | Ưu tiên | Khó | Ghi chú |
|---|---|:---:|:---:|---|
| M1 | Xem danh sách gói tập & chi tiết | M | ⬤ | |
| M2 | Đăng ký gói online + thanh toán | M | ⬤⬤⬤ | Tích hợp cổng thanh toán / VietQR |
| M3 | **Gói của tôi**: ngày còn lại, buổi PT còn lại, lịch sử giao dịch | M | ⬤⬤ | Đọc từ `session_credit_ledger` |
| M4 | Gia hạn / nâng cấp gói | M | ⬤⬤ | Nâng cấp phải tính bù trừ giá trị còn lại |
| M5 | Yêu cầu bảo lưu | M | ⬤⬤ | Kiểm tra điều kiện tự động |
| M6 | **QR check-in động** | M | ⬤⬤⬤ | HMAC-TOTP 30s, xem tài liệu 04 |
| M7 | Lịch sử check-in + thống kê tần suất tập | M | ⬤ | |
| M8 | Đặt lịch tập với PT: xem PT (hồ sơ, chuyên môn, đánh giá, giá), xem slot trống, đặt/đổi/hủy | M | ⬤⬤⬤ | Lõi của trải nghiệm; cần xử lý concurrency |
| M9 | Xem lịch tập cá nhân (PT + lớp) | M | ⬤⬤ | |
| M10 | Đăng ký lớp học (yoga, group class) | S | ⬤⬤ | Kiểm tra sức chứa, waitlist |
| M11 | **Giáo án hôm nay**: danh sách bài tập, hình minh họa, tick hoàn thành, ghi kg/reps | M | ⬤⬤ | Cần thư viện bài tập |
| M12 | Thư viện bài tập: tìm theo nhóm cơ / thiết bị / độ khó | S | ⬤ | |
| M13 | **Chỉ số cơ thể**: nhập cân nặng/số đo, xem BMI, biểu đồ tiến trình | M | ⬤⬤ | |
| M14 | Upload ảnh phiếu InBody → tự động điền số liệu | C | ⬤⬤⬤ | LLM vision, xem tài liệu 04 |
| M15 | Feedback: đánh giá PT sau buổi tập; báo hỏng thiết bị; góp ý chung | M | ⬤⬤ | Định tuyến tự động |
| M16 | Thông báo (push): sắp hết hạn, nhắc lịch PT, xác nhận thanh toán | S | ⬤⬤ | FCM/APNs |
| M17 | Chatbot hỏi đáp (gói tập, quy định, lịch mở cửa) | C | ⬤⬤⬤ | LLM + RAG |
| M18 | Hồ sơ cá nhân + ảnh đại diện (dùng cho xác minh check-in) | M | ⬤ | |

### 2.3 PERSONAL TRAINER (PT) — *Mobile app (chính) + Web (phụ)*

> **Lưu ý sửa lại so với draft:** trong draft, mục "PT" ghi *"Quản lý user: CRUD Member, CRUD PT"* và *"Quản lý gói tập: CRUD gói tập"* — đây là **quyền của Admin, không phải PT**. PT không được sửa giá gói hay tạo tài khoản PT khác. Cần sửa trong báo cáo.

| # | Tính năng | Ưu tiên | Khó | Ghi chú |
|---|---|:---:|:---:|---|
| T1 | Lịch dạy: hôm nay / tuần / tháng, dạng calendar | M | ⬤⬤ | |
| T2 | Quản lý slot trống: khai báo khung giờ nhận học viên, ngày nghỉ | M | ⬤⬤ | Nguồn cho M8 |
| T3 | Duyệt / từ chối yêu cầu đặt lịch từ hội viên | M | ⬤⬤ | |
| T4 | **Xác nhận buổi tập đã dạy** (2 chiều với hội viên) | M | ⬤⬤⬤ | Cơ chế lõi — sinh credit consume + công |
| T5 | Danh sách học viên phụ trách + hồ sơ, tiến trình, chỉ số | M | ⬤⬤ | **Chỉ học viên của mình** (phân quyền cấp bản ghi) |
| T6 | Soạn giáo án: chọn từ template, tùy biến bài tập/sets/reps, gán cho học viên | M | ⬤⬤ | |
| T7 | Sinh bản nháp giáo án bằng AI (dựa mục tiêu, level, thiết bị sẵn có) | C | ⬤⬤⬤ | **PT phải chỉnh sửa & duyệt** |
| T8 | Nhập chỉ số cơ thể cho học viên | M | ⬤ | |
| T9 | **Bảng lương của tôi**: lương cứng + chi tiết từng buổi dạy + hoa hồng + thưởng, cập nhật real-time | M | ⬤⬤ | Minh bạch — chống tranh chấp |
| T10 | Check-in/check-out ca làm của chính PT (chấm công) | M | ⬤⬤ | |
| T11 | Xem đánh giá hội viên dành cho mình | S | ⬤ | |
| T12 | Cảnh báo học viên có nguy cơ bỏ tập + task chăm sóc | S | ⬤⬤⬤ | Từ churn engine |
| T13 | Ghi nhận buổi hỗ trợ miễn phí (`complimentary`) | M | ⬤⬤ | **Vẫn được tính công** — theo yêu cầu của thầy |
| T14 | Thông báo (push): có lịch mới, học viên hủy, lương đã chốt | S | ⬤⬤ | |

### 2.4 SALE — *Web*

Draft chưa có mô tả cho role này. Đề xuất luồng nghiệp vụ đầy đủ:

| # | Tính năng | Ưu tiên | Khó | Ghi chú |
|---|---|:---:|:---:|---|
| S1 | **Quản lý lead**: tạo lead (khách vãng lai, khách qua Facebook/hotline/giới thiệu), gán nguồn | M | ⬤ | |
| S2 | Pipeline bán hàng dạng kanban: `NEW → CONTACTED → TRIAL_BOOKED → NEGOTIATING → WON / LOST` | M | ⬤⬤ | Có lý do LOST để phân tích |
| S3 | **Đặt lịch tập thử** (1–2 buổi miễn phí) cho lead | M | ⬤⬤ | Trả lời góp ý *"chương trình cho người dùng tập thử 1,2 buổi"*; buổi thử **vẫn tính công cho PT** |
| S4 | **Tạo báo giá**: chọn gói + áp khuyến mãi + chiết khấu **trong hạn mức**, hệ thống hiện giá cuối và biên lợi nhuận | M | ⬤⬤⬤ | Vượt hạn mức → gửi Admin duyệt |
| S5 | Chuyển báo giá thành hợp đồng khi khách đồng ý | M | ⬤⬤ | |
| S6 | **Danh sách hội viên sắp hết hạn** (7/15/30 ngày) — nguồn upsell chính | M | ⬤⬤ | |
| S7 | Nhật ký chăm sóc: gọi điện, nhắn tin, kết quả, lịch follow-up | M | ⬤ | |
| S8 | **KPI & hoa hồng của tôi**: doanh số tháng, số hợp đồng, tỷ lệ chuyển đổi, hoa hồng dự kiến theo bậc | M | ⬤⬤ | |
| S9 | **Dự báo bán hàng**: dựa pipeline + tỷ lệ chuyển đổi lịch sử, ước tính "1 tháng / 1 quý bán được bao nhiêu gói" | S | ⬤⬤⬤ | Trả lời trực tiếp góp ý của thầy |
| S10 | Quản lý chương trình khuyến mãi được phép áp dụng | S | ⬤⬤ | Admin tạo, Sale áp dụng |
| S11 | Nhận task chăm sóc từ churn engine | S | ⬤⬤ | |

### 2.5 RECEPTIONIST / Lễ tân — *Web (màn hình quầy)*

Trả lời câu hỏi *"Receptionist thì sẽ có luồng nghiệp vụ thế nào"*:

| # | Tính năng | Ưu tiên | Khó | Ghi chú |
|---|---|:---:|:---:|---|
| R1 | **Màn hình quầy (Front Desk)**: quẹt thẻ/QR → **bật ngay ảnh hồ sơ + tên + trạng thái gói** cỡ lớn | M | ⬤⬤⬤ | Real-time (WebSocket/SSE). Đây là màn hình quan trọng nhất |
| R2 | Xử lý các trạng thái check-in: hợp lệ / hết hạn / đang bảo lưu / không tìm thấy / nghi ngờ mượn thẻ | M | ⬤⬤ | Mỗi trạng thái có hành động gợi ý |
| R3 | Check-in thủ công (khách quên thẻ & quên điện thoại) — tra theo SĐT/tên, **bắt buộc ghi lý do** | M | ⬤⬤ | Có audit; thống kê tỷ lệ check-in thủ công (chỉ số chất lượng vận hành) |
| R4 | Bán vé lẻ / khách vãng lai (day pass) | M | ⬤⬤ | |
| R5 | **Thu tiền**: tiền mặt / chuyển khoản (sinh VietQR) / POS, in phiếu thu | M | ⬤⬤⬤ | |
| R6 | **Quản lý ca làm**: mở ca (ghi số dư đầu ca) → thu tiền trong ca → **đóng ca: đối soát tiền két vs tổng phiếu thu**, chênh lệch phải ghi lý do | M | ⬤⬤⬤ | Nghiệp vụ thật, rất đáng làm |
| R7 | Đăng ký hội viên mới tại quầy (nhập hồ sơ + **chụp ảnh** + phát thẻ) | M | ⬤⬤ | Ảnh dùng cho R1 |
| R8 | Cấp lại / khóa thẻ RFID | M | ⬤ | |
| R9 | Quản lý tủ đồ (locker): cấp, thu hồi, báo mất | C | ⬤⬤ | |
| R10 | Hỗ trợ đặt lịch PT/lớp thay hội viên | S | ⬤⬤ | |
| R11 | Tiếp nhận & định tuyến khiếu nại tại chỗ | S | ⬤ | |
| R12 | Bảng theo dõi số người đang trong phòng tập (đã check-in chưa check-out) | S | ⬤⬤ | Hữu ích cho an toàn & sức chứa |

**Luồng nghiệp vụ chuẩn của một ca lễ tân:**

```
1. MỞ CA        đăng nhập → nhập số tiền mặt đầu ca → hệ thống tạo cash_shift (OPEN)
2. TRONG CA     ├─ Check-in: quẹt → đối chiếu ảnh → cho vào / xử lý ngoại lệ
                ├─ Thu tiền: tạo hóa đơn → chọn phương thức → xác nhận → in phiếu
                ├─ Đăng ký mới: nhập hồ sơ → chụp ảnh → chọn gói → thu tiền → phát thẻ
                └─ Ngoại lệ: khách quên thẻ / gói hết hạn / khiếu nại
3. ĐÓNG CA      đếm tiền két → nhập số thực tế → hệ thống so với Σ phiếu thu tiền mặt
                ├─ khớp    → đóng ca (CLOSED)
                └─ lệch    → bắt buộc ghi lý do → chuyển trạng thái DISCREPANCY, báo Kế toán
```

### 2.6 ACCOUNTANT / Kế toán — *Web*

| # | Tính năng | Ưu tiên | Khó | Ghi chú |
|---|---|:---:|:---:|---|
| C1 | **Sổ doanh thu**: xem doanh thu ghi nhận theo kỳ, đối chiếu với tiền thực thu | M | ⬤⬤⬤ | Lõi ĐG3 |
| C2 | **Doanh thu chưa thực hiện** (Deferred Revenue): số tiền đã thu còn nợ dịch vụ | M | ⬤⬤⬤ | |
| C3 | **Quản lý chi phí**: nhập chi phí theo nhóm (mặt bằng, điện nước, lương, khấu hao, vệ sinh, marketing, bảo trì), đính kèm chứng từ, phân bổ theo kỳ | M | ⬤⬤ | |
| C4 | **Khấu hao thiết bị** tự động (đường thẳng theo giá mua & tuổi thọ) | S | ⬤⬤ | Từ bảng `facilities` |
| C5 | **Chạy bảng lương**: tính lương toàn bộ nhân sự, xem chi tiết từng dòng, chốt & khóa kỳ | M | ⬤⬤⬤ | Lõi ĐG3 |
| C6 | Đối soát dòng tiền: khớp phiếu thu ↔ sao kê ngân hàng ↔ ca lễ tân | M | ⬤⬤⬤ | |
| C7 | Xử lý hoàn tiền: tính giá trị còn lại, phí hủy, tạo bút toán hoàn | M | ⬤⬤⬤ | |
| C8 | Quản lý công nợ (khách trả góp, chưa thanh toán đủ) | S | ⬤⬤ | |
| C9 | **Báo cáo P&L** (kết quả kinh doanh) theo tháng/quý — có biểu đồ | M | ⬤⬤ | |
| C10 | **Báo cáo dòng tiền** (thu — chi thực tế) | M | ⬤⬤ | |
| C11 | Báo cáo doanh thu theo gói tập / theo nhân viên sale | M | ⬤⬤ | |
| C12 | Phân tích biên lợi nhuận theo loại gói | S | ⬤⬤⬤ | Doanh thu ghi nhận − chi phí phân bổ |
| C13 | **Xuất báo cáo** PDF/Excel gửi chủ phòng gym, có biểu đồ | M | ⬤⬤ | Yêu cầu trực tiếp trong đề bài |
| C14 | Sinh phần diễn giải bằng lời cho báo cáo bằng LLM | C | ⬤⬤ | Từ số liệu đã có, không bịa số |
| C15 | Khóa sổ kỳ kế toán (không cho sửa dữ liệu kỳ đã khóa) | S | ⬤⬤ | |

---

## 3. Ma trận phân quyền (RBAC + Record-level)

### 3.1 Phân quyền theo chức năng (trích, đầy đủ trong code)

| Tài nguyên | Admin | Member | PT | Sale | Receptionist | Accountant |
|---|:---:|:---:|:---:|:---:|:---:|:---:|
| `memberships` (gói tập) | CRUD | R | R | R | R | R |
| `membership_prices` | CRUD | – | – | R | R | R |
| `promotions` | CRUD | R | – | R | R | R |
| `members` | CRUD | R (own) | R (assigned) | CRU | CRU | R |
| `trainers` | CRUD | R | R (own) | R | R | R |
| `users` / phân quyền | CRUD | U (own pwd) | U (own pwd) | U (own pwd) | U (own pwd) | U (own pwd) |
| `registrations` | CRUD | R (own), C | R (assigned) | CRU | CRU | R, U (status) |
| `payments` | R | R (own), C | – | C | CRU | CRUD |
| `check_ins` | CRUD | R (own) | R (own) | R | CRU | R |
| `pt_sessions` | R, U | R (own), U (confirm) | CRU (own) | R | R | R |
| `session_credit_ledger` | R | R (own) | R (assigned) | R | R | R |
| `workout_plans` | R | R (own), U (log) | CRUD (assigned) | – | – | – |
| `body_metrics` | R | CRU (own) | CRU (assigned) | – | – | – |
| `feedbacks` | R, U (resolve) | C, R (own) | R (about self) | R | C, R | – |
| `expenses` | R | – | – | – | – | CRUD |
| `payroll_runs` | R | – | R (own items) | R (own items) | R (own items) | CRUD |
| `revenue_recognition` | R | – | – | – | – | R (system-generated) |
| `financial_reports` | R | – | – | R (sales only) | – | CRUD |
| `audit_logs` | R | – | – | – | – | R |
| `system_settings` | CRUD | – | – | – | – | – |

`C`=Create `R`=Read `U`=Update `D`=Delete · `own`=chỉ bản ghi của mình · `assigned`=chỉ bản ghi được phân công

### 3.2 Phân quyền cấp bản ghi (Record-level) — điểm hay bị bỏ sót

RBAC thuần không đủ. Ví dụ: PT Nguyễn Văn A gọi `GET /api/v1/members/999/body-metrics` — nếu chỉ kiểm tra "role = trainer thì được đọc body_metrics" thì A đọc được hồ sơ học viên của PT khác. Đây là lỗ hổng **IDOR** (Insecure Direct Object Reference), phổ biến và nguy hiểm.

**Giải pháp:** mỗi truy vấn nhạy cảm đi qua một lớp `AccessGuard`:

```java
@PreAuthorize("hasRole('TRAINER')")
@GetMapping("/members/{memberId}/body-metrics")
public List<BodyMetricDto> get(@PathVariable Long memberId, @AuthenticationPrincipal AppUser user) {
    accessGuard.assertTrainerManagesMember(user.trainerId(), memberId); // ném 403 nếu không
    return service.findByMember(memberId);
}
```

Và **test tự động ma trận này**: sinh test case cho `(mỗi endpoint) × (mỗi role) × (bản ghi của mình / của người khác)` → kỳ vọng `200 / 403`. Đây là bộ test bắt buộc, nêu trong `docs/05-KIEM-THU.md`.

### 3.3 Luồng đăng nhập & xác thực (tự cài đặt — theo yêu cầu của thầy)

> Thầy nhấn mạnh *"phải tự làm, hiểu quy trình login/phân quyền"* → **không dùng Keycloak/Auth0/Firebase Auth**. Tự cài đặt bằng Spring Security.

```
1. POST /auth/login {username, password}
   ├─ Tìm user theo username, status = ACTIVE
   ├─ So khớp password với BCrypt hash (cost factor 12)
   ├─ Chống brute-force: đếm lần sai trong Redis, > 5 lần/15 phút → khóa tạm 15 phút
   └─ Trả về:
        access_token  (JWT, HS256/RS256, TTL 15 phút, claims: sub, person_id,
                       role, jti) — client lưu ở memory
        refresh_token (opaque random 256-bit, TTL 30 ngày, hash lưu DB bảng
                       refresh_tokens) — mobile lưu Keychain/Keystore,
                       web lưu HttpOnly + Secure + SameSite=Strict cookie

2. Mỗi request:  Authorization: Bearer <access_token>
   └─ JwtAuthenticationFilter: verify chữ ký → verify exp → kiểm jti trong blacklist
      (Redis) → dựng SecurityContext

3. POST /auth/refresh {refresh_token}
   ├─ Tra hash trong DB, kiểm chưa hết hạn & chưa bị thu hồi
   ├─ REFRESH TOKEN ROTATION: cấp cặp token mới, thu hồi token cũ ngay
   └─ Phát hiện tái sử dụng token cũ (reuse detection) → thu hồi TOÀN BỘ
      phiên của user đó + cảnh báo → chống đánh cắp token

4. POST /auth/logout  → thu hồi refresh token + đưa jti của access token vào blacklist
5. Đổi mật khẩu / bị khóa tài khoản → thu hồi toàn bộ refresh token
```

**Bổ sung:** đăng nhập nhiều tài khoản của cùng 1 `person` → sau khi đăng nhập, nếu `person` có nhiều `users`, hiển thị màn hình **chọn vai trò** (giống Google account switcher). Đây là chi tiết UX trực tiếp phục vụ yêu cầu "1 người nhiều tài khoản".

---

## 4. Bảy luồng nghiệp vụ cốt lõi (Business Flows)

### BF1 — Từ khách lạ đến hội viên (Lead → Member)

```
Khách hỏi thông tin (hotline/FB/đến trực tiếp)
   └─► Sale tạo LEAD (nguồn: fb_ads | hotline | walk_in | referral)
       └─► Sale đặt lịch TẬP THỬ 1 buổi miễn phí, gán PT
           └─► Lễ tân check-in khách thử (tạo person tạm, chưa có member)
               └─► PT dạy buổi thử  ──► pt_sessions(type = TRIAL)
                   │                    └─► PT VẪN ĐƯỢC TÍNH CÔNG (rate card TRIAL)
                   └─► Sale follow-up sau 24h
                       ├─► Khách đồng ý ──► Sale tạo BÁO GIÁ
                       │   ├─ chọn gói + khuyến mãi + chiết khấu
                       │   ├─ nếu chiết khấu > hạn mức → gửi Admin DUYỆT
                       │   └─► Khách chấp nhận ──► tạo REGISTRATION (PENDING_PAYMENT)
                       │       └─► Thanh toán (quầy / VietQR / cổng)
                       │           └─► ĐỦ TIỀN ──► registration = ACTIVE
                       │               ├─ tạo member + tài khoản đăng nhập
                       │               ├─ chụp ảnh hồ sơ, phát thẻ RFID
                       │               ├─ session_credit_ledger: GRANT +N buổi
                       │               ├─ ghi nhận hoa hồng cho Sale
                       │               └─ bắt đầu lịch ghi nhận doanh thu theo kỳ
                       └─► Khách từ chối ──► LOST + lý do (giá/vị trí/đối thủ/chưa cần)
```

### BF2 — Check-in tại quầy

```
Hội viên tới quầy
   ├─ Cách A: mở app → QR động (đổi 30s) → quét
   ├─ Cách B: chạm thẻ RFID lên đầu đọc
   └─ Cách C: đọc SĐT (fallback)
        │
        ▼  POST /api/v1/check-ins   { method, credential, device_id }
   ┌────────────────────────────────────────────────────────────┐
   │ 1. Xác thực credential (verify HMAC-TOTP / tra card_uid)    │
   │ 2. Chống replay: nonce đã dùng? (Redis, TTL 60s) → chặn     │
   │ 3. Anti-passback: đã check-in trong 30 phút? → cảnh báo     │
   │ 4. Tra registration ACTIVE của member                       │
   │ 5. Kiểm trạng thái: hợp lệ / hết hạn / bảo lưu / chưa TT    │
   └────────────────────────────────────────────────────────────┘
        │
        ▼  Đẩy real-time (WebSocket) lên màn hình lễ tân:
   ┌────────────────────────────────────────────────────────────┐
   │  [ẢNH HỒ SƠ LỚN]   NGUYỄN VĂN A   —   MB-000123             │
   │  Gói Fitness 6 tháng · còn 47 ngày · ✅ HỢP LỆ              │
   │  PT: Trần B · còn 5/12 buổi                                │
   │  ⚠ Lượt vào thứ 2 trong 30 phút — kiểm tra                 │
   └────────────────────────────────────────────────────────────┘
        │
        ├─ Lễ tân đối chiếu ảnh ⟷ người thật
        ├─ (tùy chọn) camera chụp → so 1:1 với ảnh hồ sơ → điểm tương đồng
        └─► Xác nhận ──► ghi check_ins(member_id, at, method, verified_by, photo_ref)
                          └─► cập nhật last_visit_at → churn engine tính lại điểm
```

**Xử lý ngoại lệ:**

| Tình huống | Hành động của hệ thống |
|---|---|
| Gói hết hạn | Hiện đỏ + nút "Gia hạn ngay" (mở luồng thanh toán) hoặc "Mua vé lẻ" |
| Đang bảo lưu | Hiện vàng + nút "Kết thúc bảo lưu sớm" |
| Chưa thanh toán đủ (trả góp) | Hiện vàng + số tiền còn nợ + nút thu tiền |
| Không tìm thấy | Tra theo SĐT/tên → check-in thủ công có ghi lý do |
| Nghi mượn thẻ | Cảnh báo + bắt buộc lễ tân xác nhận bằng CCCD, ghi vào `check_in_incidents` |

### BF3 — Đặt lịch & thực hiện buổi PT (luồng đầy đủ theo góp ý của thầy)

```
Hội viên (app) → chọn PT (xem hồ sơ, chuyên môn, điểm đánh giá, giá/buổi)
   └─► Xem slot trống của PT (từ trainer_availability, trừ các buổi đã đặt)
       └─► Chọn slot → hệ thống HIỂN THỊ:
             • giá tiền/buổi (nếu chưa có gói PT) hoặc "trừ 1/12 buổi" (nếu có gói)
             • lịch tập dự kiến, địa điểm
             • chính sách hủy (hủy trước 4h: hoàn credit; muộn hơn: mất buổi)
           └─► Hội viên XÁC NHẬN
               ├─ Chưa có gói PT ──► tạo hóa đơn ──► THANH TOÁN ──► tiếp
               └─ Đã có gói PT   ──► kiểm tra số dư credit > 0 ──► tiếp
                   └─► booking = PENDING_TRAINER
                       └─► PT nhận PUSH → duyệt / từ chối (đề xuất giờ khác)
                           └─► PT DUYỆT ──► booking = CONFIRMED
                               ├─ push cho hội viên + PT
                               ├─ tạo pt_session(SCHEDULED)
                               └─ đặt reminder T-2h
   ...đến ngày tập...
   └─► Hội viên check-in vào phòng gym (BF2)
       └─► PT bấm "Bắt đầu buổi tập" (session = IN_PROGRESS)
           └─► PT bấm "Kết thúc" → sinh QR xác nhận
               └─► Hội viên quét QR của PT  (hoặc PT gửi yêu cầu, HV duyệt trong app)
                   └─► XÁC NHẬN 2 CHIỀU ──► session = COMPLETED
                       ├─► session_credit_ledger: CONSUME −1  (nếu type = PAID_PT)
                       ├─► payroll_items: +1 dòng công cho PT theo rate card
                       ├─► revenue_recognition: ghi nhận 1/12 giá trị gói PT
                       ├─► mở form đánh giá cho hội viên (1–5 sao + nhận xét)
                       └─► cập nhật churn score

   Nếu hội viên không xác nhận trong 24h → auto-confirm nhưng gắn cờ
   `auto_confirmed = true` để kiểm toán (thống kê tỷ lệ này là chỉ số chất lượng)
```

**Buổi hỗ trợ miễn phí (yêu cầu Elite Fitness của thầy):** PT chọn `session_type = COMPLIMENTARY` → **credit trừ 0**, nhưng **`payroll_items` vẫn sinh 1 dòng công** với đơn giá riêng trong rate card. Admin đặt hạn mức số buổi complimentary/PT/tháng để tránh lạm dụng.

### BF4 — Bảo lưu gói tập

```
Hội viên gửi yêu cầu bảo lưu (app) hoặc lễ tân tạo hộ
   └─► Hệ thống KIỂM TRA TỰ ĐỘNG theo config:
         ✓ registration đang ACTIVE
         ✓ gói có duration_days ≥ 90
         ✓ tổng ngày đã bảo lưu trong năm hợp đồng + ngày xin ≤ 30
         ✓ số lần bảo lưu < 2
         ✓ ngày bắt đầu ≥ hôm nay + 3 (trừ trường hợp y tế có giấy tờ)
       ├─ Đạt hết ──► APPROVED tự động
       ├─ Vi phạm 1 điều kiện, có lý do y tế + file đính kèm ──► chờ Admin duyệt
       └─ Vi phạm ──► REJECTED, hiện lý do cụ thể
   └─► Khi APPROVED:
       ├─ tạo registration_freezes(reg_id, from, to, days, reason, approved_by)
       ├─ registration.status = FROZEN (đến from_date thì hiệu lực)
       ├─ registration.end_date += số ngày bảo lưu
       ├─ TẠM DỪNG ghi nhận doanh thu trong khoảng bảo lưu (quan trọng!)
       └─ check-in trong khoảng bảo lưu bị từ chối (trừ khi kết thúc sớm)
   └─► Đến to_date (hoặc hội viên bấm "kết thúc sớm"):
       └─ status về ACTIVE, tiếp tục ghi nhận doanh thu
```

### BF5 — Chu trình tài chính hàng ngày / hàng tháng

```
HÀNG NGÀY (job 00:15)
├─ Quét registrations ACTIVE → sinh revenue_recognition_entries cho ngày hôm trước
│  ├─ gói theo thời hạn: giá_trị_hợp_đồng / tổng_số_ngày  (bỏ qua ngày bảo lưu)
│  └─ gói theo buổi: ghi nhận theo pt_sessions COMPLETED hôm qua
├─ Phân bổ chi phí có kỳ (thuê mặt bằng, bảo hiểm...) theo ngày
├─ Tính khấu hao thiết bị
├─ Cập nhật registrations hết hạn → COMPLETED, ledger ghi EXPIRE số credit dư
├─ Tính lại churn_risk_score toàn bộ hội viên active
└─ Sinh task chăm sóc cho hội viên risk = HIGH

HÀNG THÁNG (Kế toán chủ động chạy)
├─ 1. Đối soát dòng tiền: Σ payments ↔ Σ cash_shifts ↔ sao kê ngân hàng
├─ 2. Chốt chi phí tháng, upload chứng từ còn thiếu
├─ 3. CHẠY BẢNG LƯƠNG
│     ├─ với mỗi nhân sự: lương cứng + Σ(buổi × rate) + Σ hoa hồng + thưởng − khấu trừ
│     ├─ sinh payroll_run(DRAFT) + payroll_items chi tiết
│     ├─ PT/Sale xem & phản hồi trong 3 ngày (minh bạch → giảm tranh chấp)
│     └─ Kế toán chốt → APPROVED → tạo expense lương → khóa
├─ 4. Sinh báo cáo:  P&L · Dòng tiền · Doanh thu theo gói/sale · Deferred revenue
├─ 5. (tùy chọn) LLM viết phần diễn giải từ số liệu đã có
└─ 6. Xuất PDF/Excel + KHÓA SỔ kỳ
```

### BF6 — Phản hồi & bảo trì thiết bị

```
Hội viên báo "Máy chạy bộ số 3 kêu to" (app, có thể kèm ảnh)
   └─► feedbacks(type = FACILITY, facility_id, severity, description, photo)
       └─► ĐỊNH TUYẾN TỰ ĐỘNG:
           ├─ type = FACILITY ──► tạo maintenance_work_order(OPEN), gán kỹ thuật/Admin
           │                       └─ facility.status = NEEDS_REPAIR (ẩn khỏi lịch lớp)
           ├─ type = TRAINER  ──► vào trainer_ratings → ảnh hưởng KPI lương PT
           │                       └─ nếu ≤ 2 sao → tạo task cho Admin xử lý trong 24h
           ├─ type = HYGIENE  ──► task cho Admin/quản lý ca
           └─ type = GENERAL  ──► hàng đợi Admin
       └─► Xử lý: OPEN → IN_PROGRESS → RESOLVED (ghi giải pháp, chi phí sửa)
           ├─ chi phí sửa ──► tự động tạo expense(category = MAINTENANCE)
           └─ thông báo NGƯỢC LẠI cho hội viên đã báo → "đã sửa xong, cảm ơn bạn"
                                                          ↑ điểm tạo niềm tin (N7)
```

### BF7 — Giữ chân hội viên (Retention loop)

```
Job hàng đêm tính churn_risk_score cho mọi hội viên ACTIVE
   └─► Phân nhóm: LOW / MEDIUM / HIGH
       └─► HIGH ──► tạo retention_task, gán cho:
                     • PT phụ trách (nếu có gói PT)  → "gọi hỏi thăm"
                     • Sale (nếu gói sắp hết hạn)     → "chào gia hạn có ưu đãi"
           └─► Người phụ trách thực hiện → ghi kết quả (đã liên hệ / không nghe máy / đã gia hạn)
               └─► ĐO HIỆU QUẢ: so tỷ lệ gia hạn của nhóm ĐƯỢC can thiệp
                   với nhóm KHÔNG can thiệp (nhóm đối chứng)
                   → đây là THÍ NGHIỆM ĐỊNH LƯỢNG, đưa vào báo cáo ĐATN
```

---

## 5. Phân loại tính năng theo độ khó (để trả lời trực tiếp yêu cầu đề bài)

### Tính năng đơn giản — bắt buộc phải có (nền móng)
CRUD gói tập · CRUD nhân sự & hội viên · CRUD lớp/phòng · CRUD thiết bị · Đăng nhập/phân quyền cơ bản · Xem lịch sử check-in · Xem đánh giá · Danh sách bài tập · Nhập chỉ số cơ thể thủ công · Xuất Excel

### Tính năng trung bình — bắt buộc (nơi nghiệp vụ thật nằm)
Máy trạng thái hợp đồng · Bảo lưu có quy tắc · Snapshot giá & phiên bản giá · Kiểm soát chiết khấu theo hạn mức · Đặt lịch PT có kiểm tra xung đột · Ca làm & đối soát tiền mặt · Định tuyến feedback · Dashboard có biểu đồ · Phân bổ chi phí · Khấu hao thiết bị · Push notification

### Tính năng nâng cao — điểm nhấn của đồ án
| Tính năng | Vì sao là nâng cao |
|---|---|
| **Session Credit Ledger + bất biến** | Mô hình dữ liệu append-only, kiểm chứng bằng property-based testing |
| **QR động HMAC-TOTP + anti-replay** | Mật mã ứng dụng, xử lý đồng bộ thời gian, chống tấn công |
| **Ghi nhận doanh thu phân bổ (deferred revenue)** | Nghiệp vụ kế toán thật, thuật toán phân bổ theo ngày & theo buổi |
| **Engine lương & hoa hồng nhiều tầng** | Rule engine có cấu hình, phải đúng tuyệt đối |
| **Tích hợp thanh toán (VietQR + cổng + webhook)** | Idempotency, chữ ký, outbox pattern, distributed consistency |
| **Đối sánh khuôn mặt 1:1** | ONNX inference, tuân thủ pháp lý dữ liệu sinh trắc |
| **Churn scoring + đo hiệu quả can thiệp** | Phân tích dữ liệu, thiết kế thí nghiệm có nhóm đối chứng |
| **Tầng AI có kiểm soát chi phí** | RAG, model routing, prompt caching, budget guard |
| **Realtime front-desk (WebSocket)** | Đồng bộ trạng thái nhiều thiết bị |
| **Mobile app 2 vai (Member + PT)** | Kiến trúc đa nền tảng, offline-first cho check-in |
