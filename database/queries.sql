-- Common queries for GeoFence Time Tracking System

-- 1. Get all active employees
SELECT employee_id, username, full_name, email, phone
FROM employees
WHERE is_active = TRUE
ORDER BY full_name;

-- 2. Get all active geofences
SELECT geofence_id, name, description, latitude, longitude, radius_meters
FROM geofences
WHERE is_active = TRUE
ORDER BY name;

-- 3. Check if employee is currently clocked in
SELECT s.shift_id, s.clock_in, g.name as geofence_name
FROM shifts s
JOIN geofences g ON s.geofence_id = g.geofence_id
WHERE s.employee_id = ? AND s.status = 'active';

-- 4. Get employee's shift history for date range
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
    SELECT shift_id, SUM(total_minutes) as total_break_minutes
    FROM breaks
    WHERE total_minutes IS NOT NULL
    GROUP BY shift_id
) b ON s.shift_id = b.shift_id
WHERE s.employee_id = ?
    AND s.clock_in >= ?
    AND s.clock_in <= ?
ORDER BY s.clock_in DESC;

-- 5. Clock in employee (validate geofence location)
INSERT INTO shifts (employee_id, geofence_id, clock_in, clock_in_latitude, clock_in_longitude)
SELECT ?, ?, CURRENT_TIMESTAMP, ?, ?
WHERE is_within_geofence(?, ?,
    (SELECT latitude FROM geofences WHERE geofence_id = ?),
    (SELECT longitude FROM geofences WHERE geofence_id = ?),
    (SELECT radius_meters FROM geofences WHERE geofence_id = ?)
) = TRUE;

-- 6. Clock out employee
UPDATE shifts
SET clock_out = CURRENT_TIMESTAMP,
    clock_out_latitude = ?,
    clock_out_longitude = ?
WHERE shift_id = ? AND employee_id = ? AND status = 'active';

-- 7. Start break
INSERT INTO breaks (shift_id, break_type, start_time)
VALUES (?, ?, CURRENT_TIMESTAMP);

-- 8. End break
UPDATE breaks
SET end_time = CURRENT_TIMESTAMP
WHERE break_id = ? AND end_time IS NULL;

-- 9. Get current active breaks for a shift
SELECT break_id, break_type, start_time
FROM breaks
WHERE shift_id = ? AND end_time IS NULL;

-- 10. Validate employee can clock in at location
SELECT
    g.geofence_id,
    g.name,
    is_within_geofence(?, ?, g.latitude, g.longitude, g.radius_meters) as can_clock_in
FROM geofences g
WHERE g.is_active = TRUE;

-- 11. Get employee's daily summary
SELECT
    DATE(s.clock_in) as work_date,
    COUNT(s.shift_id) as total_shifts,
    SUM(s.total_minutes_worked) as total_work_minutes,
    SUM(COALESCE(b.total_break_minutes, 0)) as total_break_minutes
FROM shifts s
LEFT JOIN (
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

-- 12. Get geofence activity log
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

-- 13. Find employees currently at a geofence
SELECT DISTINCT
    e.employee_id,
    e.full_name,
    s.clock_in
FROM employees e
JOIN shifts s ON e.employee_id = s.employee_id
WHERE s.geofence_id = ?
    AND s.status = 'active';

-- 14. Get overtime hours for employee (over 8 hours per day)
SELECT
    DATE(s.clock_in) as work_date,
    SUM(s.total_minutes_worked) as total_minutes,
    CASE
        WHEN SUM(s.total_minutes_worked) > 480
        THEN SUM(s.total_minutes_worked) - 480
        ELSE 0
    END as overtime_minutes
FROM shifts s
WHERE s.employee_id = ?
    AND s.status = 'completed'
    AND s.clock_in >= ?
    AND s.clock_in <= ?
GROUP BY DATE(s.clock_in)
HAVING SUM(s.total_minutes_worked) > 480
ORDER BY work_date DESC;

-- 15. Log location event
INSERT INTO location_logs (employee_id, geofence_id, event_type, event_time, latitude, longitude)
VALUES (?, ?, ?, CURRENT_TIMESTAMP, ?, ?);