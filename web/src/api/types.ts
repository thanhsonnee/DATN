/**
 * Kiểu dữ liệu khớp với DTO của backend.
 *
 * TẠM THỜI viết tay. Khi module nghiệp vụ ổn định sẽ sinh tự động từ OpenAPI
 * (`/v3/api-docs`) để backend đổi trường thì chỗ này báo lỗi biên dịch ngay.
 */

export type UserRole =
  | 'ADMIN' | 'MEMBER' | 'TRAINER' | 'SALE' | 'RECEPTIONIST' | 'ACCOUNTANT'

export interface MeResponse {
  userId: number
  username: string
  fullName: string
  phone: string
  role: UserRole
  status: 'ACTIVE' | 'LOCKED'
  memberId: number | null
  memberCode: string | null
  /** Đã mua gói bao giờ chưa. Có tài khoản không đồng nghĩa là hội viên. */
  isMember: boolean
}

export interface TokenResponse {
  accessToken: string
  refreshToken: string
  tokenType: string
  expiresIn: number
  user: MeResponse
}

export type PackageType = 'TIME_BASED' | 'SESSION_BASED' | 'HYBRID' | 'DAY_PASS'

export interface Membership {
  id: number
  code: string
  name: string
  packageType: PackageType
  durationDays: number | null
  sessionCount: number | null
  price: number
  includesTrainer: boolean
  maxFreezeDays: number
  isRefundable: boolean
  description: string | null
}

export type RegistrationStatus =
  | 'PENDING_PAYMENT' | 'ACTIVE' | 'FROZEN' | 'COMPLETED' | 'CANCELLED' | 'REFUNDED'

export type FreezeStatus =
  | 'PENDING' | 'APPROVED' | 'REJECTED' | 'ACTIVE' | 'ENDED' | 'CANCELLED'

export interface FreezeInfo {
  fromDate: string | null
  toDate: string | null
  days: number | null
  reason: string | null
  reasonType: string | null
  status: FreezeStatus
}

export interface Registration {
  id: number
  registrationCode: string
  memberId: number
  memberCode: string
  memberName: string
  membershipId: number
  membershipName: string
  packageType: PackageType
  durationDays: number | null
  sessionsTotal: number | null
  /** Đọc sống từ bảng giá hiện tại, không snapshot vào hợp đồng. */
  maxFreezeDays: number
  listPrice: number
  discountAmount: number
  discountReason: string | null
  finalPrice: number
  contractDate: string
  startDate: string | null
  endDate: string | null
  activatedAt: string | null
  status: RegistrationStatus
  freeze: FreezeInfo | null
}

/** Định dạng lỗi thống nhất do backend trả về. */
export interface ApiErrorBody {
  timestamp: string
  code: string
  message: string
  path: string
  /** Chỉ có khi lỗi kiểm tra dữ liệu: tên trường sai → thông báo tương ứng. */
  fields?: Record<string, string>
}
