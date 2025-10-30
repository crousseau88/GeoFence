# GeoFence App - Quick Start Guide

## Prerequisites
- Android SDK installed
- Java 17+ installed
- Emulator or physical Android device

## Steps to Run the App

### 1. Start the Backend Server
```bash
cd backend
java -cp "gradle-8.5/lib/*" org.gradle.launcher.GradleMain run
```
Backend will run at `http://localhost:8080`

### 2. Start the Emulator
```bash
~/Library/Android/sdk/emulator/emulator -avd Medium_Phone_API_36.1 &
```

Wait for emulator to fully boot (about 30-60 seconds)

### 3. Build and Install the App
```bash
cd android
./gradlew clean assembleDebug
~/Library/Android/sdk/platform-tools/adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### 4. Launch the App
```bash
~/Library/Android/sdk/platform-tools/adb shell am start -n com.geofence/.MainActivity
```

## Login Credentials
- **Username**: `testuser`
- **Password**: `password123`

## Test GPS Location
After logging in, simulate GPS location changes:

```bash
# Inside geofence (Media, PA) - triggers auto clock-in
~/Library/Android/sdk/platform-tools/adb emu geo fix -75.3876 39.9187

# Outside geofence - triggers exit notification
~/Library/Android/sdk/platform-tools/adb emu geo fix -75.3876 39.9232
```

## Quick Restart (if already built)
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
