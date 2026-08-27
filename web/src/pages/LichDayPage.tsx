import { useState } from 'react'
import {
  useDuyetLich, useHlvXacNhan, useTrainerSchedule, useTuChoiLich, useVangMat,
} from '@/hooks/usePtSessions'
import { TheBuoiTap } from '@/components/TheBuoiTap'
import { Button } from '@/components/ui/Button'
import { Input } from '@/components/ui/Input'
import { Alert } from '@/components/ui/Alert'
import { Modal } from '@/components/ui/Modal'
import { Card, CardBody, CardHeader } from '@/components/ui/Card'
import { EmptyState, Spinner } from '@/components/ui/Spinner'
import { ApiError } from '@/api/client'

/** Lịch dạy của huấn luyện viên: duyệt yêu cầu, xác nhận đã dạy. */
export function LichDayPage() {
  const { data: buoiTaps, isLoading } = useTrainerSchedule()
  const duyet = useDuyetLich()
  const tuChoi = useTuChoiLich()
  const xacNhan = useHlvXacNhan()
  const vangMat = useVangMat()

  const [tuChoiId, setTuChoiId] = useState<number | null>(null)
  const [lyDo, setLyDo] = useState('')

  if (isLoading) return <Spinner />

  const choDuyet = buoiTaps?.filter((b) => b.status === 'PENDING_TRAINER') ?? []
  const daLenLich = buoiTaps?.filter((b) => b.status === 'SCHEDULED') ?? []
  const daXong = buoiTaps?.filter(
    (b) => !['PENDING_TRAINER', 'SCHEDULED'].includes(b.status)) ?? []

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

      <section className="space-y-3">
        <h2 className="font-semibold text-slate-800">Sắp tới ({daLenLich.length})</h2>
        {daLenLich.length === 0 && <EmptyState title="Không có buổi tập nào sắp tới" />}

        {daLenLich.map((b) => (
          <TheBuoiTap key={b.id} buoi={b} doiTac={b.memberName}
            actions={
              <>
                <Button variant="secondary"
                        onClick={() => vangMat.mutate({ id: b.id, noShowBy: 'MEMBER',
                                                        note: 'Hội viên không đến' })}>
                  Hội viên vắng
                </Button>
                {!b.trainerConfirmedAt && (
                  <Button loading={xacNhan.isPending} onClick={() => xacNhan.mutate(b.id)}>
                    Đã dạy xong
                  </Button>
                )}
                {b.trainerConfirmedAt && !b.memberConfirmedAt && (
                  <span className="self-center text-xs text-slate-500">
                    Đã xác nhận, chờ hội viên phản hồi
                  </span>
                )}
              </>
            } />
        ))}
      </section>

      {(xacNhan.error instanceof ApiError || vangMat.error instanceof ApiError) && (
        <Alert tone="error">
          {(xacNhan.error as ApiError)?.message ?? (vangMat.error as ApiError)?.message}
        </Alert>
      )}

      {daXong.length > 0 && (
        <section className="space-y-3">
          <h2 className="font-semibold text-slate-800">Đã kết thúc ({daXong.length})</h2>
          {daXong.map((b) => <TheBuoiTap key={b.id} buoi={b} doiTac={b.memberName} />)}
        </section>
      )}

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
