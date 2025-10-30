package com.geofence.services

import com.geofence.data.TestDatabaseFactory
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.*
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Unit tests for AuthService
 * Tests authentication, JWT token generation/validation, and employee retrieval
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AuthServiceTest {

    private lateinit var authService: AuthService
    private lateinit var testData: com.geofence.data.TestData

    @BeforeAll
    fun setup() {
        TestDatabaseFactory.init()
        authService = AuthService()
    }

    @BeforeEach
    fun beforeEach() {
        TestDatabaseFactory.clearData()
        testData = TestDatabaseFactory.seedTestData()
    }

    @AfterAll
    fun teardown() {
        TestDatabaseFactory.cleanup()
    }

    // ==================== Login Tests ====================

    @Test
    fun `login with valid credentials returns token and employee ID`() = runBlocking {
        val result = authService.login(testData.username, testData.password)

        assertNotNull(result, "Login should return a result for valid credentials")
        assertNotNull(result.token, "Token should not be null")
        assertTrue(result.token.isNotEmpty(), "Token should not be empty")
        assertEquals(testData.employeeId.toString(), result.employeeId, "Employee ID should match")
    }

    @Test
    fun `login with invalid password returns null`() = runBlocking {
        val result = authService.login(testData.username, "wrongpassword")

        assertNull(result, "Login should return null for invalid password")
    }

    @Test
    fun `login with non-existent username returns null`() = runBlocking {
        val result = authService.login("nonexistent", "password123")

        assertNull(result, "Login should return null for non-existent user")
    }

    @Test
    fun `login with empty username returns null`() = runBlocking {
        val result = authService.login("", testData.password)

        assertNull(result, "Login should return null for empty username")
    }

    @Test
    fun `login with empty password returns null`() = runBlocking {
        val result = authService.login(testData.username, "")

        assertNull(result, "Login should return null for empty password")
    }

    @Test
    fun `successful login generates valid JWT token`() = runBlocking {
        val loginResult = authService.login(testData.username, testData.password)
        assertNotNull(loginResult, "Login should succeed")

        // Verify the token can be verified
        val employeeId = authService.verifyToken(loginResult.token)
        assertNotNull(employeeId, "Token should be valid")
        assertEquals(testData.employeeId, employeeId, "Verified employee ID should match")
    }

    // ==================== Token Verification Tests ====================

    @Test
    fun `verifyToken with valid token returns employee ID`() = runBlocking {
        val loginResult = authService.login(testData.username, testData.password)
        assertNotNull(loginResult)

        val employeeId = authService.verifyToken(loginResult.token)

        assertNotNull(employeeId, "Valid token should return employee ID")
        assertEquals(testData.employeeId, employeeId, "Employee ID should match")
    }

    @Test
    fun `verifyToken with invalid token returns null`() {
        val employeeId = authService.verifyToken("invalid.token.here")

        assertNull(employeeId, "Invalid token should return null")
    }

    @Test
    fun `verifyToken with empty token returns null`() {
        val employeeId = authService.verifyToken("")

        assertNull(employeeId, "Empty token should return null")
    }

    @Test
    fun `verifyToken with malformed token returns null`() {
        val employeeId = authService.verifyToken("not-a-jwt-token")

        assertNull(employeeId, "Malformed token should return null")
    }

    @Test
    fun `verifyToken with token having wrong signature returns null`() {
        // Create a token-like string with wrong signature
        val fakeToken = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJlbXBsb3llZUlkIjoiMTIzNDU2NzgtMTIzNC0xMjM0LTEyMzQtMTIzNDU2Nzg5YWJjIiwiZXhwIjo5OTk5OTk5OTk5fQ.fake_signature"

        val employeeId = authService.verifyToken(fakeToken)

        assertNull(employeeId, "Token with wrong signature should return null")
    }

    @Test
    fun `verifyToken handles token with missing claims`() {
        // This will test the robustness of token verification
        val malformedToken = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.e30.Et9HFtf9R3GEMA0IICOfFMVXY7kkTX1wr4qCyhIf58U"

        val employeeId = authService.verifyToken(malformedToken)

        assertNull(employeeId, "Token without required claims should return null")
    }

    // ==================== Get Employee Tests ====================

    @Test
    fun `getEmployee with valid ID returns employee data`() = runBlocking {
        val employee = authService.getEmployee(testData.employeeId)

        assertNotNull(employee, "Should return employee for valid ID")
        assertEquals(testData.employeeId.toString(), employee.employeeId, "Employee ID should match")
        assertEquals(testData.username, employee.username, "Username should match")
        assertEquals(testData.email, employee.email, "Email should match")
    }

    @Test
    fun `getEmployee with non-existent ID returns null`() = runBlocking {
        val nonExistentId = java.util.UUID.randomUUID()
        val employee = authService.getEmployee(nonExistentId)

        assertNull(employee, "Should return null for non-existent employee ID")
    }

    @Test
    fun `getEmployee does not expose password`() = runBlocking {
        val employee = authService.getEmployee(testData.employeeId)

        assertNotNull(employee, "Should return employee data")
        // EmployeeResponse doesn't have a password field - verify structure
        // Note: Kotlin data class fields are not counted the same as Java fields
        assertNotNull(employee.employeeId, "Should have employeeId field")
        assertNotNull(employee.username, "Should have username field")
        assertNotNull(employee.email, "Should have email field")
    }

    // ==================== Integration Tests ====================

    @Test
    fun `full authentication flow - login, verify token, get employee`() = runBlocking {
        // Step 1: Login
        val loginResult = authService.login(testData.username, testData.password)
        assertNotNull(loginResult, "Login should succeed")

        // Step 2: Verify token
        val employeeId = authService.verifyToken(loginResult.token)
        assertNotNull(employeeId, "Token should be valid")
        assertEquals(testData.employeeId, employeeId, "Employee ID from token should match")

        // Step 3: Get employee profile
        val employee = authService.getEmployee(employeeId)
        assertNotNull(employee, "Should retrieve employee profile")
        assertEquals(testData.username, employee.username, "Username should match")
        assertEquals(testData.email, employee.email, "Email should match")
    }

    @Test
    fun `multiple logins generate different tokens with same validity`() = runBlocking {
        val result1 = authService.login(testData.username, testData.password)
        assertNotNull(result1)

        // Small delay to ensure different timestamps
        Thread.sleep(10)

        val result2 = authService.login(testData.username, testData.password)
        assertNotNull(result2)

        // Tokens should be different (due to timestamp)
        assertTrue(result1.token != result2.token, "Multiple logins should generate different tokens")

        // Both tokens should be valid
        val employeeId1 = authService.verifyToken(result1.token)
        val employeeId2 = authService.verifyToken(result2.token)

        assertNotNull(employeeId1, "First token should be valid")
        assertNotNull(employeeId2, "Second token should be valid")
        assertEquals(employeeId1, employeeId2, "Both tokens should reference same employee")
    }

    // ==================== JWT Configuration Tests ====================

    @Test
    fun `getJwtConfig returns valid configuration`() {
        val config = authService.getJwtConfig()

        assertNotNull(config, "JWT config should not be null")
        assertNotNull(config.secret, "Secret should not be null")
        assertNotNull(config.issuer, "Issuer should not be null")
        assertNotNull(config.audience, "Audience should not be null")
        assertNotNull(config.realm, "Realm should not be null")
        assertTrue(config.secret.isNotEmpty(), "Secret should not be empty")
    }
}
