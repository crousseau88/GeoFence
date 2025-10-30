package com.geofence.services

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.auth0.jwt.interfaces.DecodedJWT
import com.geofence.data.DatabaseFactory.dbQuery
import com.geofence.data.Employees
import com.geofence.models.EmployeeResponse
import com.geofence.models.LoginResponse
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.select
import org.mindrot.jbcrypt.BCrypt
import java.util.*

/**
 * Authentication service for employee login and JWT token management
 * Uses bcrypt for password hashing and JWT for stateless authentication
 */
class AuthService {

    // JWT configuration from environment variables
    private val jwtSecret = System.getenv("JWT_SECRET") ?: "geofence-secret-key-change-in-production"
    private val jwtIssuer = System.getenv("JWT_ISSUER") ?: "geofence-backend"
    private val jwtAudience = System.getenv("JWT_AUDIENCE") ?: "geofence-api"
    private val jwtRealm = "GeoFence API"

    // JWT token validity: 7 days
    private val tokenExpirationMs = 7 * 24 * 60 * 60 * 1000L

    /**
     * Authenticate employee with username and password
     * Returns JWT token if credentials are valid, null otherwise
     */
    suspend fun login(username: String, password: String): LoginResponse? = dbQuery {
        // Find employee by username
        val employee = Employees.select { Employees.username eq username }
            .singleOrNull() ?: return@dbQuery null

        // Verify password using bcrypt
        if (!BCrypt.checkpw(password, employee[Employees.password])) {
            return@dbQuery null
        }

        // Generate JWT token
        val token = generateToken(employee[Employees.id].value)
        LoginResponse(
            token = token,
            employeeId = employee[Employees.id].value.toString()
        )
    }

    /**
     * Generate JWT token for authenticated employee
     */
    private fun generateToken(employeeId: UUID): String {
        return JWT.create()
            .withAudience(jwtAudience)
            .withIssuer(jwtIssuer)
            .withClaim("employeeId", employeeId.toString())
            .withExpiresAt(Date(System.currentTimeMillis() + tokenExpirationMs))
            .sign(Algorithm.HMAC256(jwtSecret))
    }

    /**
     * Verify JWT token and extract employee ID
     * Returns employee ID if token is valid, null otherwise
     */
    fun verifyToken(token: String): UUID? {
        return try {
            val decoded = JWT.require(Algorithm.HMAC256(jwtSecret))
                .withAudience(jwtAudience)
                .withIssuer(jwtIssuer)
                .build()
                .verify(token)

            val employeeIdStr = decoded.getClaim("employeeId").asString()
            UUID.fromString(employeeIdStr)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Get employee profile by ID
     */
    suspend fun getEmployee(employeeId: UUID): EmployeeResponse? = dbQuery {
        Employees.select { Employees.id eq employeeId }
            .singleOrNull()
            ?.toEmployeeResponse()
    }

    /**
     * JWT configuration for Ktor auth
     */
    fun getJwtConfig() = JwtConfig(
        secret = jwtSecret,
        issuer = jwtIssuer,
        audience = jwtAudience,
        realm = jwtRealm
    )

    /**
     * Convert database row to EmployeeResponse
     */
    private fun ResultRow.toEmployeeResponse() = EmployeeResponse(
        employeeId = this[Employees.id].value.toString(),
        username = this[Employees.username],
        email = this[Employees.email]
    )
}

/**
 * JWT configuration data class for Ktor
 */
data class JwtConfig(
    val secret: String,
    val issuer: String,
    val audience: String,
    val realm: String
)
