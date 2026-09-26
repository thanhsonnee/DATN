import { useState } from 'react'
import { usePublicCreateLead } from '@/hooks/useLeads'
import { useMemberships } from '@/hooks/useMemberships'
import { Button } from '@/components/ui/Button'
import { Input } from '@/components/ui/Input'
import { Alert } from '@/components/ui/Alert'
import { Modal } from '@/components/ui/Modal'
import { tien } from '@/lib/format'

/**
 * Form tư vấn công khai (nguồn WEB_FORM) — dùng chung cho:
 * - Trang Bảng giá (`/`): khách vãng lai chưa đăng nhập tự điền để nhận tư vấn.
 * - Trang Bán hàng (`/ban-hang`): Sale bấm để mô phỏng lại đúng luồng khách gửi form.
 */
export function HopThoaiFormTuVan({ open, onClose }: { open: boolean; onClose: () => void }) {
  const publicLead = usePublicCreateLead()
  const { data: goiTaps } = useMemberships()

  const [fullName, setFullName] = useState('')
  const [phone, setPhone] = useState('')
  const [email, setEmail] = useState('')
  const [membershipId, setMembershipId] = useState<number | ''>('')
  const [note, setNote] = useState('')
  const [thanhCong, setThanhCong] = useState(false)

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault()
    if (!fullName || !phone) return

    publicLead.mutate(
      {
        fullName: fullName.trim(),
        phone: phone.trim(),
        email: email.trim() || undefined,
        interestedMembershipId: membershipId ? Number(membershipId) : undefined,
        note: note.trim() || undefined,
      },
      {
        onSuccess: () => {
          setThanhCong(true)
          setTimeout(() => {
            setThanhCong(false)
            setFullName('')
            setPhone('')
            setEmail('')
            setMembershipId('')
            setNote('')
            onClose()
          }, 1500)
        },
      }
    )
  }

  return (
    <Modal open={open} title="Đăng ký nhận tư vấn miễn phí" onClose={onClose}>
      {thanhCong ? (
        <Alert tone="success">
          Đã gửi thông tin thành công! Nhân viên tư vấn sẽ liên hệ với bạn trong thời gian sớm nhất.
        </Alert>
      ) : (
        <form onSubmit={handleSubmit} className="space-y-4">
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
            <Input
              label="Họ và tên của bạn *"
              value={fullName}
              onChange={(e) => setFullName(e.target.value)}
              placeholder="Nguyễn Văn A"
              required
            />
            <Input
              label="Số điện thoại *"
              value={phone}
              onChange={(e) => setPhone(e.target.value)}
              placeholder="0912345678"
              required
            />
          </div>

          <Input
            label="Email (tùy chọn)"
            type="email"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            placeholder="ban@gmail.com"
          />

          <div>
            <label className="mb-1 block text-sm font-medium text-slate-700">Gói tập quan tâm</label>
            <select
              value={membershipId}
              onChange={(e) => setMembershipId(e.target.value ? Number(e.target.value) : '')}
              className="w-full rounded-lg border border-slate-300 px-3 py-2 text-sm"
            >
              <option value="">-- Chọn gói muốn nhận tư vấn --</option>
              {goiTaps?.map((g) => (
                <option key={g.id} value={g.id}>
                  {g.name} ({tien(g.price)})
                </option>
              ))}
            </select>
          </div>

          <Input
            label="Lời nhắn / Nhu cầu"
            value={note}
            onChange={(e) => setNote(e.target.value)}
            placeholder="Tư vấn giúp mình lịch tập buổi tối..."
          />

          <div className="flex justify-end gap-2 pt-2">
            <Button variant="secondary" type="button" onClick={onClose}>Hủy</Button>
            <Button type="submit" loading={publicLead.isPending}>Gửi yêu cầu tư vấn</Button>
          </div>
        </form>
      )}
    </Modal>
  )
}
