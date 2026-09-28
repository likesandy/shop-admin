package com.acme.admin.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

/** Optional scrape credential, confined to the metrics endpoint and never a business account. */
@Configuration
@ConditionalOnProperty(name = "app.metrics-enabled", havingValue = "true")
public class MetricsSecurityConfig {
    @Bean
    @Order(1)
    SecurityFilterChain metricsSecurity(HttpSecurity http, PasswordEncoder encoder,
            @Value("${app.metrics-password}") String password) throws Exception {
        if (password.length() < 32) throw new IllegalArgumentException("Metrics password must contain at least 32 characters");
        var users = new InMemoryUserDetailsManager(User.withUsername("metrics")
                .password(encoder.encode(password)).roles("METRICS").build());
        var provider = new DaoAuthenticationProvider(users);
        provider.setPasswordEncoder(encoder);
        return http.securityMatcher("/actuator/prometheus")
                .authenticationManager(new ProviderManager(provider))
                .sessionManagement(x -> x.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .requestCache(x -> x.disable())
                .csrf(x -> x.disable())
                .authorizeHttpRequests(x -> x.anyRequest().hasRole("METRICS"))
                .httpBasic(Customizer.withDefaults()).build();
    }
}
