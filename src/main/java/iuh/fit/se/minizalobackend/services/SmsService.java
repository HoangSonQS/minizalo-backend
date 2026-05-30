package iuh.fit.se.minizalobackend.services;

/**
 * Dịch vụ gửi tin nhắn SMS OTP đến số điện thoại người dùng.
 */
public interface SmsService {
    /**
     * Gửi mã OTP đến số điện thoại.
     *
     * @param toPhone Số điện thoại người nhận (định dạng VN: 0xxxxxxxxx hoặc quốc tế +84xxxxxxxxx)
     * @param otp     Mã OTP 6 chữ số
     * @throws RuntimeException nếu gửi thất bại
     */
    void sendOtpSms(String toPhone, String otp);
}
