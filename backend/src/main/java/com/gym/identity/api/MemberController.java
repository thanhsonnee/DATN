package com.gym.identity.api;

import com.gym.identity.api.dto.MemberProfileResponse;
import com.gym.identity.api.dto.MemberSearchResult;
import com.gym.identity.api.dto.UpdateMemberProfileRequest;
import com.gym.identity.service.MemberLookupService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
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

    @Operation(summary = "Tải ảnh chân dung khuôn mặt cho hội viên",
            description = "Lễ tân/Admin tải ảnh chụp hoặc tải file ảnh chân dung cho hội viên để đối chiếu khi check-in.")
    @PreAuthorize("hasAnyRole('RECEPTIONIST','ADMIN')")
    @PostMapping(value = "/{memberId}/photo", consumes = org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE)
    public org.springframework.http.ResponseEntity<com.gym.identity.api.dto.MemberPhotoResponse> uploadPhoto(
            @PathVariable Long memberId,
            @RequestParam("file") org.springframework.web.multipart.MultipartFile file) {
        return org.springframework.http.ResponseEntity.ok(memberLookupService.uploadPhoto(memberId, file));
    }

    @Operation(summary = "Xóa ảnh chân dung của hội viên",
            description = "Dùng khi lễ tân trót tải nhầm ảnh, xóa để tải lại ảnh đúng.")
    @PreAuthorize("hasAnyRole('RECEPTIONIST','ADMIN')")
    @DeleteMapping("/{memberId}/photo")
    public com.gym.identity.api.dto.MemberPhotoResponse deletePhoto(@PathVariable Long memberId) {
        return memberLookupService.deletePhoto(memberId);
    }

    @Operation(summary = "Hồ sơ đầy đủ của một hội viên",
            description = "Dùng để nạp sẵn form sửa thông tin cá nhân tại quầy.")
    @PreAuthorize("hasAnyRole('RECEPTIONIST','ADMIN')")
    @GetMapping("/{memberId}")
    public MemberProfileResponse layThongTin(@PathVariable Long memberId) {
        return memberLookupService.layThongTin(memberId);
    }

    @Operation(summary = "Sửa thông tin cá nhân hội viên",
            description = "Không sửa được số điện thoại qua API này — SĐT là tên đăng nhập, "
                        + "đổi cần đồng bộ riêng (chưa triển khai).")
    @PreAuthorize("hasAnyRole('RECEPTIONIST','ADMIN')")
    @PutMapping("/{memberId}")
    public MemberProfileResponse suaThongTin(@PathVariable Long memberId,
                                              @Valid @RequestBody UpdateMemberProfileRequest req) {
        return memberLookupService.capNhatThongTin(memberId, req);
    }
}
