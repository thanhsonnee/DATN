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
        Person person = personRepo.findByPhoneAndDeletedAtIsNull(phone)
                .orElseGet(() -> {
                    Person p = new Person();
                    p.setFullName(req.fullName().trim());
                    p.setPhone(phone);
                    if (req.email() != null && !req.email().isBlank()) {
                        p.setEmail(req.email().trim());
                    }
                    return personRepo.save(p);
                });

        // Kiểm tra xem khách này đã có lead đang mở chưa
        List<Lead> openLeads = leadRepo.findByPersonIdAndStageNotInAndDeletedAtIsNull(
                person.getId(), List.of(LeadStage.WON, LeadStage.LOST));

        Lead lead;
        if (!openLeads.isEmpty()) {
            lead = openLeads.get(0);
            log.info("Khách {} đã có lead mở #{}, cập nhật thông tin", phone, lead.getId());
        } else {
            lead = new Lead();
            lead.setPerson(person);
            lead.setSource(req.source());
            lead.setStage(LeadStage.NEW);
        }

        if (req.interestedMembershipId() != null) {
            Membership m = membershipRepo.findById(req.interestedMembershipId()).orElse(null);
            lead.setInterestedMembership(m);
        }

        if (req.assignedToEmployeeId() != null) {
            Employee emp = employeeRepo.findById(req.assignedToEmployeeId()).orElse(null);
            lead.setAssignedTo(emp);
        } else if (actorUserId != null && lead.getAssignedTo() == null) {
            // Tự động gán cho Sale đang đăng nhập nếu người đó thuộc phòng Sales
            User u = userRepo.findById(actorUserId).orElse(null);
            if (u != null && u.getPerson() != null) {
                Employee emp = employeeRepo.findByPersonIdAndDeletedAtIsNull(u.getPerson().getId()).orElse(null);
                if (emp != null && emp.getDepartment() == Department.SALES) {
                    lead.setAssignedTo(emp);
                }
            }
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
        Person person = personRepo.findByPhoneAndDeletedAtIsNull(phone)
                .orElseGet(() -> {
                    Person p = new Person();
                    p.setFullName(req.fullName().trim());
                    p.setPhone(phone);
                    if (req.email() != null && !req.email().isBlank()) {
                        p.setEmail(req.email().trim());
                    }
                    return personRepo.save(p);
                });

        List<Lead> openLeads = leadRepo.findByPersonIdAndStageNotInAndDeletedAtIsNull(
                person.getId(), List.of(LeadStage.WON, LeadStage.LOST));

        Lead lead;
        if (!openLeads.isEmpty()) {
            lead = openLeads.get(0);
        } else {
            lead = new Lead();
            lead.setPerson(person);
            lead.setSource(LeadSource.WEB_FORM);
            lead.setStage(LeadStage.NEW);
        }

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
     */
    @Transactional
    public LeadResponse updateContact(Long leadId, UpdateLeadContactRequest req) {
        Lead lead = require(leadId);

        if (req.stage() == LeadStage.LOST) {
            throw ApiException.badRequest("USE_MARK_LOST", "Vui lòng dùng chức năng Đánh dấu thất bại để ghi rõ lý do");
        }

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
}
