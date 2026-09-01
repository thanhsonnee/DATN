import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from '@/api/client'
import type { CashShift, Invoice, Payment, PaymentMethod } from '@/api/types-cde'
import type { Registration } from '@/api/types'

export function useCongNo() {
  return useQuery({
    queryKey: ['invoices', 'unpaid'],
    queryFn: () => api.get<Invoice[]>('/billing/invoices/unpaid'),
  })
}

export function useHoaDon(id: number | null) {
  return useQuery({
    queryKey: ['invoices', id],
    queryFn: () => api.get<Invoice>(`/billing/invoices/${id}`),
    enabled: id != null,
  })
}

export function useKhoanThu(invoiceId: number | null) {
  return useQuery({
    queryKey: ['payments', invoiceId],
    queryFn: () => api.get<Payment[]>(`/billing/invoices/${invoiceId}/payments`),
    enabled: invoiceId != null,
  })
}

/** Ca đang mở của người đang đăng nhập. Trả về null nếu chưa mở ca nào. */
export function useCaHienTai(enabled = true) {
  return useQuery({
    queryKey: ['cash-shift', 'current'],
    queryFn: () => api.get<CashShift | null>('/billing/cash-shifts/current'),
    enabled,
  })
}

function useLamMoiBilling() {
  const qc = useQueryClient()
  return () => {
    qc.invalidateQueries({ queryKey: ['invoices'] })
    qc.invalidateQueries({ queryKey: ['payments'] })
    qc.invalidateQueries({ queryKey: ['cash-shift'] })
    qc.refetchQueries({ queryKey: ['cash-shift', 'current'] })
    // Thu đủ tiền thì hợp đồng kích hoạt và sổ cái được cấp buổi
    qc.invalidateQueries({ queryKey: ['registrations'] })
    qc.invalidateQueries({ queryKey: ['registrations', 'pending-payment'] })
    qc.invalidateQueries({ queryKey: ['so-cai'] })
  }
}

/**
 * Một lần bấm cho lễ tân: xác nhận đã thu tiền (mặt hoặc chuyển khoản), hợp
 * đồng TỰ kích hoạt. Gộp xuất hóa đơn + thu tiền phía sau, lễ tân không cần
 * biết tới khái niệm "hóa đơn".
 */
export function useXacNhanGoiTap() {
  const lamMoi = useLamMoiBilling()
  return useMutation({
    mutationFn: ({ registrationId, method }: { registrationId: number; method: PaymentMethod }) =>
      api.post<Registration>(`/billing/registrations/${registrationId}/confirm`, { method }),
    onSuccess: lamMoi,
  })
}

export function useXuatHoaDon() {
  const lamMoi = useLamMoiBilling()
  return useMutation({
    mutationFn: (registrationId: number) =>
      api.post<Invoice>('/billing/invoices', { registrationId }),
    onSuccess: lamMoi,
  })
}

export function useThuTien() {
  const lamMoi = useLamMoiBilling()
  return useMutation({
    mutationFn: (input: { invoiceId: number; amount: number; method: PaymentMethod }) =>
      api.post<Payment>('/billing/payments', input),
    onSuccess: lamMoi,
  })
}

export function useHoanTien() {
  const lamMoi = useLamMoiBilling()
  return useMutation({
    mutationFn: ({ paymentId, amount, reason }: {
      paymentId: number; amount: number; reason: string
    }) => api.post<Payment>(`/billing/payments/${paymentId}/refund`, { amount, reason }),
    onSuccess: lamMoi,
  })
}

export function useMoCa() {
  const lamMoi = useLamMoiBilling()
  return useMutation({
    mutationFn: (openingBalance: number) =>
      api.post<CashShift>('/billing/cash-shifts/open', { openingBalance }),
    onSuccess: lamMoi,
  })
}

export function useDongCa() {
  const lamMoi = useLamMoiBilling()
  return useMutation({
    mutationFn: ({ countedCash, reason }: { countedCash: number; reason?: string }) =>
      api.post<CashShift>('/billing/cash-shifts/close', { countedCash, reason }),
    onSuccess: lamMoi,
  })
}
