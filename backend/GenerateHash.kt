import org.mindrot.jbcrypt.BCrypt

fun main() {
    val password = "password123"
    val hash = BCrypt.hashpw(password, BCrypt.gensalt())
    println("Password: $password")
    println("BCrypt Hash: $hash")

    // Verify it works
    val check = BCrypt.checkpw(password, hash)
    println("Verification: $check")
}
