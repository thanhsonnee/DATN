import { useState } from 'react'
import { useAuth } from '@/stores/auth'
import {
  useCreateEquipment, useEquipmentList, useFeedbackQueue, useUpdateFeedbackStatus,
  useUpdateEquipment, useDeleteEquipment,
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
import type { Equipment, Feedback, FeedbackStatus, FeedbackType } from '@/api/types-cde'

// Khi chưa lọc theo trạng thái cụ thể, chỉ RESOLVED/CLOSED mới gộp vào lịch
// sử thu gọn — các trạng thái còn lại đều còn cần xử lý, luôn hiện đầy đủ.
const TRANG_THAI_DA_DONG = new Set<FeedbackStatus>(['RESOLVED', 'CLOSED'])

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
  const deleteThietBi = useDeleteEquipment()
  const [moDaDong, setMoDaDong] = useState(false)
  const [dangSuaThietBi, setDangSuaThietBi] = useState<Equipment | null>(null)

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

  // Chỉ tách khối khi chưa lọc theo trạng thái cụ thể — đã tự chọn lọc rồi
  // thì hiện đúng kết quả đã lọc, không cần tách thêm.
  const dangCanXuLy = !locStatus ? (hangDoi?.filter((f) => !TRANG_THAI_DA_DONG.has(f.status)) ?? []) : hangDoi ?? []
  const daXongHet = !locStatus ? (hangDoi?.filter((f) => TRANG_THAI_DA_DONG.has(f.status)) ?? []) : []

  const theCardPhanHoi = (f: Feedback) => (
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
  )

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
                  <div className="flex items-center gap-2">
                    <Badge tone={eq.status === 'ACTIVE' ? 'green' : eq.status === 'NEEDS_REPAIR' ? 'red' : 'amber'}>
                      {tenTrangThaiThietBi(eq.status)}
                    </Badge>
                    {user?.role === 'ADMIN' && (
                      <>
                        <button
                          onClick={() => setDangSuaThietBi(eq)}
                          className="text-xs text-slate-600 hover:text-slate-900 font-medium"
                        >
                          Sửa
                        </button>
                        <button
                          onClick={() => {
                            if (window.confirm(`Xóa thiết bị "${eq.name}"? Không thể hoàn tác.`)) {
                              deleteThietBi.mutate(eq.id)
                            }
                          }}
                          className="text-xs text-red-600 hover:text-red-800 font-medium"
                        >
                          Xóa
                        </button>
                      </>
                    )}
                  </div>
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
            <div className="space-y-3">{dangCanXuLy.map(theCardPhanHoi)}</div>
          )}
        </CardBody>

        {!locStatus && daXongHet.length > 0 && (
          <div className="border-t border-slate-100 px-5 py-4">
            <button
              onClick={() => setMoDaDong((v) => !v)}
              className="text-sm font-medium text-slate-600 hover:text-slate-900"
            >
              {moDaDong ? '▾' : '▸'} Đã xử lý xong ({daXongHet.length})
            </button>

            {moDaDong && <div className="mt-4 space-y-3">{daXongHet.map(theCardPhanHoi)}</div>}
          </div>
        )}
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

      {dangSuaThietBi && (
        <HopThoaiSuaThietBi thietBi={dangSuaThietBi} onClose={() => setDangSuaThietBi(null)} />
      )}
    </div>
  )
}

function HopThoaiSuaThietBi({ thietBi, onClose }: { thietBi: Equipment; onClose: () => void }) {
  const suaThietBi = useUpdateEquipment()
  const [ten, setTen] = useState(thietBi.name)
  const [phong, setPhong] = useState(thietBi.roomName ?? '')
  const [status, setStatus] = useState(thietBi.status)
  const [ghiChu, setGhiChu] = useState(thietBi.note ?? '')

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault()
    if (!ten.trim()) return

    suaThietBi.mutate(
      {
        id: thietBi.id,
        name: ten.trim(),
        roomName: phong.trim() || undefined,
        status,
        note: ghiChu.trim() || undefined,
      },
      { onSuccess: onClose }
    )
  }

  return (
    <Modal open title={`Sửa thiết bị: ${thietBi.name}`} onClose={onClose}>
      <form onSubmit={handleSubmit} className="space-y-4">
        {suaThietBi.error instanceof ApiError && <Alert tone="error">{suaThietBi.error.message}</Alert>}

        <Input label="Tên thiết bị *" value={ten} onChange={(e) => setTen(e.target.value)} required />
        <Input label="Khu/phòng" value={phong} onChange={(e) => setPhong(e.target.value)} />

        <div>
          <label className="mb-1 block text-sm font-medium text-slate-700">Trạng thái</label>
          <select value={status} onChange={(e) => setStatus(e.target.value as Equipment['status'])}
                  className="w-full rounded-lg border border-slate-300 px-3 py-2 text-sm">
            {(['ACTIVE', 'NEEDS_REPAIR', 'UNDER_REPAIR', 'RETIRED'] as const).map((s) => (
              <option key={s} value={s}>{tenTrangThaiThietBi(s)}</option>
            ))}
          </select>
          <p className="mt-1 text-xs text-slate-500">
            Bình thường NEEDS_REPAIR tự chuyển khi có phản hồi báo hỏng — chỉ đổi tay khi cần (vd. RETIRED khi thanh lý).
          </p>
        </div>

        <Input label="Ghi chú" value={ghiChu} onChange={(e) => setGhiChu(e.target.value)} />

        <div className="flex justify-end gap-2 pt-2">
          <Button type="button" variant="secondary" onClick={onClose}>Hủy</Button>
          <Button type="submit" loading={suaThietBi.isPending}>Lưu</Button>
        </div>
      </form>
    </Modal>
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
