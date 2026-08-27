import { useState, type FormEvent } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { useAuth } from '@/stores/auth'
import { ApiError } from '@/api/client'
import { Card, CardBody } from '@/components/ui/Card'
import { Input } from '@/components/ui/Input'
import { Button } from '@/components/ui/Button'
import { Alert } from '@/components/ui/Alert'

export function DangKyPage() {
  const register = useAuth((s) => s.register)
  const navigate = useNavigate()

  const [form, setForm] = useState({ fullName: '', phone: '', email: '', password: '' })
  const [loi, setLoi] = useState('')
  /** Lỗi theo từng ô nhập, lấy từ trường `fields` backend trả về. */
  const [loiTruong, setLoiTruong] = useState<Record<string, string>>({})
  const [dangGui, setDangGui] = useState(false)

  const doi = (ten: keyof typeof form) => (e: React.ChangeEvent<HTMLInputElement>) =>
    setForm({ ...form, [ten]: e.target.value })

  const guiForm = async (e: FormEvent) => {
    e.preventDefault()
    setLoi('')
    setLoiTruong({})
    setDangGui(true)
    try {
      await register({ ...form, email: form.email || undefined })
      navigate('/')
    } catch (err) {
      if (err instanceof ApiError) {
        setLoiTruong(err.fields ?? {})
        if (!err.fields) setLoi(err.message)
      } else {
        setLoi('Không kết nối được máy chủ')
      }
    } finally {
      setDangGui(false)
    }
  }

  return (
    <div className="mx-auto max-w-md">
      <Card>
        <CardBody className="space-y-5">
          <div>
            <h1 className="text-xl font-bold text-slate-900">Tạo tài khoản</h1>
            <p className="mt-1 text-sm text-slate-500">
              Đăng ký miễn phí. Bạn chỉ trở thành hội viên khi mua gói đầu tiên.
            </p>
          </div>

          <Alert tone="error">{loi}</Alert>

          <form onSubmit={guiForm} className="space-y-4">
            <Input label="Họ và tên" name="fullName" required
                   value={form.fullName} onChange={doi('fullName')}
                   error={loiTruong.fullName} placeholder="Nguyễn Văn An" />

            <Input label="Số điện thoại" name="phone" required
                   value={form.phone} onChange={doi('phone')}
                   error={loiTruong.phone} placeholder="0912345678"
                   hint="Số điện thoại cũng là tên đăng nhập của bạn" />

            <Input label="Email (không bắt buộc)" name="email" type="email"
                   value={form.email} onChange={doi('email')}
                   error={loiTruong.email} placeholder="an.nguyen@gmail.com"
                   hint="Dùng để lấy lại mật khẩu khi quên" />

            <Input label="Mật khẩu" name="password" type="password" required
                   autoComplete="new-password"
                   value={form.password} onChange={doi('password')}
                   error={loiTruong.password} hint="Tối thiểu 8 ký tự" />

            <Button type="submit" loading={dangGui} className="w-full">Đăng ký</Button>
          </form>

          <p className="text-center text-sm text-slate-500">
            Đã có tài khoản?{' '}
            <Link to="/dang-nhap" className="font-medium text-brand-600 hover:underline">Đăng nhập</Link>
          </p>
        </CardBody>
      </Card>
    </div>
  )
}
