import { useState } from 'react'
import { useCreateLead } from '@/hooks/useLeads'
import { useMemberships } from '@/hooks/useMemberships'
import { useSalesEmployees } from '@/hooks/useLookup'
import { Button } from '@/components/ui/Button'
import { Input } from '@/components/ui/Input'
import { Alert } from '@/components/ui/Alert'
import { Modal } from '@/components/ui/Modal'
import { ApiError } from '@/api/client'
import { tien } from '@/lib/format'
import type { LeadSource } from '@/api/types-cde'

/**
 * Tiếp nhận khách hàng tiềm năng mới (Lead) — dùng chung cho màn hình Bán hàng (Sale)
 * và màn hình quầy (Lễ tân). Lễ tân có thể chọn thẳng Sale phụ trách để báo trực tiếp
 * "chăm sóc khách này đi" thay vì để trống chờ Sale tự nhận.
 */
export function HopThoaiTaoLead({ open, onClose }: { open: boolean; onClose: () => void }) {
  const createLead = useCreateLead()
  const { data: goiTaps } = useMemberships()
  const { data: sales } = useSalesEmployees()

  const [fullName, setFullName] = useState('')
  const [phone, setPhone] = useState('')
  const [email, setEmail] = useState('')
  const [source, setSource] = useState<LeadSource>('WALK_IN')
  const [membershipId, setMembershipId] = useState<number | ''>('')
  const [assignedToEmployeeId, setAssignedToEmployeeId] = useState<number | ''>('')
  const [note, setNote] = useState('')
  const [nextFollowUp, setNextFollowUp] = useState('')

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault()
    if (!fullName || !phone) return

    createLead.mutate(
      {
        fullName: fullName.trim(),
        phone: phone.trim(),
        email: email.trim() || undefined,
        source,
        interestedMembershipId: membershipId ? Number(membershipId) : undefined,
        assignedToEmployeeId: assignedToEmployeeId ? Number(assignedToEmployeeId) : undefined,
        note: note.trim() || undefined,
        nextFollowUp: nextFollowUp || undefined,
      },
      {
        onSuccess: () => {
          setFullName('')
          setPhone('')
          setEmail('')
          setAssignedToEmployeeId('')
          setNote('')
          setNextFollowUp('')
          onClose()
        },
      }
    )
  }

  return (
    <Modal open={open} title="Tiếp nhận khách hàng tiềm năng mới (Lead)" onClose={onClose}>
      <form onSubmit={handleSubmit} className="space-y-4">
        <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
          <Input
            label="Họ và tên *"
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
          placeholder="khachhang@example.com"
        />

        <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
          <div>
            <label className="mb-1 block text-sm font-medium text-slate-700">Nguồn tiếp nhận *</label>
            <select
              value={source}
              onChange={(e) => setSource(e.target.value as LeadSource)}
              className="w-full rounded-lg border border-slate-300 px-3 py-2 text-sm"
            >
              <option value="WALK_IN">Đến quầy trực tiếp (WALK_IN)</option>
              <option value="HOTLINE">Gọi Hotline hỏi giá (HOTLINE)</option>
              <option value="WEB_FORM">Để lại số trên Website (WEB_FORM)</option>
              <option value="REFERRAL">Hội viên cũ giới thiệu (REFERRAL)</option>
              <option value="APP_SELF">Tự đăng ký App (APP_SELF)</option>
            </select>
          </div>

          <div>
            <label className="mb-1 block text-sm font-medium text-slate-700">Gói quan tâm</label>
            <select
              value={membershipId}
              onChange={(e) => setMembershipId(e.target.value ? Number(e.target.value) : '')}
              className="w-full rounded-lg border border-slate-300 px-3 py-2 text-sm"
            >
              <option value="">-- Chưa rõ / Tư vấn sau --</option>
              {goiTaps?.map((g) => (
                <option key={g.id} value={g.id}>
                  {g.name} ({tien(g.price)})
                </option>
              ))}
            </select>
          </div>
        </div>

        <div>
          <label className="mb-1 block text-sm font-medium text-slate-700">Sale phụ trách (tùy chọn)</label>
          <select
            value={assignedToEmployeeId}
            onChange={(e) => setAssignedToEmployeeId(e.target.value ? Number(e.target.value) : '')}
            className="w-full rounded-lg border border-slate-300 px-3 py-2 text-sm"
          >
            <option value="">-- Chưa rõ / để Sale tự nhận --</option>
            {sales?.map((s) => (
              <option key={s.id} value={s.id}>
                {s.fullName} ({s.employeeCode})
              </option>
            ))}
          </select>
        </div>

        <Input
          label="Hẹn ngày liên hệ lại (Follow-up)"
          type="date"
          value={nextFollowUp}
          onChange={(e) => setNextFollowUp(e.target.value)}
        />

        <Input
          label="Ghi chú nhu cầu khách"
          value={note}
          onChange={(e) => setNote(e.target.value)}
          placeholder="Muốn giảm cân, hỏi gói 6 tháng kèm PT..."
        />

        {createLead.error instanceof ApiError && (
          <Alert tone="error">
            {createLead.error.fields
              ? Object.values(createLead.error.fields).join(', ')
              : createLead.error.message}
          </Alert>
        )}

        <div className="flex justify-end gap-2 pt-2">
          <Button variant="secondary" type="button" onClick={onClose}>Hủy</Button>
          <Button type="submit" loading={createLead.isPending}>Lưu Lead</Button>
        </div>
      </form>
    </Modal>
  )
}
