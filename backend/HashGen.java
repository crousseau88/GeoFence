import org.mindrot.jbcrypt.BCrypt;

public class HashGen {
    public static void main(String[] args) {
        String password = "password123";
        String hash = BCrypt.hashpw(password, BCrypt.gensalt(10));
        System.out.println(hash);
        
        // Test it
        boolean matches = BCrypt.checkpw(password, hash);
        System.out.println("Matches: " + matches);
    }
}
