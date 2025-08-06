package com.credit.card.fraud.detection.auth.filter;

import com.credit.card.fraud.detection.auth.service.JwtService;
import com.credit.card.fraud.detection.company.repository.CompanyRepository;
import com.credit.card.fraud.detection.company.entity.Company;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final CompanyRepository companyRepository;

    // 필터를 건너뛸 경로들
    private static final List<String> EXCLUDED_PATHS = Arrays.asList(
            "/swagger-ui",
            "/v3/api-docs",
            "/auth/",
            "/actuator",
            "/favicon.ico",
            "/webjars",
            "/error"
    );

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        try {
            // Authorization 헤더에서 토큰 추출
            String token = extractTokenFromRequest(request);

            if (token == null) {
                log.debug("JWT 토큰이 없습니다. 경로: {}", request.getRequestURI());
                filterChain.doFilter(request, response);
                return;
            }

            // 토큰 유효성 검증
            if (!jwtService.isTokenValid(token)) {
                log.warn("유효하지 않은 JWT 토큰입니다. 경로: {}", request.getRequestURI());
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.getWriter().write("{\"error\":\"Invalid or expired token\"}");
                response.setContentType("application/json");
                return;
            }

            // 토큰에서 이메일 추출
            String email = jwtService.extractEmail(token);
            if (email == null) {
                log.warn("JWT 토큰에서 이메일을 추출할 수 없습니다. 경로: {}", request.getRequestURI());
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.getWriter().write("{\"error\":\"Invalid token format\"}");
                response.setContentType("application/json");
                return;
            }

            // SecurityContext에 인증 정보가 없다면 설정
            if (SecurityContextHolder.getContext().getAuthentication() == null) {
                Company company = companyRepository.findByEmail(email).orElse(null);

                if (company != null) {
                    UsernamePasswordAuthenticationToken authToken =
                            new UsernamePasswordAuthenticationToken(
                                    company, null, null  // 권한 처리 필요 시 마지막 인자 수정
                            );

                    authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authToken);

                    log.debug("JWT 인증 성공: {} (경로: {})", email, request.getRequestURI());
                } else {
                    log.warn("존재하지 않는 사용자: {} (경로: {})", email, request.getRequestURI());
                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    response.getWriter().write("{\"error\":\"User not found\"}");
                    response.setContentType("application/json");
                    return;
                }
            }

        } catch (Exception e) {
            log.error("JWT 인증 필터에서 오류 발생 (경로: {})", request.getRequestURI(), e);
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.getWriter().write("{\"error\":\"Authentication error\"}");
            response.setContentType("application/json");
            return;
        }

        filterChain.doFilter(request, response);
    }

    /**
     * 요청에서 JWT 토큰 추출
     */
    private String extractTokenFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");

        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }

        return null;
    }

    /**
     * 필터를 건너뛸 경로인지 확인
     */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return EXCLUDED_PATHS.stream()
                .anyMatch(request.getRequestURI()::startsWith);
    }
}