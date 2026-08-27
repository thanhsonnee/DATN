import { useQuery } from '@tanstack/react-query'
import { api } from '@/api/client'
import type { Membership } from '@/api/types'

/** Bảng giá công khai — không cần đăng nhập vẫn gọi được. */
export function useMemberships() {
  return useQuery({
    queryKey: ['memberships'],
    queryFn: () => api.get<Membership[]>('/memberships'),
    // Bảng giá rất ít thay đổi trong một phiên làm việc
    staleTime: 5 * 60 * 1000,
  })
}
