# Giao diện web — Hệ thống quản lý phòng gym

Giao diện cho **module A (danh tính)** và **module B (gói tập, hợp đồng)**.

## Chạy

```bash
npm install
npm run dev
```

Mở http://localhost:5173

Backend phải chạy sẵn ở cổng 8080. Vite tự chuyển tiếp mọi lời gọi `/api`
sang backend nên không cần cấu hình CORS.

## Màn hình

| Đường dẫn | Ai xem được | Nội dung |
|---|---|---|
| `/` | Mọi người | Bảng giá công khai, mua gói |
| `/dang-nhap`, `/dang-ky` | Khách chưa đăng nhập | Xác thực |
| `/goi-cua-toi` | Đã đăng nhập | Hợp đồng của tôi, xin bảo lưu |
| `/tai-khoan` | Đã đăng nhập | Thông tin cá nhân, đổi mật khẩu |
| `/quan-ly` | Nhân viên | Kích hoạt hợp đồng, duyệt bảo lưu, danh sách sắp hết hạn |

## Công nghệ

React 18 · TypeScript · Vite · TanStack Query · Zustand · Tailwind CSS

## Ghi chú

Kiểu dữ liệu trong `src/api/types.ts` hiện viết tay. Khi các module nghiệp vụ
ổn định sẽ sinh tự động từ OpenAPI (`/v3/api-docs`) để backend đổi trường thì
giao diện báo lỗi biên dịch ngay tại chỗ sai.
