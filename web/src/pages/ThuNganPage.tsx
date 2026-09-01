import { useState } from 'react'
import {
  useCaHienTai, useCongNo, useDongCa, useKhoanThu, useMoCa, useThuTien,
} from '@/hooks/useBilling'
import {
  useRevenueReport, useScanRevenue,
  usePayrollRuns, usePayrollDetail, useCalculatePayroll, useUpdatePayrollItem, useApprovePayroll, usePayPayroll,
  useExpenses, useCreateExpense, useDeleteExpense, useProfitLossReport,
} from '@/hooks/useFinance'
import { ChoThanhToan } from '@/components/ChoThanhToan'
import { Card, CardBody, CardHeader } from '@/components/ui/Card'
import { Button } from '@/components/ui/Button'
import { Input } from '@/components/ui/Input'
import { Badge } from '@/components/ui/Badge'
import { Alert } from '@/components/ui/Alert'
import { Modal } from '@/components/ui/Modal'
import { EmptyState, Spinner } from '@/components/ui/Spinner'
import { ApiError } from '@/api/client'
import { tien } from '@/lib/format'
import { ngayGio, tenHinhThucThanhToan, tenTrangThaiHoaDon } from '@/lib/format-cde'
import type { PaymentMethod, ExpenseCategory, PayrollRun } from '@/api/types-cde'

type TabType = 'THU_NGAN' | 'DOANH_THU' | 'BANG_LUONG' | 'CHI_PHI'

/** Màn hình Quản lý Tài chính & Thu ngân (Phân đoạn E1 - E5). */
export function ThuNganPage() {
  const [tab, setTab] = useState<TabType>('THU_NGAN')

  return (
    <div className="space-y-6">
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
        <div>
          <h1 className="text-2xl font-bold text-slate-900">Tài chính & Thu ngân</h1>
          <p className="text-sm text-slate-500">Quản lý dòng tiền, doanh thu dồn tích, bảng lương và chi phí</p>
        </div>

        {/* Tab Navigation */}
        <div className="flex items-center gap-1 rounded-xl bg-slate-100 p-1">
          <button
            onClick={() => setTab('THU_NGAN')}
            className={`rounded-lg px-3 py-1.5 text-xs font-semibold transition ${
              tab === 'THU_NGAN' ? 'bg-white text-brand-700 shadow-sm' : 'text-slate-600 hover:text-slate-900'
            }`}
          >
            Quầy thu ngân (E1-E2)
          </button>
          <button
            onClick={() => setTab('DOANH_THU')}
            className={`rounded-lg px-3 py-1.5 text-xs font-semibold transition ${
              tab === 'DOANH_THU' ? 'bg-white text-brand-700 shadow-sm' : 'text-slate-600 hover:text-slate-900'
            }`}
          >
            Doanh thu dồn tích (E3)
          </button>
          <button
            onClick={() => setTab('BANG_LUONG')}
            className={`rounded-lg px-3 py-1.5 text-xs font-semibold transition ${
              tab === 'BANG_LUONG' ? 'bg-white text-brand-700 shadow-sm' : 'text-slate-600 hover:text-slate-900'
            }`}
          >
            Bảng lương (E4)
          </button>
          <button
            onClick={() => setTab('CHI_PHI')}
            className={`rounded-lg px-3 py-1.5 text-xs font-semibold transition ${
              tab === 'CHI_PHI' ? 'bg-white text-brand-700 shadow-sm' : 'text-slate-600 hover:text-slate-900'
            }`}
          >
            Chi phí & P&L (E5)
          </button>
        </div>
      </div>

      {tab === 'THU_NGAN' && <TabThuNgan />}
      {tab === 'DOANH_THU' && <TabDoanhThuDonTich />}
      {tab === 'BANG_LUONG' && <TabBangLuong />}
      {tab === 'CHI_PHI' && <TabChiPhiVaLoiNhuan />}
    </div>
  )
}

// =============================================================================
// TAB 1: Thu ngân & Ca làm việc (E1 + E2)
// =============================================================================

function TabThuNgan() {
  const { data: ca, isLoading } = useCaHienTai()
  const { data: congNo } = useCongNo()
  const [thuTienChoHoaDon, setThuTienChoHoaDon] = useState<number | null>(null)

  if (isLoading) return <Spinner />

  return (
    <div className="space-y-6">
      <div className="grid gap-6 lg:grid-cols-2">
        <CaLamViec />
        <ChoThanhToan />
      </div>

      <Card>
        <CardHeader
          title="Công nợ"
          subtitle="Hóa đơn chưa thu đủ"
          action={<Badge tone="amber">{congNo?.length ?? 0} hóa đơn</Badge>}
        />
        <CardBody className="p-0">
          {congNo?.length === 0 ? (
            <EmptyState title="Không còn hóa đơn nào chưa thu" />
          ) : (
            <div className="overflow-x-auto">
              <table className="w-full text-sm">
                <thead className="border-b border-slate-100 bg-slate-50 text-left text-xs uppercase text-slate-500">
                  <tr>
                    <th className="px-5 py-3">Số hóa đơn</th>
                    <th className="px-5 py-3">Hội viên</th>
                    <th className="px-5 py-3 text-right">Tổng tiền</th>
                    <th className="px-5 py-3 text-right">Đã thu</th>
                    <th className="px-5 py-3 text-right">Còn nợ</th>
                    <th className="px-5 py-3">Trạng thái</th>
                    <th className="px-5 py-3"></th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-100">
                  {congNo?.map((hd) => (
                    <tr key={hd.id} className="hover:bg-slate-50">
                      <td className="px-5 py-3 font-mono text-xs">{hd.invoiceNo}</td>
                      <td className="px-5 py-3">
                        <div className="font-medium text-slate-800">{hd.memberName}</div>
                        <div className="font-mono text-xs text-slate-400">{hd.memberCode}</div>
                      </td>
                      <td className="px-5 py-3 text-right">{tien(hd.totalAmount)}</td>
                      <td className="px-5 py-3 text-right text-slate-500">{tien(hd.paidAmount)}</td>
                      <td className="px-5 py-3 text-right font-semibold text-amber-700">
                        {tien(hd.balanceDue)}
                      </td>
                      <td className="px-5 py-3">
                        <Badge tone="amber">{tenTrangThaiHoaDon(hd.status)}</Badge>
                      </td>
                      <td className="px-5 py-3 text-right">
                        <Button onClick={() => setThuTienChoHoaDon(hd.id)}>Thu tiền</Button>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </CardBody>
      </Card>

      <HopThoaiThuTien
        invoiceId={thuTienChoHoaDon}
        conNo={congNo?.find((h) => h.id === thuTienChoHoaDon)?.balanceDue ?? 0}
        coCaDangMo={!!ca}
        onClose={() => setThuTienChoHoaDon(null)}
      />
    </div>
  )
}

/** Mở và đóng ca, kèm đối soát tiền mặt cuối ca. */
function CaLamViec() {
  const { data: ca } = useCaHienTai()
  const moCa = useMoCa()
  const dongCa = useDongCa()

  const [tienDauCa, setTienDauCa] = useState('500000')
  const [tienDemDuoc, setTienDemDuoc] = useState('')
  const [lyDoLech, setLyDoLech] = useState('')
  const [moHopThoaiDong, setMoHopThoaiDong] = useState(false)

  const moModalDong = () => {
    dongCa.reset()
    setTienDemDuoc(String(ca?.expectedCash ?? ca?.openingBalance ?? 0))
    setLyDoLech('')
    setMoHopThoaiDong(true)
  }

  if (!ca) {
    return (
      <Card>
        <CardHeader title="Ca làm việc" subtitle="Chưa mở ca" />
        <CardBody className="space-y-3">
          <Alert tone="info">
            Phải mở ca trước khi thu tiền mặt. Không có ca thì khoản thu sẽ nằm ngoài
            mọi lần đối soát.
          </Alert>
          <Input
            label="Tiền mặt đầu ca"
            type="number"
            value={tienDauCa}
            onChange={(e) => setTienDauCa(e.target.value)}
          />
          {moCa.error instanceof ApiError && <Alert tone="error">{moCa.error.message}</Alert>}
          <Button loading={moCa.isPending} onClick={() => moCa.mutate(Number(tienDauCa))}>
            Mở ca
          </Button>
        </CardBody>
      </Card>
    )
  }

  const tienMatThu = (ca.expectedCash ?? ca.openingBalance) - ca.openingBalance

  return (
    <>
      <Card>
        <CardHeader
          title="Ca làm việc"
          subtitle={`Mở lúc ${ngayGio(ca.openedAt)}`}
          action={<Badge tone="green">Đang mở</Badge>}
        />
        <CardBody className="space-y-3">
          <dl className="space-y-1 text-sm">
            <div className="flex justify-between">
              <dt className="text-slate-500">Người trực</dt>
              <dd className="font-medium">{ca.employeeName}</dd>
            </div>
            <div className="flex justify-between">
              <dt className="text-slate-500">Tiền mặt đầu ca</dt>
              <dd className="font-medium">{tien(ca.openingBalance)}</dd>
            </div>
            <div className="flex justify-between">
              <dt className="text-slate-500">Tiền mặt thu trong ca</dt>
              <dd className="font-medium text-emerald-700">+{tien(tienMatThu)}</dd>
            </div>
            <div className="flex justify-between border-t border-slate-100 pt-1">
              <dt className="font-semibold text-slate-700">Tổng tiền mặt theo sổ</dt>
              <dd className="font-bold text-slate-900">{tien(ca.expectedCash ?? ca.openingBalance)}</dd>
            </div>
          </dl>
          <Button variant="secondary" onClick={moModalDong}>
            Đóng ca và đối soát
          </Button>
        </CardBody>
      </Card>

      <Modal open={moHopThoaiDong} title="Đóng ca và đối soát tiền mặt" onClose={() => setMoHopThoaiDong(false)}>
        <div className="space-y-4">
          <div className="rounded-lg bg-slate-50 p-3 space-y-1.5 text-sm">
            <div className="flex justify-between text-slate-600">
              <span>Tiền mặt đầu ca:</span>
              <span>{tien(ca.openingBalance)}</span>
            </div>
            <div className="flex justify-between text-slate-600">
              <span>Tiền mặt thu trong ca:</span>
              <span className="text-emerald-700 font-medium">+{tien(tienMatThu)}</span>
            </div>
            <div className="flex justify-between border-t border-slate-200 pt-1.5 font-bold text-slate-900">
              <span>Tổng tiền mặt hệ thống tính (theo sổ):</span>
              <span className="text-blue-700">{tien(ca.expectedCash ?? ca.openingBalance)}</span>
            </div>
          </div>

          <Alert tone="info">
            Hệ thống đã tính sẵn số tiền theo sổ sách bên trên. Lễ tân chỉ cần đếm lại két tiền ngoài đời thực: nếu khớp thì giữ nguyên bấm <b>Chốt ca</b>; nếu thiếu/thừa thì sửa lại số tiền và ghi lý do.
          </Alert>

          <Input
            label="Tiền mặt đếm được thực tế trong két"
            type="number"
            value={tienDemDuoc}
            onChange={(e) => setTienDemDuoc(e.target.value)}
            autoFocus
          />

          <Input
            label="Lý do nếu lệch"
            value={lyDoLech}
            onChange={(e) => setLyDoLech(e.target.value)}
            placeholder="Trả thừa tiền khách lúc 15h, đã ghi sổ tay"
            hint="Bỏ trống nếu khớp sổ"
          />

          {dongCa.error instanceof ApiError && <Alert tone="error">{dongCa.error.message}</Alert>}

          {dongCa.isSuccess && (
            <Alert tone={dongCa.data.status === 'CLOSED' ? 'success' : 'error'}>
              Theo sổ: {tien(dongCa.data.expectedCash ?? 0)} · Đếm được:{' '}
              {tien(dongCa.data.countedCash ?? 0)} · Chênh lệch:{' '}
              <b>{tien(dongCa.data.difference ?? 0)}</b>
            </Alert>
          )}

          <div className="flex justify-end gap-2">
            <Button variant="secondary" onClick={() => setMoHopThoaiDong(false)}>Đóng</Button>
            <Button
              loading={dongCa.isPending}
              disabled={!tienDemDuoc}
              onClick={() =>
                dongCa.mutate(
                  { countedCash: Number(tienDemDuoc), reason: lyDoLech || undefined },
                  { onSuccess: (kq) => { if (kq.status === 'CLOSED') setMoHopThoaiDong(false) } }
                )
              }
            >
              Chốt ca
            </Button>
          </div>
        </div>
      </Modal>
    </>
  )
}

function HopThoaiThuTien({
  invoiceId, conNo, coCaDangMo, onClose,
}: {
  invoiceId: number | null
  conNo: number
  coCaDangMo: boolean
  onClose: () => void
}) {
  const thuTien = useThuTien()
  const { data: khoanThu } = useKhoanThu(invoiceId)

  const [soTien, setSoTien] = useState('')
  const [hinhThuc, setHinhThuc] = useState<PaymentMethod>('CASH')

  const gui = () => {
    if (!invoiceId) return
    thuTien.mutate(
      { invoiceId, amount: Number(soTien), method: hinhThuc },
      { onSuccess: () => { setSoTien(''); onClose() } }
    )
  }

  return (
    <Modal open={invoiceId != null} title="Thu tiền" onClose={onClose}>
      <div className="space-y-4">
        <div className="rounded-lg bg-slate-50 p-3 text-sm">
          Còn nợ: <b className="text-amber-700">{tien(conNo)}</b>
        </div>

        {hinhThuc === 'CASH' && !coCaDangMo && (
          <Alert tone="error">
            Chưa mở ca làm việc — không thu được tiền mặt. Mở ca trước, hoặc chọn hình thức khác.
          </Alert>
        )}

        <Input
          label="Số tiền thu"
          type="number"
          value={soTien}
          autoFocus
          onChange={(e) => setSoTien(e.target.value)}
        />

        <div>
          <label className="mb-1 block text-sm font-medium text-slate-700">Hình thức</label>
          <select
            value={hinhThuc}
            onChange={(e) => setHinhThuc(e.target.value as PaymentMethod)}
            className="w-full rounded-lg border border-slate-300 px-3 py-2 text-sm"
          >
            {(['CASH', 'VIETQR', 'BANK_TRANSFER', 'CARD_POS', 'E_WALLET'] as PaymentMethod[])
              .map((m) => <option key={m} value={m}>{tenHinhThucThanhToan(m)}</option>)}
          </select>
        </div>

        <Alert tone="info">
          Thu đủ tiền thì hợp đồng <b>tự kích hoạt</b> và sổ cái <b>tự được cấp buổi</b>.
        </Alert>

        {thuTien.error instanceof ApiError && <Alert tone="error">{thuTien.error.message}</Alert>}

        {khoanThu && khoanThu.length > 0 && (
          <div className="text-xs text-slate-500">
            <p className="mb-1 font-medium">Đã thu trước đó:</p>
            <ul className="space-y-0.5">
              {khoanThu.map((p) => (
                <li key={p.id}>
                  {p.paymentNo} · {tenHinhThucThanhToan(p.method)} ·{' '}
                  <span className={p.amount < 0 ? 'text-red-600' : ''}>{tien(p.amount)}</span>
                </li>
              ))}
            </ul>
          </div>
        )}

        <div className="flex justify-end gap-2">
          <Button variant="secondary" onClick={onClose}>Hủy</Button>
          <Button onClick={gui} loading={thuTien.isPending} disabled={!soTien}>Thu tiền</Button>
        </div>
      </div>
    </Modal>
  )
}

// =============================================================================
// TAB 2: Doanh thu dồn tích (E3 - Revenue Recognition)
// =============================================================================

function TabDoanhThuDonTich() {
  const today = new Date()
  const [month, setMonth] = useState(today.getMonth() + 1)
  const [year, setYear] = useState(today.getFullYear())

  const { data: report, isLoading } = useRevenueReport(month, year)
  const scanRevenue = useScanRevenue()

  if (isLoading) return <Spinner />

  return (
    <div className="space-y-6">
      {/* Bộ lọc tháng/năm và Nút quét nhận doanh thu */}
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
        <div className="flex items-center gap-3">
          <label className="text-sm font-medium text-slate-700">Kỳ báo cáo:</label>
          <select
            value={month}
            onChange={(e) => setMonth(Number(e.target.value))}
            className="rounded-lg border border-slate-300 px-3 py-1.5 text-sm"
          >
            {Array.from({ length: 12 }, (_, i) => i + 1).map((m) => (
              <option key={m} value={m}>Tháng {m}</option>
            ))}
          </select>
          <select
            value={year}
            onChange={(e) => setYear(Number(e.target.value))}
            className="rounded-lg border border-slate-300 px-3 py-1.5 text-sm"
          >
            {[2025, 2026, 2027].map((y) => (
              <option key={y} value={y}>Năm {y}</option>
            ))}
          </select>
        </div>

        <Button
          variant="secondary"
          loading={scanRevenue.isPending}
          onClick={() => scanRevenue.mutate()}
        >
          ⚡ Quét nhận doanh thu đến hạn
        </Button>
      </div>

      {/* Thẻ chỉ số tổng quan E3 */}
      <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
        <Card>
          <CardBody>
            <p className="text-xs font-semibold uppercase tracking-wide text-slate-500">
              Thực thu trong tháng (Cash-Basis)
            </p>
            <p className="mt-2 text-2xl font-bold text-emerald-700">
              {tien(report?.totalCashCollected ?? 0)}
            </p>
            <p className="mt-1 text-xs text-slate-400">Dòng tiền thu thực tế từ hội viên</p>
          </CardBody>
        </Card>

        <Card>
          <CardBody>
            <p className="text-xs font-semibold uppercase tracking-wide text-slate-500">
              Doanh thu dồn tích (Accrual-Basis)
            </p>
            <p className="mt-2 text-2xl font-bold text-blue-700">
              {tien(report?.totalAccrualRecognized ?? 0)}
            </p>
            <p className="mt-1 text-xs text-slate-400">Doanh thu phân bổ được ghi nhận trong kỳ</p>
          </CardBody>
        </Card>

        <Card>
          <CardBody>
            <p className="text-xs font-semibold uppercase tracking-wide text-slate-500">
              Doanh thu nhận trước (Deferred)
            </p>
            <p className="mt-2 text-2xl font-bold text-amber-700">
              {tien(report?.totalDeferredRevenue ?? 0)}
            </p>
            <p className="mt-1 text-xs text-slate-400">Chờ phân bổ vào các tháng tiếp theo</p>
          </CardBody>
        </Card>
      </div>

      {/* Danh sách lịch phân bổ doanh thu */}
      <Card>
        <CardHeader
          title="Lịch phân bổ doanh thu trong tháng"
          subtitle="Tự động sinh đều theo thời hạn hợp đồng khi kích hoạt"
          action={<Badge tone="blue">{report?.schedules?.length ?? 0} khoản ghi nhận</Badge>}
        />
        <CardBody className="p-0">
          {!report?.schedules || report.schedules.length === 0 ? (
            <EmptyState title="Chưa có lịch phân bổ doanh thu nào trong tháng này" />
          ) : (
            <div className="overflow-x-auto">
              <table className="w-full text-sm">
                <thead className="border-b border-slate-100 bg-slate-50 text-left text-xs uppercase text-slate-500">
                  <tr>
                    <th className="px-5 py-3">Mã HĐ</th>
                    <th className="px-5 py-3">Hội viên</th>
                    <th className="px-5 py-3">Ngày phân bổ</th>
                    <th className="px-5 py-3 text-right">Số tiền kỳ này</th>
                    <th className="px-5 py-3">Phương pháp</th>
                    <th className="px-5 py-3">Trạng thái</th>
                    <th className="px-5 py-3">Ghi chú</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-100">
                  {report.schedules.map((s) => (
                    <tr key={s.id} className="hover:bg-slate-50">
                      <td className="px-5 py-3 font-mono text-xs font-semibold text-slate-700">{s.registrationCode}</td>
                      <td className="px-5 py-3 font-medium text-slate-800">{s.memberName}</td>
                      <td className="px-5 py-3 text-slate-600">{s.scheduleDate}</td>
                      <td className="px-5 py-3 text-right font-semibold text-blue-700">{tien(s.amount)}</td>
                      <td className="px-5 py-3">
                        <Badge tone="gray">{s.recognitionMethod}</Badge>
                      </td>
                      <td className="px-5 py-3">
                        <Badge tone={s.status === 'RECOGNIZED' ? 'green' : 'amber'}>
                          {s.status === 'RECOGNIZED' ? 'Đã ghi nhận' : 'Chờ đến hạn'}
                        </Badge>
                      </td>
                      <td className="px-5 py-3 text-xs text-slate-500">{s.note}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </CardBody>
      </Card>
    </div>
  )
}

// =============================================================================
// TAB 3: Bảng lương (E4 - Payroll Runs)
// =============================================================================

function TabBangLuong() {
  const today = new Date()
  const [calcMonth, setCalcMonth] = useState(today.getMonth() + 1)
  const [calcYear, setCalcYear] = useState(today.getFullYear())
  const [selectedRunId, setSelectedRunId] = useState<number | null>(null)

  const { data: runs, isLoading } = usePayrollRuns()
  const { data: detail } = usePayrollDetail(selectedRunId ?? runs?.[0]?.id ?? null)
  const calculatePayroll = useCalculatePayroll()
  const approvePayroll = useApprovePayroll()
  const payPayroll = usePayPayroll()

  const [editItemId, setEditItemId] = useState<number | null>(null)

  if (isLoading) return <Spinner />

  const activeRun = detail ?? runs?.[0]

  return (
    <div className="space-y-6">
      {/* Action Bar: Tính lương tháng mới */}
      <Card>
        <CardBody className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
          <div className="flex items-center gap-3">
            <span className="text-sm font-semibold text-slate-800">Tính bảng lương mới:</span>
            <select
              value={calcMonth}
              onChange={(e) => setCalcMonth(Number(e.target.value))}
              className="rounded-lg border border-slate-300 px-3 py-1.5 text-sm"
            >
              {Array.from({ length: 12 }, (_, i) => i + 1).map((m) => (
                <option key={m} value={m}>Tháng {m}</option>
              ))}
            </select>
            <select
              value={calcYear}
              onChange={(e) => setCalcYear(Number(e.target.value))}
              className="rounded-lg border border-slate-300 px-3 py-1.5 text-sm"
            >
              {[2025, 2026, 2027].map((y) => (
                <option key={y} value={y}>Năm {y}</option>
              ))}
            </select>
          </div>

          <Button
            loading={calculatePayroll.isPending}
            onClick={() => calculatePayroll.mutate({ periodMonth: calcMonth, periodYear: calcYear })}
          >
            📊 Tính lương tự động (DRAFT)
          </Button>
        </CardBody>
      </Card>

      {/* Danh sách các đợt lương */}
      <div className="grid gap-6 lg:grid-cols-3">
        <Card className="lg:col-span-1">
          <CardHeader title="Các kỳ lương" subtitle="Lịch sử đợt chạy lương" />
          <CardBody className="p-0">
            {!runs || runs.length === 0 ? (
              <p className="p-4 text-center text-sm text-slate-500">Chưa có bảng lương nào</p>
            ) : (
              <ul className="divide-y divide-slate-100">
                {runs.map((r) => {
                  const isSelected = r.id === (selectedRunId ?? runs[0]?.id)
                  return (
                    <li
                      key={r.id}
                      onClick={() => setSelectedRunId(r.id)}
                      className={`cursor-pointer p-4 transition hover:bg-slate-50 ${
                        isSelected ? 'bg-brand-50/60 border-l-4 border-brand-600' : ''
                      }`}
                    >
                      <div className="flex justify-between items-start">
                        <div>
                          <p className="font-semibold text-slate-900">
                            Tháng {r.periodMonth}/{r.periodYear}
                          </p>
                          <p className="font-mono text-xs text-slate-400">{r.payrollCode}</p>
                        </div>
                        <Badge
                          tone={
                            r.status === 'PAID'
                              ? 'green'
                              : r.status === 'APPROVED'
                              ? 'blue'
                              : 'amber'
                          }
                        >
                          {r.status === 'PAID' ? 'Đã chi trả' : r.status === 'APPROVED' ? 'Đã duyệt' : 'Dự thảo (Draft)'}
                        </Badge>
                      </div>
                      <div className="mt-2 flex justify-between text-xs text-slate-500">
                        <span>Tổng thực lĩnh:</span>
                        <span className="font-bold text-slate-800">{tien(r.totalNetSalary)}</span>
                      </div>
                    </li>
                  )
                })}
              </ul>
            )}
          </CardBody>
        </Card>

        {/* Chi tiết bảng lương đang chọn */}
        <div className="lg:col-span-2 space-y-6">
          {activeRun ? (
            <Card>
              <CardHeader
                title={`Bảng lương Tháng ${activeRun.periodMonth}/${activeRun.periodYear}`}
                subtitle={`Mã: ${activeRun.payrollCode} · Trạng thái: ${activeRun.status}`}
                action={
                  <div className="flex items-center gap-2">
                    {activeRun.status === 'DRAFT' && (
                      <Button
                        loading={approvePayroll.isPending}
                        onClick={() => approvePayroll.mutate(activeRun.id)}
                      >
                        ✓ Duyệt bảng lương
                      </Button>
                    )}
                    {activeRun.status === 'APPROVED' && (
                      <Button
                        variant="primary"
                        loading={payPayroll.isPending}
                        onClick={() => payPayroll.mutate(activeRun.id)}
                      >
                        💳 Xác nhận đã chi trả
                      </Button>
                    )}
                  </div>
                }
              />
              <CardBody className="space-y-4">
                {/* Tổng quan chỉ số */}
                <div className="grid grid-cols-2 sm:grid-cols-4 gap-3 bg-slate-50 p-3 rounded-lg text-sm">
                  <div>
                    <span className="text-xs text-slate-500">Lương cơ bản</span>
                    <p className="font-bold text-slate-800">{tien(activeRun.totalBaseSalary)}</p>
                  </div>
                  <div>
                    <span className="text-xs text-slate-500">Tổng hoa hồng</span>
                    <p className="font-bold text-blue-700">{tien(activeRun.totalCommission)}</p>
                  </div>
                  <div>
                    <span className="text-xs text-slate-500">Thưởng / Phạt</span>
                    <p className="font-bold text-slate-800">
                      +{tien(activeRun.totalBonus)} / -{tien(activeRun.totalDeduction)}
                    </p>
                  </div>
                  <div>
                    <span className="text-xs text-slate-500">Tổng chi trả</span>
                    <p className="font-bold text-emerald-700 text-base">{tien(activeRun.totalNetSalary)}</p>
                  </div>
                </div>

                {/* Bảng chi tiết nhân viên */}
                <div className="overflow-x-auto">
                  <table className="w-full text-xs">
                    <thead className="border-b border-slate-100 bg-slate-50 text-left uppercase text-slate-500">
                      <tr>
                        <th className="px-3 py-2">Nhân viên</th>
                        <th className="px-3 py-2">Phòng ban</th>
                        <th className="px-3 py-2 text-right">Lương cứng</th>
                        <th className="px-3 py-2 text-right">Hoa hồng PT</th>
                        <th className="px-3 py-2 text-right">Hoa hồng Sales</th>
                        <th className="px-3 py-2 text-right">Thưởng/Phạt</th>
                        <th className="px-3 py-2 text-right font-bold">Thực lĩnh</th>
                        <th className="px-3 py-2"></th>
                      </tr>
                    </thead>
                    <tbody className="divide-y divide-slate-100">
                      {activeRun.items?.map((item) => (
                        <tr key={item.id} className="hover:bg-slate-50">
                          <td className="px-3 py-2.5 font-medium text-slate-800">
                            {item.employeeName}
                            <span className="block font-mono text-[10px] text-slate-400">{item.employeeCode}</span>
                          </td>
                          <td className="px-3 py-2.5 text-slate-600">{item.department}</td>
                          <td className="px-3 py-2.5 text-right text-slate-600">{tien(item.baseSalary)}</td>
                          <td className="px-3 py-2.5 text-right text-blue-700">
                            {item.ptSessionsCount > 0 && `${item.ptSessionsCount} buổi `}
                            {tien(item.ptCommission)}
                          </td>
                          <td className="px-3 py-2.5 text-right text-blue-700">{tien(item.salesCommission)}</td>
                          <td className="px-3 py-2.5 text-right text-slate-600">
                            +{tien(item.bonusAmount)} / -{tien(item.deductionAmount)}
                          </td>
                          <td className="px-3 py-2.5 text-right font-bold text-emerald-700">
                            {tien(item.netSalary)}
                          </td>
                          <td className="px-3 py-2.5 text-right">
                            {activeRun.status === 'DRAFT' && (
                              <button
                                onClick={() => setEditItemId(item.id)}
                                className="text-brand-600 hover:text-brand-800 font-semibold"
                              >
                                Sửa
                              </button>
                            )}
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              </CardBody>
            </Card>
          ) : (
            <Card>
              <CardBody>
                <EmptyState title="Chọn một bảng lương bên trái để xem chi tiết" />
              </CardBody>
            </Card>
          )}
        </div>
      </div>

      {editItemId && (
        <HopThoaiSuaLuong
          itemId={editItemId}
          item={activeRun?.items?.find((i) => i.id === editItemId)}
          onClose={() => setEditItemId(null)}
        />
      )}
    </div>
  )
}

function HopThoaiSuaLuong({
  itemId, item, onClose,
}: {
  itemId: number
  item?: PayrollRun['items'][0]
  onClose: () => void
}) {
  const updateItem = useUpdatePayrollItem()
  const [bonus, setBonus] = useState(String(item?.bonusAmount ?? 0))
  const [deduction, setDeduction] = useState(String(item?.deductionAmount ?? 0))
  const [note, setNote] = useState(item?.note ?? '')

  const handleSave = () => {
    updateItem.mutate(
      {
        itemId,
        bonusAmount: Number(bonus),
        deductionAmount: Number(deduction),
        note: note || undefined,
      },
      { onSuccess: onClose }
    )
  }

  return (
    <Modal open={true} title={`Điều chỉnh lương: ${item?.employeeName}`} onClose={onClose}>
      <div className="space-y-4">
        <Input
          label="Tiền thưởng thêm (VND)"
          type="number"
          value={bonus}
          onChange={(e) => setBonus(e.target.value)}
        />
        <Input
          label="Tiền khấu trừ / Phạt (VND)"
          type="number"
          value={deduction}
          onChange={(e) => setDeduction(e.target.value)}
        />
        <Input
          label="Ghi chú điều chỉnh"
          value={note}
          onChange={(e) => setNote(e.target.value)}
          placeholder="Thưởng chuyên cần / Phạt đi muộn..."
        />

        <div className="flex justify-end gap-2 pt-2">
          <Button variant="secondary" onClick={onClose}>Hủy</Button>
          <Button loading={updateItem.isPending} onClick={handleSave}>Lưu điều chỉnh</Button>
        </div>
      </div>
    </Modal>
  )
}

// =============================================================================
// TAB 4: Chi phí & Lợi nhuận (E5 - Expenses & P&L)
// =============================================================================

function TabChiPhiVaLoiNhuan() {
  const today = new Date()
  const [month, setMonth] = useState(today.getMonth() + 1)
  const [year, setYear] = useState(today.getFullYear())
  const [openCreate, setOpenCreate] = useState(false)

  const { data: pnl, isLoading: isPnlLoading } = useProfitLossReport(month, year)
  const { data: expenses, isLoading: isExpLoading } = useExpenses()
  const deleteExpense = useDeleteExpense()

  if (isPnlLoading || isExpLoading) return <Spinner />

  return (
    <div className="space-y-6">
      {/* Bộ lọc tháng & Nút thêm chi phí */}
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
        <div className="flex items-center gap-3">
          <span className="text-sm font-semibold text-slate-800">Kỳ kinh doanh:</span>
          <select
            value={month}
            onChange={(e) => setMonth(Number(e.target.value))}
            className="rounded-lg border border-slate-300 px-3 py-1.5 text-sm"
          >
            {Array.from({ length: 12 }, (_, i) => i + 1).map((m) => (
              <option key={m} value={m}>Tháng {m}</option>
            ))}
          </select>
          <select
            value={year}
            onChange={(e) => setYear(Number(e.target.value))}
            className="rounded-lg border border-slate-300 px-3 py-1.5 text-sm"
          >
            {[2025, 2026, 2027].map((y) => (
              <option key={y} value={y}>Năm {y}</option>
            ))}
          </select>
        </div>

        <Button onClick={() => setOpenCreate(true)}>+ Ghi nhận khoản chi mới</Button>
      </div>

      {/* Báo cáo P&L (Profit & Loss) */}
      <div className="grid grid-cols-1 sm:grid-cols-4 gap-4">
        <Card>
          <CardBody>
            <span className="text-xs font-semibold uppercase text-slate-500">1. Tổng Doanh thu thực</span>
            <p className="mt-2 text-2xl font-bold text-emerald-700">{tien(pnl?.cashRevenue ?? 0)}</p>
            <p className="mt-1 text-xs text-slate-400">Doanh thu dồn tích: {tien(pnl?.accrualRevenue ?? 0)}</p>
          </CardBody>
        </Card>

        <Card>
          <CardBody>
            <span className="text-xs font-semibold uppercase text-slate-500">2. Chi phí vận hành</span>
            <p className="mt-2 text-2xl font-bold text-red-600">{tien(pnl?.operatingExpenses ?? 0)}</p>
            <p className="mt-1 text-xs text-slate-400">Điện, nước, mặt bằng, sửa máy</p>
          </CardBody>
        </Card>

        <Card>
          <CardBody>
            <span className="text-xs font-semibold uppercase text-slate-500">3. Chi phí lương nhân sự</span>
            <p className="mt-2 text-2xl font-bold text-amber-600">{tien(pnl?.salaryExpenses ?? 0)}</p>
            <p className="mt-1 text-xs text-slate-400">Bảng lương đã duyệt trong kỳ</p>
          </CardBody>
        </Card>

        <Card>
          <CardBody>
            <span className="text-xs font-semibold uppercase text-slate-500">4. Lợi nhuận ròng (P&L)</span>
            <p className={`mt-2 text-2xl font-bold ${(pnl?.netProfitCashBasis ?? 0) >= 0 ? 'text-emerald-700' : 'text-red-700'}`}>
              {tien(pnl?.netProfitCashBasis ?? 0)}
            </p>
            <p className="mt-1 text-xs text-slate-400">
              {(pnl?.netProfitCashBasis ?? 0) >= 0 ? 'Lãi ròng' : 'Thâm hụt / Lỗ'}
            </p>
          </CardBody>
        </Card>
      </div>

      {/* Danh sách các khoản chi */}
      <Card>
        <CardHeader
          title="Sổ theo dõi chi phí phòng gym"
          subtitle="Các khoản chi mặt bằng, điện nước, vật tư và bảo trì thiết bị"
          action={<Badge tone="red">{expenses?.length ?? 0} khoản chi</Badge>}
        />
        <CardBody className="p-0">
          {!expenses || expenses.length === 0 ? (
            <EmptyState title="Chưa có khoản chi nào được ghi nhận" />
          ) : (
            <div className="overflow-x-auto">
              <table className="w-full text-sm">
                <thead className="border-b border-slate-100 bg-slate-50 text-left text-xs uppercase text-slate-500">
                  <tr>
                    <th className="px-5 py-3">Mã chi phí</th>
                    <th className="px-5 py-3">Danh mục</th>
                    <th className="px-5 py-3">Tiêu đề khoản chi</th>
                    <th className="px-5 py-3">Ngày chi</th>
                    <th className="px-5 py-3 text-right">Số tiền</th>
                    <th className="px-5 py-3">Hình thức</th>
                    <th className="px-5 py-3">Người ghi</th>
                    <th className="px-5 py-3"></th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-100">
                  {expenses.map((e) => (
                    <tr key={e.id} className="hover:bg-slate-50">
                      <td className="px-5 py-3 font-mono text-xs text-slate-600">{e.expenseNo}</td>
                      <td className="px-5 py-3">
                        <Badge tone="gray">{tenDanhMucChiPhi(e.category)}</Badge>
                      </td>
                      <td className="px-5 py-3 font-medium text-slate-800">{e.title}</td>
                      <td className="px-5 py-3 text-slate-600">{e.spentAt}</td>
                      <td className="px-5 py-3 text-right font-bold text-red-600">-{tien(e.amount)}</td>
                      <td className="px-5 py-3 text-xs text-slate-600">{tenHinhThucThanhToan(e.paymentMethod)}</td>
                      <td className="px-5 py-3 text-xs text-slate-500">{e.spentByName ?? '—'}</td>
                      <td className="px-5 py-3 text-right">
                        <button
                          onClick={() => deleteExpense.mutate(e.id)}
                          className="text-xs text-red-600 hover:text-red-800 font-semibold"
                        >
                          Xóa
                        </button>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </CardBody>
      </Card>

      <HopThoaiThemChiPhi open={openCreate} onClose={() => setOpenCreate(false)} />
    </div>
  )
}

function HopThoaiThemChiPhi({ open, onClose }: { open: boolean; onClose: () => void }) {
  const createExp = useCreateExpense()

  const [category, setCategory] = useState<ExpenseCategory>('UTILITIES')
  const [title, setTitle] = useState('')
  const [amount, setAmount] = useState('')
  const [spentAt, setSpentAt] = useState(new Date().toISOString().slice(0, 10))
  const [paymentMethod, setPaymentMethod] = useState<PaymentMethod>('BANK_TRANSFER')
  const [note, setNote] = useState('')

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault()
    if (!title || !amount) return

    createExp.mutate(
      {
        category,
        title: title.trim(),
        amount: Number(amount),
        spentAt,
        paymentMethod,
        note: note.trim() || undefined,
      },
      {
        onSuccess: () => {
          setTitle('')
          setAmount('')
          setNote('')
          onClose()
        },
      }
    )
  }

  return (
    <Modal open={open} title="Ghi nhận khoản chi mới" onClose={onClose}>
      <form onSubmit={handleSubmit} className="space-y-4">
        <div>
          <label className="mb-1 block text-sm font-medium text-slate-700">Danh mục chi phí *</label>
          <select
            value={category}
            onChange={(e) => setCategory(e.target.value as ExpenseCategory)}
            className="w-full rounded-lg border border-slate-300 px-3 py-2 text-sm"
          >
            <option value="UTILITIES">Điện / Nước / Internet (UTILITIES)</option>
            <option value="RENT">Tiền thuê mặt bằng (RENT)</option>
            <option value="EQUIPMENT_MAINTENANCE">Bảo trì máy móc / Thiết bị (EQUIPMENT_MAINTENANCE)</option>
            <option value="MARKETING">Quảng cáo / Marketing (MARKETING)</option>
            <option value="SUPPLIES">Vật tư / Dụng cụ / Nước uống (SUPPLIES)</option>
            <option value="OTHER">Chi phí khác (OTHER)</option>
          </select>
        </div>

        <Input
          label="Tiêu đề khoản chi *"
          value={title}
          onChange={(e) => setTitle(e.target.value)}
          placeholder="VD: Hóa đơn tiền điện tháng 8/2026"
          required
        />

        <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
          <Input
            label="Số tiền (VND) *"
            type="number"
            value={amount}
            onChange={(e) => setAmount(e.target.value)}
            placeholder="15000000"
            required
          />
          <Input
            label="Ngày chi *"
            type="date"
            value={spentAt}
            onChange={(e) => setSpentAt(e.target.value)}
            required
          />
        </div>

        <div>
          <label className="mb-1 block text-sm font-medium text-slate-700">Hình thức thanh toán</label>
          <select
            value={paymentMethod}
            onChange={(e) => setPaymentMethod(e.target.value as PaymentMethod)}
            className="w-full rounded-lg border border-slate-300 px-3 py-2 text-sm"
          >
            <option value="BANK_TRANSFER">Chuyển khoản (BANK_TRANSFER)</option>
            <option value="CASH">Tiền mặt (CASH)</option>
            <option value="CARD_POS">Thẻ POS (CARD_POS)</option>
            <option value="E_WALLET">Ví điện tử (E_WALLET)</option>
          </select>
        </div>

        <Input
          label="Ghi chú"
          value={note}
          onChange={(e) => setNote(e.target.value)}
          placeholder="Mã hóa đơn EVN / Ghi chú thêm..."
        />

        {createExp.error instanceof ApiError && <Alert tone="error">{createExp.error.message}</Alert>}

        <div className="flex justify-end gap-2 pt-2">
          <Button variant="secondary" type="button" onClick={onClose}>Hủy</Button>
          <Button type="submit" loading={createExp.isPending}>Lưu khoản chi</Button>
        </div>
      </form>
    </Modal>
  )
}

function tenDanhMucChiPhi(cat: string) {
  switch (cat) {
    case 'RENT': return 'Mặt bằng'
    case 'UTILITIES': return 'Điện / Nước'
    case 'EQUIPMENT_MAINTENANCE': return 'Bảo trì máy'
    case 'SALARY': return 'Lương'
    case 'SUPPLIES': return 'Vật tư'
    case 'MARKETING': return 'Marketing'
    default: return 'Khác'
  }
}
