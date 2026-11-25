package com.veritaspay.common;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;

/**
 * Redis-backed fixed-window API rate limiter per authenticated principal or IP.
 * Fails open if Redis is temporarily unavailable to preserve payment availability.
 */
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);

    private final StringRedisTemplate redisTemplate;
    private final int requestsPerMinute;

    public RateLimitFilter(
            StringRedisTemplate redisTemplate,
            @Value("${app.rate-limit.requests-per-minute:120}") int requestsPerMinute) {
        this.redisTemplate = redisTemplate;
        this.requestsPerMinute = requestsPerMinute;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.startsWith("/actuator") || path.startsWith("/v3/api-docs") || path.startsWith("/swagger-ui");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        String subject = resolveSubject(request);
        long windowMinute = Instant.now().getEpochSecond() / 60;
        String key = "rl:" + subject + ":" + windowMinute;

        try {
            Long count = redisTemplate.opsForValue().increment(key);
            if (count != null && count == 1L) {
                redisTemplate.expire(key, Duration.ofSeconds(70));
            }
            if (count != null) {
                long remaining = Math.max(0, requestsPerMinute - count);
                response.setHeader("X-RateLimit-Remaining", String.valueOf(remaining));
                if (count > requestsPerMinute) {
                    response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
                    response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
                    response.getWriter().write(
                            "{\"title\":\"RATE_LIMIT_EXCEEDED\",\"status\":429,\"detail\":\"Too many requests. Retry next minute.\"}");
                    return;
                }
            }
        } catch (Exception ex) {
            log.warn("Rate limiter skipped due to Redis unavailability: {}", ex.getMessage());
        }

        filterChain.doFilter(request, response);
    }

    private String resolveSubject(HttpServletRequest request) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getPrincipal())) {
            return auth.getName();
        }
        return request.getRemoteAddr();
    }
}
