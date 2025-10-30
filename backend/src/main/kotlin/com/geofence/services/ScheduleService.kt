package com.geofence.services

import com.geofence.data.DatabaseFactory.dbQuery
import com.geofence.data.Geofences
import com.geofence.data.Schedule
import com.geofence.models.EventType
import com.geofence.models.ScheduleEventResponse
import com.geofence.models.ScheduleResponse
import org.jetbrains.exposed.sql.*
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.*

/**
 * Service for managing schedule events (clock-in, clock-out, geofence exits)
 * Handles work hours tracking and event logging
 */
class ScheduleService {

    /**
     * Record a clock-in event for the employee
     * @param employeeId Employee UUID
     * @param latitude Optional GPS latitude
     * @param longitude Optional GPS longitude
     * @return Event ID of created clock-in event
     */
    suspend fun clockIn(
        employeeId: UUID,
        latitude: Double?,
        longitude: Double?
    ): UUID = dbQuery {
        // Get the single geofence
        val geofence = Geofences.selectAll().single()
        val geofenceId = geofence[Geofences.id].value

        // Insert clock-in event
        val eventId = Schedule.insertAndGetId {
            it[Schedule.employeeId] = employeeId
            it[Schedule.geofenceId] = geofenceId
            it[eventType] = EventType.CLOCK_IN.value.toShort()
            it[eventTime] = LocalDateTime.now()
            latitude?.let { lat -> it[Schedule.latitude] = BigDecimal.valueOf(lat) }
            longitude?.let { lng -> it[Schedule.longitude] = BigDecimal.valueOf(lng) }
            it[createdAt] = LocalDateTime.now()
        }

        eventId.value
    }

    /**
     * Record a clock-out event for the employee
     * Automatically calculates duration from most recent clock-in
     * @param employeeId Employee UUID
     * @param latitude Optional GPS latitude
     * @param longitude Optional GPS longitude
     * @return Event ID of created clock-out event
     */
    suspend fun clockOut(
        employeeId: UUID,
        latitude: Double?,
        longitude: Double?
    ): UUID = dbQuery {
        // Get the single geofence
        val geofence = Geofences.selectAll().single()
        val geofenceId = geofence[Geofences.id].value

        // Find most recent clock-in event to calculate duration
        val lastClockIn = Schedule
            .select {
                (Schedule.employeeId eq employeeId) and
                        (Schedule.eventType eq EventType.CLOCK_IN.value.toShort())
            }
            .orderBy(Schedule.eventTime, SortOrder.DESC)
            .firstOrNull()

        // Calculate duration in minutes
        val durationMinutes = lastClockIn?.let {
            val clockInTime = it[Schedule.eventTime]
            val now = LocalDateTime.now()
            ChronoUnit.MINUTES.between(clockInTime, now).toInt()
        }

        // Insert clock-out event
        val eventId = Schedule.insertAndGetId {
            it[Schedule.employeeId] = employeeId
            it[Schedule.geofenceId] = geofenceId
            it[eventType] = EventType.CLOCK_OUT.value.toShort()
            it[eventTime] = LocalDateTime.now()
            latitude?.let { lat -> it[Schedule.latitude] = BigDecimal.valueOf(lat) }
            longitude?.let { lng -> it[Schedule.longitude] = BigDecimal.valueOf(lng) }
            durationMinutes?.let { dur -> it[Schedule.durationMinutes] = dur }
            it[createdAt] = LocalDateTime.now()
        }

        eventId.value
    }

    /**
     * Record a geofence exit event (when employee leaves geofence while clocked in)
     * @param employeeId Employee UUID
     * @param latitude GPS latitude where exit was detected
     * @param longitude GPS longitude where exit was detected
     * @param distanceFromGeofence Distance from geofence boundary in meters
     * @return Event ID of created exit event
     */
    suspend fun recordExit(
        employeeId: UUID,
        latitude: Double,
        longitude: Double,
        distanceFromGeofence: Double
    ): UUID = dbQuery {
        // Get the single geofence
        val geofence = Geofences.selectAll().single()
        val geofenceId = geofence[Geofences.id].value

        // Insert exit event
        val eventId = Schedule.insertAndGetId {
            it[Schedule.employeeId] = employeeId
            it[Schedule.geofenceId] = geofenceId
            it[eventType] = EventType.EXIT.value.toShort()
            it[eventTime] = LocalDateTime.now()
            it[Schedule.latitude] = BigDecimal.valueOf(latitude)
            it[Schedule.longitude] = BigDecimal.valueOf(longitude)
            it[Schedule.distanceFromGeofence] = BigDecimal.valueOf(distanceFromGeofence)
            it[createdAt] = LocalDateTime.now()
        }

        eventId.value
    }

    /**
     * Get recent schedule events for the employee
     * Returns events from the last 7 days
     * @param employeeId Employee UUID
     * @return List of schedule events ordered by time (newest first)
     */
    suspend fun getRecentSchedule(employeeId: UUID): ScheduleResponse = dbQuery {
        val sevenDaysAgo = LocalDateTime.now().minusDays(7)

        val events = Schedule
            .select { (Schedule.employeeId eq employeeId) and (Schedule.eventTime greaterEq sevenDaysAgo) }
            .orderBy(Schedule.eventTime, SortOrder.DESC)
            .map { it.toScheduleEventResponse() }

        ScheduleResponse(events)
    }

    /**
     * Convert database row to ScheduleEventResponse
     */
    private fun ResultRow.toScheduleEventResponse(): ScheduleEventResponse {
        val eventTypeValue = this[Schedule.eventType].toInt()
        val eventType = EventType.fromValue(eventTypeValue)?.displayName ?: "UNKNOWN"

        val eventTime = this[Schedule.eventTime]
        val formatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME
        val eventTimeStr = eventTime.format(formatter) + "Z"

        return ScheduleEventResponse(
            eventId = this[Schedule.id].value.toString(),
            eventType = eventType,
            eventTime = eventTimeStr,
            latitude = this[Schedule.latitude]?.toDouble(),
            longitude = this[Schedule.longitude]?.toDouble(),
            durationMinutes = this[Schedule.durationMinutes],
            distanceFromGeofence = this[Schedule.distanceFromGeofence]?.toDouble()
        )
    }
}
