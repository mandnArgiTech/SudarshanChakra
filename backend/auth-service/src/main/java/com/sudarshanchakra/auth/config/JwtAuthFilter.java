package com.sudarshanchakra.auth.config;

import com.sudarshanchakra.auth.context.TenantContext;
import com.sudarshanchakra.auth.model.User;
import com.sudarshanchakra.auth.repository.UserRepository;
import com.sudarshanchakra.auth.service.JwtService;
import com.sudarshanchakra.auth.service.ModuleResolutionService;
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
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserRepository userRepository;
    private final ModuleResolutionService moduleResolutionService;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String authHeader = request.getHeader("Authorization");

        try {
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                String token = authHeader.substring(7);
                try {
                    if (jwtService.validateToken(token)) {
                        String username = jwtService.extractUsername(token);

                        if (SecurityContextHolder.getContext().getAuthentication() == null) {
                            User user = userRepository.findByUsername(username).orElse(null);
                            if (user != null && Boolean.TRUE.equals(user.getActive())) {
                                List<SimpleGrantedAuthority> authorities = List.of(
                                        new SimpleGrantedAuthority("ROLE_" + user.getRole().name())
                                );

                                UsernamePasswordAuthenticationToken authToken =
                                        new UsernamePasswordAuthenticationToken(username, null, authorities);
                                authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                                SecurityContextHolder.getContext().setAuthentication(authToken);

                                TenantContext.set(
                                        user.getFarmId(),
                                        user.getId(),
                                        moduleResolutionService.resolveModules(user)
                                );
                            }
                        }
                    }
                } catch (Exception e) {
                    log.error("JWT authentication failed: {}", e.getMessage());
                }
            }

            filterChain.doFilter(request, response);
        } finally {
            TenantContext.clear();
        }
    }
}
