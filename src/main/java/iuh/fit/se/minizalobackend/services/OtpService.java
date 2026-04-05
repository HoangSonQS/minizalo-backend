package iuh.fit.se.minizalobackend.services;

public interface OtpService {
    String generateOtp(String phone);
    boolean verifyOtp(String phone, String otp);
    void invalidate(String phone);
}
