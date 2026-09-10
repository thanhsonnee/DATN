import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from '@/api/client'
import { useAuth } from '@/stores/auth'
import type { Registration } from '@/api/types'

export function useMyRegistrations(enabled = true) {
  return useQuery({
    queryKey: ['registrations', 'me'],
    queryFn: () => api.get<Registration[]>('/registrations/me'),
    enabled,
  })
}

/** Hợp đồng đã chốt mua nhưng chưa thu tiền — để lễ tân biết cần xuất hóa đơn cho ai. */
export function usePendingPaymentRegistrations() {
  return useQuery({
    queryKey: ['registrations', 'pending-payment'],
    queryFn: () => api.get<Registration[]>('/registrations/pending-payment'),
    // Cần thấy hợp đồng mới ngay khi hội viên vừa chốt mua trên app
    refetchInterval: 15_000,
  })
}

/** Yêu cầu bảo lưu đang chờ duyệt — để nhân viên bấm thẳng, không cần biết mã số. */
export function usePendingFreezeRegistrations() {
  return useQuery({
    queryKey: ['registrations', 'pending-freeze'],
    queryFn: () => api.get<Registration[]>('/registrations/pending-freeze'),
    refetchInterval: 15_000,
  })
}

export function useExpiringRegistrations(days = 14) {
  return useQuery({
    queryKey: ['registrations', 'expiring', days],
    queryFn: () => api.get<Registration[]>(`/registrations/expiring?days=${days}`),
  })
}

export interface MuaGoiInput {
  membershipId: number
  discountAmount?: number
  discountReason?: string
  /** Hợp đồng đang gia hạn tiếp nối — gói mới sẽ bắt đầu ngay sau khi hợp đồng này hết hạn. */
  renewFromRegistrationId?: number
}

export interface DeskRegisterInput {
  fullName: string
  phone: string
  email?: string
  membershipId: number
  discountAmount?: number
  discountReason?: string
  assignedTrainerId?: number
  note?: string
  payNow: boolean
  paymentMethod?: string
}

export function useDeskRegister() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (input: DeskRegisterInput) => api.post<Registration>('/registrations/desk', input),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['registrations'] })
      qc.invalidateQueries({ queryKey: ['cash-shift'] })
      qc.invalidateQueries({ queryKey: ['invoices'] })
      qc.invalidateQueries({ queryKey: ['payments'] })
    },
  })
}

export function useMuaGoi() {
  const qc = useQueryClient()
  const refreshUser = useAuth((s) => s.refreshUser)

  return useMutation({
    mutationFn: (input: MuaGoiInput) => api.post<Registration>('/registrations', input),
    onSuccess: async () => {
      qc.invalidateQueries({ queryKey: ['registrations'] })
      // Mua gói lần đầu biến tài khoản thành hội viên — phải đọc lại hồ sơ,
      // nếu không giao diện vẫn hiện "chưa phải hội viên".
      await refreshUser()
    },
  })
}

export function useKichHoat() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (id: number) => api.post<Registration>(`/registrations/${id}/activate`),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['registrations'] }),
  })
}

export interface BaoLuuInput {
  id: number
  fromDate: string
  toDate: string
  reason: string
  reasonType: string
}

export function useXinBaoLuu() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: ({ id, ...body }: BaoLuuInput) =>
      api.post<Registration>(`/registrations/${id}/freeze`, body),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['registrations'] }),
  })
}

export function useDuyetBaoLuu() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: ({ id, approved, reason }: { id: number; approved: boolean; reason?: string }) =>
      api.post<Registration>(`/registrations/${id}/freeze/decision`, { approved, reason }),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['registrations'] }),
  })
}
