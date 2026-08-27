import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from '@/api/client'
import type { PtSession, SoCai } from '@/api/types-cde'

export function useMyPtSessions() {
  return useQuery({
    queryKey: ['pt-sessions', 'me'],
    queryFn: () => api.get<PtSession[]>('/pt-sessions/me'),
  })
}

export function useTrainerSchedule(enabled = true) {
  return useQuery({
    queryKey: ['pt-sessions', 'trainer'],
    queryFn: () => api.get<PtSession[]>('/pt-sessions/trainer/me'),
    enabled,
  })
}

export function useSoCai(registrationId: number | null) {
  return useQuery({
    queryKey: ['so-cai', registrationId],
    queryFn: () => api.get<SoCai>(`/registrations/${registrationId}/credit-ledger`),
    enabled: registrationId != null,
  })
}

export interface DatLichInput {
  registrationId: number
  trainerId: number
  scheduledStart: string
  scheduledEnd: string
  roomName?: string
}

/** Làm mới mọi thứ liên quan sau khi buổi tập đổi trạng thái. */
function useLamMoi() {
  const qc = useQueryClient()
  return () => {
    qc.invalidateQueries({ queryKey: ['pt-sessions'] })
    // Sổ cái đổi theo khi buổi hoàn thành hoặc bị hủy
    qc.invalidateQueries({ queryKey: ['so-cai'] })
  }
}

export function useDatLich() {
  const lamMoi = useLamMoi()
  return useMutation({
    mutationFn: (input: DatLichInput) => api.post<PtSession>('/pt-sessions', input),
    onSuccess: lamMoi,
  })
}

export function useDuyetLich() {
  const lamMoi = useLamMoi()
  return useMutation({
    mutationFn: (id: number) => api.post<PtSession>(`/pt-sessions/${id}/approve`),
    onSuccess: lamMoi,
  })
}

export function useTuChoiLich() {
  const lamMoi = useLamMoi()
  return useMutation({
    mutationFn: ({ id, reason }: { id: number; reason: string }) =>
      api.post<PtSession>(`/pt-sessions/${id}/reject`, { reason }),
    onSuccess: lamMoi,
  })
}

/** Bước MỘT của xác nhận hai chiều — điều kiện khởi động cả quy trình tính công. */
export function useHlvXacNhan() {
  const lamMoi = useLamMoi()
  return useMutation({
    mutationFn: (id: number) => api.post<PtSession>(`/pt-sessions/${id}/trainer-confirm`),
    onSuccess: lamMoi,
  })
}

/** Bước HAI — đủ cả hai thì buổi hoàn thành và sổ cái trừ một buổi. */
export function useHoiVienXacNhan() {
  const lamMoi = useLamMoi()
  return useMutation({
    mutationFn: (id: number) => api.post<PtSession>(`/pt-sessions/${id}/member-confirm`),
    onSuccess: lamMoi,
  })
}

export function useHuyBuoi() {
  const lamMoi = useLamMoi()
  return useMutation({
    mutationFn: ({ id, cancelledBy, reason }: {
      id: number; cancelledBy: 'MEMBER' | 'TRAINER'; reason: string
    }) => api.post<PtSession>(`/pt-sessions/${id}/cancel`, { cancelledBy, reason }),
    onSuccess: lamMoi,
  })
}

export function useVangMat() {
  const lamMoi = useLamMoi()
  return useMutation({
    mutationFn: ({ id, noShowBy, note }: {
      id: number; noShowBy: 'MEMBER' | 'TRAINER'; note?: string
    }) => api.post<PtSession>(`/pt-sessions/${id}/no-show`, { noShowBy, note }),
    onSuccess: lamMoi,
  })
}
