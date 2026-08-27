import { useState, type FormEvent } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { useAuth } from '@/stores/auth'
import { ApiError } from '@/api/client'
import { Card, CardBody } from '@/components/ui/Card'
import { Input } from '@/components/ui/Input'
import { Button } from '@/components/ui/Button'
import { Alert } from '@/components/ui/Alert'

export function DangNhapPage() {
  const login = useAuth((s) => s.login)
  const navigate = useNavigate()

  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [loi, setLoi] = useState('')
  const [dangGui, setDangGui] = useState(false)

  const guiForm = async (e: FormEvent) => {
    e.preventDefault()
    setLoi('')
    setDangGui(true)
    try {
      await login(username, password)
      navigate('/goi-cua-toi')
    } catch (err) {
      setLoi(err instanceof ApiError ? err.message : 'Không kết nối được máy chủ')
    } finally {
      setDangGui(false)
    }
  }

  return (
    <div className="mx-auto max-w-md">
      <Card>
        <CardBody className="space-y-5">
          <div>
            <h1 className="text-xl font-bold text-slate-900">Đăng nhập</h1>
            <p className="mt-1 text-sm text-slate-500">Dùng số điện thoại đã đăng ký</p>
          </div>

          <Alert tone="error">{loi}</Alert>

          <form onSubmit={guiForm} className="space-y-4">
            <Input
              label="Số điện thoại" name="username" autoComplete="username" required
              value={username} onChange={(e) => setUsername(e.target.value)}
              placeholder="0912345678"
            />
            <Input
              label="Mật khẩu" name="password" type="password" autoComplete="current-password" required
              value={password} onChange={(e) => setPassword(e.target.value)}
            />
            <Button type="submit" loading={dangGui} className="w-full">Đăng nhập</Button>
          </form>

          <p className="text-center text-sm text-slate-500">
            Chưa có tài khoản?{' '}
            <Link to="/dang-ky" className="font-medium text-brand-600 hover:underline">Đăng ký ngay</Link>
          </p>
        </CardBody>
      </Card>
    </div>
  )
}
