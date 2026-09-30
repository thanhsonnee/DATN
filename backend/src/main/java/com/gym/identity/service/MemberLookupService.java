package com.gym.identity.service;

import com.gym.common.exception.ApiException;
import com.gym.common.util.FileStorageService;
import com.gym.identity.api.dto.MemberPhotoResponse;
import com.gym.identity.api.dto.MemberProfileResponse;
import com.gym.identity.api.dto.MemberSearchResult;
import com.gym.identity.api.dto.UpdateMemberProfileRequest;
import com.gym.identity.domain.Member;
import com.gym.identity.domain.Person;
import com.gym.identity.repository.MemberRepository;
import com.gym.identity.repository.PersonRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * Tra cứu hội viên cho các màn hình chọn-bằng-tên (không gõ tay mã số).
 *
 * DTO phải được dựng ở đây, bên trong transaction — {@code Person} được lazy-load
 * qua {@code Member.person}, nên nếu controller tự map entity -> DTO sau khi
 * repository trả về thì session Hibernate đã đóng, ném LazyInitializationException.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MemberLookupService {

    private final MemberRepository memberRepo;
    private final PersonRepository personRepo;
    private final FileStorageService fileStorageService;

    @Transactional(readOnly = true)
    public List<MemberSearchResult> timKiem(String q) {
        if (q == null || q.isBlank() || q.trim().length() < 2) return List.of();
        return memberRepo.timKiem(q.trim()).stream().map(MemberSearchResult::from).toList();
    }

    /**
     * Tải và cập nhật ảnh chân dung khuôn mặt cho hội viên (Lễ tân/Admin).
     */
    @Transactional
    public MemberPhotoResponse uploadPhoto(Long memberId, MultipartFile file) {
        Member m = memberRepo.findById(memberId)
                .filter(x -> x.getDeletedAt() == null)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy hội viên"));

        Person p = m.getPerson();
        String oldPhotoKey = p.getPhotoKey();
        String photoKey = fileStorageService.storePhoto(p.getId(), file);
        p.setPhotoKey(photoKey);
        personRepo.save(p);
        fileStorageService.deletePhoto(oldPhotoKey);

        log.info("Cập nhật ảnh chân dung thành công cho hội viên #{}: {}", memberId, photoKey);
        return new MemberPhotoResponse(m.getId(), m.getMemberCode(), p.getFullName(), photoKey, "/api/v1/files/photos/" + photoKey);
    }

    /**
     * Xóa ảnh chân dung hiện tại — dùng khi lễ tân trót tải nhầm ảnh.
     */
    @Transactional
    public MemberPhotoResponse deletePhoto(Long memberId) {
        Member m = memberRepo.findById(memberId)
                .filter(x -> x.getDeletedAt() == null)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy hội viên"));

        Person p = m.getPerson();
        String oldPhotoKey = p.getPhotoKey();
        p.setPhotoKey(null);
        personRepo.save(p);
        fileStorageService.deletePhoto(oldPhotoKey);

        log.info("Đã xóa ảnh chân dung của hội viên #{}", memberId);
        return new MemberPhotoResponse(m.getId(), m.getMemberCode(), p.getFullName(), null, null);
    }

    /** Hồ sơ đầy đủ — dùng để nạp sẵn form sửa (Lễ tân/Admin). */
    @Transactional(readOnly = true)
    public MemberProfileResponse layThongTin(Long memberId) {
        Member m = memberRepo.findById(memberId)
                .filter(x -> x.getDeletedAt() == null)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy hội viên"));
        return MemberProfileResponse.from(m);
    }

    /**
     * Sửa thông tin cá nhân hội viên — KHÔNG sửa SĐT (xem ghi chú ở
     * {@link UpdateMemberProfileRequest}). Field nào null trong request thì
     * giữ nguyên giá trị cũ (partial update).
     */
    @Transactional
    public MemberProfileResponse capNhatThongTin(Long memberId, UpdateMemberProfileRequest req) {
        Member m = memberRepo.findById(memberId)
                .filter(x -> x.getDeletedAt() == null)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy hội viên"));

        Person p = m.getPerson();
        if (req.fullName() != null && !req.fullName().isBlank()) {
            p.setFullName(req.fullName().trim());
        }
        if (req.gender() != null) {
            p.setGender(req.gender());
        }
        if (req.birthday() != null) {
            p.setBirthday(req.birthday());
        }
        if (req.nationalId() != null) {
            p.setNationalId(req.nationalId().isBlank() ? null : req.nationalId().trim());
        }
        if (req.email() != null) {
            String email = req.email().isBlank() ? null : req.email().trim();
            if (email != null) {
                personRepo.findByEmailAndDeletedAtIsNull(email)
                        .filter(other -> !other.getId().equals(p.getId()))
                        .ifPresent(other -> {
                            throw ApiException.conflict("EMAIL_TAKEN", "Email này đã được sử dụng");
                        });
            }
            p.setEmail(email);
        }
        if (req.address() != null) {
            p.setAddress(req.address().isBlank() ? null : req.address().trim());
        }
        if (req.emergencyContactName() != null) {
            p.setEmergencyContactName(req.emergencyContactName().isBlank() ? null : req.emergencyContactName().trim());
        }
        if (req.emergencyContactPhone() != null) {
            p.setEmergencyContactPhone(req.emergencyContactPhone().isBlank() ? null : req.emergencyContactPhone().trim());
        }
        personRepo.save(p);

        if (req.healthNote() != null) {
            m.setHealthNote(req.healthNote().isBlank() ? null : req.healthNote().trim());
        }
        if (req.goal() != null) {
            m.setGoal(req.goal());
        }
        if (req.source() != null) {
            m.setSource(req.source());
        }
        memberRepo.save(m);

        log.info("Đã cập nhật hồ sơ hội viên #{}", memberId);
        return MemberProfileResponse.from(m);
    }
}
