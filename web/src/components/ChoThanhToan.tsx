import { useState } from 'react'
import { usePendingPaymentRegistrations } from '@/hooks/useRegistrations'
import { useXacNhanGoiTap } from '@/hooks/useBilling'
import { Card, CardBody, CardHeader } from '@/components/ui/Card'
import { Badge } from '@/components/ui/Badge'
import { Button } from '@/components/ui/Button'
import { Alert } from '@/components/ui/Alert'
import { Modal } from '@/components/ui/Modal'
import { EmptyState, Spinner } from '@/components/ui/Spinner'
import { ApiError } from '@/api/client'
import { tien, ngay } from '@/lib/format'
import { tenHinhThucThanhToan } from '@/lib/format-cde'
import type { PaymentMethod } from '@/api/types-cde'
import type { Registration } from '@/api/types'

/**
 * Hợp đồng đã chốt mua nhưng chưa thu tiền.
 *
 * Đây là danh sách lễ tân CẦN THẤY ngay khi hội viên tự mua gói trên app rồi
 * ra quầy trả tiền — không có danh sách này thì lễ tân không cách nào biết
 * hợp đồng nào đang chờ, phải hỏi hội viên đọc mã số. Dùng chung ở cả màn
 * hình quầy (nơi lễ tân đứng cả ngày) và trang Thu ngân.
 *
 * Nút "Xác nhận gói tập" gộp xuất hóa đơn + thu đủ tiền vào một lần bấm —
 * lễ tân chỉ cần chọn hình thức đã nhận tiền, không cần biết khái niệm
 * "hóa đơn" ở phía sau.
 */
export function ChoThanhToan() {
  const { data: dsChoThanhToan, isLoading } = usePendingPaymentRegistrations()
  const [xacNhanCho, setXacNhanCho] = useState<Registration | null>(null)

  return (
    <Card>
      <CardHeader title="Chờ xác nhận"
                  subtitle="Hội viên vừa đăng ký gói, đang chờ thanh toán"
                  action={<Badge tone="amber">{dsChoThanhToan?.length ?? 0}</Badge>} />
      <CardBody className="p-0">
        {isLoading ? (
          <Spinner />
        ) : dsChoThanhToan?.length === 0 ? (
          <EmptyState title="Không có hợp đồng nào đang chờ xác nhận" />
        ) : (
          <ul className="divide-y divide-slate-100">
            {dsChoThanhToan?.map((hd) => (
              <li key={hd.id} className="flex items-center justify-between gap-3 px-5 py-3">
                <div>
                  <p className="font-medium text-slate-800">{hd.memberName}</p>
                  <p className="text-xs text-slate-500">
                    <span className="font-mono">{hd.memberCode}</span> · {hd.membershipName} ·{' '}
                    {tien(hd.finalPrice)} · ký {ngay(hd.contractDate)}
                  </p>
                </div>
                <Button onClick={() => setXacNhanCho(hd)}>Xác nhận gói tập</Button>
              </li>
            ))}
          </ul>
        )}
      </CardBody>

      <HopThoaiXacNhan hopDong={xacNhanCho} onClose={() => setXacNhanCho(null)} />
    </Card>
  )
}

function HopThoaiXacNhan({ hopDong, onClose }: {
  hopDong: Registration | null
  onClose: () => void
}) {
  const xacNhan = useXacNhanGoiTap()
  const [hinhThuc, setHinhThuc] = useState<PaymentMethod>('CASH')

  const gui = () => {
    if (!hopDong) return
    xacNhan.mutate({ registrationId: hopDong.id, method: hinhThuc }, { onSuccess: onClose })
  }

  return (
    <Modal open={!!hopDong} title="Xác nhận gói tập" onClose={onClose}>
      <div className="space-y-4">
        {hopDong && (
          <div className="rounded-lg bg-slate-50 p-3 text-sm">
            <p className="font-medium text-slate-800">{hopDong.memberName}</p>
            <p className="text-slate-500">
              {hopDong.membershipName} · <b>{tien(hopDong.finalPrice)}</b>
            </p>
          </div>
        )}

        <div>
          <label className="mb-1 block text-sm font-medium text-slate-700">
            Đã nhận tiền bằng hình thức
          </label>
          <select value={hinhThuc} onChange={(e) => setHinhThuc(e.target.value as PaymentMethod)}
                  className="w-full rounded-lg border border-slate-300 px-3 py-2 text-sm">
            {(['CASH', 'BANK_TRANSFER', 'VIETQR', 'CARD_POS', 'E_WALLET'] as PaymentMethod[])
              .map((m) => <option key={m} value={m}>{tenHinhThucThanhToan(m)}</option>)}
          </select>
        </div>

        <Alert tone="info">
          Xác nhận xong hợp đồng <b>tự kích hoạt</b> ngay — hội viên vào tập được luôn.
        </Alert>

        {xacNhan.error instanceof ApiError && <Alert tone="error">{xacNhan.error.message}</Alert>}

        <div className="flex justify-end gap-2">
          <Button variant="secondary" onClick={onClose}>Hủy</Button>
          <Button onClick={gui} loading={xacNhan.isPending}>Xác nhận</Button>
        </div>
      </div>
    </Modal>
  )
}
