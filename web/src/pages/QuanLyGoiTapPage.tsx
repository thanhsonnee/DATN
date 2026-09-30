import { useState } from 'react'
import {
  useMembershipsAdmin, useCreateMembership, useUpdateMembership, useDeleteMembership,
} from '@/hooks/useMemberships'
import { Card, CardBody } from '@/components/ui/Card'
import { Button } from '@/components/ui/Button'
import { Input } from '@/components/ui/Input'
import { Badge } from '@/components/ui/Badge'
import { Alert } from '@/components/ui/Alert'
import { Modal } from '@/components/ui/Modal'
import { EmptyState, Spinner } from '@/components/ui/Spinner'
import { ApiError } from '@/api/client'
import { tien, tenLoaiGoi } from '@/lib/format'
import type { MembershipAdmin, MembershipSellStatus, PackageType } from '@/api/types'

/**
 * Admin quản lý gói tập — Create/Update/Delete. Khác trang Bảng giá công khai
 * (BangGiaPage, chỉ đọc): trang này thấy CẢ gói đã ngừng bán (ARCHIVED) và có
 * form sửa/xóa. Xóa là xóa mềm — hợp đồng cũ đã dùng gói không bị ảnh hưởng.
 */
export function QuanLyGoiTapPage() {
  const { data: goiTap, isLoading } = useMembershipsAdmin()
  const deleteGoi = useDeleteMembership()

  const [openCreate, setOpenCreate] = useState(false)
  const [dangSua, setDangSua] = useState<MembershipAdmin | null>(null)

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between gap-4">
        <div>
          <h1 className="text-2xl font-bold text-slate-900">Quản lý gói tập</h1>
          <p className="text-sm text-slate-500">
            Đổi giá, thêm gói mới hoặc ngừng bán — hợp đồng cũ của hội viên không bị ảnh hưởng
          </p>
        </div>
        <Button onClick={() => setOpenCreate(true)}>+ Thêm gói mới</Button>
      </div>

      {deleteGoi.error instanceof ApiError && <Alert tone="error">{deleteGoi.error.message}</Alert>}

      {isLoading ? (
        <Spinner />
      ) : !goiTap || goiTap.length === 0 ? (
        <EmptyState title="Chưa có gói tập nào" hint="Bấm “Thêm gói mới” để bắt đầu" />
      ) : (
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {goiTap.map((goi) => (
            <Card key={goi.id} className={goi.status === 'ARCHIVED' ? 'opacity-60' : ''}>
              <CardBody className="flex flex-col">
                <div className="mb-3 flex items-start justify-between gap-2">
                  <div>
                    <h3 className="font-semibold text-slate-900">{goi.name}</h3>
                    <p className="mt-0.5 font-mono text-xs text-slate-400">{goi.code}</p>
                  </div>
                  <div className="flex flex-col items-end gap-1">
                    {goi.includesTrainer && <Badge tone="blue">Có PT</Badge>}
                    <Badge tone={goi.status === 'ACTIVE' ? 'green' : 'gray'}>
                      {goi.status === 'ACTIVE' ? 'Đang bán' : 'Ngừng bán'}
                    </Badge>
                  </div>
                </div>

                <p className="mb-3 text-2xl font-bold text-brand-700">{tien(goi.price)}</p>

                <dl className="mb-4 space-y-1.5 text-sm text-slate-600">
                  <Dong nhan="Loại gói" giaTri={tenLoaiGoi(goi.packageType)} />
                  {goi.durationDays && <Dong nhan="Thời hạn" giaTri={`${goi.durationDays} ngày`} />}
                  {goi.sessionCount && <Dong nhan="Số buổi PT" giaTri={`${goi.sessionCount} buổi`} />}
                  <Dong
                    nhan="Bảo lưu"
                    giaTri={goi.maxFreezeDays > 0 ? `Tối đa ${goi.maxFreezeDays} ngày` : 'Không áp dụng'}
                  />
                </dl>

                <div className="mt-auto flex gap-2">
                  <Button variant="secondary" className="flex-1" onClick={() => setDangSua(goi)}>
                    Sửa
                  </Button>
                  <button
                    onClick={() => {
                      if (window.confirm(`Xóa gói "${goi.name}"? Hợp đồng cũ đã dùng gói này không bị ảnh hưởng.`)) {
                        deleteGoi.mutate(goi.id)
                      }
                    }}
                    className="rounded-lg border border-red-200 px-3 text-sm font-medium text-red-600 hover:bg-red-50"
                  >
                    Xóa
                  </button>
                </div>
              </CardBody>
            </Card>
          ))}
        </div>
      )}

      <HopThoaiGoiTap open={openCreate} goi={null} onClose={() => setOpenCreate(false)} />

      {dangSua && (
        <HopThoaiGoiTap open goi={dangSua} onClose={() => setDangSua(null)} />
      )}
    </div>
  )
}

/** Dùng chung cho cả tạo mới lẫn sửa — `goi == null` là tạo mới. */
function HopThoaiGoiTap({ open, goi, onClose }: {
  open: boolean
  goi: MembershipAdmin | null
  onClose: () => void
}) {
  const createGoi = useCreateMembership()
  const updateGoi = useUpdateMembership()
  const dangSua = goi != null

  const [code, setCode] = useState(goi?.code ?? '')
  const [name, setName] = useState(goi?.name ?? '')
  const [packageType, setPackageType] = useState<PackageType>(goi?.packageType ?? 'TIME_BASED')
  const [durationDays, setDurationDays] = useState(goi?.durationDays ? String(goi.durationDays) : '')
  const [sessionCount, setSessionCount] = useState(goi?.sessionCount ? String(goi.sessionCount) : '')
  const [price, setPrice] = useState(goi ? String(goi.price) : '')
  const [includesTrainer, setIncludesTrainer] = useState(goi?.includesTrainer ?? false)
  const [ptValueRatio, setPtValueRatio] = useState(goi?.ptValueRatio != null ? String(goi.ptValueRatio) : '')
  const [maxFreezeDays, setMaxFreezeDays] = useState(goi ? String(goi.maxFreezeDays) : '0')
  const [isRefundable, setIsRefundable] = useState(goi?.isRefundable ?? false)
  const [description, setDescription] = useState(goi?.description ?? '')
  const [displayOrder, setDisplayOrder] = useState(goi ? String(goi.displayOrder) : '0')
  const [status, setStatus] = useState<MembershipSellStatus>(goi?.status ?? 'ACTIVE')

  const mutation = dangSua ? updateGoi : createGoi

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault()
    if (!code.trim() || !name.trim() || !price) return

    const body = {
      code: code.trim(),
      name: name.trim(),
      packageType,
      durationDays: durationDays ? Number(durationDays) : null,
      sessionCount: sessionCount ? Number(sessionCount) : null,
      price: Number(price),
      includesTrainer,
      ptValueRatio: ptValueRatio ? Number(ptValueRatio) : null,
      maxFreezeDays: Number(maxFreezeDays || 0),
      isRefundable,
      description: description.trim() || null,
      displayOrder: Number(displayOrder || 0),
    }

    if (dangSua) {
      updateGoi.mutate({ id: goi.id, ...body, status }, { onSuccess: onClose })
    } else {
      createGoi.mutate(body, { onSuccess: onClose })
    }
  }

  return (
    <Modal open={open} title={dangSua ? `Sửa gói tập — ${goi.code}` : 'Thêm gói tập mới'} onClose={onClose}>
      <form onSubmit={handleSubmit} className="space-y-4">
        {mutation.error instanceof ApiError && (
          <Alert tone="error">
            {mutation.error.fields ? Object.values(mutation.error.fields).join(', ') : mutation.error.message}
          </Alert>
        )}

        <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
          <Input label="Mã gói *" value={code} onChange={(e) => setCode(e.target.value)}
                 placeholder="FIT-01M" required />
          <Input label="Tên gói *" value={name} onChange={(e) => setName(e.target.value)}
                 placeholder="Gói Fitness 1 tháng" required />
        </div>

        <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
          <div>
            <label className="mb-1 block text-sm font-medium text-slate-700">Loại gói *</label>
            <select value={packageType} onChange={(e) => setPackageType(e.target.value as PackageType)}
                    className="w-full rounded-lg border border-slate-300 px-3 py-2 text-sm">
              <option value="TIME_BASED">Theo thời hạn (TIME_BASED)</option>
              <option value="SESSION_BASED">Theo số buổi (SESSION_BASED)</option>
              <option value="HYBRID">Kết hợp (HYBRID)</option>
              <option value="DAY_PASS">Vé lẻ (DAY_PASS)</option>
            </select>
          </div>
          <Input label="Giá (VND) *" type="number" min={0} value={price}
                 onChange={(e) => setPrice(e.target.value)} placeholder="700000" required />
        </div>

        <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
          {(packageType === 'TIME_BASED' || packageType === 'HYBRID' || packageType === 'DAY_PASS') && (
            <Input label="Thời hạn (ngày) *" type="number" min={1} value={durationDays}
                   onChange={(e) => setDurationDays(e.target.value)} required />
          )}
          {(packageType === 'SESSION_BASED' || packageType === 'HYBRID') && (
            <Input label="Số buổi *" type="number" min={1} value={sessionCount}
                   onChange={(e) => setSessionCount(e.target.value)} required />
          )}
        </div>

        {packageType === 'HYBRID' && (
          <Input label="Tỷ lệ giá trị PT (0–1) *" type="number" min={0} max={1} step={0.01}
                 value={ptValueRatio} onChange={(e) => setPtValueRatio(e.target.value)}
                 hint="Phần giá trị thuộc về PT, dùng để chia doanh thu — vd. 0.7" required />
        )}

        <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
          <Input label="Bảo lưu tối đa (ngày)" type="number" min={0} value={maxFreezeDays}
                 onChange={(e) => setMaxFreezeDays(e.target.value)} hint="0 = không áp dụng" />
          <Input label="Thứ tự hiển thị" type="number" value={displayOrder}
                 onChange={(e) => setDisplayOrder(e.target.value)} />
        </div>

        <div className="flex flex-wrap items-center gap-4">
          <label className="flex items-center gap-2 text-sm text-slate-700">
            <input type="checkbox" checked={includesTrainer}
                   onChange={(e) => setIncludesTrainer(e.target.checked)} />
            Có PT kèm theo
          </label>
          <label className="flex items-center gap-2 text-sm text-slate-700">
            <input type="checkbox" checked={isRefundable}
                   onChange={(e) => setIsRefundable(e.target.checked)} />
            Cho phép hoàn tiền
          </label>
        </div>

        {dangSua && (
          <div>
            <label className="mb-1 block text-sm font-medium text-slate-700">Trạng thái bán</label>
            <select value={status} onChange={(e) => setStatus(e.target.value as MembershipSellStatus)}
                    className="w-full rounded-lg border border-slate-300 px-3 py-2 text-sm">
              <option value="ACTIVE">Đang bán</option>
              <option value="ARCHIVED">Ngừng bán (hợp đồng cũ vẫn chạy)</option>
            </select>
          </div>
        )}

        <div>
          <label className="mb-1 block text-sm font-medium text-slate-700">Mô tả</label>
          <textarea value={description} onChange={(e) => setDescription(e.target.value)}
                    rows={2} className="w-full rounded-lg border border-slate-300 px-3 py-2 text-sm" />
        </div>

        <div className="flex justify-end gap-2 pt-2">
          <Button type="button" variant="secondary" onClick={onClose}>Hủy</Button>
          <Button type="submit" loading={mutation.isPending}>Lưu</Button>
        </div>
      </form>
    </Modal>
  )
}

function Dong({ nhan, giaTri }: { nhan: string; giaTri: string }) {
  return (
    <div className="flex justify-between gap-2">
      <dt className="text-slate-500">{nhan}</dt>
      <dd className="font-medium text-slate-700">{giaTri}</dd>
    </div>
  )
}
