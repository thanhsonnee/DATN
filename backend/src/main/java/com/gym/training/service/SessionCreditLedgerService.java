package com.gym.training.service;

import com.gym.common.exception.ApiException;
import com.gym.identity.domain.User;
import com.gym.identity.repository.UserRepository;
import com.gym.membership.domain.Registration;
import com.gym.membership.repository.RegistrationRepository;
import com.gym.training.domain.*;
import com.gym.training.repository.SessionCreditRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * SỔ CÁI TÍN DỤNG BUỔI TẬP — đóng góp học thuật số 1.
 *
 * <p>Áp dụng nguyên lý sổ cái kế toán vào quản lý số buổi tập. Toàn bộ dịch vụ
 * này chỉ có <b>một</b> phương thức ghi dữ liệu duy nhất ({@link #ghiButToan}),
 * mọi thao tác khác đều đi qua nó — nhờ vậy không có đường nào ghi vào sổ cái
 * mà bỏ qua các bước kiểm tra.
 *
 * <h3>Ba bất biến phải luôn đúng</h3>
 * <ol>
 *   <li>Tổng {@code delta} của một hợp đồng == {@code balanceAfter} của bút toán mới nhất</li>
 *   <li>{@code balanceAfter} không bao giờ âm</li>
 *   <li>{@code balanceAfter[i]} == {@code balanceAfter[i-1]} + {@code delta[i]}</li>
 * </ol>
 *
 * <p>Bất biến 2 và 3 được bảo đảm ngay lúc ghi. Bất biến 1 được kiểm chứng bằng
 * property-based testing với hàng nghìn chuỗi thao tác ngẫu nhiên.
 *
 * <h3>Vì sao phải khóa dòng</h3>
 * Hai request cùng trừ buổi một lúc mà không khóa sẽ đọc được cùng một số dư cũ,
 * rồi ghi ra hai bút toán có cùng {@code balanceAfter}. Sổ cái sai ngay lập tức
 * và không tự phát hiện được. Vì thế mọi lần ghi đều khóa bút toán cuối trước.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SessionCreditLedgerService {

    private final SessionCreditRepository ledgerRepo;
    private final RegistrationRepository registrationRepo;
    private final UserRepository userRepo;

    // ------------------------------------------------------------- ghi bút toán

    /**
     * Cấp buổi khi hợp đồng được kích hoạt. Mỗi hợp đồng chỉ cấp <b>một lần</b>
     * (chỉ mục duy nhất ở cơ sở dữ liệu chặn lần thứ hai).
     */
    @Transactional
    public SessionCreditEntry capBuoi(Registration registration, int soBuoi) {
        if (soBuoi <= 0) {
            throw ApiException.badRequest("INVALID_GRANT", "Số buổi cấp phải lớn hơn 0");
        }
        return ghiButToan(registration, LedgerEntryType.GRANT, soBuoi,
                LedgerSourceType.REGISTRATION, registration.getId(),
                "Kích hoạt hợp đồng " + registration.getRegistrationCode(), null);
    }

    /**
     * Trừ một buổi khi buổi tập hoàn thành.
     *
     * <p>Chống trừ hai lần bằng hai lớp: kiểm tra ở tầng ứng dụng cho thông báo
     * dễ hiểu, và chỉ mục duy nhất ở cơ sở dữ liệu chặn tuyệt đối kể cả khi hai
     * request chạy song song lọt qua được lớp kiểm tra.
     */
    @Transactional
    public SessionCreditEntry truBuoi(Registration registration, Long ptSessionId) {
        if (ledgerRepo.existsByEntryTypeAndSourceTypeAndSourceId(
                LedgerEntryType.CONSUME, LedgerSourceType.PT_SESSION, ptSessionId)) {
            throw ApiException.conflict("ALREADY_CONSUMED",
                    "Buổi tập này đã được trừ buổi rồi");
        }
        return ghiButToan(registration, LedgerEntryType.CONSUME, -1,
                LedgerSourceType.PT_SESSION, ptSessionId,
                "Hoàn thành buổi tập #" + ptSessionId, null);
    }

    /** Hoàn lại buổi khi hủy đúng hạn. */
    @Transactional
    public SessionCreditEntry hoanBuoi(Registration registration, Long ptSessionId, String lyDo) {
        return ghiButToan(registration, LedgerEntryType.REFUND, 1,
                LedgerSourceType.PT_SESSION, ptSessionId, lyDo, null);
    }

    /**
     * Thu hồi số buổi còn dư khi hợp đồng hết hạn.
     *
     * <p>Đây là loại biến động <b>không tương ứng với buổi tập nào</b> — cũng là
     * lý do sổ cái phải là bảng riêng, không thể suy ra bằng cách đếm
     * {@code pt_sessions}.
     */
    @Transactional
    public SessionCreditEntry thuHoiBuoiHetHan(Registration registration) {
        int soDu = soDuHienTai(registration.getId());
        if (soDu <= 0) return null;

        return ghiButToan(registration, LedgerEntryType.EXPIRE, -soDu,
                LedgerSourceType.EXPIRY_JOB, registration.getId(),
                "Hợp đồng hết hạn ngày " + registration.getEndDate()
                        + ", thu hồi " + soDu + " buổi chưa dùng", null);
    }

    /**
     * Điều chỉnh thủ công — loại bút toán DUY NHẤT do con người quyết định.
     *
     * <p>Vì thế bắt buộc phải có lý do và người thực hiện: khi kiểm toán, đây là
     * chỗ duy nhất số dư thay đổi mà không do sự kiện nghiệp vụ nào sinh ra.
     */
    @Transactional
    public SessionCreditEntry dieuChinh(Registration registration, int delta,
                                        String lyDo, Long nguoiThucHienId) {
        if (delta == 0) {
            throw ApiException.badRequest("INVALID_ADJUST", "Số buổi điều chỉnh phải khác 0");
        }
        if (lyDo == null || lyDo.isBlank()) {
            throw ApiException.badRequest("REASON_REQUIRED",
                    "Điều chỉnh thủ công bắt buộc ghi lý do");
        }
        User nguoiThucHien = userRepo.findById(nguoiThucHienId)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy người thực hiện"));

        log.warn("Điều chỉnh thủ công sổ cái: hợp đồng={} delta={} người={} lý do={}",
                registration.getRegistrationCode(), delta, nguoiThucHienId, lyDo);

        return ghiButToan(registration, LedgerEntryType.ADJUST, delta,
                LedgerSourceType.MANUAL, null, lyDo, nguoiThucHien);
    }

    // ---------------------------------------------------------------- truy vấn

    /**
     * Số buổi còn lại. Truy vấn KHÔNG khóa dòng — chỉ để hiển thị.
     *
     * <p>Đường ghi có bản khóa riêng ({@code khoaVaLayButToanCuoi}). Nếu dùng chung
     * một truy vấn có khóa cho cả hai, mọi lần xem số dư sẽ chặn lẫn nhau vô ích,
     * và PostgreSQL cũng không cho khóa dòng trong giao dịch chỉ đọc.
     */
    @Transactional(readOnly = true)
    public int soDuHienTai(Long registrationId) {
        return ledgerRepo.layButToanCuoi(registrationId)
                .map(SessionCreditEntry::getBalanceAfter)
                .orElse(0);
    }

    @Transactional(readOnly = true)
    public List<SessionCreditEntry> lichSu(Long registrationId) {
        return ledgerRepo.findByRegistrationIdOrderByIdAsc(registrationId);
    }

    /**
     * Kiểm chứng BẤT BIẾN 1 cho một hợp đồng: tổng cộng dồn phải khớp số dư cuối.
     *
     * <p>Lệch nhau nghĩa là đã có bút toán bị ghi sai — dùng trong kiểm thử và
     * trong job giám sát hằng đêm.
     */
    @Transactional(readOnly = true)
    public boolean batBienConDung(Long registrationId) {
        int tong = ledgerRepo.tongDelta(registrationId);
        int soDuCuoi = soDuHienTai(registrationId);
        boolean khop = tong == soDuCuoi;

        if (!khop) {
            log.error("SỔ CÁI SAI LỆCH tại hợp đồng {}: tổng delta={} nhưng số dư cuối={}",
                    registrationId, tong, soDuCuoi);
        }
        return khop;
    }

    // ------------------------------------------------------- lõi: ghi bút toán

    /**
     * Đường ghi DUY NHẤT vào sổ cái. Mọi thao tác ở trên đều đi qua đây.
     *
     * <p>Để {@code private}, không đánh dấu giao dịch: Spring không chặn được lời
     * gọi giữa các phương thức trong cùng một lớp, nên đặt {@code @Transactional}
     * ở đây sẽ chỉ gây hiểu nhầm là có tác dụng. Giao dịch do các phương thức công
     * khai ở trên mở, và mọi đường vào đều đi qua chúng.
     */
    private SessionCreditEntry ghiButToan(Registration registration,
                                            LedgerEntryType entryType,
                                            int delta,
                                            LedgerSourceType sourceType,
                                            Long sourceId,
                                            String reason,
                                            User createdBy) {

        // KHÓA DÒNG HỢP ĐỒNG trước, buộc mọi luồng ghi sổ cái của hợp đồng này
        // phải xếp hàng. Không khóa bút toán cuối vì cách đó KHÔNG chặn được việc
        // chèn dòng mới — hai luồng sẽ cùng đọc ra một số dư cũ và ghi trùng nhau.
        registrationRepo.khoaHopDong(registration.getId())
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy hợp đồng"));

        int soDuTruoc = ledgerRepo.layButToanCuoi(registration.getId())
                .map(SessionCreditEntry::getBalanceAfter)
                .orElse(0);

        int soDuSau = soDuTruoc + delta;

        // BẤT BIẾN 2: số dư không bao giờ âm.
        // Kiểm ở đây để có thông báo dễ hiểu; cơ sở dữ liệu vẫn chặn lần nữa.
        if (soDuSau < 0) {
            throw ApiException.conflict("INSUFFICIENT_CREDIT",
                    "Không đủ số buổi. Hiện còn " + soDuTruoc + " buổi, thao tác này cần "
                            + Math.abs(delta) + " buổi");
        }

        SessionCreditEntry entry = new SessionCreditEntry();
        entry.setRegistration(registration);
        entry.setEntryType(entryType);
        entry.setDelta(delta);
        entry.setBalanceAfter(soDuSau);      // BẤT BIẾN 3: luôn bằng số dư trước + delta
        entry.setSourceType(sourceType);
        entry.setSourceId(sourceId);
        entry.setReason(reason);
        entry.setCreatedBy(createdBy);

        entry = ledgerRepo.save(entry);

        log.info("Sổ cái {}: {} {}{} → còn {} buổi",
                registration.getRegistrationCode(), entryType,
                delta > 0 ? "+" : "", delta, soDuSau);

        return entry;
    }
}
