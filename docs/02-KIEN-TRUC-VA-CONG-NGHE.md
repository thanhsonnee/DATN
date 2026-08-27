# Phần 2: Kiến trúc & Công nghệ

## 1. Nguyên tắc lựa chọn công nghệ

1. **Ưu tiên công nghệ đã trưởng thành, tài liệu tốt** — đồ án 1 người, không có thời gian debug framework lạ.
2. **Ít ngôn ngữ nhất có thể** — mỗi ngôn ngữ thêm vào là một bộ tooling, build, test, deploy phải học và bảo trì. Kết quả: **2 ngôn ngữ chính** (Java cho backend, TypeScript cho web+mobile) + Python **chỉ khi bắt buộc** (AI service).
3. **Chọn được thì chọn cái tự cài đặt được phần lõi** — thầy yêu cầu tự làm login/phân quyền, nên dùng Spring Security (framework hỗ trợ, nhưng logic là mình viết) thay vì Keycloak (black box).
4. **Deploy được trên 1 VPS rẻ** — không phụ thuộc dịch vụ cloud đắt tiền, để demo được và để nói được về chi phí vận hành thật.

---

## 2. Stack tổng thể

```
┌──────────────────────────────────────────────────────────────────────────┐
│                              CLIENT                                      │
├────────────────────────────────────┬─────────────────────────────────────┤
│  MOBILE  React Native + Expo (TS)  │  WEB  React 18 + Vite + TypeScript  │
│  ├ App Hội viên                    │  ├ Admin Console                    │
│  └ App PT                          │  ├ Sales CRM                        │
│  (1 codebase, 2 build variant)     │  ├ Front Desk (lễ tân)              │
│                                    │  └ Finance (kế toán)                │
│  expo-camera (QR)                  │  TanStack Query · Zustand           │
│  expo-notifications (push)         │  shadcn/ui + Tailwind CSS           │
│  expo-secure-store (token)         │  Recharts (biểu đồ)                 │
│  react-native-mmkv (offline cache) │  React Hook Form + Zod              │
└────────────────────────────────────┴─────────────────────────────────────┘
                    │                              │
                    └──────────────┬───────────────┘
                          HTTPS · REST/JSON · WebSocket (STOMP)
                                   │
┌──────────────────────────────────▼───────────────────────────────────────┐
│                    EDGE — Caddy (TLS tự động) / Nginx                    │
│              reverse proxy · gzip/brotli · rate limit · CORS             │
└──────────────────────────────────┬───────────────────────────────────────┘
                                   │
┌──────────────────────────────────▼───────────────────────────────────────┐
│           BACKEND — Spring Boot 3.3 · Java 21 (LTS) · Gradle             │
│                        MODULAR MONOLITH                                  │
│                                                                          │
│  Spring Web MVC · Spring Security (JWT tự cài) · Spring Data JPA         │
│  Flyway (migration) · MapStruct (DTO) · springdoc-openapi (Swagger)      │
│  Spring Scheduler (job) · Spring WebSocket · Spring Validation           │
│  Resilience4j (circuit breaker cho tích hợp ngoài)                       │
└───┬──────────────┬───────────────┬────────────────┬──────────────────────┘
    │              │               │                │
┌───▼──────┐ ┌─────▼─────┐ ┌───────▼───────┐ ┌──────▼────────────────────┐
│PostgreSQL│ │  Redis 7  │ │  MinIO (S3)   │ │  DỊCH VỤ VỆ TINH          │
│    16    │ │           │ │               │ │                           │
│ nguồn sự │ │ • cache   │ │ • ảnh hồ sơ   │ │ ai-service (FastAPI/Py)   │
│ thật     │ │ • QR nonce│ │ • ảnh check-in│ │  ├ face verify 1:1 (ONNX) │
│ giao dịch│ │ • rate lim│ │ • media bài   │ │  ├ InBody OCR (LLM vision)│
│ ACID     │ │ • session │ │   tập         │ │  └ churn scoring          │
│          │ │ • job lock│ │ • chứng từ    │ │                           │
│          │ │           │ │ • báo cáo PDF │ │ checkin-agent (Electron)  │
│          │ │           │ │               │ │  └ đọc thẻ RFID USB-HID   │
└──────────┘ └───────────┘ └───────────────┘ └───────────────────────────┘
                                   │
                      ┌────────────▼─────────────┐
                      │   TÍCH HỢP BÊN NGOÀI     │
                      │ • VNPay/MoMo sandbox     │
                      │ • SePay/Casso (webhook   │
                      │   biến động số dư)       │
                      │ • Firebase Cloud Msg     │
                      │ • Anthropic Claude API   │
                      │ • SMTP (email báo cáo)   │
                      └──────────────────────────┘
```

---

## 3. Lý giải từng lựa chọn

### 3.1 Backend: Spring Boot 3.3 / Java 21

| Tiêu chí | Lý do chọn |
|---|---|
| **Transaction ACID mạnh** | Nghiệp vụ lõi (trừ credit + ghi công + ghi nhận doanh thu) phải nguyên tử. `@Transactional` + JPA + PostgreSQL là tổ hợp đã kiểm chứng. |
| **Spring Security cho phép tự cài đặt** | Đúng yêu cầu của thầy: viết `JwtAuthenticationFilter`, `UserDetailsService`, `AccessGuard` bằng tay — hiểu từng bước, nhưng không phải tự viết TLS hay CSRF từ đầu. |
| **Kiểm thử mạnh** | JUnit 5 + Testcontainers + jqwik (property-based) — hệ sinh thái test tốt nhất cho nghiệp vụ tài chính. |
| **Hệ sinh thái tài chính/enterprise** | `BigDecimal` chuẩn cho tiền tệ (**không bao giờ dùng `double` cho tiền**), thư viện báo cáo, scheduler. |
| **Java 21 LTS** | Records (DTO gọn), pattern matching, virtual threads (xử lý I/O đồng thời tốt hơn cho check-in giờ cao điểm). |

**Phương án thay thế đã cân nhắc:**

| Stack | Ưu | Nhược trong bối cảnh này | Kết luận |
|---|---|---|---|
| **NestJS + TypeScript** | Một ngôn ngữ cho toàn hệ thống; chia sẻ type với FE | Xử lý tiền tệ & decimal yếu hơn; transaction phức tạp hơn với TypeORM/Prisma; hệ sinh thái test nghiệp vụ tài chính mỏng hơn | Chọn nếu ưu tiên tốc độ phát triển hơn độ chặt chẽ tài chính |
| **Django + DRF** | Admin panel có sẵn, phát triển nhanh | Kém tự nhiên cho realtime & mobile API; ORM khó tối ưu query báo cáo phức tạp | Không chọn |
| **ASP.NET Core** | Tương đương Spring về mọi mặt | Ít tài liệu tiếng Việt hơn, cộng đồng SV VN nhỏ hơn | Không chọn |

> **Khuyến nghị:** dùng **Spring Boot**. Nếu đã có kinh nghiệm sâu với TypeScript và muốn giảm rủi ro tiến độ, NestJS là lựa chọn thay thế hợp lý — nhưng **phải cam kết dùng thư viện decimal (`decimal.js`) cho mọi phép tính tiền**, và phần này phải nêu rõ trong báo cáo.

### 3.2 Web: React 18 + TypeScript + Vite

| Thư viện | Vai trò | Vì sao |
|---|---|---|
| **Vite** | Build tool | Dev server nhanh, cấu hình đơn giản |
| **TanStack Query** | Server state | Cache, revalidate, optimistic update, polling — giảm ~40% code so với gọi fetch thủ công |
| **Zustand** | Client state | Nhẹ hơn Redux nhiều, đủ cho auth state & UI state |
| **shadcn/ui + Tailwind** | UI | Copy component vào repo (không phải dependency) → tùy biến thoải mái, giao diện chuyên nghiệp mà không cần designer |
| **Recharts** | Biểu đồ | API khai báo, dễ làm chart tài chính (line, bar, area, pie, combo) |
| **React Hook Form + Zod** | Form & validate | Zod schema **dùng chung** cho FE validate và type inference; đồng bộ với validate BE |
| **TanStack Table** | Bảng dữ liệu | Sort/filter/pagination cho màn hình kế toán nhiều dòng |
| **date-fns** | Ngày tháng | Nhẹ, tree-shakable |

**4 ứng dụng web riêng biệt hay 1 ứng dụng nhiều route?** → **1 ứng dụng, phân vùng theo route + layout theo role**. Lý do: chia sẻ component, auth, API client; deploy 1 lần. Chỉ tách riêng **màn hình Front Desk** thành route toàn màn hình (kiosk mode) vì UX khác hẳn.

### 3.3 Mobile: React Native + Expo

**Vì sao React Native chứ không phải Flutter?**

| Tiêu chí | React Native + Expo | Flutter |
|---|---|---|
| Chia sẻ code với web | ✅ Dùng chung TypeScript types, Zod schema, API client, logic tính toán | ❌ Dart, phải viết lại |
| Đường cong học tập (khi đã biết React) | ✅ Thấp | ⚠️ Phải học Dart + widget tree |
| Hiệu năng UI phức tạp | ⚠️ Tốt (New Architecture/Fabric) | ✅ Tốt hơn cho animation nặng |
| Camera / QR / Push | ✅ expo-camera, expo-notifications, expo-barcode-scanner | ✅ Tương đương |
| Build & phân phối cho demo | ✅ **EAS Build** — build cloud, cài qua QR, không cần Mac cho iOS | ⚠️ Cần Mac để build iOS |

→ **Chọn React Native + Expo**: lý do quyết định là **chia sẻ TypeScript giữa web và mobile** (giảm bug và công sức đáng kể cho một người làm), và **EAS Build** cho phép demo trên iOS mà không cần máy Mac.

**Một codebase, hai app?** Dùng **một Expo project** với:
- Điều hướng root rẽ nhánh theo `user.role`: `MemberNavigator` hoặc `TrainerNavigator`
- Chia sẻ toàn bộ: auth, API client, theme, component cơ bản
- Nếu cần 2 app riêng trên store: dùng **Expo config plugins + app variants** (2 `app.config.ts`, cùng source)

> Khuyến nghị cho ĐATN: **một app duy nhất, rẽ nhánh theo role**. Đơn giản hơn, demo dễ hơn, và vẫn thể hiện đủ năng lực.

**Tính năng mobile đặc thù cần xử lý:**
- **Offline-first cho QR check-in**: QR sinh phía client bằng secret đã đồng bộ (không cần mạng lúc quét) — quan trọng vì sóng trong phòng gym thường yếu
- **Đồng bộ đồng hồ**: TOTP cần thời gian chuẩn → app đồng bộ offset với server khi mở, chấp nhận cửa sổ ±1 chu kỳ
- **Biometric lock** (`expo-local-authentication`): mở app bằng vân tay/Face ID cho PT (dữ liệu học viên nhạy cảm)

### 3.4 Database: PostgreSQL 16

| Tính năng dùng tới | Mục đích |
|---|---|
| `NUMERIC(14,2)` | **Bắt buộc cho mọi cột tiền** — không dùng `float/double` |
| Partial index | `CREATE INDEX ... WHERE status='ACTIVE'` cho bảng registrations lớn |
| `GENERATED ALWAYS AS` | Cột tính toán (VD `final_price = list_price - discount`) |
| `EXCLUDE` constraint + `btree_gist` | **Chống trùng lịch**: một PT không thể có 2 buổi tập overlap — ràng buộc ở tầng DB, không phụ thuộc code |
| Range types (`tstzrange`) | Biểu diễn khoảng thời gian buổi tập / bảo lưu |
| JSONB | Lưu payload webhook thô, snapshot cấu hình, metadata linh hoạt |
| Window functions | Báo cáo: running total, so sánh kỳ trước, xếp hạng PT |
| Materialized view | Dashboard tổng hợp, refresh định kỳ |
| `pg_trgm` | Tìm kiếm mờ tên hội viên ở màn hình lễ tân |

**Không dùng MongoDB** cho hệ thống này: dữ liệu quan hệ chặt chẽ (hợp đồng ↔ thanh toán ↔ doanh thu ↔ lương), cần transaction đa bảng và ràng buộc toàn vẹn — đây chính xác là điểm mạnh của RDBMS.

### 3.5 Redis 7

| Dùng cho | Chi tiết |
|---|---|
| Chống replay QR | Lưu `nonce` đã dùng, TTL 60s |
| Rate limiting | Login (5 lần/15 phút), API check-in, chatbot LLM (theo user/ngày) |
| Cache | Danh sách gói tập, thư viện bài tập, cấu hình hệ thống, kết quả FAQ chatbot |
| Distributed lock | Đảm bảo job hàng đêm chỉ chạy 1 instance |
| Blacklist JWT | `jti` của access token đã logout |
| Pub/Sub | Đẩy sự kiện check-in real-time lên màn hình lễ tân |

### 3.6 MinIO (S3-compatible)

Self-host được (Docker) → demo local không tốn tiền; nếu deploy thật đổi sang Cloudflare R2 / AWS S3 chỉ cần đổi endpoint. Lưu: ảnh hồ sơ, ảnh check-in, media bài tập, chứng từ chi phí, báo cáo PDF đã sinh.

**Bảo mật:** ảnh hồ sơ và ảnh check-in **không public** — truy cập qua **presigned URL** TTL ngắn (5 phút) do backend cấp sau khi kiểm tra quyền.

### 3.7 AI Service (Python FastAPI) — chỉ khi làm tính năng nâng cao

Tách riêng vì lý do kỹ thuật chính đáng: **ONNX Runtime + InsightFace chỉ có hệ sinh thái tốt trên Python**. Giao tiếp với backend qua REST nội bộ (không expose ra internet).

```
ai-service/
├── app/
│   ├── face/        # đối sánh 1:1 — ArcFace ONNX, trả similarity score
│   ├── ocr/         # gọi Claude vision API đọc phiếu InBody
│   ├── churn/       # tính điểm rủi ro (rule-based, sau nâng lên sklearn)
│   └── llm/         # RAG chatbot, sinh giáo án nháp, prompt cache
└── models/          # file .onnx (không commit — tải lúc build)
```

> **Nếu tiến độ căng**: bỏ face verification (lớp 3 vốn là tùy chọn) và gọi Claude API trực tiếp từ Java bằng HTTP client → **không cần Python service**, giảm 1 thành phần phải vận hành. Đây là phương án dự phòng nên chuẩn bị sẵn.

### 3.8 Check-in Agent (máy quầy lễ tân)

Đầu đọc RFID USB thường hoạt động ở chế độ **HID keyboard emulation** — cắm vào là gõ ra mã thẻ như bàn phím. Vì vậy:
- **Phương án đơn giản (khuyến nghị)**: màn hình Front Desk trên web có ô input luôn focus → đầu đọc "gõ" mã thẻ vào → JS bắt sự kiện, gọi API. **Không cần app riêng.**
- **Phương án nâng cao**: Electron app nếu cần đọc thẻ ở chế độ raw hoặc điều khiển camera/máy in phiếu chuyên dụng.

---

## 4. Cấu trúc code base

### 4.1 Monorepo tổng thể

```
gym-management/
├── backend/                        # Spring Boot — Gradle multi-module
│   ├── build.gradle.kts
│   ├── settings.gradle.kts
│   ├── app/                        # module khởi động, cấu hình, wiring
│   ├── common/                     # kiểu dùng chung, exception, util, Money
│   └── modules/                    # ← các module nghiệp vụ
│
├── web/                            # React + Vite
├── mobile/                         # React Native + Expo
├── packages/
│   ├── shared-types/               # ← TypeScript types SINH TỰ ĐỘNG từ OpenAPI
│   └── shared-utils/               # logic dùng chung web + mobile (format tiền, BMI...)
│
├── ai-service/                     # Python FastAPI (tùy chọn)
├── db/
│   ├── schema.sql                  # schema tham chiếu (đọc hiểu)
│   └── migrations/                 # Flyway V1__init.sql, V2__..., nguồn sự thật
├── infra/
│   ├── docker-compose.yml          # môi trường dev đầy đủ
│   ├── docker-compose.prod.yml
│   └── Caddyfile
├── docs/                           # tài liệu ĐATN
├── tests/
│   ├── e2e-web/                    # Playwright
│   ├── e2e-mobile/                 # Maestro
│   └── load/                       # k6
└── .github/workflows/ci.yml
```

**Điểm quan trọng:** `packages/shared-types` được **sinh tự động từ OpenAPI spec** của backend (`openapi-typescript`). Backend đổi DTO → chạy `npm run gen:types` → web và mobile báo lỗi biên dịch ngay tại chỗ sai. Đây là lợi ích lớn nhất của việc chọn TypeScript cho cả hai client.

### 4.2 Cấu trúc module backend (Modular Monolith)

```
backend/modules/
├── identity/                   M1 — người, tài khoản, xác thực, phân quyền
├── crm-sales/                  M2 — lead, pipeline, báo giá, hoa hồng
├── membership/                 M3 — gói tập, giá, hợp đồng, bảo lưu, chuyển nhượng
├── access-control/             M4 — check-in, QR, thẻ, anti-passback, sự cố
├── training/                   M5 — buổi PT, lớp, giáo án, bài tập, chỉ số cơ thể
├── billing/                    M6 — hóa đơn, thanh toán, cổng TT, ca lễ tân, hoàn tiền
├── finance/                    M7 — ghi nhận doanh thu, chi phí, lương, báo cáo
├── facility/                   M8 — thiết bị, bảo trì, phòng tập
├── engagement/                 M9 — feedback, thông báo, churn, task chăm sóc
├── analytics/                  M10 — dashboard, số liệu tổng hợp
└── ai-assist/                  M11 — client gọi AI service, kiểm soát chi phí LLM
```

**Cấu trúc bên trong mỗi module** (áp dụng thống nhất):

```
modules/membership/
├── api/                        # tầng vào — Controller, DTO request/response
│   ├── MembershipController.java
│   ├── RegistrationController.java
│   └── dto/
├── domain/                     # ← LÕI NGHIỆP VỤ, không phụ thuộc Spring/JPA
│   ├── model/                  #   Entity nghiệp vụ, Value Object (Money, DateRange)
│   ├── service/                #   RegistrationService, FreezePolicyService
│   ├── event/                  #   RegistrationActivatedEvent, ...
│   └── policy/                 #   FreezePolicy, DiscountPolicy, PricingPolicy
├── infrastructure/             # tầng ra — JPA entity, repository, adapter
│   ├── persistence/
│   └── external/
└── internal/                   # API nội bộ để module khác gọi (interface)
    └── MembershipQueryPort.java
```

### 4.3 Quy tắc phụ thuộc giữa module (bắt buộc tuân thủ)

```
       api  ──►  domain  ◄──  infrastructure
                   ▲
                   │  (chỉ qua interface trong internal/)
              module khác
```

**Ba luật cứng:**
1. Module A **không được** import JPA repository hay entity của module B. Chỉ gọi qua interface trong `B/internal/`.
2. Package `domain` **không được** import `org.springframework.data.*` hay `jakarta.persistence.*` → lõi nghiệp vụ test được bằng unit test thuần, không cần Spring context.
3. Giao tiếp bất đồng bộ giữa module qua **domain event** + **outbox table** (không gọi trực tiếp), ví dụ:

```java
// membership module phát event
eventPublisher.publish(new RegistrationActivatedEvent(regId, memberId, sessionCount));

// training module lắng nghe → cấp credit
@TransactionalEventListener
void on(RegistrationActivatedEvent e) {
    creditLedger.grant(e.registrationId(), e.sessionCount(), "REGISTRATION_ACTIVATED");
}

// finance module lắng nghe → khởi tạo lịch ghi nhận doanh thu
@TransactionalEventListener
void on(RegistrationActivatedEvent e) {
    revenueScheduler.createSchedule(e.registrationId());
}
```

**Kiểm soát tự động:** dùng **ArchUnit** viết test kiến trúc, CI fail nếu ai vi phạm luật phụ thuộc. Đây là điểm cộng rõ rệt trong báo cáo — chứng minh kiến trúc được *thực thi*, không chỉ *được vẽ*.

```java
@ArchTest
static final ArchRule domain_khong_phu_thuoc_spring =
    noClasses().that().resideInAPackage("..domain..")
        .should().dependOnClassesThat().resideInAnyPackage(
            "org.springframework..", "jakarta.persistence..");
```

### 4.4 Cấu trúc web

```
web/src/
├── app/
│   ├── router.tsx                  # định tuyến + route guard theo role
│   ├── providers.tsx               # QueryClient, Theme, Auth
│   └── layouts/                    # AdminLayout, FrontDeskLayout, ...
├── features/                       # ← tổ chức THEO TÍNH NĂNG, không theo loại file
│   ├── auth/
│   ├── memberships/
│   ├── registrations/
│   ├── front-desk/                 # màn hình quầy (kiosk)
│   ├── sales-pipeline/
│   ├── finance-reports/
│   ├── payroll/
│   └── facilities/
│        ├── api.ts                 #   gọi API (dùng shared-types)
│        ├── hooks.ts               #   useQuery/useMutation
│        ├── components/
│        └── pages/
├── components/ui/                  # shadcn/ui
├── lib/                            # apiClient (axios + interceptor refresh token), utils
└── types/                          # re-export từ packages/shared-types
```

### 4.5 Cấu trúc mobile

```
mobile/src/
├── navigation/
│   ├── RootNavigator.tsx           # rẽ nhánh theo role sau khi đăng nhập
│   ├── MemberNavigator.tsx
│   └── TrainerNavigator.tsx
├── features/
│   ├── auth/
│   ├── my-membership/
│   ├── qr-checkin/                 # sinh QR động offline
│   ├── booking/
│   ├── workout/
│   ├── body-metrics/
│   ├── payment/
│   ├── trainer-schedule/
│   ├── trainer-payroll/
│   └── session-confirm/
├── lib/
│   ├── apiClient.ts                # dùng chung logic với web
│   ├── secureStorage.ts            # expo-secure-store
│   ├── totp.ts                     # sinh mã QR động
│   └── offlineQueue.ts             # hàng đợi thao tác khi mất mạng
└── theme/
```

---

## 5. Thiết kế API

### 5.1 Quy ước

- **REST**, versioned: `/api/v1/...`
- Đặt tên tài nguyên số nhiều, snake_case cho query param, camelCase cho JSON body
- **Phân trang chuẩn**: `?page=0&size=20&sort=createdAt,desc` → trả `{ content, page, size, totalElements, totalPages }`
- **Định dạng lỗi thống nhất** (RFC 7807 Problem Details):

```json
{
  "type": "https://api.gym.vn/errors/insufficient-credit",
  "title": "Không đủ số buổi tập",
  "status": 409,
  "detail": "Hợp đồng REG-00123 còn 0 buổi, không thể đặt lịch.",
  "instance": "/api/v1/bookings",
  "traceId": "8f3a2b...",
  "errors": []
}
```

- **Idempotency**: mọi POST tạo giao dịch tiền bắt buộc có header `Idempotency-Key: <uuid>`; server lưu key + response trong Redis 24h.
- **OpenAPI 3.1** sinh tự động bằng springdoc → Swagger UI + sinh TypeScript types.

### 5.2 Nhóm endpoint chính (trích)

```
POST   /api/v1/auth/login | refresh | logout
GET    /api/v1/auth/me

GET    /api/v1/memberships                       # danh sách gói (công khai)
POST   /api/v1/memberships                       # Admin
POST   /api/v1/memberships/{id}/prices           # tạo phiên bản giá mới

POST   /api/v1/quotes                            # Sale tạo báo giá
POST   /api/v1/quotes/{id}/approve               # Admin duyệt chiết khấu vượt hạn mức
POST   /api/v1/quotes/{id}/convert               # → registration

GET    /api/v1/registrations?status=ACTIVE&expiringInDays=30
POST   /api/v1/registrations/{id}/freeze         # bảo lưu
POST   /api/v1/registrations/{id}/unfreeze
POST   /api/v1/registrations/{id}/transfer       # chuyển nhượng
POST   /api/v1/registrations/{id}/refund

POST   /api/v1/check-ins                         # lễ tân / kiosk
GET    /api/v1/check-ins/verify?token=...        # xác thực QR động
WS     /ws/front-desk                            # đẩy sự kiện real-time

GET    /api/v1/trainers/{id}/availability?from=&to=
POST   /api/v1/bookings                          # hội viên đặt lịch PT
POST   /api/v1/bookings/{id}/approve|reject      # PT
POST   /api/v1/pt-sessions/{id}/start|complete
POST   /api/v1/pt-sessions/{id}/confirm          # hội viên xác nhận (2 chiều)

GET    /api/v1/registrations/{id}/credit-ledger  # sổ cái buổi tập

GET    /api/v1/exercises?muscleGroup=&equipment=
POST   /api/v1/workout-plans
POST   /api/v1/workout-logs

POST   /api/v1/body-metrics
POST   /api/v1/body-metrics/import-inbody        # upload ảnh phiếu → OCR

POST   /api/v1/invoices
POST   /api/v1/payments                          # Idempotency-Key bắt buộc
GET    /api/v1/payments/{id}/vietqr              # sinh mã QR chuyển khoản
POST   /api/v1/webhooks/payments/{provider}      # verify chữ ký
POST   /api/v1/cash-shifts/open|close

POST   /api/v1/expenses
POST   /api/v1/payroll-runs                      # kế toán chạy lương
GET    /api/v1/payroll-runs/{id}/items
GET    /api/v1/reports/pnl?from=&to=
GET    /api/v1/reports/cashflow
GET    /api/v1/reports/deferred-revenue
GET    /api/v1/reports/{id}/export?format=pdf|xlsx

POST   /api/v1/feedbacks
GET    /api/v1/retention-tasks?assignee=me
GET    /api/v1/dashboards/admin|sale|trainer|accountant
```

---

## 6. Hạ tầng & DevOps

### 6.1 Môi trường phát triển

```yaml
# infra/docker-compose.yml
services:
  postgres:   { image: postgres:16-alpine,  ports: ["5432:5432"] }
  redis:      { image: redis:7-alpine,      ports: ["6379:6379"] }
  minio:      { image: minio/minio,         ports: ["9000:9000", "9001:9001"] }
  mailhog:    { image: mailhog/mailhog,     ports: ["8025:8025"] }   # test email
  # backend, ai-service chạy trực tiếp từ IDE để debug nhanh
```

`docker compose up` → có ngay môi trường đầy đủ. Flyway tự chạy migration khi backend khởi động.

### 6.2 CI/CD (GitHub Actions)

```
Pull Request:
  ├─ backend:  ./gradlew build test         (unit + Testcontainers integration)
  │            + ArchUnit (kiểm tra kiến trúc)
  │            + JaCoCo coverage gate (≥80% module lõi)
  ├─ web:      tsc --noEmit + eslint + vitest + build
  ├─ mobile:   tsc --noEmit + eslint + jest
  ├─ security: OWASP Dependency-Check + gitleaks (quét secret rò rỉ)
  └─ e2e:      Playwright (chạy trên docker compose ephemeral)

Merge vào main:
  ├─ build Docker image, push GHCR
  ├─ deploy staging (VPS), chạy smoke test
  └─ mobile: EAS Build → bản preview cài qua QR
```

### 6.3 Triển khai production

```
1 VPS (4 vCPU / 8GB RAM / 100GB SSD — khoảng 15–25 USD/tháng)
  ├─ Caddy       : TLS tự động (Let's Encrypt), reverse proxy
  ├─ backend     : Docker container (JVM, -Xmx3g)
  ├─ ai-service  : Docker container (tùy chọn)
  ├─ postgres    : Docker + volume, backup pg_dump hàng ngày → object storage
  ├─ redis       : Docker
  ├─ minio       : Docker + volume
  └─ web         : static build, Caddy phục vụ trực tiếp

Giám sát: Spring Actuator + Prometheus + Grafana (tùy chọn)
          Log có cấu trúc (JSON) + traceId xuyên request
          Uptime check (UptimeRobot free tier)
```

### 6.4 Bảo mật — checklist bắt buộc

| Hạng mục | Biện pháp |
|---|---|
| Mật khẩu | BCrypt cost 12; chính sách độ mạnh; không log |
| Token | Access 15 phút, refresh rotation + reuse detection; lưu HttpOnly cookie (web) / SecureStore (mobile) |
| Truyền tải | HTTPS bắt buộc, HSTS, TLS 1.2+ |
| Dữ liệu nhạy cảm | CCCD, embedding khuôn mặt: mã hóa AES-256-GCM ở tầng cột; khóa trong biến môi trường/vault |
| SQL Injection | JPA parameterized query; cấm nối chuỗi SQL |
| XSS | React auto-escape; CSP header; sanitize HTML nếu có rich text |
| CSRF | SameSite=Strict cookie + CSRF token cho form web |
| IDOR | `AccessGuard` cấp bản ghi + test ma trận phân quyền tự động |
| Rate limit | Login, check-in, thanh toán, chatbot LLM |
| File upload | Kiểm MIME thật (magic bytes) + giới hạn dung lượng + quét tên file + lưu ngoài webroot |
| Audit | Ghi mọi thao tác trên tiền, quyền, giá vào `audit_logs` |
| Bí mật | Không commit `.env`; gitleaks trong CI; biến môi trường ở production |
| Dữ liệu cá nhân | Tuân thủ NĐ 13/2023: consent, quyền xóa, tối thiểu hóa dữ liệu, không lưu ảnh sinh trắc thô |

---

## 7. Tổng hợp bảng công nghệ (để đưa vào báo cáo)

| Tầng | Công nghệ | Phiên bản | Vai trò |
|---|---|---|---|
| Backend framework | Spring Boot | 3.3.x | Nền tảng ứng dụng |
| Ngôn ngữ BE | Java | 21 LTS | |
| Build BE | Gradle (Kotlin DSL) | 8.x | Multi-module |
| ORM | Spring Data JPA + Hibernate | 6.x | |
| Migration | Flyway | 10.x | Phiên bản hóa schema |
| Bảo mật | Spring Security + JJWT | 6.x | JWT tự cài đặt |
| API doc | springdoc-openapi | 2.x | OpenAPI 3.1 + Swagger UI |
| Mapping | MapStruct | 1.6 | Entity ↔ DTO |
| CSDL | PostgreSQL | 16 | Nguồn sự thật |
| Cache/queue | Redis | 7 | |
| Object storage | MinIO (S3 API) | latest | |
| Web framework | React + Vite | 18 / 5 | |
| Ngôn ngữ FE | TypeScript | 5.x | |
| Server state | TanStack Query | 5.x | |
| UI | Tailwind CSS + shadcn/ui | 3.x | |
| Biểu đồ | Recharts | 2.x | |
| Mobile | React Native + Expo | 0.7x / SDK 51+ | |
| AI service | Python + FastAPI | 3.11 / 0.11x | Tùy chọn |
| LLM | Anthropic Claude (Haiku 4.5 / Sonnet 5) | API | Tùy chọn |
| Test BE | JUnit 5, Testcontainers, jqwik, REST Assured, ArchUnit | | |
| Test FE | Vitest, React Testing Library, Playwright | | |
| Test mobile | Jest, Maestro | | |
| Load test | k6 | | |
| CI/CD | GitHub Actions | | |
| Container | Docker + Docker Compose | | |
| Reverse proxy | Caddy | 2.x | TLS tự động |
