import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from '@/api/client'
import type { Equipment, Feedback, FeedbackStatus, FeedbackType } from '@/api/types-cde'

export function useEquipmentList() {
  return useQuery({
    queryKey: ['equipment'],
    queryFn: () => api.get<Equipment[]>('/equipment'),
    staleTime: 5 * 60 * 1000,
  })
}

export interface CreateEquipmentInput {
  name: string
  roomName?: string
  note?: string
}

export function useCreateEquipment() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (input: CreateEquipmentInput) => api.post<Equipment>('/equipment', input),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['equipment'] }),
  })
}

export function useMyFeedback() {
  return useQuery({
    queryKey: ['feedbacks', 'me'],
    queryFn: () => api.get<Feedback[]>('/feedbacks/me'),
  })
}

export function useFeedbackAboutMe() {
  return useQuery({
    queryKey: ['feedbacks', 'trainer'],
    queryFn: () => api.get<Feedback[]>('/feedbacks/trainer/me'),
  })
}

export function useFeedbackQueue(status?: FeedbackStatus, type?: FeedbackType) {
  const params = new URLSearchParams()
  if (status) params.set('status', status)
  if (type) params.set('type', type)
  const qs = params.toString()

  return useQuery({
    queryKey: ['feedbacks', 'queue', status ?? null, type ?? null],
    queryFn: () => api.get<Feedback[]>(`/feedbacks${qs ? `?${qs}` : ''}`),
  })
}

export interface CreateFeedbackInput {
  feedbackType: FeedbackType
  trainerId?: number
  equipmentId?: number
  rating?: number
  description: string
}

function useLamMoi() {
  const qc = useQueryClient()
  return () => {
    qc.invalidateQueries({ queryKey: ['feedbacks'] })
    qc.invalidateQueries({ queryKey: ['equipment'] })
  }
}

export function useSubmitFeedback() {
  const lamMoi = useLamMoi()
  return useMutation({
    mutationFn: (input: CreateFeedbackInput) => api.post<Feedback>('/feedbacks', input),
    onSuccess: lamMoi,
  })
}

export interface UpdateFeedbackInput {
  id: number
  description: string
  rating?: number
}

export function useUpdateFeedback() {
  const lamMoi = useLamMoi()
  return useMutation({
    mutationFn: ({ id, ...body }: UpdateFeedbackInput) => api.put<Feedback>(`/feedbacks/${id}`, body),
    onSuccess: lamMoi,
  })
}

export interface UpdateFeedbackStatusInput {
  id: number
  status: FeedbackStatus
  resolutionNote?: string
  repairCost?: number
}

export function useUpdateFeedbackStatus() {
  const lamMoi = useLamMoi()
  return useMutation({
    mutationFn: ({ id, ...body }: UpdateFeedbackStatusInput) =>
      api.put<Feedback>(`/feedbacks/${id}/status`, body),
    onSuccess: lamMoi,
  })
}
