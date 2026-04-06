package iuh.fit.se.minizalobackend.controllers;

import iuh.fit.se.minizalobackend.dtos.request.ChangePasswordRequest;
import iuh.fit.se.minizalobackend.dtos.request.ResetPasswordRequest;
import iuh.fit.se.minizalobackend.dtos.request.SendOtpRequest;
import iuh.fit.se.minizalobackend.dtos.request.VerifyOtpRequest;
import iuh.fit.se.minizalobackend.exception.TokenRefreshException;
import iuh.fit.se.minizalobackend.models.RefreshToken;
import iuh.fit.se.minizalobackend.payload.request.LoginRequest;
import iuh.fit.se.minizalobackend.payload.request.SignupRequest;
import iuh.fit.se.minizalobackend.payload.request.TokenRefreshRequest;
import iuh.fit.se.minizalobackend.payload.response.JwtResponse;
import iuh.fit.se.minizalobackend.payload.response.MessageResponse;
import iuh.fit.se.minizalobackend.payload.response.TokenRefreshResponse;
import iuh.fit.se.minizalobackend.security.JwtTokenProvider;
import iuh.fit.se.minizalobackend.services.OtpService;
import iuh.fit.se.minizalobackend.services.QrLoginService;
import iuh.fit.se.minizalobackend.services.RefreshTokenService;
import iuh.fit.se.minizalobackend.security.services.UserDetailsImpl;
import iuh.fit.se.minizalobackend.services.UserService;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenService refreshTokenService;
    private final UserService userService;
    private final OtpService otpService;
    private final QrLoginService qrLoginService;

    public AuthController(AuthenticationManager authenticationManager, JwtTokenProvider jwtTokenProvider,
            RefreshTokenService refreshTokenService, UserService userService, OtpService otpService,
            QrLoginService qrLoginService) {
        this.authenticationManager = authenticationManager;
        this.jwtTokenProvider = jwtTokenProvider;
        this.refreshTokenService = refreshTokenService;
        this.userService = userService;
        this.otpService = otpService;
        this.qrLoginService = qrLoginService;
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

    @PostMapping("/send-otp")
    public ResponseEntity<?> sendOtp(@Valid @RequestBody SendOtpRequest request) {
        otpService.generateOtp(request.getPhone());
        return ResponseEntity.ok(new MessageResponse("OTP sent successfully!"));
    }

    @PostMapping("/verify-otp")
    public ResponseEntity<?> verifyOtp(@Valid @RequestBody VerifyOtpRequest request) {
        boolean valid = otpService.verifyOtp(request.getPhone(), request.getOtp());
        if (!valid) {
            return ResponseEntity.badRequest().body(new MessageResponse("Invalid or expired OTP"));
        }
        otpService.invalidate(request.getPhone());
        String verificationToken = jwtTokenProvider.generateVerificationToken(request.getPhone());
        return ResponseEntity.ok(Map.of("verificationToken", verificationToken, "message", "OTP verified successfully!"));
    }

    @PostMapping("/forgot-password/send-otp")
    public ResponseEntity<?> forgotPasswordSendOtp(@Valid @RequestBody SendOtpRequest request) {
        otpService.generateOtp(request.getPhone());
        return ResponseEntity.ok(new MessageResponse("OTP sent successfully!"));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<?> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        if (!request.getNewPassword().equals(request.getConfirmPassword())) {
            return ResponseEntity.badRequest().body(new MessageResponse("Passwords do not match"));
        }
        boolean valid = otpService.verifyOtp(request.getPhone(), request.getOtp());
        if (!valid) {
            return ResponseEntity.badRequest().body(new MessageResponse("Invalid or expired OTP"));
        }
        otpService.invalidate(request.getPhone());
        
        try {
            userService.resetPassword(request.getPhone(), request.getNewPassword());
            return ResponseEntity.ok(new MessageResponse("Password reset successfully!"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(new MessageResponse(e.getMessage()));
        }
    }

    @GetMapping("/qr-login/generate")
    public ResponseEntity<?> generateQrSession() {
        return ResponseEntity.ok(qrLoginService.generateSession());
    }

    @GetMapping("/qr-login/status/{sessionId}")
    public ResponseEntity<?> getQrSessionStatus(@PathVariable String sessionId) {
        return ResponseEntity.ok(qrLoginService.getSessionStatus(sessionId));
    }

    @PostMapping("/qr-login/confirm")
    public ResponseEntity<?> confirmQrLogin(
            @RequestBody Map<String, String> body,
            @AuthenticationPrincipal UserDetailsImpl userDetails) {
        String sessionId = body.get("sessionId");
        if (sessionId == null || sessionId.isBlank()) {
            return ResponseEntity.badRequest().body(new MessageResponse("sessionId is required"));
        }
        try {
            qrLoginService.confirmSession(sessionId, userDetails.getId().toString());
            return ResponseEntity.ok(new MessageResponse("QR login confirmed"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(new MessageResponse(e.getMessage()));
        }
    }

    @GetMapping("/qr-login/generate")
    public ResponseEntity<?> generateQrSession() {
        return ResponseEntity.ok(qrLoginService.generateSession());
    }

    @GetMapping("/qr-login/events/{sessionId}")
    public SseEmitter subscribeQrLogin(@PathVariable String sessionId) {
        SseEmitter emitter = qrLoginService.subscribe(sessionId);
        if (emitter == null) {
            throw new IllegalArgumentException("QR session not found or expired");
        }
        return emitter;
    }

    @PostMapping("/qr-login/confirm")
    public ResponseEntity<?> confirmQrLogin(
            @RequestBody Map<String, String> body,
            @AuthenticationPrincipal UserDetailsImpl userDetails) {
        String sessionId = body.get("sessionId");
        if (sessionId == null || sessionId.isBlank()) {
            return ResponseEntity.badRequest().body(new MessageResponse("sessionId is required"));
        }
        try {
            qrLoginService.confirmSession(sessionId, userDetails.getId().toString());
            return ResponseEntity.ok(new MessageResponse("QR login confirmed"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(new MessageResponse(e.getMessage()));
        }
    }
}
