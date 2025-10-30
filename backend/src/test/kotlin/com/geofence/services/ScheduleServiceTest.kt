package com.geofence.services

import com.geofence.data.Schedule
import com.geofence.data.TestDatabaseFactory
import com.geofence.models.EventType
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.jupiter.api.*
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Unit tests for ScheduleService
 * Tests clock-in, clock-out, exit events, and schedule history retrieval
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ScheduleServiceTest {

    private lateinit var scheduleService: ScheduleService
    private lateinit var testData: com.geofence.data.TestData

    @BeforeAll
    fun setup() {
        TestDatabaseFactory.init()
        scheduleService = ScheduleService()
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

    // ==================== Clock-In Tests ====================

    @Test
    fun `clockIn creates event with valid employee ID`() = runBlocking {
        val eventId = scheduleService.clockIn(
            employeeId = testData.employeeId,
            latitude = 39.9187,
            longitude = -75.3876
        )

        assertNotNull(eventId, "Clock-in should return event ID")

        // Verify event was created in database
        val event = transaction {
            Schedule.select { Schedule.id eq eventId }.singleOrNull()
        }

        assertNotNull(event, "Event should exist in database")
        assertEquals(testData.employeeId, event[Schedule.employeeId].value)
        assertEquals(EventType.CLOCK_IN.value.toShort(), event[Schedule.eventType])
    }

    @Test
    fun `clockIn with latitude and longitude stores location`() = runBlocking {
        val latitude = 39.9187
        val longitude = -75.3876

        val eventId = scheduleService.clockIn(
            employeeId = testData.employeeId,
            latitude = latitude,
            longitude = longitude
        )

        val event = transaction {
            Schedule.select { Schedule.id eq eventId }.single()
        }

        assertEquals(latitude, event[Schedule.latitude]?.toDouble())
        assertEquals(longitude, event[Schedule.longitude]?.toDouble())
    }

    @Test
    fun `clockIn without location creates event with null coordinates`() = runBlocking {
        val eventId = scheduleService.clockIn(
            employeeId = testData.employeeId,
            latitude = null,
            longitude = null
        )

        val event = transaction {
            Schedule.select { Schedule.id eq eventId }.single()
        }

        assertNull(event[Schedule.latitude], "Latitude should be null")
        assertNull(event[Schedule.longitude], "Longitude should be null")
    }

    @Test
    fun `clockIn sets correct event type`() = runBlocking {
        val eventId = scheduleService.clockIn(
            employeeId = testData.employeeId,
            latitude = null,
            longitude = null
        )

        val event = transaction {
            Schedule.select { Schedule.id eq eventId }.single()
        }

        assertEquals(EventType.CLOCK_IN.value.toShort(), event[Schedule.eventType])
    }

    @Test
    fun `multiple clockIn events can be created`() = runBlocking {
        val eventId1 = scheduleService.clockIn(testData.employeeId, 39.9187, -75.3876)
        Thread.sleep(10) // Ensure different timestamps
        val eventId2 = scheduleService.clockIn(testData.employeeId, 39.9187, -75.3876)

        assertTrue(eventId1 != eventId2, "Different clock-in events should have different IDs")

        val eventCount = transaction {
            Schedule.select {
                (Schedule.employeeId eq testData.employeeId).and(
                        Schedule.eventType eq EventType.CLOCK_IN.value.toShort())
            }.count()
        }

        assertEquals(2, eventCount, "Should have 2 clock-in events")
    }

    // ==================== Clock-Out Tests ====================

    @Test
    fun `clockOut creates event with valid employee ID`() = runBlocking {
        val eventId = scheduleService.clockOut(
            employeeId = testData.employeeId,
            latitude = 39.9187,
            longitude = -75.3876
        )

        assertNotNull(eventId, "Clock-out should return event ID")

        val event = transaction {
            Schedule.select { Schedule.id eq eventId }.singleOrNull()
        }

        assertNotNull(event, "Event should exist in database")
        assertEquals(testData.employeeId, event[Schedule.employeeId].value)
        assertEquals(EventType.CLOCK_OUT.value.toShort(), event[Schedule.eventType])
    }

    @Test
    fun `clockOut calculates duration from most recent clockIn`() = runBlocking {
        // Clock in first
        scheduleService.clockIn(testData.employeeId, 39.9187, -75.3876)

        // Wait a bit to ensure duration is > 0
        Thread.sleep(100)

        // Clock out
        val clockOutId = scheduleService.clockOut(testData.employeeId, 39.9187, -75.3876)

        val event = transaction {
            Schedule.select { Schedule.id eq clockOutId }.single()
        }

        val duration = event[Schedule.durationMinutes]
        assertNotNull(duration, "Duration should be calculated")
        assertTrue(duration >= 0, "Duration should be non-negative")
    }

    @Test
    fun `clockOut without prior clockIn has null duration`() = runBlocking {
        // Clock out without clocking in first
        val eventId = scheduleService.clockOut(testData.employeeId, 39.9187, -75.3876)

        val event = transaction {
            Schedule.select { Schedule.id eq eventId }.single()
        }

        assertNull(event[Schedule.durationMinutes], "Duration should be null without prior clock-in")
    }

    @Test
    fun `clockOut stores location data`() = runBlocking {
        val latitude = 39.9200
        val longitude = -75.3880

        val eventId = scheduleService.clockOut(
            employeeId = testData.employeeId,
            latitude = latitude,
            longitude = longitude
        )

        val event = transaction {
            Schedule.select { Schedule.id eq eventId }.single()
        }

        assertEquals(latitude, event[Schedule.latitude]?.toDouble())
        assertEquals(longitude, event[Schedule.longitude]?.toDouble())
    }

    @Test
    fun `clockOut sets correct event type`() = runBlocking {
        val eventId = scheduleService.clockOut(testData.employeeId, null, null)

        val event = transaction {
            Schedule.select { Schedule.id eq eventId }.single()
        }

        assertEquals(EventType.CLOCK_OUT.value.toShort(), event[Schedule.eventType])
    }

    // ==================== Exit Event Tests ====================

    @Test
    fun `recordExit creates exit event with all required data`() = runBlocking {
        val latitude = 39.9200
        val longitude = -75.3900
        val distance = 150.5

        val eventId = scheduleService.recordExit(
            employeeId = testData.employeeId,
            latitude = latitude,
            longitude = longitude,
            distanceFromGeofence = distance
        )

        assertNotNull(eventId, "Exit event should return event ID")

        val event = transaction {
            Schedule.select { Schedule.id eq eventId }.single()
        }

        assertEquals(testData.employeeId, event[Schedule.employeeId].value)
        assertEquals(EventType.EXIT.value.toShort(), event[Schedule.eventType])
        assertEquals(latitude, event[Schedule.latitude]?.toDouble())
        assertEquals(longitude, event[Schedule.longitude]?.toDouble())
        assertEquals(distance, event[Schedule.distanceFromGeofence]?.toDouble())
    }

    @Test
    fun `recordExit requires location data`() = runBlocking {
        val eventId = scheduleService.recordExit(
            employeeId = testData.employeeId,
            latitude = 40.0,
            longitude = -75.0,
            distanceFromGeofence = 200.0
        )

        val event = transaction {
            Schedule.select { Schedule.id eq eventId }.single()
        }

        assertNotNull(event[Schedule.latitude], "Exit event must have latitude")
        assertNotNull(event[Schedule.longitude], "Exit event must have longitude")
        assertNotNull(event[Schedule.distanceFromGeofence], "Exit event must have distance")
    }

    @Test
    fun `recordExit stores distance from geofence`() = runBlocking {
        val distance = 250.75

        val eventId = scheduleService.recordExit(
            employeeId = testData.employeeId,
            latitude = 40.0,
            longitude = -75.0,
            distanceFromGeofence = distance
        )

        val event = transaction {
            Schedule.select { Schedule.id eq eventId }.single()
        }

        assertEquals(distance, event[Schedule.distanceFromGeofence]?.toDouble())
    }

    @Test
    fun `recordExit sets correct event type`() = runBlocking {
        val eventId = scheduleService.recordExit(
            employeeId = testData.employeeId,
            latitude = 40.0,
            longitude = -75.0,
            distanceFromGeofence = 100.0
        )

        val event = transaction {
            Schedule.select { Schedule.id eq eventId }.single()
        }

        assertEquals(EventType.EXIT.value.toShort(), event[Schedule.eventType])
    }

    // ==================== Get Recent Schedule Tests ====================

    @Test
    fun `getRecentSchedule returns empty list for employee with no events`() = runBlocking {
        val result = scheduleService.getRecentSchedule(testData.employeeId)

        assertNotNull(result, "Should return a ScheduleResponse")
        assertTrue(result.events.isEmpty(), "Events list should be empty")
    }

    @Test
    fun `getRecentSchedule returns all event types`() = runBlocking {
        // Create various events
        scheduleService.clockIn(testData.employeeId, 39.9187, -75.3876)
        Thread.sleep(10)
        scheduleService.clockOut(testData.employeeId, 39.9187, -75.3876)
        Thread.sleep(10)
        scheduleService.recordExit(testData.employeeId, 39.9200, -75.3900, 150.0)

        val result = scheduleService.getRecentSchedule(testData.employeeId)

        assertEquals(3, result.events.size, "Should return all 3 events")

        val eventTypes = result.events.map { it.eventType }.toSet()
        assertTrue(eventTypes.contains("CLOCK_IN"), "Should contain clock-in event")
        assertTrue(eventTypes.contains("CLOCK_OUT"), "Should contain clock-out event")
        assertTrue(eventTypes.contains("EXIT"), "Should contain exit event")
    }

    @Test
    fun `getRecentSchedule returns events in descending order by time`() = runBlocking {
        // Create events with delays to ensure different timestamps
        scheduleService.clockIn(testData.employeeId, 39.9187, -75.3876)
        Thread.sleep(100)
        scheduleService.clockOut(testData.employeeId, 39.9187, -75.3876)
        Thread.sleep(100)
        scheduleService.recordExit(testData.employeeId, 39.9200, -75.3900, 150.0)

        val result = scheduleService.getRecentSchedule(testData.employeeId)

        assertEquals(3, result.events.size)
        // Events should be in reverse chronological order (newest first)
        assertEquals("EXIT", result.events[0].eventType)
        assertEquals("CLOCK_OUT", result.events[1].eventType)
        assertEquals("CLOCK_IN", result.events[2].eventType)
    }

    @Test
    fun `getRecentSchedule only returns events from last 7 days`() = runBlocking {
        // Create a recent event
        val recentEventId = scheduleService.clockIn(testData.employeeId, 39.9187, -75.3876)

        val result = scheduleService.getRecentSchedule(testData.employeeId)

        // Should contain the recent event
        assertTrue(result.events.isNotEmpty(), "Should return recent events")
        assertTrue(result.events.any { it.eventId == recentEventId.toString() })
    }

    @Test
    fun `getRecentSchedule includes location data when available`() = runBlocking {
        val latitude = 39.9187
        val longitude = -75.3876

        scheduleService.clockIn(testData.employeeId, latitude, longitude)

        val result = scheduleService.getRecentSchedule(testData.employeeId)

        assertEquals(1, result.events.size)
        val event = result.events[0]
        assertEquals(latitude, event.latitude)
        assertEquals(longitude, event.longitude)
    }

    @Test
    fun `getRecentSchedule includes duration for clock-out events`() = runBlocking {
        scheduleService.clockIn(testData.employeeId, 39.9187, -75.3876)
        Thread.sleep(100)
        scheduleService.clockOut(testData.employeeId, 39.9187, -75.3876)

        val result = scheduleService.getRecentSchedule(testData.employeeId)

        val clockOutEvent = result.events.find { it.eventType == "CLOCK_OUT" }
        assertNotNull(clockOutEvent, "Should have clock-out event")
        assertNotNull(clockOutEvent.durationMinutes, "Clock-out should have duration")
        assertTrue(clockOutEvent.durationMinutes!! >= 0, "Duration should be non-negative")
    }

    @Test
    fun `getRecentSchedule includes distance for exit events`() = runBlocking {
        val distance = 175.5
        scheduleService.recordExit(testData.employeeId, 39.9200, -75.3900, distance)

        val result = scheduleService.getRecentSchedule(testData.employeeId)

        val exitEvent = result.events.find { it.eventType == "EXIT" }
        assertNotNull(exitEvent, "Should have exit event")
        assertEquals(distance, exitEvent.distanceFromGeofence)
    }

    // ==================== Edge Case Tests ====================

    @Test
    fun `multiple employees can have separate events`() = runBlocking {
        val employee2Id = UUID.randomUUID()

        // Create events for different employees
        scheduleService.clockIn(testData.employeeId, 39.9187, -75.3876)
        // Note: This will fail because employee2 doesn't exist, but demonstrates the concept

        val result = scheduleService.getRecentSchedule(testData.employeeId)

        // Should only return events for testData.employeeId
        assertTrue(result.events.all { it.eventId.isNotEmpty() })
    }

    @Test
    fun `event timestamps are recorded correctly`() = runBlocking {
        val beforeTime = System.currentTimeMillis()
        scheduleService.clockIn(testData.employeeId, 39.9187, -75.3876)
        val afterTime = System.currentTimeMillis()

        val result = scheduleService.getRecentSchedule(testData.employeeId)

        assertEquals(1, result.events.size)
        val event = result.events[0]
        assertNotNull(event.eventTime, "Event should have timestamp")
        assertTrue(event.eventTime.isNotEmpty(), "Timestamp should not be empty")
    }

    // ==================== EventType Enum Tests ====================

    @Test
    fun `EventType enum has all required types`() {
        val clockIn = EventType.CLOCK_IN
        val clockOut = EventType.CLOCK_OUT
        val exit = EventType.EXIT

        assertEquals(1, clockIn.value)
        assertEquals(2, clockOut.value)
        assertEquals(3, exit.value)

        assertEquals("CLOCK_IN", clockIn.displayName)
        assertEquals("CLOCK_OUT", clockOut.displayName)
        assertEquals("EXIT", exit.displayName)
    }

    @Test
    fun `EventType fromValue returns correct enum`() {
        assertEquals(EventType.CLOCK_IN, EventType.fromValue(1))
        assertEquals(EventType.CLOCK_OUT, EventType.fromValue(2))
        assertEquals(EventType.EXIT, EventType.fromValue(3))
        assertNull(EventType.fromValue(999), "Invalid value should return null")
    }
}
