#!/bin/bash

# GeoFence Database Setup Script
# This script automates the complete setup of the GeoFence PostgreSQL database
# It creates the database, user, schema, and populates it with sample data
#
# Prerequisites:
#   - PostgreSQL must be installed and running
#   - Current user must have sudo access to run psql as postgres user
#   - schema.sql and seed_data.sql files must exist in the same directory
#
# Usage: ./setup.sh

set -e  # Exit immediately if any command returns a non-zero status

# Configuration variables
# Modify these values to match your desired database setup
DB_NAME="geofence_db"              # Name of the database to create
DB_USER="geofence_user"            # Database user for the application
DB_PASS="geofence_password"        # Password for the database user (change in production!)
DB_HOST="localhost"                # Database server hostname
DB_PORT="5432"                     # PostgreSQL port (default is 5432)

echo "Setting up GeoFence database..."

# Check if PostgreSQL is running and accepting connections
# pg_isready returns 0 if server is accepting connections, non-zero otherwise
if ! pg_isready -h $DB_HOST -p $DB_PORT > /dev/null 2>&1; then
    echo "Error: PostgreSQL is not running on $DB_HOST:$DB_PORT"
    echo "Please start PostgreSQL service first."
    echo "  - On macOS with Homebrew: brew services start postgresql"
    echo "  - On Linux with systemd: sudo systemctl start postgresql"
    exit 1
fi

# Create database and user (run as postgres superuser)
# This section must run with superuser privileges to create roles and databases
echo "Creating database and user..."
sudo -u postgres psql << EOF
-- Create application user if it doesn't already exist
-- This user will own the database and be used by the application to connect
DO \$\$
BEGIN
    IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = '$DB_USER') THEN
        CREATE ROLE $DB_USER WITH LOGIN PASSWORD '$DB_PASS';
        RAISE NOTICE 'Created role: $DB_USER';
    ELSE
        RAISE NOTICE 'Role already exists: $DB_USER';
    END IF;
END
\$\$;

-- Create database if it doesn't already exist
-- The \gexec executes the generated CREATE DATABASE statement
SELECT 'CREATE DATABASE $DB_NAME'
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = '$DB_NAME')\gexec

-- Grant all privileges on the database to the application user
-- This allows the user to create tables, indexes, functions, etc.
GRANT ALL PRIVILEGES ON DATABASE $DB_NAME TO $DB_USER;
EOF

# Run schema creation script
# This creates all tables, indexes, triggers, and functions defined in schema.sql
# PGPASSWORD environment variable allows passwordless authentication for this command
echo "Creating database schema..."
PGPASSWORD=$DB_PASS psql -h $DB_HOST -p $DB_PORT -U $DB_USER -d $DB_NAME -f schema.sql

# Insert seed data for testing and development
# This populates the database with sample employees, geofences, shifts, and logs
echo "Inserting seed data..."
PGPASSWORD=$DB_PASS psql -h $DB_HOST -p $DB_PORT -U $DB_USER -d $DB_NAME -f seed_data.sql

# Display success message and connection information
echo "Database setup completed successfully!"
echo ""
echo "Connection details:"
echo "  Database: $DB_NAME"
echo "  User: $DB_USER"
echo "  Password: $DB_PASS"
echo "  Host: $DB_HOST"
echo "  Port: $DB_PORT"
echo ""
echo "Connection string: postgresql://$DB_USER:$DB_PASS@$DB_HOST:$DB_PORT/$DB_NAME"
echo ""
echo "You can now connect to the database using:"
echo "  psql -h $DB_HOST -p $DB_PORT -U $DB_USER -d $DB_NAME"