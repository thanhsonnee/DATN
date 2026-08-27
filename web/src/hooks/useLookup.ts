import { useQuery } from '@tanstack/react-query'
import { api } from '@/api/client'
import type { MemberSearchResult, Trainer } from '@/api/types-cde'

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
