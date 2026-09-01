import { useState } from 'react'
import { usePendingPaymentRegistrations, useDeskRegister } from '@/hooks/useRegistrations'
import { useXacNhanGoiTap } from '@/hooks/useBilling'
import { useMemberships } from '@/hooks/useMemberships'
import { Card, CardBody, CardHeader } from '@/components/ui/Card'
import { Badge } from '@/components/ui/Badge'
import { Button } from '@/components/ui/Button'
import { Input } from '@/components/ui/Input'
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
  const [moModalDangKy, setMoModalDangKy] = useState(false)

  return (
    <Card>
      <CardHeader
        title="Chờ xác nhận"
        subtitle="Hội viên vừa đăng ký gói, đang chờ thanh toán"
        action={
          <div className="flex items-center gap-2">
            <Badge tone="amber">{dsChoThanhToan?.length ?? 0}</Badge>
            <Button variant="secondary" onClick={() => setMoModalDangKy(true)}>
              + Đăng ký tại quầy
            </Button>
          </div>
        }
      />
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
      <HopThoaiDangKyTaiQuay open={moModalDangKy} onClose={() => setMoModalDangKy(false)} />
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

function HopThoaiDangKyTaiQuay({ open, onClose }: {
  open: boolean
  onClose: () => void
}) {
  const { data: goiTap } = useMemberships()
  const deskRegister = useDeskRegister()

  const [fullName, setFullName] = useState('')
  const [phone, setPhone] = useState('')
  const [email, setEmail] = useState('')
  const [membershipId, setMembershipId] = useState<number | ''>('')
  const [discountAmount, setDiscountAmount] = useState<number | ''>('')
  const [discountReason, setDiscountReason] = useState('')
  const [note, setNote] = useState('')
  const [payNow, setPayNow] = useState(true)
  const [paymentMethod, setPaymentMethod] = useState<PaymentMethod>('CASH')

  const selectedGoi = goiTap?.find((g) => g.id === membershipId)
  const listPrice = selectedGoi?.price ?? 0
  const discount = Number(discountAmount) || 0
  const finalPrice = Math.max(0, listPrice - discount)

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault()
    if (!membershipId || !fullName || !phone) return

    deskRegister.mutate({
      fullName,
      phone,
      email: email || undefined,
      membershipId: Number(membershipId),
      discountAmount: discount > 0 ? discount : undefined,
      discountReason: discount > 0 ? discountReason : undefined,
      note: note || undefined,
      payNow,
      paymentMethod: payNow ? paymentMethod : undefined,
    }, {
      onSuccess: () => {
        setFullName('')
        setPhone('')
        setEmail('')
        setMembershipId('')
        setDiscountAmount('')
        setDiscountReason('')
        setNote('')
        setPayNow(true)
        onClose()
      },
    })
  }

  return (
    <Modal open={open} title="Đăng ký gói tập tại quầy (Kênh 1)" onClose={onClose}>
      <form onSubmit={handleSubmit} className="space-y-4">
        <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
          <Input
            label="Họ và tên khách hàng *"
            value={fullName}
            onChange={(e) => setFullName(e.target.value)}
            placeholder="Nguyễn Văn A"
            required
          />
          <Input
            label="Số điện thoại *"
            value={phone}
            onChange={(e) => setPhone(e.target.value)}
            placeholder="0912345678"
            required
          />
        </div>

        <Input
          label="Email (tùy chọn)"
          type="email"
          value={email}
          onChange={(e) => setEmail(e.target.value)}
          placeholder="khachhang@example.com"
        />

        <div>
          <label className="mb-1 block text-sm font-medium text-slate-700">
            Chọn gói tập *
          </label>
          <select
            value={membershipId}
            onChange={(e) => setMembershipId(e.target.value ? Number(e.target.value) : '')}
            className="w-full rounded-lg border border-slate-300 px-3 py-2 text-sm"
            required
          >
            <option value="">-- Chọn gói tập --</option>
            {goiTap?.map((g) => (
              <option key={g.id} value={g.id}>
                {g.name} - {tien(g.price)} ({g.durationDays ? `${g.durationDays} ngày` : `${g.sessionCount} buổi`})
              </option>
            ))}
          </select>
        </div>

        {selectedGoi && (
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
            <Input
              label="Số tiền giảm (đ)"
              type="number"
              value={discountAmount}
              onChange={(e) => setDiscountAmount(e.target.value ? Number(e.target.value) : '')}
              placeholder="0"
            />
            <Input
              label="Lý do / Chương trình KM"
              value={discountReason}
              onChange={(e) => setDiscountReason(e.target.value)}
              placeholder="Khai xuân / Ưu đãi hè"
              required={discount > 0}
            />
          </div>
        )}

        {selectedGoi && (
          <div className="rounded-lg bg-slate-50 p-3 text-sm flex justify-between items-center">
            <span className="text-slate-600">Thành tiền phải thu:</span>
            <span className="text-base font-bold text-brand-700">{tien(finalPrice)}</span>
          </div>
        )}

        <div className="border-t border-slate-200 pt-3">
          <label className="flex items-center gap-2 text-sm font-medium text-slate-800 cursor-pointer">
            <input
              type="checkbox"
              checked={payNow}
              onChange={(e) => setPayNow(e.target.checked)}
              className="rounded border-slate-300 text-brand-600 focus:ring-brand-500"
            />
            Thu tiền và kích hoạt ngay tại quầy (A3)
          </label>
        </div>

        {payNow && (
          <div>
            <label className="mb-1 block text-sm font-medium text-slate-700">
              Hình thức thanh toán
            </label>
            <select
              value={paymentMethod}
              onChange={(e) => setPaymentMethod(e.target.value as PaymentMethod)}
              className="w-full rounded-lg border border-slate-300 px-3 py-2 text-sm"
            >
              {(['CASH', 'BANK_TRANSFER', 'VIETQR', 'CARD_POS', 'E_WALLET'] as PaymentMethod[])
                .map((m) => <option key={m} value={m}>{tenHinhThucThanhToan(m)}</option>)}
            </select>
          </div>
        )}

        <Input
          label="Ghi chú"
          value={note}
          onChange={(e) => setNote(e.target.value)}
          placeholder="Ghi chú thêm (nếu có)"
        />

        {deskRegister.error instanceof ApiError && (
          <Alert tone="error">
            {deskRegister.error.fields
              ? Object.values(deskRegister.error.fields).join(', ')
              : deskRegister.error.message}
          </Alert>
        )}

        <div className="flex justify-end gap-2">
          <Button variant="secondary" type="button" onClick={onClose}>
            Hủy
          </Button>
          <Button type="submit" loading={deskRegister.isPending}>
            {payNow ? 'Tạo & Kích hoạt' : 'Tạo hợp đồng'}
          </Button>
        </div>
      </form>
    </Modal>
  )
}
