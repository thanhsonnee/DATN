import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from '@/api/client';
import { useAuth } from '@/stores/auth';
import type { Registration } from '@/api/types';

export function useMyRegistrations() {
  return useQuery({
    queryKey: ['registrations', 'me'],
    queryFn: () => api.get<Registration[]>('/registrations/me'),
  });
}

export interface MuaGoiInput {
  membershipId: number;
  discountAmount?: number;
  discountReason?: string;
  /** Hợp đồng đang gia hạn tiếp nối — gói mới sẽ tự động bắt đầu ngay sau khi hợp đồng này hết hạn. */
  renewFromRegistrationId?: number;
}

export function useMuaGoi() {
  const qc = useQueryClient();
  const refreshUser = useAuth((s) => s.refreshUser);

  return useMutation({
    mutationFn: (input: MuaGoiInput) => api.post<Registration>('/registrations', input),
    onSuccess: async () => {
      qc.invalidateQueries({ queryKey: ['registrations'] });
      // Mua gói lần đầu biến tài khoản thành hội viên — phải đọc lại hồ sơ,
      // nếu không giao diện vẫn hiện "chưa phải hội viên".
      await refreshUser();
    },
  });
}

export interface BaoLuuInput {
  id: number;
  fromDate: string;
  toDate: string;
  reason: string;
  reasonType: string;
}

export function useXinBaoLuu() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ id, ...body }: BaoLuuInput) =>
      api.post<Registration>(`/registrations/${id}/freeze`, body),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['registrations'] }),
  });
}
