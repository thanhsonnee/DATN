package com.gym.identity.service;

import com.gym.identity.api.dto.MemberSearchResult;
import com.gym.identity.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Tra cứu hội viên cho các màn hình chọn-bằng-tên (không gõ tay mã số).
 *
 * DTO phải được dựng ở đây, bên trong transaction — {@code Person} được lazy-load
 * qua {@code Member.person}, nên nếu controller tự map entity -> DTO sau khi
 * repository trả về thì session Hibernate đã đóng, ném LazyInitializationException.
 */
@Service
@RequiredArgsConstructor
public class MemberLookupService {

    private final MemberRepository memberRepo;

    @Transactional(readOnly = true)
    public List<MemberSearchResult> timKiem(String q) {
        if (q == null || q.isBlank() || q.trim().length() < 2) return List.of();
        return memberRepo.timKiem(q.trim()).stream().map(MemberSearchResult::from).toList();
    }
}
