package com.sudarshanchakra.jwt;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * Import this configuration from each resource server (device, alert, siren).
 */
@Configuration
@Import(TenantHibernateWebConfiguration.class)
public class JwtResourceServerConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public JwtTokenParser jwtTokenParser(@Value("${jwt.secret}") String secret) {
        return new JwtTokenParser(secret);
    }

    @Bean
    @ConditionalOnMissingBean
    public ResourceServerJwtAuthFilter resourceServerJwtAuthFilter(JwtTokenParser parser) {
        return new ResourceServerJwtAuthFilter(parser);
    }
}
