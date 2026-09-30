import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from '@/api/client'
import type { Membership, MembershipAdmin, MembershipSellStatus, MembershipUpsertInput } from '@/api/types'

/** Bảng giá công khai — không cần đăng nhập vẫn gọi được. */
export function useMemberships() {
  return useQuery({
    queryKey: ['memberships'],
    queryFn: () => api.get<Membership[]>('/memberships'),
    // Bảng giá rất ít thay đổi trong một phiên làm việc
    staleTime: 5 * 60 * 1000,
  })
}

/** Toàn bộ gói (kể cả ARCHIVED) — chỉ Admin dùng để quản lý. */
export function useMembershipsAdmin() {
  return useQuery({
    queryKey: ['memberships', 'admin'],
    queryFn: () => api.get<MembershipAdmin[]>('/memberships/admin'),
  })
}

function lamMoiSauKhiSua(qc: ReturnType<typeof useQueryClient>) {
  qc.invalidateQueries({ queryKey: ['memberships'] })
}

export function useCreateMembership() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (body: MembershipUpsertInput) => api.post<MembershipAdmin>('/memberships', body),
    onSuccess: () => lamMoiSauKhiSua(qc),
  })
}

export function useUpdateMembership() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: ({ id, status, ...body }: MembershipUpsertInput & { id: number; status: MembershipSellStatus }) =>
      api.put<MembershipAdmin>(`/memberships/${id}`, { ...body, status }),
    onSuccess: () => lamMoiSauKhiSua(qc),
  })
}

export function useDeleteMembership() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (id: number) => api.delete(`/memberships/${id}`),
    onSuccess: () => lamMoiSauKhiSua(qc),
  })
}
