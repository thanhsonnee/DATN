import { useEffect } from 'react'
import { Navigate, Route, Routes } from 'react-router-dom'
import { useAuth } from '@/stores/auth'
import type { UserRole } from '@/api/types'
import { Layout } from '@/components/Layout'
import { Spinner } from '@/components/ui/Spinner'
import { BangGiaPage } from '@/pages/BangGiaPage'
import { DangNhapPage } from '@/pages/DangNhapPage'
import { DangKyPage } from '@/pages/DangKyPage'
import { GoiCuaToiPage } from '@/pages/GoiCuaToiPage'
import { QuanLyHopDongPage } from '@/pages/QuanLyHopDongPage'
import { TaiKhoanPage } from '@/pages/TaiKhoanPage'
import { BuoiTapPage } from '@/pages/BuoiTapPage'
import { LichDayPage } from '@/pages/LichDayPage'
import { ManHinhQuayPage } from '@/pages/ManHinhQuayPage'
import { ThuNganPage } from '@/pages/ThuNganPage'
import { ThongKePage } from '@/pages/ThongKePage'
import { BanHangPage } from '@/pages/BanHangPage'
import { PhanHoiPage } from '@/pages/PhanHoiPage'
import { QuanLyPhanHoiPage } from '@/pages/QuanLyPhanHoiPage'
import { DanhGiaCuaToiPage } from '@/pages/DanhGiaCuaToiPage'
import { TuCheckInPage } from '@/pages/TuCheckInPage'
import { AdminDashboardPage } from '@/pages/AdminDashboardPage'
import { QuanLyNhanVienPage } from '@/pages/QuanLyNhanVienPage'

export default function App() {
  const { user, loading, restore } = useAuth()

  useEffect(() => { restore() }, [restore])

  if (loading) return <Spinner label="Đang khôi phục phiên đăng nhập…" />

  /** Chặn ở giao diện cho gọn. Quyền thật vẫn do backend quyết định. */
  const canQuyen = (element: JSX.Element, vaiTro: UserRole[]) =>
    user && vaiTro.includes(user.role) ? element : <Navigate to="/" replace />

  const canDangNhap = (element: JSX.Element) =>
    user ? element : <Navigate to="/dang-nhap" replace />

  return (
    <Routes>
      <Route element={<Layout />}>
        <Route path="/" element={<BangGiaPage />} />

        <Route path="/dang-nhap" element={user ? <Navigate to="/" replace /> : <DangNhapPage />} />
        <Route path="/dang-ky" element={user ? <Navigate to="/" replace /> : <DangKyPage />} />

        <Route path="/goi-cua-toi" element={canDangNhap(<GoiCuaToiPage />)} />
        <Route path="/buoi-tap" element={canDangNhap(<BuoiTapPage />)} />
        <Route path="/tai-khoan" element={canDangNhap(<TaiKhoanPage />)} />
        <Route path="/phan-hoi"
               element={canQuyen(<PhanHoiPage />, ['MEMBER'])} />
        <Route path="/check-in"
               element={canQuyen(<TuCheckInPage />, ['MEMBER'])} />

        <Route path="/quan-ly-phan-hoi"
               element={canQuyen(<QuanLyPhanHoiPage />, ['RECEPTIONIST', 'ADMIN'])} />
        <Route path="/ban-hang"
               element={canQuyen(<BanHangPage />, ['SALE', 'RECEPTIONIST', 'ADMIN'])} />
        <Route path="/lich-day"
               element={canQuyen(<LichDayPage />, ['TRAINER', 'ADMIN'])} />
        <Route path="/danh-gia-cua-toi"
               element={canQuyen(<DanhGiaCuaToiPage />, ['TRAINER', 'ADMIN'])} />
        <Route path="/quay"
               element={canQuyen(<ManHinhQuayPage />, ['RECEPTIONIST', 'ADMIN'])} />
        <Route path="/thu-ngan"
               element={canQuyen(<ThuNganPage />, ['RECEPTIONIST', 'ACCOUNTANT', 'ADMIN'])} />
        <Route path="/quan-ly"
               element={canQuyen(<QuanLyHopDongPage />,
                        ['SALE', 'RECEPTIONIST', 'ACCOUNTANT', 'ADMIN'])} />
        <Route path="/thong-ke"
               element={canQuyen(<ThongKePage />, ['ADMIN', 'ACCOUNTANT'])} />
        <Route path="/quan-tri"
               element={canQuyen(<AdminDashboardPage />, ['ADMIN'])} />
        <Route path="/quan-tri/nhan-vien"
               element={canQuyen(<QuanLyNhanVienPage />, ['ADMIN'])} />

        <Route path="*" element={<Navigate to="/" replace />} />
      </Route>
    </Routes>
  )
}
