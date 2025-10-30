package com.geofence.services

import com.geofence.data.DatabaseFactory.dbQuery
import com.geofence.data.Geofences
import com.geofence.models.GeofenceResponse
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.selectAll

/**
 * Service for managing geofence data
 * Handles retrieval of geofence information for the Android app
 */
class GeofenceService {

    /**
     * Get the single geofence for the POC system
     * Returns geofence data including location and radius for Android Geofencing API
     */
    suspend fun getGeofence(): GeofenceResponse? = dbQuery {
        // Query the single geofence (Media, PA location)
        val result = Geofences.selectAll().singleOrNull() ?: return@dbQuery null

        // Return geofence with lat/lng from separate columns
        GeofenceResponse(
            geofenceId = result[Geofences.id].value.toString(),
            requestId = result[Geofences.requestId],
            name = result[Geofences.name],
            latitude = result[Geofences.latitude],
            longitude = result[Geofences.longitude],
            radius = result[Geofences.radius]
        )
    }
}
