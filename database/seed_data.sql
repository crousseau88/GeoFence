-- Sample data for GeoFence Time Tracking System

-- Insert sample employees
INSERT INTO employees (username, full_name, password_hash, email, phone) VALUES
('jdoe', 'John Doe', '$2b$12$LQv3c1yqBWVHxkd0LHAkCOYz6TtxMQJqhN8/LeUY.rEiJmhUnJb3.', 'jdoe@company.com', '555-0101'),
('msmith', 'Mary Smith', '$2b$12$LQv3c1yqBWVHxkd0LHAkCOYz6TtxMQJqhN8/LeUY.rEiJmhUnJb3.', 'msmith@company.com', '555-0102'),
('bwilson', 'Bob Wilson', '$2b$12$LQv3c1yqBWVHxkd0LHAkCOYz6TtxMQJqhN8/LeUY.rEiJmhUnJb3.', 'bwilson@company.com', '555-0103'),
('sjohnson', 'Sarah Johnson', '$2b$12$LQv3c1yqBWVHxkd0LHAkCOYz6TtxMQJqhN8/LeUY.rEiJmhUnJb3.', 'sjohnson@company.com', '555-0104');

-- Insert sample geofences (job sites)
INSERT INTO geofences (name, description, latitude, longitude, radius_meters) VALUES
('Downtown Construction Site', 'Main office building renovation project', 40.758896, -73.985130, 100),
('Warehouse District', 'New warehouse facility construction', 40.749825, -73.993332, 150),
('Central Park Project', 'Landscaping and maintenance project', 40.782865, -73.965355, 200),
('Brooklyn Bridge Maintenance', 'Bridge inspection and repair work', 40.706001, -73.996947, 75);

-- Insert sample completed shifts
INSERT INTO shifts (employee_id, geofence_id, clock_in, clock_out, clock_in_latitude, clock_in_longitude, clock_out_latitude, clock_out_longitude) VALUES
(1, 1, '2024-01-15 08:00:00', '2024-01-15 17:00:00', 40.758896, -73.985130, 40.758896, -73.985130),
(2, 2, '2024-01-15 07:30:00', '2024-01-15 16:30:00', 40.749825, -73.993332, 40.749825, -73.993332),
(1, 1, '2024-01-16 08:15:00', '2024-01-16 17:15:00', 40.758896, -73.985130, 40.758896, -73.985130),
(3, 3, '2024-01-16 09:00:00', '2024-01-16 18:00:00', 40.782865, -73.965355, 40.782865, -73.965355);

-- Insert sample active shift (currently clocked in)
INSERT INTO shifts (employee_id, geofence_id, clock_in, clock_in_latitude, clock_in_longitude, status) VALUES
(4, 4, '2024-01-17 08:00:00', 40.706001, -73.996947, 'active');

-- Insert sample breaks for completed shifts
INSERT INTO breaks (shift_id, break_type, start_time, end_time) VALUES
(1, 'break', '2024-01-15 10:30:00', '2024-01-15 10:45:00'),
(1, 'lunch', '2024-01-15 12:00:00', '2024-01-15 13:00:00'),
(1, 'break', '2024-01-15 15:00:00', '2024-01-15 15:15:00'),
(2, 'break', '2024-01-15 10:00:00', '2024-01-15 10:15:00'),
(2, 'lunch', '2024-01-15 12:30:00', '2024-01-15 13:30:00'),
(3, 'break', '2024-01-16 10:30:00', '2024-01-16 10:45:00'),
(3, 'lunch', '2024-01-16 12:00:00', '2024-01-16 13:00:00');

-- Insert sample location logs
INSERT INTO location_logs (employee_id, geofence_id, event_type, event_time, latitude, longitude) VALUES
(1, 1, 'enter', '2024-01-15 08:00:00', 40.758896, -73.985130),
(1, 1, 'exit', '2024-01-15 12:00:00', 40.758896, -73.985130),
(1, 1, 'enter', '2024-01-15 13:00:00', 40.758896, -73.985130),
(1, 1, 'exit', '2024-01-15 17:00:00', 40.758896, -73.985130),
(2, 2, 'enter', '2024-01-15 07:30:00', 40.749825, -73.993332),
(2, 2, 'exit', '2024-01-15 12:30:00', 40.749825, -73.993332),
(2, 2, 'enter', '2024-01-15 13:30:00', 40.749825, -73.993332),
(2, 2, 'exit', '2024-01-15 16:30:00', 40.749825, -73.993332),
(4, 4, 'enter', '2024-01-17 08:00:00', 40.706001, -73.996947);