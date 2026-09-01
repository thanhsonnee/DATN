import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from '@/api/client'
import type {
  Lead,
  LeadSource,
  LeadStage,
  LostReason,
  AppUserLead,
  FunnelStats,
} from '@/api/types-cde'

export function useLeads(stage?: LeadStage, assignedTo?: number) {
  const params = new URLSearchParams()
  if (stage) params.set('stage', stage)
  if (assignedTo) params.set('assignedTo', String(assignedTo))
  const qs = params.toString() ? `?${params.toString()}` : ''

  return useQuery({
    queryKey: ['leads', stage, assignedTo],
    queryFn: () => api.get<Lead[]>(`/leads${qs}`),
  })
}

export function useLeadDetail(id: number | null) {
  return useQuery({
    queryKey: ['leads', id],
    queryFn: () => api.get<Lead>(`/leads/${id}`),
    enabled: !!id,
  })
}

export function useCreateLead() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (body: {
      fullName: string
      phone: string
      email?: string
      source: LeadSource
      interestedMembershipId?: number
      assignedToEmployeeId?: number
      note?: string
      nextFollowUp?: string
    }) => api.post<Lead>('/leads', body),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['leads'] })
      qc.invalidateQueries({ queryKey: ['funnel-stats'] })
    },
  })
}

export function usePublicCreateLead() {
  return useMutation({
    mutationFn: (body: {
      fullName: string
      phone: string
      email?: string
      interestedMembershipId?: number
      note?: string
    }) => api.post<Lead>('/leads/public', body),
  })
}

export function useUpdateLeadContact() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: ({
      leadId,
      ...body
    }: {
      leadId: number
      stage: LeadStage
      contactNote?: string
      nextFollowUp?: string
      interestedMembershipId?: number
    }) => api.put<Lead>(`/leads/${leadId}/contact`, body),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['leads'] })
      qc.invalidateQueries({ queryKey: ['funnel-stats'] })
    },
  })
}

export function useMarkLost() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: ({
      leadId,
      ...body
    }: {
      leadId: number
      lostReason: LostReason
      note?: string
    }) => api.put<Lead>(`/leads/${leadId}/lost`, body),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['leads'] })
      qc.invalidateQueries({ queryKey: ['funnel-stats'] })
    },
  })
}

export function useAssignLead() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: ({ leadId, employeeId }: { leadId: number; employeeId: number }) =>
      api.put<Lead>(`/leads/${leadId}/assign`, { employeeId }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['leads'] })
    },
  })
}

export function useAppUserLeads() {
  return useQuery({
    queryKey: ['leads', 'app-users'],
    queryFn: () => api.get<AppUserLead[]>('/leads/app-users'),
  })
}

export function useFunnelStats() {
  return useQuery({
    queryKey: ['funnel-stats'],
    queryFn: () => api.get<FunnelStats>('/leads/funnel-stats'),
  })
}
