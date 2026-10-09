package com.zhiwei.superemployee.app.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Bean;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web MVC 与 CORS 配置。
 * <p>
 * 根据 {@code super-employee.web.allowed-origin-patterns} 配置跨域允许来源，
 * 同时通过 {@link WebMvcConfigurer} 与 {@link CorsConfigurationSource} 两套机制生效，
 * 以兼容 MVC 映射与 Spring Security CORS 过滤。
 * </p>
 * @author yangdb
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    /** 允许的跨域 Origin 模式，逗号分隔。 */
    private final String allowedOriginPatterns;

    /**
     * 构造 Web 配置并注入允许的 Origin 模式。
     *
     * @param allowedOriginPatterns 逗号分隔的 Origin 模式，默认 {@code http://localhost:5173}
     */
    public WebConfig(@Value("${super-employee.web.allowed-origin-patterns:http://localhost:5173}")
                     String allowedOriginPatterns) {
        this.allowedOriginPatterns = allowedOriginPatterns;
    }

    /**
     * 为全部路径注册 CORS 映射规则。
     *
     * @param registry CORS 注册表
     */
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOriginPatterns(allowedOriginPatterns.split(","))
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*");
    }

    /**
     * 提供供 Spring Security 等组件使用的 CORS 配置源。
     *
     * @return 应用于 {@code /**} 的 CORS 配置源
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        for (String pattern : allowedOriginPatterns.split(",")) {
            configuration.addAllowedOriginPattern(pattern.trim());
        }
        configuration.addAllowedHeader("*");
        configuration.setAllowedMethods(java.util.Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
