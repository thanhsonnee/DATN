import { useState } from 'react'
import {
  useBoQuaYeuCau, useDangTrongPhong, useHangDoiChoXacNhan, useQuetRa, useQuetVao, useXemTruocCheckIn,
} from '@/hooks/useCheckIn'
import { useTimKiemHoiVien, useUploadMemberPhoto, useDeleteMemberPhoto } from '@/hooks/useLookup'
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
          <HangDoiTuCheckIn />

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
 * Hàng đợi hội viên đã tự bấm "Check-in" trên app, đang chờ lễ tân nhìn ảnh và
 * xác nhận. Song song với ô tìm kiếm bên dưới — hội viên không có app hoặc quên
 * bấm thì lễ tân vẫn tìm tay được như cũ.
 */
function HangDoiTuCheckIn() {
  const { data: hangDoi } = useHangDoiChoXacNhan()
  const quetVao = useQuetVao()
  const boQua = useBoQuaYeuCau()

  if (!hangDoi || hangDoi.length === 0) return null

  return (
    <Card className="border-brand-300">
      <CardHeader title="Yêu cầu check-in từ app"
                  subtitle="Hội viên tự bấm, đang chờ đối chiếu ảnh"
                  action={<Badge tone="blue">{hangDoi.length} người</Badge>} />
      <CardBody className="space-y-4">
        {hangDoi.map((yc) => (
          <div key={yc.memberId}
               className={`space-y-3 rounded-lg border p-4
                          ${yc.choPhepVao ? 'border-emerald-200 bg-emerald-50/50' : 'border-red-200 bg-red-50/50'}`}>
            <div className="flex flex-col sm:flex-row items-start gap-4">
              {/* Ảnh cỡ lớn giống hệt "Tìm hội viên" để lễ tân đối chiếu người thật */}
              {yc.photoKey ? (
                <img src={`/api/v1/files/photos/${yc.photoKey}`} alt={yc.memberName}
                     className="h-56 w-56 shrink-0 mx-auto sm:mx-0 rounded-xl object-cover border-2 border-slate-200 shadow-sm" />
              ) : (
                <div className="grid h-56 w-56 shrink-0 mx-auto sm:mx-0 place-items-center rounded-xl bg-slate-100 border-2 border-dashed border-slate-300 text-center p-2 text-sm text-slate-400">
                  Chưa có ảnh khuôn mặt
                </div>
              )}

              <div className="flex-1 w-full space-y-2">
                <p className="text-lg font-semibold text-slate-900">{yc.memberName}</p>
                <p className="text-sm text-slate-600">{yc.thongBao}</p>
              </div>
            </div>

            <div className="flex gap-2">
              <Button className="flex-1" disabled={!yc.choPhepVao}
                      loading={quetVao.isPending}
                      onClick={() => quetVao.mutate({ memberId: yc.memberId })}>
                Xác nhận vào tập
              </Button>
              <Button variant="secondary" onClick={() => boQua.mutate(yc.memberId)}>
                Bỏ qua
              </Button>
            </div>
          </div>
        ))}
      </CardBody>
    </Card>
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
  const uploadPhoto = useUploadMemberPhoto()
  const deletePhoto = useDeleteMemberPhoto()

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

      <div className="flex flex-col sm:flex-row items-start gap-4">
        {/* Ảnh chân dung hội viên để lễ tân đối chiếu — cỡ lớn để nhìn rõ khi đối chiếu người thật */}
        <div className="flex flex-col items-center shrink-0 mx-auto sm:mx-0">
          {xemTruoc.photoKey ? (
            <img
              src={`/api/v1/files/photos/${xemTruoc.photoKey}`}
              alt={xemTruoc.memberName}
              className="h-56 w-56 rounded-xl object-cover border-2 border-slate-200 shadow-sm"
            />
          ) : (
            <div className="grid h-56 w-56 place-items-center rounded-xl bg-slate-100 border-2 border-dashed border-slate-300 text-center p-2 text-sm text-slate-400">
              Chưa có ảnh khuôn mặt
            </div>
          )}

          <div className="mt-2 flex items-center gap-2">
            <label className="flex items-center justify-center gap-1 rounded-md bg-white px-2.5 py-1 text-xs font-semibold text-slate-700 shadow-sm border border-slate-300 hover:bg-slate-50 cursor-pointer transition">
              <span>{uploadPhoto.isPending ? 'Đang tải…' : '📷 Tải/Đổi ảnh'}</span>
              <input
                type="file"
                accept="image/*"
                className="hidden"
                disabled={uploadPhoto.isPending}
                onChange={(e) => {
                  const f = e.target.files?.[0]
                  if (f) uploadPhoto.mutate({ memberId: xemTruoc.memberId, file: f })
                }}
              />
            </label>

            {xemTruoc.photoKey && (
              <button
                type="button"
                disabled={deletePhoto.isPending}
                onClick={() => {
                  if (window.confirm(`Xóa ảnh chân dung của ${xemTruoc.memberName}?`)) {
                    deletePhoto.mutate(xemTruoc.memberId)
                  }
                }}
                className="rounded-md bg-white px-2.5 py-1 text-xs font-semibold text-red-600 shadow-sm border border-red-200 hover:bg-red-50 transition disabled:opacity-60"
              >
                {deletePhoto.isPending ? 'Đang xóa…' : '🗑 Xóa ảnh'}
              </button>
            )}
          </div>
        </div>

        <dl className="flex-1 space-y-2 text-base w-full">
          <Dong nhan="Họ tên" giaTri={xemTruoc.memberName} noiBat />
          <Dong nhan="Mã hội viên" giaTri={xemTruoc.memberCode} />
          {xemTruoc.registrationCode && <Dong nhan="Hợp đồng" giaTri={xemTruoc.registrationCode} />}
          {xemTruoc.endDate && (
            <Dong nhan="Hết hạn"
                  giaTri={`${ngay(xemTruoc.endDate)}${xemTruoc.soNgayConLai != null
                    ? ` (còn ${xemTruoc.soNgayConLai} ngày)` : ''}`} />
          )}
        </dl>
      </div>

      {uploadPhoto.error instanceof Error && (
        <Alert tone="error">{uploadPhoto.error.message}</Alert>
      )}
      {deletePhoto.error instanceof Error && (
        <Alert tone="error">{deletePhoto.error.message}</Alert>
      )}

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

        <div className="flex flex-col sm:flex-row gap-4">
          {/* Ảnh hồ sơ để lễ tân đối chiếu người thật đứng trước mặt */}
          {ketQua.photoKey ? (
            <img
              src={`/api/v1/files/photos/${ketQua.photoKey}`}
              alt={ketQua.memberName}
              className="h-40 w-40 shrink-0 mx-auto sm:mx-0 rounded-xl object-cover border-2 border-emerald-300 shadow-sm"
            />
          ) : (
            <div className="grid h-40 w-40 shrink-0 mx-auto sm:mx-0 place-items-center rounded-lg bg-slate-100 text-xs text-slate-400 border border-slate-200">
              Chưa có ảnh
            </div>
          )}

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
