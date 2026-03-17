# OTP Forwarder

Internal office tool for forwarding OTP SMS messages to employees over local Wi-Fi.

## Overview

A single Android tablet with a SIM card receives OTP SMS messages from a database system. It parses each SMS and forwards the OTP in real-time to the correct employee's browser or device via Server-Sent Events (SSE). No internet required. No data leaves the office network.

## Architecture

```
[Database System] → SMS → [Android Tablet (SIM)]
                              │
                              └─ SmsReceiver → OtpForegroundService
                                                     │
                                    ┌────────────────┼────────────────┐
                                    │                │                │
                               data/           domain/         network/
                            (Room DB)        (Use Cases)    (NanoHTTPD)
                                                                     │
                                                      ┌──────────────┼──────────────┐
                                                      ▼              ▼              ▼
                                                   /stream       /health         /sms

[Office Wi-Fi — local network only]
                              │
          ┌───────────────────┴───────────────────┐
          ▼                                           ▼
   PC Browser tabs                              Employee Android devices
   http://<tablet-ip>:8080                  (Future: Flutter app in beta)
```

## Features

- Real-time OTP forwarding via SSE
- Employee identification by name (stored in browser localStorage)
- Audio alert + visual flash on OTP arrival
- 2-minute countdown timer with visual ring
- Automatic server start on device boot
- SMS permission handling with live status
- Room database for message persistence
- Built-in log viewer in app
- Service state tracking (messages received/broadcast)
- POST endpoint for testing without SMS

## Tech Stack

| Layer | Technology |
|-------|-------------|
| Backend | NanoHTTPD (embedded in Android app) |
| Database | Room (SQLite) |
| SMS Bridge | Kotlin BroadcastReceiver |
| Architecture | Clean Architecture (data/domain/network layers) |
| Frontend | Vanilla JS (single HTML file, served statically) |
| Realtime | Server-Sent Events (SSE) |
| Build | Gradle, KSP, ViewBinding |

## Project Structure

```
otpimp/
├── app/
│   ├── src/main/
│   │   ├── kotlin/com/nesto/otpimp/
│   │   │   ├── OtpApplication.kt         # Application class
│   │   │   ├── MainActivity.kt          # UI with View Binding
│   │   │   ├── di/
│   │   │   │   └── ServiceLocator.kt     # Dependency injection
│   │   │   ├── data/
│   │   │   │   ├── local/                # Room DB (OtpDatabase, OtpDao, OtpEntity)
│   │   │   │   ├── model/                # Data models (OtpMessage, ParsedSms, Result)
│   │   │   │   └── repository/           # Repository pattern
│   │   │   ├── domain/
│   │   │   │   ├── parser/               # SMS parsing logic
│   │   │   │   └── usecase/              # Business logic
│   │   │   ├── network/
│   │   │   │   ├── OtpHttpServer.kt      # NanoHTTPD server
│   │   │   │   ├── SseConnectionManager.kt
│   │   │   │   └── handlers/             # HTTP request handlers
│   │   │   ├── receiver/
│   │   │   │   ├── BootReceiver.kt       # Starts service on boot
│   │   │   │   └── SmsReceiver.kt        # Incoming SMS handler
│   │   │   ├── service/
│   │   │   │   ├── OtpForegroundService.kt
│   │   │   │   └── ServiceState.kt       # Runtime state tracking
│   │   │   └── util/
│   │   │       ├── Logger.kt             # In-app logging
│   │   │       ├── NetworkUtils.kt       # IP address utilities
│   │   │       └── Constants.kt
│   │   ├── res/layout/
│   │   │   └── activity_main.xml         # ConstraintLayout with stats
│   │   └── AndroidManifest.xml
│   └── build.gradle.kts
├── frontend/
│   └── index.html                       # Employee UI (served by app)
└── README.md
```

## API Endpoints

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/` | GET | Serve HTML frontend |
| `/stream` | GET | SSE event stream for real-time OTPs |
| `/health` | GET | Server health status, subscriber count |
| `/sms` | POST | Submit SMS for testing (no SIM required) |

### POST /sms Example

```bash
curl -X POST http://<tablet-ip>:8080/sms \
  -H "Content-Type: application/json" \
  -d '{"sender": "+971500000000", "body": "Ajmal, your OTP is 482910"}'
```

### Response

```json
{
  "status": "ok",
  "employee_name": "Ajmal",
  "otp_code": "482910"
}
```

### GET /health Response

```json
{
  "status": "ok",
  "subscribers": 5,
  "employees": "Ajmal,Fatima,Omar,Sara,Hassan,Khalid,Mariam,Yousuf,Layla,Ali"
}
```

## Prerequisites

- Android 8.0+ (API 26)
- Android Studio for building
- Wi-Fi network (no internet required for operation)

## Building

### Using Android Studio

1. Open the project in Android Studio
2. Build → Build APK
3. Install the APK on the tablet

### Command Line

```bash
./gradlew assembleDebug
```

The APK will be at `app/build/outputs/apk/debug/app-debug.apk`

## Running

1. Install the APK on the Android tablet
2. Grant SMS and notification permissions when prompted
3. The server starts automatically on port 8080
4. Find the tablet's IP address in the app UI
5. Employees visit `http://<tablet-ip>:8080` in their browser

## App Features

### Main Activity

- **Status**: Shows if server is running
- **IP Address**: Displays local Wi-Fi IP with port
- **Permissions**: Shows SMS permission status
- **Stats**: Live count of messages received and broadcast
- **Toggle**: Start/stop the server
- **View Logs**: Dialog showing recent app logs (up to 100 entries)

### Service State

The app tracks runtime statistics:
- Messages received via SMS
- Messages broadcast to SSE clients

## Employee Workflow

1. Open `http://<tablet-ip>:8080` in browser
2. Type your name and click Save (saved in localStorage)
3. Wait for OTPs — only your own OTPs will alert
4. On OTP: hear a beep, see a flash, enter the code within 2 minutes

## Current Employee List

Hardcoded in the app: Ajmal, Fatima, Omar, Sara, Hassan, Khalid, Mariam, Yousuf, Layla, Ali

## Security Notes

- No authentication (alpha) — employees identify themselves by name
- No data leaves the local network
- SMS permissions required for operation
- `android:allowBackup="false"` prevents data leakage

## Testing

### Unit Tests

```bash
./gradlew test
```

### Testing Without SMS

Use the POST endpoint or send a test SMS via the debug tools.

## Beta (Planned)

- JWT authentication with user/admin roles
- Admin panel for viewing full log
- Flutter Android app for employees
- Employee management UI
- Multiple SIM support

## License

Internal use only
