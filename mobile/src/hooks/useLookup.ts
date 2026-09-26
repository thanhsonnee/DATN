import { useQuery } from '@tanstack/react-query';
import { api } from '@/api/client';
import type { Trainer } from '@/api/types';

/** Danh sách huấn luyện viên đang nhận lịch — để chọn bằng tên. */
export function useTrainers() {
  return useQuery({
    queryKey: ['trainers'],
    queryFn: () => api.get<Trainer[]>('/employees/trainers'),
    staleTime: 5 * 60 * 1000,
  });
}

/** Huấn luyện viên còn rảnh trong khung giờ đã chọn. */
export function useAvailableTrainers(start: string | null, end: string | null) {
  return useQuery({
    queryKey: ['trainers', 'available', start, end],
    queryFn: () => api.get<Trainer[]>(
      `/pt-sessions/available-trainers?start=${encodeURIComponent(start!)}&end=${encodeURIComponent(end!)}`,
    ),
    enabled: !!start && !!end,
  });
}
