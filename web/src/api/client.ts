import type { ApiErrorBody } from './types'

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

export function setAccessToken(token: string | null) {
  accessToken = token
  if (token) localStorage.setItem('accessToken', token)
  else localStorage.removeItem('accessToken')
}

export function getAccessToken() {
  return accessToken
}

/** Gọi khi token hết hạn — do lớp trạng thái đăng nhập gán vào. */
let onUnauthorized: (() => void) | null = null

export function setUnauthorizedHandler(handler: () => void) {
  onUnauthorized = handler
}

async function request<T>(method: string, path: string, body?: unknown): Promise<T> {
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
    // 401 nghĩa là chưa đăng nhập hoặc token hết hạn — đưa về trang đăng nhập.
    // 403 là đã đăng nhập nhưng không đủ quyền, KHÔNG được đăng xuất người dùng.
    if (res.status === 401) onUnauthorized?.()

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
