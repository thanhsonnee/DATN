import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api, getAccessToken } from '@/api/client'
import type { MemberPhotoResponse, MemberSearchResult, Trainer } from '@/api/types-cde'

/**
 * Tìm hội viên theo tên/số điện thoại/mã hội viên — gõ tới đâu ra tới đó.
 *
 * Chỉ gọi API khi đã gõ đủ 2 ký tự, để không bắn request liên tục vô ích khi
 * người dùng vừa mới gõ chữ đầu tiên.
 */
export function useTimKiemHoiVien(tuKhoa: string) {
  return useQuery({
    queryKey: ['members', 'search', tuKhoa],
    queryFn: () => api.get<MemberSearchResult[]>(`/members/search?q=${encodeURIComponent(tuKhoa)}`),
    enabled: tuKhoa.trim().length >= 2,
  })
}

/** Danh sách huấn luyện viên đang nhận lịch — để chọn bằng tên, không gõ mã số. */
export function useTrainers() {
  return useQuery({
    queryKey: ['trainers'],
    queryFn: () => api.get<Trainer[]>('/employees/trainers'),
    staleTime: 5 * 60 * 1000,
  })
}

/** Tải lên ảnh chân dung khuôn mặt cho hội viên (Lễ tân/Admin). */
export function useUploadMemberPhoto() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: async ({ memberId, file }: { memberId: number; file: File }) => {
      const formData = new FormData()
      formData.append('file', file)
      const token = getAccessToken()
      const headers: Record<string, string> = {}
      if (token) headers['Authorization'] = `Bearer ${token}`

      const res = await fetch(`/api/v1/members/${memberId}/photo`, {
        method: 'POST',
        headers,
        body: formData,
      })

      if (!res.ok) {
        const err = await res.json().catch(() => ({}))
        throw new Error(err.message || 'Tải ảnh thất bại')
      }
      return res.json() as Promise<MemberPhotoResponse>
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['check-ins', 'preview'] })
      qc.invalidateQueries({ queryKey: ['members', 'search'] })
    },
  })
}

/** Xóa ảnh chân dung hiện tại của hội viên — dùng khi lỡ tải nhầm ảnh. */
export function useDeleteMemberPhoto() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: async (memberId: number) => {
      const token = getAccessToken()
      const headers: Record<string, string> = {}
      if (token) headers['Authorization'] = `Bearer ${token}`

      const res = await fetch(`/api/v1/members/${memberId}/photo`, {
        method: 'DELETE',
        headers,
      })

      if (!res.ok) {
        const err = await res.json().catch(() => ({}))
        throw new Error(err.message || 'Xóa ảnh thất bại')
      }
      return res.json() as Promise<MemberPhotoResponse>
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['check-ins', 'preview'] })
      qc.invalidateQueries({ queryKey: ['members', 'search'] })
    },
  })
}
