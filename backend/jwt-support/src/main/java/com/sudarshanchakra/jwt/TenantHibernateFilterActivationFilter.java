package com.sudarshanchakra.jwt;

import jakarta.persistence.EntityManager;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.hibernate.Filter;
import org.hibernate.Session;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Enables Hibernate {@code tenantFilter} for the request-scoped {@link EntityManager} when
 * {@link TenantContext} carries a farm id and the user is not {@code super_admin}.
 * Requires {@code spring.jpa.open-in-view=true} so the session spans the request.
 */
public class TenantHibernateFilterActivationFilter extends OncePerRequestFilter {

    private final EntityManager entityManager;

    public TenantHibernateFilterActivationFilter(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (TenantContext.isSuperAdmin()) {
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
}
