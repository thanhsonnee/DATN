package com.gym.identity.service;

import com.gym.common.exception.ApiException;
import com.gym.common.util.FileStorageService;
import com.gym.identity.api.dto.MemberPhotoResponse;
import com.gym.identity.api.dto.MemberSearchResult;
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
}
