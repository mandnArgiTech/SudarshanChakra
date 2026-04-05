package com.sudarshanchakra.jwt;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Sets Spring Security context + {@link TenantContext} from a valid Bearer JWT.
 */
@Slf4j
@RequiredArgsConstructor
public class ResourceServerJwtAuthFilter extends OncePerRequestFilter {

    private final JwtTokenParser parser;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        try {
            String header = request.getHeader("Authorization");
            if (header != null && header.startsWith("Bearer ")) {
                String token = header.substring(7);
                try {
                    if (parser.validate(token)) {
                        JwtTokenParser.ParsedJwt pj = parser.parse(token);
                        List<SimpleGrantedAuthority> authorities = new ArrayList<>();
                        String roleNorm = pj.role().toUpperCase().replace('-', '_');
                        authorities.add(new SimpleGrantedAuthority("ROLE_" + roleNorm));
                        for (String perm : pj.permissions()) {
                            authorities.add(new SimpleGrantedAuthority("PERMISSION_" + perm));
                        }
                        UsernamePasswordAuthenticationToken auth =
                                new UsernamePasswordAuthenticationToken(pj.username(), null, authorities);
                        auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                        SecurityContextHolder.getContext().setAuthentication(auth);
                        TenantContext.set(pj.farmId(), pj.isSuperAdmin(), pj.userId());
                    }
                } catch (Exception e) {
                    log.warn("JWT processing failed: {}", e.getMessage());
                }
            }
            filterChain.doFilter(request, response);
        } finally {
            TenantContext.clear();
        }
    }
}
