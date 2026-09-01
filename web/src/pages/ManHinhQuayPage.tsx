import { useState } from 'react'
import { useDangTrongPhong, useQuetRa, useQuetVao, useXemTruocCheckIn } from '@/hooks/useCheckIn'
import { useTimKiemHoiVien } from '@/hooks/useLookup'
import { ChoThanhToan } from '@/components/ChoThanhToan'
import { Card, CardBody, CardHeader } from '@/components/ui/Card'
import { Button } from '@/components/ui/Button'
import { Badge } from '@/components/ui/Badge'
import { Alert } from '@/components/ui/Alert'
import { EmptyState } from '@/components/ui/Spinner'
import { SearchSelect } from '@/components/ui/SearchSelect'
import { ApiError } from '@/api/client'
import { gioPhut, ngayGio, tenSuCo } from '@/lib/format-cde'
import { ngay } from '@/lib/format'
import type { CheckInPreview, CheckInResult, MemberSearchResult } from '@/api/types-cde'

/**
 * Màn hình quầy lễ tân.
 *
 * Luồng: hội viên đưa mã → lễ tân quét → hệ thống trả về ảnh và tình trạng gói
 * → LỄ TÂN NHÌN, đối chiếu người thật, rồi quyết định. Máy không tự mở cửa.
 *
 * Tìm hội viên bằng TÊN hoặc SỐ ĐIỆN THOẠI — lễ tân không cần biết trước mã số.
 * Chọn xong là THẤY NGAY tình trạng (còn hạn không, nợ tiền không) TRƯỚC khi
 * bấm xác nhận — không phải bấm mù rồi mới biết kết quả.
 */
export function ManHinhQuayPage() {
  const quetVao = useQuetVao()
  const quetRa = useQuetRa()
  const { data: dangTrongPhong } = useDangTrongPhong()

  const [tuKhoa, setTuKhoa] = useState('')
  const { data: ketQuaTim, isFetching: dangTim } = useTimKiemHoiVien(tuKhoa)
  const [daChon, setDaChon] = useState<MemberSearchResult | null>(null)
  const [ketQua, setKetQua] = useState<CheckInResult | null>(null)

  const { data: xemTruoc, isFetching: dangXemTruoc } = useXemTruocCheckIn(daChon?.memberId ?? null)

  const xacNhan = (boQuaCanhBao = false) => {
    if (!daChon) return
    quetVao.mutate({ memberId: daChon.memberId, override: boQuaCanhBao }, {
      onSuccess: (kq) => { setKetQua(kq); setDaChon(null) },
    })
  }

  return (
    <div className="space-y-6">
      <h1 className="text-2xl font-bold text-slate-900">Màn hình quầy</h1>

      <div className="grid gap-6 lg:grid-cols-[1fr_1.2fr]">
        <div className="space-y-4">
          <Card>
            <CardHeader title="Tìm hội viên"
                        subtitle="Gõ tên hoặc số điện thoại — không cần nhớ mã số" />
            <CardBody className="space-y-3">
              <SearchSelect
                label="Hội viên"
                placeholder="Gõ tên hoặc số điện thoại…"
                ketQua={ketQuaTim} dangTai={dangTim}
                onTuKhoaChange={setTuKhoa}
                daChon={daChon ? { chinh: daChon.fullName, phu: daChon.memberCode } : null}
                onBoChon={() => setDaChon(null)}
                khoa={(m) => m.memberId}
                hienThi={(m) => ({ chinh: m.fullName, phu: `${m.memberCode} · ${m.phone}` })}
                onChon={(m) => { setDaChon(m); setKetQua(null) }}
              />

              {daChon && (
                <TrangThaiTruoc
                  dangTai={dangXemTruoc} xemTruoc={xemTruoc}
                  dangGui={quetVao.isPending}
                  onXacNhan={() => xacNhan(false)}
                />
              )}

              {quetVao.error instanceof ApiError && (
                <div className="mt-3"><Alert tone="error">{quetVao.error.message}</Alert></div>
              )}
            </CardBody>
          </Card>

          <ChoThanhToan />

          {ketQua && (
            <KetQuaQuet ketQua={ketQua} />
          )}
        </div>

        <Card>
          <CardHeader
            title="Đang ở trong phòng tập"
            subtitle="Tự làm mới mỗi 30 giây"
            action={<Badge tone="blue">{dangTrongPhong?.length ?? 0} người</Badge>}
          />
          <CardBody className="p-0">
            {dangTrongPhong?.length === 0 ? (
              <EmptyState title="Chưa có ai trong phòng tập" />
            ) : (
              <ul className="divide-y divide-slate-100">
                {dangTrongPhong?.map((c) => (
                  <li key={c.id} className="flex items-center justify-between gap-3 px-5 py-3">
                    <div>
                      <p className="font-medium text-slate-800">{c.memberName}</p>
                      <p className="text-xs text-slate-500">
                        <span className="font-mono">{c.memberCode}</span> · vào lúc {gioPhut(c.checkedInAt)}
                      </p>
                    </div>
                    <Button variant="secondary" loading={quetRa.isPending}
                            onClick={() => quetRa.mutate(c.memberId)}>
                      Quét ra
                    </Button>
                  </li>
                ))}
              </ul>
            )}
          </CardBody>
        </Card>
      </div>
    </div>
  )
}

/**
 * Tình trạng hội viên NGAY SAU KHI CHỌN — chưa ghi lượt check-in nào.
 *
 * Lễ tân nhìn đủ vào được không, còn mấy ngày, có nợ tiền không, TRƯỚC khi
 * quyết định bấm nút nào — không phải bấm xác nhận mù rồi mới biết kết quả.
 */
function TrangThaiTruoc({ dangTai, xemTruoc, dangGui, onXacNhan }: {
  dangTai: boolean
  xemTruoc: CheckInPreview | undefined
  dangGui: boolean
  onXacNhan: () => void
}) {
  if (dangTai || !xemTruoc) {
    return <p className="py-2 text-sm text-slate-400">Đang kiểm tra tình trạng…</p>
  }

  const choVao = xemTruoc.choPhepVao

  return (
    <div className={`space-y-3 rounded-lg border p-4
                     ${choVao ? 'border-emerald-200 bg-emerald-50/50' : 'border-red-200 bg-red-50/50'}`}>
      <div className={`rounded-lg px-3 py-2 text-center text-sm font-semibold
                       ${choVao ? 'bg-emerald-100 text-emerald-800' : 'bg-red-100 text-red-800'}`}>
        {xemTruoc.thongBao}
      </div>

      <dl className="space-y-1 text-sm">
        <Dong nhan="Họ tên" giaTri={xemTruoc.memberName} noiBat />
        <Dong nhan="Mã hội viên" giaTri={xemTruoc.memberCode} />
        {xemTruoc.registrationCode && <Dong nhan="Hợp đồng" giaTri={xemTruoc.registrationCode} />}
        {xemTruoc.endDate && (
          <Dong nhan="Hết hạn"
                giaTri={`${ngay(xemTruoc.endDate)}${xemTruoc.soNgayConLai != null
                  ? ` (còn ${xemTruoc.soNgayConLai} ngày)` : ''}`} />
        )}
      </dl>

      {choVao ? (
        <Button className="w-full" loading={dangGui} onClick={onXacNhan}>
          Xác nhận vào tập
        </Button>
      ) : (
        <Button className="w-full opacity-60 cursor-not-allowed" disabled>
          Không thể cho vào (Chưa thanh toán / Không hợp lệ)
        </Button>
      )}
    </div>
  )
}

/** Kết quả quét thật — ghi thêm thời điểm và dấu hiệu bất thường nếu có. */
function KetQuaQuet({ ketQua }: {
  ketQua: CheckInResult
}) {
  const choVao = ketQua.choPhepVao

  return (
    <Card className={choVao ? 'border-emerald-300' : 'border-red-300'}>
      <CardBody className="space-y-4">
        <div className={`rounded-lg px-4 py-3 text-center text-lg font-semibold
                         ${choVao ? 'bg-emerald-50 text-emerald-800' : 'bg-red-50 text-red-800'}`}>
          {ketQua.thongBao}
        </div>

        <div className="flex gap-4">
          {/* Ảnh hồ sơ để lễ tân đối chiếu người thật đứng trước mặt */}
          <div className="grid h-24 w-24 shrink-0 place-items-center rounded-lg bg-slate-100
                          text-xs text-slate-400">
            {ketQua.photoKey ? 'Ảnh hồ sơ' : 'Chưa có ảnh'}
          </div>

          <dl className="flex-1 space-y-1 text-sm">
            <Dong nhan="Họ tên" giaTri={ketQua.memberName} noiBat />
            <Dong nhan="Mã hội viên" giaTri={ketQua.memberCode} />
            {ketQua.registrationCode && <Dong nhan="Hợp đồng" giaTri={ketQua.registrationCode} />}
            {ketQua.endDate && (
              <Dong nhan="Hết hạn"
                    giaTri={`${ngay(ketQua.endDate)}${ketQua.soNgayConLai != null
                      ? ` (còn ${ketQua.soNgayConLai} ngày)` : ''}`} />
            )}
            <Dong nhan="Thời điểm quét" giaTri={ngayGio(ketQua.checkedInAt)} />
          </dl>
        </div>

        {ketQua.incidentType && (
          <Alert tone="error">
            <b>{tenSuCo(ketQua.incidentType)}</b>
            {ketQua.incidentNote && <div className="mt-1">{ketQua.incidentNote}</div>}
          </Alert>
        )}
      </CardBody>
    </Card>
  )
}

function Dong({ nhan, giaTri, noiBat }: { nhan: string; giaTri: string; noiBat?: boolean }) {
  return (
    <div className="flex justify-between gap-3">
      <dt className="text-slate-500">{nhan}</dt>
      <dd className={noiBat ? 'font-semibold text-slate-900' : 'font-medium text-slate-700'}>
        {giaTri}
      </dd>
    </div>
  )
}
