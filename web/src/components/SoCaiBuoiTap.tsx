import { useSoCai } from '@/hooks/usePtSessions'
import { Card, CardBody, CardHeader } from '@/components/ui/Card'
import { Badge } from '@/components/ui/Badge'
import { Alert } from '@/components/ui/Alert'
import { Spinner } from '@/components/ui/Spinner'
import { ngayGio, tenButToan, tenNguonButToan } from '@/lib/format-cde'

/**
 * Sổ cái buổi tập của một hợp đồng.
 *
 * Hiển thị CẢ số dư lẫn toàn bộ lịch sử, để hội viên không chỉ biết còn mấy buổi
 * mà còn giải thích được vì sao còn ngần ấy — đây là điểm chính của cơ chế sổ cái.
 */
export function SoCaiBuoiTap({ registrationId }: { registrationId: number }) {
  const { data: soCai, isLoading } = useSoCai(registrationId)

  if (isLoading) return <Spinner label="Đang tải sổ cái…" />
  if (!soCai) return null

  return (
    <Card>
      <CardHeader
        title="Sổ cái buổi tập"
        subtitle="Mọi biến động số buổi đều được ghi lại, không sửa không xóa"
        action={
          <div className="text-right">
            <div className="text-2xl font-bold text-brand-700">{soCai.soDuHienTai}</div>
            <div className="text-xs text-slate-500">buổi còn lại</div>
          </div>
        }
      />
      <CardBody className="p-0">
        {/* Đối chiếu chéo: tổng cộng dồn phải khớp số dư cuối */}
        {!soCai.batBienConDung && (
          <div className="p-4">
            <Alert tone="error">
              Sổ cái sai lệch: tổng các bút toán không khớp số dư cuối. Cần kiểm tra ngay.
            </Alert>
          </div>
        )}

        {soCai.lichSu.length === 0 ? (
          <p className="px-5 py-8 text-center text-sm text-slate-500">
            Chưa có biến động nào. Sổ cái được cấp buổi khi hợp đồng kích hoạt.
          </p>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead className="border-b border-slate-100 bg-slate-50 text-left text-xs uppercase text-slate-500">
                <tr>
                  <th className="px-5 py-3">Thời điểm</th>
                  <th className="px-5 py-3">Loại</th>
                  <th className="px-5 py-3 text-right">Biến động</th>
                  <th className="px-5 py-3 text-right">Còn lại</th>
                  <th className="px-5 py-3">Nguồn gốc</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {soCai.lichSu.map((e) => (
                  <tr key={e.id} className="hover:bg-slate-50">
                    <td className="px-5 py-3 text-slate-500">{ngayGio(e.createdAt)}</td>
                    <td className="px-5 py-3">
                      <Badge tone={e.delta > 0 ? 'green' : 'amber'}>{tenButToan(e.entryType)}</Badge>
                    </td>
                    <td className={`px-5 py-3 text-right font-mono font-semibold
                                    ${e.delta > 0 ? 'text-emerald-700' : 'text-amber-700'}`}>
                      {e.delta > 0 ? '+' : ''}{e.delta}
                    </td>
                    <td className="px-5 py-3 text-right font-mono font-semibold">{e.balanceAfter}</td>
                    <td className="px-5 py-3 text-xs text-slate-500">
                      {tenNguonButToan(e.sourceType)}
                      {e.sourceId && <span className="ml-1 font-mono">#{e.sourceId}</span>}
                      {e.reason && <div className="mt-0.5 text-slate-400">{e.reason}</div>}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </CardBody>
    </Card>
  )
}
