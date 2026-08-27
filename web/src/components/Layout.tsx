import { Link, NavLink, Outlet, useNavigate } from 'react-router-dom'
import { useAuth } from '@/stores/auth'
import { tenVaiTro } from '@/lib/format'
import type { UserRole } from '@/api/types'

interface MucMenu {
  to: string
  nhan: string
  /** Bỏ trống nghĩa là ai đăng nhập cũng thấy. */
  vaiTro?: UserRole[]
}

const MENU: MucMenu[] = [
  { to: '/goi-cua-toi', nhan: 'Gói của tôi' },
  { to: '/buoi-tap', nhan: 'Buổi tập' },
  { to: '/lich-day', nhan: 'Lịch dạy', vaiTro: ['TRAINER', 'ADMIN'] },
  { to: '/quay', nhan: 'Màn hình quầy', vaiTro: ['RECEPTIONIST', 'ADMIN'] },
  { to: '/thu-ngan', nhan: 'Thu ngân', vaiTro: ['RECEPTIONIST', 'ACCOUNTANT', 'ADMIN'] },
  { to: '/quan-ly', nhan: 'Hợp đồng', vaiTro: ['SALE', 'RECEPTIONIST', 'ACCOUNTANT', 'ADMIN'] },
  { to: '/thong-ke', nhan: 'Thống kê', vaiTro: ['ADMIN', 'ACCOUNTANT'] },
]

export function Layout() {
  const { user, logout } = useAuth()
  const navigate = useNavigate()

  const linkClass = ({ isActive }: { isActive: boolean }) =>
    `whitespace-nowrap rounded-lg px-3 py-2 text-sm font-medium transition ${
      isActive ? 'bg-brand-50 text-brand-700' : 'text-slate-600 hover:bg-slate-100'
    }`

  const menuHienThi = user
    ? MENU.filter((m) => !m.vaiTro || m.vaiTro.includes(user.role))
    : []

  return (
    <div className="min-h-screen">
      <header className="sticky top-0 z-40 border-b border-slate-200 bg-white/90 backdrop-blur">
        <div className="mx-auto flex max-w-7xl items-center gap-2 px-4 py-3">
          <Link to="/" className="mr-2 flex shrink-0 items-center gap-2 font-semibold text-slate-900">
            <span className="grid h-8 w-8 place-items-center rounded-lg bg-brand-600 text-white">G</span>
            <span className="hidden lg:inline">Fitness Center</span>
          </Link>

          <nav className="flex flex-1 items-center gap-1 overflow-x-auto">
            <NavLink to="/" end className={linkClass}>Bảng giá</NavLink>
            {menuHienThi.map((m) => (
              <NavLink key={m.to} to={m.to} className={linkClass}>{m.nhan}</NavLink>
            ))}
          </nav>

          <div className="flex shrink-0 items-center gap-2">
            {user ? (
              <>
                <Link to="/tai-khoan" className="hidden text-right sm:block">
                  <span className="block text-sm font-medium text-slate-800">{user.fullName}</span>
                  <span className="block text-xs text-slate-500">{tenVaiTro(user.role)}</span>
                </Link>
                <button onClick={() => { logout(); navigate('/') }}
                        className="rounded-lg px-3 py-2 text-sm text-slate-600 hover:bg-slate-100">
                  Đăng xuất
                </button>
              </>
            ) : (
              <>
                <Link to="/dang-nhap"
                      className="rounded-lg px-3 py-2 text-sm font-medium text-slate-600 hover:bg-slate-100">
                  Đăng nhập
                </Link>
                <Link to="/dang-ky"
                      className="rounded-lg bg-brand-600 px-4 py-2 text-sm font-medium text-white hover:bg-brand-700">
                  Đăng ký
                </Link>
              </>
            )}
          </div>
        </div>
      </header>

      <main className="mx-auto max-w-7xl px-4 py-8">
        <Outlet />
      </main>
    </div>
  )
}
