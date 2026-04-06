package iuh.fit.se.minizalobackend.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import iuh.fit.se.minizalobackend.models.RefreshToken;
import iuh.fit.se.minizalobackend.repository.RefreshTokenRepository;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;

public class JwtAuthenticationFilter extends OncePerRequestFilter {

    @Autowired
    private JwtTokenProvider tokenProvider;

    @Autowired
    private iuh.fit.se.minizalobackend.services.impl.UserDetailsServiceImpl userDetailsService;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    private static final Logger logger = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        try {
            String jwt = getJwtFromRequest(request);

            if (StringUtils.hasText(jwt) && tokenProvider.validateToken(jwt)) {
                String userId = tokenProvider.getUserIdFromAccessToken(jwt);
                String sessionToken = tokenProvider.getSessionTokenFromAccessToken(jwt);
                String deviceType = tokenProvider.getDeviceTypeFromAccessToken(jwt);

                // If token is bound to a session (st), verify that session still exists and is not expired.
                // This enables immediate logout when a new login revokes the old session row.
                if (sessionToken != null && !sessionToken.isBlank()) {
                    RefreshToken rt = refreshTokenRepository.findByToken(sessionToken).orElse(null);
                    if (rt == null) {
                        filterChain.doFilter(request, response);
                        return;
                    }
                    if (rt.getExpiryDate() != null && rt.getExpiryDate().isBefore(Instant.now())) {
                        refreshTokenRepository.delete(rt);
                        filterChain.doFilter(request, response);
                        return;
                    }
                    if (rt.getUser() == null || rt.getUser().getId() == null || !rt.getUser().getId().toString().equals(userId)) {
                        filterChain.doFilter(request, response);
                        return;
                    }
                    if (deviceType != null && rt.getDeviceType() != null && !rt.getDeviceType().equalsIgnoreCase(deviceType)) {
                        filterChain.doFilter(request, response);
                        return;
                    }
                }

                UserDetails userDetails = userDetailsService.loadUserById(userId);
                UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                        userDetails, null, userDetails.getAuthorities());
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        } catch (Exception ex) {
            logger.error("Cannot set user authentication: {}", ex.getMessage());
        }

        filterChain.doFilter(request, response);
    }

    private String getJwtFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }
}
