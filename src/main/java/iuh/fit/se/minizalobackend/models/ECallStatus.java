package iuh.fit.se.minizalobackend.models;

public enum ECallStatus {
    PENDING,   // Đang đổ chuông
    ACTIVE,    // Đang đàm thoại
    REJECTED,  // Bị từ chối
    ENDED,     // Kết thúc bình thường
    MISSED,    // Cuộc gọi nhỡ (người gọi hủy hoặc timeout)
    CANCELLED  // Người gọi hủy trước khi đối phương bắt máy
}
