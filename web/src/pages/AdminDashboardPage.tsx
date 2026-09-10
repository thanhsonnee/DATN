import { useAdminDashboard } from '@/hooks/useAdmin'
import { Card, CardBody, CardHeader } from '@/components/ui/Card'
import { Spinner } from '@/components/ui/Spinner'
import { tien } from '@/lib/format'

/**
 * Dashboard tổng quan cho Admin — 2 nhóm ưu tiên đã chốt: Hội viên & Doanh thu,
 * Vận hành hôm nay. Nhóm PT & CRM chưa làm, để dịp sau.
 */
export function AdminDashboardPage() {
  const { data, isLoading } = useAdminDashboard()

  if (isLoading) return <Spinner />
  if (!data) return null

  const { membersOverview: hv, revenueOverview: dt, operationsToday: vh } = data

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-bold text-slate-900">Dashboard Admin</h1>
        <p className="text-sm text-slate-500">Tổng quan hội viên, doanh thu và vận hành hôm nay</p>
      </div>

      <Card>
        <CardHeader title="Hội viên" subtitle="Tính theo hợp đồng, không phải COUNT(*) FROM members" />
        <CardBody>
          <div className="grid grid-cols-2 gap-4 sm:grid-cols-4">
            <O nhan="Đang hoạt động" giaTri={hv.activeCount} mau="text-emerald-700" />
            <O nhan="Mới trong tháng" giaTri={hv.newThisMonthCount} />
            <O nhan="Sắp hết hạn (7 ngày)" giaTri={hv.expiringSoonCount}
               mau={hv.expiringSoonCount > 0 ? 'text-amber-700' : undefined} />
            <O nhan="Đang bảo lưu" giaTri={hv.frozenCount} />
          </div>
        </CardBody>
      </Card>

      <Card>
        <CardHeader title="Doanh thu tháng này" subtitle="Đối chiếu dòng tiền thực thu và doanh thu dồn tích" />
        <CardBody>
          <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-4">
            <O nhan="Tiền mặt/chuyển khoản đã thu" giaTri={tien(dt.cashCollectedThisMonth)} mau="text-emerald-700" />
            <O nhan="Doanh thu ghi nhận (dồn tích)" giaTri={tien(dt.accrualRecognizedThisMonth)} />
            <O nhan="Doanh thu chưa ghi nhận" giaTri={tien(dt.deferredRevenueThisMonth)} mau="text-slate-500" />
            <O nhan={`Công nợ quá hạn (${dt.unpaidOverdueCount} hóa đơn)`}
               giaTri={tien(dt.unpaidOverdueAmount)}
               mau={dt.unpaidOverdueCount > 0 ? 'text-red-700' : undefined} />
          </div>
        </CardBody>
      </Card>

      <Card>
        <CardHeader title="Vận hành hôm nay" subtitle="Việc đáng chú ý ngay lúc này" />
        <CardBody>
          <div className="grid grid-cols-2 gap-4 sm:grid-cols-4">
            <O nhan="Lượt check-in" giaTri={vh.checkInsToday} />
            <O nhan="Lượt bị chặn" giaTri={vh.deniedToday}
               mau={vh.deniedToday > 0 ? 'text-amber-700' : undefined} />
            <O nhan="Ca thu ngân đang mở" giaTri={vh.openCashShifts} />
            <O nhan="Ca lệch quỹ" giaTri={vh.cashShiftDiscrepancies}
               mau={vh.cashShiftDiscrepancies > 0 ? 'text-red-700' : undefined} />
            <O nhan="Phản hồi cần xử lý" giaTri={vh.openFeedbacksCount} />
            <O nhan="Phản hồi khẩn cấp" giaTri={vh.urgentFeedbacksCount}
               mau={vh.urgentFeedbacksCount > 0 ? 'text-red-700' : undefined} />
            <O nhan="Thiết bị cần sửa" giaTri={vh.equipmentNeedsRepairCount}
               mau={vh.equipmentNeedsRepairCount > 0 ? 'text-amber-700' : undefined} />
            <O nhan="Bảng lương chờ duyệt" giaTri={vh.draftPayrollRunsCount} />
          </div>
        </CardBody>
      </Card>
    </div>
  )
}

function O({ nhan, giaTri, mau = 'text-slate-900' }: {
  nhan: string; giaTri: number | string; mau?: string
}) {
  return (
    <div>
      <p className="text-xs text-slate-500">{nhan}</p>
      <p className={`mt-1 text-2xl font-bold ${mau}`}>{giaTri}</p>
    </div>
  )
}
