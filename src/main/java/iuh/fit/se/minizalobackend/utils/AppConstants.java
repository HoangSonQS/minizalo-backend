package iuh.fit.se.minizalobackend.utils;

public class AppConstants {
    public static final String MESSAGE_TYPE_TEXT = "TEXT";
    public static final String MESSAGE_TYPE_IMAGE = "IMAGE";
    public static final String MESSAGE_TYPE_VIDEO = "VIDEO";
    public static final String MESSAGE_TYPE_DOCUMENT = "DOCUMENT";
    public static final String MESSAGE_TYPE_FILE = "FILE";
    public static final String MESSAGE_TYPE_FOLDER = "FOLDER";
    public static final String MESSAGE_TYPE_SYSTEM = "SYSTEM";
    public static final String MESSAGE_TYPE_VOICE = "VOICE";

    /** processMessage: người nhận chỉ nhận tin từ bạn bè / không nhận tin. */
    public static final String STRANGER_MESSAGES_NOT_ALLOWED = "STRANGER_MESSAGES_NOT_ALLOWED";

    public static final String ACTIVITY_MESSAGE_SENT = "MESSAGE_SENT";
    public static final String ACTIVITY_MESSAGE_FORWARDED = "MESSAGE_FORWARDED";
    public static final String ACTIVITY_USER_LOGIN = "USER_LOGIN";

    public static final String ROLE_USER = "ROLE_USER";
    public static final String ROLE_ADMIN = "ROLE_ADMIN";

    private AppConstants() {
        // Private constructor to prevent instantiation
    }
}
