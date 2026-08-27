import type { ReactNode } from 'react'
import { Badge } from '@/components/ui/Badge'
import { Card, CardBody } from '@/components/ui/Card'
import { ngayGio, tenLoaiBuoiTap, tenTrangThaiBuoiTap } from '@/lib/format-cde'
import type { PtSession, SessionStatus } from '@/api/types-cde'

function toneFor(status: SessionStatus) {
  switch (status) {
    case 'COMPLETED': return 'green' as const
    case 'SCHEDULED': return 'blue' as const
    case 'PENDING_TRAINER': return 'amber' as const
    case 'REJECTED':
    case 'NO_SHOW':
    case 'CANCELLED': return 'red' as const
    default: return 'gray' as const
  }
}

/** Một buổi tập, dùng chung cho cả màn hình hội viên và huấn luyện viên. */
export function TheBuoiTap({ buoi, doiTac, actions }: {
  buoi: PtSession
  /** Tên bên còn lại — hội viên thì hiện tên PT, và ngược lại. */
  doiTac: string
  actions?: ReactNode
}) {
  return (
    <Card>
      <CardBody className="space-y-3">
        <div className="flex items-start justify-between gap-3">
          <div>
            <p className="font-medium text-slate-900">{ngayGio(buoi.scheduledStart)}</p>
            <p className="mt-0.5 text-sm text-slate-500">
              {doiTac} · {tenLoaiBuoiTap(buoi.sessionType)}
              {buoi.roomName && ` · ${buoi.roomName}`}
            </p>
          </div>
          <Badge tone={toneFor(buoi.status)}>{tenTrangThaiBuoiTap(buoi.status)}</Badge>
        </div>

        {/* Trạng thái xác nhận hai chiều — thấy rõ đang chờ ai */}
        {buoi.status === 'SCHEDULED' && (
          <div className="flex gap-4 rounded-lg bg-slate-50 px-3 py-2 text-xs">
            <TrangThaiXacNhan nhan="Huấn luyện viên" xong={!!buoi.trainerConfirmedAt} />
            <TrangThaiXacNhan nhan="Hội viên" xong={!!buoi.memberConfirmedAt} />
          </div>
        )}

        {buoi.status === 'COMPLETED' && buoi.autoConfirmed && (
          <p className="text-xs text-amber-700">
            Hệ thống tự duyệt do hội viên không phản hồi trong 24 giờ
          </p>
        )}

        {buoi.rejectReason && (
          <p className="text-sm text-red-700">Lý do từ chối: {buoi.rejectReason}</p>
        )}
        {buoi.cancelReason && (
          <p className="text-sm text-slate-600">
            Hủy bởi {buoi.cancelledBy === 'MEMBER' ? 'hội viên' : 'huấn luyện viên'}
            {buoi.isLateCancel && ' (hủy muộn, mất buổi)'} — {buoi.cancelReason}
          </p>
        )}
        {buoi.noShowBy && (
          <p className="text-sm text-red-700">
            Vắng mặt: {buoi.noShowBy === 'MEMBER' ? 'hội viên' : 'huấn luyện viên'}
            {buoi.noShowBy === 'TRAINER' && ' — hội viên không bị mất buổi'}
          </p>
        )}

        {actions && <div className="flex flex-wrap justify-end gap-2 pt-1">{actions}</div>}
      </CardBody>
    </Card>
  )
}

function TrangThaiXacNhan({ nhan, xong }: { nhan: string; xong: boolean }) {
  return (
    <span className={xong ? 'font-medium text-emerald-700' : 'text-slate-400'}>
      {xong ? '✓' : '○'} {nhan} {xong ? 'đã xác nhận' : 'chưa xác nhận'}
    </span>
  )
}
