import type { Registration } from '@/api/types'

/** Định dạng tiền Việt: 3780000 → "3.780.000 đ" */
export function tien(value: number | string): string {
  const n = typeof value === 'string' ? Number(value) : value
  return new Intl.NumberFormat('vi-VN').format(n) + ' đ'
}

/** Định dạng ngày: "2026-08-21" → "21/08/2026" */
export function ngay(iso: string | null | undefined): string {
  if (!iso) return '—'
  const [y, m, d] = iso.slice(0, 10).split('-')
  return `${d}/${m}/${y}`
}

/**
 * Lùi lại N ngày từ một mốc ISO — dùng để suy ra ngày hết hạn CŨ từ ngày hết
 * hạn mới + số ngày bảo lưu. Đọc/ghi bằng getter local (getFullYear/getMonth/
 * getDate), KHÔNG dùng toISOString() — hàm đó quy đổi sang UTC nên ở múi giờ
 * +7 sẽ lùi lố thêm 1 ngày (00:00 giờ VN = 17:00 hôm trước theo UTC).
 */
export function truNgay(iso: string, soNgay: number): string {
  const d = new Date(iso + 'T00:00:00')
  d.setDate(d.getDate() - soNgay)
  const y = d.getFullYear()
  const m = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  return `${y}-${m}-${day}`
}

/** Số ngày còn lại tính tới ngày hết hạn. Âm nghĩa là đã quá hạn. */
export function soNgayConLai(endDate: string | null): number | null {
  if (!endDate) return null
  const end = new Date(endDate + 'T00:00:00')
  const today = new Date()
  today.setHours(0, 0, 0, 0)
  return Math.round((end.getTime() - today.getTime()) / 86_400_000)
}

export function tenLoaiGoi(type: string): string {
  const map: Record<string, string> = {
    TIME_BASED: 'Theo thời hạn',
    SESSION_BASED: 'Theo số buổi',
    HYBRID: 'Kết hợp',
    DAY_PASS: 'Vé lẻ',
  }
  return map[type] ?? type
}

export function tenVaiTro(role: string): string {
  const map: Record<string, string> = {
    ADMIN: 'Quản trị viên',
    MEMBER: 'Hội viên',
    TRAINER: 'Huấn luyện viên',
    SALE: 'Nhân viên kinh doanh',
    RECEPTIONIST: 'Lễ tân',
    ACCOUNTANT: 'Kế toán',
  }
  return map[role] ?? role
}

export function tenTrangThaiHopDong(status: string): string {
  const map: Record<string, string> = {
    PENDING_PAYMENT: 'Chờ thanh toán',
    ACTIVE: 'Đang hiệu lực',
    FROZEN: 'Đang bảo lưu',
    COMPLETED: 'Đã kết thúc',
    CANCELLED: 'Đã hủy',
    REFUNDED: 'Đã hoàn tiền',
  }
  return map[status] ?? status
}

/**
 * Ghi chú hiển thị sau khi mua gói, khi hợp đồng vừa tạo được hệ thống TỰ ĐỘNG
 * nối tiếp vào một gói cùng loại khách đang có (tự động gia hạn, tránh chồng
 * ngày) — người mua/nhân viên bán hàng cần thấy ngay, không phải tự suy luận
 * từ ngày hết hạn. Rỗng nếu đây là gói độc lập, không nối vào đâu cả.
 */
export function ghiChuNoiGoiTuDong(hopDong: Registration): string {
  if (hopDong.renewFromRegistrationId == null) return ''
  return hopDong.renewFromEndDate != null
    ? ` Đang có gói cùng loại còn hạn (kết thúc ${ngay(hopDong.renewFromEndDate)})` +
      ` nên gói này sẽ tự động bắt đầu ngay sau khi gói đó kết thúc, không bị chồng ngày hay mất ngày.`
    : ` Gói này sẽ tự động nối tiếp ngay sau gói cùng loại đã mua trước đó` +
      ` (hợp đồng ${hopDong.renewFromRegistrationCode}), không bị chồng ngày.`
}

export function tenTrangThaiBaoLuu(status: string): string {
  const map: Record<string, string> = {
    PENDING: 'Chờ duyệt',
    APPROVED: 'Đã duyệt',
    REJECTED: 'Bị từ chối',
    ACTIVE: 'Đang bảo lưu',
    ENDED: 'Đã kết thúc',
    CANCELLED: 'Đã hủy',
  }
  return map[status] ?? status
}
