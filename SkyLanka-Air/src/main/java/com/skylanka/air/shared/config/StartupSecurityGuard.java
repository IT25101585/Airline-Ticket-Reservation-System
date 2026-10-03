package com.skylanka.air.shared.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.Arrays;

@Component
public class StartupSecurityGuard implements ApplicationRunner {
    private static final String DEFAULT_DB_PASSWORD = "123";
    private static final String DEFAULT_JWT_SECRET = "skylanka-development-secret-change-me";

    private final Environment environment;

    public StartupSecurityGuard(Environment environment) {
        this.environment = environment;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (isLocalOrDevProfile()) {
            return;
        }

        String dbPassword = environment.getProperty("spring.datasource.password", "");
        String jwtSecret = environment.getProperty("security.jwt.secret", "");

        if (dbPassword.isBlank() || DEFAULT_DB_PASSWORD.equals(dbPassword)) {
            throw new IllegalStateException(
                    "A non-default DB_PASSWORD is required outside local/dev profiles."
            );
        }
        if (jwtSecret.isBlank() || DEFAULT_JWT_SECRET.equals(jwtSecret)) {
            throw new IllegalStateException(
                    "A non-default JWT_SECRET is required outside local/dev profiles."
            );
        }
    }

    private boolean isLocalOrDevProfile() {
        String[] profiles = environment.getActiveProfiles().length == 0
                ? environment.getDefaultProfiles()
                : environment.getActiveProfiles();
        return Arrays.stream(profiles)
                .anyMatch(profile ->
                        profile.equalsIgnoreCase("local")
                                || profile.equalsIgnoreCase("dev")
                                || profile.equalsIgnoreCase("development"));
    }
}