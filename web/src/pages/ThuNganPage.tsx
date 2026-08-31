import { useState } from 'react'
import {
  useCaHienTai, useCongNo, useDongCa, useKhoanThu, useMoCa, useThuTien,
} from '@/hooks/useBilling'
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
import type { PaymentMethod } from '@/api/types-cde'

/** Màn hình thu ngân: ca làm việc, xuất hóa đơn, thu tiền. */
export function ThuNganPage() {
  const { data: ca, isLoading } = useCaHienTai()
  const { data: congNo } = useCongNo()

  const [thuTienChoHoaDon, setThuTienChoHoaDon] = useState<number | null>(null)

  if (isLoading) return <Spinner />

  return (
    <div className="space-y-6">
      <h1 className="text-2xl font-bold text-slate-900">Thu ngân</h1>

      <div className="grid gap-6 lg:grid-cols-2">
        <CaLamViec />
        <ChoThanhToan />
      </div>

      <Card>
        <CardHeader title="Công nợ" subtitle="Hóa đơn chưa thu đủ"
                    action={<Badge tone="amber">{congNo?.length ?? 0} hóa đơn</Badge>} />
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
          <Input label="Tiền mặt đầu ca" type="number" value={tienDauCa}
                 onChange={(e) => setTienDauCa(e.target.value)} />
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
        <CardHeader title="Ca làm việc" subtitle={`Mở lúc ${ngayGio(ca.openedAt)}`}
                    action={<Badge tone="green">Đang mở</Badge>} />
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

      <Modal open={moHopThoaiDong} title="Đóng ca và đối soát tiền mặt"
             onClose={() => setMoHopThoaiDong(false)}>
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

          <Input label="Tiền mặt đếm được thực tế trong két" type="number" value={tienDemDuoc}
                 onChange={(e) => setTienDemDuoc(e.target.value)} autoFocus />

          <Input label="Lý do nếu lệch" value={lyDoLech}
                 onChange={(e) => setLyDoLech(e.target.value)}
                 placeholder="Trả thừa tiền khách lúc 15h, đã ghi sổ tay"
                 hint="Bỏ trống nếu khớp sổ" />

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
            <Button loading={dongCa.isPending} disabled={!tienDemDuoc}
                    onClick={() => dongCa.mutate(
                      { countedCash: Number(tienDemDuoc), reason: lyDoLech || undefined },
                      { onSuccess: (kq) => { if (kq.status === 'CLOSED') setMoHopThoaiDong(false) } })}>
              Chốt ca
            </Button>
          </div>
        </div>
      </Modal>
    </>
  )
}

function HopThoaiThuTien({ invoiceId, conNo, coCaDangMo, onClose }: {
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
    thuTien.mutate({ invoiceId, amount: Number(soTien), method: hinhThuc },
      { onSuccess: () => { setSoTien(''); onClose() } })
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

        <Input label="Số tiền thu" type="number" value={soTien} autoFocus
               onChange={(e) => setSoTien(e.target.value)} />

        <div>
          <label className="mb-1 block text-sm font-medium text-slate-700">Hình thức</label>
          <select value={hinhThuc} onChange={(e) => setHinhThuc(e.target.value as PaymentMethod)}
                  className="w-full rounded-lg border border-slate-300 px-3 py-2 text-sm">
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
