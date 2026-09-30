import { useState } from 'react'
import {
  useTuChoiYeuCau, useDangTrongPhong, useHangDoiChoXacNhan, useQuetRa, useQuetVao, useXemTruocCheckIn,
} from '@/hooks/useCheckIn'
import {
  useTimKiemHoiVien, useUploadMemberPhoto, useDeleteMemberPhoto, useMemberProfile, useUpdateMemberProfile,
} from '@/hooks/useLookup'
import { ChoThanhToan } from '@/components/ChoThanhToan'
import { HopThoaiTaoLead } from '@/components/HopThoaiTaoLead'
import { Card, CardBody, CardHeader } from '@/components/ui/Card'
import { Button } from '@/components/ui/Button'
import { Badge } from '@/components/ui/Badge'
import { Alert } from '@/components/ui/Alert'
import { Input } from '@/components/ui/Input'
import { Modal } from '@/components/ui/Modal'
import { EmptyState } from '@/components/ui/Spinner'
import { SearchSelect } from '@/components/ui/SearchSelect'
import { ApiError } from '@/api/client'
import { gioPhut, ngayGio, tenSuCo } from '@/lib/format-cde'
import { ngay } from '@/lib/format'
import type { CheckInPreview, CheckInResult, MemberSearchResult, Gender, MemberGoal, MemberSource } from '@/api/types-cde'

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
  const [dangSuaHoSo, setDangSuaHoSo] = useState(false)
  const [openTaoLead, setOpenTaoLead] = useState(false)

  const { data: xemTruoc, isFetching: dangXemTruoc } = useXemTruocCheckIn(daChon?.memberId ?? null)

  const xacNhan = (boQuaCanhBao = false) => {
    if (!daChon) return
    quetVao.mutate({ memberId: daChon.memberId, override: boQuaCanhBao }, {
      onSuccess: (kq) => { setKetQua(kq); setDaChon(null) },
    })
  }

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between gap-4">
        <h1 className="text-2xl font-bold text-slate-900">Màn hình quầy</h1>
        <Button variant="secondary" onClick={() => setOpenTaoLead(true)}>
          + Khách cần tư vấn thêm
        </Button>
      </div>

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
                <>
                  <div className="flex justify-end">
                    <Button variant="secondary" className="!py-1 !px-3 !text-xs"
                            onClick={() => setDangSuaHoSo(true)}>
                      Sửa hồ sơ
                    </Button>
                  </div>
                  <TrangThaiTruoc
                    dangTai={dangXemTruoc} xemTruoc={xemTruoc}
                    dangGui={quetVao.isPending}
                    onXacNhan={() => xacNhan(false)}
                  />
                </>
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

          {daChon && dangSuaHoSo && (
            <HopThoaiSuaHoSo memberId={daChon.memberId} onClose={() => setDangSuaHoSo(false)} />
          )}

          <HopThoaiTaoLead open={openTaoLead} onClose={() => setOpenTaoLead(false)} />
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
  const tuChoi = useTuChoiYeuCau()

  const [dangTuChoi, setDangTuChoi] = useState<{ memberId: number; memberName: string } | null>(null)
  const [lyDo, setLyDo] = useState('')

  const moModalTuChoi = (memberId: number, memberName: string) => {
    tuChoi.reset()
    setLyDo('')
    setDangTuChoi({ memberId, memberName })
  }

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
              <Button variant="secondary" onClick={() => moModalTuChoi(yc.memberId, yc.memberName)}>
                Từ chối
              </Button>
            </div>
          </div>
        ))}
      </CardBody>

      <Modal open={dangTuChoi != null} title="Từ chối yêu cầu check-in"
             onClose={() => setDangTuChoi(null)}>
        <div className="space-y-4">
          <p className="text-sm text-slate-600">
            Từ chối yêu cầu của <b>{dangTuChoi?.memberName}</b> — hội viên sẽ thấy ngay là
            không được vào tập, kèm lý do bên dưới.
          </p>

          <Input
            label="Lý do từ chối"
            value={lyDo}
            onChange={(e) => setLyDo(e.target.value)}
            placeholder="Không đối chiếu được khuôn mặt, cần xuất trình CCCD"
            autoFocus
          />

          {tuChoi.error instanceof ApiError && <Alert tone="error">{tuChoi.error.message}</Alert>}

          <div className="flex justify-end gap-2">
            <Button variant="secondary" onClick={() => setDangTuChoi(null)}>Đóng</Button>
            <Button
              loading={tuChoi.isPending}
              disabled={!lyDo.trim()}
              onClick={() => {
                if (!dangTuChoi) return
                tuChoi.mutate({ memberId: dangTuChoi.memberId, lyDo: lyDo.trim() }, {
                  onSuccess: () => setDangTuChoi(null),
                })
              }}
            >
              Xác nhận từ chối
            </Button>
          </div>
        </div>
      </Modal>
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

const TEN_GIOI_TINH: Record<Gender, string> = { MALE: 'Nam', FEMALE: 'Nữ', OTHER: 'Khác' }

const TEN_MUC_TIEU: Record<MemberGoal, string> = {
  LOSE_FAT: 'Giảm mỡ', GAIN_MUSCLE: 'Tăng cơ', ENDURANCE: 'Sức bền', HEALTH: 'Sức khỏe chung',
}

const TEN_NGUON: Record<MemberSource, string> = {
  WALK_IN: 'Khách vãng lai', HOTLINE: 'Hotline', WEB_FORM: 'Form web', REFERRAL: 'Giới thiệu', APP_SELF: 'Tự đăng ký app',
}

/**
 * Sửa hồ sơ hội viên tại quầy — KHÔNG sửa được số điện thoại (là tên đăng nhập),
 * đổi cần đồng bộ riêng, chưa triển khai.
 */
function HopThoaiSuaHoSo({ memberId, onClose }: { memberId: number; onClose: () => void }) {
  const { data: hoSo, isLoading } = useMemberProfile(memberId)
  const capNhat = useUpdateMemberProfile()

  const [fullName, setFullName] = useState('')
  const [gender, setGender] = useState<Gender | ''>('')
  const [birthday, setBirthday] = useState('')
  const [nationalId, setNationalId] = useState('')
  const [email, setEmail] = useState('')
  const [address, setAddress] = useState('')
  const [emergencyContactName, setEmergencyContactName] = useState('')
  const [emergencyContactPhone, setEmergencyContactPhone] = useState('')
  const [healthNote, setHealthNote] = useState('')
  const [goal, setGoal] = useState<MemberGoal | ''>('')
  const [source, setSource] = useState<MemberSource | ''>('')
  const [daNap, setDaNap] = useState(false)

  // Nạp dữ liệu vào form đúng 1 lần khi hồ sơ tải xong — tránh ghi đè lại field
  // người dùng đang gõ dở mỗi khi query refetch.
  if (hoSo && !daNap) {
    setFullName(hoSo.fullName)
    setGender(hoSo.gender ?? '')
    setBirthday(hoSo.birthday ?? '')
    setNationalId(hoSo.nationalId ?? '')
    setEmail(hoSo.email ?? '')
    setAddress(hoSo.address ?? '')
    setEmergencyContactName(hoSo.emergencyContactName ?? '')
    setEmergencyContactPhone(hoSo.emergencyContactPhone ?? '')
    setHealthNote(hoSo.healthNote ?? '')
    setGoal(hoSo.goal ?? '')
    setSource(hoSo.source ?? '')
    setDaNap(true)
  }

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault()
    if (!fullName.trim()) return

    capNhat.mutate(
      {
        memberId,
        fullName: fullName.trim(),
        gender: gender || undefined,
        birthday: birthday || undefined,
        nationalId: nationalId.trim() || undefined,
        email: email.trim() || undefined,
        address: address.trim() || undefined,
        emergencyContactName: emergencyContactName.trim() || undefined,
        emergencyContactPhone: emergencyContactPhone.trim() || undefined,
        healthNote: healthNote.trim() || undefined,
        goal: goal || undefined,
        source: source || undefined,
      },
      { onSuccess: onClose }
    )
  }

  return (
    <Modal open title={`Sửa hồ sơ — ${hoSo?.memberCode ?? ''}`} onClose={onClose}>
      {isLoading || !hoSo ? (
        <p className="py-4 text-center text-sm text-slate-400">Đang tải hồ sơ…</p>
      ) : (
        <form onSubmit={handleSubmit} className="space-y-4">
          {capNhat.isError && (
            <Alert tone="error">
              {capNhat.error instanceof ApiError ? capNhat.error.message : 'Không sửa được hồ sơ, vui lòng thử lại'}
            </Alert>
          )}

          <Input label="Số điện thoại (không sửa được)" value={hoSo.phone} disabled />

          <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
            <Input label="Họ và tên *" value={fullName}
                   onChange={(e) => setFullName(e.target.value)} required />
            <div>
              <label className="mb-1 block text-sm font-medium text-slate-700">Giới tính</label>
              <select value={gender} onChange={(e) => setGender(e.target.value as Gender | '')}
                      className="w-full rounded-lg border border-slate-300 px-3 py-2 text-sm">
                <option value="">-- Chưa rõ --</option>
                {(Object.keys(TEN_GIOI_TINH) as Gender[]).map((g) => (
                  <option key={g} value={g}>{TEN_GIOI_TINH[g]}</option>
                ))}
              </select>
            </div>
          </div>

          <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
            <Input label="Ngày sinh" type="date" value={birthday}
                   onChange={(e) => setBirthday(e.target.value)} />
            <Input label="CCCD/CMND" value={nationalId}
                   onChange={(e) => setNationalId(e.target.value)} />
          </div>

          <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
            <Input label="Email" type="email" value={email}
                   onChange={(e) => setEmail(e.target.value)} />
            <Input label="Địa chỉ" value={address}
                   onChange={(e) => setAddress(e.target.value)} />
          </div>

          <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
            <Input label="Người liên hệ khẩn cấp" value={emergencyContactName}
                   onChange={(e) => setEmergencyContactName(e.target.value)} />
            <Input label="SĐT liên hệ khẩn cấp" value={emergencyContactPhone}
                   onChange={(e) => setEmergencyContactPhone(e.target.value)} />
          </div>

          <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
            <div>
              <label className="mb-1 block text-sm font-medium text-slate-700">Mục tiêu tập luyện</label>
              <select value={goal} onChange={(e) => setGoal(e.target.value as MemberGoal | '')}
                      className="w-full rounded-lg border border-slate-300 px-3 py-2 text-sm">
                <option value="">-- Chưa rõ --</option>
                {(Object.keys(TEN_MUC_TIEU) as MemberGoal[]).map((g) => (
                  <option key={g} value={g}>{TEN_MUC_TIEU[g]}</option>
                ))}
              </select>
            </div>
            <div>
              <label className="mb-1 block text-sm font-medium text-slate-700">Nguồn hội viên</label>
              <select value={source} onChange={(e) => setSource(e.target.value as MemberSource | '')}
                      className="w-full rounded-lg border border-slate-300 px-3 py-2 text-sm">
                <option value="">-- Chưa rõ --</option>
                {(Object.keys(TEN_NGUON) as MemberSource[]).map((s) => (
                  <option key={s} value={s}>{TEN_NGUON[s]}</option>
                ))}
              </select>
            </div>
          </div>

          <div>
            <label className="mb-1 block text-sm font-medium text-slate-700">Bệnh nền / chấn thương</label>
            <textarea value={healthNote} onChange={(e) => setHealthNote(e.target.value)}
                      rows={2} placeholder="PT dùng để loại bài tập chống chỉ định"
                      className="w-full rounded-lg border border-slate-300 px-3 py-2 text-sm" />
          </div>

          <div className="flex justify-end gap-2 pt-2">
            <Button type="button" variant="secondary" onClick={onClose}>Hủy</Button>
            <Button type="submit" loading={capNhat.isPending}>Lưu</Button>
          </div>
        </form>
      )}
    </Modal>
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
