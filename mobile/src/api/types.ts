/**
 * Kiểu dữ liệu khớp với DTO của backend — chép lại có chọn lọc từ
 * `web/src/api/types.ts` và `web/src/api/types-cde.ts`, chỉ giữ phần App Hội
 * viên dùng tới. Sửa API nào bên backend thì nhớ sửa cả 2 nơi (web + mobile)
 * vì hiện chưa có gói dùng chung giữa 2 codebase.
 */

// ------------------------------------------------------------ định danh

export type UserRole =
  | 'ADMIN' | 'MEMBER' | 'TRAINER' | 'SALE' | 'RECEPTIONIST' | 'ACCOUNTANT';

export interface MeResponse {
  userId: number;
  username: string;
  fullName: string;
  phone: string;
  role: UserRole;
  status: 'ACTIVE' | 'LOCKED';
  memberId: number | null;
  memberCode: string | null;
  /** Đã mua gói bao giờ chưa. Có tài khoản không đồng nghĩa là hội viên. */
  isMember: boolean;
}

export interface TokenResponse {
  accessToken: string;
  refreshToken: string;
  tokenType: string;
  expiresIn: number;
  user: MeResponse;
}

/** Định dạng lỗi thống nhất do backend trả về. */
export interface ApiErrorBody {
  timestamp: string;
  code: string;
  message: string;
  path: string;
  fields?: Record<string, string>;
}

// ------------------------------------------------------------ gói tập & hợp đồng

export type PackageType = 'TIME_BASED' | 'SESSION_BASED' | 'HYBRID' | 'DAY_PASS';

export interface Membership {
  id: number;
  code: string;
  name: string;
  packageType: PackageType;
  durationDays: number | null;
  sessionCount: number | null;
  price: number;
  includesTrainer: boolean;
  maxFreezeDays: number;
  isRefundable: boolean;
  description: string | null;
}

export type RegistrationStatus =
  | 'PENDING_PAYMENT' | 'ACTIVE' | 'FROZEN' | 'COMPLETED' | 'CANCELLED' | 'REFUNDED';

export type FreezeStatus =
  | 'PENDING' | 'APPROVED' | 'REJECTED' | 'ACTIVE' | 'ENDED' | 'CANCELLED';

export interface FreezeInfo {
  fromDate: string | null;
  toDate: string | null;
  days: number | null;
  reason: string | null;
  reasonType: string | null;
  status: FreezeStatus;
}

export interface Registration {
  id: number;
  registrationCode: string;
  memberId: number;
  memberCode: string;
  memberName: string;
  membershipId: number;
  membershipName: string;
  packageType: PackageType;
  durationDays: number | null;
  sessionsTotal: number | null;
  maxFreezeDays: number;
  listPrice: number;
  discountAmount: number;
  discountReason: string | null;
  finalPrice: number;
  contractDate: string;
  startDate: string | null;
  endDate: string | null;
  activatedAt: string | null;
  status: RegistrationStatus;
  freeze: FreezeInfo | null;
}

// ------------------------------------------------------------ buổi tập PT

export type SessionStatus =
  | 'PENDING_TRAINER' | 'REJECTED' | 'SCHEDULED'
  | 'COMPLETED' | 'NO_SHOW' | 'CANCELLED';

export type SessionType =
  | 'PAID_PT' | 'COMPLIMENTARY' | 'TRIAL' | 'ORIENTATION' | 'MAKEUP' | 'ASSESSMENT';

export interface PtSession {
  id: number;
  memberId: number;
  memberName: string;
  trainerId: number;
  trainerName: string;
  registrationId: number;
  sessionType: SessionType;
  scheduledStart: string;
  scheduledEnd: string;
  actualStart: string | null;
  actualEnd: string | null;
  roomName: string | null;
  status: SessionStatus;
  rejectReason: string | null;
  /** Hai mốc xác nhận — buổi chỉ hoàn thành khi CẢ HAI đều có giá trị. */
  trainerConfirmedAt: string | null;
  memberConfirmedAt: string | null;
  autoConfirmed: boolean;
  noShowBy: string | null;
  cancelledBy: string | null;
  cancelReason: string | null;
  isLateCancel: boolean;
  note: string | null;
}

export type LedgerEntryType = 'GRANT' | 'CONSUME' | 'REFUND' | 'EXPIRE' | 'ADJUST';

export interface LedgerEntry {
  id: number;
  entryType: LedgerEntryType;
  /** Dương là cộng buổi, âm là trừ buổi. */
  delta: number;
  balanceAfter: number;
  sourceType: string;
  sourceId: number | null;
  reason: string | null;
  createdAt: string;
}

export interface SoCai {
  registrationId: number;
  soDuHienTai: number;
  batBienConDung: boolean;
  lichSu: LedgerEntry[];
}

export interface Trainer {
  id: number;
  employeeCode: string;
  fullName: string;
  level: string | null;
  specialties: string | null;
  bio: string | null;
  ratingAvg: number | null;
  ratingCount: number | null;
}

// ------------------------------------------------------------ check-in

export interface CheckInPreview {
  memberId: number;
  memberCode: string;
  memberName: string;
  photoKey: string | null;
  result: string;
  choPhepVao: boolean;
  thongBao: string;
  registrationCode: string | null;
  endDate: string | null;
  soNgayConLai: number | null;
}

export type CheckInSelfStatusValue = 'PENDING' | 'CONFIRMED' | 'NONE';

export interface CheckInSelfStatus {
  status: CheckInSelfStatusValue;
  choPhepVao: boolean;
  thongBao: string;
  /** Lý do lễ tân từ chối — chỉ khác null khi lễ tân từ chối thủ công kèm lý do. */
  lyDo: string | null;
  checkedInAt: string | null;
}

// ------------------------------------------------------------ phản hồi

export type FeedbackType = 'TRAINER' | 'FACILITY' | 'HYGIENE' | 'SERVICE' | 'GENERAL';

export type FeedbackStatus = 'OPEN' | 'IN_PROGRESS' | 'WAITING_PARTS' | 'RESOLVED' | 'CLOSED';

export interface Feedback {
  id: number;
  memberId: number;
  memberName: string;
  feedbackType: FeedbackType;
  trainerId: number | null;
  trainerName: string | null;
  equipmentId: number | null;
  equipmentName: string | null;
  rating: number | null;
  description: string;
  status: FeedbackStatus;
  urgent: boolean;
  repairCost: number | null;
  resolutionNote: string | null;
  resolvedByName: string | null;
  resolvedAt: string | null;
  createdAt: string;
}

export interface Equipment {
  id: number;
  name: string;
  roomName: string | null;
  status: string;
  note: string | null;
}
