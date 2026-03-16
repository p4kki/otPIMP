# OTP Forwarder

Internal office tool for forwarding OTP SMS messages to employees over local Wi-Fi.

## Overview

A single Android tablet with a SIM card receives OTP SMS messages from a database system. It parses each SMS and forwards the OTP in real-time to the correct employee's browser or device via Server-Sent Events (SSE). No internet required. No data leaves the office network.

## Architecture

```
[Database System] → SMS → [Android Tablet (SIM)]
                              │
                              ├─ Kotlin SmsReceiver → OtpForegroundService (NanoHTTPD on port 8080)
                              │                                          │
                              │                                          ├── SMS Parser
                              │                                          ├── SQLite Database
                              │                                          └── SSE Endpoint
                              │
[Office Wi-Fi — local network only]
                              │
          ┌───────────────────┴───────────────────┐
          ▼                                           ▼
   PC Browser tabs                              Employee Android devices
   http://<tablet-ip>:8080                  (Future: Flutter app in beta)
```

## Features (Alpha)

- Real-time OTP forwarding via SSE
- Employee identification by name (stored in browser localStorage)
- Audio alert + visual flash on OTP arrival
- 2-minute countdown timer with visual ring
- Automatic server start on device boot
- SMS permission handling
- SQLite logging (permanent, never deleted)
- Failed parse logging for manual review

## Tech Stack

| Layer | Technology |
|-------|-------------|
| Backend | FastAPI + SQLite (Android app hosts NanoHTTPD) |
| SMS Bridge | Kotlin BroadcastReceiver |
| Web Server | NanoHTTPD (embedded in Android app) |
| Frontend | Vanilla JS (single HTML file, served statically) |
| Realtime | Server-Sent Events (SSE) |

## Project Structure

```
otpimp/
├── app/
│   ├── src/main/
│   │   ├── kotlin/com/nesto/otpimp/
│   │   │   ├── MainActivity.kt         # UI for status/permissions
│   │   │   ├── OtpForegroundService.kt # Foreground service with web server
│   │   │   ├── OtpServer.kt            # NanoHTTPD server + SSE handling
│   │   │   ├── SmsReceiver.kt          # BroadcastReceiver for incoming SMS
│   │   │   └── BootReceiver.kt         # Starts service on device boot
│   │   └── AndroidManifest.xml
│   └── build.gradle.kts
├── frontend/
│   └── index.html                       # Employee UI (served by app)
├── backend/
│   ├── main.py                          # FastAPI version (Termux alternative)
│   └── otp_log.db                      # SQLite database
├── build.gradle.kts
├── settings.gradle.kts
└── CLAUDE.md                            # Project documentation
```

## Prerequisites

- Android 8.1+ (API 27)
- Termux or Android Studio for building
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
2. Grant SMS permissions when prompted
3. The server starts automatically on port 8080
4. Find the tablet's IP address in the app (or from Settings → Wi-Fi)
5. Employees visit `http://<tablet-ip>:8080` in their browser

## Testing Without SMS

Send a test SMS via the app's built-in endpoint:

```bash
curl -X POST http://localhost:8080/sms \
  -H "Content-Type: application/json" \
  -d '{"sender": "+971500000000", "body": "Ajmal, your OTP is 482910"}'
```

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

## Beta (Planned)

- JWT authentication with user/admin roles
- Admin panel for viewing full log
- Flutter Android app for employees
- Employee management UI

## License

Internal use only