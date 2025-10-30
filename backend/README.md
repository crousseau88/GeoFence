# GeoFence POC Backend

A minimal, proof-of-concept Kotlin backend for a geofencing time-tracking Android app. This system tracks a single employee at a specific geofence location (WHG3+H5, Media, Pennsylvania) using PostgreSQL with PostGIS and a REST API built with Ktor.

## Features

- **Single Employee System**: Pre-configured for one employee (john_doe)
- **Single Geofence**: Media, PA location (lat: 39.9187, lng: -75.3876, radius: 100m)
- **JWT Authentication**: Secure token-based authentication with bcrypt password hashing
- **Schedule Tracking**: Clock-in, clock-out, and geofence exit event logging
- **PostGIS Integration**: Geospatial queries for location-based operations
- **REST API**: Clean RESTful endpoints for Android app integration

## Technology Stack

- **Kotlin**: 1.9.21
- **Ktor**: 2.3.7 (Web framework)
- **Exposed**: 0.45.0 (ORM for database access)
- **PostgreSQL**: with PostGIS extension
- **JWT**: For stateless authentication
- **BCrypt**: For secure password hashing

## Project Structure

```
backend/
├── build.gradle.kts           # Gradle build configuration
├── settings.gradle.kts         # Gradle settings
├── db/
│   └── schema.sql             # PostgreSQL schema with PostGIS
└── src/main/kotlin/com/geofence/
    ├── Application.kt         # Main application entry point
    ├── models/
    │   └── Models.kt          # API request/response models
    ├── data/
    │   ├── DatabaseTables.kt  # Exposed table definitions
    │   └── DatabaseFactory.kt # Database connection management
    ├── services/
    │   ├── AuthService.kt     # Authentication & JWT management
    │   ├── GeofenceService.kt # Geofence data operations
    │   └── ScheduleService.kt # Schedule event operations
    └── routes/
        └── Routes.kt          # API endpoint definitions
```

## Prerequisites

1. **Java 17** or later
2. **PostgreSQL 14+** with PostGIS extension
3. **Gradle 8.0+** (or use included wrapper)

## Database Setup

### 1. Install PostgreSQL and PostGIS

**macOS (Homebrew):**
```bash
brew install postgresql@14 postgis
brew services start postgresql@14
```

**Ubuntu/Debian:**
```bash
sudo apt-get install postgresql-14 postgresql-14-postgis-3
sudo systemctl start postgresql
```

### 2. Create Database and Run Schema

```bash
# Create database and user
sudo -u postgres psql << EOF
CREATE ROLE geofence_user WITH LOGIN PASSWORD 'geofence_password';
CREATE DATABASE geofence_db OWNER geofence_user;
GRANT ALL PRIVILEGES ON DATABASE geofence_db TO geofence_user;
EOF

# Run schema creation
cd backend/db
PGPASSWORD=geofence_password psql -h localhost -U geofence_user -d geofence_db -f schema.sql
```

The schema will create:
- `employees` table with one employee (username: `john_doe`, password: `password123`)
- `geofences` table with Media, PA location
- `schedule` table for tracking events
- PostGIS extension for geospatial operations

## Configuration

The backend uses environment variables for configuration. Set these before running:

```bash
# Database configuration
export DATABASE_URL="jdbc:postgresql://localhost:5432/geofence_db"
export DATABASE_USER="geofence_user"
export DATABASE_PASSWORD="geofence_password"

# JWT configuration (change in production!)
export JWT_SECRET="your-secret-key-here"
export JWT_ISSUER="geofence-backend"
export JWT_AUDIENCE="geofence-api"
```

**Note**: If environment variables are not set, the application uses default values suitable for local development.

## Running the Backend

### Using Gradle Wrapper (Recommended)

```bash
cd backend
./gradlew run
```

### Building and Running JAR

```bash
cd backend
./gradlew build
java -jar build/libs/geofence-backend-1.0.0-all.jar
```

The server will start on `http://localhost:8080`

## API Endpoints

### Public Endpoints

#### 1. Login
```http
POST /api/login
Content-Type: application/json

{
  "username": "john_doe",
  "password": "password123"
}
```

**Response (200 OK):**
```json
{
  "token": "eyJhbGciOiJIUzI1NiIs...",
  "employeeId": "550e8400-e29b-41d4-a716-446655440000"
}
```

### Protected Endpoints (Require JWT Token)

All protected endpoints require the `Authorization` header:
```
Authorization: Bearer <your-jwt-token>
```

#### 2. Get Employee Profile
```http
GET /api/employee
Authorization: Bearer <token>
```

**Response (200 OK):**
```json
{
  "employeeId": "550e8400-e29b-41d4-a716-446655440000",
  "username": "john_doe",
  "email": "john@example.com"
}
```

#### 3. Get Geofence Data
```http
GET /api/geofence
Authorization: Bearer <token>
```

**Response (200 OK):**
```json
{
  "geofenceId": "660e8400-e29b-41d4-a716-446655440000",
  "requestId": "media_pa_geofence",
  "name": "Media PA Office",
  "latitude": 39.9187,
  "longitude": -75.3876,
  "radius": 100
}
```

#### 4. Clock In
```http
POST /api/schedule/clock-in
Authorization: Bearer <token>
Content-Type: application/json

{
  "latitude": 39.9187,
  "longitude": -75.3876
}
```

**Response (201 Created):**
```json
{
  "message": "Clock-in recorded successfully",
  "eventId": "770e8400-e29b-41d4-a716-446655440000"
}
```

#### 5. Clock Out
```http
POST /api/schedule/clock-out
Authorization: Bearer <token>
Content-Type: application/json

{
  "latitude": 39.9187,
  "longitude": -75.3876
}
```

**Response (201 Created):**
```json
{
  "message": "Clock-out recorded successfully",
  "eventId": "880e8400-e29b-41d4-a716-446655440000"
}
```

#### 6. Log Geofence Exit
```http
POST /api/schedule/exit
Authorization: Bearer <token>
Content-Type: application/json

{
  "latitude": 39.9190,
  "longitude": -75.3880,
  "distanceFromGeofence": 45.5
}
```

**Response (201 Created):**
```json
{
  "message": "Exit event recorded successfully",
  "eventId": "990e8400-e29b-41d4-a716-446655440000"
}
```

#### 7. Get Schedule History
```http
GET /api/schedule
Authorization: Bearer <token>
```

**Response (200 OK):**
```json
{
  "events": [
    {
      "eventId": "990e8400-e29b-41d4-a716-446655440000",
      "eventType": "EXIT",
      "eventTime": "2024-01-15T14:30:00Z",
      "latitude": 39.9190,
      "longitude": -75.3880,
      "distanceFromGeofence": 45.5
    },
    {
      "eventId": "880e8400-e29b-41d4-a716-446655440000",
      "eventType": "CLOCK_OUT",
      "eventTime": "2024-01-15T17:00:00Z",
      "latitude": 39.9187,
      "longitude": -75.3876,
      "durationMinutes": 480
    },
    {
      "eventId": "770e8400-e29b-41d4-a716-446655440000",
      "eventType": "CLOCK_IN",
      "eventTime": "2024-01-15T09:00:00Z",
      "latitude": 39.9187,
      "longitude": -75.3876
    }
  ]
}
```

## Testing with cURL

### 1. Login and Get Token
```bash
TOKEN=$(curl -s -X POST http://localhost:8080/api/login \
  -H "Content-Type: application/json" \
  -d '{"username":"john_doe","password":"password123"}' \
  | jq -r '.token')

echo "Token: $TOKEN"
```

### 2. Get Employee Profile
```bash
curl -X GET http://localhost:8080/api/employee \
  -H "Authorization: Bearer $TOKEN"
```

### 3. Get Geofence Data
```bash
curl -X GET http://localhost:8080/api/geofence \
  -H "Authorization: Bearer $TOKEN"
```

### 4. Clock In
```bash
curl -X POST http://localhost:8080/api/schedule/clock-in \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"latitude":39.9187,"longitude":-75.3876}'
```

### 5. Clock Out
```bash
curl -X POST http://localhost:8080/api/schedule/clock-out \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"latitude":39.9187,"longitude":-75.3876}'
```

### 6. Log Exit Event
```bash
curl -X POST http://localhost:8080/api/schedule/exit \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"latitude":39.9190,"longitude":-75.3880,"distanceFromGeofence":45.5}'
```

### 7. Get Schedule History
```bash
curl -X GET http://localhost:8080/api/schedule \
  -H "Authorization: Bearer $TOKEN"
```

## Error Responses

All endpoints return consistent error responses:

**400 Bad Request:**
```json
{
  "error": "bad_request",
  "message": "Username and password are required"
}
```

**401 Unauthorized:**
```json
{
  "error": "unauthorized",
  "message": "Invalid username or password"
}
```

**404 Not Found:**
```json
{
  "error": "not_found",
  "message": "Employee not found"
}
```

**500 Internal Server Error:**
```json
{
  "error": "server_error",
  "message": "An error occurred"
}
```

## Security Considerations

### For Development
- Default credentials: `john_doe` / `password123`
- Default JWT secret is insecure
- CORS allows all origins

### For Production
1. **Change all default credentials** in the database
2. **Set strong JWT_SECRET** environment variable
3. **Restrict CORS** to specific Android app origin in `Application.kt`
4. **Use HTTPS** - Configure SSL certificates in production
5. **Set strong database password** and restrict access
6. **Enable PostgreSQL SSL** connections
7. **Add rate limiting** to prevent brute force attacks

## Android Integration

The backend provides all necessary data for Android Geofencing API:

1. **requestId**: Use `media_pa_geofence` from `/api/geofence` endpoint
2. **Latitude/Longitude**: Use coordinates from geofence response
3. **Radius**: Use radius from geofence response (100 meters)
4. **JWT Token**: Store securely in Android SharedPreferences after login

Example Android Geofence registration:
```kotlin
val geofence = Geofence.Builder()
    .setRequestId("media_pa_geofence")
    .setCircularRegion(39.9187, -75.3876, 100f)
    .setExpirationDuration(Geofence.NEVER_EXPIRE)
    .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER or Geofence.GEOFENCE_TRANSITION_EXIT)
    .build()
```

## Troubleshooting

### Database Connection Issues
```bash
# Check PostgreSQL is running
pg_isready -h localhost -p 5432

# Test database connection
PGPASSWORD=geofence_password psql -h localhost -U geofence_user -d geofence_db -c "SELECT 1;"
```

### PostGIS Extension Missing
```sql
-- Connect as superuser
sudo -u postgres psql geofence_db

-- Enable PostGIS
CREATE EXTENSION IF NOT EXISTS postgis;
```

### Port 8080 Already in Use
```bash
# Find process using port 8080
lsof -i :8080

# Kill the process (replace PID)
kill -9 <PID>
```

## Development

### Running Tests
```bash
./gradlew test
```

### Building JAR
```bash
./gradlew build
# Output: build/libs/geofence-backend-1.0.0.jar
```

### Checking Dependencies
```bash
./gradlew dependencies
```

## License

This is a proof-of-concept project for demonstration purposes.

## Support

For issues or questions about this POC implementation, refer to the source code comments or database schema documentation.
