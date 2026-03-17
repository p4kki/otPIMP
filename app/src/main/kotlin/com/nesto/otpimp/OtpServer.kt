package com.nesto.otpimp

import android.content.Context
import android.util.Log
import fi.iki.elonen.NanoHTTPD
import fi.iki.elonen.NanoHTTPD.Response.Status
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.ConcurrentLinkedQueue

private const val TAG = "OtpServer"
private const val PORT = 8080

// ---------------------------------------------------------------------------
// OtpServer — singleton that owns the NanoHTTPD instance and all SSE state
// ---------------------------------------------------------------------------

object OtpServer {

    // Each SSE subscriber -> lambda that writes a formatted SSE string into its pipe
    //   store (callback, outputStream) -> close the streams

    private data class Subscriber(
        val callback: suspend (String) -> Unit,
        val pipe: PipedOutputStream
    )

    private val subscribers = ConcurrentLinkedQueue<Subscriber>()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val employees = listOf(
        "Ajmal", "Fatima", "Omar", "Sara", "Hassan",
        "Khalid", "Mariam", "Yousuf", "Layla", "Ali"
    )

    private var server: Http? = null
    private var appContext: Context? = null

    // ---------------------------------------------------------------------------
    // Lifecycle
    // ---------------------------------------------------------------------------

    fun start(context: Context) {
        appContext = context.applicationContext
        server = Http().also {
            it.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false)
            Log.d(TAG, "NanoHTTPD started on port $PORT")
        }
    }

    fun stop() {
        server?.stop()
        server = null
        // Close every open SSE pipe so NanoHTTPD threads unblock and exit
        subscribers.forEach { runCatching { it.pipe.close() } }
        subscribers.clear()
        Log.d(TAG, "OtpServer stopped")
    }

    // ---------------------------------------------------------------------------
    // SMS entry point — called directly by SmsReceiver (same process)
    // ---------------------------------------------------------------------------

    fun onSmsReceived(sender: String, rawBody: String) {
        val (employeeName, otpCode) = parseSms(rawBody)
        val now = ZonedDateTime.now(ZoneOffset.UTC)
            .format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"))

        Log.d(TAG, "SMS parsed: employee=$employeeName otp=$otpCode")

        val payload = JSONObject().apply {
            put("employee_name", employeeName)
            put("otp_code", otpCode)
            put("received_at", now)
            put("sender", sender)
            put("raw_sms", rawBody)
        }.toString()

        broadcast(payload)
    }

    // ---------------------------------------------------------------------------
    // Broadcast to all connected SSE clients
    // ---------------------------------------------------------------------------

    private fun broadcast(jsonPayload: String) {
        
        val frame = "event: otp\ndata: $jsonPayload\n\n"
        val dead = mutableListOf<Subscriber>()

        subscribers.forEach { sub ->
            scope.launch {
                try {
                    sub.callback(frame)
                } catch (e: Exception) {
                    Log.w(TAG, "Subscriber write failed, removing: ${e.message}")
                    dead.add(sub)
                }
            }
        }

        dead.forEach {
            subscribers.remove(it)
            runCatching { it.pipe.close() }
        }
    }

    // ---------------------------------------------------------------------------
    // SMS parser
    // ---------------------------------------------------------------------------

    private fun parseSms(body: String): Pair<String?, String?> {
        // OTP: first standalone 4-8 digit sequence
        val otpCode = Regex("(?<!\\d)(\\d{4,8})(?!\\d)").find(body)?.value

        // Name: first employee found in body (case-insensitive)
        val bodyLower = body.lowercase()
        val employeeName = employees.firstOrNull { it.lowercase() in bodyLower }

        return Pair(employeeName, otpCode)
    }

    fun getEmployees(): List<String> = employees

    // ---------------------------------------------------------------------------
    // NanoHTTPD inner class
    // ---------------------------------------------------------------------------

    private class Http : NanoHTTPD(PORT) {

        override fun serve(session: IHTTPSession): Response {
            return when {
                session.method == Method.GET  && session.uri == "/"       -> serveHtml()
                session.method == Method.GET  && session.uri == "/stream" -> serveSse()
                session.method == Method.GET  && session.uri == "/health" -> serveHealth()
                session.method == Method.POST && session.uri == "/sms"    -> handleSmsPost(session)
                else -> newFixedLengthResponse(Status.NOT_FOUND, MIME_PLAINTEXT, "Not found")
            }
        }

        // ── GET / ─────────────────────────────────────────────────────────────

        private fun serveHtml(): Response {
            return try {
                val ctx = appContext
                    ?: return newFixedLengthResponse(
                        Status.INTERNAL_ERROR, MIME_PLAINTEXT, "No context"
                    )
                val html = ctx.assets.open("index.html").bufferedReader().readText()
                newFixedLengthResponse(Status.OK, "text/html; charset=utf-8", html)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to serve index.html: ${e.message}")
                newFixedLengthResponse(
                    Status.INTERNAL_ERROR, MIME_PLAINTEXT,
                    "index.html not found in assets"
                )
            }
        }

        private fun serveSse(): Response {
            val output = PipedOutputStream()
            val input  = PipedInputStream(output, 8192)

            // CPR -- write a comment every 15 s so proxies don't drop the connection
            val keepaliveSub = Subscriber(
                callback = { frame ->
                    output.write(frame.toByteArray(Charsets.UTF_8))
                    output.flush()
                },
                pipe = output
            )
            subscribers.add(keepaliveSub)

            scope.launch {
                try {
                    while (true) {
                        kotlinx.coroutines.delay(15_000)
                        output.write(": keepalive\n\n".toByteArray(Charsets.UTF_8))
                        output.flush()
                    }
                } catch (_: Exception) {
                    // Pipe closed — client gone
                }
            }

            val response = newChunkedResponse(
                Status.OK,
                "text/event-stream",
                input
            )
            // Prevention to not appear every layer
            response.addHeader("Cache-Control", "no-cache")
            response.addHeader("X-Accel-Buffering", "no")
            response.addHeader("Connection", "keep-alive")
            response.addHeader("Access-Control-Allow-Origin", "*")
            return response
        }

        // ── POST /sms ─────────────────────────────────────────────────────────
        //
        // Accepts the same JSON shape as the FastAPI backend so curl tests
        // from a dev machine work identically:
        //   curl -X POST http://<tablet-ip>:8080/sms \
        //     -H "Content-Type: application/json" \
        //     -d '{"sender":"+971500000000","body":"Ajmal your OTP is 482910"}'

        private fun handleSmsPost(session: IHTTPSession): Response {
            return try {
                // NanoHTTPD requires us to read the body via parseBody
                val bodyMap = mutableMapOf<String, String>()
                session.parseBody(bodyMap)

                // The raw POST body lands in bodyMap["postData"]
                val raw = bodyMap["postData"]
                    ?: return newFixedLengthResponse(
                        Status.BAD_REQUEST, MIME_PLAINTEXT, "Empty body"
                    )

                val json   = JSONObject(raw)
                val sender = json.optString("sender", "unknown")
                val body   = json.optString("body", "").trim()

                if (body.isEmpty()) {
                    return newFixedLengthResponse(
                        Status.BAD_REQUEST, MIME_PLAINTEXT, "Missing 'body' field"
                    )
                }

                onSmsReceived(sender, body)

                val (name, otp) = parseSms(body)
                val responseJson = JSONObject().apply {
                    put("status", "ok")
                    put("employee_name", name)
                    put("otp_code", otp)
                }.toString()

                newFixedLengthResponse(Status.OK, "application/json", responseJson)

            } catch (e: Exception) {
                Log.e(TAG, "POST /sms error: ${e.message}")
                newFixedLengthResponse(
                    Status.INTERNAL_ERROR, MIME_PLAINTEXT, "Parse error: ${e.message}"
                )
            }
        }

        // ── GET /health ───────────────────────────────────────────────────────

        private fun serveHealth(): Response {
            val json = JSONObject().apply {
                put("status", "ok")
                put("subscribers", subscribers.size)
                put("employees", employees.joinToString(","))
            }.toString()
            return newFixedLengthResponse(Status.OK, "application/json", json)
        }
    }
}