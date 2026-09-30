# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

Đồ án tốt nghiệp: hệ thống quản lý phòng gym (6 vai trò: ADMIN, MEMBER, TRAINER,
SALE, RECEPTIONIST, ACCOUNTANT). Ba workspace độc lập, không phải monorepo build
chung: `backend/` (Spring Boot API), `web/` (React, dùng bởi Admin/Sale/Lễ
tân/Kế toán + trang công khai/hội viên), `mobile/` (Expo/React Native, hội viên
+ PT). Giao diện và code đều viết tiếng Việt (tên biến, route, trang).

`docs/*.md` là tài liệu thiết kế/định hướng đồ án (kiến trúc lý tưởng, kế hoạch
kiểm thử, đóng góp học thuật) — mô tả **ý định**, không phải lúc nào cũng khớp
100% với code hiện tại (vd. doc nói Gradle nhưng backend dùng Maven; doc có
module `finance`/`crm-sales`/`engagement` nhưng code đặt tên `billing`/`sales`/
`feedback`). Khi có mâu thuẫn, code là nguồn sự thật.

Cho câu hỏi về **kiến trúc/schema**, ưu tiên đọc `db/database_7_9_26.md` (đọc
thẳng từ các file migration đã áp dụng, mô tả **hiện trạng thật**) thay vì
`docs/*.md` (kế hoạch cũ, nhiều phần chưa triển khai). Đừng suy luận kiến trúc
chỉ từ `docs/*.md` — luôn đối chiếu với code/migration thật trước khi đưa lời
khuyên (vd. một dependency có trong `pom.xml`/`docker-compose.yml` không có
nghĩa là code thực sự dùng nó — `spring-boot-starter-data-redis` có khai báo
nhưng không có code nào dùng thật, `/actuator/health` đã tắt health check
Redis vì lý do này. Ngược lại, MinIO **có** dùng thật: `FileStorageService`
lưu/đọc/xóa ảnh chân dung hội viên qua `MinioClient` (giao thức S3), không ghi
đĩa cục bộ — cấu hình qua `app.storage.*`/biến môi trường `MINIO_*`. Dev local
dùng container MinIO của `docker-compose.yml` (volume `miniodata`, endpoint
`localhost:9000`). Trên Railway, service `gym-backend` có khai báo đủ 5 biến
`MINIO_ENDPOINT`/`MINIO_ACCESS_KEY`/`MINIO_SECRET_KEY`/`MINIO_BUCKET`/
`MINIO_VIRTUAL_STYLE` nhưng project Railway không có service MinIO nào —
nghĩa là endpoint đó trỏ ra một MinIO/S3 khác bên ngoài project. **Ảnh upload
ở local và ảnh upload trên bản deploy nằm ở hai nơi lưu trữ vật lý khác
nhau, hoàn toàn tách biệt** — đừng giả định ảnh test ở máy dev sẽ xuất hiện
trên production hay ngược lại).

## Commands

### Backend (`backend/`, Maven, Java 21, chạy trong thư mục này)
```
mvn spring-boot:run           # chạy API tại :8080 (cần postgres+redis, xem dưới)
mvn test                      # toàn bộ test
mvn test -Dtest=AuthApiTest   # 1 test class
mvn test -Dtest=AuthApiTest#login_thanhCong   # 1 test method
mvn compile                   # build nhanh, kiểm tra lỗi biên dịch
```
Hạ tầng dev: `docker compose up` ở gốc repo (postgres cổng **5434**, redis
6379, minio 9000/9001). Flyway tự chạy migration khi backend khởi động —
**không** để `ddl-auto: update`, luôn sửa schema qua migration mới trong
`backend/src/main/resources/db/migration/V{n}__ten.sql`.

### Web (`web/`)
```
npm run dev         # :5173, proxy /api sang backend :8080 (xem vite.config)
npm run build        # tsc -b && vite build
npm run typecheck    # tsc --noEmit
```

### Mobile (`mobile/`)
```
npm start            # expo start
npm run android / ios / web
```
**Trước khi sửa code mobile, đọc `mobile/AGENTS.md`**: Expo đã đổi nhiều so với
kiến thức cũ — phải tra docs bản đúng version (hiện SDK 57) tại
`docs.expo.dev/versions/v57.0.0/` trước khi viết code, không suy đoán API từ
trí nhớ.

## Kiến trúc backend

Modular monolith theo package: `admin`, `billing`, `checkin`, `common`,
`feedback`, `identity`, `membership`, `sales`, `settings`, `training` dưới
`backend/src/main/java/com/gym/`. Mỗi module thường có `api/` (Controller +
DTO), `domain/` (entity JPA), `repository/`, `service/`; một vài module có
thêm `job/` (Spring Scheduler).

- **identity**: người dùng, xác thực JWT tự cài (`JwtAuthFilter`,
  `JwtService`), `Person`/`Member`/`Employee`/`User`, `UserRole` enum (6 role
  cố định). API đăng ký công khai luôn gán cứng `MEMBER`, không nhận role từ
  client.
- **membership**: gói tập, hợp đồng (`Registration`), bảo lưu, giá.
- **billing**: hóa đơn, thanh toán, ca thu ngân (`CashShift`), chi phí
  (`Expense`), lương (`PayrollRun`/`PayrollItem`), ghi nhận doanh thu
  (`RevenueSchedule` — phân bổ doanh thu theo kỳ, tách "thu tiền" khỏi "doanh
  thu ghi nhận").
- **training**: buổi tập PT (`PtSession`) — chuyển `COMPLETED` cần xác nhận
  hai chiều (PT + hội viên), dùng để tính công PT và trừ buổi hội viên.
- **checkin**: check-in hội viên, kết quả/loại sự cố.
- **sales**: lead, pipeline.
- **feedback**: phản hồi hội viên, thiết bị.
- **settings**: cấu hình hệ thống (không hard-code các ngưỡng nghiệp vụ).
- **admin**: dashboard tổng quan, tạo/quản lý tài khoản nhân viên (PT/Sale/Lễ
  tân/Kế toán). Ngoại lệ so với mô tả chung ở trên: module này **không có**
  `domain/`/`repository/` riêng, dùng thẳng entity của `identity`
  (`Person`/`Employee`/`User`).
- **common**: `BaseEntity`, exception + `GlobalExceptionHandler` (định dạng
  lỗi thống nhất), `SecurityConfig`, seed dữ liệu demo
  (`DemoAccountSeeder`/`DemoDataSeeder`), lưu file.

Quy ước quan trọng (theo `application.yml` và code):
- Tiền tệ luôn `NUMERIC`/`BigDecimal`, không dùng `double`/`float`.
- `ddl-auto: validate` — Flyway migration là nguồn sự thật duy nhất của schema.
- Jackson giữ nguyên field `null` (`default-property-inclusion: always`) để
  frontend phân biệt được "không có trường" và "trường rỗng".
- Timezone cố định `Asia/Ho_Chi_Minh` cho cả JPA lẫn Jackson.
- Test đặt tên tiếng Việt mô tả kịch bản nghiệp vụ (vd.
  `SoCaiBuoiTapTest`, `XacNhanHaiChieuTest`, `ThanhToanVaCheckInTest`) —
  giữ quy ước này khi thêm test mới thay vì đặt tên kiểu `ServiceTest` chung
  chung. Đây là quy ước cho test **mới**: các test cũ hơn
  (`AuthApiTest`, `LeadApiTest`, `RegistrationApiTest`, `FinanceApiTest`) vẫn
  đặt tên kiểu `<Domain>ApiTest` từ trước khi quy ước này ra đời — không cần
  đổi tên lại, chỉ áp dụng cho test mới thêm.

## Kiến trúc web

React 18 + Vite + TypeScript, một ứng dụng duy nhất phân quyền theo route
(không tách app riêng cho từng role). Cấu trúc phẳng, không chia theo
"features/":
```
web/src/
├── api/        # client.ts (fetch wrapper, auto refresh token), types.ts, types-cde.ts
├── components/ # dùng chung + components/ui
├── hooks/      # TanStack Query hooks theo domain (useAdmin, useLookup, ...)
├── lib/        # format.ts, format-cde.ts (định dạng tiền/ngày/nhãn trạng thái)
├── pages/      # 1 file/route, tên tiếng Việt (BangGiaPage, ThuNganPage, ...)
└── stores/     # Zustand (auth)
```
- Route guard ở `App.tsx` bằng `canQuyen(element, vaiTro[])` — chỉ chặn ở
  giao diện cho gọn UX, **quyền thật luôn do backend quyết định**, đừng coi
  guard FE là biên bảo mật.
- `api/client.ts`: comment trong file ghi access token 15 phút, refresh token
  30 ngày — đây là mô tả hành vi backend (giá trị thật cấu hình ở
  `application.yml`: `access-token-ttl`/`refresh-token-ttl`), client.ts không
  tự khai hằng số TTL. Nhiều request 401 cùng lúc gộp chung **một** lần gọi
  `/auth/refresh` (biến `dangLamMoi`) để tránh cấp thừa token. `/auth/logout`
  bị loại khỏi luồng refresh/redirect để tránh đệ quy vô hạn (bug đã từng xảy
  ra thật) — xem comment trong file trước khi sửa.
- Kiểu dữ liệu (`types.ts`, `types-cde.ts`) hiện viết tay, chưa sinh tự động
  từ OpenAPI — khi backend đổi DTO phải tự sửa tay theo, chưa có compile-time
  check tự động như tài liệu thiết kế mô tả.

## Kiến trúc mobile

Expo + React Native + TypeScript, điều hướng rẽ nhánh theo role sau đăng
nhập. Cấu trúc: `src/api`, `src/components/ui`, `src/hooks`, `src/lib`,
`src/navigation`, `src/screens/{auth,member,trainer}`, `src/stores`. Dùng
`expo-secure-store` lưu token, TanStack Query cho server state, Zustand cho
client state — cùng pattern với web nhưng không share code trực tiếp (chưa có
`packages/shared-types` như tài liệu thiết kế đề xuất). Toàn bộ thư mục
`mobile/` hiện chưa có lịch sử commit (untracked) — đừng dùng `git log`/`git
blame` để tìm bối cảnh phát triển ở đây, code hiện tại là toàn bộ những gì có.

## Database

PostgreSQL 16, port host **5434** (5432/5433 đã bị PostgreSQL cài sẵn trên máy
chiếm). Schema quản lý bằng Flyway, file tại
`backend/src/main/resources/db/migration/V{n}__mo_ta.sql`, đánh số tuần tự,
không sửa migration đã chạy — thêm migration mới. `db/` ở gốc repo chỉ chứa
tài liệu tham chiếu/ghi chú thiết kế, không phải nguồn migration thật.

Trong `db/`, ưu tiên **`database_7_9_26.md`** khi cần biết schema hiện tại —
file này đọc trực tiếp từ các migration đã áp dụng (23 bảng đang chạy) và tự
đối chiếu với kế hoạch gốc để chỉ rõ phần nào **chưa triển khai** (lớp học
nhóm, bài tập & chỉ số cơ thể, RFID/nhận diện khuôn mặt ở check-in, v.v.).
`32_bang.md` chỉ là **kế hoạch gốc 32 bảng**, không phải hiện trạng — đừng
dùng nó một mình để trả lời câu hỏi về schema thật.

Lưu ý: file `database_7_9_26.md` là ảnh chụp tại một thời điểm (viết dựa trên
migration tới V19) nên có thể **lùi sau vài migration** so với thư mục
`db/migration/` thật (vd. tại thời điểm viết ghi chú này, migration mới nhất
đã là V21 nhưng chưa tạo bảng mới nên số "23 bảng" vẫn đúng, chỉ lệch vài chi
tiết cột/enum). Khi cần con số tuyệt đối mới nhất, đối chiếu thêm với
`ls backend/src/main/resources/db/migration/` thay vì tin tuyệt đối vào ngày
trong tên file.
