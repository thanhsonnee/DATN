import { useEffect, useRef, useState } from 'react'

/** Trì hoãn cập nhật giá trị — tránh bắn API mỗi lần gõ một chữ. */
function useDebounce<T>(value: T, delayMs: number): T {
  const [debounced, setDebounced] = useState(value)
  useEffect(() => {
    const timer = setTimeout(() => setDebounced(value), delayMs)
    return () => clearTimeout(timer)
  }, [value, delayMs])
  return debounced
}

interface Props<T> {
  label: string
  placeholder?: string
  hint?: string
  ketQua: T[] | undefined
  dangTai?: boolean
  hienThi: (item: T) => { chinh: string; phu: string }
  khoa: (item: T) => string | number
  onChon: (item: T) => void
  /**
   * Báo từ khóa (đã trì hoãn 300ms) ra ngoài để trang cha gọi API tìm kiếm.
   * Không tự gọi API bên trong component này — mỗi nơi dùng có thể cần gọi
   * API khác nhau (tìm hội viên, tìm huấn luyện viên...).
   */
  onTuKhoaChange: (tuKhoa: string) => void
  /** Người đang chọn được hiển thị thay ô tìm kiếm, kèm nút đổi lựa chọn khác. */
  daChon?: { chinh: string; phu: string } | null
  onBoChon?: () => void
}

/**
 * Ô "gõ để tìm" dùng chung: gõ vài ký tự → hiện danh sách khớp → bấm chọn.
 *
 * Thay cho việc bắt người dùng nhớ và gõ tay một con số ID — đây là mẫu dùng
 * lại cho mọi chỗ cần "chọn một người/một thứ" trong toàn bộ ứng dụng.
 */
export function SearchSelect<T>({
  label, placeholder, hint, ketQua, dangTai, hienThi, khoa, onChon,
  onTuKhoaChange, daChon, onBoChon,
}: Props<T>) {
  const [tuKhoa, setTuKhoa] = useState('')
  const debounced = useDebounce(tuKhoa, 300)
  const [dangMo, setDangMo] = useState(false)
  const boxRef = useRef<HTMLDivElement>(null)

  useEffect(() => { onTuKhoaChange(debounced) }, [debounced, onTuKhoaChange])

  // Đóng danh sách khi bấm ra ngoài
  useEffect(() => {
    const onClickOutside = (e: MouseEvent) => {
      if (boxRef.current && !boxRef.current.contains(e.target as Node)) setDangMo(false)
    }
    document.addEventListener('mousedown', onClickOutside)
    return () => document.removeEventListener('mousedown', onClickOutside)
  }, [])

  if (daChon) {
    return (
      <div>
        <label className="mb-1 block text-sm font-medium text-slate-700">{label}</label>
        <div className="flex items-center justify-between rounded-lg border border-brand-300
                        bg-brand-50 px-3 py-2 text-sm">
          <div>
            <span className="font-medium text-slate-900">{daChon.chinh}</span>
            <span className="ml-2 text-slate-500">{daChon.phu}</span>
          </div>
          {onBoChon && (
            <button type="button" onClick={onBoChon}
                    className="text-xs font-medium text-brand-600 hover:underline">
              Đổi
            </button>
          )}
        </div>
      </div>
    )
  }

  return (
    <div ref={boxRef} className="relative">
      <label className="mb-1 block text-sm font-medium text-slate-700">{label}</label>
      <input
        value={tuKhoa}
        onChange={(e) => { setTuKhoa(e.target.value); setDangMo(true) }}
        onFocus={() => setDangMo(true)}
        placeholder={placeholder}
        className="w-full rounded-lg border border-slate-300 px-3 py-2 text-sm outline-none
                   focus:border-brand-500 focus:ring-2 focus:ring-brand-500/30"
      />
      {hint && !dangMo && <p className="mt-1 text-xs text-slate-500">{hint}</p>}

      {dangMo && debounced.trim().length >= 2 && (
        <div className="absolute z-20 mt-1 w-full rounded-lg border border-slate-200
                        bg-white shadow-lg">
          {dangTai ? (
            <p className="px-3 py-2 text-sm text-slate-400">Đang tìm…</p>
          ) : ketQua && ketQua.length > 0 ? (
            <ul className="max-h-64 overflow-y-auto py-1">
              {ketQua.map((item) => {
                const { chinh, phu } = hienThi(item)
                return (
                  <li key={khoa(item)}>
                    <button
                      type="button"
                      onClick={() => { onChon(item); setTuKhoa(''); setDangMo(false) }}
                      className="flex w-full items-center justify-between px-3 py-2 text-left
                                 text-sm hover:bg-slate-50"
                    >
                      <span className="font-medium text-slate-800">{chinh}</span>
                      <span className="text-xs text-slate-500">{phu}</span>
                    </button>
                  </li>
                )
              })}
            </ul>
          ) : (
            <p className="px-3 py-2 text-sm text-slate-400">Không tìm thấy</p>
          )}
        </div>
      )}
    </div>
  )
}
