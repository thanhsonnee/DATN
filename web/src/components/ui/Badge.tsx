import type { ReactNode } from 'react'

type Tone = 'gray' | 'green' | 'amber' | 'red' | 'blue'

const tones: Record<Tone, string> = {
  gray: 'bg-slate-100 text-slate-700',
  green: 'bg-emerald-100 text-emerald-800',
  amber: 'bg-amber-100 text-amber-800',
  red: 'bg-red-100 text-red-800',
  blue: 'bg-blue-100 text-blue-800',
}

export function Badge({ tone = 'gray', children }: { tone?: Tone; children: ReactNode }) {
  return (
    <span className={`inline-flex rounded-full px-2.5 py-0.5 text-xs font-medium ${tones[tone]}`}>
      {children}
    </span>
  )
}

/** Màu theo trạng thái hợp đồng — dùng chung để mọi màn hình hiển thị nhất quán. */
export function tonesForRegistration(status: string): Tone {
  switch (status) {
    case 'ACTIVE': return 'green'
    case 'PENDING_PAYMENT': return 'amber'
    case 'FROZEN': return 'blue'
    case 'CANCELLED':
    case 'REFUNDED': return 'red'
    default: return 'gray'
  }
}
