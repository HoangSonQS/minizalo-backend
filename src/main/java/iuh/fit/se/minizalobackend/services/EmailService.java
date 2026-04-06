package iuh.fit.se.minizalobackend.services;

public interface EmailService {
    void sendOtpEmail(String toEmail, String otp);
}
