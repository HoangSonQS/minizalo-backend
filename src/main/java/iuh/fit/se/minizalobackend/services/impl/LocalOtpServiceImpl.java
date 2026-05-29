package iuh.fit.se.minizalobackend.services.impl;

import iuh.fit.se.minizalobackend.services.OtpService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
public class LocalOtpServiceImpl implements OtpService {

    private static final int OTP_LENGTH = 6;
    private static final long OTP_EXPIRY_SECONDS = 300; // 5 minutes

    private record OtpEntry(String code, Instant expiresAt) {}

    private final ConcurrentHashMap<String, OtpEntry> otpStore = new ConcurrentHashMap<>();
    private final SecureRandom random = new SecureRandom();

    @Override
    public String generateOtp(String phone) {
        String code = String.format("%0" + OTP_LENGTH + "d", random.nextInt((int) Math.pow(10, OTP_LENGTH)));
        otpStore.put(phone, new OtpEntry(code, Instant.now().plusSeconds(OTP_EXPIRY_SECONDS)));
        log.info("=== [LOCAL OTP] Phone: {} | OTP: {} | Expires in {}s ===", phone, code, OTP_EXPIRY_SECONDS);
        return code;
    }

    @Override
    public boolean verifyOtp(String phone, String otp) {
        OtpEntry entry = otpStore.get(phone);
        if (entry == null) return false;
        if (Instant.now().isAfter(entry.expiresAt())) {
            otpStore.remove(phone);
            return false;
        }
        return entry.code().equals(otp);
    }

    @Override
    public void invalidate(String phone) {
        otpStore.remove(phone);
    }
}
