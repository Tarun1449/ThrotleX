package com.throttlex.urlshortener.ratelimit;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
@RequiredArgsConstructor
public class RateLimitInterceptor implements HandlerInterceptor {

    private final RateLimitService rateLimitService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        
        // We only want to rate limit GET requests (URL Redirection)
        // POST requests (Creation) will remain un-throttled as per your request
        if (!HttpMethod.GET.name().equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        String clientIp = request.getRemoteAddr();

        if (!rateLimitService.isAllowed(clientIp)) {
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.getWriter().write("Rate limit exceeded. Please try again later.");
            return false; // Stop the request chain, do not reach the controller
        }

        return true; // Allowed, continue to the controller
    }
}
