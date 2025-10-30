-- Common queries for GeoFence Time Tracking System
-- This file contains frequently used SQL queries for the application
-- Placeholders (?) should be replaced with actual values when using these queries

-- Query 1: Get all active employees
-- Purpose: Retrieve list of all currently active employees for display in UI
-- Returns: Employee details sorted alphabetically by name
SELECT employee_id, username, full_name, email, phone
FROM employees
WHERE is_active = TRUE
ORDER BY full_name;

-- Query 2: Get all active geofences
-- Purpose: Retrieve list of all active work locations where employees can clock in
-- Returns: Geofence details including coordinates and radius, sorted by name
SELECT geofence_id, name, description, latitude, longitude, radius_meters
FROM geofences
WHERE is_active = TRUE
ORDER BY name;

-- Query 3: Check if employee is currently clocked in
-- Purpose: Determine if an employee has an active shift in progress
-- Parameters: ? = employee_id
-- Returns: Shift details if employee is clocked in, empty result if not
SELECT s.shift_id, s.clock_in, g.name as geofence_name
FROM shifts s
JOIN geofences g ON s.geofence_id = g.geofence_id
WHERE s.employee_id = ? AND s.status = 'active';

-- Query 4: Get employee's shift history for date range
-- Purpose: Retrieve all shifts worked by an employee within a specific date range
-- Parameters: ? = employee_id, ? = start_date, ? = end_date
-- Returns: Complete shift details including work time and break time, newest first
-- Use case: Generating timesheets, payroll reports, or employee work history
SELECT
    s.shift_id,
    s.clock_in,
    s.clock_out,
    s.total_minutes_worked,
    g.name as geofence_name,
    COALESCE(b.total_break_minutes, 0) as total_break_minutes
FROM shifts s
JOIN geofences g ON s.geofence_id = g.geofence_id
LEFT JOIN (
    -- Subquery to calculate total break time per shift
    SELECT shift_id, SUM(total_minutes) as total_break_minutes
    FROM breaks
    WHERE total_minutes IS NOT NULL
    GROUP BY shift_id
) b ON s.shift_id = b.shift_id
WHERE s.employee_id = ?
    AND s.clock_in >= ?
    AND s.clock_in <= ?
ORDER BY s.clock_in DESC;

-- Query 5: Clock in employee (validate geofence location)
-- Purpose: Start a new shift for an employee, only if they are within the geofence boundary
-- Parameters: ? = employee_id, ? = geofence_id, ? = clock_in_latitude, ? = clock_in_longitude,
--            (and same values repeated for is_within_geofence validation)
-- Returns: Inserts 1 row if location is valid, 0 rows if location is outside geofence
-- Important: This query validates GPS location before allowing clock-in
INSERT INTO shifts (employee_id, geofence_id, clock_in, clock_in_latitude, clock_in_longitude)
SELECT ?, ?, CURRENT_TIMESTAMP, ?, ?
WHERE is_within_geofence(?, ?,                                              -- Employee's GPS coordinates
    (SELECT latitude FROM geofences WHERE geofence_id = ?),                 -- Geofence center latitude
    (SELECT longitude FROM geofences WHERE geofence_id = ?),                -- Geofence center longitude
    (SELECT radius_meters FROM geofences WHERE geofence_id = ?)             -- Geofence radius
) = TRUE;

-- Query 6: Clock out employee
-- Purpose: End an active shift and record the clock-out time and location
-- Parameters: ? = clock_out_latitude, ? = clock_out_longitude, ? = shift_id, ? = employee_id
-- Returns: Updates 1 row if shift is active, 0 rows if no active shift found
-- Note: The trigger will automatically calculate total_minutes_worked and set status to 'completed'
UPDATE shifts
SET clock_out = CURRENT_TIMESTAMP,
    clock_out_latitude = ?,
    clock_out_longitude = ?
WHERE shift_id = ? AND employee_id = ? AND status = 'active';

-- Query 7: Start break
-- Purpose: Begin a break period during an active shift
-- Parameters: ? = shift_id, ? = break_type ('break' or 'lunch')
-- Returns: Inserts 1 row with the break start time
-- Note: Break time will be subtracted from total_minutes_worked when the shift ends
INSERT INTO breaks (shift_id, break_type, start_time)
VALUES (?, ?, CURRENT_TIMESTAMP);

-- Query 8: End break
-- Purpose: Complete a break period and calculate its duration
-- Parameters: ? = break_id
-- Returns: Updates 1 row if an active break exists, 0 rows if break not found or already ended
-- Note: The trigger will automatically calculate and set total_minutes
UPDATE breaks
SET end_time = CURRENT_TIMESTAMP
WHERE break_id = ? AND end_time IS NULL;

-- Query 9: Get current active breaks for a shift
-- Purpose: Find any breaks that are currently in progress for a specific shift
-- Parameters: ? = shift_id
-- Returns: List of breaks without end_time (still ongoing)
-- Use case: Checking if employee is currently on break before allowing clock-out
SELECT break_id, break_type, start_time
FROM breaks
WHERE shift_id = ? AND end_time IS NULL;

-- Query 10: Validate employee can clock in at location
-- Purpose: Check which geofences (if any) the employee is currently within
-- Parameters: ? = employee_latitude, ? = employee_longitude
-- Returns: All active geofences with a flag indicating if employee is within each one
-- Use case: Showing employee which locations they can clock in at based on current GPS position
SELECT
    g.geofence_id,
    g.name,
    is_within_geofence(?, ?, g.latitude, g.longitude, g.radius_meters) as can_clock_in
FROM geofences g
WHERE g.is_active = TRUE;

-- Query 11: Get employee's daily summary
-- Purpose: Generate daily time tracking summaries for an employee across a date range
-- Parameters: ? = employee_id, ? = start_date, ? = end_date
-- Returns: Daily aggregated totals of shifts, work time, and break time
-- Use case: Creating weekly/monthly timesheets or payroll summaries
SELECT
    DATE(s.clock_in) as work_date,
    COUNT(s.shift_id) as total_shifts,                                      -- Number of shifts worked that day
    SUM(s.total_minutes_worked) as total_work_minutes,                      -- Total billable work time (excludes breaks)
    SUM(COALESCE(b.total_break_minutes, 0)) as total_break_minutes         -- Total break time
FROM shifts s
LEFT JOIN (
    -- Subquery to sum up break time per shift
    SELECT shift_id, SUM(total_minutes) as total_break_minutes
    FROM breaks
    WHERE total_minutes IS NOT NULL
    GROUP BY shift_id
) b ON s.shift_id = b.shift_id
WHERE s.employee_id = ?
    AND s.status = 'completed'
    AND s.clock_in >= ?
    AND s.clock_in <= ?
GROUP BY DATE(s.clock_in)
ORDER BY work_date DESC;

-- Query 12: Get geofence activity log
-- Purpose: Retrieve entry/exit history for a specific geofence location
-- Parameters: ? = geofence_id, ? = start_datetime, ? = end_datetime
-- Returns: Chronological log of who entered/exited the location with GPS coordinates
-- Use case: Site access audit trails, compliance reporting, security monitoring
SELECT
    e.full_name,
    l.event_type,
    l.event_time,
    l.latitude,
    l.longitude
FROM location_logs l
JOIN employees e ON l.employee_id = e.employee_id
WHERE l.geofence_id = ?
    AND l.event_time >= ?
    AND l.event_time <= ?
ORDER BY l.event_time DESC;

-- Query 13: Find employees currently at a geofence
-- Purpose: Get real-time list of employees who are currently at a specific work location
-- Parameters: ? = geofence_id
-- Returns: Employee details and when they clocked in for their current shift
-- Use case: Site roll call, emergency evacuation lists, workforce visibility
SELECT DISTINCT
    e.employee_id,
    e.full_name,
    s.clock_in
FROM employees e
JOIN shifts s ON e.employee_id = s.employee_id
WHERE s.geofence_id = ?
    AND s.status = 'active';

-- Query 14: Get overtime hours for employee (over 8 hours per day)
-- Purpose: Calculate overtime for days where employee worked more than 8 hours (480 minutes)
-- Parameters: ? = employee_id, ? = start_date, ? = end_date
-- Returns: Only days with overtime, showing total time and overtime portion
-- Use case: Payroll processing for overtime pay calculation
-- Note: 480 minutes = 8 hours (standard workday threshold)
SELECT
    DATE(s.clock_in) as work_date,
    SUM(s.total_minutes_worked) as total_minutes,                          -- Total time worked that day
    CASE
        WHEN SUM(s.total_minutes_worked) > 480                             -- If more than 8 hours
        THEN SUM(s.total_minutes_worked) - 480                             -- Calculate overtime minutes
        ELSE 0
    END as overtime_minutes
FROM shifts s
WHERE s.employee_id = ?
    AND s.status = 'completed'
    AND s.clock_in >= ?
    AND s.clock_in <= ?
GROUP BY DATE(s.clock_in)
HAVING SUM(s.total_minutes_worked) > 480                                   -- Only return days with overtime
ORDER BY work_date DESC;

-- Query 15: Log location event
-- Purpose: Record when an employee enters or exits a geofenced area
-- Parameters: ? = employee_id, ? = geofence_id, ? = event_type ('enter' or 'exit'),
--            ? = latitude, ? = longitude
-- Returns: Inserts 1 row into the location_logs table
-- Use case: Creating audit trail for compliance, tracking movement patterns
INSERT INTO location_logs (employee_id, geofence_id, event_type, event_time, latitude, longitude)
VALUES (?, ?, ?, CURRENT_TIMESTAMP, ?, ?);