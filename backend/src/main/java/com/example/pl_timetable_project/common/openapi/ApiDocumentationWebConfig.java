package com.example.pl_timetable_project.common.openapi;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** API 전용 호스트의 루트 요청을 사람이 읽기 좋은 Scalar 문서로 연결합니다. */
@Configuration
@ConditionalOnProperty(name = "scalar.enabled", havingValue = "true", matchIfMissing = true)
public class ApiDocumentationWebConfig implements WebMvcConfigurer {

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        registry.addRedirectViewController("/", "/scalar");
    }
}
