package com.example.pl_timetable_project.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.auth.google")
public record GoogleAuthProperties(
        boolean enabled,
        String successRedirectUri,
        String failureRedirectUri
) {
}
