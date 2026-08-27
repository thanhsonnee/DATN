import type { ReactNode } from 'react'

type Tone = 'error' | 'success' | 'info'

const tones: Record<Tone, string> = {
  error: 'border-red-200 bg-red-50 text-red-800',
  success: 'border-emerald-200 bg-emerald-50 text-emerald-800',
  info: 'border-blue-200 bg-blue-50 text-blue-800',
}

export function Alert({ tone = 'info', children }: { tone?: Tone; children: ReactNode }) {
  if (!children) return null
  return (
    <div role={tone === 'error' ? 'alert' : 'status'}
         className={`rounded-lg border px-4 py-3 text-sm ${tones[tone]}`}>
      {children}
    </div>
  )
}
