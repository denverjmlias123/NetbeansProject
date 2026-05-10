package groupprojectexe;
import java.nio.charset.StandardCharsets;  // ADD THIS

public class PasswordUtil {
    public static String hashPassword(String password) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hashedBytes = md.digest(password.getBytes(StandardCharsets.UTF_8));  // UPDATED
            StringBuilder sb = new StringBuilder();
            for (byte b : hashedBytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException("Critical Error: Unable to hash password - " + e.getMessage(), e);
        }
    }
}