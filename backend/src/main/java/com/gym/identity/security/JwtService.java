package com.gym.identity.security;

import com.gym.common.config.AppProperties;
import com.gym.common.exception.ApiException;
import com.gym.identity.domain.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

/** Sinh và kiểm tra JWT. Tự cài đặt, không dùng thư viện đóng gói sẵn. */
@Service
public class JwtService {

    private static final String CLAIM_TYPE = "type";
    private static final String TYPE_REFRESH = "refresh";

    private final SecretKey key;
    private final AppProperties props;

    public JwtService(AppProperties props) {
        this.props = props;
        this.key = Keys.hmacShaKeyFor(props.jwt().secret().getBytes(StandardCharsets.UTF_8));
    }

    public String issueAccessToken(User user) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(String.valueOf(user.getId()))
                .claim("username", user.getUsername())
                .claim("role", user.getPrimaryRole().name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(props.jwt().accessTokenTtl())))
                .signWith(key)
                .compact();
    }

    /**
     * Token gọn hơn access token — chỉ mang định danh, không mang vai trò, vì
     * vai trò có thể đổi trong 30 ngày hiệu lực và sẽ được nạp lại mới ở mỗi lần
     * gọi /refresh (đọc thẳng từ CSDL, không tin vào claim cũ trong token).
     */
    public String issueRefreshToken(User user) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(String.valueOf(user.getId()))
                .claim(CLAIM_TYPE, TYPE_REFRESH)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(props.jwt().refreshTokenTtl())))
                .signWith(key)
                .compact();
    }

    /** Trả về nội dung token nếu hợp lệ, ném ngoại lệ nếu chữ ký sai hoặc đã hết hạn. */
    public Claims parse(String token) {
        return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
    }

    /** Như {@link #parse}, nhưng còn từ chối token không phải loại refresh (vd. access token bị đem đi refresh). */
    public Claims parseRefreshToken(String token) {
        Claims claims;
        try {
            claims = parse(token);
        } catch (JwtException | IllegalArgumentException e) {
            throw ApiException.unauthorized("INVALID_REFRESH_TOKEN", "Refresh token không hợp lệ hoặc đã hết hạn");
        }
        if (!TYPE_REFRESH.equals(claims.get(CLAIM_TYPE, String.class))) {
            throw ApiException.unauthorized("INVALID_REFRESH_TOKEN", "Refresh token không hợp lệ hoặc đã hết hạn");
        }
        return claims;
    }

    public long accessTokenSeconds() {
        return props.jwt().accessTokenTtl().toSeconds();
    }
}
