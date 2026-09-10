import { create } from 'zustand'
import { api, setAccessToken, setRefreshToken, setUnauthorizedHandler } from '@/api/client'
import type { MeResponse, TokenResponse, UserRole } from '@/api/types'

interface AuthState {
  user: MeResponse | null
  /** Đang kiểm tra token cũ còn hiệu lực không, để chưa vội chuyển trang. */
  loading: boolean
  login: (username: string, password: string) => Promise<void>
  register: (input: RegisterInput) => Promise<void>
  logout: () => Promise<void>
  restore: () => Promise<void>
  refreshUser: () => Promise<void>
}

export interface RegisterInput {
  fullName: string
  phone: string
  email?: string
  password: string
}

let dangDangXuat = false

export const useAuth = create<AuthState>((set, get) => ({
  user: null,
  loading: true,

  login: async (username, password) => {
    const res = await api.post<TokenResponse>('/auth/login', { username, password })
    setAccessToken(res.accessToken)
    setRefreshToken(res.refreshToken)
    set({ user: res.user })
  },

  register: async (input) => {
    const res = await api.post<TokenResponse>('/auth/register', input)
    setAccessToken(res.accessToken)
    setRefreshToken(res.refreshToken)
    set({ user: res.user })
  },

  /**
   * Thu hồi refresh token THẬT SỰ ở server (tăng token_version), không chỉ xóa
   * token phía trình duyệt như trước — nếu không, refresh token cũ (30 ngày)
   * vẫn kỹ thuật còn dùng được nếu ai đó đã có nó. Lỗi mạng thì vẫn dọn phía
   * client bình thường — không để người dùng kẹt lại vì gọi API thất bại.
   *
   * Chặn gọi chồng (`dangDangXuat`): lớp phòng thủ thứ 2, phòng khi có đường
   * nào khác (ngoài client.ts đã chặn) vô tình gọi logout() nhiều lần liên
   * tiếp — để không lặp lại sự cố gọi /auth/logout dồn dập từng xảy ra.
   */
  logout: async () => {
    if (dangDangXuat) return
    dangDangXuat = true
    try {
      try { await api.post('/auth/logout') } catch { /* best-effort */ }
      setAccessToken(null)
      setRefreshToken(null)
      set({ user: null })
    } finally {
      dangDangXuat = false
    }
  },

  /**
   * Khôi phục phiên khi tải lại trang: token còn trong máy thì hỏi lại backend.
   * Access token dù đã hết hạn (quá 15 phút) vẫn tự làm mới được nhờ refresh
   * token (hạn 30 ngày) — cơ chế nằm trong tầng gọi API (client.ts), gọi
   * /auth/me ở đây không cần biết gì thêm.
   */
  restore: async () => {
    if (!localStorage.getItem('accessToken')) {
      set({ loading: false })
      return
    }
    try {
      const me = await api.get<MeResponse>('/auth/me')
      set({ user: me, loading: false })
    } catch {
      setAccessToken(null)
      setRefreshToken(null)
      set({ user: null, loading: false })
    }
  },

  /**
   * Đọc lại thông tin tài khoản.
   * Cần gọi sau khi mua gói lần đầu, vì lúc đó người dùng mới trở thành hội viên.
   */
  refreshUser: async () => {
    if (!get().user) return
    const me = await api.get<MeResponse>('/auth/me')
    set({ user: me })
  },
}))

// Token hết hạn thì tự đăng xuất, không để người dùng bấm mãi mà không hiểu vì sao
setUnauthorizedHandler(() => useAuth.getState().logout())

// Đồng bộ đăng xuất giữa các tab cùng trình duyệt: sự kiện 'storage' chỉ nổ ở
// CÁC TAB KHÁC (không phải tab vừa bấm), nên không lo gọi logout() lặp ở chính
// tab vừa đăng xuất. Không xử lý chiều đăng nhập vì chưa ai yêu cầu.
window.addEventListener('storage', (e) => {
  if (e.key === 'accessToken' && e.newValue === null && useAuth.getState().user) {
    useAuth.getState().logout()
  }
})

const NHAN_VIEN: UserRole[] = ['ADMIN', 'SALE', 'RECEPTIONIST', 'ACCOUNTANT']

export const laNhanVien = (role?: UserRole) => !!role && NHAN_VIEN.includes(role)
