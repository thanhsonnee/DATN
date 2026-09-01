package com.gym.common.util;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

/**
 * Sinh mã nghiệp vụ hiển thị ra ngoài: mã hội viên, mã hợp đồng.
 *
 * <p>Dùng SEQUENCE của PostgreSQL chứ không đếm số dòng hiện có. Cách đếm dòng
 * sẽ sinh mã TRÙNG khi hai người mua gói cùng lúc, còn sequence bảo đảm mỗi lần
 * gọi trả về một số khác nhau kể cả khi chạy song song.
 */
@Component
@RequiredArgsConstructor
public class CodeGenerator {

    private final EntityManager em;

    /** Ví dụ: {@code MB-000123} — mã in trên thẻ hội viên. */
    @Transactional
    public String nextMemberCode() {
        return String.format("MB-%06d", nextVal("member_code_seq"));
    }

    /** Ví dụ: {@code REG-2026-000451} — mã in trên hợp đồng. */
    @Transactional
    public String nextRegistrationCode() {
        return String.format("REG-%d-%06d", LocalDate.now().getYear(), nextVal("registration_code_seq"));
    }

    /** Ví dụ: {@code INV-2026-000451} — mã in trên hóa đơn đưa khách. */
    @Transactional
    public String nextInvoiceNo() {
        return String.format("INV-%d-%06d", LocalDate.now().getYear(), nextVal("invoice_no_seq"));
    }

    /** Ví dụ: {@code PAY-2026-000988} — mã dùng khi đối soát với ngân hàng. */
    @Transactional
    public String nextPaymentNo() {
        return String.format("PAY-%d-%06d", LocalDate.now().getYear(), nextVal("payment_no_seq"));
    }

    /** Ví dụ: {@code EXP-2026-000001} — mã chi phí. */
    @Transactional
    public String nextExpenseNo() {
        return String.format("EXP-%d-%06d", LocalDate.now().getYear(), nextVal("expense_no_seq"));
    }

    /** Ví dụ: {@code PAYROLL-2026-08-001} — mã bảng lương. */
    @Transactional
    public String nextPayrollCode(int year, int month) {
        return String.format("PAYROLL-%d-%02d-%03d", year, month, nextVal("payroll_code_seq"));
    }

    private long nextVal(String sequenceName) {
        return ((Number) em.createNativeQuery("SELECT nextval('" + sequenceName + "')")
                .getSingleResult()).longValue();
    }
}
