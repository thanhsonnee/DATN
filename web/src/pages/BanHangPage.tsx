import { useState } from 'react'
import {
  useLeads,
  useCreateLead,
  usePublicCreateLead,
  useUpdateLeadContact,
  useMarkLost,
  useAppUserLeads,
  useFunnelStats,
} from '@/hooks/useLeads'
import { useMemberships } from '@/hooks/useMemberships'
import { Card, CardBody, CardHeader } from '@/components/ui/Card'
import { Button } from '@/components/ui/Button'
import { Input } from '@/components/ui/Input'
import { Badge } from '@/components/ui/Badge'
import { Alert } from '@/components/ui/Alert'
import { Modal } from '@/components/ui/Modal'
import { EmptyState, Spinner } from '@/components/ui/Spinner'
import { ApiError } from '@/api/client'
import { tien } from '@/lib/format'
import { ngayGio } from '@/lib/format-cde'
import type { Lead, LeadSource, LeadStage, LostReason } from '@/api/types-cde'

/** Màn hình Bán hàng & Quản lý Phễu Leads (Phân đoạn F). */
export function BanHangPage() {
  const [activeTab, setActiveTab] = useState<'FUNNEL' | 'APP_USERS'>('FUNNEL')
  const [filterStage, setFilterStage] = useState<LeadStage | 'ALL'>('ALL')

  const { data: stats, isLoading: isStatsLoading } = useFunnelStats()
  const { data: leads, isLoading: isLeadsLoading } = useLeads(
    filterStage === 'ALL' ? undefined : filterStage
  )
  const { data: appUsers, isLoading: isAppUsersLoading } = useAppUserLeads()

  const [openCreate, setOpenCreate] = useState(false)
  const [openPublicForm, setOpenPublicForm] = useState(false)
  const [selectedLeadForContact, setSelectedLeadForContact] = useState<Lead | null>(null)
  const [selectedLeadForLost, setSelectedLeadForLost] = useState<Lead | null>(null)

  if (isStatsLoading || isLeadsLoading || isAppUsersLoading) return <Spinner />

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
        <div>
          <h1 className="text-2xl font-bold text-slate-900">Bán hàng & CRM</h1>
          <p className="text-sm text-slate-500">Quản lý khách hàng tiềm năng, phễu bán hàng và chăm sóc hội viên mới</p>
        </div>

        <div className="flex items-center gap-2">
          <Button variant="secondary" onClick={() => setOpenPublicForm(true)}>
            🌐 Form tư vấn Web
          </Button>
          <Button onClick={() => setOpenCreate(true)}>+ Thêm Lead mới</Button>
        </div>
      </div>

      {/* Funnel Overview Cards */}
      <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-6 gap-3">
        <FunnelCard
          title="1. Mới (NEW)"
          count={stats?.stageCounts?.['NEW'] ?? 0}
          tone="gray"
          active={filterStage === 'NEW'}
          onClick={() => setFilterStage(filterStage === 'NEW' ? 'ALL' : 'NEW')}
        />
        <FunnelCard
          title="2. Đã liên hệ"
          count={stats?.stageCounts?.['CONTACTED'] ?? 0}
          tone="blue"
          active={filterStage === 'CONTACTED'}
          onClick={() => setFilterStage(filterStage === 'CONTACTED' ? 'ALL' : 'CONTACTED')}
        />
        <FunnelCard
          title="3. Hẹn tập thử"
          count={stats?.stageCounts?.['TRIAL_BOOKED'] ?? 0}
          tone="amber"
          active={filterStage === 'TRIAL_BOOKED'}
          onClick={() => setFilterStage(filterStage === 'TRIAL_BOOKED' ? 'ALL' : 'TRIAL_BOOKED')}
        />
        <FunnelCard
          title="4. Đã tập thử"
          count={stats?.stageCounts?.['TRIAL_DONE'] ?? 0}
          tone="blue"
          active={filterStage === 'TRIAL_DONE'}
          onClick={() => setFilterStage(filterStage === 'TRIAL_DONE' ? 'ALL' : 'TRIAL_DONE')}
        />
        <FunnelCard
          title="5. Đã chốt (WON)"
          count={stats?.stageCounts?.['WON'] ?? 0}
          tone="green"
          active={filterStage === 'WON'}
          onClick={() => setFilterStage(filterStage === 'WON' ? 'ALL' : 'WON')}
        />
        <FunnelCard
          title="6. Mất khách (LOST)"
          count={stats?.stageCounts?.['LOST'] ?? 0}
          tone="red"
          active={filterStage === 'LOST'}
          onClick={() => setFilterStage(filterStage === 'LOST' ? 'ALL' : 'LOST')}
        />
      </div>

      {/* Tabs */}
      <div className="flex items-center justify-between border-b border-slate-200 pb-2">
        <div className="flex items-center gap-4">
          <button
            onClick={() => setActiveTab('FUNNEL')}
            className={`pb-2 text-sm font-semibold transition border-b-2 ${
              activeTab === 'FUNNEL'
                ? 'border-brand-600 text-brand-700'
                : 'border-transparent text-slate-500 hover:text-slate-800'
            }`}
          >
            Phễu khách hàng tiềm năng ({leads?.length ?? 0})
          </button>
          <button
            onClick={() => setActiveTab('APP_USERS')}
            className={`pb-2 text-sm font-semibold transition border-b-2 ${
              activeTab === 'APP_USERS'
                ? 'border-brand-600 text-brand-700'
                : 'border-transparent text-slate-500 hover:text-slate-800'
            }`}
          >
            Đăng ký App chưa mua gói ({appUsers?.length ?? 0})
          </button>
        </div>

        {activeTab === 'FUNNEL' && filterStage !== 'ALL' && (
          <button
            onClick={() => setFilterStage('ALL')}
            className="text-xs text-brand-600 hover:underline font-medium"
          >
            ✕ Xóa bộ lọc phễu
          </button>
        )}
      </div>

      {/* Main Tab Content */}
      {activeTab === 'FUNNEL' && (
        <Card>
          <CardBody className="p-0">
            {!leads || leads.length === 0 ? (
              <EmptyState title="Không có khách hàng tiềm năng nào phù hợp" />
            ) : (
              <div className="overflow-x-auto">
                <table className="w-full text-sm">
                  <thead className="border-b border-slate-100 bg-slate-50 text-left text-xs uppercase text-slate-500">
                    <tr>
                      <th className="px-5 py-3">Khách hàng</th>
                      <th className="px-5 py-3">Nguồn</th>
                      <th className="px-5 py-3">Gói quan tâm</th>
                      <th className="px-5 py-3">Sale phụ trách</th>
                      <th className="px-5 py-3">Giai đoạn</th>
                      <th className="px-5 py-3">Liên hệ gần nhất & Ghi chú</th>
                      <th className="px-5 py-3">Hẹn gọi lại</th>
                      <th className="px-5 py-3 text-right">Thao tác</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-slate-100">
                    {leads.map((l) => (
                      <tr key={l.id} className="hover:bg-slate-50">
                        <td className="px-5 py-3">
                          <div className="font-semibold text-slate-900">{l.fullName}</div>
                          <div className="font-mono text-xs text-brand-700">{l.phone}</div>
                          {l.email && <div className="text-xs text-slate-400">{l.email}</div>}
                        </td>
                        <td className="px-5 py-3">
                          <Badge tone="gray">{tenNguonLead(l.source)}</Badge>
                        </td>
                        <td className="px-5 py-3">
                          {l.interestedMembershipName ? (
                            <div>
                              <span className="font-medium text-slate-800">{l.interestedMembershipName}</span>
                              {l.interestedMembershipPrice && (
                                <div className="text-xs text-slate-500">{tien(l.interestedMembershipPrice)}</div>
                              )}
                            </div>
                          ) : (
                            <span className="text-xs text-slate-400">Chưa chọn</span>
                          )}
                        </td>
                        <td className="px-5 py-3">
                          {l.assignedToName ? (
                            <span className="text-slate-800 font-medium">{l.assignedToName}</span>
                          ) : (
                            <span className="text-xs text-amber-600 font-medium">Chưa phân công</span>
                          )}
                        </td>
                        <td className="px-5 py-3">
                          <Badge tone={toneChoStage(l.stage)}>{tenStage(l.stage)}</Badge>
                          {l.stage === 'LOST' && l.lostReason && (
                            <span className="block text-[10px] text-red-600 mt-0.5">
                              Lý do: {tenLyDoMatKhach(l.lostReason)}
                            </span>
                          )}
                        </td>
                        <td className="px-5 py-3 max-w-xs">
                          {l.lastContactAt && (
                            <div className="text-[11px] text-slate-400 mb-0.5">{ngayGio(l.lastContactAt)}</div>
                          )}
                          <div className="text-xs text-slate-700 line-clamp-2">
                            {l.lastContactNote ?? 'Chưa có ghi chú trao đổi'}
                          </div>
                        </td>
                        <td className="px-5 py-3">
                          {l.nextFollowUp ? (
                            <span className="text-xs font-semibold text-brand-700">{l.nextFollowUp}</span>
                          ) : (
                            <span className="text-xs text-slate-400">—</span>
                          )}
                        </td>
                        <td className="px-5 py-3 text-right space-x-1 whitespace-nowrap">
                          {l.stage !== 'WON' && l.stage !== 'LOST' && (
                            <>
                              <Button
                                variant="secondary"
                                className="!py-1 !px-2 !text-xs"
                                onClick={() => setSelectedLeadForContact(l)}
                              >
                                📞 Chăm sóc
                              </Button>
                              <button
                                onClick={() => setSelectedLeadForLost(l)}
                                className="text-xs text-red-600 hover:text-red-800 font-medium px-1.5 py-1"
                              >
                                Mất khách
                              </button>
                            </>
                          )}
                          {l.stage === 'WON' && (
                            <span className="text-xs font-semibold text-emerald-700">✓ Đã mua gói</span>
                          )}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </CardBody>
        </Card>
      )}

      {/* Tab APP_USERS */}
      {activeTab === 'APP_USERS' && (
        <Card>
          <CardHeader
            title="Khách hàng đã đăng ký App nhưng chưa mua gói (APP_SELF)"
            subtitle="Cơ hội vàng cho Sales: Khách đã chủ động tải app và quan tâm phòng tập"
            action={<Badge tone="amber">{appUsers?.length ?? 0} tài khoản</Badge>}
          />
          <CardBody className="p-0">
            {!appUsers || appUsers.length === 0 ? (
              <EmptyState title="Tất cả người dùng tải app đều đã là hội viên có gói tập" />
            ) : (
              <div className="overflow-x-auto">
                <table className="w-full text-sm">
                  <thead className="border-b border-slate-100 bg-slate-50 text-left text-xs uppercase text-slate-500">
                    <tr>
                      <th className="px-5 py-3">Họ và tên</th>
                      <th className="px-5 py-3">Số điện thoại</th>
                      <th className="px-5 py-3">Email</th>
                      <th className="px-5 py-3">Ngày đăng ký app</th>
                      <th className="px-5 py-3">Thời gian trôi qua</th>
                      <th className="px-5 py-3 text-right"></th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-slate-100">
                    {appUsers.map((u) => (
                      <tr key={u.personId} className="hover:bg-slate-50">
                        <td className="px-5 py-3 font-semibold text-slate-900">{u.fullName}</td>
                        <td className="px-5 py-3 font-mono text-brand-700">{u.phone}</td>
                        <td className="px-5 py-3 text-slate-500">{u.email ?? '—'}</td>
                        <td className="px-5 py-3 text-slate-600">{ngayGio(u.registeredAt)}</td>
                        <td className="px-5 py-3">
                          <Badge tone={u.daysSinceRegistration >= 3 ? 'amber' : 'gray'}>
                            {u.daysSinceRegistration} ngày trước
                          </Badge>
                        </td>
                        <td className="px-5 py-3 text-right">
                          <Button
                            variant="secondary"
                            className="!py-1 !px-3 !text-xs"
                            onClick={() => {
                              setSelectedLeadForContact({
                                id: 0,
                                personId: u.personId,
                                fullName: u.fullName,
                                phone: u.phone,
                                email: u.email,
                                source: 'APP_SELF',
                                interestedMembershipId: null,
                                interestedMembershipName: null,
                                interestedMembershipCode: null,
                                interestedMembershipPrice: null,
                                assignedToId: null,
                                assignedToName: null,
                                assignedToCode: null,
                                stage: 'NEW',
                                lostReason: null,
                                lastContactAt: null,
                                lastContactNote: null,
                                nextFollowUp: null,
                                createdAt: u.registeredAt,
                              })
                            }}
                          >
                            📞 Gọi tư vấn ngay
                          </Button>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </CardBody>
        </Card>
      )}

      {/* Modals */}
      <HopThoaiTaoLead open={openCreate} onClose={() => setOpenCreate(false)} />
      <HopThoaiFormWeb open={openPublicForm} onClose={() => setOpenPublicForm(false)} />

      {selectedLeadForContact && (
        <HopThoaiChamSocLead
          lead={selectedLeadForContact}
          onClose={() => setSelectedLeadForContact(null)}
        />
      )}

      {selectedLeadForLost && (
        <HopThoaiDanhDauThatBai
          lead={selectedLeadForLost}
          onClose={() => setSelectedLeadForLost(null)}
        />
      )}
    </div>
  )
}

function FunnelCard({
  title, count, tone, active, onClick,
}: {
  title: string
  count: number
  tone: 'gray' | 'green' | 'amber' | 'red' | 'blue'
  active?: boolean
  onClick?: () => void
}) {
  return (
    <div
      onClick={onClick}
      className={`cursor-pointer rounded-xl border p-3 transition ${
        active
          ? 'border-brand-600 bg-brand-50/50 shadow-sm ring-2 ring-brand-500/20'
          : 'border-slate-200 bg-white hover:border-slate-300'
      }`}
    >
      <span className="text-xs font-semibold text-slate-500">{title}</span>
      <div className="mt-1.5 flex items-baseline justify-between">
        <span className="text-2xl font-bold text-slate-900">{count}</span>
        <Badge tone={tone}>{count > 0 ? `${count} lead` : '0'}</Badge>
      </div>
    </div>
  )
}

// -----------------------------------------------------------------------------
// Modals
// -----------------------------------------------------------------------------

function HopThoaiTaoLead({ open, onClose }: { open: boolean; onClose: () => void }) {
  const createLead = useCreateLead()
  const { data: goiTaps } = useMemberships()

  const [fullName, setFullName] = useState('')
  const [phone, setPhone] = useState('')
  const [email, setEmail] = useState('')
  const [source, setSource] = useState<LeadSource>('WALK_IN')
  const [membershipId, setMembershipId] = useState<number | ''>('')
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
        note: note.trim() || undefined,
        nextFollowUp: nextFollowUp || undefined,
      },
      {
        onSuccess: () => {
          setFullName('')
          setPhone('')
          setEmail('')
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

function HopThoaiChamSocLead({ lead, onClose }: { lead: Lead; onClose: () => void }) {
  const updateContact = useUpdateLeadContact()
  const createLead = useCreateLead()
  const { data: goiTaps } = useMemberships()

  const [stage, setStage] = useState<LeadStage>(
    lead.stage === 'NEW' ? 'CONTACTED' : lead.stage
  )
  const [membershipId, setMembershipId] = useState<number | ''>(
    lead.interestedMembershipId ?? ''
  )
  const [contactNote, setContactNote] = useState('')
  const [nextFollowUp, setNextFollowUp] = useState(lead.nextFollowUp ?? '')

  const handleSave = (e: React.FormEvent) => {
    e.preventDefault()

    // Nếu là lead ảo từ AppUsers (id === 0), tạo lead mới
    if (lead.id === 0) {
      createLead.mutate(
        {
          fullName: lead.fullName,
          phone: lead.phone,
          email: lead.email || undefined,
          source: 'APP_SELF',
          interestedMembershipId: membershipId ? Number(membershipId) : undefined,
          note: contactNote.trim() || undefined,
          nextFollowUp: nextFollowUp || undefined,
        },
        { onSuccess: onClose }
      )
      return
    }

    updateContact.mutate(
      {
        leadId: lead.id,
        stage,
        contactNote: contactNote.trim() || undefined,
        nextFollowUp: nextFollowUp || undefined,
        interestedMembershipId: membershipId ? Number(membershipId) : undefined,
      },
      { onSuccess: onClose }
    )
  }

  return (
    <Modal open={true} title={`Chăm sóc khách: ${lead.fullName} (${lead.phone})`} onClose={onClose}>
      <form onSubmit={handleSave} className="space-y-4">
        <div>
          <label className="mb-1 block text-sm font-medium text-slate-700">Chuyển giai đoạn phễu *</label>
          <select
            value={stage}
            onChange={(e) => setStage(e.target.value as LeadStage)}
            className="w-full rounded-lg border border-slate-300 px-3 py-2 text-sm font-medium"
          >
            <option value="CONTACTED">2. Đã liên hệ / Đang tư vấn (CONTACTED)</option>
            <option value="TRIAL_BOOKED">3. Đã hẹn lịch tập thử (TRIAL_BOOKED)</option>
            <option value="TRIAL_DONE">4. Khách đã đến tập thử xong (TRIAL_DONE)</option>
            <option value="WON">5. Chốt mua gói thành công (WON)</option>
          </select>
        </div>

        <div>
          <label className="mb-1 block text-sm font-medium text-slate-700">Gói tập quan tâm</label>
          <select
            value={membershipId}
            onChange={(e) => setMembershipId(e.target.value ? Number(e.target.value) : '')}
            className="w-full rounded-lg border border-slate-300 px-3 py-2 text-sm"
          >
            <option value="">-- Chưa chọn gói --</option>
            {goiTaps?.map((g) => (
              <option key={g.id} value={g.id}>
                {g.name} ({tien(g.price)})
              </option>
            ))}
          </select>
        </div>

        <Input
          label="Nội dung cuộc trao đổi vừa rồi *"
          value={contactNote}
          onChange={(e) => setContactNote(e.target.value)}
          placeholder="Khách khen cơ sở vật chất, hẹn chiều mai 17h đến tập thử..."
          required
        />

        <Input
          label="Hẹn ngày liên hệ tiếp theo (Follow-up)"
          type="date"
          value={nextFollowUp}
          onChange={(e) => setNextFollowUp(e.target.value)}
        />

        <div className="flex justify-end gap-2 pt-2">
          <Button variant="secondary" type="button" onClick={onClose}>Hủy</Button>
          <Button type="submit" loading={updateContact.isPending || createLead.isPending}>
            Lưu nhật ký chăm sóc
          </Button>
        </div>
      </form>
    </Modal>
  )
}

function HopThoaiDanhDauThatBai({ lead, onClose }: { lead: Lead; onClose: () => void }) {
  const markLost = useMarkLost()
  const [lostReason, setLostReason] = useState<LostReason>('PRICE')
  const [note, setNote] = useState('')

  const handleSave = (e: React.FormEvent) => {
    e.preventDefault()
    markLost.mutate(
      {
        leadId: lead.id,
        lostReason,
        note: note.trim() || undefined,
      },
      { onSuccess: onClose }
    )
  }

  return (
    <Modal open={true} title={`Đánh dấu mất khách: ${lead.fullName}`} onClose={onClose}>
      <form onSubmit={handleSave} className="space-y-4">
        <Alert tone="error">
          Dữ liệu lý do mất khách là cơ sở quan trọng giúp phòng gym cải tiến chính sách giá và dịch vụ.
        </Alert>

        <div>
          <label className="mb-1 block text-sm font-medium text-slate-700">Lý do không chốt được gói *</label>
          <select
            value={lostReason}
            onChange={(e) => setLostReason(e.target.value as LostReason)}
            className="w-full rounded-lg border border-slate-300 px-3 py-2 text-sm font-semibold text-red-700"
          >
            <option value="PRICE">Giá cao / Vượt ngân sách (PRICE)</option>
            <option value="LOCATION">Khoảng cách xa / Không tiện đường (LOCATION)</option>
            <option value="COMPETITOR">Chọn phòng gym đối thủ (COMPETITOR)</option>
            <option value="NOT_READY">Chưa sẵn sàng / Bận công việc (NOT_READY)</option>
            <option value="NO_RESPONSE">Không nghe máy / Không phản hồi (NO_RESPONSE)</option>
          </select>
        </div>

        <Input
          label="Ghi chú chi tiết lý do"
          value={note}
          onChange={(e) => setNote(e.target.value)}
          placeholder="Khách chê giá gói 12 tháng cao hơn bên cạnh 500k..."
        />

        <div className="flex justify-end gap-2 pt-2">
          <Button variant="secondary" type="button" onClick={onClose}>Hủy</Button>
          <Button type="submit" loading={markLost.isPending}>
            Xác nhận Mất khách
          </Button>
        </div>
      </form>
    </Modal>
  )
}

function HopThoaiFormWeb({ open, onClose }: { open: boolean; onClose: () => void }) {
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
            onClose()
          }, 1500)
        },
      }
    )
  }

  return (
    <Modal open={open} title="Giả lập: Khách hàng gửi Form tư vấn từ Website" onClose={onClose}>
      {thanhCong ? (
        <Alert tone="success">
          Đã gửi thông tin thành công! Hệ thống đã tự động tạo Lead nguồn <b>WEB_FORM</b> ở trạng thái <b>NEW</b>.
        </Alert>
      ) : (
        <form onSubmit={handleSubmit} className="space-y-4">
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
            <Input
              label="Họ và tên của bạn *"
              value={fullName}
              onChange={(e) => setFullName(e.target.value)}
              placeholder="Nguyễn Khách Lạ"
              required
            />
            <Input
              label="Số điện thoại *"
              value={phone}
              onChange={(e) => setPhone(e.target.value)}
              placeholder="0988111222"
              required
            />
          </div>

          <Input
            label="Email (tùy chọn)"
            type="email"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            placeholder="khachla@gmail.com"
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

function tenNguonLead(src: LeadSource) {
  switch (src) {
    case 'WALK_IN': return 'Quầy trực tiếp'
    case 'HOTLINE': return 'Hotline'
    case 'WEB_FORM': return 'Website'
    case 'REFERRAL': return 'Giới thiệu'
    case 'APP_SELF': return 'Tải App'
    default: return src
  }
}

function tenStage(st: LeadStage) {
  switch (st) {
    case 'NEW': return '1. Mới'
    case 'CONTACTED': return '2. Đã liên hệ'
    case 'TRIAL_BOOKED': return '3. Hẹn tập thử'
    case 'TRIAL_DONE': return '4. Đã tập thử'
    case 'WON': return '5. Đã chốt'
    case 'LOST': return '6. Mất khách'
    default: return st
  }
}

function toneChoStage(st: LeadStage): 'gray' | 'green' | 'amber' | 'red' | 'blue' {
  switch (st) {
    case 'NEW': return 'gray'
    case 'CONTACTED': return 'blue'
    case 'TRIAL_BOOKED': return 'amber'
    case 'TRIAL_DONE': return 'blue'
    case 'WON': return 'green'
    case 'LOST': return 'red'
    default: return 'gray'
  }
}

function tenLyDoMatKhach(reason: LostReason) {
  switch (reason) {
    case 'PRICE': return 'Giá cao'
    case 'LOCATION': return 'Xa / Không tiện'
    case 'COMPETITOR': return 'Chọn bên khác'
    case 'NOT_READY': return 'Chưa sẵn sàng'
    case 'NO_RESPONSE': return 'Không nghe máy'
    default: return reason
  }
}
