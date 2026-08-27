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
