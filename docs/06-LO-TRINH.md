# Phần 6: Lộ trình, Phạm vi & Quản trị rủi ro

## 1. Phân loại phạm vi theo MoSCoW

> Nguyên tắc: **Must have phải xong 100% trước khi bắt đầu Should have.** Đây là quy tắc chống rủi ro tiến độ quan trọng nhất cho đồ án một người.

### MUST HAVE — không có thì đồ án không hoàn chỉnh (~60% công sức)

| Nhóm | Hạng mục |
|---|---|
| Nền tảng | Đăng nhập/JWT/refresh rotation · RBAC 6 role · phân quyền cấp bản ghi · audit log |
| Hội viên | CRUD hội viên · hồ sơ + ảnh · CRUD gói tập + phiên bản giá |
| Hợp đồng | Máy trạng thái 8 trạng thái · snapshot giá · **bảo lưu có quy tắc** |
| Check-in | QR động HMAC-TOTP · thẻ RFID · màn hình quầy realtime có ảnh · anti-passback |
| Buổi PT | Slot trống · đặt lịch · duyệt · **xác nhận 2 chiều** · **sổ cái credit** · buổi miễn phí tính công |
| Thanh toán | Hóa đơn · tiền mặt + ca làm việc đối soát · VietQR + webhook · idempotency |
| Tài chính | **Ghi nhận doanh thu phân bổ** · chi phí · **engine lương + hoa hồng** · P&L · dòng tiền · xuất PDF/Excel |
| Sale | Lead · pipeline · báo giá có hạn mức chiết khấu · danh sách sắp hết hạn |
| Bài tập | Thư viện bài tập · giáo án · nhật ký tập · chỉ số cơ thể + BMI + biểu đồ |
| Phản hồi | Feedback + định tuyến · đánh giá PT · work order bảo trì |
| Dashboard | 4 dashboard: Admin, Kế toán, Sale, PT |
| Nền tảng client | Web (4 nhóm màn hình) + Mobile (Member + PT) |

### SHOULD HAVE — nên có, làm sau khi Must xong (~25%)

Push notification · lớp học nhóm + đặt chỗ + waitlist · churn score + task chăm sóc · dự báo bán hàng · chuyển nhượng gói · trả góp · khấu hao thiết bị tự động · khóa sổ kỳ kế toán · quản lý tủ đồ

### COULD HAVE — điểm cộng nếu còn thời gian (~15%)

Đối sánh khuôn mặt 1:1 · OCR phiếu InBody · chatbot RAG · sinh giáo án nháp bằng AI · diễn giải báo cáo bằng LLM · đồng bộ Google Fit · A/B test can thiệp giữ chân

### WON'T HAVE — nêu rõ để giới hạn phạm vi (quan trọng khi bảo vệ)

**Quản lý nhiều chi nhánh** (hệ thống chỉ phục vụ 1 phòng gym) · bán hàng hóa/đồ uống (POS bán lẻ) · quản lý kho · module HR đầy đủ (BHXH, thuế TNCN) · livestream lớp học online · tích hợp thiết bị cardio (FTMS/ANT+) · app riêng cho Sale/Lễ tân/Kế toán trên mobile · đa ngôn ngữ · nhiều đơn vị tiền tệ

---

## 2. Lộ trình theo Sprint (16 tuần / 8 sprint × 2 tuần)

> Giả định ~25–30 giờ/tuần. Điều chỉnh theo lịch học thực tế.

### Sprint 0 (Tuần 1–2) — Nền móng
- [ ] Khảo sát thực tế **2–3 phòng gym**: phỏng vấn quản lý + lễ tân + PT → **xác minh các số liệu `[GĐ]`** trong `docs/03`
- [ ] Chốt phạm vi với giảng viên hướng dẫn (dùng bảng MoSCoW ở trên)
- [ ] Dựng monorepo, Docker Compose, CI cơ bản
- [ ] Flyway V1–V4 (identity, membership, access, training core)
- [ ] Auth: login/refresh rotation/RBAC + `AccessGuard`
- [ ] Bộ khung web (layout + routing theo role) và mobile (navigation theo role)

**Kết quả bàn giao:** đăng nhập được bằng cả 6 role, môi trường dev chạy 1 lệnh, CI xanh.

### Sprint 1 (Tuần 3–4) — Hội viên & Hợp đồng
- [ ] CRUD person/user/member/trainer/staff, upload ảnh hồ sơ
- [ ] CRUD gói tập + phiên bản giá + khuyến mãi
- [ ] Máy trạng thái `registrations` + snapshot giá
- [ ] Bảo lưu (`registration_freezes`) + engine quy tắc cấu hình được
- [ ] Web: màn hình quản lý hội viên, gói tập, hợp đồng

**Kết quả bàn giao:** tạo được hợp đồng, kích hoạt, bảo lưu, hết hạn tự động. Unit test cho `FreezePolicy` đầy đủ.

### Sprint 2 (Tuần 5–6) — Check-in & Sổ cái buổi tập ⭐
- [ ] QR động HMAC-TOTP: sinh phía mobile (offline) + verify phía server + chống replay
- [ ] Đọc thẻ RFID qua ô input tự focus trên màn hình quầy
- [ ] **Màn hình Front Desk realtime** (WebSocket) hiện ảnh + trạng thái
- [ ] Anti-passback + `check_in_incidents`
- [ ] **`session_credit_ledger`** + trigger append-only + xử lý đồng thời (`SELECT FOR UPDATE`)
- [ ] **Property-based test cho bất biến sổ cái** ⭐

**Kết quả bàn giao:** demo được luồng check-in đầy đủ, sổ cái credit đúng tuyệt đối. **Đây là sprint quan trọng nhất — đừng rút ngắn.**

### Sprint 3 (Tuần 7–8) — Buổi PT & Lịch
- [ ] `trainer_availability`, `pt_bookings`, `pt_sessions`
- [ ] EXCLUDE constraint chống trùng lịch (PT và phòng)
- [ ] **Xác nhận hai chiều** PT ↔ hội viên
- [ ] `session_type` + buổi hỗ trợ miễn phí vẫn tính công
- [ ] `member_trainers` (nhiều PT / 1 hội viên)
- [ ] Mobile: lịch PT, đặt lịch, duyệt, xác nhận buổi tập

**Kết quả bàn giao:** E5 và E6 (kịch bản E2E) chạy được đầu-cuối.

### Sprint 4 (Tuần 9–10) — Thanh toán
- [ ] Hóa đơn + `payment_allocations`
- [ ] Tiền mặt + **ca làm việc + đối soát cuối ca**
- [ ] VietQR: sinh chuỗi TLV EMVCo + webhook đối soát + xử lý chưa khớp
- [ ] Cổng thanh toán sandbox (chọn 1: VNPay hoặc MoMo) + verify chữ ký IPN
- [ ] POS: nhập tay + import sao kê CSV
- [ ] Idempotency + outbox pattern
- [ ] Hoàn tiền

**Kết quả bàn giao:** thu được tiền bằng 4 phương thức, đối soát tự động, E1/E8 chạy được.

### Sprint 5 (Tuần 11–12) — Tài chính ⭐
- [ ] `revenue_schedules` + job ghi nhận doanh thu hàng đêm (2 phương pháp)
- [ ] Xử lý bảo lưu / hủy / hoàn tiền trong ghi nhận doanh thu (phần khó nhất)
- [ ] `expenses` + phân bổ theo kỳ + khấu hao
- [ ] **Engine lương**: rate card + hoa hồng bậc thang + KPI
- [ ] Báo cáo P&L, dòng tiền, deferred revenue, doanh thu theo gói/sale
- [ ] Xuất PDF/Excel có biểu đồ
- [ ] **Property-based test cho bất biến doanh thu** ⭐

**Kết quả bàn giao:** E9, E10 chạy được. Đây là sprint thể hiện đóng góp ĐG3 — dành đủ thời gian.

### Sprint 6 (Tuần 13–14) — CRM, Bài tập, Gắn kết
- [ ] Lead + pipeline + báo giá + hạn mức chiết khấu + duyệt
- [ ] Danh sách sắp hết hạn + hoa hồng sale
- [ ] Import thư viện bài tập (Free Exercise DB) + dịch bằng Batch API + **rà soát thủ công**
- [ ] Giáo án: template → plan → log
- [ ] Chỉ số cơ thể + BMI/BMR/TDEE + biểu đồ
- [ ] Feedback + định tuyến + work order bảo trì
- [ ] Push notification (FCM)
- [ ] Churn score (rule-based) + retention task

**Kết quả bàn giao:** E11, E12 chạy được; app hội viên hoàn chỉnh về mặt trải nghiệm.

### Sprint 7 (Tuần 15–16) — Kiểm thử, Tinh chỉnh, Viết báo cáo
- [ ] Sinh **golden dataset 6 tháng** + Oracle test
- [ ] Hoàn thiện ma trận phân quyền tự sinh
- [ ] Load test k6 + tối ưu truy vấn chậm (thêm index / materialized view)
- [ ] Quét bảo mật (ZAP, Dependency-Check, gitleaks) + vá lỗ hổng
- [ ] **Kiểm thử khả dụng vòng 1** → sửa → **vòng 2**
- [ ] Deploy staging, demo được từ internet
- [ ] Hoàn thiện báo cáo, slide, video demo

**Đệm:** nếu chậm tiến độ, cắt từ COULD HAVE trước, rồi SHOULD HAVE. **Không bao giờ cắt Sprint 7** — kiểm thử là phần trả lời câu hỏi 5 và 6 của đề bài.

---

## 3. Sơ đồ phụ thuộc giữa các sprint

```
S0 Nền móng
 └─► S1 Hội viên & Hợp đồng
      ├─► S2 Check-in & SỔ CÁI CREDIT ⭐
      │    └─► S3 Buổi PT (cần sổ cái để trừ credit)
      │         └─► S5 Tài chính (cần buổi PT để ghi nhận DT theo buổi
      │                           và để tính công PT)
      └─► S4 Thanh toán (cần hợp đồng để tạo hóa đơn)
           └─► S5 Tài chính (cần thanh toán để đối soát dòng tiền)
                └─► S6 CRM & Gắn kết
                     └─► S7 Kiểm thử & Hoàn thiện
```

**Đường găng (critical path):** `S0 → S1 → S2 → S3 → S5 → S7`. Chậm ở bất kỳ khâu nào trên đường này đều đẩy lùi toàn bộ. S4 và S6 có độ trễ cho phép.

---

## 4. Quản trị rủi ro

| # | Rủi ro | Khả năng | Tác động | Biện pháp phòng ngừa | Phương án dự phòng |
|---|---|:---:|:---:|---|---|
| R1 | **Phạm vi quá rộng, không kịp** | Cao | Cao | MoSCoW nghiêm ngặt; chốt phạm vi với GVHD ngay Sprint 0; review tiến độ cuối mỗi sprint | Cắt toàn bộ COULD; nếu vẫn chậm, cắt lớp học nhóm và trả góp khỏi SHOULD |
| R2 | **Module tài chính khó hơn dự kiến** | Cao | Cao | Dành trọn Sprint 5; viết test trước khi viết code (TDD) cho phần ghi nhận doanh thu | Đơn giản hóa: chỉ làm `STRAIGHT_LINE`, bỏ `HYBRID`; nêu rõ giới hạn trong báo cáo |
| R3 | **Không tiếp cận được phòng gym để khảo sát** | Trung bình | Trung bình | Liên hệ sớm từ Sprint 0; chuẩn bị 3–5 lựa chọn | Dùng số liệu `[GĐ]`, **ghi rõ là giả định** và nêu phương pháp xác minh nếu có cơ hội |
| R4 | **Không có người dùng thật để test khả dụng** | Trung bình | Cao (ảnh hưởng câu hỏi 6) | Tuyển từ Sprint 5, không đợi Sprint 7 | Sinh viên đóng vai theo kịch bản chi tiết; **ghi rõ hạn chế** trong báo cáo |
| R5 | **Chưa có kinh nghiệm React Native** | Trung bình | Trung bình | Học sớm trong Sprint 0–1; dùng Expo (đơn giản nhất) | Giảm phạm vi mobile: chỉ làm app Hội viên; PT dùng web responsive |
| R6 | **Tích hợp thanh toán bị chặn (sandbox không đăng ký được)** | Thấp | Trung bình | Đăng ký sandbox VNPay/MoMo từ Sprint 0 | Tự viết **mock gateway** đầy đủ (redirect + IPN + chữ ký) — vẫn chứng minh được thiết kế đúng |
| R7 | **Face verification không đạt độ chính xác** | Trung bình | Thấp | Đây vốn là COULD HAVE, lớp tùy chọn | Bỏ hoàn toàn; lớp 1+2 đã đủ; phần phân tích tuân thủ NĐ 13/2023 vẫn giữ nguyên giá trị |
| R8 | **Chi phí LLM vượt kiểm soát khi test** | Thấp | Thấp | Đặt hạn mức ngân sách ngay từ đầu; dùng Haiku + Batch cho mọi thứ có thể | Tắt tính năng AI; chỉ demo 1 use case |
| R9 | **Mất dữ liệu/code** | Thấp | Rất cao | Git push mỗi ngày; backup DB tự động; **không để code chỉ trên 1 máy** | – |
| R10 | **Hiệu năng báo cáo tài chính quá chậm** | Trung bình | Trung bình | Đo sớm với golden dataset ở Sprint 5, không đợi Sprint 7 | Materialized view refresh hàng đêm + bảng tổng hợp `daily_revenue_summary` |
| R11 | **Sổ cái credit bị race condition** | Trung bình | Cao | `SELECT FOR UPDATE` + integration test đồng thời ngay từ Sprint 2 | – (bắt buộc phải giải quyết, không có dự phòng) |

---

## 5. Cấu trúc báo cáo ĐATN đề xuất

```
Chương 1 — Mở đầu
  1.1 Bối cảnh & động lực (thực trạng vận hành phòng gym tại VN)
  1.2 Nhu cầu người dùng                              ← câu hỏi 1
  1.3 Phát biểu 3 vấn đề cần giải quyết               ← câu hỏi 2
  1.4 Mục tiêu, phạm vi, giới hạn (MoSCoW + WON'T HAVE)
  1.5 Đóng góp của đồ án                              ← câu hỏi 7
  1.6 Bố cục báo cáo

Chương 2 — Khảo sát & Phân tích
  2.1 Khảo sát thực tế (phỏng vấn phòng gym, kết quả, số liệu xác minh)
  2.2 Khảo sát giải pháp hiện có trên thị trường (so sánh sòng phẳng)
  2.3 Phân tích yêu cầu: 6 persona, use case, user story
  2.4 Yêu cầu phi chức năng (hiệu năng, bảo mật, tuân thủ NĐ 13/2023)

Chương 3 — Giải pháp đề xuất                          ← câu hỏi 3
  3.1 GP1 — Sổ cái vận hành (check-in đa lớp + session credit ledger)
  3.2 GP2 — Chuỗi tài chính khép kín (deferred revenue + payroll engine)
  3.3 GP3 — Nền tảng gắn kết & giữ chân
  3.4 Ánh xạ Vấn đề ↔ Giải pháp ↔ Nhu cầu

Chương 4 — Thiết kế hệ thống                          ← câu hỏi 4
  4.1 Kiến trúc tổng thể (một dòng sự kiện, ba lớp tiêu thụ)
  4.2 Kiến trúc kỹ thuật (modular monolith + vệ tinh) & lý giải lựa chọn
  4.3 Thiết kế CSDL (ERD 4 ngữ cảnh, các quyết định then chốt)
  4.4 Thiết kế API & luồng nghiệp vụ (7 BF)
  4.5 Thiết kế bảo mật & phân quyền
  4.6 Thiết kế giao diện (web + mobile)

Chương 5 — Triển khai
  5.1 Công nghệ sử dụng & lý do
  5.2 Cấu trúc mã nguồn & thực thi ranh giới module (ArchUnit)
  5.3 Cài đặt các thành phần khó:
      a) QR động HMAC-TOTP & chống replay
      b) Sổ cái tín dụng buổi tập & xử lý đồng thời
      c) Engine ghi nhận doanh thu phân bổ
      d) Engine lương & hoa hồng
      e) Tích hợp thanh toán (VietQR, cổng, đối soát)
      f) [tùy chọn] Đối sánh khuôn mặt & OCR InBody
  5.4 Tầng AI & kiểm soát chi phí LLM                 ← yêu cầu của thầy
  5.5 DevOps & triển khai

Chương 6 — Kiểm thử & Đánh giá
  6.1 Chiến lược kiểm thử tổng thể
  6.2 Xác minh: giải pháp giải đúng vấn đề            ← câu hỏi 5
      (property-based, oracle test, golden dataset, so với baseline thủ công)
  6.3 Thẩm định: đáp ứng nhu cầu người dùng           ← câu hỏi 6
      (usability test, SUS, UAT, analytics, khảo sát trước-sau)
  6.4 Đánh giá hiệu năng & bảo mật
  6.5 Thảo luận kết quả & so sánh với mục tiêu ban đầu

Chương 7 — Kết luận
  7.1 Kết quả đạt được (đối chiếu từng đóng góp với outcome đo được)
  7.2 Hạn chế (trung thực — đây là phần hội đồng đánh giá cao)
  7.3 Hướng phát triển

Phụ lục
  A. Schema CSDL đầy đủ
  B. Đặc tả API (OpenAPI)
  C. Bảng user story & tiêu chí chấp nhận
  D. Biên bản UAT có ký nhận
  E. Kết quả khảo sát & usability test (số liệu thô)
  F. Bộ tham số & script sinh dữ liệu mô phỏng
  G. Bảng chi phí LLM chi tiết
```

---

## 6. Danh sách kiểm tra trước buổi bảo vệ

### Sản phẩm
- [ ] Hệ thống chạy được trên môi trường public (staging), có link demo
- [ ] Golden dataset 6 tháng đã nạp — demo dashboard có số liệu thật, không phải màn hình trống
- [ ] App mobile cài được qua QR (EAS Build) hoặc APK gửi trước cho hội đồng
- [ ] Video demo 5–7 phút cho các luồng cốt lõi (dự phòng khi demo trực tiếp gặp sự cố mạng)
- [ ] Tài khoản demo cho **cả 6 role**, mật khẩu ghi sẵn trên slide

### Kịch bản demo trực tiếp (12–15 phút — chọn đúng thứ tự này)
1. **Check-in** trên màn hình quầy — quét QR động từ app, ảnh hiện lên (30 giây, gây ấn tượng ngay)
2. **Quét lại chính QR đó** → bị từ chối (chứng minh chống replay)
3. **Chu trình buổi PT** — đặt lịch → xác nhận 2 chiều → chỉ ra **4 hệ quả từ 1 sự kiện**: credit giảm, PT có công, doanh thu ghi nhận, churn score đổi ⭐ *(đây là điểm mạnh nhất — chứng minh 3 giải pháp liên thông)*
4. **Buổi hỗ trợ miễn phí** — credit không đổi nhưng PT vẫn có công *(trả lời trực tiếp góp ý của thầy)*
5. **Bảo lưu** — end_date đẩy lùi, doanh thu tạm dừng ghi nhận
6. **Báo cáo tài chính** — chỉ ra sự khác biệt giữa "tiền thu được" và "doanh thu ghi nhận" ⭐
7. **Bảng lương** — mở chi tiết từng dòng, chỉ ra tính minh bạch

### Chuẩn bị trả lời câu hỏi
- [ ] "Tại sao không dùng microservices?" → §2.4.2 tài liệu 00
- [ ] "Số liệu này lấy từ đâu?" → nêu rõ đâu là khảo sát thật, đâu là `[GĐ]`, và cơ sở của giả định
- [ ] "Hệ thống của em khác gì phần mềm có sẵn trên thị trường?" → deferred revenue + session credit ledger + xác nhận 2 chiều; và **thừa nhận** những mặt phần mềm thương mại làm tốt hơn
- [ ] "Làm sao biết giải pháp thực sự hiệu quả?" → Oracle test + so sánh baseline (Chương 6)
- [ ] "Dữ liệu sinh trắc học có hợp pháp không?" → phân tích NĐ 13/2023, và lý do để nó là lớp tùy chọn
- [ ] "Chi phí vận hành thật là bao nhiêu?" → bảng chi phí đầy đủ, ~2,2 triệu VND/tháng cho 1.000 hội viên
- [ ] "Hạn chế lớn nhất của đồ án là gì?" → **chuẩn bị câu trả lời trung thực**: dữ liệu mô phỏng chứ không phải dữ liệu vận hành thật; số người thử nghiệm nhỏ; chưa chạy production dài hạn

---

## 7. Ba việc nên làm ngay tuần này

1. **Chốt phạm vi với giảng viên hướng dẫn** bằng bảng MoSCoW ở mục 1 — đặc biệt là mục **WON'T HAVE**. Việc này quan trọng hơn viết code, vì nó quyết định 16 tuần tới.

2. **Liên hệ 2–3 phòng gym để khảo sát.** Xin 30 phút phỏng vấn quản lý + lễ tân. Câu hỏi cần chuẩn bị:
   - Hiện tại đang dùng phần mềm gì? Điều gì khiến khó chịu nhất?
   - Một tháng mất bao lâu để chốt sổ và tính lương?
   - Có gặp tình trạng dùng chung thẻ / gói hết hạn vẫn vào tập không? Ước tính bao nhiêu %?
   - Tính công PT như thế nào? Có hay xảy ra tranh chấp không?
   - Giá các gói tập hiện tại là bao nhiêu? (để xác minh bảng giá `[GĐ]`)
   - Tỷ lệ hội viên gia hạn khoảng bao nhiêu?

   Kết quả khảo sát này biến toàn bộ số liệu `[GĐ]` thành **dữ liệu có căn cứ** — đây là điều làm nên sự khác biệt giữa một đồ án "tự nghĩ ra" và một đồ án "giải quyết vấn đề thật".

3. **Dựng Sprint 0**: monorepo + Docker Compose + Flyway V1 + auth. Có nền móng chạy được sớm sẽ giảm rất nhiều áp lực về sau.
