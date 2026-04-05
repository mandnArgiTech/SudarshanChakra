package com.sudarshanchakra.auth.config;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

@Configuration(proxyBeanMethods = false)
public class AuthTenantHibernateWebConfiguration {

    @Bean
    @ConditionalOnBean(EntityManagerFactory.class)
    @ConditionalOnProperty(name = "tenant.filter.enabled", havingValue = "true", matchIfMissing = true)
    public FilterRegistrationBean<AuthTenantHibernateFilterActivationFilter> authTenantHibernateFilterActivationFilter(
            EntityManager entityManager) {
        AuthTenantHibernateFilterActivationFilter filter = new AuthTenantHibernateFilterActivationFilter(entityManager);
        FilterRegistrationBean<AuthTenantHibernateFilterActivationFilter> reg =
                new FilterRegistrationBean<>(filter);
        reg.setOrder(Ordered.LOWEST_PRECEDENCE);
        return reg;
    }
}
