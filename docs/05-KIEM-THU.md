# Phần 5: Chiến lược Kiểm thử

> Tách bạch hai mục tiêu, tương ứng câu hỏi 5 và 6 của đề bài:
> - **Xác minh (Verification)** — *"Hệ thống có giải đúng vấn đề không?"* → đo bằng số, so với baseline
> - **Thẩm định (Validation)** — *"Ứng dụng có đáp ứng nhu cầu người dùng không?"* → đo bằng hành vi và cảm nhận người dùng

---

## PHẦN A — XÁC MINH: hệ thống giải đúng vấn đề

## 1. Kim tự tháp kiểm thử

```
                    ╱╲          E2E (Playwright/Maestro)   ~30 kịch bản
                   ╱  ╲         chậm, giòn, nhưng gần thực tế nhất
                  ╱────╲
                 ╱      ╲       Integration (Testcontainers) ~150 test
                ╱────────╲      DB thật, transaction thật
               ╱          ╲
              ╱────────────╲    Contract/API (REST Assured)  ~200 test
             ╱              ╲   gồm ma trận phân quyền tự sinh
            ╱────────────────╲
           ╱                  ╲ Unit + Property-based        ~500 test
          ╱────────────────────╲ nhanh, chạy mọi lúc
```

**Nguyên tắc phân bổ công sức:** tập trung test dày ở **lõi nghiệp vụ tài chính và sổ cái**, test mỏng ở code CRUD thuần. Không đặt mục tiêu coverage đồng đều cho mọi module — đó là lãng phí.

| Module | Mục tiêu coverage | Lý do |
|---|---:|---|
| `finance` (doanh thu, lương) | **≥ 90%** | Sai một dòng = sai tiền |
| `training` (sổ cái credit) | **≥ 90%** | Bất biến phải giữ tuyệt đối |
| `billing` (thanh toán) | **≥ 85%** | Liên quan tiền + tích hợp ngoài |
| `membership` (hợp đồng, bảo lưu) | **≥ 80%** | Nhiều quy tắc nghiệp vụ |
| `access-control` | **≥ 75%** | |
| Các module CRUD khác | ≥ 50% | Không đầu tư quá mức |

---

## 2. Unit test — công thức nghiệp vụ

Tập trung vào các hàm tính toán thuần, không phụ thuộc Spring/DB:

```java
class RevenueRecognitionCalculatorTest {

    @Test
    void phan_bo_deu_theo_ngay_cho_goi_thoi_han() {
        var reg = registration()
            .finalPrice(new BigDecimal("9000000"))
            .startDate(LocalDate.of(2026, 1, 15))
            .durationDays(365).build();

        var entries = calculator.forPeriod(reg,
            LocalDate.of(2026,1,1), LocalDate.of(2026,1,31));

        // 15/01 → 31/01 = 17 ngày
        assertThat(entries).hasSize(17);
        assertThat(sum(entries))
            .isEqualByComparingTo(new BigDecimal("419178.09"));  // 9.000.000/365×17
    }

    @Test
    void ngay_bao_luu_khong_ghi_nhan_doanh_thu() {
        var reg = registrationWithFreeze(
            LocalDate.of(2026,3,15), LocalDate.of(2026,4,13));   // 30 ngày

        var march = calculator.forMonth(reg, YearMonth.of(2026, 3));

        assertThat(march).hasSize(14);           // 1–14/03, không tính 15–31/03
    }

    @Test
    void lam_tron_ngay_cuoi_de_tong_khop_tuyet_doi() {
        // 9.000.000 / 365 = 24.657,534... → 364 ngày đầu làm tròn 2 số lẻ,
        // ngày cuối nhận phần dư để TỔNG == 9.000.000 CHÍNH XÁC
        var all = calculator.forWholeContract(reg);
        assertThat(sum(all)).isEqualByComparingTo(reg.finalPrice());
    }
}

class PayrollCalculatorTest {

    @Test
    void buoi_ho_tro_mien_phi_van_duoc_tinh_cong() {
        var sessions = List.of(
            session(PAID_PT),  session(PAID_PT),
            session(COMPLIMENTARY),                 // ← miễn phí với hội viên
            session(TRIAL));
        var rates = rateCard(PAID_PT, 120_000, COMPLIMENTARY, 60_000, TRIAL, 80_000);

        var items = payrollCalculator.sessionFees(trainer, sessions, rates);

        // 2×120.000 + 1×60.000 + 1×80.000 = 380.000
        assertThat(totalOf(items)).isEqualByComparingTo("380000");
        assertThat(items).hasSize(4);            // MỖI buổi một dòng, minh bạch
    }

    @Test
    void buoi_complimentary_khong_tru_credit_cua_hoi_vien() {
        var before = ledger.balanceOf(registrationId);
        sessionService.complete(complimentarySession);
        assertThat(ledger.balanceOf(registrationId)).isEqualTo(before);
    }
}

class FreezePolicyTest {

    @ParameterizedTest
    @CsvSource({
      // durationDays, daysUsedThisYear, requestedDays, advanceDays, expected
      "  365,  0, 30, 5, APPROVED",
      "  365, 20, 15, 5, REJECTED",     // 20+15 = 35 > 30 ngày/năm
      "   30,  0,  7, 5, REJECTED",     // gói < 90 ngày không được bảo lưu
      "  365,  0, 10, 1, REJECTED",     // báo trước < 3 ngày
      "  180,  0, 10, 5, APPROVED"
    })
    void kiem_tra_dieu_kien_bao_luu(int dur, int used, int req, int adv, String exp) {
        assertThat(policy.evaluate(dur, used, req, adv).name()).isEqualTo(exp);
    }
}

class BodyMetricCalculatorTest {

    @Test void bmi_dung_cong_thuc() {
        assertThat(calc.bmi(70.0, 175.0)).isCloseTo(22.86, within(0.01));
    }

    @Test void bmr_mifflin_st_jeor_nam() {
        // 10×70 + 6.25×175 − 5×28 + 5 = 1658.75
        assertThat(calc.bmr(MALE, 70, 175, 28)).isCloseTo(1658.75, within(0.01));
    }

    @Test void phan_loai_bmi_theo_nguong_chau_a() {
        assertThat(calc.classify(23.5)).isEqualTo(OVERWEIGHT);  // quốc tế: BÌNH THƯỜNG
    }
}
```

---

## 3. Property-based test — kiểm chứng BẤT BIẾN

> Đây là tầng test có giá trị học thuật cao nhất. Thay vì viết ví dụ cụ thể, ta khai báo **thuộc tính phải luôn đúng** và để công cụ (jqwik) sinh hàng nghìn chuỗi thao tác ngẫu nhiên để cố phá vỡ nó.

### 3.1 Bất biến sổ cái tín dụng buổi tập

```java
@Property(tries = 2000)
void so_cai_luon_nhat_quan(
        @ForAll("chuoiThaoTacNgauNhien") List<CreditOperation> ops) {

    var reg = createRegistrationWithSessions(12);

    for (var op : ops) {
        try { creditService.apply(reg.id(), op); }
        catch (InsufficientCreditException ignored) { /* từ chối hợp lệ */ }
    }

    var entries = ledgerRepo.findByRegistration(reg.id());

    // BẤT BIẾN 1: tổng delta == balance_after của bút toán cuối
    int sumDelta = entries.stream().mapToInt(Entry::delta).sum();
    assertThat(sumDelta).isEqualTo(last(entries).balanceAfter());

    // BẤT BIẾN 2: số dư không bao giờ âm ở BẤT KỲ thời điểm nào
    assertThat(entries).allMatch(e -> e.balanceAfter() >= 0);

    // BẤT BIẾN 3: balance_after mỗi bút toán = balance_after trước + delta
    for (int i = 1; i < entries.size(); i++)
        assertThat(entries.get(i).balanceAfter())
            .isEqualTo(entries.get(i-1).balanceAfter() + entries.get(i).delta());

    // BẤT BIẾN 4: một buổi tập chỉ trừ credit đúng MỘT lần
    assertThat(entries.stream()
        .filter(e -> e.entryType() == CONSUME)
        .map(Entry::sourceId).toList())
        .doesNotHaveDuplicates();
}

@Provide
Arbitrary<List<CreditOperation>> chuoiThaoTacNgauNhien() {
    return Arbitraries.of(GRANT, CONSUME, REFUND, EXPIRE, ADJUST)
        .flatMap(type -> Arbitraries.integers().between(1, 5)
            .map(n -> new CreditOperation(type, n)))
        .list().ofMinSize(1).ofMaxSize(60);
}
```

### 3.2 Bất biến ghi nhận doanh thu

```java
@Property(tries = 1000)
void doanh_thu_ghi_nhan_cong_chua_ghi_nhan_luon_bang_gia_tri_hop_dong(
        @ForAll @IntRange(min = 30,  max = 365)      int durationDays,
        @ForAll @LongRange(min = 500_000, max = 20_000_000) long price,
        @ForAll("cacLanBaoLuu")   List<FreezePeriod>  freezes,
        @ForAll @IntRange(min = 1, max = 400)         int daysElapsed) {

    var reg = createActiveRegistration(durationDays, BigDecimal.valueOf(price));
    freezes.forEach(f -> freezeService.apply(reg.id(), f));

    // chạy job ghi nhận doanh thu ngày qua ngày
    for (int d = 0; d < daysElapsed; d++)
        revenueJob.runFor(reg.startDate().plusDays(d));

    var sch = scheduleRepo.byRegistration(reg.id());

    // BẤT BIẾN CỐT LÕI — không bao giờ được sai dù chỉ 1 đồng
    assertThat(sch.recognizedAmount().add(sch.deferredAmount()))
        .isEqualByComparingTo(reg.finalPrice());

    // Không ghi nhận vượt quá giá trị hợp đồng
    assertThat(sch.recognizedAmount()).isLessThanOrEqualTo(reg.finalPrice());

    // Tổng bút toán == recognized_amount trên schedule
    assertThat(entryRepo.sumBySchedule(sch.id()))
        .isEqualByComparingTo(sch.recognizedAmount());
}
```

### 3.3 Bất biến thanh toán

```java
@Property(tries = 1000)
void tong_phan_bo_khong_bao_gio_vuot_so_tien_thanh_toan(
        @ForAll("cacHoaDon")   List<Invoice> invoices,
        @ForAll("cacKhoanThu") List<Payment> payments) {

    allocationService.autoAllocate(payments, invoices);

    for (var p : payments)
        assertThat(allocationRepo.sumByPayment(p.id()))
            .isLessThanOrEqualTo(p.amount());

    for (var inv : invoices)
        assertThat(inv.paidAmount()).isLessThanOrEqualTo(inv.totalAmount());
}
```

---

## 4. Integration test — Testcontainers (DB thật)

```java
@SpringBootTest
@Testcontainers
class CheckInConcurrencyIT {

    @Container static PostgreSQLContainer<?> pg =
        new PostgreSQLContainer<>("postgres:16-alpine");
    @Container static GenericContainer<?> redis =
        new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    @Test
    void hai_request_tru_credit_dong_thoi_chi_mot_thanh_cong() throws Exception {
        var reg = createRegistration(withSessions(1));   // chỉ còn ĐÚNG 1 buổi
        var start = new CountDownLatch(1);
        var pool  = Executors.newFixedThreadPool(2);

        var f1 = pool.submit(() -> { start.await(); return tryConsume(reg); });
        var f2 = pool.submit(() -> { start.await(); return tryConsume(reg); });
        start.countDown();

        // Đúng 1 thành công, 1 thất bại — KHÔNG được cả hai cùng thành công
        assertThat(List.of(f1.get(), f2.get()))
            .containsExactlyInAnyOrder(true, false);
        assertThat(ledger.balanceOf(reg.id())).isZero();
    }

    @Test
    void khong_the_dat_hai_buoi_trung_gio_cho_cung_mot_PT() {
        var slot = tstz("2026-08-01T19:00", "2026-08-01T20:00");
        bookingService.confirm(booking(trainerA, memberX, slot));

        // EXCLUDE constraint ở tầng DB phải chặn
        assertThatThrownBy(() ->
            bookingService.confirm(booking(trainerA, memberY, slot)))
            .isInstanceOf(ScheduleConflictException.class);
    }

    @Test
    void ghi_nhan_thanh_toan_la_idempotent() {
        var key = UUID.randomUUID().toString();
        var p1  = paymentService.create(request, key);
        var p2  = paymentService.create(request, key);   // client retry

        assertThat(p2.id()).isEqualTo(p1.id());          // CÙNG giao dịch
        assertThat(paymentRepo.count()).isEqualTo(1);    // KHÔNG tạo bản ghi thứ 2
    }

    @Test
    void so_cai_credit_khong_the_bi_sua_hay_xoa() {
        var entry = ledger.grant(regId, 12);
        assertThatThrownBy(() -> jdbc.update(
            "UPDATE session_credit_ledger SET delta = 99 WHERE id = ?", entry.id()))
            .hasMessageContaining("append-only");
    }

    @Test
    void webhook_goi_hai_lan_chi_ghi_nhan_mot_lan() {
        var payload = sepayWebhook("evt_123", 4_200_000, "GYM INV2026000001");
        webhookController.handle(payload, validKey);
        webhookController.handle(payload, validKey);      // gửi lại

        assertThat(paymentRepo.findByProviderTxnId("evt_123")).hasSize(1);
        assertThat(invoiceRepo.find(1L).paidAmount())
            .isEqualByComparingTo("4200000");             // KHÔNG cộng đôi
    }
}
```

---

## 5. Kiểm thử ma trận phân quyền (tự sinh)

Đây là bộ test bắt lỗ hổng IDOR — loại lỗi hay bị bỏ sót nhất.

```java
@ParameterizedTest
@MethodSource("maTranPhanQuyen")
void moi_endpoint_tra_dung_ma_trang_thai_cho_moi_role(
        String method, String path, Role role, Ownership own, int expectedStatus) {

    var token = tokenFor(role, own);
    var res   = mockMvc.perform(request(method, path).header(AUTHORIZATION, token));

    assertThat(res.andReturn().getResponse().getStatus()).isEqualTo(expectedStatus);
}

static Stream<Arguments> maTranPhanQuyen() {
    return Stream.of(
        // Endpoint                             Role          Sở hữu     Kỳ vọng
        args("GET", "/members/{id}/body-metrics", MEMBER,      OWN,        200),
        args("GET", "/members/{id}/body-metrics", MEMBER,      OTHER,      403), // ← IDOR
        args("GET", "/members/{id}/body-metrics", TRAINER,     ASSIGNED,   200),
        args("GET", "/members/{id}/body-metrics", TRAINER,     NOT_ASSIGNED,403),// ← IDOR
        args("GET", "/members/{id}/body-metrics", RECEPTIONIST,ANY,        403),
        args("GET", "/members/{id}/body-metrics", ADMIN,       ANY,        200),

        args("POST","/memberships",               ADMIN,       ANY,        201),
        args("POST","/memberships",               SALE,        ANY,        403),
        args("POST","/memberships",               TRAINER,     ANY,        403), // draft ghi
                                                          // nhầm là quyền PT

        args("POST","/payroll-runs",              ACCOUNTANT,  ANY,        201),
        args("POST","/payroll-runs",              ADMIN,       ANY,        403), // chỉ KT chạy
        args("GET", "/payroll-runs/{id}/items",   TRAINER,     OWN,        200),
        args("GET", "/payroll-runs/{id}/items",   TRAINER,     OTHER,      403),

        args("POST","/registrations/{id}/refund", ACCOUNTANT,  ANY,        201),
        args("POST","/registrations/{id}/refund", SALE,        ANY,        403),

        args("GET", "/reports/pnl",               ACCOUNTANT,  ANY,        200),
        args("GET", "/reports/pnl",               ADMIN,       ANY,        200),
        args("GET", "/reports/pnl",               SALE,        ANY,        403)
        // ... sinh đầy đủ cho ~80 endpoint × 6 role × 3 mức sở hữu
    );
}
```

---

## 6. Mười hai kịch bản E2E cốt lõi

| # | Kịch bản | Nền tảng | Kiểm chứng điều gì |
|---|---|---|---|
| **E1** | Lead → tập thử → báo giá có chiết khấu → Admin duyệt → thanh toán VietQR → hợp đồng ACTIVE → cấp credit | Web + webhook giả lập | BF1 toàn trình, chuỗi Sale→Tiền→Hợp đồng |
| **E2** | Hội viên mở app → sinh QR động → lễ tân quét → ảnh hiện lên → cho vào → ghi check_in | Mobile + Web | BF2, QR động, realtime |
| **E3** | Quét lại chính QR đó lần 2 → hệ thống **từ chối** (replay) | Mobile + Web | Chống replay |
| **E4** | Hội viên gói hết hạn quét → hiện đỏ → lễ tân bấm "Gia hạn ngay" → thanh toán → check-in lại thành công | Mobile + Web | Xử lý ngoại lệ + upsell tại quầy |
| **E5** | Hội viên đặt lịch PT → PT duyệt → tập → xác nhận 2 chiều → credit −1 → PT có 1 dòng công → doanh thu ghi nhận 1/12 | Mobile | **BF3 — kịch bản quan trọng nhất**, chứng minh GP1+GP2 liên thông |
| **E6** | PT ghi nhận buổi hỗ trợ miễn phí → credit hội viên **không đổi** → PT **vẫn có** dòng công | Mobile | Yêu cầu Elite Fitness của thầy |
| **E7** | Hội viên xin bảo lưu 30 ngày → duyệt → end_date đẩy lùi 30 ngày → check-in trong kỳ bị từ chối → doanh thu tạm dừng ghi nhận | Mobile + Web | BF4, phần khó nhất của nghiệp vụ hợp đồng |
| **E8** | Lễ tân mở ca → thu 3 khoản tiền mặt → đóng ca đếm **thiếu 50.000đ** → bắt buộc nhập lý do → trạng thái DISCREPANCY → Kế toán nhận cảnh báo | Web | Đối soát tiền mặt |
| **E9** | Kế toán chạy bảng lương tháng → đối chiếu **từng dòng** với bảng tính tay → chốt → sinh chi phí lương | Web | Engine lương, đúng tuyệt đối |
| **E10** | Kế toán sinh báo cáo P&L → kiểm tra: doanh thu ghi nhận ≠ tiền thực thu; deferred revenue khớp; xuất PDF có biểu đồ | Web | Đóng góp ĐG3 |
| **E11** | Hội viên báo máy hỏng kèm ảnh → tạo work order → thiết bị đổi trạng thái → kỹ thuật sửa → chi phí thành expense → **thông báo ngược lại cho hội viên** | Mobile + Web | BF6, vòng phản hồi khép kín |
| **E12** | Hội viên 30 ngày không đến → churn score HIGH → sinh task cho PT → PT gọi → ghi kết quả → hội viên gia hạn | Mobile + Web | BF7, vòng giữ chân |

**Ví dụ code E5 (Playwright):**

```typescript
test('E5: chu trình buổi PT hoàn chỉnh', async ({ page, request }) => {
  const { memberId, trainerId, regId } = await seedPtPackage({ sessions: 12 });

  // 1. Hội viên đặt lịch (qua API để rút ngắn)
  const booking = await api.post('/bookings', {
    trainerId, start: '2026-08-01T19:00:00+07:00', durationMin: 60 });

  // 2. PT duyệt
  await api.as('trainer').post(`/bookings/${booking.id}/approve`);

  // 3. Hoàn thành buổi tập + xác nhận 2 chiều
  const s = await api.as('trainer').post(`/pt-sessions/${booking.sessionId}/complete`);
  await api.as('member').post(`/pt-sessions/${s.id}/confirm`);

  // 4. KIỂM CHỨNG BỐN HỆ QUẢ TỪ MỘT SỰ KIỆN
  const ledger = await api.get(`/registrations/${regId}/credit-ledger`);
  expect(ledger.currentBalance).toBe(11);                       // credit −1
  expect(ledger.entries.at(-1).entryType).toBe('CONSUME');

  const payroll = await api.as('accountant')
      .get(`/payroll/preview?trainerId=${trainerId}&month=2026-08`);
  expect(payroll.items.filter(i => i.sourceId === s.id)).toHaveLength(1);  // 1 dòng công

  const rev = await api.as('accountant').get(`/reports/revenue?date=2026-08-01`);
  expect(rev.byCategory.PT).toBe(350_000);                      // 4.200.000 / 12

  await page.goto(`/members/${memberId}/timeline`);             // hiển thị trên UI
  await expect(page.getByText('Buổi tập với PT')).toBeVisible();
});
```

---

## 7. Kiểm thử hiệu năng (k6)

```javascript
// tests/load/checkin-peak.js
import http from 'k6/http';
import { check } from 'k6';

export const options = {
  scenarios: {
    gio_cao_diem: {                 // 18h–20h: 200 lượt check-in/phút
      executor: 'ramping-arrival-rate',
      startRate: 20, timeUnit: '1m',
      stages: [
        { target: 60,  duration: '2m' },
        { target: 200, duration: '5m' },   // đỉnh
        { target: 200, duration: '10m' },  // duy trì
        { target: 30,  duration: '3m' },
      ],
      preAllocatedVUs: 100,
    },
    duyet_app: {                    // 500 người dùng app đồng thời
      executor: 'constant-vus', vus: 500, duration: '20m',
    },
  },
  thresholds: {
    'http_req_duration{name:checkin}': ['p(95)<200'],   // check-in phải nhanh
    'http_req_duration{name:dashboard}': ['p(95)<500'],
    'http_req_duration': ['p(95)<300', 'p(99)<800'],
    'http_req_failed': ['rate<0.001'],                  // < 0,1% lỗi
  },
};
```

**Kịch bản tải riêng cho báo cáo tài chính** (truy vấn nặng nhất):
```
Sinh P&L 12 tháng trên golden dataset 6 tháng (~150.000 bản ghi doanh thu)
Mục tiêu: < 3 giây. Nếu chậm hơn → thêm materialized view hoặc bảng tổng hợp.
```

---

## 8. Kiểm thử bảo mật

| Loại | Công cụ | Kiểm tra |
|---|---|---|
| Quét tự động | OWASP ZAP baseline scan | XSS, SQLi, header thiếu, cookie không secure |
| Phụ thuộc | OWASP Dependency-Check, `npm audit` | CVE trong thư viện |
| Rò rỉ bí mật | gitleaks (chạy trong CI) | API key, mật khẩu commit nhầm |
| Phân quyền | Ma trận tự sinh (mục 5) | IDOR, thiếu kiểm tra quyền |
| Xác thực | Test thủ công theo OWASP ASVS L1 | Brute-force, refresh token rotation, reuse detection, JWT bị sửa, token hết hạn |
| Thanh toán | Test thủ công | Webhook giả mạo chữ ký, replay, sửa số tiền trong IPN |
| Upload file | Test thủ công | File .php đổi đuôi .jpg, file khổng lồ, path traversal trong tên file |
| Dữ liệu cá nhân | Rà soát thủ công | Embedding khuôn mặt có mã hóa không; API xóa dữ liệu có hoạt động không |

**Cổng chất lượng:** 0 lỗ hổng mức **High/Critical** trước khi bảo vệ.

---

## 9. Kiểm thử đối chiếu (Oracle Test) — bằng chứng mạnh nhất

Đây là phần thuyết phục nhất trong buổi bảo vệ.

```
BƯỚC 1 — Xây golden dataset
  Script sinh 6 tháng vận hành theo tham số ở docs/03 mục 4.4:
    • 1.000 hội viên, 8 PT, 2 sale, 4 lễ tân
    • ~1.400 hợp đồng, ~5.400 buổi PT, ~62.000 lượt check-in
    • ~180 lần bảo lưu, ~35 lần hoàn tiền, ~70 chuyển nhượng
    • Gài SẴN 50 tình huống bất thường (bảng dưới)

BƯỚC 2 — Xử lý thủ công (baseline)
  Một người dùng Excel xử lý cùng bộ dữ liệu theo quy trình thủ công
  → ghi lại: thời gian bỏ ra, số ca phát hiện được, kết quả tính toán

BƯỚC 3 — Chạy qua hệ thống
  Import → chạy toàn bộ job → xuất báo cáo

BƯỚC 4 — SO SÁNH TỪNG Ô
  Script tự động diff: hệ thống ↔ bảng Excel ↔ giá trị kỳ vọng đã biết trước
```

**Các tình huống gài sẵn (50 ca):**

| Nhóm | Ví dụ | Kỳ vọng hệ thống |
|---|---|---|
| Gian lận check-in (15 ca) | Thẻ A quét lần 2 khi người A vẫn đang ở trong phòng tập; QR chụp màn hình dùng lại sau 2 phút; gói hết hạn 3 ngày vẫn quét | Phát hiện ≥ 95% |
| Sai lệch công PT (10 ca) | PT ghi buổi tập vào giờ đang dạy lớp khác; buổi tập ghi cho hội viên đã hết credit | Chặn 100% |
| Kế toán phức tạp (15 ca) | Bảo lưu vắt qua 2 kỳ; hoàn tiền giữa kỳ; chuyển nhượng còn 5 tháng; nâng cấp gói bù trừ giá trị | Sai số 0đ |
| Thanh toán (10 ca) | Chuyển thiếu 50.000đ; chuyển thừa; webhook đến 2 lần; nội dung CK sai định dạng | Xử lý đúng 100% |

**Bảng kết quả để đưa vào báo cáo:**

| Chỉ số | Thủ công (Excel) | Hệ thống | Cải thiện |
|---|---|---|---|
| Thời gian chốt sổ tháng | ... giờ | ... phút | ...× |
| Ca gian lận check-in phát hiện | .../50 | .../50 | ... |
| Sai lệch tổng doanh thu ghi nhận | ... đ | **0 đ** | – |
| Dòng lương sai | ... dòng | **0 dòng** | – |
| Số bút toán không truy vết được | ... | **0** | – |

---

## PHẦN B — THẨM ĐỊNH: đáp ứng nhu cầu người dùng

## 10. Kiểm thử khả dụng có kịch bản

### 10.1 Thiết kế nghiên cứu

| Hạng mục | Chi tiết |
|---|---|
| Số người | 5–8 người/role × 6 role = **30–48 phiên** (theo Nielsen, 5 người phát hiện ~85% vấn đề) |
| Tuyển chọn | Ưu tiên người **đúng vai thật** (hội viên phòng gym, PT). Nếu dùng sinh viên đóng vai → **phải ghi rõ hạn chế này trong báo cáo** |
| Thời lượng | 30–45 phút/phiên |
| Phương pháp | Think-aloud protocol, ghi màn hình + ghi âm |
| Người điều phối | Không gợi ý, không giải thích — chỉ hỏi "bạn đang nghĩ gì?" |
| Vòng lặp | **Vòng 1** → sửa lỗi khả dụng → **Vòng 2** với nhóm khác → so sánh SUS trước/sau |

### 10.2 Bộ tác vụ theo role

**Hội viên (mobile):**
1. Tìm xem gói tập của bạn còn bao nhiêu ngày
2. Bạn vừa tới phòng gym — hãy check-in
3. Đặt lịch tập với PT Trần Bình vào 19h thứ Năm tuần này
4. Bạn vừa cân được 72,5 kg — ghi lại và xem biểu đồ 3 tháng qua
5. Máy chạy bộ số 3 bị kêu to — hãy báo cho phòng gym
6. Gói sắp hết hạn — hãy gia hạn bằng chuyển khoản
7. Xem giáo án hôm nay và đánh dấu đã hoàn thành bài đầu tiên

**PT (mobile):**
1. Xem lịch dạy hôm nay
2. Có 1 yêu cầu đặt lịch mới — duyệt nó
3. Bạn vừa dạy xong buổi với hội viên Nguyễn An — xác nhận buổi tập
4. Bạn vừa hỗ trợ một hội viên tập miễn phí 30 phút — ghi nhận để được tính công
5. Xem lương tháng này của bạn đã được bao nhiêu
6. Soạn giáo án tuần cho học viên Trần Bình

**Lễ tân (web):**
1. Mở ca làm việc với 500.000đ tiền lẻ
2. Có khách đến quẹt thẻ — xử lý check-in
3. Khách này gói đã hết hạn — xử lý tình huống
4. Khách quên thẻ và quên điện thoại — cho khách vào
5. Thu 1.800.000đ tiền mặt gia hạn gói và in phiếu thu
6. Đóng ca — bạn đếm được 2.250.000đ trong két

**Kế toán (web):**
1. Xem doanh thu tháng này và so với tháng trước
2. Nhập chi phí tiền điện tháng 7 là 55.000.000đ
3. Chạy bảng lương tháng 7 và kiểm tra lương của PT Trần Bình
4. Xuất báo cáo P&L tháng 7 ra PDF

**Sale (web):** tạo lead → đặt lịch tập thử → tạo báo giá giảm 15% → xem danh sách khách sắp hết hạn → xem hoa hồng của mình

**Admin (web):** tạo gói tập mới → xem doanh thu 6 tháng → xem đánh giá về từng PT → duyệt yêu cầu chiết khấu 25%

### 10.3 Chỉ số và ngưỡng chấp nhận

| Chỉ số | Cách đo | Ngưỡng đạt |
|---|---|---|
| **Task Success Rate** | % hoàn thành không cần trợ giúp | ≥ **90%** cho tác vụ cốt lõi (check-in, đặt lịch, thu tiền, xác nhận buổi tập) |
| **Time on Task** | Đồng hồ bấm giây | Check-in ≤ **10s** · Đặt lịch PT ≤ **60s** · Thu tiền + in phiếu ≤ **45s** · Xác nhận buổi tập ≤ **15s** |
| **Error Rate** | Số thao tác sai/tác vụ | ≤ **1** |
| **SEQ** (sau mỗi tác vụ) | "Tác vụ này dễ hay khó?" 1–7 | TB ≥ **5,5** |
| **SUS** (cuối phiên) | 10 câu chuẩn hóa | ≥ **68** (trung bình ngành), phấn đấu ≥ **75** |
| **Số vấn đề khả dụng nghiêm trọng** | Phân loại theo mức 1–4 | 0 vấn đề mức 4 (chặn hoàn toàn) |

---

## 11. Kiểm thử chấp nhận (UAT)

Mỗi user story có tiêu chí Given/When/Then được người đóng vai Product Owner (giảng viên hướng dẫn, hoặc quản lý phòng gym nếu tiếp cận được) **ký nhận**.

```gherkin
Tính năng: Xác nhận buổi tập hai chiều

  Kịch bản: Buổi cuối của gói PT
    Cho rằng  hội viên có gói PT 12 buổi, đã dùng 11 buổi
    Và       PT đã dạy buổi thứ 12
    Khi      PT bấm "Kết thúc buổi tập" và hội viên quét QR xác nhận
    Thì      số dư buổi tập về 0
    Và       hợp đồng chuyển trạng thái COMPLETED
    Và       PT nhận được 1 dòng công 120.000đ
    Và       doanh thu ghi nhận thêm 350.000đ
    Và       hội viên nhận thông báo gợi ý gia hạn
    Và       Sale nhận task upsell cho hội viên này

  Kịch bản: Hội viên không xác nhận trong 24h
    Cho rằng  PT đã bấm kết thúc buổi tập lúc 20:00 ngày 01/08
    Khi      đến 20:00 ngày 02/08 hội viên vẫn chưa xác nhận
    Thì      buổi tập tự động chuyển COMPLETED
    Và       trường auto_confirmed được đặt TRUE
    Và       buổi tập này xuất hiện trong báo cáo kiểm toán
```

**Biên bản UAT** (ký nhận từng story) đưa vào phụ lục báo cáo.

---

## 12. Chạy thử thực địa & Đo lường trong ứng dụng

### 12.1 Pilot

| Phương án | Điều kiện | Cách làm |
|---|---|---|
| **A — Ưu tiên** | Tiếp cận được 1 phòng gym < 300 hội viên | Chạy **song song** hệ thống hiện tại 2–4 tuần (không thay thế, để không gây rủi ro cho họ). Đo: thời gian check-in thực tế, số ca gian lận phát hiện, phản hồi nhân viên |
| **B — Dự phòng** | Không tiếp cận được | Mô phỏng vận hành 1 tuần với 10–15 người đóng đủ 6 vai, kịch bản có sẵn tình huống bất thường. Đủ để thu SUS và task success rate |

### 12.2 Product Analytics

Nhúng sự kiện (bảng `analytics_events` tự làm hoặc PostHog self-host):

| Phễu / chỉ số | Đo cái gì |
|---|---|
| Phễu đăng ký gói: xem → chọn → thanh toán → thành công | Bước nào rơi rụng nhiều nhất |
| Tỷ lệ hoàn tất đặt lịch PT (bắt đầu → xác nhận) | Luồng đặt lịch có phức tạp quá không |
| Retention D1/D7/D30 của app | App có được dùng lại không, hay chỉ mở 1 lần |
| Tỷ lệ check-in bằng app / tổng check-in | Mức chấp nhận công nghệ mới |
| Số hội viên ghi chỉ số cơ thể ≥ 2 lần | Tính năng có tạo được thói quen không |
| Số PT dùng tính năng soạn giáo án ≥ 3 lần/tuần | PT có thực sự dùng hay chỉ dùng khi được yêu cầu |

**Đây là điểm quan trọng:** khảo sát cho biết người dùng *nói* gì; analytics cho biết họ *làm* gì. Hai nguồn này phải khớp nhau thì kết luận mới đáng tin.

### 12.3 Khảo sát trước — sau

| Thời điểm | Nội dung |
|---|---|
| **Trước** | Khảo sát điểm đau: với mỗi nhu cầu N1–N8, "hiện tại việc này khó khăn thế nào?" (Likert 1–5) → đường cơ sở |
| **Sau** | Cùng bộ câu hỏi + **NPS**: "Bạn có giới thiệu ứng dụng này cho phòng gym khác không?" (0–10) |
| **Phỏng vấn sâu** | 3–5 người sau pilot: điều gì hữu ích nhất? thừa? thiếu? |

---

## 13. Tiêu chí kết luận (Definition of Done cho toàn đồ án)

Đồ án chỉ được kết luận là "đã giải quyết được vấn đề và đáp ứng nhu cầu" khi **cả ba nhóm điều kiện** dưới đây đồng thời thỏa mãn:

### Nhóm A — Xác minh (giải đúng vấn đề)
- [ ] Toàn bộ property-based test về bất biến **xanh** với ≥ 1.000 lần sinh ngẫu nhiên
- [ ] Coverage ≥ 90% ở `finance` và `training`, ≥ 80% ở `membership` và `billing`
- [ ] Oracle test trên golden dataset: **sai lệch doanh thu = 0đ**, **dòng lương sai = 0**
- [ ] Phát hiện ≥ 95% tình huống check-in gian lận đã gài
- [ ] Ma trận phân quyền: **0 test đỏ** (không có IDOR)
- [ ] Load test đạt ngưỡng P95 và tỷ lệ lỗi
- [ ] 0 lỗ hổng bảo mật mức High/Critical
- [ ] Thời gian chốt sổ tháng ≤ 10 phút (so với 4–8h thủ công)

### Nhóm B — Thẩm định (đáp ứng nhu cầu)
- [ ] Task Success Rate ≥ 90% cho tác vụ cốt lõi ở **cả 6 role**
- [ ] SUS trung bình ≥ 68 (mục tiêu 75); SUS vòng 2 **cao hơn** vòng 1
- [ ] Thời gian check-in thực đo ≤ 10 giây
- [ ] 100% user story cốt lõi được ký nhận UAT
- [ ] Analytics cho thấy tính năng cốt lõi được sử dụng lặp lại (retention D7 > 40%)
- [ ] Khảo sát sau > khảo sát trước có ý nghĩa ở các nhu cầu N1–N8

### Nhóm C — Trung thực học thuật
- [ ] **Ghi rõ mọi hạn chế**: dữ liệu mô phỏng không phải dữ liệu thật; số người thử nghiệm nhỏ; chưa chạy production quy mô lớn
- [ ] Mọi số liệu `[GĐ]` được đánh dấu rõ và nêu cách xác minh
- [ ] So sánh sòng phẳng với các giải pháp hiện có trên thị trường, nêu rõ hệ thống **không** vượt trội ở điểm nào
