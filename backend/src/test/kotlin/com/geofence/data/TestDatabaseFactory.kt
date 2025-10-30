package com.geofence.data

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.deleteAll
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction
import org.mindrot.jbcrypt.BCrypt
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.*

/**
 * Test database factory for setting up H2 in-memory database for unit tests
 * Uses H2 to simulate PostgreSQL without requiring a full database server
 */
object TestDatabaseFactory {

    private var dataSource: HikariDataSource? = null

    /**
     * Initialize H2 in-memory database for testing
     */
    fun init() {
        val config = HikariConfig().apply {
            jdbcUrl = "jdbc:h2:mem:test;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH"
            driverClassName = "org.h2.Driver"
            maximumPoolSize = 3
            isAutoCommit = false
            transactionIsolation = "TRANSACTION_REPEATABLE_READ"
            validate()
        }

        dataSource = HikariDataSource(config)
        Database.connect(dataSource!!)

        // Create tables
        transaction {
            SchemaUtils.create(Employees, Geofences, Schedule)
        }
    }

    /**
     * Execute a database query within a transaction
     */
    suspend fun <T> dbQuery(block: () -> T): T = transaction {
        block()
    }

    /**
     * Clean up the database after tests
     */
    fun cleanup() {
        transaction {
            SchemaUtils.drop(Schedule, Geofences, Employees)
        }
        dataSource?.close()
        dataSource = null
    }

    /**
     * Seed test data for a standard test scenario
     * Creates test employee and geofence
     */
    fun seedTestData(): TestData {
        val employeeId = UUID.randomUUID()
        val geofenceId = UUID.randomUUID()
        val passwordHash = BCrypt.hashpw("password123", BCrypt.gensalt())

        transaction {
            // Insert test employee
            Employees.insert {
                it[id] = employeeId
                it[username] = "testuser"
                it[password] = passwordHash
                it[email] = "test@example.com"
                it[createdAt] = LocalDateTime.now()
            }

            // Insert test geofence (Media, PA location)
            Geofences.insert {
                it[id] = geofenceId
                it[requestId] = "MEDIA_OFFICE"
                it[name] = "Media Office"
                it[radius] = 100
                it[createdAt] = LocalDateTime.now()
            }
        }

        return TestData(
            employeeId = employeeId,
            username = "testuser",
            password = "password123",
            email = "test@example.com",
            geofenceId = geofenceId,
            geofenceName = "Media Office",
            geofenceRadius = 100,
            latitude = 39.9187,
            longitude = -75.3876
        )
    }

    /**
     * Clear all data from tables without dropping schema
     */
    fun clearData() {
        transaction {
            Schedule.deleteAll()
            Geofences.deleteAll()
            Employees.deleteAll()
        }
    }
}

/**
 * Test data container for seeded data
 */
data class TestData(
    val employeeId: UUID,
    val username: String,
    val password: String,
    val email: String,
    val geofenceId: UUID,
    val geofenceName: String,
    val geofenceRadius: Int,
    val latitude: Double,
    val longitude: Double
)
