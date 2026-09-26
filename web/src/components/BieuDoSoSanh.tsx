import { Bar, BarChart, Cell, LabelList, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts'

/**
 * Biểu đồ cột ngang so sánh độ lớn giữa vài mục — dùng cho các nhóm chỉ số
 * dashboard (hội viên, doanh thu...). Một hệ màu (sequential) cho mục bình
 * thường; màu cảnh báo/nguy hiểm chỉ dùng khi chỉ số đó thực sự đáng chú ý,
 * khớp với quy ước màu đã dùng ở các stat tile trong trang.
 */
const MAU_HEX = {
  default: '#2563eb', // brand-600 — khớp màu accent chính của web
  thanhcong: '#047857', // emerald-700
  canhbao: '#b45309', // amber-700
  nguyhiem: '#b91c1c', // red-700
} as const

export interface MucBieuDo {
  nhan: string
  giaTri: number
  mau?: keyof typeof MAU_HEX
}

export function BieuDoSoSanh({ duLieu, dinhDangGiaTri = (v) => v.toLocaleString('vi-VN') }: {
  duLieu: MucBieuDo[]
  dinhDangGiaTri?: (v: number) => string
}) {
  const chieuCao = duLieu.length * 40 + 12
  // Ước lượng bề rộng nhãn giá trị (VD "36.120.000 đ") để không bị cắt chữ —
  // đo trước thay vì để `overflow` cắt mất phần cuối nhãn.
  const bienPhai = Math.max(...duLieu.map((m) => dinhDangGiaTri(m.giaTri).length)) * 6.5 + 20

  return (
    <ResponsiveContainer width="100%" height={chieuCao}>
      <BarChart
        data={duLieu} layout="vertical"
        margin={{ top: 4, right: bienPhai, bottom: 4, left: 4 }}
        barCategoryGap={10}
      >
        <XAxis type="number" hide domain={[0, (dataMax: number) => (dataMax === 0 ? 1 : dataMax * 1.25)]} />
        <YAxis
          type="category" dataKey="nhan" width={172}
          axisLine={false} tickLine={false}
          tick={{ fontSize: 12, fill: '#52514e' }}
        />
        <Tooltip
          cursor={{ fill: '#f1f5f9' }}
          formatter={(value: number) => [dinhDangGiaTri(value), 'Giá trị']}
          contentStyle={{ borderRadius: 8, borderColor: '#e2e8f0', fontSize: 13 }}
        />
        <Bar dataKey="giaTri" radius={[0, 4, 4, 0]} barSize={22} isAnimationActive={false}>
          {duLieu.map((muc, i) => (
            <Cell key={i} fill={MAU_HEX[muc.mau ?? 'default']} />
          ))}
          <LabelList
            dataKey="giaTri" position="right" formatter={dinhDangGiaTri}
            style={{ fill: '#0b0b0b', fontSize: 12, fontWeight: 600 }}
          />
        </Bar>
      </BarChart>
    </ResponsiveContainer>
  )
}
