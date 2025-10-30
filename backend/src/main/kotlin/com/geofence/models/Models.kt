package com.geofence.models

import kotlinx.serialization.Serializable
import java.util.UUID

/**
 * Data models for the GeoFence API
 * These models are used for API requests/responses and map to database entities
 */

// ==================== Request Models ====================

/**
 * Login request containing employee credentials
 */
@Serializable
data class LoginRequest(
    val username: String,
    val password: String
)

/**
 * Clock-in request with optional location data
 */
@Serializable
data class ClockInRequest(
    val latitude: Double? = null,
    val longitude: Double? = null
)

/**
 * Clock-out request with optional location data
 */
@Serializable
data class ClockOutRequest(
    val latitude: Double? = null,
    val longitude: Double? = null
)

/**
 * Geofence exit event request
 * Includes location and distance from geofence boundary
 */
@Serializable
data class ExitEventRequest(
    val latitude: Double,
    val longitude: Double,
    val distanceFromGeofence: Double
)

// ==================== Response Models ====================

/**
 * Login response containing JWT token
 */
@Serializable
data class LoginResponse(
    val token: String,
    val employeeId: String
)

/**
 * Employee profile response
 */
@Serializable
data class EmployeeResponse(
    val employeeId: String,
    val username: String,
    val email: String
)

/**
 * Geofence data response for Android app
 * Includes request_id for Android Geofencing API
 */
@Serializable
data class GeofenceResponse(
    val geofenceId: String,
    val requestId: String,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val radius: Int
)

/**
 * Schedule event response
 * Represents a single clock-in, clock-out, or exit event
 */
@Serializable
data class ScheduleEventResponse(
    val eventId: String,
    val eventType: String,  // "CLOCK_IN", "CLOCK_OUT", or "EXIT"
    val eventTime: String,  // ISO 8601 format
    val latitude: Double? = null,
    val longitude: Double? = null,
    val durationMinutes: Int? = null,  // For CLOCK_OUT events
    val distanceFromGeofence: Double? = null  // For EXIT events
)

/**
 * Schedule history response containing recent events
 */
@Serializable
data class ScheduleResponse(
    val events: List<ScheduleEventResponse>
)

/**
 * Standard error response
 */
@Serializable
data class ErrorResponse(
    val error: String,
    val message: String
)

/**
 * Standard success response
 */
@Serializable
data class SuccessResponse(
    val message: String,
    val eventId: String? = null
)

/**
 * Event type enum for schedule events
 * Maps to database event_type column (SMALLINT)
 */
enum class EventType(val value: Int, val displayName: String) {
    CLOCK_IN(1, "CLOCK_IN"),
    CLOCK_OUT(2, "CLOCK_OUT"),
    EXIT(3, "EXIT");

    companion object {
        /**
         * Convert numeric value from database to EventType
         * @param value Database event type value
         * @return EventType or null if value doesn't match any type
         */
        fun fromValue(value: Int): EventType? {
            return values().find { it.value == value }
        }
    }
}
