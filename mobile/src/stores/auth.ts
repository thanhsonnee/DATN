import { create } from 'zustand';
import {
  api, restoreTokensFromStorage, setAccessToken, setRefreshToken, setUnauthorizedHandler,
} from '@/api/client';
import type { MeResponse, TokenResponse, UserRole } from '@/api/types';

interface AuthState {
  user: MeResponse | null;
  /** Đang kiểm tra token cũ còn hiệu lực không, để chưa vội chuyển màn hình. */
  loading: boolean;
  login: (username: string, password: string) => Promise<void>;
  register: (input: RegisterInput) => Promise<void>;
  logout: () => Promise<void>;
  restore: () => Promise<void>;
  refreshUser: () => Promise<void>;
}

export interface RegisterInput {
  fullName: string;
  phone: string;
  email?: string;
  password: string;
}

let dangDangXuat = false;

export const useAuth = create<AuthState>((set, get) => ({
  user: null,
  loading: true,

  login: async (username, password) => {
    const res = await api.post<TokenResponse>('/auth/login', { username, password });
    await setAccessToken(res.accessToken);
    await setRefreshToken(res.refreshToken);
    set({ user: res.user });
  },

  register: async (input) => {
    const res = await api.post<TokenResponse>('/auth/register', input);
    await setAccessToken(res.accessToken);
    await setRefreshToken(res.refreshToken);
    set({ user: res.user });
  },

  /**
   * Thu hồi refresh token THẬT SỰ ở server (tăng token_version), không chỉ
   * xóa token trong máy. Lỗi mạng thì vẫn dọn phía client bình thường — không
   * để người dùng kẹt lại vì gọi API thất bại.
   */
  logout: async () => {
    if (dangDangXuat) return;
    dangDangXuat = true;
    try {
      try { await api.post('/auth/logout'); } catch { /* best-effort */ }
      await setAccessToken(null);
      await setRefreshToken(null);
      set({ user: null });
    } finally {
      dangDangXuat = false;
    }
  },

  /**
   * Khôi phục phiên khi mở lại app: đọc token đã lưu trong SecureStore rồi
   * hỏi lại backend. Access token dù hết hạn (quá 15 phút) vẫn tự làm mới
   * được nhờ refresh token (hạn 30 ngày) — nằm trong tầng gọi API (client.ts).
   */
  restore: async () => {
    const { accessToken } = await restoreTokensFromStorage();
    if (!accessToken) {
      set({ loading: false });
      return;
    }
    try {
      const me = await api.get<MeResponse>('/auth/me');
      set({ user: me, loading: false });
    } catch {
      await setAccessToken(null);
      await setRefreshToken(null);
      set({ user: null, loading: false });
    }
  },

  /**
   * Đọc lại thông tin tài khoản — cần gọi sau khi mua gói lần đầu, vì lúc đó
   * người dùng mới trở thành hội viên.
   */
  refreshUser: async () => {
    if (!get().user) return;
    const me = await api.get<MeResponse>('/auth/me');
    set({ user: me });
  },
}));

// Token hết hạn thì tự đăng xuất, không để người dùng thao tác mãi mà không hiểu vì sao
setUnauthorizedHandler(() => useAuth.getState().logout());

export const laNhanVien = (role?: UserRole) =>
  !!role && (['ADMIN', 'SALE', 'RECEPTIONIST', 'ACCOUNTANT'] as UserRole[]).includes(role);
