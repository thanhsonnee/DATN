import { useFeedbackAboutMe } from '@/hooks/useFeedback'
import { Card, CardBody, CardHeader } from '@/components/ui/Card'
import { EmptyState, Spinner } from '@/components/ui/Spinner'
import { ngayGio } from '@/lib/format-cde'

/** Huấn luyện viên xem lại đánh giá hội viên gửi về mình — chỉ đọc, không sửa được. */
export function DanhGiaCuaToiPage() {
  const { data: danhGia, isLoading } = useFeedbackAboutMe()

  if (isLoading) return <Spinner />

  const coSao = danhGia?.filter((f) => f.rating != null) ?? []
  const diemTB = coSao.length > 0
    ? (coSao.reduce((tong, f) => tong + (f.rating ?? 0), 0) / coSao.length).toFixed(1)
    : null

  return (
    <div className="space-y-6">
      <h1 className="text-2xl font-bold text-slate-900">Đánh giá về tôi</h1>

      <Card>
        <CardHeader
          title="Điểm trung bình"
          subtitle={`Tính từ ${coSao.length} lượt đánh giá có chấm sao`}
          action={
            <div className="text-right">
              <div className="text-2xl font-bold text-brand-700">{diemTB ?? '—'}</div>
              <div className="text-xs text-slate-500">/ 5 sao</div>
            </div>
          }
        />
      </Card>

      <div className="space-y-3">
        {danhGia?.length === 0 ? (
          <EmptyState title="Chưa có hội viên nào gửi đánh giá" />
        ) : (
          danhGia?.map((f) => (
            <Card key={f.id}>
              <CardBody className="space-y-1.5">
                <div className="flex items-start justify-between gap-3">
                  <p className="font-medium text-slate-900">{f.memberName}</p>
                  <div className="flex items-center gap-2">
                    {f.rating != null && <span className="text-amber-400">{'★'.repeat(f.rating)}{'☆'.repeat(5 - f.rating)}</span>}
                    <span className="text-xs text-slate-500">{ngayGio(f.createdAt)}</span>
                  </div>
                </div>
                <p className="text-sm text-slate-700">{f.description}</p>
              </CardBody>
            </Card>
          ))
        )}
      </div>
    </div>
  )
}
