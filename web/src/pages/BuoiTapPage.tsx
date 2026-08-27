import { useState } from 'react'
import { Link } from 'react-router-dom'
import {
  useDatLich, useHoiVienXacNhan, useHuyBuoi, useMyPtSessions,
} from '@/hooks/usePtSessions'
import { useMyRegistrations } from '@/hooks/useRegistrations'
import { useTrainers } from '@/hooks/useLookup'
import { TheBuoiTap } from '@/components/TheBuoiTap'
import { SoCaiBuoiTap } from '@/components/SoCaiBuoiTap'
import { Button } from '@/components/ui/Button'
import { Input } from '@/components/ui/Input'
import { Alert } from '@/components/ui/Alert'
import { Modal } from '@/components/ui/Modal'
import { EmptyState, Spinner } from '@/components/ui/Spinner'
import { ApiError } from '@/api/client'

/** Buổi tập của hội viên: xem lịch, đặt lịch mới, xác nhận đã tập. */
export function BuoiTapPage() {
  const { data: buoiTaps, isLoading } = useMyPtSessions()
  const { data: hopDongs } = useMyRegistrations()
  const xacNhan = useHoiVienXacNhan()
  const huyBuoi = useHuyBuoi()

  const [moDatLich, setMoDatLich] = useState(false)
  const [huyBuoiId, setHuyBuoiId] = useState<number | null>(null)
  const [lyDoHuy, setLyDoHuy] = useState('')

  // Chỉ hợp đồng còn hiệu lực và có buổi PT mới đặt lịch được
  const hopDongCoPt = hopDongs?.filter(
    (h) => h.status === 'ACTIVE' && h.sessionsTotal != null && h.sessionsTotal > 0,
  ) ?? []

  if (isLoading) return <Spinner />

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between gap-4">
        <h1 className="text-2xl font-bold text-slate-900">Buổi tập với huấn luyện viên</h1>
        {hopDongCoPt.length > 0 && (
          <Button onClick={() => setMoDatLich(true)}>Đặt lịch mới</Button>
        )}
      </div>

      {hopDongCoPt.length === 0 && (
        <Alert tone="info">
          Bạn chưa có gói tập nào kèm buổi PT.{' '}
          <Link to="/" className="font-medium underline">Xem bảng giá</Link>
        </Alert>
      )}

      {/* Sổ cái của từng hợp đồng có buổi PT */}
      {hopDongCoPt.map((h) => (
        <SoCaiBuoiTap key={h.id} registrationId={h.id} />
      ))}

      <div className="space-y-4">
        <h2 className="font-semibold text-slate-800">Lịch sử buổi tập</h2>

        {buoiTaps?.length === 0 && <EmptyState title="Chưa có buổi tập nào" />}

        {buoiTaps?.map((b) => (
          <TheBuoiTap
            key={b.id} buoi={b} doiTac={b.trainerName}
            actions={
              <>
                {/* Chỉ xác nhận được SAU KHI huấn luyện viên đã bấm kết thúc */}
                {b.status === 'SCHEDULED' && b.trainerConfirmedAt && !b.memberConfirmedAt && (
                  <Button loading={xacNhan.isPending} onClick={() => xacNhan.mutate(b.id)}>
                    Xác nhận đã tập
                  </Button>
                )}
                {b.status === 'SCHEDULED' && !b.trainerConfirmedAt && (
                  <span className="self-center text-xs text-slate-500">
                    Chờ huấn luyện viên bấm kết thúc buổi tập
                  </span>
                )}
                {(b.status === 'PENDING_TRAINER' || b.status === 'SCHEDULED') && (
                  <Button variant="secondary" onClick={() => setHuyBuoiId(b.id)}>Hủy buổi</Button>
                )}
              </>
            }
          />
        ))}
      </div>

      {xacNhan.error instanceof ApiError && (
        <Alert tone="error">{xacNhan.error.message}</Alert>
      )}

      <HopThoaiDatLich
        open={moDatLich} onClose={() => setMoDatLich(false)}
        hopDongs={hopDongCoPt.map((h) => ({ id: h.id, ten: h.membershipName }))}
      />

      <Modal open={huyBuoiId != null} title="Hủy buổi tập" onClose={() => setHuyBuoiId(null)}>
        <div className="space-y-4">
          <Alert tone="info">
            Hủy trước giờ hẹn ít nhất 4 tiếng thì buổi được hoàn lại. Hủy sát giờ sẽ mất buổi.
          </Alert>
          <Input label="Lý do hủy" value={lyDoHuy}
                 onChange={(e) => setLyDoHuy(e.target.value)}
                 placeholder="Bận đột xuất" />
          {huyBuoi.error instanceof ApiError && <Alert tone="error">{huyBuoi.error.message}</Alert>}
          <div className="flex justify-end gap-2">
            <Button variant="secondary" onClick={() => setHuyBuoiId(null)}>Đóng</Button>
            <Button variant="danger" loading={huyBuoi.isPending}
                    onClick={() => huyBuoiId && huyBuoi.mutate(
                      { id: huyBuoiId, cancelledBy: 'MEMBER', reason: lyDoHuy },
                      { onSuccess: () => { setHuyBuoiId(null); setLyDoHuy('') } })}>
              Xác nhận hủy
            </Button>
          </div>
        </div>
      </Modal>
    </div>
  )
}

function HopThoaiDatLich({ open, onClose, hopDongs }: {
  open: boolean
  onClose: () => void
  hopDongs: { id: number; ten: string }[]
}) {
  const datLich = useDatLich()
  const { data: trainers, isLoading: dangTaiTrainers } = useTrainers()
  const [form, setForm] = useState({
    registrationId: 0, trainerId: 0, ngay: '', gio: '19:00', roomName: 'Khu tạ tự do',
  })

  const gui = () => {
    const batDau = new Date(`${form.ngay}T${form.gio}:00`)
    const ketThuc = new Date(batDau.getTime() + 60 * 60 * 1000)

    datLich.mutate({
      registrationId: form.registrationId || hopDongs[0]?.id,
      trainerId: form.trainerId,
      scheduledStart: batDau.toISOString(),
      scheduledEnd: ketThuc.toISOString(),
      roomName: form.roomName,
    }, { onSuccess: onClose })
  }

  return (
    <Modal open={open} title="Đặt lịch tập" onClose={onClose}>
      <div className="space-y-4">
        <Alert tone="info">
          Buổi tập sẽ ở trạng thái chờ huấn luyện viên duyệt.
          Số buổi <b>chưa bị trừ</b> — chỉ trừ khi buổi thực sự hoàn thành.
        </Alert>

        {hopDongs.length > 1 && (
          <div>
            <label className="mb-1 block text-sm font-medium text-slate-700">Dùng gói</label>
            <select
              value={form.registrationId || hopDongs[0]?.id}
              onChange={(e) => setForm({ ...form, registrationId: Number(e.target.value) })}
              className="w-full rounded-lg border border-slate-300 px-3 py-2 text-sm"
            >
              {hopDongs.map((h) => <option key={h.id} value={h.id}>{h.ten}</option>)}
            </select>
          </div>
        )}

        <div>
          <label className="mb-1.5 block text-sm font-medium text-slate-700">Chọn huấn luyện viên</label>
          {dangTaiTrainers ? (
            <p className="text-sm text-slate-400">Đang tải danh sách…</p>
          ) : (
            <div className="grid gap-2 sm:grid-cols-2">
              {trainers?.map((tr) => (
                <button
                  key={tr.id} type="button"
                  onClick={() => setForm({ ...form, trainerId: tr.id })}
                  className={`rounded-lg border px-3 py-2.5 text-left text-sm transition
                    ${form.trainerId === tr.id
                      ? 'border-brand-500 bg-brand-50 ring-1 ring-brand-500'
                      : 'border-slate-200 hover:border-slate-300'}`}
                >
                  <p className="font-medium text-slate-900">{tr.fullName}</p>
                  <p className="mt-0.5 text-xs text-slate-500">
                    {tr.level && <span>{tr.level} · </span>}
                    {tr.ratingAvg != null
                      ? `★ ${tr.ratingAvg.toFixed(1)} (${tr.ratingCount} đánh giá)`
                      : 'Chưa có đánh giá'}
                  </p>
                </button>
              ))}
            </div>
          )}
        </div>

        <div className="grid grid-cols-2 gap-3">
          <Input label="Ngày tập" type="date" value={form.ngay}
                 onChange={(e) => setForm({ ...form, ngay: e.target.value })} />
          <Input label="Giờ bắt đầu" type="time" value={form.gio}
                 onChange={(e) => setForm({ ...form, gio: e.target.value })} />
        </div>

        <Input label="Phòng tập" value={form.roomName}
               onChange={(e) => setForm({ ...form, roomName: e.target.value })} />

        {datLich.error instanceof ApiError && <Alert tone="error">{datLich.error.message}</Alert>}

        <div className="flex justify-end gap-2">
          <Button variant="secondary" onClick={onClose}>Hủy</Button>
          <Button onClick={gui} loading={datLich.isPending} disabled={!form.ngay || !form.trainerId}>
            Gửi yêu cầu
          </Button>
        </div>
      </div>
    </Modal>
  )
}
