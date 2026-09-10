import type { ApiErrorBody, TokenResponse } from './types'

const BASE = '/api/v1'

/** Lỗi từ backend, giữ nguyên mã lỗi để giao diện xử lý theo từng trường hợp. */
export class ApiError extends Error {
  constructor(
    readonly status: number,
    readonly code: string,
    message: string,
    readonly fields?: Record<string, string>,
  ) {
    super(message)
    this.name = 'ApiError'
  }
}

/** Nơi duy nhất giữ token. Đặt ở đây để tầng gọi API không phụ thuộc React. */
let accessToken: string | null = localStorage.getItem('accessToken')
let refreshToken: string | null = localStorage.getItem('refreshToken')

export function setAccessToken(token: string | null) {
  accessToken = token
  if (token) localStorage.setItem('accessToken', token)
  else localStorage.removeItem('accessToken')
}

export function setRefreshToken(token: string | null) {
  refreshToken = token
  if (token) localStorage.setItem('refreshToken', token)
  else localStorage.removeItem('refreshToken')
}

export function getAccessToken() {
  return accessToken
}

/** Gọi khi refresh token cũng không cứu được nữa — do lớp trạng thái đăng nhập gán vào. */
let onUnauthorized: (() => void) | null = null

export function setUnauthorizedHandler(handler: () => void) {
  onUnauthorized = handler
}

/**
 * Nhiều request có thể cùng lúc nhận 401 (access token vừa hết hạn sau 15 phút).
 * Gộp lại làm mới MỘT LẦN — các request khác đợi chung promise này thay vì mỗi
 * cái tự gọi /auth/refresh riêng, tránh cấp thừa token và tốn round-trip.
 */
let dangLamMoi: Promise<boolean> | null = null

async function lamMoiToken(): Promise<boolean> {
  if (!refreshToken) return false
  if (!dangLamMoi) {
    dangLamMoi = (async () => {
      try {
        const res = await fetch(BASE + '/auth/refresh', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ refreshToken }),
        })
        if (!res.ok) return false
        const data: TokenResponse = await res.json()
        setAccessToken(data.accessToken)
        setRefreshToken(data.refreshToken)
        return true
      } catch {
        return false
      } finally {
        dangLamMoi = null
      }
    })()
  }
  return dangLamMoi
}

async function request<T>(method: string, path: string, body?: unknown, daThuLamMoi = false): Promise<T> {
  const headers: Record<string, string> = {}
  if (body !== undefined) headers['Content-Type'] = 'application/json'
  if (accessToken) headers['Authorization'] = `Bearer ${accessToken}`

  const res = await fetch(BASE + path, {
    method,
    headers,
    body: body === undefined ? undefined : JSON.stringify(body),
  })

  if (res.status === 204) return null as T

  if (!res.ok) {
    // Access token hết hạn giữa lúc đang thao tác (15 phút) — thử làm mới bằng
    // refresh token (hạn 30 ngày) rồi gọi lại ĐÚNG request gốc một lần, để
    // người dùng không bị đá ra ngoài chỉ vì đang thao tác hơi lâu.
    // /auth/logout LOẠI RIÊNG: gọi nó nghĩa là đang đăng xuất — token có hết hạn
    // hay bị khóa (vd tài khoản auto_locked) thì cũng mặc kệ, không thử refresh
    // rồi gọi lại, càng không được đi tiếp xuống onUnauthorized bên dưới.
    if (res.status === 401 && !daThuLamMoi
        && path !== '/auth/refresh' && path !== '/auth/login' && path !== '/auth/logout') {
      if (await lamMoiToken()) return request<T>(method, path, body, true)
    }

    // Refresh cũng không cứu được (hoặc không có refresh token) → thật sự
    // chưa đăng nhập/hết hạn, đưa về trang đăng nhập.
    // 403 là đã đăng nhập nhưng không đủ quyền, KHÔNG được đăng xuất người dùng.
    // /auth/logout tự nó 401 thì KHÔNG được gọi lại onUnauthorized (= logout())
    // — nếu không sẽ đệ quy vô hạn: logout() gọi /auth/logout, 401, gọi lại
    // onUnauthorized → logout() → /auth/logout → 401 → ... (đã xảy ra thật).
    if (res.status === 401 && path !== '/auth/logout') onUnauthorized?.()

    let payload: Partial<ApiErrorBody> = {}
    try {
      payload = await res.json()
    } catch {
      /* phản hồi không phải JSON, dùng thông báo mặc định bên dưới */
    }
    throw new ApiError(
      res.status,
      payload.code ?? 'UNKNOWN',
      payload.message ?? 'Không kết nối được máy chủ, vui lòng thử lại',
      payload.fields,
    )
  }

  return res.json() as Promise<T>
}

export const api = {
  get: <T>(path: string) => request<T>('GET', path),
  post: <T>(path: string, body?: unknown) => request<T>('POST', path, body),
  put: <T>(path: string, body?: unknown) => request<T>('PUT', path, body),
  delete: <T>(path: string) => request<T>('DELETE', path),
}
