import { useState } from 'react'
import { useAuth } from '@/stores/auth'
import {
  useCreateEquipment, useEquipmentList, useFeedbackQueue, useUpdateFeedbackStatus,
} from '@/hooks/useFeedback'
import { Button } from '@/components/ui/Button'
import { Input } from '@/components/ui/Input'
import { Alert } from '@/components/ui/Alert'
import { Modal } from '@/components/ui/Modal'
import { Card, CardBody, CardHeader } from '@/components/ui/Card'
import { Badge } from '@/components/ui/Badge'
import { EmptyState, Spinner } from '@/components/ui/Spinner'
import { ApiError } from '@/api/client'
import {
  ngayGio, tenLoaiPhanHoi, tenTrangThaiPhanHoi, tenTrangThaiThietBi,
} from '@/lib/format-cde'
import type { Feedback, FeedbackStatus, FeedbackType } from '@/api/types-cde'

const TIEP_THEO: Record<FeedbackStatus, FeedbackStatus[]> = {
  OPEN: ['IN_PROGRESS', 'CLOSED'],
  IN_PROGRESS: ['WAITING_PARTS', 'RESOLVED', 'CLOSED'],
  WAITING_PARTS: ['IN_PROGRESS', 'RESOLVED', 'CLOSED'],
  RESOLVED: ['CLOSED'],
  CLOSED: [],
}

function badgeTheoTrangThai(status: string) {
  if (status === 'OPEN') return <Badge tone="amber">{tenTrangThaiPhanHoi(status)}</Badge>
  if (status === 'RESOLVED' || status === 'CLOSED') return <Badge tone="green">{tenTrangThaiPhanHoi(status)}</Badge>
  return <Badge tone="blue">{tenTrangThaiPhanHoi(status)}</Badge>
}

export function QuanLyPhanHoiPage() {
  const { user } = useAuth()
  const [locStatus, setLocStatus] = useState<FeedbackStatus | ''>('')
  const [locType, setLocType] = useState<FeedbackType | ''>('')

  const { data: hangDoi, isLoading } = useFeedbackQueue(locStatus || undefined, locType || undefined)
  const { data: equipment } = useEquipmentList()
  const capNhat = useUpdateFeedbackStatus()

  const [dangXuLy, setDangXuLy] = useState<Feedback | null>(null)
  const [trangThaiMoi, setTrangThaiMoi] = useState<FeedbackStatus>('IN_PROGRESS')
  const [ghiChu, setGhiChu] = useState('')
  const [chiPhi, setChiPhi] = useState('')

  const moModal = (f: Feedback, tt: FeedbackStatus) => {
    setDangXuLy(f); setTrangThaiMoi(tt); setGhiChu(''); setChiPhi('')
  }

  const xacNhan = () => {
    if (!dangXuLy) return
    capNhat.mutate({
      id: dangXuLy.id,
      status: trangThaiMoi,
      resolutionNote: ghiChu || undefined,
      repairCost: chiPhi ? Number(chiPhi) : undefined,
    }, { onSuccess: () => setDangXuLy(null) })
  }

  return (
    <div className="space-y-6">
      <h1 className="text-2xl font-bold text-slate-900">Phản hồi & sự cố thiết bị</h1>

      {user?.role === 'ADMIN' && <QuanLyThietBi />}

      <Card>
        <CardHeader title="Danh sách thiết bị"
                    subtitle="Thiết bị NEEDS_REPAIR do hội viên báo hỏng tự động chuyển, không cần chỉnh tay" />
        <CardBody className="p-0">
          {!equipment || equipment.length === 0 ? (
            <p className="px-5 py-6 text-sm text-slate-500">Chưa có thiết bị nào.</p>
          ) : (
            <div className="divide-y divide-slate-100">
              {equipment.map((eq) => (
                <div key={eq.id} className="flex items-center justify-between px-5 py-3">
                  <div>
                    <p className="text-sm font-medium text-slate-900">{eq.name}</p>
                    {eq.roomName && <p className="text-xs text-slate-500">{eq.roomName}</p>}
                  </div>
                  <Badge tone={eq.status === 'ACTIVE' ? 'green' : eq.status === 'NEEDS_REPAIR' ? 'red' : 'amber'}>
                    {tenTrangThaiThietBi(eq.status)}
                  </Badge>
                </div>
              ))}
            </div>
          )}
        </CardBody>
      </Card>

      <Card>
        <CardHeader title="Hàng đợi phản hồi" />
        <CardBody className="space-y-4">
          <div className="flex flex-wrap gap-3">
            <select value={locStatus} onChange={(e) => setLocStatus(e.target.value as FeedbackStatus | '')}
                    className="rounded-lg border border-slate-300 px-3 py-2 text-sm">
              <option value="">Mọi trạng thái</option>
              {(['OPEN', 'IN_PROGRESS', 'WAITING_PARTS', 'RESOLVED', 'CLOSED'] as const).map((s) => (
                <option key={s} value={s}>{tenTrangThaiPhanHoi(s)}</option>
              ))}
            </select>
            <select value={locType} onChange={(e) => setLocType(e.target.value as FeedbackType | '')}
                    className="rounded-lg border border-slate-300 px-3 py-2 text-sm">
              <option value="">Mọi loại</option>
              {(['TRAINER', 'FACILITY', 'HYGIENE', 'SERVICE', 'GENERAL'] as const).map((t) => (
                <option key={t} value={t}>{tenLoaiPhanHoi(t)}</option>
              ))}
            </select>
          </div>

          {isLoading ? <Spinner /> : hangDoi?.length === 0 ? (
            <EmptyState title="Không có phản hồi nào khớp bộ lọc" />
          ) : (
            <div className="space-y-3">
              {hangDoi?.map((f) => (
                <div key={f.id}
                     className={`rounded-lg border p-4 ${f.urgent ? 'border-red-300 bg-red-50/50' : 'border-slate-200'}`}>
                  <div className="flex items-start justify-between gap-3">
                    <div>
                      <div className="flex items-center gap-2">
                        <p className="font-medium text-slate-900">{tenLoaiPhanHoi(f.feedbackType)}</p>
                        {f.urgent && <Badge tone="red">Khẩn — cần xử lý trong 24h</Badge>}
                      </div>
                      <p className="text-xs text-slate-500">
                        {f.memberName} · {ngayGio(f.createdAt)}
                      </p>
                    </div>
                    {badgeTheoTrangThai(f.status)}
                  </div>

                  {f.trainerName && <p className="mt-2 text-sm text-slate-600">HLV: {f.trainerName}{f.rating ? ` · ${f.rating}★` : ''}</p>}
                  {f.equipmentName && <p className="mt-2 text-sm text-slate-600">Thiết bị: {f.equipmentName}</p>}
                  <p className="mt-1 text-sm text-slate-700">{f.description}</p>
                  {f.resolutionNote && (
                    <div className="mt-2 rounded-lg bg-slate-50 px-3 py-2 text-sm text-slate-600">
                      {f.resolutionNote}
                      {f.repairCost != null && ` — chi phí sửa: ${f.repairCost.toLocaleString('vi-VN')}đ`}
                    </div>
                  )}

                  {TIEP_THEO[f.status].length > 0 && (
                    <div className="mt-3 flex flex-wrap gap-2">
                      {TIEP_THEO[f.status].map((tt) => (
                        <Button key={tt} variant={tt === 'CLOSED' ? 'secondary' : 'primary'}
                                onClick={() => moModal(f, tt)}>
                          {tenTrangThaiPhanHoi(tt)}
                        </Button>
                      ))}
                    </div>
                  )}
                </div>
              ))}
            </div>
          )}
        </CardBody>
      </Card>

      <Modal open={!!dangXuLy}
             title={`Chuyển sang "${tenTrangThaiPhanHoi(trangThaiMoi)}"`}
             onClose={() => setDangXuLy(null)}>
        <div className="space-y-4">
          <Input label="Ghi chú xử lý (không bắt buộc)" value={ghiChu}
                 onChange={(e) => setGhiChu(e.target.value)}
                 placeholder="Đã thay dây curoa máy chạy bộ" />

          {dangXuLy?.feedbackType === 'FACILITY' && (trangThaiMoi === 'RESOLVED' || trangThaiMoi === 'CLOSED') && (
            <Input label="Chi phí sửa chữa (nếu có)" type="number" min="0" value={chiPhi}
                   onChange={(e) => setChiPhi(e.target.value)}
                   hint="Có giá trị sẽ tự sinh 1 dòng chi phí EQUIPMENT_MAINTENANCE" />
          )}

          {capNhat.error instanceof ApiError && <Alert tone="error">{capNhat.error.message}</Alert>}

          <div className="flex justify-end gap-2">
            <Button variant="secondary" onClick={() => setDangXuLy(null)}>Đóng</Button>
            <Button loading={capNhat.isPending} onClick={xacNhan}>Xác nhận</Button>
          </div>
        </div>
      </Modal>
    </div>
  )
}

function QuanLyThietBi() {
  const taoThietBi = useCreateEquipment()
  const [ten, setTen] = useState('')
  const [phong, setPhong] = useState('')

  const gui = () => {
    taoThietBi.mutate({ name: ten, roomName: phong || undefined }, {
      onSuccess: () => { setTen(''); setPhong('') },
    })
  }

  return (
    <Card>
      <CardHeader title="Thêm thiết bị" subtitle="Chỉ Admin thấy mục này" />
      <CardBody className="space-y-3">
        <div className="grid gap-3 sm:grid-cols-2">
          <Input label="Tên thiết bị" value={ten} onChange={(e) => setTen(e.target.value)}
                 placeholder="Máy chạy bộ số 3" />
          <Input label="Khu/phòng (không bắt buộc)" value={phong} onChange={(e) => setPhong(e.target.value)}
                 placeholder="Khu cardio" />
        </div>
        {taoThietBi.error instanceof ApiError && <Alert tone="error">{taoThietBi.error.message}</Alert>}
        <div className="flex justify-end">
          <Button loading={taoThietBi.isPending} disabled={!ten.trim()} onClick={gui}>Thêm thiết bị</Button>
        </div>
      </CardBody>
    </Card>
  )
}
