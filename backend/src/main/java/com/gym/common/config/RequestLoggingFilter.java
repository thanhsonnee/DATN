package com.gym.common.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * In ra một dòng cho MỖI request tới API — kể cả những API không có
 * {@code log.info(...)} viết tay trong service.
 *
 * <p>Không có filter này thì gọi API xong terminal im lặng hoàn toàn, trừ khi
 * request lỗi (bị {@code GlobalExceptionHandler} in ra) hoặc service đó có
 * chủ động ghi log sự kiện nghiệp vụ.
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestLoggingFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain chain) throws ServletException, IOException {

        long batDau = System.currentTimeMillis();
        try {
            chain.doFilter(request, response);
        } finally {
            long thoiGianMs = System.currentTimeMillis() - batDau;
            String query = request.getQueryString();
            String duongDan = query == null
                    ? request.getRequestURI()
                    : request.getRequestURI() + "?" + query;
            log.info("--> {} {} {} {}ms",
                    request.getMethod(), duongDan, response.getStatus(), thoiGianMs);
        }
    }
}
