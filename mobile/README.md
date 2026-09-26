# App Hội viên & PT — Fitness Center

App React Native (Expo) — một codebase, rẽ nhánh giao diện theo `user.role` sau khi đăng nhập
(**Hội viên** hoặc **Huấn luyện viên**). Dùng chung JWT auth với `web/`, gọi thẳng các API đã có ở
`backend/` — không thêm endpoint mới nào.

## Chạy thử (Expo Go, điện thoại thật)

1. Cài `Expo Go` từ Play Store / App Store trên điện thoại.
2. Đảm bảo **điện thoại và máy tính chạy backend cùng một mạng Wi-Fi**.
3. Xem IP LAN của máy tính: `ipconfig` (Windows) → lấy IPv4 của Wi-Fi đang dùng, ví dụ `192.168.1.23`.
4. Tạo file `.env.local` (copy từ `.env.example`), sửa đúng IP:
   ```
   EXPO_PUBLIC_API_BASE_URL=http://192.168.1.23:8080/api/v1
   ```
5. Đảm bảo backend đang chạy (`cd ../backend && mvn spring-boot:run`), lắng nghe trên `0.0.0.0` chứ
   không chỉ `localhost` (Spring Boot mặc định đã vậy).
6. Cài dependency (lần đầu) và chạy:
   ```
   npm install
   npm start
   ```
7. Quét mã QR hiện trong terminal bằng app Expo Go.

> **Nếu điện thoại không gọi được API dù đúng IP/Wi-Fi**: nhiều khả năng do Windows Firewall chặn
> kết nối đến cổng 8080 từ máy khác trong mạng LAN (mặc định Windows chặn theo "Private network"
> cho ứng dụng lạ như `java.exe`). Khi Windows hỏi lúc `mvn spring-boot:run` lần đầu, chọn cho
> phép ở **Private networks**. Nếu đã lỡ chặn, vào Windows Defender Firewall → Allowed apps → thêm
> `java.exe`.

### Tài khoản demo có sẵn (DemoAccountSeeder)
| Vai trò | SĐT | Mật khẩu |
|---|---|---|
| Hội viên (Nguyễn Văn An) | `0912345678` | `MatKhau@2026` |
| Huấn luyện viên (Trần Bình) | `0900000002` | `MatKhau@2026` |

Hội viên cũng có thể bấm "Đăng ký ngay" trong app để tạo tài khoản mới — PT thì không tự đăng ký
được (đúng thiết kế backend, chỉ Admin tạo qua web), phải dùng tài khoản demo hoặc tài khoản Admin
tạo sẵn.

## Cấu trúc

```
src/
  api/        client.ts (fetch + refresh token qua SecureStore), types.ts
  stores/     auth.ts (zustand — login/register/logout/restore)
  hooks/      react-query hooks theo từng nhóm nghiệp vụ
  components/ ui/ (Button, Card, Input, Alert, Badge, Modal, Screen…) + widget dùng chung
  screens/    auth/ (Login, Register)
              member/ (5 tab: Gói tập, Check-in, Buổi tập, Phản hồi, Tài khoản)
              trainer/ (Lịch dạy, Đánh giá — dùng chung tab Tài khoản với Member)
  navigation/ RootNavigator (rẽ nhánh Auth / MemberTabs / TrainerTabs theo user.role)
  lib/        format.ts (định dạng ngày/tiền/nhãn), theme.ts (màu, spacing)
```

### Lịch dạy của PT — khác web một chỗ có chủ đích

Web (`LichDayPage.tsx`) vẽ lịch tuần kiểu Google Calendar (lưới giờ, cuộn ngang). Trên điện thoại
~380px, lưới đó không có chỗ đứng — `LichDayScreen.tsx` thay bằng danh sách dạng agenda, chia 3
khối theo trạng thái (Chờ duyệt / Sắp tới / Lịch sử), cùng dữ liệu và cùng hành động (duyệt, từ
chối, đã dạy xong, hội viên vắng), chỉ khác cách trình bày cho hợp màn hình dọc.

## Đã cố tình để dành cho sau (không phải thiếu sót)

- **Push notification thật (remote push)**: Expo Go từ SDK 52+ không hỗ trợ remote push trên
  Android. Cần chuyển sang EAS Dev Client mới làm được — xem lại quyết định đã chốt trong lịch sử
  trao đổi trước khi code phần này.
- **Chọn ngày/giờ đặt lịch PT và xin bảo lưu**: đang dùng ô nhập text định dạng `YYYY-MM-DD` /
  `HH:MM` thay vì native date picker, để không phải thêm dependency native mới
  (`@react-native-community/datetimepicker`) ở v1. Nâng cấp sau nếu cần UX tốt hơn.
- **Giáo án tập luyện / chỉ số cơ thể (module G)**: backend chưa có package riêng cho việc này —
  không đưa vào app vì chưa có API để gọi.
