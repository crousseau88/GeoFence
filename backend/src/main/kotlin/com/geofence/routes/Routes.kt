package com.geofence.routes

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.geofence.data.*
import com.geofence.models.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import org.mindrot.jbcrypt.BCrypt
import java.math.BigDecimal
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.*

// Simple JWT configuration
val JWT_SECRET = System.getenv("JWT_SECRET") ?: "geofence-secret-key"
val JWT_ISSUER = System.getenv("JWT_ISSUER") ?: "geofence-backend"
val JWT_AUDIENCE = System.getenv("JWT_AUDIENCE") ?: "geofence-api"

/**
 * All API routes - simplified version with business logic inline
 */
fun Application.configureRoutes() {
    routing {
        // Login endpoint - no auth required
        route("/api") {
            post("/login") {
                try {
                    val request = call.receive<LoginRequest>()

                    if (request.username.isBlank() || request.password.isBlank()) {
                        call.respond(HttpStatusCode.BadRequest,
                            ErrorResponse("bad_request", "Username and password required"))
                        return@post
                    }

                    // Query database directly
                    val result = transaction {
                        val employee = Employees.select { Employees.username eq request.username }
                            .singleOrNull()

                        if (employee == null) {
                            return@transaction null
                        }

                        // Check password
                        if (!BCrypt.checkpw(request.password, employee[Employees.password])) {
                            return@transaction null
                        }

                        // Generate token
                        val employeeId = employee[Employees.id].value
                        val token = JWT.create()
                            .withAudience(JWT_AUDIENCE)
                            .withIssuer(JWT_ISSUER)
                            .withClaim("employeeId", employeeId.toString())
                            .withExpiresAt(Date(System.currentTimeMillis() + 7 * 24 * 60 * 60 * 1000L))
                            .sign(Algorithm.HMAC256(JWT_SECRET))

                        LoginResponse(token, employeeId.toString())
                    }

                    if (result != null) {
                        call.respond(HttpStatusCode.OK, result)
                    } else {
                        call.respond(HttpStatusCode.Unauthorized,
                            ErrorResponse("unauthorized", "Invalid credentials"))
                    }
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.InternalServerError,
                        ErrorResponse("server_error", e.message ?: "Error"))
                }
            }
        }

        // Protected endpoints - require JWT
        authenticate("auth-jwt") {
            route("/api") {
                // Get employee profile
                get("/employee") {
                    try {
                        val principal = call.principal<JWTPrincipal>()
                        val employeeIdStr = principal?.payload?.getClaim("employeeId")?.asString()

                        if (employeeIdStr == null) {
                            call.respond(HttpStatusCode.Unauthorized,
                                ErrorResponse("unauthorized", "Invalid token"))
                            return@get
                        }

                        val employeeId = UUID.fromString(employeeIdStr)
                        val employee = transaction {
                            Employees.select { Employees.id eq employeeId }
                                .singleOrNull()
                                ?.let {
                                    EmployeeResponse(
                                        it[Employees.id].value.toString(),
                                        it[Employees.username],
                                        it[Employees.email]
                                    )
                                }
                        }

                        if (employee != null) {
                            call.respond(HttpStatusCode.OK, employee)
                        } else {
                            call.respond(HttpStatusCode.NotFound,
                                ErrorResponse("not_found", "Employee not found"))
                        }
                    } catch (e: Exception) {
                        call.respond(HttpStatusCode.InternalServerError,
                            ErrorResponse("server_error", e.message ?: "Error"))
                    }
                }

                // Get geofence
                get("/geofence") {
                    try {
                        val geofence = transaction {
                            Geofences.selectAll().singleOrNull()?.let {
                                GeofenceResponse(
                                    it[Geofences.id].value.toString(),
                                    it[Geofences.requestId],
                                    it[Geofences.name],
                                    it[Geofences.latitude],
                                    it[Geofences.longitude],
                                    it[Geofences.radius]
                                )
                            }
                        }

                        if (geofence != null) {
                            call.respond(HttpStatusCode.OK, geofence)
                        } else {
                            call.respond(HttpStatusCode.NotFound,
                                ErrorResponse("not_found", "Geofence not found"))
                        }
                    } catch (e: Exception) {
                        call.respond(HttpStatusCode.InternalServerError,
                            ErrorResponse("server_error", e.message ?: "Error"))
                    }
                }

                // Clock in
                post("/schedule/clock-in") {
                    try {
                        val principal = call.principal<JWTPrincipal>()
                        val employeeIdStr = principal?.payload?.getClaim("employeeId")?.asString()
                            ?: return@post call.respond(HttpStatusCode.Unauthorized,
                                ErrorResponse("unauthorized", "Invalid token"))

                        val employeeId = UUID.fromString(employeeIdStr)
                        val request = call.receive<ClockInRequest>()

                        val eventId = transaction {
                            val geofence = Geofences.selectAll().single()
                            val geofenceId = geofence[Geofences.id].value

                            Schedule.insertAndGetId {
                                it[Schedule.employeeId] = employeeId
                                it[Schedule.geofenceId] = geofenceId
                                it[eventType] = 1 // CLOCK_IN
                                it[eventTime] = LocalDateTime.now()
                                request.latitude?.let { lat -> it[Schedule.latitude] = BigDecimal.valueOf(lat) }
                                request.longitude?.let { lng -> it[Schedule.longitude] = BigDecimal.valueOf(lng) }
                                it[createdAt] = LocalDateTime.now()
                            }.value
                        }

                        call.respond(HttpStatusCode.Created,
                            SuccessResponse("Clock-in recorded", eventId.toString()))
                    } catch (e: Exception) {
                        call.respond(HttpStatusCode.InternalServerError,
                            ErrorResponse("server_error", e.message ?: "Error"))
                    }
                }

                // Clock out
                post("/schedule/clock-out") {
                    try {
                        val principal = call.principal<JWTPrincipal>()
                        val employeeIdStr = principal?.payload?.getClaim("employeeId")?.asString()
                            ?: return@post call.respond(HttpStatusCode.Unauthorized,
                                ErrorResponse("unauthorized", "Invalid token"))

                        val employeeId = UUID.fromString(employeeIdStr)
                        val request = call.receive<ClockOutRequest>()

                        val eventId = transaction {
                            val geofence = Geofences.selectAll().single()
                            val geofenceId = geofence[Geofences.id].value

                            // Find last clock-in to calculate duration
                            val lastClockIn = Schedule
                                .select { (Schedule.employeeId eq employeeId) and
                                         (Schedule.eventType eq 1.toShort()) }
                                .orderBy(Schedule.eventTime, SortOrder.DESC)
                                .firstOrNull()

                            val durationMinutes = lastClockIn?.let {
                                ChronoUnit.MINUTES.between(it[Schedule.eventTime], LocalDateTime.now()).toInt()
                            }

                            Schedule.insertAndGetId {
                                it[Schedule.employeeId] = employeeId
                                it[Schedule.geofenceId] = geofenceId
                                it[eventType] = 2 // CLOCK_OUT
                                it[eventTime] = LocalDateTime.now()
                                request.latitude?.let { lat -> it[Schedule.latitude] = BigDecimal.valueOf(lat) }
                                request.longitude?.let { lng -> it[Schedule.longitude] = BigDecimal.valueOf(lng) }
                                durationMinutes?.let { dur -> it[Schedule.durationMinutes] = dur }
                                it[createdAt] = LocalDateTime.now()
                            }.value
                        }

                        call.respond(HttpStatusCode.Created,
                            SuccessResponse("Clock-out recorded", eventId.toString()))
                    } catch (e: Exception) {
                        call.respond(HttpStatusCode.InternalServerError,
                            ErrorResponse("server_error", e.message ?: "Error"))
                    }
                }

                // Exit event
                post("/schedule/exit") {
                    try {
                        val principal = call.principal<JWTPrincipal>()
                        val employeeIdStr = principal?.payload?.getClaim("employeeId")?.asString()
                            ?: return@post call.respond(HttpStatusCode.Unauthorized,
                                ErrorResponse("unauthorized", "Invalid token"))

                        val employeeId = UUID.fromString(employeeIdStr)
                        val request = call.receive<ExitEventRequest>()

                        if (request.distanceFromGeofence < 0) {
                            call.respond(HttpStatusCode.BadRequest,
                                ErrorResponse("bad_request", "Distance must be positive"))
                            return@post
                        }

                        val eventId = transaction {
                            val geofence = Geofences.selectAll().single()
                            val geofenceId = geofence[Geofences.id].value

                            Schedule.insertAndGetId {
                                it[Schedule.employeeId] = employeeId
                                it[Schedule.geofenceId] = geofenceId
                                it[eventType] = 3 // EXIT
                                it[eventTime] = LocalDateTime.now()
                                it[Schedule.latitude] = BigDecimal.valueOf(request.latitude)
                                it[Schedule.longitude] = BigDecimal.valueOf(request.longitude)
                                it[Schedule.distanceFromGeofence] = BigDecimal.valueOf(request.distanceFromGeofence)
                                it[createdAt] = LocalDateTime.now()
                            }.value
                        }

                        call.respond(HttpStatusCode.Created,
                            SuccessResponse("Exit recorded", eventId.toString()))
                    } catch (e: Exception) {
                        call.respond(HttpStatusCode.InternalServerError,
                            ErrorResponse("server_error", e.message ?: "Error"))
                    }
                }

                // Get schedule history
                get("/schedule") {
                    try {
                        val principal = call.principal<JWTPrincipal>()
                        val employeeIdStr = principal?.payload?.getClaim("employeeId")?.asString()
                            ?: return@get call.respond(HttpStatusCode.Unauthorized,
                                ErrorResponse("unauthorized", "Invalid token"))

                        val employeeId = UUID.fromString(employeeIdStr)

                        val events = transaction {
                            val sevenDaysAgo = LocalDateTime.now().minusDays(7)

                            Schedule.select {
                                (Schedule.employeeId eq employeeId) and
                                (Schedule.eventTime greaterEq sevenDaysAgo)
                            }
                            .orderBy(Schedule.eventTime, SortOrder.DESC)
                            .map { row ->
                                val eventTypeValue = row[Schedule.eventType].toInt()
                                val eventTypeName = when(eventTypeValue) {
                                    1 -> "CLOCK_IN"
                                    2 -> "CLOCK_OUT"
                                    3 -> "EXIT"
                                    else -> "UNKNOWN"
                                }

                                val formatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME
                                val eventTimeStr = row[Schedule.eventTime].format(formatter) + "Z"

                                ScheduleEventResponse(
                                    row[Schedule.id].value.toString(),
                                    eventTypeName,
                                    eventTimeStr,
                                    row[Schedule.latitude]?.toDouble(),
                                    row[Schedule.longitude]?.toDouble(),
                                    row[Schedule.durationMinutes],
                                    row[Schedule.distanceFromGeofence]?.toDouble()
                                )
                            }
                        }

                        call.respond(HttpStatusCode.OK, ScheduleResponse(events))
                    } catch (e: Exception) {
                        call.respond(HttpStatusCode.InternalServerError,
                            ErrorResponse("server_error", e.message ?: "Error"))
                    }
                }
            }
        }
    }
}
