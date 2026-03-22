package com.sudarshanchakra.jwt;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/**
 * Registers Hibernate tenant filter activation when JPA is present (device / alert / siren services).
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnClass(EntityManager.class)
public class TenantHibernateWebConfiguration {

    @Bean
    @ConditionalOnBean(EntityManagerFactory.class)
    @ConditionalOnProperty(name = "tenant.filter.enabled", havingValue = "true", matchIfMissing = true)
    public FilterRegistrationBean<TenantHibernateFilterActivationFilter> tenantHibernateFilterActivationFilter(
            EntityManager entityManager) {
        TenantHibernateFilterActivationFilter filter = new TenantHibernateFilterActivationFilter(entityManager);
        FilterRegistrationBean<TenantHibernateFilterActivationFilter> reg = new FilterRegistrationBean<>(filter);
        reg.setOrder(Ordered.LOWEST_PRECEDENCE);
        return reg;
    }
}
