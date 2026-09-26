/**
 * URL gốc của backend, đọc từ biến môi trường EXPO_PUBLIC_API_BASE_URL
 * (định nghĩa trong `.env.local` — xem `.env.example`).
 *
 * KHÔNG dùng "localhost": trên điện thoại thật, localhost là chính điện
 * thoại đó — phải trỏ thẳng vào IP LAN của máy đang chạy backend Spring Boot.
 */
export const API_BASE_URL =
  process.env.EXPO_PUBLIC_API_BASE_URL ?? 'http://192.168.1.100:8080/api/v1';

if (__DEV__ && API_BASE_URL.includes('192.168.1.100')) {
  console.warn(
    '[config] Đang dùng API_BASE_URL mặc định (192.168.1.100) — tạo file ' +
    '.env.local từ .env.example và sửa đúng IP LAN máy chạy backend.'
  );
}
