import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useMemberships } from '@/hooks/useMemberships'
import { useMuaGoi } from '@/hooks/useRegistrations'
import { useAuth } from '@/stores/auth'
import { Card, CardBody } from '@/components/ui/Card'
import { Button } from '@/components/ui/Button'
import { Badge } from '@/components/ui/Badge'
import { Alert } from '@/components/ui/Alert'
import { Spinner } from '@/components/ui/Spinner'
import { Modal } from '@/components/ui/Modal'
import { tien, tenLoaiGoi } from '@/lib/format'
import { ApiError } from '@/api/client'
import type { Membership } from '@/api/types'

export function BangGiaPage() {
  const { data: goiTap, isLoading, error } = useMemberships()
  const user = useAuth((s) => s.user)
  const navigate = useNavigate()
  const muaGoi = useMuaGoi()

  const [dangChon, setDangChon] = useState<Membership | null>(null)
  const [ketQua, setKetQua] = useState<string | null>(null)

  const batDauMua = (goi: Membership) => {
    if (!user) return navigate('/dang-nhap')
    setDangChon(goi)
  }

  const xacNhanMua = () => {
    if (!dangChon) return
    muaGoi.mutate(
      { membershipId: dangChon.id },
      {
        onSuccess: (hopDong) => {
          setDangChon(null)
          setKetQua(`Đã tạo hợp đồng ${hopDong.registrationCode}. Vui lòng thanh toán tại quầy để kích hoạt.`)
        },
      },
    )
  }

  if (isLoading) return <Spinner label="Đang tải bảng giá…" />
  if (error) return <Alert tone="error">Không tải được bảng giá. Kiểm tra backend đã chạy chưa.</Alert>

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-bold text-slate-900">Bảng giá gói tập</h1>
        <p className="mt-1 text-slate-600">
          Giá và chương trình khuyến mãi đều công khai — không cần liên hệ để hỏi giá.
        </p>
      </div>

      {ketQua && <Alert tone="success">{ketQua}</Alert>}

      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
        {goiTap?.map((goi) => (
          <Card key={goi.id} className="flex flex-col">
            <CardBody className="flex flex-1 flex-col">
              <div className="mb-3 flex items-start justify-between gap-2">
                <div>
                  <h3 className="font-semibold text-slate-900">{goi.name}</h3>
                  <p className="mt-0.5 font-mono text-xs text-slate-400">{goi.code}</p>
                </div>
                {goi.includesTrainer && <Badge tone="blue">Có PT</Badge>}
              </div>

              <p className="mb-4 text-2xl font-bold text-brand-700">{tien(goi.price)}</p>

              <dl className="mb-4 flex-1 space-y-1.5 text-sm text-slate-600">
                <Dong nhan="Loại gói" giaTri={tenLoaiGoi(goi.packageType)} />
                {goi.durationDays && <Dong nhan="Thời hạn" giaTri={`${goi.durationDays} ngày`} />}
                {goi.sessionCount && <Dong nhan="Số buổi PT" giaTri={`${goi.sessionCount} buổi`} />}
                <Dong
                  nhan="Bảo lưu"
                  giaTri={goi.maxFreezeDays > 0 ? `Tối đa ${goi.maxFreezeDays} ngày` : 'Không áp dụng'}
                />
              </dl>

              <Button onClick={() => batDauMua(goi)} className="w-full">
                {user ? 'Mua gói này' : 'Đăng nhập để mua'}
              </Button>
            </CardBody>
          </Card>
        ))}
      </div>

      <Modal open={!!dangChon} title="Xác nhận mua gói" onClose={() => setDangChon(null)}>
        {dangChon && (
          <div className="space-y-4">
            <div className="rounded-lg bg-slate-50 p-4">
              <p className="font-medium text-slate-900">{dangChon.name}</p>
              <p className="mt-1 text-xl font-bold text-brand-700">{tien(dangChon.price)}</p>
            </div>

            <p className="text-sm text-slate-600">
              Hợp đồng sẽ được tạo ở trạng thái <b>chờ thanh toán</b>. Sau khi bạn thanh toán,
              lễ tân kích hoạt thì gói mới bắt đầu có hiệu lực.
            </p>

            {muaGoi.error instanceof ApiError && (
              <Alert tone="error">{muaGoi.error.message}</Alert>
            )}

            <div className="flex justify-end gap-2">
              <Button variant="secondary" onClick={() => setDangChon(null)}>Hủy</Button>
              <Button onClick={xacNhanMua} loading={muaGoi.isPending}>Xác nhận mua</Button>
            </div>
          </div>
        )}
      </Modal>
    </div>
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
