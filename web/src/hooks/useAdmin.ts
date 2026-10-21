import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from '@/api/client'
import type {
  AdminDashboard,
  EmployeeAccount,
  EmployeeSummary,
  EmployeeRole,
  EmploymentType,
  ResetPasswordResult,
  TrainerLevel,
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
      level?: TrainerLevel
      employmentType?: EmploymentType
      baseSalary?: number
      startDate?: string
    }) => api.post<EmployeeAccount>('/admin/employees', body),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['admin', 'employees'] })
    },
  })
}

export interface UpdateEmployeeInput {
  id: number
  fullName?: string
  baseSalary?: number
}

/** Chỉ sửa được họ tên + lương cơ bản — không có SĐT/vai trò (xem UpdateEmployeeRequest ở backend). */
export function useUpdateEmployeeAccount() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: ({ id, ...body }: UpdateEmployeeInput) =>
      api.put<EmployeeSummary>(`/admin/employees/${id}`, body),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['admin', 'employees'] })
    },
  })
}

/**
 * Đặt lại mật khẩu cho một tài khoản bất kỳ, tìm theo số điện thoại — dùng cho
 * cả nhân viên lẫn hội viên, không chỉ 4 vai trò nhân sự. Không có query liên
 * quan để invalidate vì không có danh sách hội viên nào trong web hiển thị
 * trạng thái khóa tài khoản.
 */
export function useResetPassword() {
  return useMutation({
    mutationFn: (phone: string) =>
      api.post<ResetPasswordResult>('/admin/employees/reset-mat-khau', { phone }),
  })
}
