package com.skylanka.air.shared.config;

import org.springframework.context.annotation.*;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import com.skylanka.air.shared.security.JwtAuthenticationFilter;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {
    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(c -> c.ignoringRequestMatchers(
                        PathPatternRequestMatcher.pathPattern("/api/**")
                ))
                .authorizeHttpRequests(a -> a
                        .requestMatchers("/", "/login", "/register", "/search", "/css/**", "/js/**", "/images/**",
                                "/api/auth/**", "/error", "/favicon.ico",
                                "/forgot-password", "/reset-password").permitAll()
                        // Guest checkout: seat selection and booking creation are open to anonymous visitors.
                        .requestMatchers(HttpMethod.GET, "/book/*", "/flights/*/seat-status").permitAll()
                        .requestMatchers(HttpMethod.POST, "/book/*", "/seats/*/hold", "/seats/*/release").permitAll()
                        .anyRequest().authenticated()
                )
                .exceptionHandling(e -> e.authenticationEntryPoint(new LoginRedirectEntryPoint()))
                .formLogin(f -> f.disable())
                .logout(l -> l.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }
}
