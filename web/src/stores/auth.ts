import { create } from 'zustand'
import { api, setAccessToken, setUnauthorizedHandler } from '@/api/client'
import type { MeResponse, TokenResponse, UserRole } from '@/api/types'

interface AuthState {
  user: MeResponse | null
  /** Đang kiểm tra token cũ còn hiệu lực không, để chưa vội chuyển trang. */
  loading: boolean
  login: (username: string, password: string) => Promise<void>
  register: (input: RegisterInput) => Promise<void>
  logout: () => void
  restore: () => Promise<void>
  refreshUser: () => Promise<void>
}

export interface RegisterInput {
  fullName: string
  phone: string
  email?: string
  password: string
}

export const useAuth = create<AuthState>((set, get) => ({
  user: null,
  loading: true,

  login: async (username, password) => {
    const res = await api.post<TokenResponse>('/auth/login', { username, password })
    setAccessToken(res.accessToken)
    set({ user: res.user })
  },

  register: async (input) => {
    const res = await api.post<TokenResponse>('/auth/register', input)
    setAccessToken(res.accessToken)
    set({ user: res.user })
  },

  logout: () => {
    setAccessToken(null)
    set({ user: null })
  },

  /** Khôi phục phiên khi tải lại trang: token còn trong máy thì hỏi lại backend. */
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

const NHAN_VIEN: UserRole[] = ['ADMIN', 'SALE', 'RECEPTIONIST', 'ACCOUNTANT']

export const laNhanVien = (role?: UserRole) => !!role && NHAN_VIEN.includes(role)
