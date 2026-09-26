package com.gym.checkin.repository;

import com.gym.checkin.domain.CheckIn;
import com.gym.checkin.domain.CheckInResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface CheckInRepository extends JpaRepository<CheckIn, Long> {

    List<CheckIn> findByMemberIdOrderByCheckedInAtDesc(Long memberId);

    /**
     * Lượt ĐANG THỰC SỰ MỞ của hội viên (đã được CHO VÀO — ALLOWED/ALLOWED_OVERRIDE —
     * và chưa quét ra) — dùng để phát hiện nghi vấn dùng chung thẻ.
     *
     * <p>Cố ý lọc theo {@code result}: lượt bị TỪ CHỐI (DENIED_*) cũng có
     * {@code checkedOutAt = null} (đơn giản vì cột đó không áp dụng cho lượt không được
     * vào), nhưng KHÔNG có nghĩa là hội viên "đang ở trong phòng". Nếu không lọc, lượt bị
     * từ chối gần nhất sẽ bị hiểu nhầm thành "đang mở", khiến lượt hợp lệ ngay sau đó (vd.
     * vừa nộp tiền xong quét lại) bị chặn oan thành DENIED_ALREADY_INSIDE.
     */
    @Query("SELECT c FROM CheckIn c WHERE c.member.id = :memberId AND c.checkedOutAt IS NULL "
         + "  AND c.result IN (com.gym.checkin.domain.CheckInResult.ALLOWED, "
         + "                   com.gym.checkin.domain.CheckInResult.ALLOWED_OVERRIDE) "
         + "ORDER BY c.checkedInAt DESC")
    List<CheckIn> timCacLuotDangMoThucSu(@Param("memberId") Long memberId);

    /** Lượt gần nhất, kể cả đã ra — dùng để phát hiện quét lại quá nhanh. */
    Optional<CheckIn> findFirstByMemberIdOrderByCheckedInAtDesc(Long memberId);

    /** Ai đang ở trong phòng tập ngay lúc này. */
    @Query("SELECT c FROM CheckIn c "
         + "WHERE c.checkedOutAt IS NULL "
         + "  AND c.result IN (com.gym.checkin.domain.CheckInResult.ALLOWED, "
         + "                   com.gym.checkin.domain.CheckInResult.ALLOWED_OVERRIDE) "
         + "ORDER BY c.checkedInAt DESC")
    List<CheckIn> dangTrongPhongTap();

    /** Lượt quên quét lúc về, để job đêm tự đóng. */
    @Query("SELECT c FROM CheckIn c "
         + "WHERE c.checkedOutAt IS NULL AND c.checkedInAt < :truoc "
         + "  AND c.result IN (com.gym.checkin.domain.CheckInResult.ALLOWED, "
         + "                   com.gym.checkin.domain.CheckInResult.ALLOWED_OVERRIDE)")
    List<CheckIn> timLuotQuenCheckOut(@Param("truoc") OffsetDateTime truoc);

    /** Thống kê hiệu quả chống thất thoát: đếm lượt bị chặn theo từng lý do. */
    @Query("SELECT c.result, COUNT(c) FROM CheckIn c "
         + "WHERE c.checkedInAt >= :tuNgay GROUP BY c.result")
    List<Object[]> thongKeTheoKetQua(@Param("tuNgay") OffsetDateTime tuNgay);

    long countByResultAndCheckedInAtAfter(CheckInResult result, OffsetDateTime tuNgay);

    /** Tổng lượt quét (cho vào lẫn bị chặn) từ một mốc thời gian — dùng cho dashboard Admin. */
    long countByCheckedInAtAfter(OffsetDateTime tuNgay);

    /** Lượt bị chặn (mọi lý do DENIED_*) từ một mốc thời gian. */
    @Query("SELECT COUNT(c) FROM CheckIn c WHERE c.checkedInAt >= :tuNgay "
         + "  AND c.result NOT IN (com.gym.checkin.domain.CheckInResult.ALLOWED, "
         + "                       com.gym.checkin.domain.CheckInResult.ALLOWED_OVERRIDE)")
    long countBiTuChoiAfter(@Param("tuNgay") OffsetDateTime tuNgay);
}
