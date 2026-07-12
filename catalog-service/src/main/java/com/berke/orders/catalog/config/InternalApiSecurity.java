package com.berke.orders.catalog.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Configuration
public class InternalApiSecurity implements WebMvcConfigurer {
    private final byte[] expected;

    public InternalApiSecurity(@Value("${integrations.internal-api-key}") String expected) {
        this.expected = expected.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new HandlerInterceptor() {
            @Override
            public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
                String supplied = request.getHeader("X-Internal-Api-Key");
                if (supplied == null || !MessageDigest.isEqual(supplied.getBytes(StandardCharsets.UTF_8), expected)) {
                    throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid internal API key");
                }
                return true;
            }
        }).addPathPatterns("/api/catalog/**");
    }
}
