import { useState } from 'react'
import { Link } from 'react-router-dom'
import { useMyRegistrations, useXinBaoLuu } from '@/hooks/useRegistrations'
import { useAuth } from '@/stores/auth'
import { Card, CardBody, CardHeader } from '@/components/ui/Card'
import { Badge, tonesForRegistration } from '@/components/ui/Badge'
import { Button } from '@/components/ui/Button'
import { Input } from '@/components/ui/Input'
import { Alert } from '@/components/ui/Alert'
import { Modal } from '@/components/ui/Modal'
import { EmptyState, Spinner } from '@/components/ui/Spinner'
import { ApiError } from '@/api/client'
import {
  ngay, soNgayConLai, tien, tenLoaiGoi,
  tenTrangThaiBaoLuu, tenTrangThaiHopDong,
} from '@/lib/format'
import type { Registration } from '@/api/types'

export function GoiCuaToiPage() {
  const user = useAuth((s) => s.user)
  const { data: hopDongs, isLoading } = useMyRegistrations()
  const [xinBaoLuuCho, setXinBaoLuuCho] = useState<Registration | null>(null)

  if (isLoading) return <Spinner />

  return (
    <div className="space-y-6">
      <h1 className="text-2xl font-bold text-slate-900">Gói tập của tôi</h1>

      {/* Có tài khoản nhưng chưa mua gói bao giờ — trạng thái hoàn toàn hợp lệ */}
      {!user?.isMember && (
        <Alert tone="info">
          Bạn chưa mua gói tập nào. <Link to="/" className="font-medium underline">Xem bảng giá</Link> để bắt đầu.
        </Alert>
      )}

      {hopDongs?.length === 0 && user?.isMember && (
        <EmptyState title="Chưa có hợp đồng nào" />
      )}

      <div className="space-y-4">
        {hopDongs?.map((hd) => (
          <TheHopDong key={hd.id} hopDong={hd} onXinBaoLuu={() => setXinBaoLuuCho(hd)} />
        ))}
      </div>

      <HopThoaiBaoLuu hopDong={xinBaoLuuCho} onClose={() => setXinBaoLuuCho(null)} />
    </div>
  )
}

function TheHopDong({ hopDong, onXinBaoLuu }: {
  hopDong: Registration
  onXinBaoLuu: () => void
}) {
  const conLai = soNgayConLai(hopDong.endDate)
  const dangChay = hopDong.status === 'ACTIVE'
  // Vé lẻ (vd vé 1 ngày): hiệu lực và hết hạn cùng một ngày lịch — gộp lại một
  // dòng cho rõ nghĩa, thay vì hiện "từ 23/08 đến 23/08" gây hiểu lầm là đã qua.
  const chiTrongMotNgay = hopDong.startDate === hopDong.endDate
  // Mỗi hợp đồng chỉ được bảo lưu MỘT lần — đã dùng thì ẩn nút đi
  const chuaTungBaoLuu = hopDong.freeze === null

  return (
    <Card>
      <CardHeader
        title={hopDong.membershipName}
        subtitle={<span className="font-mono text-xs">{hopDong.registrationCode}</span>}
        action={
          <Badge tone={tonesForRegistration(hopDong.status)}>
            {tenTrangThaiHopDong(hopDong.status)}
          </Badge>
        }
      />
      <CardBody className="space-y-4">
        <dl className="grid grid-cols-2 gap-x-6 gap-y-3 text-sm sm:grid-cols-4">
          <O nhan="Loại gói" giaTri={tenLoaiGoi(hopDong.packageType)} />
          <O nhan="Ngày ký" giaTri={ngay(hopDong.contractDate)} />
          {chiTrongMotNgay ? (
            <O nhan="Có hiệu lực" giaTri={`Trong ngày ${ngay(hopDong.startDate)}`} />
          ) : (
            <>
              <O nhan="Hiệu lực từ" giaTri={ngay(hopDong.startDate)} />
              <O nhan="Hết hạn" giaTri={ngay(hopDong.endDate)} />
            </>
          )}

          {hopDong.sessionsTotal != null && (
            <O nhan="Số buổi PT" giaTri={`${hopDong.sessionsTotal} buổi`} />
          )}
          <O nhan="Giá niêm yết" giaTri={tien(hopDong.listPrice)} />
          {Number(hopDong.discountAmount) > 0 && (
            <O nhan="Giảm giá" giaTri={`− ${tien(hopDong.discountAmount)}`} />
          )}
          <O nhan="Thành tiền" giaTri={tien(hopDong.finalPrice)} noiBat />
        </dl>

        {Number(hopDong.discountAmount) > 0 && hopDong.discountReason && (
          <p className="text-xs text-slate-500">Áp dụng: {hopDong.discountReason}</p>
        )}

        {dangChay && conLai !== null && (
          <Alert tone={conLai <= 14 ? 'error' : 'info'}>
            {conLai < 0
              ? 'Gói đã hết hạn'
              : conLai === 0
                ? 'Hết hạn hôm nay'
                : `Còn ${conLai} ngày sử dụng${conLai <= 14 ? ' — nên gia hạn sớm' : ''}`}
          </Alert>
        )}

        {hopDong.status === 'PENDING_PAYMENT' && (
          <Alert tone="info">
            Vui lòng thanh toán tại quầy. Lễ tân kích hoạt xong thì gói mới bắt đầu tính ngày.
          </Alert>
        )}

        {hopDong.freeze && (
          <div className="rounded-lg border border-blue-200 bg-blue-50 px-4 py-3 text-sm">
            <p className="font-medium text-blue-900">
              Bảo lưu: {ngay(hopDong.freeze.fromDate)} → {ngay(hopDong.freeze.toDate)}
              {hopDong.freeze.days && ` (${hopDong.freeze.days} ngày)`}
            </p>
            <p className="mt-0.5 text-blue-700">
              Trạng thái: {tenTrangThaiBaoLuu(hopDong.freeze.status)} · {hopDong.freeze.reason}
            </p>
          </div>
        )}

        {dangChay && chuaTungBaoLuu && (
          <div className="flex justify-end">
            <Button variant="secondary" onClick={onXinBaoLuu}>Xin bảo lưu</Button>
          </div>
        )}
      </CardBody>
    </Card>
  )
}

function O({ nhan, giaTri, noiBat }: { nhan: string; giaTri: string; noiBat?: boolean }) {
  return (
    <div>
      <dt className="text-xs text-slate-500">{nhan}</dt>
      <dd className={noiBat ? 'font-semibold text-brand-700' : 'font-medium text-slate-800'}>
        {giaTri}
      </dd>
    </div>
  )
}

function HopThoaiBaoLuu({ hopDong, onClose }: {
  hopDong: Registration | null
  onClose: () => void
}) {
  const xinBaoLuu = useXinBaoLuu()
  const [form, setForm] = useState({ fromDate: '', toDate: '', reason: '', reasonType: 'PERSONAL' })

  const gui = () => {
    if (!hopDong) return
    xinBaoLuu.mutate({ id: hopDong.id, ...form }, { onSuccess: onClose })
  }

  return (
    <Modal open={!!hopDong} title="Xin bảo lưu gói tập" onClose={onClose}>
      <div className="space-y-4">
        <Alert tone="info">
          Mỗi hợp đồng chỉ được bảo lưu <b>một lần</b>. Ngày hết hạn sẽ được đẩy lùi
          đúng bằng số ngày bảo lưu, bạn không mất ngày đã trả tiền.
        </Alert>

        <div className="grid grid-cols-2 gap-3">
          <Input label="Từ ngày" type="date" value={form.fromDate}
                 onChange={(e) => setForm({ ...form, fromDate: e.target.value })} />
          <Input label="Đến ngày" type="date" value={form.toDate}
                 onChange={(e) => setForm({ ...form, toDate: e.target.value })} />
        </div>

        <div>
          <label className="mb-1 block text-sm font-medium text-slate-700">Loại lý do</label>
          <select
            value={form.reasonType}
            onChange={(e) => setForm({ ...form, reasonType: e.target.value })}
            className="w-full rounded-lg border border-slate-300 px-3 py-2 text-sm"
          >
            <option value="PERSONAL">Lý do cá nhân</option>
            <option value="MEDICAL">Lý do sức khỏe</option>
            <option value="TRAVEL">Đi công tác, du lịch</option>
            <option value="OTHER">Khác</option>
          </select>
        </div>

        <Input label="Lý do cụ thể" value={form.reason}
               onChange={(e) => setForm({ ...form, reason: e.target.value })}
               placeholder="Đi công tác nước ngoài 1 tháng" />

        {xinBaoLuu.error instanceof ApiError && (
          <Alert tone="error">{xinBaoLuu.error.message}</Alert>
        )}

        <div className="flex justify-end gap-2">
          <Button variant="secondary" onClick={onClose}>Hủy</Button>
          <Button onClick={gui} loading={xinBaoLuu.isPending}>Gửi yêu cầu</Button>
        </div>
      </div>
    </Modal>
  )
}
