package com.gym.sales.service;

import com.gym.common.exception.ApiException;
import com.gym.identity.domain.Department;
import com.gym.identity.domain.Employee;
import com.gym.identity.domain.Person;
import com.gym.identity.domain.User;
import com.gym.identity.repository.EmployeeRepository;
import com.gym.identity.repository.PersonRepository;
import com.gym.identity.repository.UserRepository;
import com.gym.membership.domain.Membership;
import com.gym.membership.repository.MembershipRepository;
import com.gym.sales.api.dto.*;
import com.gym.sales.domain.Lead;
import com.gym.sales.domain.LeadSource;
import com.gym.sales.domain.LeadStage;
import com.gym.sales.repository.LeadRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Phân đoạn F: Nghiệp vụ Bán hàng & Quản lý khách hàng tiềm năng (Leads).
 *
 * <p>Theo đúng thiết kế CSDL 32_bang.md:
 * <ul>
 *   <li>Chỉ dùng 1 bảng duy nhất {@link Lead}, không tạo bảng phụ lead_interactions</li>
 *   <li>Mỗi lead liên kết với một {@link Person} qua số điện thoại để chống trùng</li>
 *   <li>Quản lý phễu: NEW → CONTACTED → TRIAL_BOOKED → TRIAL_DONE → WON / LOST</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LeadService {

    private final LeadRepository leadRepo;
    private final PersonRepository personRepo;
    private final MembershipRepository membershipRepo;
    private final EmployeeRepository employeeRepo;
    private final UserRepository userRepo;

    /**
     * Nhân viên (Sale/Lễ tân) tiếp nhận hoặc tạo Lead tại quầy, qua hotline hoặc giới thiệu.
     */
    @Transactional
    public LeadResponse createLead(CreateLeadRequest req, Long actorUserId) {
        String phone = req.phone().trim();
        Person person = findOrCreatePersonByPhone(phone, req.fullName(), req.email());

        Lead lead = findOrCreateLead(person, req.source());
        User actor = actorUserId != null ? userRepo.findById(actorUserId).orElse(null) : null;

        if (lead.getCreatedBy() == null && actor != null) {
            lead.setCreatedBy(actor);
        }

        if (req.interestedMembershipId() != null) {
            Membership m = membershipRepo.findById(req.interestedMembershipId())
                    .orElseThrow(() -> ApiException.notFound("Không tìm thấy gói tập quan tâm"));
            lead.setInterestedMembership(m);
        }

        if (req.assignedToEmployeeId() != null) {
            Employee emp = employeeRepo.findById(req.assignedToEmployeeId())
                    .filter(e -> e.getDeletedAt() == null)
                    .orElseThrow(() -> ApiException.notFound("Không tìm thấy nhân viên"));
            lead.setAssignedTo(emp);
        } else {
            tuGanChoSaleNeuChuaCoAi(lead, actor);
        }

        if (req.note() != null && !req.note().isBlank()) {
            lead.setLastContactNote(req.note().trim());
            lead.setLastContactAt(OffsetDateTime.now());
        }
        if (req.nextFollowUp() != null) {
            lead.setNextFollowUp(req.nextFollowUp());
        }

        Lead saved = leadRepo.save(lead);
        log.info("Tạo/cập nhật Lead #{} cho khách {} - nguồn {}", saved.getId(), person.getFullName(), saved.getSource());
        return LeadResponse.from(saved);
    }

    /**
     * Khách hàng tự điền form tư vấn trên Website (Nguồn WEB_FORM).
     */
    @Transactional
    public LeadResponse publicCreateLead(PublicLeadRequest req) {
        String phone = req.phone().trim();
        Person person = findOrCreatePersonByPhone(phone, req.fullName(), req.email());
        Lead lead = findOrCreateLead(person, LeadSource.WEB_FORM);

        if (req.interestedMembershipId() != null) {
            membershipRepo.findById(req.interestedMembershipId()).ifPresent(lead::setInterestedMembership);
        }
        if (req.note() != null && !req.note().isBlank()) {
            lead.setLastContactNote("Lời nhắn từ web: " + req.note().trim());
            lead.setLastContactAt(OffsetDateTime.now());
        }

        lead = leadRepo.save(lead);
        log.info("Tiếp nhận Lead từ Web Form #{} - SĐT: {}", lead.getId(), phone);
        return LeadResponse.from(lead);
    }

    /**
     * Gán Sale phụ trách Lead.
     */
    @Transactional
    public LeadResponse assignLead(Long leadId, Long employeeId) {
        Lead lead = require(leadId);
        Employee emp = employeeRepo.findById(employeeId)
                .filter(e -> e.getDeletedAt() == null)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy nhân viên"));

        lead.setAssignedTo(emp);
        lead = leadRepo.save(lead);
        log.info("Gán Lead #{} cho Sale: {}", leadId, emp.getPerson().getFullName());
        return LeadResponse.from(lead);
    }

    /**
     * Ghi nhận tương tác chăm sóc (gọi điện, hẹn tập thử, cập nhật phễu).
     *
     * <p>Lead chưa có Sale phụ trách (vd. từ form công khai) thì Sale nào LƯU nhật ký
     * chăm sóc TRƯỚC sẽ tự động được gán — không cần màn hình "Nhận lead" riêng, tránh
     * việc nhiều Sale cùng gọi trùng một khách.
     */
    @Transactional
    public LeadResponse updateContact(Long leadId, UpdateLeadContactRequest req, Long actorUserId) {
        Lead lead = require(leadId);

        if (req.stage() == LeadStage.LOST) {
            throw ApiException.badRequest("USE_MARK_LOST", "Vui lòng dùng chức năng Đánh dấu thất bại để ghi rõ lý do");
        }

        User actor = actorUserId != null ? userRepo.findById(actorUserId).orElse(null) : null;
        tuGanChoSaleNeuChuaCoAi(lead, actor);

        lead.setStage(req.stage());
        lead.setLastContactAt(OffsetDateTime.now());

        if (req.contactNote() != null && !req.contactNote().isBlank()) {
            lead.setLastContactNote(req.contactNote().trim());
        }
        if (req.nextFollowUp() != null) {
            lead.setNextFollowUp(req.nextFollowUp());
        }
        if (req.interestedMembershipId() != null) {
            membershipRepo.findById(req.interestedMembershipId()).ifPresent(lead::setInterestedMembership);
        }

        lead = leadRepo.save(lead);
        log.info("Cập nhật Lead #{}: stage={}, nextFollowUp={}", leadId, lead.getStage(), lead.getNextFollowUp());
        return LeadResponse.from(lead);
    }

    /**
     * Sửa thông tin chung của Lead (tên/SĐT/email/nguồn) khi nhập sai lúc tạo.
     * Field nào null trong request thì giữ nguyên giá trị cũ.
     */
    @Transactional
    public LeadResponse updateLead(Long leadId, UpdateLeadRequest req) {
        Lead lead = require(leadId);
        Person p = lead.getPerson();

        if (req.fullName() != null && !req.fullName().isBlank()) {
            p.setFullName(req.fullName().trim());
        }
        if (req.phone() != null && !req.phone().isBlank()) {
            String phone = req.phone().trim();
            personRepo.findByPhoneAndDeletedAtIsNull(phone)
                    .filter(other -> !other.getId().equals(p.getId()))
                    .ifPresent(other -> {
                        throw ApiException.conflict("PHONE_TAKEN", "Số điện thoại này đã được đăng ký");
                    });
            p.setPhone(phone);
        }
        if (req.email() != null) {
            p.setEmail(req.email().isBlank() ? null : req.email().trim());
        }
        personRepo.save(p);

        if (req.source() != null) {
            lead.setSource(req.source());
        }
        if (req.interestedMembershipId() != null) {
            Membership m = membershipRepo.findById(req.interestedMembershipId())
                    .orElseThrow(() -> ApiException.notFound("Không tìm thấy gói tập quan tâm"));
            lead.setInterestedMembership(m);
        }
        lead = leadRepo.save(lead);

        log.info("Cập nhật thông tin chung Lead #{}", leadId);
        return LeadResponse.from(lead);
    }

    /**
     * Xóa Lead (khách trùng/spam nhập nhầm) — xóa mềm.
     */
    @Transactional
    public void deleteLead(Long leadId) {
        Lead lead = require(leadId);
        lead.setDeletedAt(OffsetDateTime.now());
        leadRepo.save(lead);
        log.info("Đã xóa Lead #{}", leadId);
    }

    /**
     * Đánh dấu Thất bại (Bắt buộc chọn lý do mất khách).
     */
    @Transactional
    public LeadResponse markLost(Long leadId, MarkLostRequest req) {
        Lead lead = require(leadId);
        lead.setStage(LeadStage.LOST);
        lead.setLostReason(req.lostReason());
        lead.setLastContactAt(OffsetDateTime.now());

        if (req.note() != null && !req.note().isBlank()) {
            lead.setLastContactNote("Lý do thất bại: " + req.lostReason() + " - " + req.note().trim());
        }

        lead = leadRepo.save(lead);
        log.info("Lead #{} thất bại vì lý do: {}", leadId, req.lostReason());
        return LeadResponse.from(lead);
    }

    /**
     * Tự động chuyển Lead thành WON khi khách hàng ký/kích hoạt hợp đồng.
     */
    @Transactional
    public void markWonByPersonId(Long personId) {
        if (personId == null) return;
        List<Lead> openLeads = leadRepo.findByPersonIdAndStageNotInAndDeletedAtIsNull(
                personId, List.of(LeadStage.WON, LeadStage.LOST));

        for (Lead l : openLeads) {
            l.setStage(LeadStage.WON);
            l.setLastContactAt(OffsetDateTime.now());
            l.setLastContactNote("Đã chốt hợp đồng thành công");
            leadRepo.save(l);
            log.info("Tự động chuyển Lead #{} của personId={} sang WON", l.getId(), personId);
        }
    }

    /**
     * Tra cứu danh sách Lead (có bộ lọc).
     */
    @Transactional(readOnly = true)
    public List<LeadResponse> getLeads(LeadStage stage, Long assignedToEmployeeId) {
        List<Lead> list;
        if (stage != null && assignedToEmployeeId != null) {
            list = leadRepo.findByAssignedToIdAndStageAndDeletedAtIsNullOrderByCreatedAtDesc(assignedToEmployeeId, stage);
        } else if (stage != null) {
            list = leadRepo.findByStageAndDeletedAtIsNullOrderByCreatedAtDesc(stage);
        } else if (assignedToEmployeeId != null) {
            list = leadRepo.findByAssignedToIdAndDeletedAtIsNullOrderByCreatedAtDesc(assignedToEmployeeId);
        } else {
            list = leadRepo.findByDeletedAtIsNullOrderByCreatedAtDesc();
        }
        return list.stream().map(LeadResponse::from).toList();
    }

    /**
     * Chi tiết 1 lead.
     */
    @Transactional(readOnly = true)
    public LeadResponse getLeadById(Long id) {
        return LeadResponse.from(require(id));
    }

    /**
     * Danh sách tài khoản đã đăng ký app nhưng chưa mua gói (APP_SELF).
     */
    @Transactional(readOnly = true)
    public List<AppUserLeadResponse> getAppRegisteredLeads() {
        List<Object[]> rows = leadRepo.findAppRegisteredUsersWithoutMembership();
        List<AppUserLeadResponse> result = new ArrayList<>();
        LocalDate today = LocalDate.now();

        for (Object[] r : rows) {
            Long personId = ((Number) r[0]).longValue();
            String fullName = (String) r[1];
            String phone = (String) r[2];
            String email = (String) r[3];
            OffsetDateTime registeredAt;
            if (r[4] instanceof Timestamp ts) {
                registeredAt = ts.toInstant().atOffset(ZoneOffset.ofHours(7));
            } else if (r[4] instanceof OffsetDateTime odt) {
                registeredAt = odt;
            } else {
                registeredAt = OffsetDateTime.now();
            }

            long days = ChronoUnit.DAYS.between(registeredAt.toLocalDate(), today);
            result.add(new AppUserLeadResponse(personId, fullName, phone, email, registeredAt, days));
        }
        return result;
    }

    /**
     * Thống kê tỷ lệ chuyển đổi phễu bán hàng & phân tích lý do thất bại.
     */
    @Transactional(readOnly = true)
    public FunnelStatsResponse getFunnelStats() {
        Map<String, Long> stageCounts = new LinkedHashMap<>();
        for (LeadStage st : LeadStage.values()) {
            stageCounts.put(st.name(), 0L);
        }

        List<Object[]> stageRows = leadRepo.countByStage();
        long total = 0;
        long wonCount = 0;

        for (Object[] row : stageRows) {
            if (row[0] != null && row[1] != null) {
                String stageName = row[0].toString();
                long count = ((Number) row[1]).longValue();
                stageCounts.put(stageName, count);
                total += count;
                if (LeadStage.WON.name().equals(stageName)) {
                    wonCount = count;
                }
            }
        }

        Map<String, Long> lostCounts = new LinkedHashMap<>();
        List<Object[]> lostRows = leadRepo.countLostByReason();
        for (Object[] row : lostRows) {
            if (row[0] != null && row[1] != null) {
                lostCounts.put(row[0].toString(), ((Number) row[1]).longValue());
            }
        }

        double conversionRate = total > 0 ? ((double) wonCount / total) * 100.0 : 0.0;
        return new FunnelStatsResponse(total, stageCounts, lostCounts, Math.round(conversionRate * 10.0) / 10.0);
    }

    private Lead require(Long id) {
        return leadRepo.findById(id)
                .filter(l -> l.getDeletedAt() == null)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy khách hàng tiềm năng"));
    }

    /**
     * Lead chưa có ai phụ trách thì tự gán cho actor đang thao tác, NẾU actor đó là Sale
     * (phòng ban SALES) — dùng chung cho lúc tạo lead lẫn lúc lưu nhật ký chăm sóc, để
     * "Sale nào chăm sóc trước thì nhận lead đó" mà không cần thêm màn hình "Nhận lead".
     * Không làm gì nếu lead đã có người phụ trách hoặc actor không phải Sale (vd. Lễ tân,
     * Admin thao tác hộ) — tránh gán nhầm cho người không phải Sale.
     */
    private void tuGanChoSaleNeuChuaCoAi(Lead lead, User actor) {
        if (lead.getAssignedTo() != null || actor == null || actor.getPerson() == null) return;

        employeeRepo.findByPersonIdAndDeletedAtIsNull(actor.getPerson().getId())
                .filter(emp -> emp.getDepartment() == Department.SALES)
                .ifPresent(lead::setAssignedTo);
    }

    /** Tìm Person theo SĐT, tạo mới nếu chưa có — chống trùng khách hàng giữa các kênh tiếp nhận. */
    private Person findOrCreatePersonByPhone(String phone, String fullName, String email) {
        return personRepo.findByPhoneAndDeletedAtIsNull(phone)
                .orElseGet(() -> {
                    Person p = new Person();
                    p.setFullName(fullName.trim());
                    p.setPhone(phone);
                    if (email != null && !email.isBlank()) {
                        p.setEmail(email.trim());
                    }
                    return personRepo.save(p);
                });
    }

    /** Dùng lại lead đang mở (chưa WON/LOST) của khách nếu có, không thì tạo lead mới. */
    private Lead findOrCreateLead(Person person, LeadSource source) {
        List<Lead> openLeads = leadRepo.findByPersonIdAndStageNotInAndDeletedAtIsNull(
                person.getId(), List.of(LeadStage.WON, LeadStage.LOST));

        if (!openLeads.isEmpty()) {
            Lead lead = openLeads.get(0);
            log.info("Khách {} đã có lead mở #{}, dùng lại", person.getPhone(), lead.getId());
            return lead;
        }

        Lead lead = new Lead();
        lead.setPerson(person);
        lead.setSource(source);
        lead.setStage(LeadStage.NEW);
        return lead;
    }
}
