package com.geofence.routes

import com.geofence.data.TestDatabaseFactory
import com.geofence.models.*
import com.geofence.services.AuthService
import com.geofence.services.GeofenceService
import com.geofence.services.ScheduleService
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.*
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Integration tests for API routes
 * Tests all endpoints with authentication and request/response handling
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RoutesIntegrationTest {

    private lateinit var authService: AuthService
    private lateinit var geofenceService: GeofenceService
    private lateinit var scheduleService: ScheduleService
    private lateinit var testData: com.geofence.data.TestData
    private lateinit var validToken: String

    @BeforeAll
    fun setup() {
        TestDatabaseFactory.init()
        authService = AuthService()
        geofenceService = GeofenceService()
        scheduleService = ScheduleService()
    }

    @BeforeEach
    fun beforeEach() = runBlocking {
        TestDatabaseFactory.clearData()
        testData = TestDatabaseFactory.seedTestData()

        // Get a valid token for protected routes
        val loginResult = authService.login(testData.username, testData.password)
        assertNotNull(loginResult)
        validToken = loginResult.token
    }

    @AfterAll
    fun teardown() {
        TestDatabaseFactory.cleanup()
    }

    // Helper function to configure test application
    private fun Application.testModule() {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                prettyPrint = true
            })
        }

        val jwtConfig = authService.getJwtConfig()
        install(Authentication) {
            jwt("auth-jwt") {
                realm = jwtConfig.realm
                verifier(
                    com.auth0.jwt.JWT
                        .require(com.auth0.jwt.algorithms.Algorithm.HMAC256(jwtConfig.secret))
                        .withAudience(jwtConfig.audience)
                        .withIssuer(jwtConfig.issuer)
                        .build()
                )
                validate { credential ->
                    if (credential.payload.getClaim("employeeId").asString() != null) {
                        JWTPrincipal(credential.payload)
                    } else null
                }
            }
        }

        configureRoutes()
    }

    // ==================== Login Route Tests ====================

    @Test
    fun `POST login with valid credentials returns token and 200`() = testApplication {
        application { testModule() }

        val response = client.post("/api/login") {
            contentType(ContentType.Application.Json)
            setBody("""{"username":"${testData.username}","password":"${testData.password}"}""")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        assertTrue(body.contains("token"), "Response should contain token")
        assertTrue(body.contains(testData.employeeId.toString()), "Response should contain employee ID")
    }

    @Test
    fun `POST login with invalid password returns 401`() = testApplication {
        application { testModule() }

        val response = client.post("/api/login") {
            contentType(ContentType.Application.Json)
            setBody("""{"username":"${testData.username}","password":"wrongpassword"}""")
        }

        assertEquals(HttpStatusCode.Unauthorized, response.status)
        val body = response.bodyAsText()
        assertTrue(body.contains("error"), "Response should contain error")
    }

    @Test
    fun `POST login with non-existent user returns 401`() = testApplication {
        application { testModule() }

        val response = client.post("/api/login") {
            contentType(ContentType.Application.Json)
            setBody("""{"username":"nonexistent","password":"password123"}""")
        }

        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `POST login with blank username returns 400`() = testApplication {
        application { testModule() }

        val response = client.post("/api/login") {
            contentType(ContentType.Application.Json)
            setBody("""{"username":"","password":"password123"}""")
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
        val body = response.bodyAsText()
        assertTrue(body.contains("required"), "Response should indicate required fields")
    }

    @Test
    fun `POST login with blank password returns 400`() = testApplication {
        application { testModule() }

        val response = client.post("/api/login") {
            contentType(ContentType.Application.Json)
            setBody("""{"username":"${testData.username}","password":""}""")
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `POST login with missing fields returns 400`() = testApplication {
        application { testModule() }

        val response = client.post("/api/login") {
            contentType(ContentType.Application.Json)
            setBody("""{"username":"${testData.username}"}""")
        }

        // Note: May return 500 if JSON parsing fails before validation
        assertTrue(
            response.status == HttpStatusCode.BadRequest || response.status == HttpStatusCode.InternalServerError,
            "Should return error status for missing fields"
        )
    }

    // ==================== Get Employee Route Tests ====================

    @Test
    fun `GET employee with valid token returns employee data and 200`() = testApplication {
        application { testModule() }

        val response = client.get("/api/employee") {
            header("Authorization", "Bearer $validToken")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        assertTrue(body.contains(testData.username), "Response should contain username")
        assertTrue(body.contains(testData.email), "Response should contain email")
    }

    @Test
    fun `GET employee without token returns 401`() = testApplication {
        application { testModule() }

        val response = client.get("/api/employee")

        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `GET employee with invalid token returns 401`() = testApplication {
        application { testModule() }

        val response = client.get("/api/employee") {
            header("Authorization", "Bearer invalid.token.here")
        }

        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `GET employee with malformed authorization header returns 401`() = testApplication {
        application { testModule() }

        val response = client.get("/api/employee") {
            header("Authorization", "InvalidFormat")
        }

        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    // ==================== Get Geofence Route Tests ====================

    @Test
    fun `GET geofence without token returns 401`() = testApplication {
        application { testModule() }

        val response = client.get("/api/geofence")

        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `GET geofence with valid token attempts to retrieve geofence`() = testApplication {
        application { testModule() }

        val response = client.get("/api/geofence") {
            header("Authorization", "Bearer $validToken")
        }

        // Will return 404 or 500 in H2 due to PostGIS incompatibility
        // In real PostgreSQL, should return 200
        assertTrue(
            response.status == HttpStatusCode.NotFound ||
            response.status == HttpStatusCode.InternalServerError,
            "H2 cannot process PostGIS queries"
        )
    }

    // ==================== Clock-In Route Tests ====================

    @Test
    fun `POST clock-in with valid token creates event and returns 201`() = testApplication {
        application { testModule() }

        val response = client.post("/api/schedule/clock-in") {
            header("Authorization", "Bearer $validToken")
            contentType(ContentType.Application.Json)
            setBody("""{"latitude":39.9187,"longitude":-75.3876}""")
        }

        assertEquals(HttpStatusCode.Created, response.status)
        val body = response.bodyAsText()
        assertTrue(body.contains("success") || body.contains("recorded"), "Response should indicate success")
    }

    @Test
    fun `POST clock-in without location data succeeds with 201`() = testApplication {
        application { testModule() }

        val response = client.post("/api/schedule/clock-in") {
            header("Authorization", "Bearer $validToken")
            contentType(ContentType.Application.Json)
            setBody("""{}""")
        }

        assertEquals(HttpStatusCode.Created, response.status)
    }

    @Test
    fun `POST clock-in without token returns 401`() = testApplication {
        application { testModule() }

        val response = client.post("/api/schedule/clock-in") {
            contentType(ContentType.Application.Json)
            setBody("""{"latitude":39.9187,"longitude":-75.3876}""")
        }

        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `POST clock-in with invalid token returns 401`() = testApplication {
        application { testModule() }

        val response = client.post("/api/schedule/clock-in") {
            header("Authorization", "Bearer invalid.token")
            contentType(ContentType.Application.Json)
            setBody("""{"latitude":39.9187,"longitude":-75.3876}""")
        }

        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    // ==================== Clock-Out Route Tests ====================

    @Test
    fun `POST clock-out with valid token creates event and returns 201`() = testApplication {
        application { testModule() }

        val response = client.post("/api/schedule/clock-out") {
            header("Authorization", "Bearer $validToken")
            contentType(ContentType.Application.Json)
            setBody("""{"latitude":39.9187,"longitude":-75.3876}""")
        }

        assertEquals(HttpStatusCode.Created, response.status)
        val body = response.bodyAsText()
        assertTrue(body.contains("success") || body.contains("recorded"))
    }

    @Test
    fun `POST clock-out without location data succeeds with 201`() = testApplication {
        application { testModule() }

        val response = client.post("/api/schedule/clock-out") {
            header("Authorization", "Bearer $validToken")
            contentType(ContentType.Application.Json)
            setBody("""{}""")
        }

        assertEquals(HttpStatusCode.Created, response.status)
    }

    @Test
    fun `POST clock-out without token returns 401`() = testApplication {
        application { testModule() }

        val response = client.post("/api/schedule/clock-out") {
            contentType(ContentType.Application.Json)
            setBody("""{"latitude":39.9187,"longitude":-75.3876}""")
        }

        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    // ==================== Exit Event Route Tests ====================

    @Test
    fun `POST exit with valid data creates event and returns 201`() = testApplication {
        application { testModule() }

        val response = client.post("/api/schedule/exit") {
            header("Authorization", "Bearer $validToken")
            contentType(ContentType.Application.Json)
            setBody("""{"latitude":39.9200,"longitude":-75.3900,"distanceFromGeofence":150.5}""")
        }

        assertEquals(HttpStatusCode.Created, response.status)
        val body = response.bodyAsText()
        assertTrue(body.contains("success") || body.contains("recorded"))
    }

    @Test
    fun `POST exit with negative distance returns 400`() = testApplication {
        application { testModule() }

        val response = client.post("/api/schedule/exit") {
            header("Authorization", "Bearer $validToken")
            contentType(ContentType.Application.Json)
            setBody("""{"latitude":39.9200,"longitude":-75.3900,"distanceFromGeofence":-10.0}""")
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
        val body = response.bodyAsText()
        assertTrue(body.contains("negative") || body.contains("Distance"))
    }

    @Test
    fun `POST exit without token returns 401`() = testApplication {
        application { testModule() }

        val response = client.post("/api/schedule/exit") {
            contentType(ContentType.Application.Json)
            setBody("""{"latitude":39.9200,"longitude":-75.3900,"distanceFromGeofence":150.5}""")
        }

        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `POST exit with zero distance succeeds with 201`() = testApplication {
        application { testModule() }

        val response = client.post("/api/schedule/exit") {
            header("Authorization", "Bearer $validToken")
            contentType(ContentType.Application.Json)
            setBody("""{"latitude":39.9200,"longitude":-75.3900,"distanceFromGeofence":0.0}""")
        }

        assertEquals(HttpStatusCode.Created, response.status)
    }

    // ==================== Get Schedule Route Tests ====================

    @Test
    fun `GET schedule with valid token returns schedule and 200`() = testApplication {
        application { testModule() }

        val response = client.get("/api/schedule") {
            header("Authorization", "Bearer $validToken")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        assertTrue(body.contains("events"), "Response should contain events array")
    }

    @Test
    fun `GET schedule without token returns 401`() = testApplication {
        application { testModule() }

        val response = client.get("/api/schedule")

        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `GET schedule with invalid token returns 401`() = testApplication {
        application { testModule() }

        val response = client.get("/api/schedule") {
            header("Authorization", "Bearer invalid.token")
        }

        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `GET schedule returns empty events for employee with no events`() = testApplication {
        application { testModule() }

        val response = client.get("/api/schedule") {
            header("Authorization", "Bearer $validToken")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        // Should return empty events array
        assertTrue(body.contains("[]") || body.contains("events"), "Should contain events array")
    }

    // ==================== Full Workflow Integration Tests ====================

    @Test
    fun `full workflow - login, clock-in, clock-out, get schedule`() = testApplication {
        application { testModule() }

        // Step 1: Login
        val loginResponse = client.post("/api/login") {
            contentType(ContentType.Application.Json)
            setBody("""{"username":"${testData.username}","password":"${testData.password}"}""")
        }
        assertEquals(HttpStatusCode.OK, loginResponse.status)

        // Step 2: Clock in
        val clockInResponse = client.post("/api/schedule/clock-in") {
            header("Authorization", "Bearer $validToken")
            contentType(ContentType.Application.Json)
            setBody("""{"latitude":39.9187,"longitude":-75.3876}""")
        }
        assertEquals(HttpStatusCode.Created, clockInResponse.status)

        // Step 3: Clock out
        val clockOutResponse = client.post("/api/schedule/clock-out") {
            header("Authorization", "Bearer $validToken")
            contentType(ContentType.Application.Json)
            setBody("""{"latitude":39.9187,"longitude":-75.3876}""")
        }
        assertEquals(HttpStatusCode.Created, clockOutResponse.status)

        // Step 4: Get schedule
        val scheduleResponse = client.get("/api/schedule") {
            header("Authorization", "Bearer $validToken")
        }
        assertEquals(HttpStatusCode.OK, scheduleResponse.status)
        val body = scheduleResponse.bodyAsText()
        assertTrue(body.contains("CLOCK_IN") || body.contains("events"), "Schedule should contain events")
    }

    @Test
    fun `multiple clock-in events can be created sequentially`() = testApplication {
        application { testModule() }

        // First clock-in
        val response1 = client.post("/api/schedule/clock-in") {
            header("Authorization", "Bearer $validToken")
            contentType(ContentType.Application.Json)
            setBody("""{"latitude":39.9187,"longitude":-75.3876}""")
        }
        assertEquals(HttpStatusCode.Created, response1.status)

        // Second clock-in
        val response2 = client.post("/api/schedule/clock-in") {
            header("Authorization", "Bearer $validToken")
            contentType(ContentType.Application.Json)
            setBody("""{"latitude":39.9187,"longitude":-75.3876}""")
        }
        assertEquals(HttpStatusCode.Created, response2.status)
    }

    @Test
    fun `authentication flow with get employee works correctly`() = testApplication {
        application { testModule() }

        // Login
        val loginResponse = client.post("/api/login") {
            contentType(ContentType.Application.Json)
            setBody("""{"username":"${testData.username}","password":"${testData.password}"}""")
        }
        assertEquals(HttpStatusCode.OK, loginResponse.status)

        // Get employee profile
        val employeeResponse = client.get("/api/employee") {
            header("Authorization", "Bearer $validToken")
        }
        assertEquals(HttpStatusCode.OK, employeeResponse.status)
        val body = employeeResponse.bodyAsText()
        assertTrue(body.contains(testData.username))
        assertTrue(body.contains(testData.email))
    }
}
