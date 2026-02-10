package iuh.fit.se.minizalobackend.controllers;

import iuh.fit.se.minizalobackend.dtos.request.ChangePasswordRequest;
import iuh.fit.se.minizalobackend.exception.TokenRefreshException;
import iuh.fit.se.minizalobackend.models.RefreshToken;
import iuh.fit.se.minizalobackend.payload.request.LoginRequest;
import iuh.fit.se.minizalobackend.payload.request.SignupRequest;
import iuh.fit.se.minizalobackend.payload.request.TokenRefreshRequest;
import iuh.fit.se.minizalobackend.payload.response.JwtResponse;
import iuh.fit.se.minizalobackend.payload.response.MessageResponse;
import iuh.fit.se.minizalobackend.payload.response.TokenRefreshResponse;
import iuh.fit.se.minizalobackend.security.JwtTokenProvider;
import iuh.fit.se.minizalobackend.services.RefreshTokenService;
import iuh.fit.se.minizalobackend.security.services.UserDetailsImpl;
import iuh.fit.se.minizalobackend.services.UserService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenService refreshTokenService;
    private final UserService userService;

    public AuthController(AuthenticationManager authenticationManager, JwtTokenProvider jwtTokenProvider,
            RefreshTokenService refreshTokenService, UserService userService) {
        this.authenticationManager = authenticationManager;
        this.jwtTokenProvider = jwtTokenProvider;
        this.refreshTokenService = refreshTokenService;
        this.userService = userService;
    }

    @PostMapping("/signin")
    public ResponseEntity<?> authenticateUser(@Valid @RequestBody LoginRequest loginRequest) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(loginRequest.getUsername(), loginRequest.getPassword()));

        SecurityContextHolder.getContext().setAuthentication(authentication);
        UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();

        // Update online status
        userService.updateOnlineStatus(userDetails.getId(), true);

        String jwt = jwtTokenProvider.generateAccessToken(userDetails.getId().toString());
        RefreshToken refreshToken = refreshTokenService.createRefreshToken(userDetails.getId().toString());

        return ResponseEntity.ok(new JwtResponse(jwt, refreshToken.getToken()));
    }

    @PostMapping("/signup")
    public ResponseEntity<?> registerUser(@Valid @RequestBody SignupRequest signupRequest) {
        userService.registerNewUser(signupRequest);
        return ResponseEntity.ok(new MessageResponse("User registered successfully!"));
    }

    @PostMapping("/refreshtoken")
    public ResponseEntity<?> refreshtoken(@Valid @RequestBody TokenRefreshRequest request) {
        String requestRefreshToken = request.getRefreshToken();

        return refreshTokenService.findByToken(requestRefreshToken)
                .map(refreshTokenService::verifyExpiration)
                .map(refreshTokenService::rotateRefreshToken)
                .map(newRefreshToken -> {
                    String accessToken = jwtTokenProvider
                            .generateAccessToken(newRefreshToken.getUser().getId().toString());
                    return ResponseEntity.ok(new TokenRefreshResponse(accessToken, newRefreshToken.getToken()));
                })
                .orElseThrow(() -> new TokenRefreshException(requestRefreshToken,
                        "Refresh token is not in database!"));
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logoutUser() {
        UserDetailsImpl userDetails = (UserDetailsImpl) SecurityContextHolder.getContext().getAuthentication()
                .getPrincipal();
        refreshTokenService.deleteByUserId(userDetails.getId().toString());
        return ResponseEntity.ok(new MessageResponse("Log out successful!"));
    }

    @PostMapping("/change-password")
    public ResponseEntity<?> changePassword(
            @Valid @RequestBody ChangePasswordRequest request,
            @AuthenticationPrincipal UserDetailsImpl userDetails) {
        userService.changePassword(userDetails.getId(), request);
        return ResponseEntity.ok(new MessageResponse("Password changed successfully!"));
    }
}
