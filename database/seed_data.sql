-- Sample data for GeoFence Time Tracking System
-- This file populates the database with test data for development and demonstration purposes

-- Insert sample employees
-- Password hash shown is for the plaintext password "password123" (bcrypt hashed)
-- In production, these would be unique per employee and securely generated
INSERT INTO employees (username, full_name, password_hash, email, phone) VALUES
('jdoe', 'John Doe', '$2b$12$LQv3c1yqBWVHxkd0LHAkCOYz6TtxMQJqhN8/LeUY.rEiJmhUnJb3.', 'jdoe@company.com', '555-0101'),
('msmith', 'Mary Smith', '$2b$12$LQv3c1yqBWVHxkd0LHAkCOYz6TtxMQJqhN8/LeUY.rEiJmhUnJb3.', 'msmith@company.com', '555-0102'),
('bwilson', 'Bob Wilson', '$2b$12$LQv3c1yqBWVHxkd0LHAkCOYz6TtxMQJqhN8/LeUY.rEiJmhUnJb3.', 'bwilson@company.com', '555-0103'),
('sjohnson', 'Sarah Johnson', '$2b$12$LQv3c1yqBWVHxkd0LHAkCOYz6TtxMQJqhN8/LeUY.rEiJmhUnJb3.', 'sjohnson@company.com', '555-0104');

-- Insert sample geofences (job sites)
-- These represent work locations in New York City where employees can clock in/out
-- Coordinates are real NYC locations, radius defines the acceptable distance from center
INSERT INTO geofences (name, description, latitude, longitude, radius_meters) VALUES
('Downtown Construction Site', 'Main office building renovation project', 40.758896, -73.985130, 100),  -- Times Square area, 100m radius
('Warehouse District', 'New warehouse facility construction', 40.749825, -73.993332, 150),             -- Chelsea area, 150m radius
('Central Park Project', 'Landscaping and maintenance project', 40.782865, -73.965355, 200),           -- Upper West Side, 200m radius
('Brooklyn Bridge Maintenance', 'Bridge inspection and repair work', 40.706001, -73.996947, 75);       -- Brooklyn Bridge, 75m radius

-- Insert sample completed shifts
-- These represent historical work sessions that have been completed (clocked in and out)
-- The trigger will automatically calculate total_minutes_worked and set status to 'completed'
INSERT INTO shifts (employee_id, geofence_id, clock_in, clock_out, clock_in_latitude, clock_in_longitude, clock_out_latitude, clock_out_longitude) VALUES
(1, 1, '2024-01-15 08:00:00', '2024-01-15 17:00:00', 40.758896, -73.985130, 40.758896, -73.985130),  -- John Doe worked 9 hours
(2, 2, '2024-01-15 07:30:00', '2024-01-15 16:30:00', 40.749825, -73.993332, 40.749825, -73.993332),  -- Mary Smith worked 9 hours
(1, 1, '2024-01-16 08:15:00', '2024-01-16 17:15:00', 40.758896, -73.985130, 40.758896, -73.985130),  -- John Doe worked 9 hours
(3, 3, '2024-01-16 09:00:00', '2024-01-16 18:00:00', 40.782865, -73.965355, 40.782865, -73.965355);  -- Bob Wilson worked 9 hours

-- Insert sample active shift (currently clocked in)
-- This represents an employee who is currently at work (no clock_out time yet)
-- Status is 'active' to indicate the shift is ongoing
INSERT INTO shifts (employee_id, geofence_id, clock_in, clock_in_latitude, clock_in_longitude, status) VALUES
(4, 4, '2024-01-17 08:00:00', 40.706001, -73.996947, 'active');  -- Sarah Johnson is currently working

-- Insert sample breaks for completed shifts
-- These breaks are associated with the shifts above (shift_id references)
-- The trigger will automatically calculate total_minutes for each break
-- Break times are automatically subtracted from shift total_minutes_worked
INSERT INTO breaks (shift_id, break_type, start_time, end_time) VALUES
(1, 'break', '2024-01-15 10:30:00', '2024-01-15 10:45:00'),  -- John's morning break: 15 minutes
(1, 'lunch', '2024-01-15 12:00:00', '2024-01-15 13:00:00'),  -- John's lunch break: 60 minutes
(1, 'break', '2024-01-15 15:00:00', '2024-01-15 15:15:00'),  -- John's afternoon break: 15 minutes
(2, 'break', '2024-01-15 10:00:00', '2024-01-15 10:15:00'),  -- Mary's morning break: 15 minutes
(2, 'lunch', '2024-01-15 12:30:00', '2024-01-15 13:30:00'),  -- Mary's lunch break: 60 minutes
(3, 'break', '2024-01-16 10:30:00', '2024-01-16 10:45:00'),  -- John's morning break: 15 minutes
(3, 'lunch', '2024-01-16 12:00:00', '2024-01-16 13:00:00');  -- John's lunch break: 60 minutes

-- Insert sample location logs
-- These create an audit trail of when employees entered/exited geofenced areas
-- Useful for compliance reporting, tracking movement patterns, and debugging issues
INSERT INTO location_logs (employee_id, geofence_id, event_type, event_time, latitude, longitude) VALUES
(1, 1, 'enter', '2024-01-15 08:00:00', 40.758896, -73.985130),  -- John entered Downtown site at clock-in
(1, 1, 'exit', '2024-01-15 12:00:00', 40.758896, -73.985130),   -- John left for lunch break
(1, 1, 'enter', '2024-01-15 13:00:00', 40.758896, -73.985130),  -- John returned from lunch
(1, 1, 'exit', '2024-01-15 17:00:00', 40.758896, -73.985130),   -- John left at clock-out
(2, 2, 'enter', '2024-01-15 07:30:00', 40.749825, -73.993332),  -- Mary entered Warehouse site at clock-in
(2, 2, 'exit', '2024-01-15 12:30:00', 40.749825, -73.993332),   -- Mary left for lunch break
(2, 2, 'enter', '2024-01-15 13:30:00', 40.749825, -73.993332),  -- Mary returned from lunch
(2, 2, 'exit', '2024-01-15 16:30:00', 40.749825, -73.993332),   -- Mary left at clock-out
(4, 4, 'enter', '2024-01-17 08:00:00', 40.706001, -73.996947);  -- Sarah entered Brooklyn Bridge site (still there)