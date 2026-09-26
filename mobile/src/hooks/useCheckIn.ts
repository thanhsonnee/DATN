import { useMutation, useQuery } from '@tanstack/react-query';
import { api } from '@/api/client';
import type { CheckInPreview, CheckInSelfStatus } from '@/api/types';

/** Hội viên bấm "Tôi đã đến phòng tập" — chưa ghi lượt vào, chỉ vào hàng đợi chờ lễ tân. */
export function useGuiYeuCauTuCheckIn() {
  return useMutation({
    mutationFn: () => api.post<CheckInPreview>('/check-ins/self-request'),
  });
}

/**
 * Poll trạng thái yêu cầu tự check-in sau khi đã gửi — để tự hiện thông báo
 * ngay khi lễ tân xác nhận, không cần hội viên tự bấm làm mới.
 */
export function useCheckInSelfStatus(enabled: boolean) {
  return useQuery({
    queryKey: ['check-ins', 'self-status'],
    queryFn: () => api.get<CheckInSelfStatus>('/check-ins/self-status'),
    enabled,
    refetchInterval: 3_000,
  });
}
