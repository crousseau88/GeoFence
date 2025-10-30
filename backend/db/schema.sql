-- GeoFence POC Database Schema
-- Minimal schema for single-employee, single-geofence time-tracking system
-- Location: WHG3+H5, Media, Pennsylvania (lat: 39.9187, lng: -75.3876)

-- Enable PostGIS extension for geospatial support
CREATE EXTENSION IF NOT EXISTS postgis;

-- Drop existing tables if they exist (for clean setup)
DROP TABLE IF EXISTS schedule CASCADE;
DROP TABLE IF EXISTS geofences CASCADE;
DROP TABLE IF EXISTS employees CASCADE;

-- Create Employees table
-- Stores basic details for a single employee
CREATE TABLE employees (
    employee_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),    -- Unique identifier
    username VARCHAR(50) UNIQUE NOT NULL,                      -- Login username
    password VARCHAR(255) NOT NULL,                            -- Bcrypt hashed password
    email VARCHAR(100) UNIQUE NOT NULL,                        -- Employee email
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP             -- Record creation time
);

-- Create Geofences table
-- Stores geofence data with PostGIS POINT for location
CREATE TABLE geofences (
    geofence_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),    -- Unique identifier
    request_id VARCHAR(100) UNIQUE NOT NULL,                   -- Android Geofencing API request ID
    name VARCHAR(100) NOT NULL,                                -- Geofence name
    location GEOGRAPHY(POINT, 4326) NOT NULL,                  -- PostGIS POINT (lat, lng) with SRID 4326
    radius INTEGER NOT NULL CHECK (radius >= 100),             -- Radius in meters (minimum 100m)
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP             -- Record creation time
);

-- Create Schedule table
-- Tracks clock-in, clock-out, and geofence exit events
CREATE TABLE schedule (
    event_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),       -- Unique event identifier
    employee_id UUID NOT NULL REFERENCES employees(employee_id) ON DELETE CASCADE,  -- Employee reference
    geofence_id UUID NOT NULL REFERENCES geofences(geofence_id) ON DELETE CASCADE,  -- Geofence reference
    event_type SMALLINT NOT NULL CHECK (event_type IN (1, 2, 3)),  -- 1=CLOCK_IN, 2=CLOCK_OUT, 3=EXIT
    event_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,   -- Event timestamp
    latitude DECIMAL(10, 8),                                   -- Optional GPS latitude
    longitude DECIMAL(11, 8),                                  -- Optional GPS longitude
    duration_minutes INTEGER,                                  -- Work duration in minutes (for CLOCK_OUT)
    distance_from_geofence DECIMAL(10, 2),                    -- Distance in meters (for EXIT)
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP             -- Record creation time
);

-- Create indexes for performance
CREATE INDEX idx_geofences_location ON geofences USING GIST(location);  -- Spatial index for geofence queries
CREATE INDEX idx_schedule_employee ON schedule(employee_id);            -- Fast lookup by employee
CREATE INDEX idx_schedule_geofence ON schedule(geofence_id);            -- Fast lookup by geofence
CREATE INDEX idx_schedule_time ON schedule(event_time DESC);            -- Fast time-based queries

-- Insert sample employee
-- Password: "password123" (bcrypt hashed with cost factor 12)
INSERT INTO employees (username, password, email) VALUES
('john_doe', '$2a$12$LQv3c1yqBWVHxkd0LHAkCOYz6TtxMQJqhN8/LeUY.rEiJmhUnJb3.', 'john@example.com');

-- Insert sample geofence at WHG3+H5, Media, PA
-- Location: lat 39.9187, lng -75.3876, radius 100 meters
-- request_id: "media_pa_geofence" for Android Geofencing API
INSERT INTO geofences (request_id, name, location, radius) VALUES
('media_pa_geofence', 'Media PA Office', ST_SetSRID(ST_MakePoint(-75.3876, 39.9187), 4326)::geography, 100);

-- Verification queries
-- View created tables
SELECT 'Tables created successfully' AS status;
SELECT table_name FROM information_schema.tables
WHERE table_schema = 'public' AND table_type = 'BASE TABLE'
ORDER BY table_name;

-- View employee data
SELECT employee_id, username, email, created_at FROM employees;

-- View geofence data (with location as lat/lng)
SELECT
    geofence_id,
    request_id,
    name,
    ST_Y(location::geometry) AS latitude,
    ST_X(location::geometry) AS longitude,
    radius,
    created_at
FROM geofences;
