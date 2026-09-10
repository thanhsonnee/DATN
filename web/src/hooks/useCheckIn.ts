import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from '@/api/client'
import type { CheckInPreview, CheckInResult } from '@/api/types-cde'

export function useDangTrongPhong(enabled = true) {
  return useQuery({
    queryKey: ['check-ins', 'inside'],
    queryFn: () => api.get<CheckInResult[]>('/check-ins/inside'),
    enabled,
    // Màn hình quầy cần cập nhật liên tục để biết ai còn trong phòng
    refetchInterval: 30_000,
  })
}

export function useThongKeCheckIn(days = 30, enabled = true) {
  return useQuery({
    queryKey: ['check-ins', 'stats', days],
    queryFn: () => api.get<Record<string, number>>(`/check-ins/stats?days=${days}`),
    enabled,
  })
}

export function useLichSuCheckIn(memberId: number | null) {
  return useQuery({
    queryKey: ['check-ins', 'member', memberId],
    queryFn: () => api.get<CheckInResult[]>(`/check-ins/member/${memberId}`),
    enabled: memberId != null,
  })
}

/**
 * Xem trước tình trạng hội viên sau khi lễ tân chọn từ ô tìm kiếm — chưa ghi
 * lượt check-in nào. Cho lễ tân thấy đủ vào được không, còn mấy ngày, trước
 * khi bấm xác nhận, thay vì phải bấm mù rồi mới biết kết quả.
 */
export function useXemTruocCheckIn(memberId: number | null) {
  return useQuery({
    queryKey: ['check-ins', 'preview', memberId],
    queryFn: () => api.get<CheckInPreview>(`/check-ins/preview/${memberId}`),
    enabled: memberId != null,
  })
}

/** Hội viên bấm "Tôi đã đến phòng tập" trên app — chưa ghi lượt vào, chỉ vào hàng đợi chờ lễ tân. */
export function useGuiYeuCauTuCheckIn() {
  return useMutation({
    mutationFn: () => api.post<CheckInPreview>('/check-ins/self-request'),
  })
}

/** Hàng đợi cho màn hình quầy — cập nhật liên tục vì mang tính thời điểm, hội viên đang đứng chờ. */
export function useHangDoiChoXacNhan() {
  return useQuery({
    queryKey: ['check-ins', 'pending-requests'],
    queryFn: () => api.get<CheckInPreview[]>('/check-ins/pending-requests'),
    refetchInterval: 4_000,
  })
}

export function useBoQuaYeuCau() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (memberId: number) => api.delete<void>(`/check-ins/pending-requests/${memberId}`),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['check-ins', 'pending-requests'] }),
  })
}

export function useQuetVao() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: ({ memberId, override }: { memberId: number; override?: boolean }) =>
      api.post<CheckInResult>('/check-ins', { memberId, override }),
    // Prefix 'check-ins' khớp cả 'inside' lẫn 'pending-requests' — quét vào qua
    // đường tìm kiếm cũng tự dọn hàng đợi tự check-in của đúng người đó (xử lý ở backend).
    onSuccess: () => qc.invalidateQueries({ queryKey: ['check-ins'] }),
  })
}

export function useQuetRa() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (memberId: number) =>
      api.post<CheckInResult>(`/check-ins/${memberId}/check-out`),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['check-ins'] }),
  })
}
