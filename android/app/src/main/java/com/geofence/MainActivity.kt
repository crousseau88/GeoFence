package com.geofence

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingClient
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices
import com.google.gson.Gson
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

class MainActivity : AppCompatActivity() {

    private lateinit var geofencingClient: GeofencingClient
    private val httpClient = OkHttpClient()
    private val gson = Gson()

    private var isLoggedIn = false
    private var authToken: String? = null

    private lateinit var loginLayout: View
    private lateinit var mainLayout: View
    private lateinit var usernameInput: EditText
    private lateinit var passwordInput: EditText
    private lateinit var loginButton: Button
    private lateinit var statusText: TextView
    private lateinit var clockInButton: Button
    private lateinit var clockOutButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        geofencingClient = LocationServices.getGeofencingClient(this)

        loginLayout = findViewById(R.id.loginLayout)
        mainLayout = findViewById(R.id.mainLayout)
        usernameInput = findViewById(R.id.usernameInput)
        passwordInput = findViewById(R.id.passwordInput)
        loginButton = findViewById(R.id.loginButton)
        statusText = findViewById(R.id.statusText)
        clockInButton = findViewById(R.id.clockInButton)
        clockOutButton = findViewById(R.id.clockOutButton)

        checkSavedLogin()

        loginButton.setOnClickListener {
            val username = usernameInput.text.toString()
            val password = passwordInput.text.toString()
            login(username, password)
        }

        clockInButton.setOnClickListener {
            clockIn()
        }

        clockOutButton.setOnClickListener {
            clockOut()
        }

        requestPermissions()
    }

    private fun checkSavedLogin() {
        val prefs = getSharedPreferences("geofence_prefs", Context.MODE_PRIVATE)
        authToken = prefs.getString("auth_token", null)
        if (authToken != null) {
            isLoggedIn = true
            showMainScreen()
            registerGeofence()
        } else {
            showLoginScreen()
        }
    }

    private fun showLoginScreen() {
        loginLayout.visibility = View.VISIBLE
        mainLayout.visibility = View.GONE
    }

    private fun showMainScreen() {
        loginLayout.visibility = View.GONE
        mainLayout.visibility = View.VISIBLE
        statusText.text = "Waiting for geofence..."
    }

    private fun login(username: String, password: String) {
        Thread {
            try {
                println("GeoFence: Starting login for user: $username")
                val json = """{"username":"$username","password":"$password"}"""
                println("GeoFence: JSON payload: $json")
                val body = json.toRequestBody("application/json".toMediaType())
                val request = Request.Builder()
                    .url("http://10.0.2.2:8080/api/login")
                    .post(body)
                    .build()

                println("GeoFence: Sending request to backend...")
                val response = httpClient.newCall(request).execute()
                val responseBody = response.body?.string()

                println("GeoFence: Response code: ${response.code}")
                println("GeoFence: Response body: $responseBody")

                if (response.isSuccessful && responseBody != null) {
                    val loginResponse = gson.fromJson(responseBody, LoginResponse::class.java)
                    authToken = loginResponse.token
                    println("GeoFence: Login successful, token: ${authToken?.take(20)}...")

                    val prefs = getSharedPreferences("geofence_prefs", Context.MODE_PRIVATE)
                    prefs.edit().putString("auth_token", authToken).apply()

                    runOnUiThread {
                        isLoggedIn = true
                        showMainScreen()
                        registerGeofence()
                        Toast.makeText(this, "Login successful", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    println("GeoFence: Login failed - code: ${response.code}")
                    runOnUiThread {
                        Toast.makeText(this, "Login failed: ${response.code}", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                println("GeoFence: Exception during login: ${e.message}")
                e.printStackTrace()
                runOnUiThread {
                    Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }

    @SuppressLint("MissingPermission")
    private fun registerGeofence() {
        val geofence = Geofence.Builder()
            .setRequestId("media_pa_001")
            .setCircularRegion(39.9187, -75.3876, 100f)
            .setExpirationDuration(Geofence.NEVER_EXPIRE)
            .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER or Geofence.GEOFENCE_TRANSITION_EXIT)
            .build()

        val geofencingRequest = GeofencingRequest.Builder()
            .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER)
            .addGeofence(geofence)
            .build()

        val intent = Intent(this, GeofenceBroadcastReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            geofencingClient.addGeofences(geofencingRequest, pendingIntent)
                .addOnSuccessListener {
                    Toast.makeText(this, "Geofence registered", Toast.LENGTH_SHORT).show()
                }
                .addOnFailureListener {
                    Toast.makeText(this, "Geofence failed: ${it.message}", Toast.LENGTH_SHORT).show()
                }
        }
    }

    private fun clockIn() {
        Thread {
            try {
                val json = """{"latitude":39.9187,"longitude":-75.3876}"""
                val body = json.toRequestBody("application/json".toMediaType())
                val request = Request.Builder()
                    .url("http://10.0.2.2:8080/api/schedule/clock-in")
                    .header("Authorization", "Bearer $authToken")
                    .post(body)
                    .build()

                val response = httpClient.newCall(request).execute()

                runOnUiThread {
                    if (response.isSuccessful) {
                        Toast.makeText(this, "Clocked in", Toast.LENGTH_SHORT).show()
                        statusText.text = "Status: Clocked In"
                    } else {
                        Toast.makeText(this, "Clock-in failed", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: IOException) {
                runOnUiThread {
                    Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
    }

    private fun clockOut() {
        Thread {
            try {
                val json = """{"latitude":39.9187,"longitude":-75.3876}"""
                val body = json.toRequestBody("application/json".toMediaType())
                val request = Request.Builder()
                    .url("http://10.0.2.2:8080/api/schedule/clock-out")
                    .header("Authorization", "Bearer $authToken")
                    .post(body)
                    .build()

                val response = httpClient.newCall(request).execute()

                runOnUiThread {
                    if (response.isSuccessful) {
                        Toast.makeText(this, "Clocked out", Toast.LENGTH_SHORT).show()
                        statusText.text = "Status: Clocked Out"
                    } else {
                        Toast.makeText(this, "Clock-out failed", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: IOException) {
                runOnUiThread {
                    Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
    }

    fun updateStatus(status: String) {
        runOnUiThread {
            statusText.text = "Status: $status"
        }
    }

    private fun requestPermissions() {
        val permissions = arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_BACKGROUND_LOCATION
        )
        ActivityCompat.requestPermissions(this, permissions, 100)
    }

    data class LoginResponse(val token: String)
}
