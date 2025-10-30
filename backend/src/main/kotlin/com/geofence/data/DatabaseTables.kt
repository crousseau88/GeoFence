package com.geofence.data

import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.javatime.datetime

/**
 * Exposed ORM table definitions for the GeoFence database
 * These map directly to the PostgreSQL schema tables
 */

/**
 * Employees table
 * Stores basic employee information with bcrypt hashed passwords
 */
object Employees : UUIDTable("employees", "employee_id") {
    val username = varchar("username", 50).uniqueIndex()
    val password = varchar("password", 255)  // Bcrypt hash
    val email = varchar("email", 100).uniqueIndex()
    val createdAt = datetime("created_at")
}

/**
 * Geofences table
 * Stores geofence location data with separate lat/lon columns
 */
object Geofences : UUIDTable("geofences", "geofence_id") {
    val requestId = varchar("request_id", 100).uniqueIndex()
    val name = varchar("name", 100)
    val latitude = double("latitude")
    val longitude = double("longitude")
    val radius = integer("radius")
    val createdAt = datetime("created_at")
}

/**
 * Schedule table
 * Tracks clock-in, clock-out, and geofence exit events
 * event_type: 1=CLOCK_IN, 2=CLOCK_OUT, 3=EXIT
 */
object Schedule : UUIDTable("schedule", "event_id") {
    val employeeId = reference("employee_id", Employees)
    val geofenceId = reference("geofence_id", Geofences)
    val eventType = short("event_type")  // 1, 2, or 3
    val eventTime = datetime("event_time")
    val latitude = decimal("latitude", 10, 8).nullable()
    val longitude = decimal("longitude", 11, 8).nullable()
    val durationMinutes = integer("duration_minutes").nullable()
    val distanceFromGeofence = decimal("distance_from_geofence", 10, 2).nullable()
    val createdAt = datetime("created_at")
}
