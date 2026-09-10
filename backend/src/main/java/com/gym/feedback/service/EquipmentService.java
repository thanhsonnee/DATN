package com.gym.feedback.service;

import com.gym.common.exception.ApiException;
import com.gym.feedback.api.dto.CreateEquipmentRequest;
import com.gym.feedback.api.dto.EquipmentResponse;
import com.gym.feedback.domain.Equipment;
import com.gym.feedback.repository.EquipmentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** CRUD thiết bị — tầng đơn giản, không có nghiệp vụ. Trạng thái NEEDS_REPAIR do FeedbackService gán. */
@Slf4j
@Service
@RequiredArgsConstructor
public class EquipmentService {

    private final EquipmentRepository equipmentRepo;

    @Transactional
    public EquipmentResponse taoThietBi(CreateEquipmentRequest req) {
        Equipment e = new Equipment();
        e.setName(req.name().trim());
        e.setRoomName(req.roomName() != null ? req.roomName().trim() : null);
        e.setNote(req.note() != null ? req.note().trim() : null);

        e = equipmentRepo.save(e);
        log.info("Tạo thiết bị #{}: {}", e.getId(), e.getName());
        return EquipmentResponse.from(e);
    }

    @Transactional(readOnly = true)
    public List<EquipmentResponse> danhSach() {
        return equipmentRepo.findByDeletedAtIsNullOrderByNameAsc().stream()
                .map(EquipmentResponse::from).toList();
    }

    Equipment require(Long id) {
        return equipmentRepo.findById(id)
                .filter(e -> e.getDeletedAt() == null)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy thiết bị"));
    }
}
