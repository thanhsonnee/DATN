import { useState } from 'react'
import {
  useEquipmentList, useMyFeedback, useSubmitFeedback, useUpdateFeedback,
} from '@/hooks/useFeedback'
import { useTrainers } from '@/hooks/useLookup'
import { Button } from '@/components/ui/Button'
import { Alert } from '@/components/ui/Alert'
import { Card, CardBody, CardHeader } from '@/components/ui/Card'
import { Badge } from '@/components/ui/Badge'
import { Modal } from '@/components/ui/Modal'
import { EmptyState, Spinner } from '@/components/ui/Spinner'
import { ApiError } from '@/api/client'
import { ngayGio, tenLoaiPhanHoi, tenTrangThaiPhanHoi } from '@/lib/format-cde'
import type { Feedback, FeedbackType } from '@/api/types-cde'

const LOAI: { value: FeedbackType; nhan: string }[] = [
  { value: 'TRAINER', nhan: 'Đánh giá huấn luyện viên' },
  { value: 'FACILITY', nhan: 'Báo hỏng thiết bị' },
  { value: 'HYGIENE', nhan: 'Vệ sinh' },
  { value: 'SERVICE', nhan: 'Chất lượng dịch vụ' },
  { value: 'GENERAL', nhan: 'Góp ý chung' },
]

function badgeTheoTrangThai(status: string) {
  if (status === 'OPEN') return <Badge tone="amber">{tenTrangThaiPhanHoi(status)}</Badge>
  if (status === 'RESOLVED' || status === 'CLOSED') return <Badge tone="green">{tenTrangThaiPhanHoi(status)}</Badge>
  return <Badge tone="blue">{tenTrangThaiPhanHoi(status)}</Badge>
}

export function PhanHoiPage() {
  const { data: lichSu, isLoading } = useMyFeedback()
  const { data: trainers } = useTrainers()
  const { data: equipment } = useEquipmentList()
  const guiPhanHoi = useSubmitFeedback()
  const suaPhanHoi = useUpdateFeedback()

  const [loai, setLoai] = useState<FeedbackType>('GENERAL')
  const [trainerId, setTrainerId] = useState<number | ''>('')
  const [equipmentId, setEquipmentId] = useState<number | ''>('')
  const [rating, setRating] = useState<number>(0)
  const [moTa, setMoTa] = useState('')

  const reset = () => {
    setLoai('GENERAL'); setTrainerId(''); setEquipmentId(''); setRating(0); setMoTa('')
  }

  const gui = () => {
    guiPhanHoi.mutate({
      feedbackType: loai,
      trainerId: loai === 'TRAINER' && trainerId !== '' ? trainerId : undefined,
      equipmentId: loai === 'FACILITY' && equipmentId !== '' ? equipmentId : undefined,
      rating: loai === 'TRAINER' && rating > 0 ? rating : undefined,
      description: moTa,
    }, { onSuccess: reset })
  }

  const hopLe = moTa.trim().length > 0
    && (loai !== 'TRAINER' || trainerId !== '')
    && (loai !== 'FACILITY' || equipmentId !== '')

  const [dangSua, setDangSua] = useState<Feedback | null>(null)
  const [moTaSua, setMoTaSua] = useState('')
  const [ratingSua, setRatingSua] = useState(0)

  const moSua = (f: Feedback) => {
    setDangSua(f); setMoTaSua(f.description); setRatingSua(f.rating ?? 0)
  }

  const luuSua = () => {
    if (!dangSua) return
    suaPhanHoi.mutate({
      id: dangSua.id,
      description: moTaSua,
      rating: dangSua.feedbackType === 'TRAINER' && ratingSua > 0 ? ratingSua : undefined,
    }, { onSuccess: () => setDangSua(null) })
  }

  return (
    <div className="space-y-6">
      <h1 className="text-2xl font-bold text-slate-900">Phản hồi & góp ý</h1>

      <Card>
        <CardHeader title="Gửi phản hồi mới"
                    subtitle="Đánh giá PT, báo thiết bị hỏng, hoặc góp ý chung — hệ thống xử lý ngay khi gửi" />
        <CardBody className="space-y-4">
          <div>
            <label className="mb-1.5 block text-sm font-medium text-slate-700">Loại phản hồi</label>
            <div className="grid gap-2 sm:grid-cols-3">
              {LOAI.map((l) => (
                <button key={l.value} type="button"
                        onClick={() => setLoai(l.value)}
                        className={`rounded-lg border px-3 py-2.5 text-left text-sm transition
                          ${loai === l.value
                            ? 'border-brand-500 bg-brand-50 ring-1 ring-brand-500 font-medium text-brand-700'
                            : 'border-slate-200 hover:border-slate-300'}`}>
                  {l.nhan}
                </button>
              ))}
            </div>
          </div>

          {loai === 'TRAINER' && (
            <div className="space-y-3">
              <div>
                <label className="mb-1.5 block text-sm font-medium text-slate-700">Huấn luyện viên</label>
                <select value={trainerId}
                        onChange={(e) => setTrainerId(e.target.value ? Number(e.target.value) : '')}
                        className="w-full rounded-lg border border-slate-300 px-3 py-2 text-sm">
                  <option value="">— Chọn huấn luyện viên —</option>
                  {trainers?.map((t) => <option key={t.id} value={t.id}>{t.fullName}</option>)}
                </select>
              </div>
              <div>
                <label className="mb-1.5 block text-sm font-medium text-slate-700">Đánh giá (không bắt buộc)</label>
                <div className="flex gap-1">
                  {[1, 2, 3, 4, 5].map((sao) => (
                    <button key={sao} type="button" onClick={() => setRating(sao === rating ? 0 : sao)}
                            className={`text-2xl transition ${sao <= rating ? 'text-amber-400' : 'text-slate-200'}`}>
                      ★
                    </button>
                  ))}
                </div>
              </div>
            </div>
          )}

          {loai === 'FACILITY' && (
            <div>
              <label className="mb-1.5 block text-sm font-medium text-slate-700">Thiết bị bị lỗi</label>
              <select value={equipmentId}
                      onChange={(e) => setEquipmentId(e.target.value ? Number(e.target.value) : '')}
                      className="w-full rounded-lg border border-slate-300 px-3 py-2 text-sm">
                <option value="">— Chọn thiết bị —</option>
                {equipment?.map((eq) => (
                  <option key={eq.id} value={eq.id}>
                    {eq.name}{eq.roomName ? ` · ${eq.roomName}` : ''}
                  </option>
                ))}
              </select>
            </div>
          )}

          <div>
            <label className="mb-1.5 block text-sm font-medium text-slate-700">Nội dung</label>
            <textarea value={moTa} onChange={(e) => setMoTa(e.target.value)} rows={3}
                      placeholder="Mô tả chi tiết…"
                      className="w-full rounded-lg border border-slate-300 px-3 py-2 text-sm outline-none
                                 focus:border-brand-500 focus:ring-2 focus:ring-brand-500/30" />
          </div>

          {guiPhanHoi.error instanceof ApiError && <Alert tone="error">{guiPhanHoi.error.message}</Alert>}
          {guiPhanHoi.isSuccess && <Alert tone="success">Đã gửi phản hồi, cảm ơn bạn!</Alert>}

          <div className="flex justify-end">
            <Button onClick={gui} loading={guiPhanHoi.isPending} disabled={!hopLe}>Gửi phản hồi</Button>
          </div>
        </CardBody>
      </Card>

      <div className="space-y-3">
        <h2 className="font-semibold text-slate-800">Phản hồi đã gửi</h2>
        {isLoading ? <Spinner /> : lichSu?.length === 0 ? (
          <EmptyState title="Chưa gửi phản hồi nào" />
        ) : (
          lichSu?.map((f) => (
            <Card key={f.id}>
              <CardBody className="space-y-2">
                <div className="flex items-start justify-between gap-3">
                  <div>
                    <p className="font-medium text-slate-900">{tenLoaiPhanHoi(f.feedbackType)}</p>
                    <p className="text-xs text-slate-500">{ngayGio(f.createdAt)}</p>
                  </div>
                  {badgeTheoTrangThai(f.status)}
                </div>
                {f.trainerName && <p className="text-sm text-slate-600">HLV: {f.trainerName}{f.rating ? ` · ${f.rating}★` : ''}</p>}
                {f.equipmentName && <p className="text-sm text-slate-600">Thiết bị: {f.equipmentName}</p>}
                <p className="text-sm text-slate-700">{f.description}</p>
                {f.resolutionNote && (
                  <div className="rounded-lg bg-slate-50 px-3 py-2 text-sm text-slate-600">
                    <span className="font-medium">Phản hồi từ phòng gym: </span>{f.resolutionNote}
                  </div>
                )}
                <div className="flex justify-end">
                  <Button variant="secondary" onClick={() => moSua(f)}>Sửa</Button>
                </div>
              </CardBody>
            </Card>
          ))
        )}
      </div>

      <Modal open={!!dangSua} title="Sửa phản hồi" onClose={() => setDangSua(null)}>
        <div className="space-y-4">
          {dangSua?.feedbackType === 'TRAINER' && (
            <div>
              <label className="mb-1.5 block text-sm font-medium text-slate-700">
                Đánh giá lại HLV {dangSua.trainerName}
              </label>
              <div className="flex gap-1">
                {[1, 2, 3, 4, 5].map((sao) => (
                  <button key={sao} type="button" onClick={() => setRatingSua(sao === ratingSua ? 0 : sao)}
                          className={`text-2xl transition ${sao <= ratingSua ? 'text-amber-400' : 'text-slate-200'}`}>
                    ★
                  </button>
                ))}
              </div>
              <p className="mt-1 text-xs text-slate-500">Thấy huấn luyện viên đã cải thiện? Chấm lại sao ở đây.</p>
            </div>
          )}

          <div>
            <label className="mb-1.5 block text-sm font-medium text-slate-700">Nội dung</label>
            <textarea value={moTaSua} onChange={(e) => setMoTaSua(e.target.value)} rows={3}
                      className="w-full rounded-lg border border-slate-300 px-3 py-2 text-sm outline-none
                                 focus:border-brand-500 focus:ring-2 focus:ring-brand-500/30" />
          </div>

          {suaPhanHoi.error instanceof ApiError && <Alert tone="error">{suaPhanHoi.error.message}</Alert>}

          <div className="flex justify-end gap-2">
            <Button variant="secondary" onClick={() => setDangSua(null)}>Đóng</Button>
            <Button loading={suaPhanHoi.isPending} disabled={!moTaSua.trim()} onClick={luuSua}>
              Lưu thay đổi
            </Button>
          </div>
        </div>
      </Modal>
    </div>
  )
}
