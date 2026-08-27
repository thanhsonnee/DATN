import { useState } from 'react'
import { useThongKeCheckIn } from '@/hooks/useCheckIn'
import { Card, CardBody, CardHeader } from '@/components/ui/Card'
import { Spinner } from '@/components/ui/Spinner'
import { tenKetQuaCheckIn } from '@/lib/format-cde'

/**
 * Thống kê hiệu quả chống thất thoát.
 *
 * Số lượt BỊ CHẶN mới là chỉ số đáng quan tâm — nó cho biết cơ chế kiểm soát
 * đã ngăn được bao nhiêu lượt vào không hợp lệ. Chỉ đếm lượt vào thành công
 * thì không đo được gì.
 */
export function ThongKePage() {
  const [soNgay, setSoNgay] = useState(30)
  const { data: thongKe, isLoading } = useThongKeCheckIn(soNgay)

  if (isLoading) return <Spinner />

  const muc = Object.entries(thongKe ?? {})
  const tong = muc.reduce((s, [, v]) => s + v, 0)
  const choVao = muc.filter(([k]) => k.startsWith('ALLOWED')).reduce((s, [, v]) => s + v, 0)
  const biChan = tong - choVao
  const tyLeChan = tong === 0 ? 0 : Math.round((biChan / tong) * 100)

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between gap-4">
        <h1 className="text-2xl font-bold text-slate-900">Thống kê kiểm soát ra vào</h1>
        <select value={soNgay} onChange={(e) => setSoNgay(Number(e.target.value))}
                className="rounded-lg border border-slate-300 px-3 py-2 text-sm">
          <option value={7}>7 ngày qua</option>
          <option value={30}>30 ngày qua</option>
          <option value={90}>90 ngày qua</option>
        </select>
      </div>

      <div className="grid gap-4 sm:grid-cols-3">
        <O nhan="Tổng lượt quét" giaTri={tong} />
        <O nhan="Được vào" giaTri={choVao} mau="text-emerald-700" />
        <O nhan="Bị chặn" giaTri={biChan} mau="text-red-700" phu={`${tyLeChan}% tổng số lượt`} />
      </div>

      <Card>
        <CardHeader title="Chi tiết theo kết quả"
                    subtitle="Lượt bị chặn được ghi lại đầy đủ, đây là dữ liệu đo hiệu quả kiểm soát" />
        <CardBody className="p-0">
          {muc.length === 0 ? (
            <p className="px-5 py-8 text-center text-sm text-slate-500">
              Chưa có lượt quét nào trong khoảng thời gian này
            </p>
          ) : (
            <ul className="divide-y divide-slate-100">
              {muc.sort((a, b) => b[1] - a[1]).map(([ketQua, soLuong]) => {
                const duocVao = ketQua.startsWith('ALLOWED')
                const phanTram = tong === 0 ? 0 : (soLuong / tong) * 100
                return (
                  <li key={ketQua} className="px-5 py-3">
                    <div className="mb-1.5 flex items-center justify-between text-sm">
                      <span className={duocVao ? 'text-slate-700' : 'font-medium text-red-700'}>
                        {tenKetQuaCheckIn(ketQua)}
                      </span>
                      <span className="font-mono font-semibold">{soLuong}</span>
                    </div>
                    <div className="h-1.5 overflow-hidden rounded-full bg-slate-100">
                      <div className={`h-full rounded-full ${duocVao ? 'bg-emerald-400' : 'bg-red-400'}`}
                           style={{ width: `${phanTram}%` }} />
                    </div>
                  </li>
                )
              })}
            </ul>
          )}
        </CardBody>
      </Card>
    </div>
  )
}

function O({ nhan, giaTri, mau = 'text-slate-900', phu }: {
  nhan: string; giaTri: number; mau?: string; phu?: string
}) {
  return (
    <Card>
      <CardBody>
        <p className="text-sm text-slate-500">{nhan}</p>
        <p className={`mt-1 text-3xl font-bold ${mau}`}>{giaTri}</p>
        {phu && <p className="mt-0.5 text-xs text-slate-400">{phu}</p>}
      </CardBody>
    </Card>
  )
}
