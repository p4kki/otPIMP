# Beta.md (Archived)

> **This document describes the OLD architecture.** The current implementation uses a self-contained Android app with NanoHTTPD. See README.md for the updated architecture.

---

## Old: FastAPI + Termux Approach

The original design used FastAPI running in Termux on the tablet:

```
SMS arrives → Kotlin app POSTs to FastAPI → FastAPI broadcasts via SSE → Browser shows alert
```

### Project Structure (Old)

```
otp-forwarder/
├── backend/
│   ├── main.py           ← entire FastAPI app
│   ├── otp_log.db        ← SQLite database (auto-created)
│   └── requirements.txt  ← fastapi, uvicorn, aiosqlite
├── android/
│   └── SmsReceiver.kt    ← Kotlin SMS listener
└── frontend/
    └── index.html        ← React build output (static)
```

### Database (Old)

```sql
CREATE TABLE otp_log (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    received_at TEXT DEFAULT CURRENT_TIMESTAMP,
    raw_sms TEXT NOT NULL,
    employee_name TEXT,
    otp_code TEXT
);
```

---

## Current Architecture

See README.md for the current implementation using:
- Pure Android app with NanoHTTPD
- Room database for persistence
- Clean Architecture (data/domain/network layers)
- View Binding for UI

---

## What Changed

| Old | New |
|-----|-----|
| FastAPI in Termux | NanoHTTPD in Android app |
| SQLite via aiosqlite | Room database |
| POST to 127.0.0.1:8080 | Direct function call (same process) |
| Kotlin minimal bridge | Full Kotlin app with use cases |
| No View Binding | View Binding + ConstraintLayout |

---

## Deferred Features (Still Pending)

- Authentication (JWT)
- Admin panel
- Flutter app
- Employee management UI
- Failed parse alerts
