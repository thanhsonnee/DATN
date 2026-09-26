/** Định dạng dùng chung cho màn hình — chép lại đúng cách tính từ `web/src/lib/format.ts`. */

export function tien(giaTri: number | string) {
  return new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND' }).format(Number(giaTri));
}

export function ngay(iso: string | null | undefined) {
  if (!iso) return '—';
  return new Date(iso).toLocaleDateString('vi-VN');
}

export function ngayGio(iso: string | null | undefined) {
  if (!iso) return '—';
  return new Date(iso).toLocaleString('vi-VN');
}

export function gioPhut(iso: string | null | undefined) {
  if (!iso) return '—';
  return new Date(iso).toLocaleTimeString('vi-VN', { hour: '2-digit', minute: '2-digit' });
}

export function soNgayConLai(endDate: string | null): number | null {
  if (!endDate) return null;
  const hetHan = new Date(endDate);
  const homNay = new Date();
  hetHan.setHours(0, 0, 0, 0);
  homNay.setHours(0, 0, 0, 0);
  return Math.round((hetHan.getTime() - homNay.getTime()) / 86_400_000);
}

export function truNgay(iso: string, soNgay: number) {
  const d = new Date(iso);
  d.setDate(d.getDate() - soNgay);
  return d.toISOString().slice(0, 10);
}

/** Chuỗi "YYYY-MM-DD" theo giờ địa phương của máy — KHÔNG dùng toISOString() vì nó quy về UTC. */
export function ngayIsoDiaPhuong(d: Date): string {
  const nam = d.getFullYear();
  const thang = String(d.getMonth() + 1).padStart(2, '0');
  const ngay = String(d.getDate()).padStart(2, '0');
  return `${nam}-${thang}-${ngay}`;
}

const TEN_LOAI_GOI: Record<string, string> = {
  TIME_BASED: 'Theo thời hạn',
  SESSION_BASED: 'Theo số buổi',
  HYBRID: 'Kết hợp',
  DAY_PASS: 'Vé lẻ',
};
export const tenLoaiGoi = (t: string) => TEN_LOAI_GOI[t] ?? t;

const TEN_TRANG_THAI_HD: Record<string, string> = {
  PENDING_PAYMENT: 'Chờ thanh toán',
  ACTIVE: 'Đang hoạt động',
  FROZEN: 'Đang bảo lưu',
  COMPLETED: 'Đã hoàn thành',
  CANCELLED: 'Đã hủy',
  REFUNDED: 'Đã hoàn tiền',
};
export const tenTrangThaiHopDong = (t: string) => TEN_TRANG_THAI_HD[t] ?? t;

const TEN_TRANG_THAI_BAO_LUU: Record<string, string> = {
  PENDING: 'Chờ duyệt',
  APPROVED: 'Đã duyệt',
  REJECTED: 'Bị từ chối',
  ACTIVE: 'Đang bảo lưu',
  ENDED: 'Đã kết thúc',
  CANCELLED: 'Đã hủy',
};
export const tenTrangThaiBaoLuu = (t: string) => TEN_TRANG_THAI_BAO_LUU[t] ?? t;

const TEN_TRANG_THAI_BUOI: Record<string, string> = {
  PENDING_TRAINER: 'Chờ huấn luyện viên duyệt',
  REJECTED: 'Bị từ chối',
  SCHEDULED: 'Đã lên lịch',
  COMPLETED: 'Đã hoàn thành',
  NO_SHOW: 'Vắng mặt',
  CANCELLED: 'Đã hủy',
};
export const tenTrangThaiBuoi = (t: string) => TEN_TRANG_THAI_BUOI[t] ?? t;

const TEN_LOAI_BUOI_TAP: Record<string, string> = {
  PAID_PT: 'Buổi có trả phí',
  COMPLIMENTARY: 'Hỗ trợ miễn phí',
  TRIAL: 'Tập thử',
  ORIENTATION: 'Hướng dẫn làm quen',
  MAKEUP: 'Tập bù',
  ASSESSMENT: 'Đo chỉ số',
};
export const tenLoaiBuoiTap = (t: string) => TEN_LOAI_BUOI_TAP[t] ?? t;

const TEN_BUT_TOAN: Record<string, string> = {
  GRANT: 'Cấp buổi',
  CONSUME: 'Trừ buổi',
  REFUND: 'Hoàn buổi',
  EXPIRE: 'Hết hạn thu hồi',
  ADJUST: 'Điều chỉnh tay',
};
export const tenButToan = (t: string) => TEN_BUT_TOAN[t] ?? t;

const TEN_NGUON_BUT_TOAN: Record<string, string> = {
  REGISTRATION: 'Từ hợp đồng',
  PT_SESSION: 'Từ buổi tập',
  MANUAL: 'Nhập tay',
  EXPIRY_JOB: 'Job hết hạn',
};
export const tenNguonButToan = (t: string) => TEN_NGUON_BUT_TOAN[t] ?? t;

const TEN_LOAI_PHAN_HOI: Record<string, string> = {
  TRAINER: 'Đánh giá huấn luyện viên',
  FACILITY: 'Báo hỏng thiết bị',
  HYGIENE: 'Vệ sinh',
  SERVICE: 'Chất lượng dịch vụ',
  GENERAL: 'Góp ý chung',
};
export const tenLoaiPhanHoi = (t: string) => TEN_LOAI_PHAN_HOI[t] ?? t;

const TEN_TRANG_THAI_PHAN_HOI: Record<string, string> = {
  OPEN: 'Đang chờ xử lý',
  IN_PROGRESS: 'Đang xử lý',
  WAITING_PARTS: 'Chờ linh kiện',
  RESOLVED: 'Đã xử lý',
  CLOSED: 'Đã đóng',
};
export const tenTrangThaiPhanHoi = (t: string) => TEN_TRANG_THAI_PHAN_HOI[t] ?? t;

const TEN_VAI_TRO: Record<string, string> = {
  ADMIN: 'Quản trị viên',
  MEMBER: 'Hội viên',
  TRAINER: 'Huấn luyện viên',
  SALE: 'Nhân viên kinh doanh',
  RECEPTIONIST: 'Lễ tân',
  ACCOUNTANT: 'Kế toán',
};
export const tenVaiTro = (t: string) => TEN_VAI_TRO[t] ?? t;
