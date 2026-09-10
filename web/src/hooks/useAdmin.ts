import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from '@/api/client'
import type {
  AdminDashboard,
  EmployeeAccount,
  EmployeeSummary,
  EmployeeRole,
  EmploymentType,
} from '@/api/types-cde'

export function useAdminDashboard() {
  return useQuery({
    queryKey: ['admin', 'dashboard'],
    queryFn: () => api.get<AdminDashboard>('/admin/dashboard'),
  })
}

export function useEmployeeAccounts() {
  return useQuery({
    queryKey: ['admin', 'employees'],
    queryFn: () => api.get<EmployeeSummary[]>('/admin/employees'),
  })
}

export function useCreateEmployeeAccount() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (body: {
      fullName: string
      phone: string
      email?: string
      role: EmployeeRole
      position?: string
      employmentType?: EmploymentType
      baseSalary?: number
      startDate?: string
    }) => api.post<EmployeeAccount>('/admin/employees', body),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['admin', 'employees'] })
    },
  })
}
