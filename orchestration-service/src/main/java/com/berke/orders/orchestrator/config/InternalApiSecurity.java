package com.berke.orders.orchestrator.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Configuration
@RequiredArgsConstructor
public class InternalApiSecurity implements WebMvcConfigurer {
    private final IntegrationProperties integrations;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new ApiKeyInterceptor()).addPathPatterns("/api/orchestrator/**");
    }

    private final class ApiKeyInterceptor implements HandlerInterceptor {
        @Override
        public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
            String supplied = request.getHeader("X-Internal-Api-Key");
            if (supplied == null || !MessageDigest.isEqual(
                    supplied.getBytes(StandardCharsets.UTF_8),
                    integrations.getInternalApiKey().getBytes(StandardCharsets.UTF_8))) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid internal API key");
            }
            return true;
        }
    }
}
