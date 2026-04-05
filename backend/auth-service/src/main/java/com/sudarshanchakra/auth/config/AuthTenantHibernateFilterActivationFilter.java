package com.sudarshanchakra.auth.config;

import com.sudarshanchakra.auth.context.TenantContext;
import com.sudarshanchakra.auth.model.Role;
import jakarta.persistence.EntityManager;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.hibernate.Filter;
import org.hibernate.Session;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Enables Hibernate {@code tenantFilter} on the request EntityManager when {@link TenantContext}
 * has a farm id and the caller is not {@link Role#SUPER_ADMIN}. Runs after Spring Security
 * (low servlet filter order). Requires {@code spring.jpa.open-in-view=true} so the session
 * spans the request, matching jwt-support resource servers.
 */
public class AuthTenantHibernateFilterActivationFilter extends OncePerRequestFilter {

    private final EntityManager entityManager;

    public AuthTenantHibernateFilterActivationFilter(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (isSuperAdmin()) {
            filterChain.doFilter(request, response);
            return;
        }
        UUID farmId = TenantContext.getFarmId();
        if (farmId == null) {
            filterChain.doFilter(request, response);
            return;
        }
        Session session = entityManager.unwrap(Session.class);
        Filter hf = session.enableFilter("tenantFilter");
        hf.setParameter("farmId", farmId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            session.disableFilter("tenantFilter");
        }
    }

    private static boolean isSuperAdmin() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) {
            return false;
        }
        for (GrantedAuthority a : auth.getAuthorities()) {
            if ("ROLE_SUPER_ADMIN".equals(a.getAuthority())) {
                return true;
            }
        }
        return false;
    }
}
