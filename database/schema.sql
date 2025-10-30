-- GeoFence Time Tracking Database Schema
-- PostgreSQL implementation

-- Drop tables if they exist (for clean setup)
DROP TABLE IF EXISTS location_logs;
DROP TABLE IF EXISTS breaks;
DROP TABLE IF EXISTS shifts;
DROP TABLE IF EXISTS geofences;
DROP TABLE IF EXISTS employees;

-- Create Employees table
-- Stores employee/worker information for the time tracking system
-- Each employee has a unique username and can clock in/out at geofenced locations
CREATE TABLE employees (
    employee_id SERIAL PRIMARY KEY,           -- Unique identifier for each employee
    username VARCHAR(50) UNIQUE NOT NULL,     -- Login username, must be unique
    full_name VARCHAR(100) NOT NULL,          -- Employee's full name for display purposes
    password_hash VARCHAR(255) NOT NULL,      -- Hashed password for authentication (bcrypt format)
    email VARCHAR(100) UNIQUE,                -- Employee's email address, must be unique
    phone VARCHAR(20),                        -- Employee's phone number for contact
    is_active BOOLEAN DEFAULT TRUE,           -- Flag to soft-delete employees without removing records
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP  -- Timestamp when employee record was created
);

-- Create Geofences table
-- Defines geographic boundaries (circular regions) where employees can clock in/out
-- Each geofence represents a job site, office, or work location
CREATE TABLE geofences (
    geofence_id SERIAL PRIMARY KEY,           -- Unique identifier for each geofence
    name VARCHAR(100) NOT NULL,               -- Name of the location (e.g., "Downtown Office")
    description TEXT,                         -- Optional detailed description of the location
    latitude DECIMAL(10, 8) NOT NULL,         -- Center point latitude (e.g., 40.758896)
    longitude DECIMAL(11, 8) NOT NULL,        -- Center point longitude (e.g., -73.985130)
    radius_meters INTEGER NOT NULL CHECK (radius_meters > 0),  -- Radius in meters from center point
    is_active BOOLEAN DEFAULT TRUE,           -- Flag to enable/disable geofence without deleting
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,  -- Timestamp when geofence was created
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP   -- Timestamp when geofence was last modified
);

-- Create Shifts table
-- Tracks work sessions when employees clock in and out at geofenced locations
-- Automatically calculates total time worked excluding breaks via trigger
CREATE TABLE shifts (
    shift_id SERIAL PRIMARY KEY,              -- Unique identifier for each shift
    employee_id INTEGER NOT NULL REFERENCES employees(employee_id) ON DELETE CASCADE,  -- Employee working this shift
    geofence_id INTEGER NOT NULL REFERENCES geofences(geofence_id) ON DELETE CASCADE,  -- Location where shift occurs
    clock_in TIMESTAMP NOT NULL,              -- When employee started their shift
    clock_out TIMESTAMP,                      -- When employee ended their shift (NULL if still active)
    total_minutes_worked INTEGER,             -- Total work time in minutes (calculated automatically by trigger)
    clock_in_latitude DECIMAL(10, 8),         -- GPS latitude where employee clocked in
    clock_in_longitude DECIMAL(11, 8),        -- GPS longitude where employee clocked in
    clock_out_latitude DECIMAL(10, 8),        -- GPS latitude where employee clocked out
    clock_out_longitude DECIMAL(11, 8),       -- GPS longitude where employee clocked out
    status VARCHAR(20) DEFAULT 'active' CHECK (status IN ('active', 'completed', 'cancelled')),  -- Shift status
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,   -- When this record was created
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,   -- When this record was last updated

    -- Ensure clock_out is after clock_in (data integrity constraint)
    CONSTRAINT valid_shift_times CHECK (clock_out IS NULL OR clock_out > clock_in)
);

-- Create Breaks table
-- Records breaks and lunch periods taken during shifts
-- Break time is automatically subtracted from total_minutes_worked in shifts
CREATE TABLE breaks (
    break_id SERIAL PRIMARY KEY,              -- Unique identifier for each break
    shift_id INTEGER NOT NULL REFERENCES shifts(shift_id) ON DELETE CASCADE,  -- Which shift this break belongs to
    break_type VARCHAR(10) NOT NULL CHECK (break_type IN ('break', 'lunch')),  -- Type: short break or lunch
    start_time TIMESTAMP NOT NULL,            -- When the break started
    end_time TIMESTAMP,                       -- When the break ended (NULL if still on break)
    total_minutes INTEGER,                    -- Duration in minutes (calculated automatically by trigger)
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,   -- When this record was created
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,   -- When this record was last updated

    -- Ensure end_time is after start_time (data integrity constraint)
    CONSTRAINT valid_break_times CHECK (end_time IS NULL OR end_time > start_time)
);

-- Create Location Logs table
-- Maintains an audit trail of all geofence entry and exit events
-- Useful for compliance, tracking movement patterns, and debugging
CREATE TABLE location_logs (
    log_id SERIAL PRIMARY KEY,               -- Unique identifier for each log entry
    employee_id INTEGER NOT NULL REFERENCES employees(employee_id) ON DELETE CASCADE,  -- Employee who triggered the event
    geofence_id INTEGER NOT NULL REFERENCES geofences(geofence_id) ON DELETE CASCADE,  -- Geofence that was entered/exited
    event_type VARCHAR(10) NOT NULL CHECK (event_type IN ('enter', 'exit')),  -- Type of event: enter or exit
    event_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,  -- When the event occurred
    latitude DECIMAL(10, 8) NOT NULL,         -- GPS latitude at the time of event
    longitude DECIMAL(11, 8) NOT NULL,        -- GPS longitude at the time of event
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP  -- When this record was created in the database
);

-- Create indexes for better query performance
-- These indexes speed up common queries and foreign key lookups
CREATE INDEX idx_employees_username ON employees(username);        -- Fast username lookups for login
CREATE INDEX idx_employees_active ON employees(is_active);         -- Filter active employees efficiently
CREATE INDEX idx_geofences_active ON geofences(is_active);         -- Filter active geofences efficiently
CREATE INDEX idx_shifts_employee ON shifts(employee_id);           -- Fast lookup of shifts by employee
CREATE INDEX idx_shifts_geofence ON shifts(geofence_id);           -- Fast lookup of shifts by location
CREATE INDEX idx_shifts_status ON shifts(status);                  -- Filter shifts by status (active/completed)
CREATE INDEX idx_shifts_clock_in ON shifts(clock_in);              -- Date range queries on shifts
CREATE INDEX idx_breaks_shift ON breaks(shift_id);                 -- Fast lookup of breaks for a shift
CREATE INDEX idx_breaks_type ON breaks(break_type);                -- Filter by break type
CREATE INDEX idx_location_logs_employee ON location_logs(employee_id);  -- Fast lookup of logs by employee
CREATE INDEX idx_location_logs_geofence ON location_logs(geofence_id);  -- Fast lookup of logs by geofence
CREATE INDEX idx_location_logs_event_time ON location_logs(event_time); -- Date range queries on logs

-- Create trigger function to automatically calculate total work time for a shift
-- This function runs whenever a shift is updated (typically when clocking out)
-- It calculates: (clock_out - clock_in) - total_break_time = total_minutes_worked
CREATE OR REPLACE FUNCTION update_shift_total_minutes()
RETURNS TRIGGER AS $$
BEGIN
    -- Only calculate if employee has clocked out
    IF NEW.clock_out IS NOT NULL THEN
        -- Calculate gross minutes worked (clock_out - clock_in)
        NEW.total_minutes_worked := EXTRACT(EPOCH FROM (NEW.clock_out - NEW.clock_in)) / 60;

        -- Subtract all completed break time from this shift
        NEW.total_minutes_worked := NEW.total_minutes_worked - COALESCE(
            (SELECT SUM(total_minutes) FROM breaks WHERE shift_id = NEW.shift_id AND total_minutes IS NOT NULL),
            0
        );

        -- Mark shift as completed when clocked out
        NEW.status := 'completed';
    END IF;

    -- Update the timestamp to track when this record was last modified
    NEW.updated_at := CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Attach the trigger to the shifts table
-- Fires before each UPDATE operation on a shift row
CREATE TRIGGER trigger_update_shift_total_minutes
    BEFORE UPDATE ON shifts
    FOR EACH ROW
    EXECUTE FUNCTION update_shift_total_minutes();

-- Create trigger function to automatically calculate break duration
-- This function runs whenever a break is updated (typically when ending a break)
-- It calculates: (end_time - start_time) = total_minutes
CREATE OR REPLACE FUNCTION update_break_total_minutes()
RETURNS TRIGGER AS $$
BEGIN
    -- Only calculate if break has ended
    IF NEW.end_time IS NOT NULL THEN
        -- Calculate break duration in minutes
        NEW.total_minutes := EXTRACT(EPOCH FROM (NEW.end_time - NEW.start_time)) / 60;
    END IF;

    -- Update the timestamp to track when this record was last modified
    NEW.updated_at := CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Attach the trigger to the breaks table
-- Fires before each UPDATE operation on a break row
CREATE TRIGGER trigger_update_break_total_minutes
    BEFORE UPDATE ON breaks
    FOR EACH ROW
    EXECUTE FUNCTION update_break_total_minutes();

-- Create utility function to validate if a GPS location is within a geofence boundary
-- Uses the Haversine formula to calculate great-circle distance between two points on Earth
-- Parameters:
--   lat, lng: The GPS coordinates to check
--   geofence_lat, geofence_lng: The center point of the geofence
--   radius_meters: The radius of the geofence in meters
-- Returns: TRUE if the location is within the geofence, FALSE otherwise
CREATE OR REPLACE FUNCTION is_within_geofence(
    lat DECIMAL(10, 8),              -- Latitude of location to check
    lng DECIMAL(11, 8),              -- Longitude of location to check
    geofence_lat DECIMAL(10, 8),     -- Center latitude of geofence
    geofence_lng DECIMAL(11, 8),     -- Center longitude of geofence
    radius_meters INTEGER            -- Radius of geofence in meters
) RETURNS BOOLEAN AS $$
DECLARE
    distance_meters DECIMAL;         -- Calculated distance between points
BEGIN
    -- Calculate distance using Haversine formula
    -- Formula accounts for Earth's spherical shape (Earth radius = 6371 km = 6371000 m)
    -- Result is the great-circle distance between the two GPS points
    distance_meters := 6371000 * acos(
        cos(radians(lat)) *
        cos(radians(geofence_lat)) *
        cos(radians(geofence_lng) - radians(lng)) +
        sin(radians(lat)) *
        sin(radians(geofence_lat))
    );

    -- Return TRUE if calculated distance is within the allowed radius
    RETURN distance_meters <= radius_meters;
END;
$$ LANGUAGE plpgsql;