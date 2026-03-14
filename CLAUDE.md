# CLAUDE.md — OTP Forwarding System

## Project overview

Internal office tool for a data entry team (~10 employees). A single Android tablet
holds a SIM card that receives OTP SMS messages from a database system. The tablet
parses each SMS and forwards it in real time to the correct employee's browser or
Android device over the local office Wi-Fi. No internet required. No third-party
services. OTPs are sensitive — nothing leaves the office network.

This is an internal tool, not a public product. Security and reliability matter more
than polish in alpha. Non-technical end users (data entry staff) must never see an
error or be required to do anything technical.

---

## Roles

| Role | Description |
|---|---|
| `user` | Data entry employee. Sees only their own OTP alert. No log access. |
| `admin` | Manager / IT. Sees full OTP log. Manages employee profiles. Added in beta. |

Alpha has no auth — employees identify by typing their name once (saved in browser
localStorage). Auth (JWT, user/admin roles) is added in beta.

---

## Phased build plan

### Alpha (current focus)
- FastAPI backend on the tablet (Termux)
- SQLite for OTP log storage
- SSE (Server-Sent Events) to push OTPs to connected browser clients
- Kotlin SMS bridge (BroadcastReceiver → local HTTP POST to FastAPI)
- React frontend served from FastAPI — employee types name, receives OTP alert,
  2-minute auto-clear countdown, alert sound on arrival
- No auth in alpha

### Beta
- JWT auth — user role and admin role
- Flutter Android app (Vibecoded) replaces browser tab for employees on Android
  — hits same FastAPI endpoints, no backend changes needed
- React admin panel — full log view, employee profile management
- FastAPI stays as the backend throughout beta

### Future
- Consider swapping to Django for built-in user management and admin panel
- SQLite → Postgres if log volume grows
- Role-gated log access views

---

## Tech stack

| Layer | Technology | Notes |
|---|---|---|
| Backend | Python · FastAPI | Developer's primary comfort zone |
| Database | SQLite (alpha) | File at `/data/otp_log.db` on tablet |
| Realtime push | SSE (Server-Sent Events) | Built into browsers, no WS library needed |
| SMS bridge | Kotlin · Android BroadcastReceiver | ~40 lines, posts to FastAPI locally |
| Frontend (alpha) | React · built in Deepsite | Served as static files from FastAPI |
| Mobile (beta) | Flutter · Vibecoded | Connects to same FastAPI via HTTP + SSE |
| Auth (beta) | JWT via `python-jose` + `passlib` | User and admin roles |
| Future backend | Django (optional migration) | For user mgmt, built-in admin panel |

---

## Architecture

```
[Database system]
      │ SMS
      ▼
[Android Tablet — SIM device]
  ├── Kotlin BroadcastReceiver
  │     └── HTTP POST → FastAPI /internal/sms
  └── FastAPI (Termux, port 8080)
        ├── SMS parser (name · ID · phone · OTP extraction)
        ├── SQLite logger
        ├── SSE endpoint  GET /stream
        └── Static files  React frontend

[Office Wi-Fi — no internet needed]
  ├── PC Browser tabs  → http://<tablet-ip>:8080
  └── Android phones   → same URL or Flutter app (beta)
```

OTP flow:
1. SMS arrives on tablet SIM
2. Kotlin receiver captures it, POSTs raw SMS body to `POST /internal/sms`
3. FastAPI parser extracts identifier (name / ID / phone) and OTP code
4. OTP stored in SQLite with timestamp, sender, raw message
5. FastAPI broadcasts event to all SSE subscribers
6. Each browser/app checks if the identifier matches the logged-in employee
7. Match → loud alert + OTP displayed with 2-min countdown
8. No match → silently ignored
9. After 2 min → OTP marked expired on screen, log entry remains permanently

---

## Project structure

```
otp-forwarder/
├── CLAUDE.md                  ← this file
├── backend/
│   ├── main.py                ← FastAPI app entry point
│   ├── routers/
│   │   ├── sms.py             ← POST /internal/sms (from Kotlin bridge)
│   │   ├── stream.py          ← GET /stream (SSE endpoint)
│   │   └── admin.py           ← admin routes (beta)
│   ├── services/
│   │   ├── parser.py          ← SMS text parsing logic
│   │   ├── broadcaster.py     ← SSE connection manager
│   │   └── logger.py          ← SQLite read/write
│   ├── models/
│   │   └── otp.py             ← Pydantic models
│   ├── db/
│   │   └── schema.sql         ← SQLite schema
│   ├── config.py              ← env vars, constants
│   └── requirements.txt
├── android-sms-bridge/
│   └── SmsReceiver.kt         ← Kotlin BroadcastReceiver (~40 lines)
└── frontend/
    └── (React app — built in Deepsite, dropped here as static build)
```

---

## Key implementation details

### SMS parser (`services/parser.py`)
The SMS body contains a mix of identifiers — full name, employee ID, or phone number.
Parser must be flexible:
- Try regex for known ID formats first (e.g. `EMP-\d+`, `ID: \d+`)
- Fall back to fuzzy name matching against employee list
- Extract OTP — typically 4–8 digit numeric code
- Log the raw SMS body always, even if parsing fails (for manual review)
- Never discard an SMS silently — failed parses go to a `failed_parse` table

### SSE broadcaster (`services/broadcaster.py`)
- Maintain a list of active SSE connections (one per browser tab / app)
- On new OTP: iterate connections, send JSON event to all
- Each client does its own identity matching — server broadcasts everything
- Handle disconnects gracefully (remove from list, no crash)

### SQLite schema
```sql
CREATE TABLE otp_log (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    received_at TEXT NOT NULL,          -- ISO8601 timestamp
    raw_sms     TEXT NOT NULL,          -- full original SMS body
    sender      TEXT,                   -- phone number SMS came from
    identifier  TEXT,                   -- parsed name / ID / phone
    otp_code    TEXT,                   -- extracted OTP
    parse_ok    INTEGER DEFAULT 1       -- 0 if parsing failed
);

CREATE TABLE failed_parse (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    received_at TEXT NOT NULL,
    raw_sms     TEXT NOT NULL,
    sender      TEXT
);
```

### OTP expiry (frontend)
- On OTP arrival: start a 120-second countdown timer in the browser
- At 0: dim the OTP, show "Expired — check log" message
- Log entry on the tablet is never deleted — permanent record
- Auto-clear is purely a UI behaviour, not a backend delete

### Kotlin SMS bridge
- Registered in `AndroidManifest.xml` to receive `android.provider.Telephony.SMS_RECEIVED`
- On receive: concatenate all SMS parts, POST to `http://127.0.0.1:8080/internal/sms`
- Body: `{ "sender": "+971...", "body": "Your OTP for Ajmal Al-... is 482910" }`
- No retry logic needed in alpha — local loopback is effectively instant

### FastAPI internal endpoint security
- `/internal/sms` only accepts connections from `127.0.0.1` (loopback)
- Add a shared secret header (`X-Internal-Token`) in alpha as a basic guard
- In beta this moves behind JWT

---

### SMS parser (`services/parser.py`)

Parses incoming SMS to extract the recipient identifier and OTP code.

**Inputs:** Raw SMS body, sender phone number
**Outputs:** Identifier (employee ID, phone, or name), OTP code, parse success flag

Matching priority:
1. Employee ID — regex `EMP-\d{3,6}` or `ID:\s*\d+`
2. Phone number — regex `\+?\d{10,15}`, matched against employees.json
3. Name — fuzzy match (difflib, ratio ≥ 0.85) against employees.json

OTP extraction: First sequence of 4–8 digits not matching an employee ID or phone.

**On parse failure:** Insert into `failed_parse` table. Do not discard. Consider emitting
an SSE event `{"type": "parse_error"}` so an admin dashboard (beta) can alert.

---

## Environment / running locally

### Tablet (Termux)
```bash
pkg install python
pip install fastapi uvicorn aiosqlite python-multipart
cd backend
uvicorn main:app --host 0.0.0.0 --port 8080
```

### Dev machine (for backend development)
```bash
python -m venv venv
source venv/bin/activate      # Windows: venv\Scripts\activate
pip install -r backend/requirements.txt
uvicorn backend.main:app --reload --port 8080
```

Mock SMS injection for testing (no tablet needed):
```bash
curl -X POST http://localhost:8080/internal/sms \
  -H "Content-Type: application/json" \
  -H "X-Internal-Token: dev-secret" \
  -d '{"sender": "+97150000000", "body": "Dear Ajmal, your OTP is 482910. Valid for 2 minutes."}'
```

---

## Constraints and rules

- **No internet dependency** — the system must work with Wi-Fi but no internet access
- **No third-party cloud** — OTPs never leave the office network
- **No admin rights on PCs** — browser-only solution for Windows clients
- **Non-technical users** — no error screens, no setup steps for employees
- **SQLite is permanent** — never run DELETE on `otp_log` or `failed_parse` tables
- **Parser failures are not silent** — always log to `failed_parse`, consider a tablet-side alert for the admin
- **SSE not WebSockets** — keep it simple, SSE is sufficient and has no library dependency on the client
- **Port 8080** — fixed, no dynamic ports, employees bookmark the URL

---

## What Claude should know when helping

- Developer is comfortable with Python and FastAPI
- Kotlin is unfamiliar — keep the SMS bridge minimal and heavily commented
- Flutter frontend will be Vibecoded — FastAPI endpoints must be clean REST + SSE,
  well-documented so Vibecode can generate the Flutter client from them
- React frontend is being built in Deepsite — provide API contract clearly
- This is a QA/proxy role — the actual users are a non-technical data entry team
- Alpha ships first, keep it simple and working over clever and complex
- Django migration is future — do not couple alpha code to Django patterns
- When suggesting changes, always state if it affects the Kotlin bridge,
  the Flutter contract, or the React frontend so the right tool gets updated