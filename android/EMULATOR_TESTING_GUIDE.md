# 📱 Emulator Testing Quick Start Guide

## 🚀 Quick Setup (5 minutes)

### 1. Start Backend
```bash
cd backend
java -cp "gradle-8.5/lib/*" org.gradle.launcher.GradleMain run
```
✅ Backend running at `http://localhost:8080`

### 2. Open Android Studio
```bash
cd android
# Open in Android Studio
```

### 3. Run App
1. Select emulator (create one if needed)
2. Click green ▶️ Run button
3. Wait for app to launch

### 4. Login
- Username: `testuser` (pre-filled)
- Password: `password123` (pre-filled)
- Tap **LOGIN**

---

## 🗺️ Simulating GPS Location

### Test Coordinates (Media, PA)

| Location | Latitude | Longitude | Description |
|----------|----------|-----------|-------------|
| **Inside** | 39.9187 | -75.3876 | Geofence center |
| **Outside** | 39.9232 | -75.3876 | 500m north (outside) |
| **Far Away** | 39.9287 | -75.3876 | 1000m north (far) |

### Method 1: Extended Controls (Easiest)

1. Click **"..."** on emulator toolbar
2. Select **"Location"** tab
3. Enter coordinates:
   ```
   Latitude: 39.9187
   Longitude: -75.3876
   ```
4. Click **"SEND"**

### Method 2: Command Line (Fastest)

```bash
# Inside geofence (triggers ENTER)
adb emu geo fix -75.3876 39.9187

# Wait 5 seconds...

# Outside geofence (triggers EXIT)
adb emu geo fix -75.3876 39.9232
```

---

## ✅ Test Scenarios

### Scenario 1: Automatic Clock-In (Geofence Entry)

**Steps**:
1. Start outside geofence: `adb emu geo fix -75.3876 39.9232`
2. Wait for UI to show "Outside Geofence" (red)
3. Move inside: `adb emu geo fix -75.3876 39.9187`

**Expected**:
- ✅ Notification: "Entered Work Area"
- ✅ Status changes to "Inside Geofence" (green)
- ✅ New "CLOCK_IN" event appears in schedule
- ✅ Backend receives POST `/api/schedule/clock-in`

### Scenario 2: Automatic Exit Event (Geofence Exit)

**Steps**:
1. Start inside geofence: `adb emu geo fix -75.3876 39.9187`
2. Wait for UI to show "Inside Geofence" (green)
3. Move outside: `adb emu geo fix -75.3876 39.9232`

**Expected**:
- ✅ Notification: "Left Work Area"
- ✅ Status changes to "Outside Geofence" (red)
- ✅ New "EXIT" event appears in schedule
- ✅ Distance shown (e.g., "Distance: 400.0 meters")
- ✅ Backend receives POST `/api/schedule/exit`

### Scenario 3: Manual Clock-In/Out

**Steps**:
1. Set any location
2. Tap **"CLOCK IN"** button
3. Wait for success toast
4. Tap **"CLOCK OUT"** button

**Expected**:
- ✅ Success messages shown
- ✅ Events added to schedule
- ✅ Duration calculated for clock-out

### Scenario 4: Real-Time Distance Tracking

**Steps**:
1. Start at center: `adb emu geo fix -75.3876 39.9187`
   - Distance: ~0 meters
2. Move 50m: `adb emu geo fix -75.3876 39.9182`
   - Distance: ~50 meters
3. Move 100m: `adb emu geo fix -75.3876 39.9178`
   - Distance: ~100 meters (boundary)

**Expected**:
- ✅ Distance updates in real-time
- ✅ Color changes at boundary (green → red)

---

## 🔍 Debugging

### Check Backend Connection
```bash
# From emulator
adb shell
curl http://10.0.2.2:8080/api/geofence

# Should return: {"error":"unauthorized","message":"Invalid or expired token"}
# (This is correct - means backend is reachable)
```

### View Logs
In Android Studio, filter LogCat by:
```
GeofenceReceiver
```

Look for:
```
D/GeofenceReceiver: Entered geofence
D/GeofenceReceiver: Clock-in successful
```

### Common Issues

**❌ "Network error"**
- ✅ Check backend is running: `curl http://localhost:8080/api/geofence`
- ✅ Try different backend URL in `build.gradle.kts`

**❌ Geofence not triggering**
- ✅ Enable "Mock locations" in Developer Options
- ✅ Set app as mock location provider
- ✅ Restart app after changing location

**❌ Location not updating**
- ✅ Grant location permissions
- ✅ Send location via Extended Controls manually
- ✅ Tap refresh button in app

---

## 📊 Monitoring

### Watch Backend Logs
```bash
cd backend
tail -f logs/application.log  # if logging configured
```

### Watch Network Traffic
In Android Studio:
1. Open **App Inspection** tab
2. Select **Network Inspector**
3. Watch API calls in real-time

---

## 🎯 Full Test Flow (End-to-End)

```bash
# 1. Start outside
adb emu geo fix -75.3876 39.9232
# UI: "Outside Geofence" (red), distance ~500m

# 2. Wait 3 seconds, then enter
adb emu geo fix -75.3876 39.9187
# UI: "Inside Geofence" (green)
# Notification: "Entered Work Area"
# Event: "CLOCK_IN"

# 3. Wait 10 seconds (simulate work)

# 4. Exit geofence
adb emu geo fix -75.3876 39.9232
# UI: "Outside Geofence" (red)
# Notification: "Left Work Area"
# Event: "EXIT" with distance

# 5. Manual clock-out
# Tap "CLOCK OUT" button
# Event: "CLOCK_OUT" with duration

# 6. Check schedule
# Scroll down to see all 3 events
```

Expected schedule:
1. **CLOCK_OUT** - just now - Duration: XX min
2. **EXIT** - 10 seconds ago - Distance: 400.0 m
3. **CLOCK_IN** - 10 seconds ago - Location: 39.9187, -75.3876

---

## 🧪 Advanced Testing

### Test GPX Route (Simulated Journey)

Create `test_route.gpx`:
```xml
<?xml version="1.0"?>
<gpx version="1.1">
  <trk>
    <trkseg>
      <!-- Start outside -->
      <trkpt lat="39.9232" lon="-75.3876"><time>2024-01-01T09:00:00Z</time></trkpt>
      <!-- Walk to geofence -->
      <trkpt lat="39.9210" lon="-75.3876"><time>2024-01-01T09:01:00Z</time></trkpt>
      <!-- Enter geofence -->
      <trkpt lat="39.9187" lon="-75.3876"><time>2024-01-01T09:02:00Z</time></trkpt>
      <!-- Stay inside -->
      <trkpt lat="39.9187" lon="-75.3876"><time>2024-01-01T09:30:00Z</time></trkpt>
      <!-- Leave geofence -->
      <trkpt lat="39.9210" lon="-75.3876"><time>2024-01-01T09:31:00Z</time></trkpt>
      <trkpt lat="39.9232" lon="-75.3876"><time>2024-01-01T09:32:00Z</time></trkpt>
    </trkseg>
  </trk>
</gpx>
```

Load in emulator:
1. Extended Controls → Location
2. **Load GPX/KML** → Select file
3. Click **▶️ Play route**
4. Set speed to 2x for faster testing

---

## 📱 Testing on Physical Device

If you want to test on a real Android phone:

1. Enable Developer Options
2. Enable USB Debugging
3. Change backend URL to your computer's IP:
   ```kotlin
   // In build.gradle.kts
   buildConfigField("String", "API_BASE_URL", "\"http://YOUR_IP:8080/api/\"")
   ```
4. Find your IP: `ifconfig` (Mac/Linux) or `ipconfig` (Windows)
5. Walk to actual location (39.9187, -75.3876) in Media, PA!

---

## ✨ Pro Tips

1. **Keep LogCat open** - Essential for debugging
2. **Use command line** - Faster than clicking through UI
3. **Test edge cases** - Right at 100m boundary
4. **Test background** - Lock phone/minimize app
5. **Test battery saver** - May affect location updates
6. **Clear app data** - Fresh start if issues occur

---

## 🎉 Success Criteria

Your app is working correctly if:
- ✅ Login works with test credentials
- ✅ Geofence info loads from backend
- ✅ Distance updates when moving GPS location
- ✅ Entering geofence triggers notification + clock-in
- ✅ Exiting geofence triggers notification + exit event
- ✅ Manual buttons work
- ✅ Schedule shows events in correct order
- ✅ Backend receives all API calls

Happy Testing! 🚀
