import { useEffect, useRef, useState } from 'react'

interface Props {
  label: string
  value: string // 'YYYY-MM-DD' hoặc rỗng
  onChange: (isoDate: string) => void
  /** 'YYYY-MM-DD' — ngày nhỏ nhất được chọn (không bắt buộc) */
  min?: string
  /** 'YYYY-MM-DD' — ngày lớn nhất được chọn (không bắt buộc) */
  max?: string
  placeholder?: string
}

const THU = ['T2', 'T3', 'T4', 'T5', 'T6', 'T7', 'CN']
const THANG = [
  'Tháng 1', 'Tháng 2', 'Tháng 3', 'Tháng 4', 'Tháng 5', 'Tháng 6',
  'Tháng 7', 'Tháng 8', 'Tháng 9', 'Tháng 10', 'Tháng 11', 'Tháng 12',
]

function toIso(d: Date): string {
  const y = d.getFullYear()
  const m = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  return `${y}-${m}-${day}`
}

function tuIso(iso: string): Date {
  return new Date(iso + 'T00:00:00')
}

function hienThi(iso: string): string {
  const [y, m, d] = iso.split('-')
  return `${d}/${m}/${y}`
}

/** Lịch chọn ngày dạng popover — thay cho việc gõ tay chuỗi ngày YYYY-MM-DD. */
export function DatePicker({ label, value, onChange, min, max, placeholder = 'Chọn ngày' }: Props) {
  const [open, setOpen] = useState(false)
  const [thangXem, setThangXem] = useState(() => tuIso(value || min || toIso(new Date())))
  const wrapRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    if (!open) return
    const onClickNgoai = (e: MouseEvent) => {
      if (wrapRef.current && !wrapRef.current.contains(e.target as Node)) setOpen(false)
    }
    document.addEventListener('mousedown', onClickNgoai)
    return () => document.removeEventListener('mousedown', onClickNgoai)
  }, [open])

  const moLich = () => {
    setThangXem(tuIso(value || min || toIso(new Date())))
    setOpen(true)
  }

  const namXem = thangXem.getFullYear()
  const thangXemSo = thangXem.getMonth()
  const ngayDauThang = new Date(namXem, thangXemSo, 1)
  // Thứ 2 = 0 ... Chủ nhật = 6 (Date.getDay() trả 0 = Chủ nhật)
  const lechDau = (ngayDauThang.getDay() + 6) % 7
  const soNgayTrongThang = new Date(namXem, thangXemSo + 1, 0).getDate()

  const oCells: (Date | null)[] = [
    ...Array(lechDau).fill(null),
    ...Array.from({ length: soNgayTrongThang }, (_, i) => new Date(namXem, thangXemSo, i + 1)),
  ]

  const homNay = toIso(new Date())

  return (
    <div ref={wrapRef} className="relative">
      <label className="mb-1 block text-sm font-medium text-slate-700">{label}</label>
      <button
        type="button"
        onClick={moLich}
        className={`flex w-full items-center justify-between rounded-lg border px-3 py-2 text-left text-sm outline-none transition
                    focus:ring-2 focus:ring-brand-500/30
                    ${open ? 'border-brand-500' : 'border-slate-300'}`}
      >
        <span className={value ? 'text-slate-800' : 'text-slate-400'}>
          {value ? hienThi(value) : placeholder}
        </span>
        <span aria-hidden className="text-slate-400">📅</span>
      </button>

      {open && (
        <div className="absolute z-20 mt-1 w-64 rounded-xl border border-slate-200 bg-white p-3 shadow-lg">
          <div className="mb-2 flex items-center justify-between">
            <button type="button" onClick={() => setThangXem(new Date(namXem, thangXemSo - 1, 1))}
                    className="rounded p-1 text-slate-500 hover:bg-slate-100" aria-label="Tháng trước">
              ‹
            </button>
            <span className="text-sm font-medium text-slate-800">{THANG[thangXemSo]}, {namXem}</span>
            <button type="button" onClick={() => setThangXem(new Date(namXem, thangXemSo + 1, 1))}
                    className="rounded p-1 text-slate-500 hover:bg-slate-100" aria-label="Tháng sau">
              ›
            </button>
          </div>

          <div className="grid grid-cols-7 gap-0.5 text-center text-xs text-slate-400">
            {THU.map((t) => <div key={t} className="py-1">{t}</div>)}
          </div>
          <div className="grid grid-cols-7 gap-0.5">
            {oCells.map((d, i) => {
              if (!d) return <div key={i} />
              const iso = toIso(d)
              const disabled = (min && iso < min) || (max && iso > max)
              const daChon = iso === value
              const laHomNay = iso === homNay
              return (
                <button
                  key={i}
                  type="button"
                  disabled={!!disabled}
                  onClick={() => { onChange(iso); setOpen(false) }}
                  className={`rounded-md py-1.5 text-xs transition
                    ${disabled ? 'cursor-not-allowed text-slate-300' : 'text-slate-700 hover:bg-brand-50'}
                    ${daChon ? 'bg-brand-600 text-white hover:bg-brand-600' : ''}
                    ${laHomNay && !daChon ? 'font-semibold text-brand-600' : ''}`}
                >
                  {d.getDate()}
                </button>
              )
            })}
          </div>
        </div>
      )}
    </div>
  )
}
