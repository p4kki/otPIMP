```markdown
# Beta.md
## OTP PIMP (Minimal Alpha)

## What this is

Office tablet receives OTP SMS messages. Forwards them to employee browsers over local Wi-Fi. That's it.

- ~10 employees
- No internet required
- No auth (alpha)
- No admin panel (alpha)

---

## How it works

```
SMS arrives → Kotlin app POSTs to FastAPI → FastAPI broadcasts via SSE → Browser shows alert
```

1. SMS hits tablet SIM
2. Kotlin BroadcastReceiver sends SMS body to `POST http://127.0.0.1:8080/sms`
3. FastAPI parses out the OTP and employee name
4. FastAPI pushes to all connected browsers via SSE
5. Browser checks if name matches, shows alert if yes

---

## Tech stack

- **Backend:** FastAPI + SQLite (runs in Termux on tablet)
- **SMS bridge:** Kotlin BroadcastReceiver (~40 lines)
- **Frontend:** React (single page, served from FastAPI)
- **Realtime:** SSE (no WebSocket library needed)

---

## Project structure

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

Keep it in one `main.py` until it gets unwieldy. No routers, no services folder yet.

---

## Database

One table:

```sql
CREATE TABLE otp_log (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    received_at TEXT DEFAULT CURRENT_TIMESTAMP,
    raw_sms TEXT NOT NULL,
    employee_name TEXT,
    otp_code TEXT
);
```

Never delete rows. Failed parses still get logged with `employee_name` and `otp_code` as NULL.

---

## API

**POST /sms** — Kotlin bridge calls this

```json
{"sender": "+971...", "body": "Ahmed, your OTP is 482910"}
```

**GET /stream** — Browser connects here for SSE

```
event: otp
data: {"employee_name": "Ahmed", "otp_code": "482910"}
```

**GET /** — Serves the React frontend

---

## Frontend behavior

1. Employee types their name once (saved in localStorage)
2. Page connects to `/stream`
3. On OTP event: if name matches, play sound + show OTP
4. Show 2-minute countdown, then dim the OTP
5. No match = ignore silently

---

## Running it

Tablet (Termux):

```bash
pip install fastapi uvicorn aiosqlite
uvicorn main:app --host 0.0.0.0 --port 8080
```

Test without tablet:

```bash
curl -X POST http://localhost:8080/sms \
  -H "Content-Type: application/json" \
  -d '{"sender": "+971500000000", "body": "Ahmed your OTP is 482910"}'
```

---

## Parser rules

Keep it dumb for now:

- OTP = first 4–8 digit number found
- Employee name = hardcoded list in `main.py`, fuzzy match against SMS body
- If parse fails, log it anyway with NULLs

```python
EMPLOYEES = ["Ahmed", "Fatima", "Omar", "Sara", "Hassan"]
```

Refine the parser once you see real SMS formats.

---

## What's deferred to beta

- Authentication (JWT)
- Admin panel
- Flutter app
- Proper project structure (routers, services)
- Employee management UI
- Failed parse alerts

---

## Constraints

- Port 8080, always
- No internet dependency
- Employees bookmark `http://<tablet-ip>:8080`
- Keep Kotlin minimal — developer is unfamiliar with it

---

## Notes for Claude

- Developer knows Python/FastAPI well
- Kotlin is unfamiliar — keep SMS bridge minimal with comments
- Alpha = working > clever
- When suggesting code, note if it affects backend, android, or frontend
```

---

This is ready to drop into your project root. Want me to generate the `main.py` or `SmsReceiver.kt` next?