package com.geofence

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.geofence.routes.*
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.http.*
import io.ktor.server.response.*
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.Database

/**
 * Simple GeoFence backend - all config in one place
 */
fun main() {
    embeddedServer(Netty, port = 8080, host = "0.0.0.0") {
        configureApp()
    }.start(wait = true)
}

fun Application.configureApp() {
    // Setup database - simple connection
    initDatabase()

    // Install all plugins
    install(ContentNegotiation) {
        json(Json {
            prettyPrint = true
            isLenient = true
            ignoreUnknownKeys = true
        })
    }

    install(CORS) {
        anyHost()
        allowHeader(HttpHeaders.ContentType)
        allowHeader(HttpHeaders.Authorization)
        allowMethod(HttpMethod.Get)
        allowMethod(HttpMethod.Post)
        allowMethod(HttpMethod.Put)
        allowMethod(HttpMethod.Delete)
        allowMethod(HttpMethod.Options)
    }

    install(StatusPages) {
        exception<Throwable> { call, cause ->
            call.respond(HttpStatusCode.InternalServerError,
                mapOf("error" to "server_error", "message" to cause.message))
        }
    }

    // JWT auth setup
    install(Authentication) {
        jwt("auth-jwt") {
            realm = "GeoFence API"
            verifier(
                JWT.require(Algorithm.HMAC256(JWT_SECRET))
                    .withAudience(JWT_AUDIENCE)
                    .withIssuer(JWT_ISSUER)
                    .build()
            )
            validate { credential ->
                if (credential.payload.audience.contains(JWT_AUDIENCE)) {
                    JWTPrincipal(credential.payload)
                } else null
            }
            challenge { _, _ ->
                call.respond(HttpStatusCode.Unauthorized,
                    mapOf("error" to "unauthorized", "message" to "Invalid token"))
            }
        }
    }

    // Setup routes
    configureRoutes()
}

// Simple database initialization
fun initDatabase() {
    val config = HikariConfig().apply {
        jdbcUrl = System.getenv("DATABASE_URL") ?: "jdbc:postgresql://localhost:5432/geofence_db"
        driverClassName = "org.postgresql.Driver"
        username = System.getenv("DATABASE_USER") ?: "geofence_user"
        password = System.getenv("DATABASE_PASSWORD") ?: "geofence_password"
        maximumPoolSize = 10
        isAutoCommit = false
        transactionIsolation = "TRANSACTION_REPEATABLE_READ"
        validate()
    }
    val dataSource = HikariDataSource(config)
    Database.connect(dataSource)
}
