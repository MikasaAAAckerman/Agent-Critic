package com.agentcritic.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * WebMvc CORS —— 加了 spring-boot-starter-web 后应用是 Servlet 栈（Tomcat），
 * 必须用 WebMvcConfigurer（WebFluxConfigurer 在 servlet 环境下不会被调用）。
 * 前端（frontend/index.html 独立打开）跨域访问评测 API。
 */
@Slf4j
@Configuration
public class WebMvcCorsConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOriginPatterns("*")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600);
        log.info("[Config] WebMvc CORS 已配置 | /api/**");
    }
}
