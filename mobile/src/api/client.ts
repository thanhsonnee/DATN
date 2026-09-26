import * as SecureStore from 'expo-secure-store';
import { API_BASE_URL } from '@/config';
import type { ApiErrorBody, TokenResponse } from './types';

const ACCESS_KEY = 'accessToken';
const REFRESH_KEY = 'refreshToken';

/** Lỗi từ backend, giữ nguyên mã lỗi để giao diện xử lý theo từng trường hợp. */
export class ApiError extends Error {
  constructor(
    readonly status: number,
    readonly code: string,
    message: string,
    readonly fields?: Record<string, string>,
  ) {
    super(message);
    this.name = 'ApiError';
  }
}

/**
 * Nơi duy nhất giữ token trong bộ nhớ, để tầng gọi API không phụ thuộc React.
 * Khác web (đọc localStorage đồng bộ lúc khởi động module), SecureStore chỉ
 * có API bất đồng bộ — phải gọi `restoreTokensFromStorage()` một lần lúc mở
 * app (làm trong `stores/auth.ts`) trước khi request nào có thể cần token.
 */
let accessToken: string | null = null;
let refreshToken: string | null = null;

export async function restoreTokensFromStorage() {
  const [a, r] = await Promise.all([
    SecureStore.getItemAsync(ACCESS_KEY),
    SecureStore.getItemAsync(REFRESH_KEY),
  ]);
  accessToken = a;
  refreshToken = r;
  return { accessToken: a, refreshToken: r };
}

export async function setAccessToken(token: string | null) {
  accessToken = token;
  if (token) await SecureStore.setItemAsync(ACCESS_KEY, token);
  else await SecureStore.deleteItemAsync(ACCESS_KEY);
}

export async function setRefreshToken(token: string | null) {
  refreshToken = token;
  if (token) await SecureStore.setItemAsync(REFRESH_KEY, token);
  else await SecureStore.deleteItemAsync(REFRESH_KEY);
}

export function getAccessToken() {
  return accessToken;
}

/** Gọi khi refresh token cũng không cứu được nữa — do lớp trạng thái đăng nhập gán vào. */
let onUnauthorized: (() => void) | null = null;

export function setUnauthorizedHandler(handler: () => void) {
  onUnauthorized = handler;
}

/**
 * Nhiều request có thể cùng lúc nhận 401 (access token vừa hết hạn sau 15
 * phút). Gộp lại làm mới MỘT LẦN — các request khác đợi chung promise này
 * thay vì mỗi cái tự gọi /auth/refresh riêng.
 */
let dangLamMoi: Promise<boolean> | null = null;

async function lamMoiToken(): Promise<boolean> {
  if (!refreshToken) return false;
  if (!dangLamMoi) {
    dangLamMoi = (async () => {
      try {
        const res = await fetch(API_BASE_URL + '/auth/refresh', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ refreshToken }),
        });
        if (!res.ok) return false;
        const data: TokenResponse = await res.json();
        await setAccessToken(data.accessToken);
        await setRefreshToken(data.refreshToken);
        return true;
      } catch {
        return false;
      } finally {
        dangLamMoi = null;
      }
    })();
  }
  return dangLamMoi;
}

async function request<T>(method: string, path: string, body?: unknown, daThuLamMoi = false): Promise<T> {
  const headers: Record<string, string> = {};
  if (body !== undefined) headers['Content-Type'] = 'application/json';
  if (accessToken) headers['Authorization'] = `Bearer ${accessToken}`;

  let res: Response;
  try {
    res = await fetch(API_BASE_URL + path, {
      method,
      headers,
      body: body === undefined ? undefined : JSON.stringify(body),
    });
  } catch {
    throw new ApiError(0, 'NETWORK_ERROR',
      'Không kết nối được máy chủ — kiểm tra điện thoại và máy tính có cùng Wi-Fi, và IP trong .env.local còn đúng không');
  }

  if (res.status === 204) return null as T;

  if (!res.ok) {
    // Access token hết hạn giữa lúc đang thao tác (15 phút) — thử làm mới
    // bằng refresh token (30 ngày) rồi gọi lại ĐÚNG request gốc một lần.
    if (res.status === 401 && !daThuLamMoi
        && path !== '/auth/refresh' && path !== '/auth/login' && path !== '/auth/logout') {
      if (await lamMoiToken()) return request<T>(method, path, body, true);
    }

    // Refresh cũng không cứu được → thật sự chưa đăng nhập/hết hạn.
    // 403 là đủ quyền đăng nhập nhưng không đủ quyền, KHÔNG được đăng xuất.
    // /auth/logout tự nó 401 thì KHÔNG được gọi lại onUnauthorized, tránh đệ quy.
    if (res.status === 401 && path !== '/auth/logout') onUnauthorized?.();

    let payload: Partial<ApiErrorBody> = {};
    try {
      payload = await res.json();
    } catch {
      /* phản hồi không phải JSON, dùng thông báo mặc định bên dưới */
    }
    throw new ApiError(
      res.status,
      payload.code ?? 'UNKNOWN',
      payload.message ?? 'Không kết nối được máy chủ, vui lòng thử lại',
      payload.fields,
    );
  }

  return res.json() as Promise<T>;
}

export const api = {
  get: <T>(path: string) => request<T>('GET', path),
  post: <T>(path: string, body?: unknown) => request<T>('POST', path, body),
  put: <T>(path: string, body?: unknown) => request<T>('PUT', path, body),
  delete: <T>(path: string) => request<T>('DELETE', path),
};
