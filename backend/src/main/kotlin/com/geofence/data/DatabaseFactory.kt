package com.geofence.data

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.transactions.transaction

/**
 * Database connection factory using HikariCP connection pool
 * Manages PostgreSQL database connections for the application
 */
object DatabaseFactory {

    /**
     * Initialize database connection pool and connect to PostgreSQL
     * Uses environment variables for configuration
     */
    fun init() {
        val config = HikariConfig().apply {
            // Database connection settings from environment variables
            jdbcUrl = System.getenv("DATABASE_URL")
                ?: "jdbc:postgresql://localhost:5432/geofence_db"
            driverClassName = "org.postgresql.Driver"
            username = System.getenv("DATABASE_USER") ?: "geofence_user"
            password = System.getenv("DATABASE_PASSWORD") ?: "geofence_password"

            // Connection pool settings
            maximumPoolSize = 10
            minimumIdle = 2
            idleTimeout = 300000  // 5 minutes
            connectionTimeout = 30000  // 30 seconds
            maxLifetime = 1800000  // 30 minutes

            // Validation
            isAutoCommit = false
            transactionIsolation = "TRANSACTION_REPEATABLE_READ"
            validate()
        }

        val dataSource = HikariDataSource(config)
        Database.connect(dataSource)
    }

    /**
     * Execute a database query within a transaction
     * Provides automatic transaction management
     */
    suspend fun <T> dbQuery(block: () -> T): T {
        return transaction {
            block()
        }
    }
}
