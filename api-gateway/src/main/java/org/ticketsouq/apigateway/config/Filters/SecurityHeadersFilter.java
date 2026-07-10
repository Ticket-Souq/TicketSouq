package org.ticketsouq.apigateway.config.Filters;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class SecurityHeadersFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(
            HttpServletRequest req,
            HttpServletResponse res,
            FilterChain chain) throws ServletException, IOException {

        // Prevent MIME-type sniffing (browser won't guess Content-Type)
        res.setHeader("X-Content-Type-Options", "nosniff");

        // Only allow embedding in same-origin frames (clickjacking protection)
        res.setHeader("X-Frame-Options", "SAMEORIGIN");

        // Backward-compatible alias for frame-ancestors in CSP above
        res.setHeader("Content-Security-Policy",
                "default-src 'self'; " +
                "img-src 'self' data: https:; " +
                "script-src 'self' 'unsafe-inline'; " +
                "style-src 'self' 'unsafe-inline'; " +
                "frame-ancestors 'self'; " +
                "frame-src https://www.youtube.com;");

        // Tell the browser how much referrer info to send (don't leak the full URL cross-origin)
        res.setHeader("Referrer-Policy", "strict-origin-when-cross-origin");

        // Force HTTPS for a year, including all subdomains
        res.setHeader("Strict-Transport-Security", "max-age=31536000; includeSubDomains");

        // Lock down which browser APIs this page can access
        res.setHeader("Permissions-Policy", "camera=(), microphone=(), geolocation=()");

        // Prevent cross-origin popups from accessing this window's context
        res.setHeader("Cross-Origin-Opener-Policy", "same-origin");

        // Stop other origins from embedding our resources (fonts, scripts, etc.)
        res.setHeader("Cross-Origin-Resource-Policy", "same-origin");

        chain.doFilter(req, res);
    }
}
