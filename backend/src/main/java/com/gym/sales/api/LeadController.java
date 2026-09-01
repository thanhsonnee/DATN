package com.gym.sales.api;

import com.gym.sales.api.dto.*;
import com.gym.sales.domain.LeadStage;
import com.gym.sales.service.LeadService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Bán hàng & CRM", description = "Quản lý khách hàng tiềm năng, phễu bán hàng (Phân đoạn F)")
@RestController
@RequestMapping("/api/v1/leads")
@RequiredArgsConstructor
public class LeadController {

    private final LeadService leadService;

    @Operation(summary = "Tiếp nhận Lead từ Web Form công khai",
            description = "Khách hàng tự để lại số điện thoại trên landing page để nhận tư vấn.")
    @PostMapping("/public")
    public ResponseEntity<LeadResponse> publicCreateLead(@Valid @RequestBody PublicLeadRequest req) {
        var res = leadService.publicCreateLead(req);
        return ResponseEntity.status(HttpStatus.CREATED).body(res);
    }

    @Operation(summary = "Tạo Lead nội bộ",
            description = "Lễ tân/Sale tạo lead từ khách vãng lai, hotline hoặc giới thiệu.")
    @PreAuthorize("hasAnyRole('SALE','RECEPTIONIST','ADMIN')")
    @PostMapping
    public ResponseEntity<LeadResponse> createLead(@AuthenticationPrincipal Long userId,
                                                   @Valid @RequestBody CreateLeadRequest req) {
        var res = leadService.createLead(req, userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(res);
    }

    @Operation(summary = "Tra cứu danh sách Leads",
            description = "Lọc theo giai đoạn phễu hoặc nhân viên phụ trách.")
    @PreAuthorize("hasAnyRole('SALE','RECEPTIONIST','ADMIN')")
    @GetMapping
    public List<LeadResponse> getLeads(@RequestParam(required = false) LeadStage stage,
                                       @RequestParam(required = false) Long assignedTo) {
        return leadService.getLeads(stage, assignedTo);
    }

    @Operation(summary = "Chi tiết một Lead")
    @PreAuthorize("hasAnyRole('SALE','RECEPTIONIST','ADMIN')")
    @GetMapping("/{id}")
    public LeadResponse getLeadById(@PathVariable Long id) {
        return leadService.getLeadById(id);
    }

    @Operation(summary = "Phân công Sale phụ trách Lead")
    @PreAuthorize("hasAnyRole('SALE','ADMIN')")
    @PutMapping("/{id}/assign")
    public LeadResponse assignLead(@PathVariable Long id,
                                   @Valid @RequestBody AssignLeadRequest req) {
        return leadService.assignLead(id, req.employeeId());
    }

    @Operation(summary = "Ghi nhận tương tác và chuyển giai đoạn phễu",
            description = "Ghi lại nội dung cuộc gọi/tư vấn và hẹn ngày liên hệ tiếp theo.")
    @PreAuthorize("hasAnyRole('SALE','RECEPTIONIST','ADMIN')")
    @PutMapping("/{id}/contact")
    public LeadResponse updateContact(@PathVariable Long id,
                                      @Valid @RequestBody UpdateLeadContactRequest req) {
        return leadService.updateContact(id, req);
    }

    @Operation(summary = "Đánh dấu Lead thất bại",
            description = "Bắt buộc chọn lý do mất khách (PRICE, LOCATION, COMPETITOR, NOT_READY, NO_RESPONSE).")
    @PreAuthorize("hasAnyRole('SALE','ADMIN')")
    @PutMapping("/{id}/lost")
    public LeadResponse markLost(@PathVariable Long id,
                                 @Valid @RequestBody MarkLostRequest req) {
        return leadService.markLost(id, req);
    }

    @Operation(summary = "Danh sách khách đã tải app nhưng chưa mua gói (APP_SELF)")
    @PreAuthorize("hasAnyRole('SALE','RECEPTIONIST','ADMIN')")
    @GetMapping("/app-users")
    public List<AppUserLeadResponse> getAppRegisteredLeads() {
        return leadService.getAppRegisteredLeads();
    }

    @Operation(summary = "Thống kê tỷ lệ chuyển đổi phễu và lý do thất bại")
    @PreAuthorize("hasAnyRole('SALE','ADMIN')")
    @GetMapping("/funnel-stats")
    public FunnelStatsResponse getFunnelStats() {
        return leadService.getFunnelStats();
    }
}
