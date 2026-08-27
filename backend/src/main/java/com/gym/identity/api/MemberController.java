package com.gym.identity.api;

import com.gym.identity.api.dto.MemberSearchResult;
import com.gym.identity.service.MemberLookupService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Hội viên", description = "Tra cứu hội viên cho nhân viên")
@RestController
@RequestMapping("/api/v1/members")
@RequiredArgsConstructor
public class MemberController {

    private final MemberLookupService memberLookupService;

    @Operation(summary = "Tìm hội viên theo tên, số điện thoại hoặc mã hội viên",
            description = "Gõ-tới-đâu-ra-tới-đó. Dùng cho màn hình quầy để lễ tân tìm đúng "
                        + "người mà không cần biết trước mã số.")
    @PreAuthorize("hasAnyRole('RECEPTIONIST','SALE','ACCOUNTANT','ADMIN')")
    @GetMapping("/search")
    public List<MemberSearchResult> timKiem(@RequestParam String q) {
        return memberLookupService.timKiem(q);
    }
}
