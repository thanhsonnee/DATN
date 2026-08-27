# Phần 4: Giải pháp cho các vấn đề kỹ thuật khó

> Tài liệu này trả lời chi tiết **5 câu hỏi thầy đánh dấu "chưa biết làm thế nào, hãy gợi ý"** cùng phần chi phí LLM.

---

## 1. Nhận diện chính xác hội viên khi check-in

> *"Việc hội viên đến tập phải được quản lý (hồ sơ, profile, ảnh) để khi người tập cầm thẻ đến quẹt xác minh thì hệ thống phải nhận diện được chính xác member đó đến tập"*

### 1.1 Phân tích vấn đề

Bài toán thực chất là **xác thực danh tính** (authentication) chứ không phải nhận diện thuần túy. Có ba mối đe dọa cụ thể:

| Mối đe dọa | Mô tả | Thiệt hại |
|---|---|---|
| **Mượn/dùng chung thẻ** | A đưa thẻ cho B vào tập | Mất doanh thu trực tiếp |
| **Sao chép QR** | Chụp màn hình QR gửi qua Zalo | Mất doanh thu, khó phát hiện |
| **Gói hết hạn vẫn vào** | Hệ thống không kiểm tra hoặc lễ tân bỏ qua | Mất doanh thu |

Cần một giải pháp **phân lớp**: lớp rẻ và nhanh chặn phần lớn trường hợp, lớp đắt chỉ dùng khi cần.

### 1.2 Lớp 1 — QR động HMAC-TOTP (khuyến nghị làm trước)

**Nguyên lý:** giống mã OTP của app ngân hàng. Mỗi hội viên có một `secret` riêng; app sinh mã dựa trên `secret` + thời gian hiện tại, mã đổi mỗi 30 giây. Ảnh chụp màn hình chỉ có hiệu lực trong ≤ 30 giây → gửi cho bạn thì đã hết hạn.

**Quy trình đăng ký secret** (chỉ một lần, khi cài app):

```
Server: secret = random 32 bytes (cryptographically secure)
        lưu vào qr_secrets.secret_enc (mã hóa AES-256-GCM bằng master key)
        trả secret cho app QUA HTTPS, MỘT LẦN DUY NHẤT
App:    lưu secret vào Secure Enclave / Android Keystore (expo-secure-store)
        đồng bộ độ lệch đồng hồ: clock_offset = server_time − device_time
```

**Sinh QR phía client** (hoạt động **offline** — quan trọng vì sóng trong phòng gym yếu):

```typescript
// mobile/src/lib/totp.ts
import * as Crypto from 'expo-crypto';

const PERIOD = 30;                       // giây

export async function generateCheckinToken(
  memberId: number, secret: string, clockOffsetMs: number
): Promise<string> {
  const now      = Math.floor((Date.now() + clockOffsetMs) / 1000);
  const counter  = Math.floor(now / PERIOD);
  const message  = `${memberId}:${counter}`;
  const hmac     = await hmacSha256(secret, message);      // 32 bytes
  const code     = hmac.slice(0, 16);                      // 128-bit, đủ an toàn
  // payload QR (không mã hóa nhưng đã ký — server verify được)
  return base64url(JSON.stringify({ v: 1, m: memberId, c: counter, s: code }));
}
```

**Xác thực phía server:**

```java
public CheckinVerification verify(String token, String deviceId) {
    var p = decode(token);                                   // {v, m, c, s}

    // 1. Chấp nhận cửa sổ ±1 chu kỳ (bù lệch đồng hồ và độ trễ mạng)
    long serverCounter = Instant.now().getEpochSecond() / PERIOD;
    if (Math.abs(p.counter() - serverCounter) > 1)
        return DENIED_EXPIRED_TOKEN;

    // 2. Xác minh chữ ký — dùng so sánh chống timing attack
    byte[] secret   = vault.decrypt(qrSecretRepo.findByMemberId(p.memberId()));
    byte[] expected = hmacSha256(secret, p.memberId() + ":" + p.counter());
    if (!MessageDigest.isEqual(truncate(expected, 16), p.signature()))
        return DENIED_INVALID_SIGNATURE;

    // 3. CHỐNG REPLAY — mỗi (member, counter) chỉ dùng được đúng 1 lần
    String nonceKey = "qr:used:" + p.memberId() + ":" + p.counter();
    if (Boolean.FALSE.equals(redis.opsForValue()
            .setIfAbsent(nonceKey, deviceId, Duration.ofSeconds(90)))) {
        incidentService.record(SUSPECTED_REPLAY, p.memberId(), deviceId);
        return DENIED_REPLAY;
    }

    // 4. Kiểm tra nghiệp vụ: hợp đồng còn hiệu lực? đang bảo lưu? đã trả đủ tiền?
    return membershipService.evaluateAccess(p.memberId());
}
```

**Anti-passback** (chống một người quét hộ nhiều người):

```java
// Cùng member check-in lần 2 trong 30 phút → cảnh báo, không chặn cứng
// (có thể là ra ngoài mua nước rồi vào lại — cần lễ tân phán đoán)
if (lastCheckIn(memberId).isAfter(now.minusMinutes(30)))
    flag(ANTI_PASSBACK_WARNING);

// Member đang ở TRONG phòng tập (đã check-in, chưa check-out) mà lại có
// lượt check-in mới → gần như chắc chắn có người thứ 2 dùng thẻ/QR của họ
// → cảnh báo mức HIGH, lễ tân phải xác minh bằng CCCD
if (isStillInside(memberId))
    flag(SUSPECTED_SHARING, HIGH);
```

### 1.3 Lớp 2 — Xác minh bằng mắt của lễ tân (bắt buộc, chi phí gần bằng 0)

**Đây là lớp hiệu quả nhất trên thực tế.** Ngay khi quét thành công, đẩy qua WebSocket lên màn hình quầy:

```
╔══════════════════════════════════════════════════════════╗
║  ┌────────────┐                                          ║
║  │            │   NGUYỄN VĂN AN            MB-000123      ║
║  │   ẢNH      │   Nam · 28 tuổi                           ║
║  │  HỒ SƠ     │   ───────────────────────────────────    ║
║  │  180×180   │   ✅ Fitness 6 tháng · còn 47 ngày        ║
║  └────────────┘   PT: Trần Bình · còn 5/12 buổi           ║
║                                                          ║
║  ⚠️  Lượt vào thứ 2 trong 30 phút — vui lòng kiểm tra    ║
║                                                          ║
║      [ ✓ CHO VÀO ]      [ ✗ TỪ CHỐI ]     [ Chi tiết ]   ║
╚══════════════════════════════════════════════════════════╝
```

Chi phí: chỉ cần **chụp ảnh khi đăng ký hội viên** và một màn hình web. Hiệu quả cao vì lễ tân nhận diện người quen rất nhanh.

### 1.4 Lớp 3 — Đối sánh khuôn mặt 1:1 (nâng cao, tùy chọn)

**Điểm mấu chốt về mặt kỹ thuật:** dùng **1:1 verification**, không phải **1:N identification**.

| | 1:N (nhận dạng) | **1:1 (xác minh)** ← chọn cách này |
|---|---|---|
| Đầu vào | Chỉ ảnh khuôn mặt | Ảnh + ID đã biết (từ thẻ/QR) |
| Xử lý | So với **toàn bộ** N hội viên | So với **đúng 1** embedding đã lưu |
| Độ phức tạp | O(N), cần vector database | **O(1)**, chỉ 1 phép tính cosine |
| Độ chính xác | Giảm khi N tăng | Ổn định, không phụ thuộc N |
| Thời gian | 200–800ms với N=1000 | **< 50ms** |

**Kiến trúc:**

```
Đăng ký (một lần, có consent bằng văn bản):
  Ảnh hồ sơ → detect face (RetinaFace) → align → ArcFace ONNX
           → embedding 512 chiều (float32)
           → MÃ HÓA AES-256-GCM → lưu persons.face_embedding_enc
           → ẢNH GỐC KHÔNG LƯU (chỉ giữ ảnh hồ sơ để lễ tân đối chiếu)

Check-in:
  Camera chụp → detect + liveness check (chống dùng ảnh in / màn hình)
             → ArcFace → embedding mới
             → cosine_similarity(embedding_mới, embedding_đã_lưu)
             → ≥ 0.65  : khớp, tự động cho vào
             → 0.45–0.65: nghi ngờ, chuyển lễ tân quyết định
             → < 0.45  : không khớp, ghi check_in_incidents(FACE_MISMATCH)
```

```python
# ai-service/app/face/verify.py
import numpy as np, onnxruntime as ort

session = ort.InferenceSession("models/arcface_r100.onnx",
                               providers=["CPUExecutionProvider"])

def embed(aligned_face: np.ndarray) -> np.ndarray:
    x = aligned_face.transpose(2, 0, 1)[None].astype(np.float32)
    x = (x - 127.5) / 128.0
    v = session.run(None, {"data": x})[0][0]
    return v / np.linalg.norm(v)              # chuẩn hóa L2

def verify(probe: np.ndarray, enrolled: np.ndarray) -> float:
    return float(np.dot(probe, enrolled))     # cosine similarity
```

**Tuân thủ pháp lý — phần bắt buộc phải trình bày trong báo cáo:**

Nghị định 13/2023/NĐ-CP về Bảo vệ dữ liệu cá nhân xếp **dữ liệu sinh trắc học vào nhóm dữ liệu cá nhân nhạy cảm**. Do đó thiết kế phải:

| Yêu cầu | Cách đáp ứng |
|---|---|
| **Đồng ý rõ ràng, riêng biệt** | Màn hình consent riêng, không gộp vào điều khoản chung; lưu `person_consents(person_id, consent_type, granted_at, ip, version)` |
| **Quyền từ chối** | Từ chối vẫn dùng được lớp 1 + 2 đầy đủ — **không được ép buộc** |
| **Tối thiểu hóa dữ liệu** | Chỉ lưu **embedding**, không lưu ảnh khuôn mặt gốc; embedding không thể tái tạo ngược thành ảnh |
| **Mã hóa** | AES-256-GCM ở tầng cột, khóa quản lý riêng ngoài DB |
| **Quyền xóa** | API xóa embedding, thực thi trong 72h, ghi audit |
| **Thời hạn lưu trữ** | Xóa embedding sau 90 ngày kể từ khi hội viên ngừng hoạt động |
| **Thông báo** | Biển báo có camera nhận diện tại quầy |

**Khuyến nghị thực tế cho ĐATN:** làm lớp 1 + 2 hoàn chỉnh (đủ chặn ~90% trường hợp gian lận), lớp 3 làm **demo trên môi trường thử nghiệm** với dữ liệu tình nguyện viên đã đồng ý — thể hiện năng lực kỹ thuật mà không gánh rủi ro pháp lý. Trong báo cáo, phần phân tích tuân thủ NĐ 13/2023 tự nó đã là một đóng góp.

---

## 2. Thu tiền hội viên: tiền mặt, chuyển khoản, máy quẹt thẻ

> *"thu tiền hội viên: thanh toán tiền mặt, chuyển khoản, máy quẹt thẻ… chưa biết làm thế nào"*

### 2.1 Bảng so sánh phương thức

| Phương thức | Có tích hợp API được không? | Cách xử lý | Độ khó |
|---|---|---|---|
| Tiền mặt | Không (bản chất là vật lý) | Ghi nhận thủ công + **quy trình ca làm việc để đối soát** | ⬤⬤ |
| Chuyển khoản/VietQR | **Có** — qua dịch vụ đọc biến động số dư | Sinh QR có nội dung mã hóa đơn + webhook đối soát tự động | ⬤⬤⬤ |
| Cổng thanh toán | **Có** — sandbox miễn phí | Redirect + IPN webhook + verify chữ ký | ⬤⬤⬤ |
| Máy POS quẹt thẻ | **Không** (máy POS ngân hàng không mở API cho bên thứ ba) | Nhập tay mã giao dịch từ biên lai + đối soát với sao kê cuối ngày | ⬤ |

### 2.2 Tiền mặt — quan trọng nhất là quy trình, không phải công nghệ

Vấn đề thật của tiền mặt không phải "làm sao ghi nhận" mà là **"làm sao biết tiền không bị thất thoát"**. Giải pháp là mô hình **ca làm việc (cash shift)**:

```
Mở ca:   lễ tân đăng nhập → nhập số tiền mặt đầu ca (VD 500.000đ tiền lẻ)
         → tạo cash_shifts(status = OPEN, opening_balance = 500000)

Trong ca: mỗi lần thu tiền mặt → payments(method=CASH, cash_shift_id = <ca hiện tại>)
         → in phiếu thu 2 liên (1 khách, 1 lưu)
         → hệ thống cộng dồn expected_cash

Đóng ca:  lễ tân ĐẾM TIỀN THỰC TẾ trong két → nhập counted_cash
         → hệ thống tính difference = counted_cash − expected_cash
         ├─ difference = 0    → status = CLOSED ✓
         └─ difference ≠ 0    → BẮT BUỘC nhập difference_reason
                              → status = DISCREPANCY
                              → tự động thông báo Kế toán + Admin
```

Ràng buộc DB đã cài đặt (xem `db/schema.sql`):
- `chk_pay_cash_shift`: thanh toán tiền mặt thành công **bắt buộc** phải gắn với một ca
- `uq_open_shift_per_staff`: mỗi nhân viên chỉ có tối đa 1 ca đang mở
- `chk_shift_diff_reason`: có chênh lệch thì bắt buộc có lý do

**Chỉ số quản trị rút ra:** tỷ lệ ca có chênh lệch, giá trị chênh lệch trung bình theo nhân viên → phát hiện vấn đề vận hành hoặc gian lận.

### 2.3 VietQR — phương án thực tế nhất ở Việt Nam

**Bước 1 — Sinh mã QR động** theo chuẩn EMVCo/NAPAS:

```java
public String buildVietQr(Invoice invoice) {
    return VietQr.builder()
        .bankBin("970422")                       // MB Bank
        .accountNumber("0123456789")
        .amount(invoice.balanceDue())
        // NỘI DUNG CHUYỂN KHOẢN chứa mã hóa đơn → CƠ SỞ ĐỂ ĐỐI SOÁT TỰ ĐỘNG
        .description("GYM " + invoice.invoiceNo().replace("-", ""))
        .build()
        .toQrString();      // chuỗi TLV EMVCo, render thành ảnh QR
}
```

Hoặc dùng dịch vụ sinh ảnh QR sẵn có (`img.vietqr.io`) để đơn giản hóa — nhưng **tự cài đặt bộ sinh chuỗi TLV EMVCo là điểm cộng kỹ thuật rõ rệt** cho đồ án (chuẩn công khai, không khó, thể hiện hiểu biết).

**Bước 2 — Đối soát tự động qua webhook biến động số dư:**

Dịch vụ như **SePay** hoặc **Casso** kết nối với tài khoản ngân hàng, khi có tiền vào sẽ gọi webhook. Chi phí thấp (có gói miễn phí), phù hợp đồ án.

```java
@PostMapping("/api/v1/webhooks/payments/sepay")
public ResponseEntity<Void> handle(
        @RequestBody String rawBody,
        @RequestHeader("Authorization") String apiKey) {

    // 1. Xác thực nguồn
    if (!constantTimeEquals(apiKey, config.sepayApiKey()))
        return ResponseEntity.status(401).build();

    var event = mapper.readValue(rawBody, SepayEvent.class);

    // 2. CHỐNG XỬ LÝ TRÙNG — unique(provider, event_id) ở tầng DB
    if (webhookRepo.existsByProviderAndEventId("SEPAY", event.id()))
        return ResponseEntity.ok().build();          // đã xử lý, trả 200 để không retry
    webhookRepo.save(WebhookEvent.of("SEPAY", event, rawBody));

    // 3. Trích mã hóa đơn từ nội dung chuyển khoản
    //    "CT DEN:... GYM INV2026000123 ..." → INV-2026-000123
    Optional<String> invoiceNo = InvoiceRefParser.extract(event.content());
    if (invoiceNo.isEmpty()) {
        unmatchedPaymentService.queueForManualReview(event);   // lễ tân gán tay
        return ResponseEntity.ok().build();
    }

    // 4. Ghi nhận thanh toán (idempotent)
    paymentService.recordBankTransfer(
        invoiceNo.get(), event.amount(), event.transactionId(), rawBody);

    return ResponseEntity.ok().build();
}
```

**Xử lý các tình huống thực tế bắt buộc phải nghĩ đến:**

| Tình huống | Cách xử lý |
|---|---|
| Khách sửa nội dung chuyển khoản | Đưa vào hàng đợi "chưa khớp", lễ tân gán thủ công theo số tiền + thời gian |
| Chuyển thiếu tiền | Ghi nhận một phần → `invoice.status = PARTIALLY_PAID`, thông báo khách số còn thiếu |
| Chuyển thừa tiền | Ghi nhận đủ hóa đơn, phần dư → số dư tài khoản khách (`member_wallet`) hoặc hoàn lại |
| Webhook đến 2 lần | `UNIQUE(provider, event_id)` chặn ở tầng DB |
| Webhook không đến (mất mạng) | Job đối soát mỗi 15 phút: gọi API lấy danh sách giao dịch, so với DB |
| Khách chuyển rồi mới quét QR | Vẫn khớp được vì đối soát theo nội dung, không theo thứ tự |

### 2.4 Cổng thanh toán (VNPay / MoMo / ZaloPay)

Cả ba đều có **sandbox miễn phí** cho sinh viên. Luồng chuẩn:

```
App/Web → BE tạo payment(INITIATED) → BE ký request (HMAC-SHA512 với secret)
        → redirect user sang cổng thanh toán
        → user thanh toán
        → cổng gọi IPN (server-to-server) ──► BE verify chữ ký ──► cập nhật SUCCEEDED
        → cổng redirect user về returnUrl ──► FE hiển thị kết quả
```

**Nguyên tắc bắt buộc:**
1. **Chỉ tin IPN, không tin returnUrl.** `returnUrl` là redirect trình duyệt — người dùng có thể giả mạo. IPN là server-to-server có chữ ký.
2. **Verify chữ ký trước khi đọc dữ liệu.** Sắp xếp tham số theo alphabet, nối chuỗi, HMAC với secret key, so sánh bằng hàm chống timing attack.
3. **Idempotent.** IPN có thể gọi nhiều lần — dùng `UNIQUE(provider, provider_txn_id)`.
4. **Đối chiếu số tiền.** Số tiền trong IPN phải khớp với `payments.amount` đã tạo — nếu lệch, từ chối và ghi cảnh báo bảo mật.

### 2.5 Máy POS quẹt thẻ

Máy POS của ngân hàng **không cung cấp API cho bên thứ ba** (lý do bảo mật thẻ / PCI-DSS). Cách xử lý thực tế:

```
1. Lễ tân quẹt thẻ trên máy POS ngân hàng (thiết bị độc lập)
2. Máy in biên lai có: mã giao dịch, số tiền, 4 số cuối thẻ
3. Lễ tân nhập vào hệ thống: pos_terminal_id, provider_txn_id, pos_card_last4
4. → payments(method = CARD_POS, status = SUCCEEDED)
5. Cuối ngày: Kế toán tải sao kê POS từ ngân hàng (CSV)
              → import vào hệ thống → tự động khớp theo mã giao dịch
              → giao dịch không khớp → đưa vào danh sách cần xử lý
```

Đây là quy trình các phòng gym thật đang dùng. Đưa vào báo cáo với ghi chú *"giới hạn do hạ tầng ngân hàng, không phải giới hạn thiết kế"* — thể hiện hiểu biết về ràng buộc thực tế.

### 2.6 Nguyên tắc kỹ thuật xuyên suốt module thanh toán

```java
@PostMapping("/api/v1/payments")
public PaymentDto create(@RequestBody CreatePaymentRequest req,
                         @RequestHeader("Idempotency-Key") String key) {
    // Cùng Idempotency-Key → trả về CÙNG kết quả, KHÔNG tạo giao dịch mới
    return idempotencyService.execute(key, () -> paymentService.create(req));
}
```

| Nguyên tắc | Vì sao |
|---|---|
| **Idempotency-Key bắt buộc** | Client retry khi mạng chập chờn → nếu không có, khách bị trừ tiền 2 lần |
| **Outbox pattern** | Ghi `payments` và phát sự kiện `PaymentSucceeded` trong **cùng transaction** → không bao giờ có tình trạng "đã thu tiền nhưng hợp đồng chưa kích hoạt" |
| **Máy trạng thái tường minh** | `INITIATED → PENDING → SUCCEEDED/FAILED/EXPIRED`; chỉ chuyển theo đúng đường |
| **Lưu payload thô** | `payments.raw_payload JSONB` + `webhook_events.payload` → điều tra được khi có tranh chấp |
| **Không bao giờ lưu số thẻ đầy đủ** | Chỉ 4 số cuối (`pos_card_last4`), tuân thủ PCI-DSS |

---

## 3. Nguồn dữ liệu bài tập

> *"Xem bài tập, Đánh dấu hoàn thành bài tập (phải có nguồn dữ liệu bài tập có sẵn)"*

### 3.1 So sánh nguồn dữ liệu

| Nguồn | Số bài | Có ảnh/video | Giấy phép | Đánh giá |
|---|---:|---|---|---|
| **Free Exercise DB** (`yuhonas/free-exercise-db`) | ~870 | ✅ ảnh JPG 2 góc | **Unlicense (public domain)** | ⭐ **Tốt nhất** — dùng thoải mái, JSON sạch |
| **wger** (`wger.de`) | ~500+ | ✅ ảnh, có video | CC-BY-SA 4.0 / AGPL | Tốt, nhưng phải ghi nguồn và giữ giấy phép |
| ExerciseDB (RapidAPI) | ~1300 | ✅ GIF động | Thương mại, free tier giới hạn | Không phù hợp sản phẩm thật |
| Tự nhập | Tùy | Tự chụp | Của mình | Tốn công, chỉ dùng bổ sung |

→ **Chọn Free Exercise DB làm nền, bổ sung từ wger nếu thiếu.**

### 3.2 Quy trình nhập dữ liệu

```
1. TẢI       git clone yuhonas/free-exercise-db
             → exercises.json (~870 bản ghi) + thư mục ảnh

2. CHUẨN HÓA script Node/Python ánh xạ sang schema `exercises`:
             muscle_group: "chest" → CHEST
             equipment:    "barbell" → BARBELL
             difficulty:   "beginner" → BEGINNER
             ghi source = 'FREE_EXERCISE_DB', license = 'Unlicense'

3. DỊCH      Gọi Claude Haiku 4.5 qua BATCH API (giảm 50% chi phí)
             Dịch name_en + instructions sang tiếng Việt
             Prompt: "Dịch tên và hướng dẫn bài tập gym sang tiếng Việt.
                      Giữ nguyên thuật ngữ đã phổ biến trong giới tập gym VN
                      (squat, deadlift, bench press...). Trả JSON."
             ~870 bài × (300 in + 400 out) tokens
             ≈ 0,26M in + 0,35M out → với Haiku Batch: ≈ 1,0 USD MỘT LẦN

4. RÀ SOÁT   ⚠️ BẮT BUỘC — người đọc lại toàn bộ bản dịch
             is_reviewed = TRUE sau khi duyệt
             Bài chưa duyệt KHÔNG hiển thị cho hội viên

5. ẢNH       Upload vào MinIO/S3, cập nhật media_url
```

### 3.3 Xây giáo án từ thư viện

```
exercises (870 bài, đã dịch)
    │
    ├──► workout_templates          giáo án MẪU do PT/Admin soạn
    │      "Tăng cơ 8 tuần cho người mới" (4 buổi/tuần)
    │      "Giảm mỡ 12 tuần" · "Yoga cơ bản 6 tuần"
    │           └──► workout_template_items (day_index, exercise, sets, reps, rest)
    │
    └──► workout_plans              giáo án GÁN cho hội viên cụ thể
           PT chọn template → tùy biến theo thể trạng → gán cho học viên
                └──► workout_plan_items
                        └──► workout_logs   hội viên tick hoàn thành,
                                            ghi kg/reps/RPE thực tế
```

**Màn hình "Hôm nay tập gì" trên app hội viên:**
```
┌───────────────────────────────────────┐
│  NGÀY 12 · NGỰC & TAY SAU             │
│  ───────────────────────────────────  │
│  ☑ 1. Bench Press          4×8-10     │
│       [ảnh]  60kg · nghỉ 90s          │
│  ☑ 2. Incline Dumbbell     3×10-12    │
│  ☐ 3. Cable Fly            3×12-15    │
│       [ảnh]  ⓘ Hướng dẫn              │
│  ☐ 4. Tricep Pushdown      3×12       │
│                                       │
│  Tiến độ: 2/6 bài · 18 phút           │
└───────────────────────────────────────┘
```

### 3.4 Sinh giáo án nháp bằng AI (tùy chọn)

**Nguyên tắc bắt buộc: AI chỉ tạo BẢN NHÁP, PT phải duyệt.** Ràng buộc đã cài trong DB:
```sql
CONSTRAINT chk_wp_ai_reviewed CHECK (NOT ai_generated OR reviewed_by IS NOT NULL)
```

Kỹ thuật quan trọng: **không để LLM tự bịa tên bài tập**. Thay vào đó truyền danh sách `exercise_id` có sẵn và bắt LLM chỉ được chọn trong danh sách đó, dùng **structured output** để đảm bảo định dạng:

```java
var schema = """
{ "type":"object",
  "properties": {
    "plan_name": {"type":"string"},
    "days": {"type":"array","items":{
       "type":"object",
       "properties":{
         "day_index":{"type":"integer"},
         "focus":{"type":"string"},
         "items":{"type":"array","items":{
            "type":"object",
            "properties":{
              "exercise_id":{"type":"integer"},
              "sets":{"type":"integer"},
              "reps":{"type":"string"},
              "rest_sec":{"type":"integer"}},
            "required":["exercise_id","sets","reps","rest_sec"],
            "additionalProperties":false}}},
       "required":["day_index","focus","items"],
       "additionalProperties":false}}},
  "required":["plan_name","days"],
  "additionalProperties":false }
""";
// + hậu kiểm phía server: mọi exercise_id trả về PHẢI tồn tại trong DB
//   và phải nằm trong danh sách thiết bị phòng gym đang có
```

---

## 4. Chỉ số cơ thể & biểu đồ tiến trình

> *"Body Measurement: Xem cân nặng, Xem BMI, Xem biểu đồ thay đổi"*

### 4.1 Ba cách nhập dữ liệu

| Cách | Độ khó | Ghi chú |
|---|---|---|
| **Nhập tay** (hội viên hoặc PT) | ⬤ | Bắt buộc phải có |
| **Upload phiếu InBody → OCR** | ⬤⬤⬤ | Điểm nhấn — xem 4.3 |
| Đồng bộ Google Fit / HealthKit | ⬤⬤ | Ưu tiên thấp |

### 4.2 Các chỉ số tính toán (server-side, không tính ở client)

```java
// BMI — cột GENERATED trong DB, không tính tay
bmi = weight_kg / (height_m)²

// BMR — công thức Mifflin-St Jeor (chính xác hơn Harris-Benedict)
BMR_nam = 10×kg + 6.25×cm − 5×tuổi + 5
BMR_nữ  = 10×kg + 6.25×cm − 5×tuổi − 161

// TDEE — nhu cầu calo hàng ngày
TDEE = BMR × hệ_số_vận_động
  ít vận động 1.2 · nhẹ 1.375 · vừa 1.55 · nhiều 1.725 · rất nhiều 1.9

// Tỷ lệ eo/mông — chỉ báo rủi ro tim mạch
WHR = waist_cm / hip_cm
```

**Ngưỡng BMI cho người châu Á (WHO Asia-Pacific)** — khác ngưỡng quốc tế, cần dùng đúng:

| Phân loại | BMI châu Á | BMI quốc tế |
|---|---|---|
| Thiếu cân | < 18,5 | < 18,5 |
| Bình thường | 18,5 – 22,9 | 18,5 – 24,9 |
| Thừa cân | 23,0 – 24,9 | 25,0 – 29,9 |
| Béo phì độ I | 25,0 – 29,9 | 30,0 – 34,9 |
| Béo phì độ II | ≥ 30,0 | ≥ 35,0 |

> ⚠️ **Ranh giới đạo đức bắt buộc nêu trong báo cáo:** hệ thống **chỉ hiển thị số liệu và tham chiếu ngưỡng WHO**, **không đưa ra chẩn đoán, khuyến nghị y tế, hay kế hoạch dinh dưỡng cá nhân hóa**. Mọi màn hình liên quan phải có dòng: *"Thông tin mang tính tham khảo. Vui lòng tham vấn bác sĩ hoặc chuyên gia dinh dưỡng trước khi thay đổi chế độ tập luyện/ăn uống."*

### 4.3 Đọc phiếu InBody bằng LLM Vision (điểm nhấn kỹ thuật)

**Bối cảnh thực tế:** phần lớn phòng gym tầm trung tại Việt Nam có máy **InBody 270 / 570**. Máy in ra phiếu giấy khổ A4 với hàng chục chỉ số. Hiện tại hội viên chụp lại bằng điện thoại rồi… quên. Số liệu không vào hệ thống nào.

**Giải pháp:** hội viên chụp ảnh phiếu → LLM vision trích xuất → **hiển thị form đã điền sẵn để người dùng xác nhận/sửa** → mới lưu.

```java
public InbodyDraft extractFromPhoto(byte[] imageBytes, Long memberId) {
    var response = anthropic.messages().create(MessageCreateParams.builder()
        .model("claude-haiku-4-5")          // đủ tốt cho OCR bảng số, rẻ nhất
        .maxTokens(1024)
        .outputConfig(OutputConfig.builder()
            .format(JsonOutputFormat.builder().schema(INBODY_SCHEMA).build())
            .build())
        .addUserMessageOfBlockParams(List.of(
            ContentBlockParam.ofImage(ImageBlockParam.builder()
                .source(Base64ImageSource.builder()
                    .mediaType("image/jpeg")
                    .data(Base64.getEncoder().encodeToString(imageBytes))
                    .build())
                .build()),
            ContentBlockParam.ofText(TextBlockParam.builder().text("""
                Đây là ảnh phiếu kết quả đo thành phần cơ thể InBody.
                Trích xuất các chỉ số sau. Nếu chỉ số nào không đọc được rõ,
                trả về null cho chỉ số đó — TUYỆT ĐỐI KHÔNG suy đoán hay bịa số.
                Đơn vị: cân nặng kg, chiều cao cm, mỡ %, cơ kg.
                Kèm theo trường confidence 0..1 thể hiện độ chắc chắn tổng thể.
                """).build())))
        .build());

    var draft = parse(response);
    // KHÔNG lưu thẳng — trả về cho người dùng xác nhận
    return draft.withRequiresConfirmation(true);
}
```

**Ràng buộc an toàn (đã cài trong schema):**

```sql
source            VARCHAR(20)   -- 'INBODY_OCR'
ocr_confidence    NUMERIC(4,3)
confirmed_by_user BOOLEAN NOT NULL DEFAULT TRUE
-- + CHECK hợp lý sinh học chặn số liệu vô lý:
CONSTRAINT chk_bm_weight CHECK (weight_kg IS NULL OR weight_kg BETWEEN 20 AND 300)
CONSTRAINT chk_bm_fat    CHECK (body_fat_pct IS NULL OR body_fat_pct BETWEEN 1 AND 70)
```

**Luồng UX bắt buộc:**
```
Chụp ảnh → "Đang đọc phiếu..." (2–4 giây)
         → Form ĐÃ ĐIỀN SẴN, các ô có confidence thấp được TÔ VÀNG
         → "Kiểm tra lại số liệu rồi bấm Lưu"
         → Người dùng sửa nếu cần → LƯU
```

**Chi phí:** ~1.800 token input (ảnh) + 400 output với Haiku 4.5 = **0,0038 USD/lần ≈ 100 VND/lần**. Với 800 lần đo/tháng: **~3 USD/tháng**.

### 4.4 Biểu đồ tiến trình

```
Cân nặng (kg)                                   Mục tiêu: 70kg
82 ┤●
80 ┤ ╰●╮
78 ┤    ╰●─●╮
76 ┤         ╰●╮
74 ┤            ╰●───●
72 ┤ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ (mục tiêu)
   └─┬───┬───┬───┬───┬───┬───┬───┬──
    T1  T2  T3  T4  T5  T6  T7  T8

Hiển thị kèm:  ↓ 8,2 kg trong 8 tuần  ·  ↓ 4,1% mỡ  ·  ↑ 1,3 kg cơ
```

Dùng **Recharts** trên web, **react-native-svg-charts** (hoặc Victory Native) trên mobile. Cho phép chọn chỉ số hiển thị (cân nặng / % mỡ / khối cơ / số đo vòng eo) và khoảng thời gian (1/3/6/12 tháng).

---

## 5. Tầng AI & kiểm soát chi phí LLM

> *"Cần tính cả chi phí để sử dụng LLM API khi làm sản phẩm thực tế"*

### 5.1 Nguyên tắc thiết kế

| Nguyên tắc | Diễn giải |
|---|---|
| **AI hỗ trợ, không thay thế quyết định** | Giáo án AI sinh → PT duyệt. OCR → người xác nhận. Báo cáo → LLM chỉ viết lời, **không tính số**. |
| **Không bao giờ để LLM ghi thẳng vào DB** | Mọi output là bản nháp; có ràng buộc DB cưỡng chế điều này |
| **Định tuyến mô hình theo độ khó** | Mặc định Haiku 4.5 (rẻ nhất); chỉ nâng Sonnet 5 khi thực sự cần suy luận |
| **Có ngân sách trần và tự động ngắt** | Chạm 100% ngân sách → tắt tính năng không thiết yếu, không để chi phí vượt kiểm soát |
| **Ghi log token từng request** | Không đo được thì không quản trị được |

### 5.2 Đơn giá (cập nhật 07/2026 — kiểm tra lại trước khi nộp báo cáo)

| Model | Input (USD/1M tok) | Output (USD/1M tok) |
|---|---:|---:|
| Claude Haiku 4.5 | 1,00 | 5,00 |
| Claude Sonnet 5 | 3,00 | 15,00 |
| Claude Opus 5 | 5,00 | 25,00 |

**Ba cơ chế giảm giá:**
- **Prompt caching**: đọc cache ≈ **0,1×** giá input; ghi cache 1,25× (TTL 5 phút). Hiệu quả cao cho chatbot vì system prompt dài và cố định.
- **Batch API**: **−50%** toàn bộ, cho tác vụ không cần realtime.
- **Model routing**: chọn model nhỏ nhất đủ dùng.

### 5.3 Bảng ước tính chi phí (1.000 hội viên/tháng)

| Use case | Model | Lượt/th | In/lượt | Out/lượt | Tối ưu | Chi phí/th |
|---|---|---:|---:|---:|---|---:|
| Chatbot hội viên | Haiku 4.5 | 3.000 | 2.500 | 300 | cache 2.000 tok | **6,75 $** |
| Giáo án nháp cho PT | Sonnet 5 | 300 | 4.000 | 1.500 | – | **10,35 $** |
| OCR phiếu InBody | Haiku 4.5 | 800 | 1.800 | 400 | – | **3,04 $** |
| Tổng hợp feedback (đêm) | Sonnet 5 | 30 | 20.000 | 1.200 | **Batch −50%** | **1,17 $** |
| Diễn giải báo cáo TC | Sonnet 5 | 10 | 8.000 | 2.000 | – | **0,54 $** |
| | | | | | **Tổng cơ sở** | **21,85 $** |
| | | | | | **× 2,5 an toàn** | **≈ 55 $** |

**Chi tiết cách tính chatbot (để kiểm chứng):**
```
Input:  3.000 lượt × 2.500 tok = 7,5M tok
        ├─ 2.000 tok/lượt từ cache: 6,0M × $0,10/M = $0,60
        ├─ ghi cache (~50 lần/tháng): ≈ $0,15
        └─ 500 tok/lượt không cache: 1,5M × $1,00/M = $1,50
Output: 3.000 × 300 = 0,9M tok × $5,00/M = $4,50
                                    Tổng = $6,75
```

**Quy đổi:** 55 USD × 26.000 = **≈ 1,43 triệu VND/tháng** → **≈ 1.430đ/hội viên/tháng**.

**Đặt trong bối cảnh:** gói fitness 1 tháng 700.000đ → chi phí LLM ≈ **0,2% doanh thu/hội viên**. Đây là kết luận có giá trị thực tiễn: **AI khả thi về kinh tế trong phần mềm quản lý phòng gym**.

### 5.4 Cơ chế kiểm soát chi phí (bắt buộc cài đặt)

```java
@Component
public class LlmBudgetGuard {

    public void assertWithinBudget(String feature, Long userId) {
        // 1. Hạn mức theo người dùng/ngày
        int used = redis.incr("llm:user:" + userId + ":" + today());
        redis.expire(..., Duration.ofDays(1));
        int limit = settings.getInt("llm.max_chat_per_member_per_day", 20);
        if (used > limit)
            throw new QuotaExceededException(FALLBACK_TO_STATIC_FAQ);

        // 2. Ngân sách tổ chức/tháng
        BigDecimal spent = llmUsageRepo.sumCostThisMonth();
        BigDecimal budget = settings.getDecimal("llm.monthly_budget_usd");
        if (spent.compareTo(budget.multiply(new BigDecimal("0.8"))) > 0)
            alertService.notifyAdmin(LLM_BUDGET_80_PERCENT, spent, budget);
        if (spent.compareTo(budget) >= 0 && !isEssential(feature))
            throw new BudgetExhaustedException(feature);
    }

    public void record(LlmCall call) {
        llmUsageRepo.save(LlmUsageLog.from(call));   // input/output/cache tokens + cost
    }
}
```

| Cơ chế | Chi tiết |
|---|---|
| Hạn mức người dùng | 20 lượt chatbot/hội viên/ngày; vượt → trả FAQ tĩnh |
| Ngân sách tổ chức | Trần USD/tháng; 80% → cảnh báo, 100% → tắt tính năng không thiết yếu |
| Cache tầng ứng dụng | 50 câu FAQ phổ biến nhất trả lời từ Redis, **không gọi API** |
| Ghi log đầy đủ | `llm_usage_logs` → dashboard chi phí AI theo tính năng/tháng |
| Prompt caching | System prompt + tài liệu tham chiếu đặt **trước** phần thay đổi, đánh dấu `cache_control` |
| Batch API | Tác vụ đêm (tổng hợp feedback, dịch bài tập) dùng Batch → giảm 50% |
| Giới hạn `max_tokens` | Đặt trần output theo từng tính năng, tránh câu trả lời lan man tốn tiền |

### 5.5 Chi phí hạ tầng đầy đủ (để có bức tranh thật)

| Hạng mục | Chi phí/tháng |
|---|---:|
| VPS 4 vCPU / 8GB / 100GB | 15–25 $ |
| Object storage (100GB) | ~5 $ |
| Domain + TLS | ~1 $ |
| Push notification (FCM) | 0 $ (miễn phí) |
| Dịch vụ webhook ngân hàng (SePay) | 0–8 $ |
| **LLM API** | **55 $** |
| **Tổng** | **≈ 76–94 $/tháng ≈ 2,0–2,4 triệu VND** |

Với doanh thu 812 triệu/tháng (mô hình ở `docs/03`), chi phí công nghệ chiếm **~0,28% doanh thu** — con số này rất đáng đưa vào phần kết luận của báo cáo.
