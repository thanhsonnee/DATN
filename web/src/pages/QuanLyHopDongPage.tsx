import { useState } from 'react'
import {
  useExpiringRegistrations, usePendingFreezeRegistrations, useDuyetBaoLuu,
} from '@/hooks/useRegistrations'
import { Card, CardBody, CardHeader } from '@/components/ui/Card'
import { Badge } from '@/components/ui/Badge'
import { Button } from '@/components/ui/Button'
import { Alert } from '@/components/ui/Alert'
import { EmptyState, Spinner } from '@/components/ui/Spinner'
import { ApiError } from '@/api/client'
import { ngay, soNgayConLai, tien } from '@/lib/format'

/**
 * Màn hình quản lý hợp đồng cho nhân viên.
 *
 * Toàn bộ thao tác đều là BẤM TỪ DANH SÁCH — không có ô nào bắt gõ tay mã số.
 * Kích hoạt hợp đồng đi qua trang Thu ngân (đúng luồng: xuất hóa đơn → thu tiền
 * → tự kích hoạt), trang này chỉ còn việc duyệt bảo lưu và xem hợp đồng sắp hết hạn.
 */
export function QuanLyHopDongPage() {
  const [soNgay, setSoNgay] = useState(14)
  const { data: sapHetHan, isLoading } = useExpiringRegistrations(soNgay)

  return (
    <div className="space-y-6">
      <h1 className="text-2xl font-bold text-slate-900">Quản lý hợp đồng</h1>

      <ChoDuyetBaoLuu />

      <Card>
        <CardHeader
          title="Hợp đồng sắp hết hạn"
          subtitle="Danh sách để gọi mời gia hạn"
          action={
            <select
              value={soNgay}
              onChange={(e) => setSoNgay(Number(e.target.value))}
              className="rounded-lg border border-slate-300 px-3 py-1.5 text-sm"
            >
              <option value={7}>Trong 7 ngày</option>
              <option value={14}>Trong 14 ngày</option>
              <option value={30}>Trong 30 ngày</option>
            </select>
          }
        />
        <CardBody className="p-0">
          {isLoading ? (
            <Spinner />
          ) : sapHetHan?.length === 0 ? (
            <EmptyState title="Không có hợp đồng nào sắp hết hạn"
                        hint={`Trong ${soNgay} ngày tới`} />
          ) : (
            <div className="overflow-x-auto">
              <table className="w-full text-sm">
                <thead className="border-b border-slate-100 bg-slate-50 text-left text-xs uppercase text-slate-500">
                  <tr>
                    <th className="px-5 py-3">Mã hợp đồng</th>
                    <th className="px-5 py-3">Hội viên</th>
                    <th className="px-5 py-3">Gói tập</th>
                    <th className="px-5 py-3">Hết hạn</th>
                    <th className="px-5 py-3">Còn lại</th>
                    <th className="px-5 py-3 text-right">Giá trị</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-100">
                  {sapHetHan?.map((hd) => {
                    const conLai = soNgayConLai(hd.endDate)
                    return (
                      <tr key={hd.id} className="hover:bg-slate-50">
                        <td className="px-5 py-3 font-mono text-xs">{hd.registrationCode}</td>
                        <td className="px-5 py-3">
                          <div className="font-medium text-slate-800">{hd.memberName}</div>
                          <div className="font-mono text-xs text-slate-400">{hd.memberCode}</div>
                        </td>
                        <td className="px-5 py-3">{hd.membershipName}</td>
                        <td className="px-5 py-3">{ngay(hd.endDate)}</td>
                        <td className="px-5 py-3">
                          <Badge tone={conLai !== null && conLai <= 7 ? 'red' : 'amber'}>
                            {conLai} ngày
                          </Badge>
                        </td>
                        <td className="px-5 py-3 text-right font-medium">{tien(hd.finalPrice)}</td>
                      </tr>
                    )
                  })}
                </tbody>
              </table>
            </div>
          )}
        </CardBody>
      </Card>
    </div>
  )
}

/** Yêu cầu bảo lưu đang chờ duyệt — bấm thẳng từ danh sách, không cần biết mã số. */
function ChoDuyetBaoLuu() {
  const { data: dsChoDuyet, isLoading } = usePendingFreezeRegistrations()
  const duyet = useDuyetBaoLuu()
  const [loi, setLoi] = useState<string | null>(null)

  const xuLy = (id: number, approved: boolean) => {
    setLoi(null)
    duyet.mutate({ id, approved, reason: approved ? undefined : 'Không đủ điều kiện bảo lưu' }, {
      onError: (e) => setLoi(e instanceof ApiError ? e.message : 'Có lỗi xảy ra'),
    })
  }

  return (
    <Card>
      <CardHeader
        title="Chờ duyệt bảo lưu"
        subtitle="Hội viên đã gửi yêu cầu, đang chờ nhân viên quyết định"
        action={<Badge tone="amber">{dsChoDuyet?.length ?? 0}</Badge>}
      />
      <CardBody className="p-0">
        {isLoading ? (
          <Spinner />
        ) : dsChoDuyet?.length === 0 ? (
          <EmptyState title="Không có yêu cầu bảo lưu nào đang chờ" />
        ) : (
          <ul className="divide-y divide-slate-100">
            {dsChoDuyet?.map((hd) => (
              <li key={hd.id} className="flex items-center justify-between gap-3 px-5 py-3">
                <div>
                  <p className="font-medium text-slate-800">{hd.memberName}</p>
                  <p className="text-xs text-slate-500">
                    <span className="font-mono">{hd.memberCode}</span> · {hd.membershipName}
                  </p>
                  {hd.freeze && (
                    <p className="mt-0.5 text-xs text-slate-500">
                      {ngay(hd.freeze.fromDate)} → {ngay(hd.freeze.toDate)}
                      {hd.freeze.days && ` (${hd.freeze.days} ngày)`} · {hd.freeze.reason}
                    </p>
                  )}
                </div>
                <div className="flex shrink-0 gap-2">
                  <Button variant="secondary" loading={duyet.isPending}
                          onClick={() => xuLy(hd.id, false)}>
                    Từ chối
                  </Button>
                  <Button loading={duyet.isPending} onClick={() => xuLy(hd.id, true)}>
                    Duyệt
                  </Button>
                </div>
              </li>
            ))}
          </ul>
        )}

        {loi && <div className="px-5 pb-4"><Alert tone="error">{loi}</Alert></div>}
      </CardBody>
    </Card>
  )
}
