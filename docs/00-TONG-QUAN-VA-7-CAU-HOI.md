# ĐATN — Hệ thống Quản lý Phòng Gym
## Phần 0: Tổng quan & Trả lời 7 câu hỏi định hướng
1. Nhu cầu của người dùng ứng dụng/giải pháp. 2. Các vấn đề em cần giải quyết, liên kết đến các nhu cầu của người dùng như thế nào. 3. Giải pháp cho từng vấn đề. 4. Liên kết các giải pháp đó trong 1 kiến trúc tổng thể như thế nào. 5. Em test như thế nào để kết luận là giải pháp của em đáp ứng đúng vấn đề cần giải quyết ? 6. Em test như thế nào để kết luận là app của em đáp ứng đúng nhu cầu của người dùng. 7. Theo em đâu là đóng góp chính của ĐATN ? Outcome của từng đóng góp đối với vấn đề cần giải quyết và nhu cầu người dùng tương ứng?

> Tài liệu này là **xương sống** của đồ án. Các tài liệu 01–06 là phần triển khai chi tiết cho từng ý ở đây.
Note:
1. giả định làm việc

**Lưu ý về số liệu:** các con số thị trường trong tài liệu là **giả định làm việc** dựa trên quan sát mô hình vận hành phòng gym tầm trung tại Việt Nam, **cần được xác thực bằng khảo sát thực tế** (phỏng vấn 3–5 phòng gym) trước khi đưa vào báo cáo chính thức. Chỗ nào là giả định đều được đánh dấu `[GĐ]`.

---

## 1. Nhu cầu của người dùng

Hệ thống có 6 role, nhưng nhu cầu thực sự chia thành **3 nhóm lợi ích** khác nhau — đây là điểm cần làm rõ vì nó quyết định kiến trúc:

### Nhóm A — Người trả tiền (Hội viên)

| Nhu cầu | Diễn giải | Hiện trạng thường gặp |
|---|---|---|
| N1. Biết mình đang có gì | Gói còn bao nhiêu ngày / bao nhiêu buổi PT, khi nào hết hạn | Phải hỏi lễ tân hoặc nhắn Zalo PT |
| N2. Vào tập nhanh, không phiền | Check-in dưới 5 giây, không cần mang thẻ giấy | Quẹt thẻ từ, quên thẻ thì tra tay |
| N3. Đặt lịch PT / lớp chủ động | Xem lịch trống của PT, đặt, đổi, hủy | Nhắn tin thỏa thuận, dễ trùng lịch |
| N4. Biết mình có tiến bộ không | Cân nặng, body fat, số đo, khối lượng tạ nâng theo thời gian | Ghi sổ tay hoặc không ghi |
| N5. Biết hôm nay tập gì | Giáo án cụ thể, có hình minh họa, tick hoàn thành | PT viết tay ra giấy |
| N6. Thanh toán / gia hạn thuận tiện | Chuyển khoản, quét QR, không phải đến quầy | Đến quầy, tiền mặt |
| N7. Được lắng nghe | Phản ánh về PT, thiết bị hỏng, vệ sinh | Nói miệng, thường rơi vào quên lãng |
| N8. Bảo lưu khi bận/ốm | Tạm dừng gói, không mất tiền oan | Xin xỏ, tùy cảm tính quản lý |

**Kênh ưu tiên: mobile app** — hội viên dùng điện thoại ngay trong phòng tập, giữa các set.

### Nhóm B — Người vận hành (PT, Sale, Lễ tân)

| Role | Nhu cầu chính |
|---|---|
| **PT** | Lịch dạy hôm nay/tuần này; xác nhận buổi đã dạy để **được tính công**; xem lương dự kiến real-time (lương cứng + hoa hồng + tiền buổi); soạn giáo án nhanh; theo dõi tiến độ học viên; biết học viên nào sắp bỏ tập |
| **Sale** | Danh sách lead & pipeline; danh sách hội viên **sắp hết hạn** (nguồn upsell chính); báo giá có chiết khấu **trong hạn mức được duyệt**; theo dõi KPI & hoa hồng của mình theo thời gian thực |
| **Lễ tân** | Xác minh **đúng người** khi check-in (chống mượn thẻ); thu tiền nhiều hình thức; in phiếu thu; xử lý khách vãng lai / khách tập thử; **chốt ca đối soát tiền mặt** cuối ca |

**Kênh ưu tiên:** PT → mobile (di chuyển trong sàn tập). Sale & Lễ tân → web (ngồi quầy, nhập liệu nhiều, cần màn hình lớn).

### Nhóm C — Người ra quyết định (Chủ phòng gym / Admin, Kế toán)

| Role | Nhu cầu chính |
|---|---|
| **Admin / Chủ** | Tháng này **lãi hay lỗ thật** (không phải "thu được bao nhiêu tiền"); doanh thu theo gói tập / theo nhân viên sale; tỷ lệ gia hạn & churn; đánh giá của hội viên về PT và cơ sở vật chất; CRUD gói tập & nhân sự; duyệt chính sách giá và chiết khấu |
| **Kế toán** | Ghi nhận doanh thu **phân bổ theo kỳ** (bán gói 12 tháng nhưng không ghi nhận hết vào tháng 1); quản lý chi phí vận hành; tính lương + hoa hồng chính xác; lập báo cáo P&L, dòng tiền, công nợ; xuất báo cáo có biểu đồ gửi chủ phòng |

**Kênh ưu tiên: web** — làm việc với bảng biểu, biểu đồ, xuất file.

---

## 2. Các vấn đề cần giải quyết

Gộp lại thành **3 vấn đề cốt lõi**. Mỗi vấn đề nối trực tiếp tới các nhu cầu ở mục 1.

### VĐ1 — Ghi nhận vận hành không đáng tin cậy và phân mảnh

**Mô tả.** Ba sự kiện quan trọng nhất của phòng gym — *ai đã vào tập*, *buổi tập nào đã diễn ra*, *PT nào đã dạy buổi nào* — đang được ghi nhận rời rạc: thẻ từ ghi vào phần mềm cửa, buổi PT ghi vào sổ/Excel, giáo án ghi ra giấy. Không có nguồn sự thật duy nhất.

**Hệ quả:**
- **Thất thoát doanh thu:** hội viên hết hạn vẫn vào tập; một thẻ dùng chung nhiều người; khách vãng lai vào không thu tiền. `[GĐ] ước tính 3–8% lượt vào là không hợp lệ ở phòng gym quản lý thủ công.`
- **Sai lương PT:** buổi tập khai khống hoặc quên ghi. Tranh chấp cuối tháng giữa PT và kế toán là chuyện thường xuyên.
- **Không có dữ liệu hành vi:** không biết hội viên nào đã 3 tuần không đến — tức là mất luôn khả năng can thiệp giữ chân.

**Nối tới nhu cầu:** N1, N2 (hội viên); nhu cầu tính công của PT; nhu cầu xác minh của Lễ tân; nhu cầu dữ liệu tin cậy của Admin/Kế toán.

---

### VĐ2 — Đứt gãy chuỗi *Bán hàng → Tiền → Sổ sách*, không quản trị được lợi nhuận

**Mô tả.** Chuỗi giá trị tài chính của phòng gym có 4 mắt xích, và ở phần lớn phòng gym vừa & nhỏ, chúng nằm ở 4 nơi khác nhau:

```
Chính sách giá & khuyến mãi  →  Hợp đồng bán ra  →  Dòng tiền thực thu  →  Sổ sách & lợi nhuận
      (Excel/Zalo)                (sổ/phần mềm)      (két + app ngân hàng)     (Excel kế toán)
```

**Hệ quả:**
- **Giá không kiểm soát:** sale tự ý giảm giá để chốt đơn; không ai biết biên lợi nhuận thực của từng hợp đồng.
- **Không phân biệt "thu tiền" và "doanh thu":** bán gói 12 tháng thu 9 triệu trong tháng 1 → sổ sách hiện 9 triệu doanh thu tháng 1, trong khi thực chất đó là **nghĩa vụ phải phục vụ 12 tháng**. Chủ phòng gym nhìn báo cáo tháng 1 thấy lãi lớn, tháng 6 thấy lỗ, không hiểu vì sao.
- **Lương & hoa hồng tính tay:** lương PT = lương cứng + hoa hồng bán gói + tiền công theo buổi (kể cả **buổi hỗ trợ miễn phí vẫn phải tính công**) + thưởng KPI. Công thức nhiều tầng, tính bằng Excel → sai sót và mất niềm tin.
- **Chi phí không được phân bổ:** tiền thuê mặt bằng, điện nước, vệ sinh, khấu hao thiết bị, marketing — không gắn được vào từng kỳ/từng dịch vụ → không biết gói nào có lãi.

**Nối tới nhu cầu:** toàn bộ nhu cầu của Sale, Kế toán, Admin; gián tiếp tới N6 (thanh toán) của hội viên.

---

### VĐ3 — Trải nghiệm hội viên rời rạc, dẫn tới tỷ lệ rời bỏ cao

**Mô tả.** Hội viên tương tác với phòng gym qua ít nhất 5 kênh không kết nối: thẻ tập, Zalo với PT, ảnh chụp bảng lịch lớp, giấy giáo án, quầy lễ tân. Không có nơi nào cho hội viên thấy **bức tranh tổng thể về hành trình tập luyện của mình**.

**Hệ quả:**
- Hội viên không thấy tiến bộ → mất động lực → ngừng đến → không gia hạn. `[GĐ] tỷ lệ không gia hạn sau gói đầu tiên ở phòng gym tầm trung khoảng 40–60%.`
- Phòng gym **không có tín hiệu cảnh báo sớm**: chỉ biết mất khách khi khách đã không gia hạn — lúc đó chi phí giành lại cao hơn nhiều so với chi phí giữ chân.
- Doanh thu phụ thuộc vào việc liên tục tìm khách mới (chi phí marketing cao) thay vì khai thác khách hiện có.

**Nối tới nhu cầu:** N1, N3, N4, N5, N7 (hội viên); nhu cầu "biết học viên nào sắp bỏ" của PT; nhu cầu upsell của Sale; nhu cầu churn của Admin.

---

## 3. Giải pháp cho từng vấn đề

### GP1 — "Sổ cái vận hành" (Operational Ledger): một nguồn sự thật, bất biến, xác minh được

Ba thành phần:

**(a) Check-in đa phương thức có xác minh danh tính**

| Lớp | Cơ chế | Vai trò |
|---|---|---|
| Lớp 1 (bắt buộc) | **QR động** trên mobile app — mã HMAC-TOTP đổi mỗi 30 giây, ký bằng secret riêng của từng hội viên, server verify + chống replay | Chống chụp màn hình gửi cho bạn |
| Lớp 1 (bắt buộc) | **Thẻ RFID/NFC** (Mifare 13.56 MHz, đầu đọc USB-HID) cho hội viên không dùng smartphone | Tương thích hạ tầng phòng gym hiện có |
| Lớp 2 (bắt buộc) | **Ảnh hồ sơ bật lên màn hình lễ tân** ngay khi quẹt + trạng thái gói (còn hạn / hết hạn / bảo lưu) | Human-in-the-loop: lễ tân đối chiếu bằng mắt — đây là cách các chuỗi gym VN đang làm và hiệu quả |
| Lớp 2 (bắt buộc) | **Anti-passback**: chặn check-in lần 2 trong X phút; cảnh báo 1 thẻ xuất hiện ở 2 cơ sở gần nhau về thời gian | Chống dùng chung thẻ |
| Lớp 3 (nâng cao, tùy chọn) | **Đối sánh khuôn mặt 1:1** — khi quẹt QR/thẻ, camera chụp ảnh, so embedding với ảnh hồ sơ đã đăng ký (ArcFace/InsightFace ONNX, microservice Python) | Chỉ so 1:1 (không phải 1:N) → nhanh, chính xác, không cần vector DB lớn |

> **Về mặt pháp lý (phải nêu trong báo cáo):** Nghị định 13/2023/NĐ-CP xếp **dữ liệu sinh trắc học là dữ liệu cá nhân nhạy cảm**. Lớp 3 vì vậy phải: (i) có **đồng ý rõ ràng** bằng văn bản/điện tử, (ii) **cho phép từ chối** và vẫn dùng được lớp 1–2, (iii) chỉ lưu **vector embedding đã mã hóa**, không lưu ảnh gốc, (iv) có quy trình xóa khi hội viên yêu cầu. Đây chính là lý do lớp 3 để tùy chọn — nó là điểm nhấn kỹ thuật nhưng không được là điều kiện bắt buộc để dùng hệ thống.

**(b) Sổ cái tín dụng buổi tập (Session Credit Ledger)** — *đóng góp học thuật chính*

Thay vì lưu `sessions_remaining` là một con số bị UPDATE (dễ sai, không truy vết được), dùng mô hình **sổ cái append-only kiểu kế toán kép**:

```
session_credit_ledger(id, registration_id, entry_type, delta, balance_after,
                      source_type, source_id, created_by, created_at, note)

entry_type: GRANT     (+12)  khi kích hoạt gói PT 12 buổi
            CONSUME   (-1)   khi PT & hội viên cùng xác nhận buổi tập
            REFUND    (+1)   khi hủy buổi đúng hạn
            EXPIRE    (-n)   khi gói hết hạn còn dư
            ADJUST    (±n)   điều chỉnh thủ công — bắt buộc có lý do + người duyệt
```

Bất biến hệ thống: `SUM(delta) WHERE registration_id = X` **luôn** bằng `balance_after` của bút toán mới nhất. Đây là **invariant kiểm thử được**, và là cơ sở để đối soát tự động giữa số buổi hội viên đã dùng và số buổi PT được tính công.

**(c) Xác nhận hai chiều cho buổi tập PT**

Buổi tập chỉ chuyển sang `COMPLETED` (và trừ credit + phát sinh công cho PT) khi **cả PT và hội viên cùng xác nhận** — hội viên xác nhận bằng cách quét QR của PT trên app, hoặc PT tạo yêu cầu và hội viên duyệt trong 24h (quá hạn thì auto-confirm nhưng gắn cờ để kiểm toán). Điều này đóng lỗ hổng khai khống buổi tập.

**Xử lý các yêu cầu riêng của đề bài:**
- *Nhiều PT chăm sóc 1 hội viên:* bảng `member_trainers(member_id, trainer_id, role, from_date, to_date)` với `role ∈ {primary, secondary, substitute}`. Công được tính cho **PT thực tế dạy buổi đó**, không phải PT được gán chính.
- *PT hỗ trợ tập miễn phí vẫn được tính công:* trường `session_type ∈ {paid_pt, complimentary, trial, orientation, class, makeup}` trên bảng buổi tập, mỗi loại có **đơn giá công riêng** trong bảng `payroll_rate_cards`. Buổi `complimentary` trừ 0 credit của hội viên nhưng vẫn phát sinh 1 dòng công cho PT.

---

### GP2 — Chuỗi tài chính khép kín, từ chính sách giá tới báo cáo P&L

**(a) Máy trạng thái hợp đồng (Registration Lifecycle)**

```
DRAFT ──duyệt──► PENDING_PAYMENT ──đủ tiền──► ACTIVE ──┬──► COMPLETED (hết hạn/hết buổi)
   │                    │                              ├──► FROZEN ──giải bảo lưu──► ACTIVE
   └──► CANCELLED  ◄─────┘                              ├──► TRANSFERRED (chuyển nhượng)
                                                        └──► REFUNDED (hoàn tiền có phí)
```

Quy tắc **bảo lưu** (trả lời trực tiếp câu hỏi của thầy) — mã hóa thành cấu hình, không hard-code:
- Chỉ gói thời hạn ≥ 3 tháng mới được bảo lưu
- Tối đa **30 ngày/năm hợp đồng**, tối đa 2 lần
- Phải đăng ký trước ≥ 3 ngày; bảo lưu vì lý do y tế (có giấy) được miễn giới hạn
- Khi bảo lưu, `end_date` được **đẩy lùi đúng số ngày bảo lưu** → bảng `registration_freezes` lưu từng lần

**(b) Snapshot giá — hợp đồng bất biến với thay đổi giá**

Vấn đề: nếu `registrations` chỉ trỏ `membership_id`, khi admin sửa giá gói thì các hợp đồng cũ bị thay đổi giá trị → sai toàn bộ báo cáo lịch sử.

Giải pháp: khi tạo hợp đồng, **sao chép (snapshot)** toàn bộ điều khoản thương mại vào chính hợp đồng: `list_price`, `discount_amount`, `discount_reason`, `final_price`, `duration_days`, `session_count`. Bảng `membership_prices` lưu giá theo phiên bản có hiệu lực (`valid_from`, `valid_to`), bảng `promotions` lưu chương trình khuyến mãi. Đây là chuẩn thực hành trong hệ thống bán hàng và là câu trả lời cho *"chính sách tăng giá, hạ giá"* trong góp ý của thầy.

**(c) Kiểm soát chiết khấu theo hạn mức**

`discount_policies(role, max_discount_percent, requires_approval_above)`: Sale được tự quyết giảm ≤ 10%; 10–20% cần quản lý duyệt; > 20% cần Admin. Mọi chiết khấu đều lưu lý do + người duyệt → truy vết được biên lợi nhuận.

**(d) Phân tách "Thu tiền" (Cash) và "Doanh thu ghi nhận" (Revenue)** — *đóng góp học thuật thứ hai*

Đây là điểm khiến module kế toán có chiều sâu thật, không chỉ là CRUD:

```
Bán gói 12 tháng, 9.000.000đ, thu tiền mặt ngày 15/01/2026
  ├─ Dòng tiền (Cash flow): +9.000.000đ vào ngày 15/01
  └─ Doanh thu (Revenue recognition): phân bổ 9.000.000 / 365 ngày
      → tháng 01 ghi nhận: 16 ngày × 24.657đ =   394.520đ
      → tháng 02–12 ghi nhận: đủ ngày mỗi tháng
      → phần chưa ghi nhận nằm ở "Doanh thu chưa thực hiện" (Deferred Revenue)
```

Với gói theo **số buổi** (gói PT 12 buổi), doanh thu ghi nhận **theo buổi tiêu dùng thực tế** (mỗi bút toán `CONSUME` trong sổ cái ở GP1 kích hoạt ghi nhận 1/12 giá trị) — đây chính là chỗ GP1 và GP2 khớp nối vào nhau.

Bảng `revenue_recognition_entries` được sinh tự động bởi job chạy hàng đêm, cho phép báo cáo:
- **Bảng cân đối thu — chi thực tế** (dòng tiền)
- **Báo cáo kết quả kinh doanh** (doanh thu ghi nhận − chi phí kỳ = lãi/lỗ thật)
- **Công nợ phải trả dịch vụ** (deferred revenue = số tiền đã thu nhưng còn nợ dịch vụ)

**(e) Engine tính lương & hoa hồng**

```
Lương PT tháng = Lương cứng
               + Σ(buổi dạy × đơn giá theo session_type theo rate card)
               + Σ(hoa hồng bán gói × tỷ lệ theo bậc)
               + Thưởng KPI (số buổi, điểm đánh giá TB, tỷ lệ gia hạn học viên)
               − Khấu trừ (nghỉ không phép, phạt hủy buổi muộn)
```

Engine chạy trên dữ liệu từ sổ cái GP1 → **không nhập tay dòng nào** → không sai. Kết quả sinh ra `payroll_runs` + `payroll_items` (bảng chi tiết từng dòng, PT xem được trên app → minh bạch).

**(f) Quản lý chi phí**

`expenses(category, amount, period_start, period_end, allocation_method, vendor, attachment)` với các nhóm chi phí thực tế: thuê mặt bằng, điện/nước, lương nhân sự, khấu hao thiết bị (đường thẳng theo `facilities.purchase_price` và `useful_life_months`), vệ sinh, marketing, bảo trì, khác. Chi phí có kỳ (`period`) được phân bổ theo tháng giống doanh thu → P&L mới có nghĩa.

**Số liệu giả định để mô phỏng (phải hợp lý — theo yêu cầu của thầy):** xem `docs/03-CO-SO-DU-LIEU.md` mục "Bộ số liệu mô phỏng".

**(g) Thanh toán** — trả lời câu hỏi *"thu tiền hội viên: tiền mặt, chuyển khoản, máy quẹt thẻ"*

| Phương thức | Cách triển khai | Mức độ |
|---|---|---|
| **Tiền mặt** | Lễ tân ghi nhận → phiếu thu có mã → **phiên làm việc (shift)**: mở ca, ghi số dư đầu ca, cuối ca đối soát tiền két vs tổng phiếu thu, chênh lệch phải ghi lý do | Bắt buộc — đây là nghiệp vụ thật, đáng làm |
| **Chuyển khoản / VietQR** | Sinh mã **VietQR động** (chuẩn EMVCo/NAPAS) với nội dung chuyển khoản chứa mã hóa đơn `GYM INV12345`. Đối soát tự động qua webhook biến động số dư (SePay/Casso — chi phí thấp, phù hợp đồ án) hoặc API ngân hàng doanh nghiệp | Bắt buộc — thực tế nhất ở VN |
| **Cổng thanh toán** | VNPay / MoMo / ZaloPay **sandbox** — luồng redirect + IPN webhook + verify chữ ký | Nên có — demo được luồng đầy đủ, miễn phí |
| **Máy POS quẹt thẻ** | Ghi nhận thủ công (lễ tân nhập 4 số cuối thẻ + mã giao dịch từ biên lai POS) rồi đối soát cuối ngày với sao kê | Bắt buộc — POS thật không có API cho bên thứ ba |
| **Trả góp 0%** | `payment_schedules(registration_id, due_date, amount, status)` + nhắc nợ tự động | Tùy chọn — thực tế nhiều gym VN có |

**Nguyên tắc kỹ thuật bắt buộc cho module thanh toán:**
- `Idempotency-Key` trên mọi API tạo giao dịch (chống double-charge khi retry)
- Xác thực chữ ký webhook, chống replay bằng nonce + timestamp
- **Outbox pattern**: ghi DB và gửi event trong cùng transaction để không mất/nhân đôi sự kiện
- Trạng thái thanh toán là **máy trạng thái tường minh**: `INITIATED → PENDING → SUCCEEDED | FAILED | EXPIRED`, và `SUCCEEDED → REFUNDED | PARTIALLY_REFUNDED`

---

### GP3 — Nền tảng gắn kết & giữ chân hội viên

**(a) Mobile app hội viên là "trung tâm hành trình tập luyện"** — gom N1–N7 vào một nơi: gói của tôi, QR check-in, lịch PT, giáo án hôm nay, chỉ số cơ thể, thanh toán, feedback.

**(b) Thư viện bài tập & giáo án** — trả lời *"phải có nguồn dữ liệu bài tập có sẵn"*

Nguồn dữ liệu khuyến nghị (đã kiểm chứng về giấy phép):
1. **Free Exercise DB** (`yuhonas/free-exercise-db`) — ~800 bài tập, JSON có sẵn + ảnh minh họa, **public domain / Unlicense** → dùng thoải mái, đây là lựa chọn tốt nhất
2. **wger** (`wger.de`) — API mở + database bài tập cộng đồng, giấy phép CC-BY-SA / AGPL → dùng được nhưng phải ghi nguồn

Quy trình: tải một lần → seed vào PostgreSQL → **dịch tên và mô tả sang tiếng Việt bằng LLM theo lô (Batch API, rẻ 50%)** → **có người rà soát** trước khi phát hành. Mô hình dữ liệu:

```
exercises (id, code, name_vi, name_en, muscle_group, secondary_muscles,
           equipment, difficulty, instructions, media_url, source, license)
workout_templates (id, name, goal, level, duration_weeks, created_by)
workout_template_items (template_id, day_index, exercise_id, sets, reps, rest_sec, note)
workout_plans (id, member_id, trainer_id, template_id, start_date, status)
workout_logs (id, plan_item_id, member_id, performed_at, sets_done, reps_done,
              weight_kg, rpe, note)
```

**(c) Chỉ số cơ thể (Body Measurement)** — trả lời câu hỏi của thầy

| Cách nhập | Mô tả |
|---|---|
| Nhập tay (bắt buộc) | Hội viên hoặc PT nhập: cân nặng, chiều cao, % mỡ, khối lượng cơ, các số đo vòng (ngực/eo/mông/tay/đùi) |
| **Upload phiếu InBody** (nâng cao, đáng làm) | Phòng gym VN phổ biến dùng máy **InBody 270/570**. Máy in ra phiếu giấy → hội viên **chụp ảnh phiếu** → dùng **LLM vision (Haiku 4.5) trích xuất số liệu** → hiển thị bản nháp cho người dùng **xác nhận/sửa** trước khi lưu. Đây là ứng dụng LLM có giá trị thực, chi phí rất thấp (xem mục 7 & tài liệu 04) |
| Đồng bộ Google Fit / Apple HealthKit | Tùy chọn, độ ưu tiên thấp |

Server tính: **BMI**, **BMR** (công thức Mifflin-St Jeor), **TDEE** (BMR × hệ số vận động), tỷ lệ eo/mông. Biểu đồ đường theo thời gian, so với mục tiêu đã đặt. **Không tự đưa lời khuyên y tế** — chỉ hiển thị số liệu và tham chiếu ngưỡng WHO/châu Á, kèm cảnh báo tham khảo ý kiến chuyên môn.

**(d) Điểm rủi ro rời bỏ (Churn Risk Score)**

Bắt đầu bằng **mô hình quy tắc có trọng số** (giải thích được, không cần dữ liệu lớn):

```
risk = w1·(số ngày kể từ lần check-in cuối / 30)
     + w2·(1 − tần suất tập 4 tuần gần nhất / tần suất 4 tuần trước đó)
     + w3·(số buổi PT còn lại thấp & sắp hết hạn)
     + w4·(có feedback tiêu cực chưa xử lý)
     + w5·(chưa từng đặt lịch PT hoặc lớp)
```

Ngưỡng `high` → tự động tạo **task chăm sóc** giao cho PT phụ trách hoặc Sale, kèm gợi ý hành động. Nếu có đủ dữ liệu mô phỏng, có thể nâng cấp lên logistic regression / gradient boosting và **so sánh AUC với mô hình quy tắc** — đây là một thí nghiệm định lượng tốt cho báo cáo.

**(e) Vòng phản hồi khép kín**

Feedback về PT / thiết bị / vệ sinh → tự động định tuyến: feedback thiết bị → tạo **work order bảo trì**; feedback PT → vào bảng điểm PT (ảnh hưởng KPI lương); feedback vệ sinh → giao Admin. Mỗi feedback có SLA và trạng thái → hội viên thấy được phản ánh của mình đã đi tới đâu (đây là điều tạo niềm tin, N7).

---

## 4. Liên kết các giải pháp trong một kiến trúc tổng thể

### 4.1 Ý tưởng kiến trúc: một dòng sự kiện, ba lớp tiêu thụ

Điểm mấu chốt: **GP1 sinh ra dữ liệu, GP2 và GP3 tiêu thụ dữ liệu đó.** Ba giải pháp không phải ba module song song — chúng là một chuỗi.

```
              ┌─────────────────────────────────────────────────────┐
              │        GP1 — SỔ CÁI VẬN HÀNH (nguồn sự thật)        │
              │  check_ins · pt_sessions · session_credit_ledger    │
              └──────────────┬──────────────────────┬───────────────┘
                             │                      │
        ┌────────────────────▼──────┐    ┌──────────▼─────────────────────┐
        │  GP2 — CHUỖI TÀI CHÍNH    │    │  GP3 — GẮN KẾT HỘI VIÊN        │
        │  • ghi nhận doanh thu     │    │  • hồ sơ tiến bộ                │
        │    theo buổi tiêu dùng    │    │  • điểm rủi ro rời bỏ           │
        │  • tính công PT theo buổi │    │  • gợi ý gia hạn đúng lúc      │
        │  • P&L, dòng tiền         │    │  • task chăm sóc cho PT/Sale   │
        └───────────┬───────────────┘    └──────────┬─────────────────────┘
                    │                               │
                    └───────────┬───────────────────┘
                                ▼
              ┌─────────────────────────────────────┐
              │   Dashboard theo role (BI layer)    │
              │ Admin · Accountant · Sale · PT      │
              └─────────────────────────────────────┘
```

**Ví dụ một sự kiện chảy xuyên hệ thống** — minh chứng ba giải pháp thực sự dính vào nhau:

> Hội viên A quét QR check-in lúc 18:05 → PT B xác nhận buổi tập, A duyệt trên app
>
> 1. `check_ins` ghi 1 lượt vào (GP1)
> 2. `pt_sessions` chuyển `COMPLETED`, `session_credit_ledger` ghi bút toán `CONSUME −1` (GP1)
> 3. Job ghi nhận doanh thu: 1/12 × 3.600.000 = **300.000đ chuyển từ Deferred Revenue sang Revenue** (GP2)
> 4. `payroll_items` sinh 1 dòng công cho PT B theo rate card `paid_pt` (GP2)
> 5. `churn_risk` của A được tính lại → giảm; ngày check-in cuối cập nhật (GP3)
> 6. Dashboard Admin, bảng lương PT B, hồ sơ tiến bộ của A — tất cả cập nhật từ **cùng một sự kiện**, không nhập liệu lần hai

### 4.2 Kiến trúc kỹ thuật: Modular Monolith + 2 vệ tinh

Chi tiết đầy đủ ở `docs/02-KIEN-TRUC-VA-CONG-NGHE.md`. Tóm tắt:

```
   Mobile App (React Native/Expo)          Web App (React + TypeScript)
   Member · Personal Trainer               Admin · Sale · Receptionist · Accountant
              │                                        │
              └──────────────┬─────────────────────────┘
                             │  HTTPS / REST (JSON) + WebSocket
                  ┌──────────▼───────────┐
                  │   API Gateway lớp    │  Nginx/Caddy: TLS, rate limit, CORS
                  └──────────┬───────────┘
                             │
     ┌───────────────────────▼────────────────────────────────┐
     │       BACKEND — Spring Boot 3 (Java 21)                │
     │       MODULAR MONOLITH — 1 tiến trình, N module        │
     │                                                        │
     │  M1 identity   M2 crm-sales   M3 membership            │
     │  M4 access     M5 training    M6 billing               │
     │  M7 finance    M8 facility    M9 engagement            │
     │  M10 analytics M11 ai-assist                           │
     │                                                        │
     │  Module giao tiếp qua interface + domain event         │
     │  (Spring ApplicationEvent + Outbox) — KHÔNG gọi        │
     │  thẳng repository của nhau                             │
     └───┬─────────────┬──────────────┬──────────────┬────────┘
         │             │              │              │
   ┌─────▼─────┐ ┌─────▼─────┐ ┌──────▼──────┐ ┌─────▼──────────┐
   │PostgreSQL │ │  Redis    │ │ MinIO / S3  │ │ Vệ tinh:       │
   │ 16        │ │ cache,    │ │ ảnh hồ sơ,  │ │ • ai-service   │
   │ dữ liệu   │ │ QR nonce, │ │ media bài   │ │   (FastAPI)    │
   │ giao dịch │ │ rate limit│ │ tập, chứng  │ │ • checkin-agent│
   │           │ │           │ │ từ          │ │   (đầu đọc thẻ)│
   └───────────┘ └───────────┘ └─────────────┘ └────────────────┘
```

**Tại sao Modular Monolith chứ không microservices?** Đúng yêu cầu *"không quá phức tạp, cũng không đơn giản"*:
- Microservices cho một đồ án 1 người là **over-engineering**: chi phí vận hành (service mesh, distributed tracing, eventual consistency, saga) lớn hơn giá trị nhận được, và các nghiệp vụ ở đây cần **transaction ACID** (trừ credit + ghi công + ghi nhận doanh thu phải nguyên tử).
- Nhưng vẫn phải có **ranh giới module rõ ràng** (package theo domain, giao tiếp qua interface + event) → thể hiện năng lực thiết kế, và nếu sau này cần tách microservice thì đường cắt đã sẵn.
- Hai vệ tinh tách riêng vì **lý do kỹ thuật chính đáng**: AI service cần Python (ONNX Runtime, thư viện ML); check-in agent phải chạy tại máy quầy lễ tân để nói chuyện với đầu đọc thẻ qua USB-HID.

### 4.3 Nguyên tắc kiến trúc xuyên suốt

| Nguyên tắc | Áp dụng ở đâu |
|---|---|
| **Append-only cho dữ liệu tiền & công** | `session_credit_ledger`, `payments`, `revenue_recognition_entries`, `payroll_items` — sửa thì ghi bút toán đảo, không UPDATE/DELETE |
| **Snapshot điều khoản thương mại** | `registrations` giữ bản sao giá & điều kiện tại thời điểm ký |
| **Idempotency ở mọi ranh giới ngoài** | Thanh toán, webhook, check-in, push notification |
| **RBAC + phân quyền cấp bản ghi** | Không chỉ "role nào gọi được API nào", mà "PT chỉ đọc được hồ sơ học viên của mình" |
| **Audit log toàn hệ thống** | Mọi thao tác ghi trên dữ liệu tiền, quyền, giá đều ghi `audit_logs(actor, action, entity, before, after, ip, at)` |
| **Cấu hình chứ không hard-code** | Quy tắc bảo lưu, hạn mức chiết khấu, rate card lương, trọng số churn — nằm trong bảng cấu hình, admin sửa được |

---

## 5. Kiểm thử để kết luận giải pháp **giải đúng vấn đề**

> Đây là kiểm thử **xác minh (verification)** — đo bằng số, so với đường cơ sở (baseline).

### 5.1 Thiết kế thí nghiệm: so sánh có đối chứng

Xây dựng **"bộ dữ liệu vàng" (golden dataset)** mô phỏng **6 tháng vận hành** một phòng gym 800–1.200 hội viên: hợp đồng, thanh toán, check-in, buổi PT, chi phí, bảo lưu, hoàn tiền, chuyển nhượng, tranh chấp. Trong đó **cố tình gài các tình huống khó**: hội viên hết hạn cố vào tập, 1 thẻ dùng 2 người, PT khai buổi không có thật, gói bảo lưu vắt qua 2 kỳ kế toán, hoàn tiền giữa kỳ.

Song song, **một người dùng Excel** xử lý cùng bộ dữ liệu đó theo quy trình thủ công hiện hành → đó là **baseline**.

### 5.2 Chỉ số & tiêu chí chấp nhận

| VĐ | Chỉ số | Cách đo | Baseline `[GĐ]` | Mục tiêu hệ thống |
|---|---|---|---|---|
| VĐ1 | **Tỷ lệ phát hiện check-in không hợp lệ** | Số ca gài / số ca hệ thống chặn hoặc cảnh báo | ~30% (chỉ khi lễ tân để ý) | **≥ 95%** |
| VĐ1 | **Thời gian check-in trung bình** | Đo trên 100 lượt mô phỏng | 12–20 giây | **≤ 5 giây (P95)** |
| VĐ1 | **Sai lệch công PT** | \|số buổi hệ thống tính − số buổi thực tế trong golden dataset\| / tổng buổi | 3–7% | **0%** (bất biến sổ cái phải giữ tuyệt đối) |
| VĐ2 | **Sai lệch bảng lương** | So từng dòng lương với bảng tính tay do người làm | 2–5% số dòng sai | **0 dòng sai** trên toàn bộ golden dataset |
| VĐ2 | **Thời gian chốt sổ cuối tháng** | Từ lúc bắt đầu tới lúc có báo cáo P&L hoàn chỉnh | 4–8 giờ | **≤ 10 phút** (tự động) |
| VĐ2 | **Đúng đắn ghi nhận doanh thu** | `Σ revenue_recognized + deferred_revenue == Σ contract_value` — kiểm cho **mọi** hợp đồng, mọi ngày trong 6 tháng | Không tồn tại khái niệm | **Bất biến luôn đúng, sai số 0đ** |
| VĐ2 | **Đối soát dòng tiền** | `Σ payments == Σ phiếu thu + Σ giao dịch ngân hàng khớp` | Lệch 1–3% | **Lệch = 0**, chênh lệch phải có bút toán giải trình |
| VĐ3 | **Chất lượng cảnh báo churn** | Precision@k, Recall, AUC trên golden dataset (nhãn = có gia hạn hay không) | Không có (0%) | **Precision@100 ≥ 0.6**, AUC ≥ 0.75 |

### 5.3 Các tầng kiểm thử

| Tầng | Công cụ | Trọng tâm |
|---|---|---|
| **Unit** | JUnit 5 + AssertJ, Vitest | Công thức: BMI/BMR/TDEE, phân bổ doanh thu theo ngày/theo buổi, tính lương, tính ngày bảo lưu, tính phí hoàn tiền |
| **Property-based** | jqwik | Các **bất biến**: sổ cái credit không bao giờ âm; `SUM(delta) == balance_after`; `revenue + deferred == contract_value` với **mọi** chuỗi thao tác sinh ngẫu nhiên. Đây là kiểu test mạnh nhất cho lõi nghiệp vụ và là điểm cộng học thuật |
| **Integration** | **Testcontainers** (PostgreSQL + Redis thật trong Docker) | Repository, transaction, migration Flyway, concurrency (2 lượt check-in cùng lúc, 2 lần trừ credit đồng thời) |
| **Contract / API** | REST Assured + springdoc-openapi | Mọi endpoint đúng schema; **ma trận phân quyền**: mỗi endpoint × mỗi role → kỳ vọng 200/403 (đây là test tự sinh, bắt được lỗ hổng phân quyền) |
| **E2E kịch bản nghiệp vụ** | Playwright (web), Maestro (mobile) | 12 kịch bản đầu-cuối, xem `docs/05-KIEM-THU.md` |
| **Hiệu năng** | k6 | Giờ cao điểm 18h–20h: 200 check-in/phút, 500 người dùng đồng thời. Mục tiêu P95 API < 300ms, check-in < 200ms |
| **Bảo mật** | OWASP ZAP, Dependency-Check, thủ công theo ASVS L1 | SQL injection, IDOR (đổi `member_id` trên URL có đọc được hồ sơ người khác không?), JWT refresh rotation, brute-force login, rò rỉ dữ liệu sinh trắc |
| **Đối chiếu (Oracle test)** | Script so sánh | Chạy toàn bộ 6 tháng golden dataset → xuất báo cáo → so **từng ô** với bảng Excel do người tính. Đây là bằng chứng thuyết phục nhất trong buổi bảo vệ |

**Cổng chất lượng (Definition of Done):** coverage ≥ 80% ở module `finance`, `billing`, `training` (lõi nghiệp vụ) — không đặt mục tiêu coverage cho code CRUD thuần; **0 lỗ hổng High/Critical**; toàn bộ bất biến property-based đều xanh.

---

## 6. Kiểm thử để kết luận ứng dụng **đáp ứng đúng nhu cầu người dùng**

> Đây là kiểm thử **thẩm định (validation)** — đo bằng hành vi và cảm nhận của người dùng thật.

### 6.1 Kiểm thử khả dụng có kịch bản (Task-based Usability Test)

**Đối tượng:** 5–8 người/role (theo Nielsen, 5 người phát hiện ~85% vấn đề khả dụng). Ưu tiên mời người **đúng vai** — hội viên phòng gym thật, PT thật; nếu không được thì sinh viên đóng vai theo kịch bản chi tiết, và **phải ghi rõ hạn chế này trong báo cáo**.

**Mỗi người thực hiện 5–7 tác vụ**, ví dụ với hội viên:
1. "Bạn muốn biết gói tập của mình còn bao nhiêu ngày" → tìm thông tin
2. "Bạn vừa tới phòng gym, hãy check-in"
3. "Bạn muốn tập với PT Nguyễn Văn A vào 19h thứ Năm" → đặt lịch
4. "Bạn vừa cân xong, hãy ghi lại 72,5 kg và xem biểu đồ 3 tháng"
5. "Máy chạy bộ số 3 bị hỏng, hãy báo cho phòng gym"
6. "Gói bạn sắp hết hạn, hãy gia hạn bằng chuyển khoản"

**Chỉ số đo:**

| Chỉ số | Cách đo | Mục tiêu |
|---|---|---|
| **Task Success Rate** | % người hoàn thành không cần trợ giúp | ≥ 90% cho tác vụ cốt lõi |
| **Time on Task** | Đo giây | Check-in ≤ 10s; đặt lịch PT ≤ 60s; gia hạn ≤ 90s |
| **Error Rate** | Số thao tác sai / tác vụ | ≤ 1 |
| **SEQ** (Single Ease Question) | Sau mỗi tác vụ: "Tác vụ này dễ hay khó?" thang 1–7 | TB ≥ 5.5 |
| **SUS** (System Usability Scale) | 10 câu chuẩn hóa, cuối phiên | **≥ 68** (mức trung bình ngành), phấn đấu **≥ 75** |

Ghi màn hình + think-aloud protocol → tổng hợp danh sách vấn đề khả dụng theo mức nghiêm trọng → sửa → **test vòng 2 với nhóm khác** để chứng minh cải thiện (báo cáo có SUS trước/sau là bằng chứng rất mạnh).

### 6.2 Kiểm thử chấp nhận (UAT) theo user story

Mỗi user story có tiêu chí chấp nhận dạng **Given / When / Then**, ví dụ:

> **Given** hội viên có gói PT 12 buổi, đã dùng 11 buổi
> **When** PT tạo yêu cầu xác nhận buổi thứ 12 và hội viên duyệt
> **Then** số dư credit về 0, gói chuyển trạng thái `COMPLETED`, PT nhận 1 dòng công, hệ thống gửi thông báo gợi ý gia hạn cho hội viên **và** tạo task upsell cho Sale

Người đóng vai Product Owner (giảng viên hướng dẫn, hoặc quản lý một phòng gym nếu tiếp cận được) **ký nhận** từng story → có biên bản UAT đính kèm phụ lục báo cáo.

### 6.3 Chạy thử thực địa (Pilot) — nếu tiếp cận được phòng gym

Phương án ưu tiên: liên hệ **1 phòng gym nhỏ (< 300 hội viên)** chạy song song 2–4 tuần (chạy song song, không thay thế hệ thống hiện tại — để không gây rủi ro cho họ).

Phương án dự phòng (chắc chắn làm được): **mô phỏng vận hành 1 tuần** với 10–15 người đóng đủ 6 vai, dùng kịch bản có sẵn tình huống bất thường (khách quên thẻ, tranh chấp buổi tập, hủy gói đòi hoàn tiền, máy hỏng giữa giờ cao điểm).

### 6.4 Đo lường trong ứng dụng (Product Analytics)

Nhúng sự kiện phân tích (self-host, ví dụ PostHog hoặc bảng `analytics_events` tự làm) để đo hành vi thật thay vì chỉ hỏi cảm nhận:

| Phễu / chỉ số | Ý nghĩa |
|---|---|
| Phễu đăng ký gói: xem gói → chọn → thanh toán → thành công | Tìm bước rơi rụng |
| Tỷ lệ hoàn tất đặt lịch PT | Nếu thấp → luồng đặt lịch có vấn đề |
| Retention D1 / D7 / D30 của app | Ứng dụng có được dùng lại không |
| Tỷ lệ check-in bằng app / tổng check-in | Đo mức chấp nhận công nghệ mới |
| Số hội viên ghi chỉ số cơ thể ≥ 2 lần | Đo tính năng có tạo thói quen không |

### 6.5 Khảo sát & phỏng vấn

- **Trước khi dùng:** khảo sát điểm đau (Likert 1–5) cho từng nhu cầu N1–N8 → có đường cơ sở định lượng
- **Sau khi dùng:** khảo sát cùng bộ câu hỏi + **NPS** ("bạn có giới thiệu ứng dụng này cho phòng gym khác không?")
- **Phỏng vấn bán cấu trúc** 3–5 người sau pilot: cái gì hữu ích nhất, cái gì thừa, cái gì thiếu

**Kết luận chỉ được rút ra khi cả 3 nguồn đồng thuận**: chỉ số khả dụng đạt ngưỡng + phân tích hành vi cho thấy tính năng được dùng thật + người dùng tự nói ra giá trị trong phỏng vấn. Một mình SUS cao không đủ để kết luận.

---

## 7. Đóng góp chính của ĐATN và Outcome

| # | Đóng góp | Loại | Giải quyết VĐ | Đáp ứng nhu cầu | Outcome đo được |
|---|---|---|---|---|---|
| **ĐG1** | **Mô hình "Sổ cái tín dụng buổi tập" (Session Credit Ledger)** — áp dụng nguyên lý sổ cái append-only của kế toán vào quản lý buổi tập, kèm cơ chế xác nhận hai chiều PT–hội viên và bộ bất biến kiểm chứng được bằng property-based testing | Mô hình dữ liệu + thuật toán | VĐ1 | Tính công PT; xác minh của Lễ tân; N1 | Sai lệch công PT **7% → 0%**; tranh chấp lương có bằng chứng truy vết 100%; số dư buổi tập hội viên xem được real-time |
| **ĐG2** | **Kiến trúc check-in đa lớp có xác minh danh tính**, cân bằng giữa độ tin cậy, tốc độ và **tuân thủ Nghị định 13/2023/NĐ-CP** (QR động HMAC-TOTP + RFID + đối chiếu ảnh + anti-passback, sinh trắc học là lớp tùy chọn có consent) | Thiết kế hệ thống + bảo mật | VĐ1 | N2; xác minh của Lễ tân | Phát hiện **≥ 95%** lượt vào không hợp lệ; thời gian check-in **20s → ≤ 5s**; không lưu ảnh sinh trắc thô |
| **ĐG3** | **Engine tài chính phòng gym**: tách bạch dòng tiền và doanh thu ghi nhận (deferred revenue), phân bổ theo thời gian **và** theo buổi tiêu dùng, phân bổ chi phí, tự động hóa lương + hoa hồng nhiều tầng | Nghiệp vụ + kỹ thuật | VĐ2 | Toàn bộ nhu cầu Kế toán & Chủ phòng gym | Thời gian chốt sổ **4–8h → ≤ 10 phút**; bảng lương **0 dòng sai**; lần đầu chủ phòng gym thấy **lãi/lỗ thật theo tháng** thay vì "thu được bao nhiêu tiền" |
| **ĐG4** | **Vòng lặp giữ chân hội viên dựa trên dữ liệu**: điểm rủi ro rời bỏ giải thích được → tự động sinh task chăm sóc → đo hiệu quả can thiệp, khép kín từ tín hiệu tới hành động | Phân tích dữ liệu + quy trình | VĐ3 | N1, N4, N7; PT & Sale | Precision@100 ≥ 0.6, AUC ≥ 0.75 trên golden dataset; chuyển từ **phản ứng bị động** sang **can thiệp chủ động** — nếu có pilot, đo tỷ lệ gia hạn trước/sau |
| **ĐG5** | **Tầng AI có kiểm soát chi phí (Cost-aware AI layer)**: định tuyến mô hình theo độ khó tác vụ, prompt caching, Batch API, hạn mức ngân sách theo người dùng/ngày, luôn có người xác nhận trước khi ghi dữ liệu | Kỹ thuật ứng dụng LLM | Hỗ trợ VĐ3 | N4, N5; soạn giáo án của PT | Chi phí LLM **≈ 22 USD/tháng** cho 1.000 hội viên `[ước tính, xem 7.1]` ≈ **575đ/hội viên/tháng** — chứng minh AI khả thi về kinh tế trong sản phẩm thật, không chỉ là demo |
| **ĐG6** | **Kiến trúc đa nền tảng thực dụng**: modular monolith + mobile (React Native) cho Member/PT + web cho nhóm back-office, chia sẻ kiểu dữ liệu TypeScript giữa web và mobile | Kiến trúc phần mềm | Cả 3 | Kênh ưu tiên của từng nhóm role | Một backend phục vụ 6 role trên 2 nền tảng; ranh giới module rõ ràng, có đường cắt sẵn nếu cần tách microservice |

### 7.1 Chi phí LLM API khi làm sản phẩm thực tế

> Theo đúng yêu cầu của thầy. **Đơn giá cập nhật tại thời điểm lập kế hoạch (07/2026)** — cần kiểm tra lại tại `platform.claude.com/docs/en/pricing` trước khi đưa vào báo cáo cuối.

**Đơn giá (USD / 1 triệu token):**

| Model | Input | Output | Dùng cho |
|---|---:|---:|---|
| Claude Haiku 4.5 | 1,00 | 5,00 | Tác vụ khối lượng lớn, đơn giản |
| Claude Sonnet 5 | 3,00 | 15,00 | Tác vụ cần suy luận |
| Claude Opus 5 | 5,00 | 25,00 | Không dùng thường xuyên trong sản phẩm này |

**Ba cơ chế giảm giá được áp dụng:**
- **Prompt caching**: đọc từ cache ≈ **0,1×** giá input; ghi cache 1,25× (TTL 5 phút). Rất hiệu quả vì chatbot và OCR dùng chung một system prompt dài.
- **Batch API**: **−50%** toàn bộ, cho tác vụ không cần trả lời ngay (tổng hợp feedback đêm, dịch thư viện bài tập).
- **Model routing**: mặc định Haiku 4.5; chỉ nâng lên Sonnet 5 khi tác vụ thực sự cần suy luận.

**Ước tính cho quy mô 1.000 hội viên hoạt động / 1 cơ sở / tháng:**

| # | Use case | Model | Lượt/tháng | Input/lượt | Output/lượt | Chi phí |
|---|---|---|---:|---:|---:|---:|
| A | Chatbot hỏi đáp hội viên (RAG trên dữ liệu gói tập, lịch, quy định) | Haiku 4.5 + cache | 3.000 | 2.500 (2.000 cached) | 300 | **6,75 $** |
| B | Sinh **bản nháp** giáo án cho PT (PT bắt buộc chỉnh sửa & duyệt) | Sonnet 5 | 300 | 4.000 | 1.500 | **10,35 $** |
| C | OCR phiếu InBody bằng vision → điền form cho người dùng xác nhận | Haiku 4.5 | 800 | 1.800 | 400 | **3,04 $** |
| D | Tổng hợp feedback → insight cho Admin (chạy đêm) | Sonnet 5 + **Batch** | 30 | 20.000 | 1.200 | **1,17 $** |
| E | Sinh phần diễn giải bằng lời cho báo cáo tài chính | Sonnet 5 | 10 | 8.000 | 2.000 | **0,54 $** |
| | **Tổng cơ sở** | | | | | **≈ 21,85 $/tháng** |
| | **Nhân hệ số an toàn 2,5×** (retry, prompt phình, môi trường dev/test, tăng trưởng) | | | | | **≈ 55 $/tháng** |

**Quy đổi:** ≈ **1,4 triệu VND/tháng** (tỷ giá giả định 26.000 VND/USD) cho 1.000 hội viên → **≈ 1.400đ/hội viên/tháng**.

**Đặt trong bối cảnh kinh doanh:** gói fitness 1 tháng giá 900.000đ → chi phí LLM chiếm **~0,15% doanh thu/hội viên**. Kết luận: **hoàn toàn khả thi về kinh tế**, và đây là một kết luận có giá trị thực tiễn để đưa vào báo cáo.

**Cơ chế kiểm soát chi phí bắt buộc phải cài đặt (nếu không, con số trên vô nghĩa):**
1. **Hạn mức theo người dùng**: mỗi hội viên tối đa N lượt hỏi chatbot/ngày; vượt thì trả lời bằng FAQ tĩnh
2. **Hạn mức tổ chức**: ngân sách trần/tháng; chạm 80% → cảnh báo admin, chạm 100% → tự động tắt tính năng không thiết yếu
3. **Ghi log token từng request** vào bảng `llm_usage_logs(feature, model, input_tokens, output_tokens, cached_tokens, cost_usd, user_id, at)` → dashboard chi phí AI theo tính năng
4. **Cache tầng ứng dụng**: câu hỏi lặp lại (top 50 câu FAQ) trả lời từ cache Redis, không gọi API
5. **Không bao giờ để LLM ghi thẳng vào DB**: mọi output (giáo án, số liệu InBody) đều là **bản nháp**, phải có người xác nhận — vừa an toàn nghiệp vụ, vừa là yêu cầu đạo đức khi làm sản phẩm liên quan sức khỏe

**Chi phí hạ tầng khác (để hoàn chỉnh bức tranh):** 1 VPS 4 vCPU/8GB (~15–25 $/tháng) + object storage (~5 $) + domain & TLS (~1 $) ≈ **25–35 $/tháng**. Tổng chi phí công nghệ ≈ **80–90 $/tháng ≈ 2,2 triệu VND** cho 1.000 hội viên.

---

## Bản đồ tài liệu

| File | Nội dung |
|---|---|
| `00-TONG-QUAN-VA-7-CAU-HOI.md` | ← Bạn đang ở đây |
| `01-NGHIEP-VU-VA-PHAN-QUYEN.md` | 6 role: tính năng chi tiết, luồng nghiệp vụ, ma trận phân quyền, phân loại độ khó |
| `02-KIEN-TRUC-VA-CONG-NGHE.md` | Tech stack, cấu trúc code base, cấu trúc thư mục, DevOps |
| `03-CO-SO-DU-LIEU.md` | Phân tích hạn chế của schema hiện tại + thiết kế mới + bộ số liệu mô phỏng |
| `04-GIAI-PHAP-KY-THUAT-KHO.md` | Chi tiết kỹ thuật: QR động, nhận diện, thanh toán, bài tập, InBody OCR, LLM |
| `05-KIEM-THU.md` | Chiến lược kiểm thử đầy đủ + 12 kịch bản E2E |
| `06-LO-TRINH.md` | Lộ trình theo sprint, MoSCoW, quản trị rủi ro |
| `../db/schema.sql` | Schema PostgreSQL hoàn chỉnh |
