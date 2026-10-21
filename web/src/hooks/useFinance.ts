import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from '@/api/client'
import type {
  RevenueReport,
  PayrollRun,
  PayrollItem,
  Expense,
  ExpenseCategory,
  ProfitLossReport,
  PaymentMethod,
  DailyRevenue,
} from '@/api/types-cde'

// =============================================================================
// E3: Doanh thu dồn tích (Revenue Recognition)
// =============================================================================

export function useRevenueReport(month: number, year: number) {
  return useQuery({
    queryKey: ['finance', 'revenue-report', month, year],
    queryFn: () =>
      api.get<RevenueReport>(`/finance/revenue/report?month=${month}&year=${year}`),
  })
}

export function useScanRevenue() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: () => api.post<number>('/finance/revenue/scan-recognized', {}),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['finance', 'revenue-report'] })
    },
  })
}

// =============================================================================
// E4: Bảng lương (Payroll Runs)
// =============================================================================

export function usePayrollRuns() {
  return useQuery({
    queryKey: ['finance', 'payrolls'],
    queryFn: () => api.get<PayrollRun[]>('/finance/payrolls'),
  })
}

export function usePayrollDetail(id: number | null) {
  return useQuery({
    queryKey: ['finance', 'payrolls', id],
    queryFn: () => api.get<PayrollRun>(`/finance/payrolls/${id}`),
    enabled: !!id,
  })
}

export function useCalculatePayroll() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (body: { periodMonth: number; periodYear: number }) =>
      api.post<PayrollRun>('/finance/payrolls/calculate', body),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['finance', 'payrolls'] })
    },
  })
}

export function useUpdatePayrollItem() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: ({
      itemId,
      ...body
    }: {
      itemId: number
      bonusAmount?: number
      deductionAmount?: number
      note?: string
    }) => api.put<PayrollItem>(`/finance/payrolls/items/${itemId}`, body),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['finance', 'payrolls'] })
    },
  })
}

export function useApprovePayroll() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (id: number) =>
      api.post<PayrollRun>(`/finance/payrolls/${id}/approve`, {}),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['finance', 'payrolls'] })
    },
  })
}

export function usePayPayroll() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (id: number) =>
      api.post<PayrollRun>(`/finance/payrolls/${id}/pay`, {}),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['finance', 'payrolls'] })
    },
  })
}

export function useMyPayslip(month: number, year: number) {
  return useQuery({
    queryKey: ['finance', 'my-payslip', month, year],
    queryFn: () =>
      api.get<PayrollItem>(`/finance/payrolls/my-payslip?month=${month}&year=${year}`),
  })
}

// =============================================================================
// E5: Chi phí & Lợi nhuận (P&L)
// =============================================================================

export function useExpenses(from?: string, to?: string, category?: ExpenseCategory) {
  const params = new URLSearchParams()
  if (from) params.set('from', from)
  if (to) params.set('to', to)
  if (category) params.set('category', category)
  const qs = params.toString() ? `?${params.toString()}` : ''

  return useQuery({
    queryKey: ['finance', 'expenses', from, to, category],
    queryFn: () => api.get<Expense[]>(`/finance/expenses${qs}`),
  })
}

export function useCreateExpense() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (body: {
      category: ExpenseCategory
      title: string
      amount: number
      spentAt: string
      paymentMethod: PaymentMethod
      receiptUrl?: string
      note?: string
    }) => api.post<Expense>('/finance/expenses', body),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['finance', 'expenses'] })
      qc.invalidateQueries({ queryKey: ['finance', 'profit-loss'] })
    },
  })
}

export function useUpdateExpense() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: ({
      id,
      ...body
    }: {
      id: number
      category: ExpenseCategory
      title: string
      amount: number
      spentAt: string
      paymentMethod: PaymentMethod
      receiptUrl?: string
      note?: string
    }) => api.put<Expense>(`/finance/expenses/${id}`, body),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['finance', 'expenses'] })
      qc.invalidateQueries({ queryKey: ['finance', 'profit-loss'] })
    },
  })
}

export function useDeleteExpense() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (id: number) => api.delete(`/finance/expenses/${id}`),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['finance', 'expenses'] })
      qc.invalidateQueries({ queryKey: ['finance', 'profit-loss'] })
    },
  })
}

export function useProfitLossReport(month: number, year: number) {
  return useQuery({
    queryKey: ['finance', 'profit-loss', month, year],
    queryFn: () =>
      api.get<ProfitLossReport>(`/finance/profit-loss?month=${month}&year=${year}`),
  })
}

// =============================================================================
// E6: Đối soát doanh thu theo ngày — minh bạch dòng tiền Lễ tân ↔ Kế toán
// =============================================================================

/** `date` dạng YYYY-MM-DD, bỏ trống = hôm nay. */
export function useDailyRevenue(date?: string) {
  return useQuery({
    queryKey: ['finance', 'daily-revenue', date ?? null],
    queryFn: () => api.get<DailyRevenue>(`/finance/daily-revenue${date ? `?date=${date}` : ''}`),
  })
}

/** Kế toán/Admin xác nhận gộp 1 lần cho cả ngày — không duyệt từng dòng. */
export function useConfirmDailyRevenue() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (date: string) => api.post<DailyRevenue>(`/finance/daily-revenue/${date}/confirm`),
    onSuccess: (_, date) => {
      qc.invalidateQueries({ queryKey: ['finance', 'daily-revenue', date] })
    },
  })
}
