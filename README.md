# GeoFence

A geofence-based time tracking system for employees with Android client and backend API.

## Quick Start Guide

### Prerequisites
- Android SDK installed
- Java 17+ installed
- Emulator or physical Android device

### Steps to Run the App

#### 1. Start the Backend Server
```bash
cd backend
java -cp "gradle-8.5/lib/*" org.gradle.launcher.GradleMain run
```
Backend will run at `http://localhost:8080`

#### 2. Start the Emulator
```bash
~/Library/Android/sdk/emulator/emulator -avd Medium_Phone_API_36.1 &
```
Wait for emulator to fully boot (about 30-60 seconds)

#### 3. Build and Install the App
```bash
cd android
./gradlew clean assembleDebug
~/Library/Android/sdk/platform-tools/adb install -r app/build/outputs/apk/debug/app-debug.apk
```

#### 4. Launch the App
```bash
~/Library/Android/sdk/platform-tools/adb shell am start -n com.geofence/.MainActivity
```

### Login Credentials

Test user credentials (from database/seed_data.sql):
- **Username:** `jdoe`
- **Password:** `password123`

Other test users: `msmith`, `bwilson`, `sjohnson` (all use password: `password123`)

### Test GPS Location
After logging in, simulate GPS location changes:

```bash
# Inside geofence (Media, PA) - triggers auto clock-in
~/Library/Android/sdk/platform-tools/adb emu geo fix -75.3876 39.9187

# Outside geofence - triggers exit notification
~/Library/Android/sdk/platform-tools/adb emu geo fix -75.3876 39.9232
```

### Quick Restart (if already built)
```bash
# Kill and restart emulator with visible window
~/Library/Android/sdk/platform-tools/adb emu kill
sleep 3
~/Library/Android/sdk/emulator/emulator -avd Medium_Phone_API_36.1 &
sleep 15

# Reinstall and launch
~/Library/Android/sdk/platform-tools/adb install -r app/build/outputs/apk/debug/app-debug.apk
~/Library/Android/sdk/platform-tools/adb shell am start -n com.geofence/.MainActivity
```

## Changing Hardcoded Location Coordinates

The application currently has hardcoded coordinates for Media, PA (Latitude: 39.9187, Longitude: -75.3876) in multiple locations. To change these to your desired location:

### 1. Android App - MainActivity.kt
File: `android/app/src/main/java/com/geofence/MainActivity.kt`

**Line ~156 - Geofence Registration:**
```kotlin
val geofence = Geofence.Builder()
    .setRequestId("media_pa_001")
    .setCircularRegion(39.9187, -75.3876, 100f)  // <-- Change these coordinates
    .setExpirationDuration(Geofence.NEVER_EXPIRE)
    .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER or Geofence.GEOFENCE_TRANSITION_EXIT)
    .build()
```

**Line ~188 - Clock In Request:**
```kotlin
val json = """{"latitude":39.9187,"longitude":-75.3876}"""  // <-- Change these
```

**Line ~217 - Clock Out Request:**
```kotlin
val json = """{"latitude":39.9187,"longitude":-75.3876}"""  // <-- Change these
```

### 2. Android App - GeofenceBroadcastReceiver.kt
File: `android/app/src/main/java/com/geofence/GeofenceBroadcastReceiver.kt`

**Line ~46 - Enter Event:**
```kotlin
val json = """{"latitude":39.9187,"longitude":-75.3876}"""  // <-- Change these
```

**Line ~69 - Exit Event:**
```kotlin
val json = """{"latitude":39.9187,"longitude":-75.3876}"""  // <-- Change these
```

### 3. Backend Test Files (Optional)
If you want consistent test data, update these files:
- `backend/src/test/kotlin/com/geofence/routes/RoutesIntegrationTest.kt` (multiple instances)
- `backend/src/test/kotlin/com/geofence/services/ScheduleServiceTest.kt`

### Notes
- The radius is set to 100 meters (100f) in the geofence builder
- Replace both latitude and longitude values with your target location
- Consider updating the geofence request ID to match your location
