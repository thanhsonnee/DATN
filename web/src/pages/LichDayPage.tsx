import { useMemo, useState } from 'react'
import {
  useDuyetLich, useHlvXacNhan, useTrainerSchedule, useTuChoiLich, useVangMat,
} from '@/hooks/usePtSessions'
import { TheBuoiTap } from '@/components/TheBuoiTap'
import { Button } from '@/components/ui/Button'
import { Input } from '@/components/ui/Input'
import { Alert } from '@/components/ui/Alert'
import { Modal } from '@/components/ui/Modal'
import { Card, CardBody, CardHeader } from '@/components/ui/Card'
import { Spinner } from '@/components/ui/Spinner'
import { ApiError } from '@/api/client'
import { gioPhut } from '@/lib/format-cde'
import type { PtSession } from '@/api/types-cde'

/** Lịch dạy của huấn luyện viên: duyệt yêu cầu, xem lịch tuần, xác nhận đã dạy. */
export function LichDayPage() {
  const { data: buoiTaps, isLoading } = useTrainerSchedule()
  const duyet = useDuyetLich()
  const tuChoi = useTuChoiLich()

  const [tuChoiId, setTuChoiId] = useState<number | null>(null)
  const [lyDo, setLyDo] = useState('')

  if (isLoading) return <Spinner />

  const choDuyet = buoiTaps?.filter((b) => b.status === 'PENDING_TRAINER') ?? []

  return (
    <div className="space-y-6">
      <h1 className="text-2xl font-bold text-slate-900">Lịch dạy của tôi</h1>

      <Card>
        <CardHeader title="Cách tính công buổi tập"
                    subtitle="Ghi chú cho người chấm — sẽ gỡ khi hoàn thiện" />
        <CardBody className="text-sm text-slate-600">
          Buổi tập chỉ được tính công khi <b>cả hai bên cùng xác nhận</b>. Việc bạn bấm
          "Đã dạy xong" là <b>điều kiện khởi động</b>: không bấm thì không có gì xảy ra,
          nên buổi không dạy sẽ không bao giờ tự động được trả công. Hội viên không phản
          hồi trong 24 giờ thì hệ thống tự duyệt, nhưng có đánh dấu để kiểm toán.
        </CardBody>
      </Card>

      {choDuyet.length > 0 && (
        <section className="space-y-3">
          <h2 className="font-semibold text-slate-800">Chờ bạn duyệt ({choDuyet.length})</h2>
          {choDuyet.map((b) => (
            <TheBuoiTap key={b.id} buoi={b} doiTac={b.memberName}
              actions={
                <>
                  <Button variant="secondary" onClick={() => setTuChoiId(b.id)}>Từ chối</Button>
                  <Button loading={duyet.isPending} onClick={() => duyet.mutate(b.id)}>
                    Nhận lịch
                  </Button>
                </>
              } />
          ))}
        </section>
      )}

      {duyet.error instanceof ApiError && <Alert tone="error">{duyet.error.message}</Alert>}

      <LichTuanHuanLuyenVien buoiTaps={buoiTaps ?? []} />

      <Modal open={tuChoiId != null} title="Từ chối yêu cầu đặt lịch"
             onClose={() => setTuChoiId(null)}>
        <div className="space-y-4">
          <Input label="Lý do từ chối" value={lyDo} onChange={(e) => setLyDo(e.target.value)}
                 placeholder="Trùng lịch với học viên khác"
                 hint="Hội viên cần biết lý do để xếp lại lịch" />
          {tuChoi.error instanceof ApiError && <Alert tone="error">{tuChoi.error.message}</Alert>}
          <div className="flex justify-end gap-2">
            <Button variant="secondary" onClick={() => setTuChoiId(null)}>Đóng</Button>
            <Button variant="danger" loading={tuChoi.isPending} disabled={!lyDo.trim()}
                    onClick={() => tuChoiId && tuChoi.mutate({ id: tuChoiId, reason: lyDo },
                      { onSuccess: () => { setTuChoiId(null); setLyDo('') } })}>
              Từ chối
            </Button>
          </div>
        </div>
      </Modal>
    </div>
  )
}

// =============================================================================
// Lịch tuần kiểu Google Calendar
// =============================================================================

const GIO_BAT_DAU = 6    // 6h sáng
const GIO_KET_THUC = 22  // 10h tối
const CAO_MOI_GIO = 56   // px mỗi ô giờ
const TEN_THU = ['Chủ nhật', 'Thứ 2', 'Thứ 3', 'Thứ 4', 'Thứ 5', 'Thứ 6', 'Thứ 7']

function dauTuanCua(d: Date): Date {
  const x = new Date(d)
  const thu = x.getDay() // 0 = Chủ nhật
  const lech = thu === 0 ? -6 : 1 - thu // đưa về Thứ 2 đầu tuần
  x.setDate(x.getDate() + lech)
  x.setHours(0, 0, 0, 0)
  return x
}

function cungNgay(a: Date, b: Date): boolean {
  return a.getFullYear() === b.getFullYear() && a.getMonth() === b.getMonth() && a.getDate() === b.getDate()
}

function ddmm(d: Date): string {
  return `${d.getDate().toString().padStart(2, '0')}/${(d.getMonth() + 1).toString().padStart(2, '0')}`
}

function toneNen(status: string): string {
  switch (status) {
    case 'SCHEDULED': return 'border-blue-300 bg-blue-100 text-blue-800 hover:bg-blue-200'
    case 'COMPLETED': return 'border-emerald-300 bg-emerald-100 text-emerald-800 hover:bg-emerald-200'
    case 'NO_SHOW': return 'border-red-300 bg-red-100 text-red-700 hover:bg-red-200'
    default: return 'border-slate-300 bg-slate-100 text-slate-500 line-through hover:bg-slate-200'
  }
}

/**
 * Lịch dạy dạng lưới tuần, giống Google Calendar. Chỉ hiện buổi ĐÃ CHỐT trở
 * đi (SCHEDULED/COMPLETED/NO_SHOW/CANCELLED/REJECTED) — buổi PENDING_TRAINER
 * chưa có gì chắc chắn nên vẫn nằm riêng ở khối "Chờ bạn duyệt" phía trên,
 * không vẽ lên lịch kẻo lẫn với lịch đã chắc chắn.
 */
function LichTuanHuanLuyenVien({ buoiTaps }: { buoiTaps: PtSession[] }) {
  const [dauTuan, setDauTuan] = useState(() => dauTuanCua(new Date()))
  const [dangXem, setDangXem] = useState<PtSession | null>(null)
  const xacNhan = useHlvXacNhan()
  const vangMat = useVangMat()

  const ngayTrongTuan = useMemo(() => Array.from({ length: 7 }, (_, i) => {
    const d = new Date(dauTuan)
    d.setDate(d.getDate() + i)
    return d
  }), [dauTuan])

  const gioTrongNgay = useMemo(
    () => Array.from({ length: GIO_KET_THUC - GIO_BAT_DAU }, (_, i) => GIO_BAT_DAU + i),
    [],
  )

  const buoiHienThi = buoiTaps.filter((b) => b.status !== 'PENDING_TRAINER')

  return (
    <Card>
      <CardHeader
        title="Lịch dạy theo tuần"
        subtitle={`${ddmm(ngayTrongTuan[0])} — ${ddmm(ngayTrongTuan[6])}`}
        action={
          <div className="flex gap-2">
            <Button variant="secondary" className="!px-3 !py-1.5 !text-xs"
                    onClick={() => setDauTuan((d) => { const x = new Date(d); x.setDate(x.getDate() - 7); return x })}>
              ‹ Tuần trước
            </Button>
            <Button variant="secondary" className="!px-3 !py-1.5 !text-xs"
                    onClick={() => setDauTuan(dauTuanCua(new Date()))}>
              Tuần này
            </Button>
            <Button variant="secondary" className="!px-3 !py-1.5 !text-xs"
                    onClick={() => setDauTuan((d) => { const x = new Date(d); x.setDate(x.getDate() + 7); return x })}>
              Tuần sau ›
            </Button>
          </div>
        }
      />
      <CardBody className="overflow-x-auto p-0">
        <div className="min-w-[820px]">
          {/* Hàng tiêu đề: tên thứ + ngày */}
          <div className="grid grid-cols-[56px_repeat(7,1fr)] border-b border-slate-200">
            <div />
            {ngayTrongTuan.map((d) => (
              <div key={d.toISOString()}
                   className={`border-l border-slate-100 px-2 py-2 text-center text-xs font-medium
                              ${cungNgay(d, new Date()) ? 'bg-brand-50 text-brand-700' : 'text-slate-600'}`}>
                {TEN_THU[d.getDay()]}<br />{ddmm(d)}
              </div>
            ))}
          </div>

          {/* Lưới giờ */}
          <div className="grid grid-cols-[56px_repeat(7,1fr)]">
            <div>
              {gioTrongNgay.map((gio) => (
                <div key={gio} className="border-t border-slate-100 px-1 text-right text-[11px] text-slate-400"
                     style={{ height: CAO_MOI_GIO }}>
                  {gio}:00
                </div>
              ))}
            </div>

            {ngayTrongTuan.map((ngayCot) => (
              <div key={ngayCot.toISOString()} className="relative border-l border-slate-100">
                {gioTrongNgay.map((gio) => (
                  <div key={gio} className="border-t border-slate-50" style={{ height: CAO_MOI_GIO }} />
                ))}

                {buoiHienThi
                  .filter((b) => cungNgay(new Date(b.scheduledStart), ngayCot))
                  .map((b) => {
                    const bd = new Date(b.scheduledStart)
                    const kt = new Date(b.scheduledEnd)
                    const phutBatDau = (bd.getHours() - GIO_BAT_DAU) * 60 + bd.getMinutes()
                    const phutKeoDai = Math.max((kt.getTime() - bd.getTime()) / 60_000, 25)
                    const top = (phutBatDau / 60) * CAO_MOI_GIO
                    const height = (phutKeoDai / 60) * CAO_MOI_GIO

                    return (
                      <button key={b.id} onClick={() => setDangXem(b)}
                              className={`absolute left-0.5 right-0.5 overflow-hidden rounded-md
                                         border px-1.5 py-0.5 text-left text-[11px] leading-tight
                                         transition ${toneNen(b.status)}`}
                              style={{ top, height }}>
                        <div className="font-semibold">{gioPhut(b.scheduledStart)}</div>
                        <div className="truncate">{b.memberName}</div>
                      </button>
                    )
                  })}
              </div>
            ))}
          </div>
        </div>

        {buoiHienThi.length === 0 && (
          <p className="px-5 py-6 text-center text-sm text-slate-500">
            Không có buổi tập nào đã lên lịch trong khoảng thời gian này
          </p>
        )}
      </CardBody>

      {(xacNhan.error instanceof ApiError || vangMat.error instanceof ApiError) && (
        <div className="px-5 pb-4">
          <Alert tone="error">
            {(xacNhan.error as ApiError)?.message ?? (vangMat.error as ApiError)?.message}
          </Alert>
        </div>
      )}

      <Modal open={!!dangXem} title="Chi tiết buổi tập" onClose={() => setDangXem(null)}>
        {dangXem && (
          <TheBuoiTap
            buoi={dangXem}
            doiTac={dangXem.memberName}
            actions={dangXem.status === 'SCHEDULED' ? (
              <>
                <Button variant="secondary"
                        onClick={() => vangMat.mutate(
                          { id: dangXem.id, noShowBy: 'MEMBER', note: 'Hội viên không đến' },
                          { onSuccess: () => setDangXem(null) },
                        )}>
                  Hội viên vắng
                </Button>
                {!dangXem.trainerConfirmedAt && (
                  <Button loading={xacNhan.isPending}
                          onClick={() => xacNhan.mutate(dangXem.id, { onSuccess: () => setDangXem(null) })}>
                    Đã dạy xong
                  </Button>
                )}
                {dangXem.trainerConfirmedAt && !dangXem.memberConfirmedAt && (
                  <span className="self-center text-xs text-slate-500">
                    Đã xác nhận, chờ hội viên phản hồi
                  </span>
                )}
              </>
            ) : undefined}
          />
        )}
      </Modal>
    </Card>
  )
}
