import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from '@/api/client';
import type { Equipment, Feedback, FeedbackType } from '@/api/types';

export function useEquipmentList() {
  return useQuery({
    queryKey: ['equipment'],
    queryFn: () => api.get<Equipment[]>('/equipment'),
    staleTime: 5 * 60 * 1000,
  });
}

export function useMyFeedback() {
  return useQuery({
    queryKey: ['feedbacks', 'me'],
    queryFn: () => api.get<Feedback[]>('/feedbacks/me'),
  });
}

/** Huấn luyện viên xem đánh giá hội viên gửi về mình. */
export function useFeedbackAboutMe() {
  return useQuery({
    queryKey: ['feedbacks', 'trainer'],
    queryFn: () => api.get<Feedback[]>('/feedbacks/trainer/me'),
  });
}

export interface CreateFeedbackInput {
  feedbackType: FeedbackType;
  trainerId?: number;
  equipmentId?: number;
  rating?: number;
  description: string;
}

function useLamMoi() {
  const qc = useQueryClient();
  return () => {
    qc.invalidateQueries({ queryKey: ['feedbacks'] });
    qc.invalidateQueries({ queryKey: ['equipment'] });
  };
}

export function useSubmitFeedback() {
  const lamMoi = useLamMoi();
  return useMutation({
    mutationFn: (input: CreateFeedbackInput) => api.post<Feedback>('/feedbacks', input),
    onSuccess: lamMoi,
  });
}

export interface UpdateFeedbackInput {
  id: number;
  description: string;
  rating?: number;
}

export function useUpdateFeedback() {
  const lamMoi = useLamMoi();
  return useMutation({
    mutationFn: ({ id, ...body }: UpdateFeedbackInput) => api.put<Feedback>(`/feedbacks/${id}`, body),
    onSuccess: lamMoi,
  });
}
