-- GeoFence Time Tracking Database Schema
-- PostgreSQL implementation

-- Drop tables if they exist (for clean setup)
DROP TABLE IF EXISTS location_logs;
DROP TABLE IF EXISTS breaks;
DROP TABLE IF EXISTS shifts;
DROP TABLE IF EXISTS geofences;
DROP TABLE IF EXISTS employees;

-- Create Employees table
CREATE TABLE employees (
    employee_id SERIAL PRIMARY KEY,
    username VARCHAR(50) UNIQUE NOT NULL,
    full_name VARCHAR(100) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    email VARCHAR(100) UNIQUE,
    phone VARCHAR(20),
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Create Geofences table
CREATE TABLE geofences (
    geofence_id SERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    description TEXT,
    latitude DECIMAL(10, 8) NOT NULL,
    longitude DECIMAL(11, 8) NOT NULL,
    radius_meters INTEGER NOT NULL CHECK (radius_meters > 0),
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Create Shifts table
CREATE TABLE shifts (
    shift_id SERIAL PRIMARY KEY,
    employee_id INTEGER NOT NULL REFERENCES employees(employee_id) ON DELETE CASCADE,
    geofence_id INTEGER NOT NULL REFERENCES geofences(geofence_id) ON DELETE CASCADE,
    clock_in TIMESTAMP NOT NULL,
    clock_out TIMESTAMP,
    total_minutes_worked INTEGER,
    clock_in_latitude DECIMAL(10, 8),
    clock_in_longitude DECIMAL(11, 8),
    clock_out_latitude DECIMAL(10, 8),
    clock_out_longitude DECIMAL(11, 8),
    status VARCHAR(20) DEFAULT 'active' CHECK (status IN ('active', 'completed', 'cancelled')),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    -- Ensure clock_out is after clock_in
    CONSTRAINT valid_shift_times CHECK (clock_out IS NULL OR clock_out > clock_in)
);

-- Create Breaks table
CREATE TABLE breaks (
    break_id SERIAL PRIMARY KEY,
    shift_id INTEGER NOT NULL REFERENCES shifts(shift_id) ON DELETE CASCADE,
    break_type VARCHAR(10) NOT NULL CHECK (break_type IN ('break', 'lunch')),
    start_time TIMESTAMP NOT NULL,
    end_time TIMESTAMP,
    total_minutes INTEGER,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    -- Ensure end_time is after start_time
    CONSTRAINT valid_break_times CHECK (end_time IS NULL OR end_time > start_time)
);

-- Create Location Logs table
CREATE TABLE location_logs (
    log_id SERIAL PRIMARY KEY,
    employee_id INTEGER NOT NULL REFERENCES employees(employee_id) ON DELETE CASCADE,
    geofence_id INTEGER NOT NULL REFERENCES geofences(geofence_id) ON DELETE CASCADE,
    event_type VARCHAR(10) NOT NULL CHECK (event_type IN ('enter', 'exit')),
    event_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    latitude DECIMAL(10, 8) NOT NULL,
    longitude DECIMAL(11, 8) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Create indexes for better performance
CREATE INDEX idx_employees_username ON employees(username);
CREATE INDEX idx_employees_active ON employees(is_active);
CREATE INDEX idx_geofences_active ON geofences(is_active);
CREATE INDEX idx_shifts_employee ON shifts(employee_id);
CREATE INDEX idx_shifts_geofence ON shifts(geofence_id);
CREATE INDEX idx_shifts_status ON shifts(status);
CREATE INDEX idx_shifts_clock_in ON shifts(clock_in);
CREATE INDEX idx_breaks_shift ON breaks(shift_id);
CREATE INDEX idx_breaks_type ON breaks(break_type);
CREATE INDEX idx_location_logs_employee ON location_logs(employee_id);
CREATE INDEX idx_location_logs_geofence ON location_logs(geofence_id);
CREATE INDEX idx_location_logs_event_time ON location_logs(event_time);

-- Create triggers to update total_minutes_worked automatically
CREATE OR REPLACE FUNCTION update_shift_total_minutes()
RETURNS TRIGGER AS $$
BEGIN
    IF NEW.clock_out IS NOT NULL THEN
        -- Calculate total minutes excluding breaks
        NEW.total_minutes_worked := EXTRACT(EPOCH FROM (NEW.clock_out - NEW.clock_in)) / 60;

        -- Subtract break time
        NEW.total_minutes_worked := NEW.total_minutes_worked - COALESCE(
            (SELECT SUM(total_minutes) FROM breaks WHERE shift_id = NEW.shift_id AND total_minutes IS NOT NULL),
            0
        );

        -- Update shift status
        NEW.status := 'completed';
    END IF;

    NEW.updated_at := CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trigger_update_shift_total_minutes
    BEFORE UPDATE ON shifts
    FOR EACH ROW
    EXECUTE FUNCTION update_shift_total_minutes();

-- Create trigger to update break total_minutes automatically
CREATE OR REPLACE FUNCTION update_break_total_minutes()
RETURNS TRIGGER AS $$
BEGIN
    IF NEW.end_time IS NOT NULL THEN
        NEW.total_minutes := EXTRACT(EPOCH FROM (NEW.end_time - NEW.start_time)) / 60;
    END IF;

    NEW.updated_at := CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trigger_update_break_total_minutes
    BEFORE UPDATE ON breaks
    FOR EACH ROW
    EXECUTE FUNCTION update_break_total_minutes();

-- Create function to check if location is within geofence
CREATE OR REPLACE FUNCTION is_within_geofence(
    lat DECIMAL(10, 8),
    lng DECIMAL(11, 8),
    geofence_lat DECIMAL(10, 8),
    geofence_lng DECIMAL(11, 8),
    radius_meters INTEGER
) RETURNS BOOLEAN AS $$
DECLARE
    distance_meters DECIMAL;
BEGIN
    -- Calculate distance using Haversine formula (approximate)
    distance_meters := 6371000 * acos(
        cos(radians(lat)) *
        cos(radians(geofence_lat)) *
        cos(radians(geofence_lng) - radians(lng)) +
        sin(radians(lat)) *
        sin(radians(geofence_lat))
    );

    RETURN distance_meters <= radius_meters;
END;
$$ LANGUAGE plpgsql;