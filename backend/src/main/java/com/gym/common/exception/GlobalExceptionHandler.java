package com.gym.common.exception;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/** Chuyển mọi lỗi thành một định dạng JSON thống nhất cho web và mobile. */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<Map<String, Object>> handleApi(ApiException ex, HttpServletRequest req) {
        return ResponseEntity.status(ex.getStatus()).body(body(ex.getCode(), ex.getMessage(), req));
    }

    /** Lỗi kiểm tra dữ liệu đầu vào — trả về từng trường sai để giao diện tô đỏ đúng ô. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(
            MethodArgumentNotValidException ex, HttpServletRequest req) {

        Map<String, String> fields = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(f -> fields.putIfAbsent(f.getField(), f.getDefaultMessage()));

        Map<String, Object> body = body("VALIDATION_FAILED", "Dữ liệu không hợp lệ", req);
        body.put("fields", fields);
        return ResponseEntity.badRequest().body(body);
    }

    /**
     * Không đủ quyền gọi endpoint này.
     *
     * <p>PHẢI khai báo riêng: nếu để bộ bắt lỗi tổng quát bên dưới xử lý, mọi lỗi
     * phân quyền sẽ biến thành 500 và giao diện không biết đó là lỗi quyền hạn.
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, Object>> handleAccessDenied(
            AccessDeniedException ex, HttpServletRequest req) {

        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(body("ACCESS_DENIED", "Bạn không có quyền thực hiện thao tác này", req));
    }

    /**
     * Vi phạm ràng buộc ở tầng cơ sở dữ liệu.
     *
     * <p>Đây là lớp phòng thủ cuối cùng — nếu tới được đây nghĩa là tầng nghiệp vụ
     * đã bỏ sót một phép kiểm tra, nên ghi log ở mức cảnh báo để còn phát hiện.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, Object>> handleDataIntegrity(
            DataIntegrityViolationException ex, HttpServletRequest req) {

        log.warn("Ràng buộc CSDL chặn tại {}: {}", req.getRequestURI(), rootMessage(ex));
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(body("CONSTRAINT_VIOLATION", "Dữ liệu vi phạm ràng buộc của hệ thống", req));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleOther(Exception ex, HttpServletRequest req) {
        log.error("Lỗi không lường trước tại {}", req.getRequestURI(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(body("INTERNAL_ERROR", "Có lỗi xảy ra, vui lòng thử lại", req));
    }

    private String rootMessage(Throwable ex) {
        Throwable root = ex;
        while (root.getCause() != null && root.getCause() != root) root = root.getCause();
        return root.getMessage();
    }

    private Map<String, Object> body(String code, String message, HttpServletRequest req) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("timestamp", OffsetDateTime.now());
        m.put("code", code);
        m.put("message", message);
        m.put("path", req.getRequestURI());
        return m;
    }
}
