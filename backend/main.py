import asyncio
import json
import re
from datetime import datetime
from pathlib import Path

import aiosqlite
from fastapi import FastAPI, Request, HTTPException
from fastapi.responses import StreamingResponse, HTMLResponse
from fastapi.middleware.cors import CORSMiddleware

# ---------------------------------------------------------------------------
# Config
# ---------------------------------------------------------------------------

DB_PATH = Path(__file__).parent / "otp_log.db"
FRONTEND_PATH = Path(__file__).parent.parent / "frontend" / "index.html"

EMPLOYEES = ["Ajmal", "Fatima", "Omar", "Sara", "Hassan",
             "Khalid", "Mariam", "Yousuf", "Layla", "Ali"]

# ---------------------------------------------------------------------------
# App
# ---------------------------------------------------------------------------

app = FastAPI(title="OTP Forwarder", version="0.1.0-alpha")

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_methods=["*"],
    allow_headers=["*"],
)

subscribers: list[asyncio.Queue] = []

# ---------------------------------------------------------------------------
# DB
# ---------------------------------------------------------------------------

async def init_db():
    async with aiosqlite.connect(DB_PATH) as db:
        await db.execute("""
            CREATE TABLE IF NOT EXISTS otp_log (
                id            INTEGER PRIMARY KEY AUTOINCREMENT,
                received_at   TEXT    NOT NULL,
                raw_sms       TEXT    NOT NULL,
                sender        TEXT,
                employee_name TEXT,
                otp_code      TEXT
            )
        """)
        # Separate table for SMS that could not be parsed.
        # Never silently discard an unmatched SMS.
        await db.execute("""
            CREATE TABLE IF NOT EXISTS failed_parse (
                id          INTEGER PRIMARY KEY AUTOINCREMENT,
                received_at TEXT NOT NULL,
                raw_sms     TEXT NOT NULL,
                sender      TEXT
            )
        """)
        await db.commit()

@app.on_event("startup")
async def on_startup():
    await init_db()

# ---------------------------------------------------------------------------
# SMS parser
# ---------------------------------------------------------------------------

def parse_sms(body: str) -> tuple[str | None, str | None]:
    """
    Returns (employee_name, otp_code).
    Either value can be None if not found.

    OTP  — first 4-8 digit standalone number in the message.
    Name — first employee name found (case-insensitive substring match).
    """
    # Standalone digit sequence: not surrounded by other digits
    otp_match = re.search(r'(?<!\d)(\d{4,8})(?!\d)', body)
    otp_code = otp_match.group(1) if otp_match else None

    employee_name = None
    body_lower = body.lower()
    for emp in EMPLOYEES:
        if emp.lower() in body_lower:
            employee_name = emp
            break

    return employee_name, otp_code

# ---------------------------------------------------------------------------
# POST /sms  — called by the Kotlin SMS bridge on the tablet
# ---------------------------------------------------------------------------

@app.post("/sms")
async def receive_sms(request: Request):
    try:
        payload = await request.json()
    except Exception:
        raise HTTPException(status_code=400, detail="Invalid JSON")

    raw_sms = payload.get("body", "").strip()
    sender  = payload.get("sender", "").strip()

    if not raw_sms:
        raise HTTPException(status_code=400, detail="Missing 'body' field")

    now = datetime.utcnow().isoformat()
    employee_name, otp_code = parse_sms(raw_sms)

    async with aiosqlite.connect(DB_PATH) as db:
        if employee_name is None and otp_code is None:
            # Could not parse anything useful — log to failed_parse for
            # manual review. Never silently drop an incoming SMS.
            await db.execute(
                "INSERT INTO failed_parse (received_at, raw_sms, sender) VALUES (?, ?, ?)",
                (now, raw_sms, sender)
            )
        else:
            await db.execute(
                """INSERT INTO otp_log
                   (received_at, raw_sms, sender, employee_name, otp_code)
                   VALUES (?, ?, ?, ?, ?)""",
                (now, raw_sms, sender, employee_name, otp_code)
            )
        await db.commit()

    # Broadcast to every connected SSE client regardless of parse result
    event_payload = {
        "employee_name": employee_name,
        "otp_code":      otp_code,
        "received_at":   now,
    }
    dead: list[asyncio.Queue] = []
    for q in subscribers:
        try:
            await q.put(event_payload)
        except Exception:
            dead.append(q)
    for q in dead:
        try:
            subscribers.remove(q)
        except ValueError:
            pass

    return {"status": "ok", "employee_name": employee_name, "otp_code": otp_code}

# ---------------------------------------------------------------------------
# GET /stream  — SSE, one persistent connection per browser tab
# ---------------------------------------------------------------------------

async def event_generator(queue: asyncio.Queue):
    """
    Yields SSE-formatted strings.
    Sends a keepalive comment every 15s so the browser does not time out
    and any proxy (nginx etc.) does not drop the connection.
    """
    try:
        while True:
            try:
                data = await asyncio.wait_for(queue.get(), timeout=15.0)
                # json.dumps is critical here — your original used str(dict)
                # which produces Python repr with single quotes, not valid JSON.
                # The browser's JSON.parse() would silently fail on every event.
                yield f"event: otp\ndata: {json.dumps(data)}\n\n"
            except asyncio.TimeoutError:
                # SSE comment line — browser ignores it, connection stays alive
                yield ": keepalive\n\n"
    except asyncio.CancelledError:
        pass  # Client disconnected cleanly
    finally:
        try:
            subscribers.remove(queue)
        except ValueError:
            pass  # Already cleaned up elsewhere

@app.get("/stream")
async def stream():
    queue: asyncio.Queue = asyncio.Queue()
    subscribers.append(queue)
    return StreamingResponse(
        event_generator(queue),
        media_type="text/event-stream",
        headers={
            # Prevent nginx / Termux network stack from buffering the stream
            "Cache-Control":      "no-cache",
            "X-Accel-Buffering": "no",
            "Connection":         "keep-alive",
        },
    )

# ---------------------------------------------------------------------------
# GET /
# ---------------------------------------------------------------------------

@app.get("/", response_class=HTMLResponse)
async def serve_frontend():
    if FRONTEND_PATH.exists():
        return HTMLResponse(FRONTEND_PATH.read_text(encoding="utf-8"))
    return HTMLResponse(
        f"<h1 style='font-family:monospace;padding:2rem'>"
        f"Frontend not found.<br>Expected: {FRONTEND_PATH}</h1>",
        status_code=404,
    )

# ---------------------------------------------------------------------------
# GET /health 
# ---------------------------------------------------------------------------

@app.get("/health")
async def health():
    return {
        "status":      "ok",
        "subscribers": len(subscribers),
        "db":          str(DB_PATH),
        "employees":   EMPLOYEES,
    }

# ---------------------------------------------------------------------------
# Door
# ---------------------------------------------------------------------------

if __name__ == "__main__":
    import uvicorn
    uvicorn.run("main:app", host="0.0.0.0", port=8080, reload=True)