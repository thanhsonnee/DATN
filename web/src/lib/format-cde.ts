/** Định dạng riêng cho module buổi tập, thanh toán, check-in. */

export function gioPhut(iso: string | null): string {
  if (!iso) return '—'
  const d = new Date(iso)
  return d.toLocaleTimeString('vi-VN', { hour: '2-digit', minute: '2-digit' })
}

export function ngayGio(iso: string | null): string {
  if (!iso) return '—'
  const d = new Date(iso)
  return d.toLocaleString('vi-VN', {
    day: '2-digit', month: '2-digit', year: 'numeric',
    hour: '2-digit', minute: '2-digit',
  })
}

export function tenTrangThaiBuoiTap(status: string): string {
  const map: Record<string, string> = {
    PENDING_TRAINER: 'Chờ huấn luyện viên duyệt',
    REJECTED: 'Bị từ chối',
    SCHEDULED: 'Đã lên lịch',
    COMPLETED: 'Đã hoàn thành',
    NO_SHOW: 'Vắng mặt',
    CANCELLED: 'Đã hủy',
  }
  return map[status] ?? status
}

export function tenLoaiBuoiTap(type: string): string {
  const map: Record<string, string> = {
    PAID_PT: 'Buổi có trả phí',
    COMPLIMENTARY: 'Hỗ trợ miễn phí',
    TRIAL: 'Tập thử',
    ORIENTATION: 'Hướng dẫn làm quen',
    MAKEUP: 'Tập bù',
    ASSESSMENT: 'Đo chỉ số',
  }
  return map[type] ?? type
}

export function tenButToan(type: string): string {
  const map: Record<string, string> = {
    GRANT: 'Cấp buổi',
    CONSUME: 'Trừ buổi',
    REFUND: 'Hoàn buổi',
    EXPIRE: 'Hết hạn thu hồi',
    ADJUST: 'Điều chỉnh tay',
  }
  return map[type] ?? type
}

export function tenNguonButToan(type: string): string {
  const map: Record<string, string> = {
    REGISTRATION: 'Từ hợp đồng',
    PT_SESSION: 'Từ buổi tập',
    MANUAL: 'Nhập tay',
    EXPIRY_JOB: 'Job hết hạn',
  }
  return map[type] ?? type
}

export function tenTrangThaiHoaDon(status: string): string {
  const map: Record<string, string> = {
    UNPAID: 'Chưa thanh toán',
    PARTIALLY_PAID: 'Thanh toán một phần',
    PAID: 'Đã thanh toán',
    OVERDUE: 'Quá hạn',
    CANCELLED: 'Đã hủy',
    REFUNDED: 'Đã hoàn tiền',
  }
  return map[status] ?? status
}

export function tenHinhThucThanhToan(method: string): string {
  const map: Record<string, string> = {
    CASH: 'Tiền mặt',
    BANK_TRANSFER: 'Chuyển khoản',
    VIETQR: 'Quét mã VietQR',
    CARD_POS: 'Quẹt thẻ',
    E_WALLET: 'Ví điện tử',
    GATEWAY: 'Cổng thanh toán',
  }
  return map[method] ?? method
}

export function tenKetQuaCheckIn(result: string): string {
  const map: Record<string, string> = {
    ALLOWED: 'Cho vào',
    ALLOWED_OVERRIDE: 'Cho vào dù còn nợ',
    DENIED_EXPIRED: 'Chặn — hết hạn',
    DENIED_FROZEN: 'Chặn — đang bảo lưu',
    DENIED_UNPAID: 'Chặn — chưa thanh toán/kích hoạt',
    DENIED_NOT_FOUND: 'Chặn — không tìm thấy',
    DENIED_SUSPECT: 'Chặn — nghi vấn',
  }
  return map[result] ?? result
}

export function tenLoaiPhanHoi(type: string): string {
  const map: Record<string, string> = {
    TRAINER: 'Đánh giá huấn luyện viên',
    FACILITY: 'Báo hỏng thiết bị',
    HYGIENE: 'Vệ sinh',
    SERVICE: 'Chất lượng dịch vụ',
    GENERAL: 'Góp ý chung',
  }
  return map[type] ?? type
}

export function tenTrangThaiPhanHoi(status: string): string {
  const map: Record<string, string> = {
    OPEN: 'Mới gửi',
    IN_PROGRESS: 'Đang xử lý',
    WAITING_PARTS: 'Chờ linh kiện',
    RESOLVED: 'Đã xử lý xong',
    CLOSED: 'Đã đóng',
  }
  return map[status] ?? status
}

export function tenTrangThaiThietBi(status: string): string {
  const map: Record<string, string> = {
    ACTIVE: 'Hoạt động tốt',
    NEEDS_REPAIR: 'Cần sửa',
    UNDER_REPAIR: 'Đang sửa',
    RETIRED: 'Ngừng sử dụng',
  }
  return map[status] ?? status
}

export function tenSuCo(type: string | null): string | null {
  if (!type) return null
  const map: Record<string, string> = {
    ANTI_PASSBACK: 'Quét lại quá nhanh',
    SUSPECTED_SHARING: 'Nghi dùng chung tài khoản',
    REPLAY_ATTEMPT: 'Dùng lại mã cũ',
    EXPIRED_ATTEMPT: 'Cố vào khi đã hết hạn',
  }
  return map[type] ?? type
}
