package com.skylanka.air.shared.config;

import com.skylanka.air.shared.entity.AuditLog;
import com.skylanka.air.shared.repository.AuditLogRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

@Configuration
public class AuditWebConfig implements WebMvcConfigurer {
    private final AuditLogRepository audits;

    public AuditWebConfig(AuditLogRepository audits) {
        this.audits = audits;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new HandlerInterceptor() {
            @Override
            public void afterCompletion(
                    HttpServletRequest request,
                    HttpServletResponse response,
                    Object handler,
                    Exception exception) {
                AuditLog log = new AuditLog();
                Object userId = request.getSession(false) == null
                        ? null : request.getSession(false).getAttribute("userId");
                if (userId != null) {
                    try {
                        log.setUserId(Long.valueOf(String.valueOf(userId)));
                    } catch (NumberFormatException ignored) {
                    }
                }
                if (log.getUserId() == null) {
                    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
                    if (authentication != null) {
                        try {
                            log.setUserId(Long.valueOf(authentication.getName()));
                        } catch (NumberFormatException ignored) {
                        }
                    }
                }
                log.setAction("HTTP_REQUEST");
                log.setTarget(request.getMethod() + " " + request.getRequestURI());
                log.setDetails("status=" + response.getStatus()
                        + "; ip=" + request.getRemoteAddr()
                        + (exception == null ? "" : "; error=" + exception.getClass().getSimpleName()));
                audits.save(log);
            }
        });
    }
}