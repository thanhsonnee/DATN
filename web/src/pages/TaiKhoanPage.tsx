import { useState, type FormEvent } from 'react'
import { api, ApiError } from '@/api/client'
import { useAuth } from '@/stores/auth'
import { Card, CardBody, CardHeader } from '@/components/ui/Card'
import { Input } from '@/components/ui/Input'
import { Button } from '@/components/ui/Button'
import { Alert } from '@/components/ui/Alert'
import { Badge } from '@/components/ui/Badge'
import { tenVaiTro } from '@/lib/format'

export function TaiKhoanPage() {
  const user = useAuth((s) => s.user)!

  return (
    <div className="mx-auto max-w-2xl space-y-6">
      <h1 className="text-2xl font-bold text-slate-900">Tài khoản</h1>

      <Card>
        <CardHeader title="Thông tin cá nhân" />
        <CardBody>
          <dl className="grid grid-cols-2 gap-4 text-sm">
            <Dong nhan="Họ và tên" giaTri={user.fullName} />
            <Dong nhan="Số điện thoại" giaTri={user.phone} />
            <Dong nhan="Vai trò" giaTri={tenVaiTro(user.role)} />
            <div>
              <dt className="text-xs text-slate-500">Tình trạng hội viên</dt>
              <dd className="mt-0.5">
                {user.isMember ? (
                  <Badge tone="green">Hội viên · {user.memberCode}</Badge>
                ) : (
                  <Badge tone="gray">Chưa mua gói</Badge>
                )}
              </dd>
            </div>
          </dl>
        </CardBody>
      </Card>

      <DoiMatKhau />
    </div>
  )
}

function Dong({ nhan, giaTri }: { nhan: string; giaTri: string }) {
  return (
    <div>
      <dt className="text-xs text-slate-500">{nhan}</dt>
      <dd className="mt-0.5 font-medium text-slate-800">{giaTri}</dd>
    </div>
  )
}

function DoiMatKhau() {
  const logout = useAuth((s) => s.logout)
  const [form, setForm] = useState({ currentPassword: '', newPassword: '' })
  const [loi, setLoi] = useState('')
  const [xong, setXong] = useState(false)
  const [dangGui, setDangGui] = useState(false)

  const gui = async (e: FormEvent) => {
    e.preventDefault()
    setLoi('')
    setDangGui(true)
    try {
      await api.post('/auth/change-password', form)
      setXong(true)
      setForm({ currentPassword: '', newPassword: '' })
      // Đổi mật khẩu xong thì đăng nhập lại cho chắc chắn
      setTimeout(logout, 2000)
    } catch (err) {
      setLoi(err instanceof ApiError ? err.message : 'Có lỗi xảy ra')
    } finally {
      setDangGui(false)
    }
  }

  return (
    <Card>
      <CardHeader title="Đổi mật khẩu" />
      <CardBody>
        {xong ? (
          <Alert tone="success">Đổi mật khẩu thành công. Đang đưa bạn về trang đăng nhập…</Alert>
        ) : (
          <form onSubmit={gui} className="space-y-4">
            <Alert tone="error">{loi}</Alert>

            <Input label="Mật khẩu hiện tại" type="password" required
                   autoComplete="current-password"
                   value={form.currentPassword}
                   onChange={(e) => setForm({ ...form, currentPassword: e.target.value })} />

            <Input label="Mật khẩu mới" type="password" required
                   autoComplete="new-password" hint="Tối thiểu 8 ký tự"
                   value={form.newPassword}
                   onChange={(e) => setForm({ ...form, newPassword: e.target.value })} />

            <Button type="submit" loading={dangGui}>Đổi mật khẩu</Button>
          </form>
        )}
      </CardBody>
    </Card>
  )
}
