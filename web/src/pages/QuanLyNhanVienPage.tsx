import { useState } from 'react'
import { useEmployeeAccounts, useCreateEmployeeAccount } from '@/hooks/useAdmin'
import { Card, CardBody, CardHeader } from '@/components/ui/Card'
import { Button } from '@/components/ui/Button'
import { Input } from '@/components/ui/Input'
import { Badge } from '@/components/ui/Badge'
import { Alert } from '@/components/ui/Alert'
import { Modal } from '@/components/ui/Modal'
import { EmptyState, Spinner } from '@/components/ui/Spinner'
import { ApiError } from '@/api/client'
import { ngay } from '@/lib/format'
import type { EmployeeAccount, EmployeeRole, EmploymentType } from '@/api/types-cde'

const TEN_VAI_TRO: Record<EmployeeRole, string> = {
  TRAINER: 'Huấn luyện viên',
  SALE: 'Nhân viên kinh doanh',
  RECEPTIONIST: 'Lễ tân',
  ACCOUNTANT: 'Kế toán',
}

const TEN_PHONG_BAN: Record<string, string> = {
  TRAINING: 'Huấn luyện',
  SALES: 'Kinh doanh',
  FRONT_DESK: 'Lễ tân',
  ACCOUNTING: 'Kế toán',
}

/**
 * Admin tạo tài khoản cho nhân viên (PT/Sale/Lễ tân/Kế toán) — các vai trò này
 * không tự đăng ký được. Mật khẩu tạm chỉ hiển thị đúng 1 lần ngay sau khi tạo.
 */
export function QuanLyNhanVienPage() {
  const { data: nhanVien, isLoading } = useEmployeeAccounts()
  const [openCreate, setOpenCreate] = useState(false)
  const [taiKhoanMoiTao, setTaiKhoanMoiTao] = useState<EmployeeAccount | null>(null)

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between gap-4">
        <div>
          <h1 className="text-2xl font-bold text-slate-900">Quản lý tài khoản nhân viên</h1>
          <p className="text-sm text-slate-500">
            PT, Sale, Lễ tân, Kế toán không tự đăng ký được — chỉ Admin tạo thay
          </p>
        </div>
        <Button onClick={() => setOpenCreate(true)}>+ Tạo tài khoản nhân viên</Button>
      </div>

      <Card>
        <CardHeader title="Danh sách nhân viên đã có tài khoản" />
        <CardBody className="p-0">
          {isLoading ? (
            <Spinner />
          ) : !nhanVien || nhanVien.length === 0 ? (
            <EmptyState title="Chưa có nhân viên nào" hint="Bấm “Tạo tài khoản nhân viên” để bắt đầu" />
          ) : (
            <ul className="divide-y divide-slate-100">
              {nhanVien.map((nv) => (
                <li key={nv.id} className="flex items-center justify-between gap-4 px-5 py-3">
                  <div>
                    <p className="font-medium text-slate-900">
                      {nv.fullName} <span className="font-mono text-xs text-slate-400">{nv.employeeCode}</span>
                    </p>
                    <p className="text-sm text-slate-500">
                      {nv.phone}{nv.email ? ` · ${nv.email}` : ''}{nv.position ? ` · ${nv.position}` : ''}
                    </p>
                  </div>
                  <div className="flex items-center gap-3">
                    <span className="text-xs text-slate-400">Vào làm {ngay(nv.startDate)}</span>
                    <Badge tone={nv.status === 'ACTIVE' ? 'green' : nv.status === 'ON_LEAVE' ? 'amber' : 'gray'}>
                      {TEN_PHONG_BAN[nv.department] ?? nv.department}
                    </Badge>
                  </div>
                </li>
              ))}
            </ul>
          )}
        </CardBody>
      </Card>

      <HopThoaiTaoTaiKhoan
        open={openCreate}
        onClose={() => setOpenCreate(false)}
        onCreated={(tk) => {
          setOpenCreate(false)
          setTaiKhoanMoiTao(tk)
        }}
      />

      <HopThoaiMatKhauTam taiKhoan={taiKhoanMoiTao} onClose={() => setTaiKhoanMoiTao(null)} />
    </div>
  )
}

function HopThoaiTaoTaiKhoan({ open, onClose, onCreated }: {
  open: boolean
  onClose: () => void
  onCreated: (tk: EmployeeAccount) => void
}) {
  const createAccount = useCreateEmployeeAccount()

  const [fullName, setFullName] = useState('')
  const [phone, setPhone] = useState('')
  const [email, setEmail] = useState('')
  const [role, setRole] = useState<EmployeeRole>('TRAINER')
  const [position, setPosition] = useState('')
  const [employmentType, setEmploymentType] = useState<EmploymentType | ''>('')
  const [baseSalary, setBaseSalary] = useState('')
  const [startDate, setStartDate] = useState('')

  const resetForm = () => {
    setFullName(''); setPhone(''); setEmail(''); setRole('TRAINER')
    setPosition(''); setEmploymentType(''); setBaseSalary(''); setStartDate('')
  }

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault()
    if (!fullName || !phone) return

    createAccount.mutate(
      {
        fullName: fullName.trim(),
        phone: phone.trim(),
        email: email.trim() || undefined,
        role,
        position: position.trim() || undefined,
        employmentType: employmentType || undefined,
        baseSalary: baseSalary ? Number(baseSalary) : undefined,
        startDate: startDate || undefined,
      },
      {
        onSuccess: (tk) => {
          resetForm()
          onCreated(tk)
        },
      }
    )
  }

  return (
    <Modal open={open} title="Tạo tài khoản nhân viên" onClose={onClose}>
      <form onSubmit={handleSubmit} className="space-y-4">
        {createAccount.isError && (
          <Alert tone="error">
            {createAccount.error instanceof ApiError
              ? createAccount.error.message
              : 'Không tạo được tài khoản, vui lòng thử lại'}
          </Alert>
        )}

        <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
          <Input label="Họ và tên *" value={fullName}
                 onChange={(e) => setFullName(e.target.value)}
                 placeholder="Trần Bình" required />
          <Input label="Số điện thoại *" value={phone}
                 onChange={(e) => setPhone(e.target.value)}
                 placeholder="0912345678" required />
        </div>

        <Input label="Email (tùy chọn)" type="email" value={email}
               onChange={(e) => setEmail(e.target.value)}
               placeholder="nhanvien@example.com" />

        <div>
          <label className="mb-1 block text-sm font-medium text-slate-700">Vai trò *</label>
          <select value={role} onChange={(e) => setRole(e.target.value as EmployeeRole)}
                  className="w-full rounded-lg border border-slate-300 px-3 py-2 text-sm">
            {(Object.keys(TEN_VAI_TRO) as EmployeeRole[]).map((r) => (
              <option key={r} value={r}>{TEN_VAI_TRO[r]}</option>
            ))}
          </select>
        </div>

        <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
          <Input label="Chức danh (tùy chọn)" value={position}
                 onChange={(e) => setPosition(e.target.value)}
                 placeholder="Huấn luyện viên cá nhân" />
          <div>
            <label className="mb-1 block text-sm font-medium text-slate-700">Hình thức làm việc</label>
            <select value={employmentType}
                    onChange={(e) => setEmploymentType(e.target.value as EmploymentType | '')}
                    className="w-full rounded-lg border border-slate-300 px-3 py-2 text-sm">
              <option value="">-- Chưa rõ --</option>
              <option value="FULL_TIME">Toàn thời gian</option>
              <option value="PART_TIME">Bán thời gian</option>
              <option value="FREELANCE">Cộng tác viên</option>
            </select>
          </div>
        </div>

        <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
          <Input label="Lương cứng (VND, tùy chọn)" type="number" min={0} value={baseSalary}
                 onChange={(e) => setBaseSalary(e.target.value)}
                 placeholder="8000000" />
          <Input label="Ngày vào làm (bỏ trống = hôm nay)" type="date" value={startDate}
                 onChange={(e) => setStartDate(e.target.value)} />
        </div>

        <div className="flex justify-end gap-2 pt-2">
          <Button type="button" variant="secondary" onClick={onClose}>Hủy</Button>
          <Button type="submit" loading={createAccount.isPending}>Tạo tài khoản</Button>
        </div>
      </form>
    </Modal>
  )
}

/** Hiện mật khẩu tạm ĐÚNG MỘT LẦN — đóng lại là mất, không xem lại được. */
function HopThoaiMatKhauTam({ taiKhoan, onClose }: {
  taiKhoan: EmployeeAccount | null
  onClose: () => void
}) {
  const [daSaoChep, setDaSaoChep] = useState(false)

  if (!taiKhoan) return null

  const saoChep = async () => {
    try {
      await navigator.clipboard.writeText(taiKhoan.tempPassword)
      setDaSaoChep(true)
    } catch {
      /* trình duyệt chặn clipboard — người dùng tự đọc và gõ lại */
    }
  }

  return (
    <Modal open={!!taiKhoan} title="Đã tạo tài khoản — mật khẩu tạm" onClose={onClose}>
      <div className="space-y-4">
        <Alert tone="success">
          Tài khoản <strong>{taiKhoan.fullName}</strong> ({TEN_VAI_TRO[taiKhoan.role]}) đã được tạo,
          mã nhân viên <strong>{taiKhoan.employeeCode}</strong>.
        </Alert>

        <Alert tone="error">
          Mật khẩu bên dưới chỉ hiển thị <strong>đúng một lần</strong>. Đóng cửa sổ này là mất, không xem
          lại được — hãy báo ngay cho nhân viên (nhắn tin/gặp trực tiếp), không gửi qua kênh nào khác.
        </Alert>

        <div className="rounded-lg border border-slate-200 bg-slate-50 p-4">
          <p className="text-xs text-slate-500">Tên đăng nhập</p>
          <p className="font-mono text-lg font-semibold text-slate-900">{taiKhoan.username}</p>
          <p className="mt-3 text-xs text-slate-500">Mật khẩu tạm</p>
          <p className="font-mono text-lg font-semibold text-slate-900">{taiKhoan.tempPassword}</p>
        </div>

        <div className="flex justify-end gap-2">
          <Button variant="secondary" onClick={saoChep}>
            {daSaoChep ? 'Đã sao chép ✓' : 'Sao chép mật khẩu'}
          </Button>
          <Button onClick={onClose}>Đã báo cho nhân viên, đóng lại</Button>
        </div>
      </div>
    </Modal>
  )
}
