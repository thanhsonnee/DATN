/** Kiểu dữ liệu cho module buổi tập, thanh toán và check-in. */

// ------------------------------------------------------------ buổi tập PT

export type SessionStatus =
  | 'PENDING_TRAINER' | 'REJECTED' | 'SCHEDULED'
  | 'COMPLETED' | 'NO_SHOW' | 'CANCELLED'

export type SessionType =
  | 'PAID_PT' | 'COMPLIMENTARY' | 'TRIAL' | 'ORIENTATION' | 'MAKEUP' | 'ASSESSMENT'

export interface PtSession {
  id: number
  memberId: number
  memberName: string
  trainerId: number
  trainerName: string
  registrationId: number
  sessionType: SessionType
  scheduledStart: string
  scheduledEnd: string
  actualStart: string | null
  actualEnd: string | null
  roomName: string | null
  status: SessionStatus
  rejectReason: string | null
  /** Hai mốc xác nhận — buổi chỉ hoàn thành khi CẢ HAI đều có giá trị. */
  trainerConfirmedAt: string | null
  memberConfirmedAt: string | null
  autoConfirmed: boolean
  noShowBy: string | null
  cancelledBy: string | null
  cancelReason: string | null
  isLateCancel: boolean
  note: string | null
}

// ------------------------------------------------------------ sổ cái

export type LedgerEntryType = 'GRANT' | 'CONSUME' | 'REFUND' | 'EXPIRE' | 'ADJUST'

export interface LedgerEntry {
  id: number
  entryType: LedgerEntryType
  /** Dương là cộng buổi, âm là trừ buổi. */
  delta: number
  balanceAfter: number
  sourceType: string
  sourceId: number | null
  reason: string | null
  createdAt: string
}

export interface SoCai {
  registrationId: number
  soDuHienTai: number
  /** Tổng cộng dồn có khớp số dư cuối không. Sai lệch nghĩa là có bút toán hỏng. */
  batBienConDung: boolean
  lichSu: LedgerEntry[]
}

// ------------------------------------------------------------ thanh toán

export type InvoiceStatus =
  | 'UNPAID' | 'PARTIALLY_PAID' | 'PAID' | 'OVERDUE' | 'CANCELLED' | 'REFUNDED'

export type PaymentMethod =
  | 'CASH' | 'BANK_TRANSFER' | 'VIETQR' | 'CARD_POS' | 'E_WALLET' | 'GATEWAY'

export interface Invoice {
  id: number
  invoiceNo: string
  memberId: number
  memberName: string
  memberCode: string
  registrationId: number
  registrationCode: string
  description: string | null
  totalAmount: number
  paidAmount: number
  balanceDue: number
  status: InvoiceStatus
  issuedAt: string
  dueDate: string | null
  paidAt: string | null
}

export interface Payment {
  id: number
  paymentNo: string
  invoiceId: number
  paymentType: 'PAYMENT' | 'REFUND'
  method: PaymentMethod
  /** Dương là thu vào, âm là hoàn trả. */
  amount: number
  status: string
  cashShiftId: number | null
  refundReason: string | null
  paidAt: string | null
}

export interface CashShift {
  id: number
  employeeId: number
  employeeName: string
  openedAt: string
  closedAt: string | null
  openingBalance: number
  /** Số hệ thống tính ra là phải có trong két. */
  expectedCash: number | null
  /** Số lễ tân đếm được. */
  countedCash: number | null
  /** Âm là thiếu tiền so với sổ. */
  difference: number | null
  differenceReason: string | null
  status: 'OPEN' | 'CLOSED' | 'DISCREPANCY'
}

// ------------------------------------------------------------ check-in

export interface CheckInResult {
  id: number
  memberId: number
  memberCode: string
  memberName: string
  photoKey: string | null
  result: string
  choPhepVao: boolean
  /** Câu hiển thị to trên màn hình quầy. */
  thongBao: string
  registrationCode: string | null
  endDate: string | null
  soNgayConLai: number | null
  incidentType: string | null
  incidentNote: string | null
  checkedInAt: string
}

/** Xem trước tình trạng hội viên — chưa ghi lượt check-in nào. */
export interface CheckInPreview {
  memberId: number
  memberCode: string
  memberName: string
  photoKey: string | null
  result: string
  choPhepVao: boolean
  thongBao: string
  registrationCode: string | null
  endDate: string | null
  soNgayConLai: number | null
}

// ------------------------------------------------------------ tra cứu

export interface MemberSearchResult {
  memberId: number
  memberCode: string
  fullName: string
  phone: string
  status: string
}

export interface Trainer {
  id: number
  employeeCode: string
  fullName: string
  level: string | null
  specialties: string | null
  bio: string | null
  ratingAvg: number | null
  ratingCount: number | null
}

// ------------------------------------------------------------ Tài chính nâng cao (E3, E4, E5)

// E3: Doanh thu dồn tích
export interface RevenueSchedule {
  id: number
  registrationId: number
  registrationCode: string
  memberName: string
  scheduleDate: string
  amount: number
  status: 'PENDING' | 'RECOGNIZED' | 'REVERSED'
  recognitionMethod: string
  recognizedAt: string | null
  note: string | null
}

export interface RevenueReport {
  month: number
  year: number
  totalCashCollected: number
  totalAccrualRecognized: number
  totalDeferredRevenue: number
  schedules: RevenueSchedule[]
}

// E4: Bảng lương
export type PayrollStatus = 'DRAFT' | 'APPROVED' | 'PAID' | 'CANCELLED'

export interface PayrollItem {
  id: number
  employeeId: number
  employeeCode: string
  employeeName: string
  department: string
  position: string | null
  baseSalary: number
  ptSessionsCount: number
  ptCommission: number
  salesContractsCount: number
  salesCommission: number
  bonusAmount: number
  deductionAmount: number
  netSalary: number
  note: string | null
}

export interface PayrollRun {
  id: number
  payrollCode: string
  periodMonth: number
  periodYear: number
  totalBaseSalary: number
  totalCommission: number
  totalBonus: number
  totalDeduction: number
  totalNetSalary: number
  status: PayrollStatus
  createdByName: string | null
  approvedByName: string | null
  approvedAt: string | null
  paidAt: string | null
  note: string | null
  items: PayrollItem[]
}

// E5: Chi phí & Lợi nhuận
export type ExpenseCategory =
  | 'RENT'
  | 'UTILITIES'
  | 'EQUIPMENT_MAINTENANCE'
  | 'SALARY'
  | 'SUPPLIES'
  | 'MARKETING'
  | 'OTHER'

export interface Expense {
  id: number
  expenseNo: string
  category: ExpenseCategory
  title: string
  amount: number
  spentAt: string
  spentByName: string | null
  approvedByName: string | null
  status: 'PENDING' | 'APPROVED' | 'REJECTED'
  paymentMethod: PaymentMethod
  receiptUrl: string | null
  note: string | null
  createdAt: string
}

export interface ProfitLossReport {
  month: number
  year: number
  cashRevenue: number
  accrualRevenue: number
  operatingExpenses: number
  salaryExpenses: number
  totalExpenses: number
  netProfitCashBasis: number
  netProfitAccrualBasis: number
  expensesByCategory: Record<string, number>
}

