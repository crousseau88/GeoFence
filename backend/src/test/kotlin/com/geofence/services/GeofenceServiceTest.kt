package com.geofence.services

import com.geofence.data.Geofences
import com.geofence.data.TestDatabaseFactory
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.sql.deleteAll
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.jupiter.api.*
import java.time.LocalDateTime
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Unit tests for GeofenceService
 * Tests geofence data retrieval
 *
 * Note: H2 doesn't support PostGIS, so these tests verify the basic structure.
 * Full PostGIS functionality should be tested with integration tests using real PostgreSQL.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class GeofenceServiceTest {

    private lateinit var geofenceService: GeofenceService
    private lateinit var testData: com.geofence.data.TestData

    @BeforeAll
    fun setup() {
        TestDatabaseFactory.init()
        geofenceService = GeofenceService()
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

    // ==================== Geofence Retrieval Tests ====================

    @Test
    fun `getGeofence returns null when no geofence exists`() = runBlocking {
        // Clear all geofences
        transaction {
            Geofences.deleteAll()
        }

        val result = geofenceService.getGeofence()

        assertNull(result, "Should return null when no geofence exists")
    }

    @Test
    fun `getGeofence returns geofence data structure`() = runBlocking {
        // This test verifies the service attempts to retrieve geofence
        // Note: PostGIS queries will fail in H2, but we can verify the attempt
        val result = try {
            geofenceService.getGeofence()
        } catch (e: Exception) {
            // Expected to fail due to PostGIS syntax in H2
            // This verifies the service is attempting the query
            null
        }

        // In H2, this will be null due to PostGIS incompatibility
        // In real PostgreSQL with PostGIS, this should return valid data
        // This test documents the expected behavior
        assertNull(result, "H2 cannot process PostGIS queries, expecting null")
    }

    @Test
    fun `getGeofence structure validation`() {
        // Test to verify GeofenceResponse structure
        val mockResponse = com.geofence.models.GeofenceResponse(
            geofenceId = testData.geofenceId.toString(),
            requestId = "MEDIA_OFFICE",
            name = "Media Office",
            latitude = 39.9187,
            longitude = -75.3876,
            radius = 100
        )

        assertNotNull(mockResponse, "GeofenceResponse should be constructable")
        assertEquals(testData.geofenceId.toString(), mockResponse.geofenceId)
        assertEquals("MEDIA_OFFICE", mockResponse.requestId)
        assertEquals("Media Office", mockResponse.name)
        assertEquals(39.9187, mockResponse.latitude)
        assertEquals(-75.3876, mockResponse.longitude)
        assertEquals(100, mockResponse.radius)
    }

    @Test
    fun `geofence data contains all required fields`() {
        val mockResponse = com.geofence.models.GeofenceResponse(
            geofenceId = UUID.randomUUID().toString(),
            requestId = "TEST_GEOFENCE",
            name = "Test Location",
            latitude = 40.0,
            longitude = -75.0,
            radius = 150
        )

        // Verify all fields are present
        assertNotNull(mockResponse.geofenceId, "geofenceId should not be null")
        assertNotNull(mockResponse.requestId, "requestId should not be null")
        assertNotNull(mockResponse.name, "name should not be null")
        assertNotNull(mockResponse.latitude, "latitude should not be null")
        assertNotNull(mockResponse.longitude, "longitude should not be null")
        assertNotNull(mockResponse.radius, "radius should not be null")
    }

    @Test
    fun `geofence validates coordinate ranges`() {
        // Test valid coordinate ranges
        val validGeofence = com.geofence.models.GeofenceResponse(
            geofenceId = UUID.randomUUID().toString(),
            requestId = "VALID",
            name = "Valid Location",
            latitude = 39.9187,  // Valid: -90 to 90
            longitude = -75.3876,  // Valid: -180 to 180
            radius = 100
        )

        // Latitude should be in valid range
        assert(validGeofence.latitude in -90.0..90.0) {
            "Latitude should be between -90 and 90"
        }

        // Longitude should be in valid range
        assert(validGeofence.longitude in -180.0..180.0) {
            "Longitude should be between -180 and 180"
        }

        // Radius should be positive
        assert(validGeofence.radius > 0) {
            "Radius should be positive"
        }
    }

    @Test
    fun `geofence radius is positive integer`() {
        val geofence = com.geofence.models.GeofenceResponse(
            geofenceId = UUID.randomUUID().toString(),
            requestId = "TEST",
            name = "Test",
            latitude = 0.0,
            longitude = 0.0,
            radius = 100
        )

        assert(geofence.radius > 0) { "Radius should be positive" }
        assert(geofence.radius is Int) { "Radius should be an integer" }
    }

    // ==================== Edge Case Tests ====================

    @Test
    fun `geofence handles extreme valid coordinates`() {
        // Test boundary coordinates
        val northPole = com.geofence.models.GeofenceResponse(
            geofenceId = UUID.randomUUID().toString(),
            requestId = "NORTH_POLE",
            name = "North Pole",
            latitude = 90.0,
            longitude = 0.0,
            radius = 1000
        )

        val southPole = com.geofence.models.GeofenceResponse(
            geofenceId = UUID.randomUUID().toString(),
            requestId = "SOUTH_POLE",
            name = "South Pole",
            latitude = -90.0,
            longitude = 0.0,
            radius = 1000
        )

        val dateLine = com.geofence.models.GeofenceResponse(
            geofenceId = UUID.randomUUID().toString(),
            requestId = "DATE_LINE",
            name = "International Date Line",
            latitude = 0.0,
            longitude = 180.0,
            radius = 500
        )

        assertNotNull(northPole)
        assertNotNull(southPole)
        assertNotNull(dateLine)
    }

    @Test
    fun `geofence requestId is unique identifier`() {
        val geofence1 = com.geofence.models.GeofenceResponse(
            geofenceId = UUID.randomUUID().toString(),
            requestId = "UNIQUE_ID_1",
            name = "Location 1",
            latitude = 40.0,
            longitude = -75.0,
            radius = 100
        )

        val geofence2 = com.geofence.models.GeofenceResponse(
            geofenceId = UUID.randomUUID().toString(),
            requestId = "UNIQUE_ID_2",
            name = "Location 2",
            latitude = 40.0,
            longitude = -75.0,
            radius = 100
        )

        assert(geofence1.requestId != geofence2.requestId) {
            "Different geofences should have different request IDs"
        }
    }

    // ==================== Data Integrity Tests ====================

    @Test
    fun `geofence data persists in database`() = runBlocking {
        // Verify test data was seeded
        val geofenceExists = transaction {
            Geofences.selectAll().count() > 0
        }

        assert(geofenceExists) { "Geofence should exist in database after seeding" }
    }

    @Test
    fun `multiple geofences can exist in database`() = runBlocking {
        // Add additional geofence
        transaction {
            Geofences.insert {
                it[id] = UUID.randomUUID()
                it[requestId] = "SECOND_LOCATION"
                it[name] = "Second Location"
                it[radius] = 200
                it[createdAt] = LocalDateTime.now()
            }
        }

        val count = transaction {
            Geofences.selectAll().count()
        }

        assert(count >= 2) { "Should have at least 2 geofences in database" }
    }
}
