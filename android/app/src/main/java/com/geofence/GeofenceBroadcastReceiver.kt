package com.geofence

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

class GeofenceBroadcastReceiver : BroadcastReceiver() {

    private val httpClient = OkHttpClient()

    override fun onReceive(context: Context, intent: Intent) {
        val geofencingEvent = GeofencingEvent.fromIntent(intent)

        if (geofencingEvent == null || geofencingEvent.hasError()) {
            return
        }

        val transition = geofencingEvent.geofenceTransition

        when (transition) {
            Geofence.GEOFENCE_TRANSITION_ENTER -> {
                handleEnter(context)
            }
            Geofence.GEOFENCE_TRANSITION_EXIT -> {
                handleExit(context)
            }
        }
    }

    private fun handleEnter(context: Context) {
        Toast.makeText(context, "Entered geofence - Auto clock in", Toast.LENGTH_LONG).show()

        Thread {
            try {
                val prefs = context.getSharedPreferences("geofence_prefs", Context.MODE_PRIVATE)
                val token = prefs.getString("auth_token", null) ?: return@Thread

                val json = """{"latitude":39.9187,"longitude":-75.3876}"""
                val body = json.toRequestBody("application/json".toMediaType())
                val request = Request.Builder()
                    .url("http://10.0.2.2:8080/api/schedule/clock-in")
                    .header("Authorization", "Bearer $token")
                    .post(body)
                    .build()

                httpClient.newCall(request).execute()
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }.start()
    }

    private fun handleExit(context: Context) {
        Toast.makeText(context, "Exited geofence - Auto clock out", Toast.LENGTH_LONG).show()

        Thread {
            try {
                val prefs = context.getSharedPreferences("geofence_prefs", Context.MODE_PRIVATE)
                val token = prefs.getString("auth_token", null) ?: return@Thread

                val json = """{"latitude":39.9187,"longitude":-75.3876}"""
                val body = json.toRequestBody("application/json".toMediaType())
                val request = Request.Builder()
                    .url("http://10.0.2.2:8080/api/schedule/clock-out")
                    .header("Authorization", "Bearer $token")
                    .post(body)
                    .build()

                httpClient.newCall(request).execute()
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }.start()
    }
}
