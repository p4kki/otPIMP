package com.nesto.otpimp.network

import com.nesto.otpimp.util.Logger
import java.io.InputStream
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * A blocking InputStream backed by a queue of byte-array chunks.
 *
 * Write SSE frames with write(String). NanoHTTPD reads from this as an
 * InputStream for its chunked HTTP response.
 *
 * Key contract for InputStream.read(b, off, len):
 *   - Block until at least 1 byte is available (or EOF).
 *   - Return as many of those bytes as fit in [len], but NEVER loop back
 *     to the queue looking for more. One chunk per call is correct and safe.
 *   - This avoids partial/malformed chunks that cause browsers to drop the
 *     connection with "interrupted while page was loading".
 */
class SseOutputStream : InputStream() {

    private val queue = LinkedBlockingQueue<ByteArray>()
    private val closed = AtomicBoolean(false)

    // The chunk currently being drained
    private var currentBuffer: ByteArray? = null
    private var currentPosition = 0

    companion object {
        private const val TAG = "SseOutputStream"
        private val POISON_PILL = ByteArray(0)
    }

    // ── Write side ────────────────────────────────────────────────────────

    fun write(data: String) {
        if (!closed.get()) {
            queue.offer(data.toByteArray(Charsets.UTF_8))
        }
    }

    fun closeStream() {
        if (closed.compareAndSet(false, true)) {
            queue.offer(POISON_PILL)
        }
    }

    // ── Read side (called by NanoHTTPD on its server thread) ──────────────

    /**
     * Single-byte read. Blocks until a byte is available or EOF.
     * Used as the fallback by the JDK when read(b,off,len) isn't overridden,
     * but we override read(b,off,len) directly so this is rarely hot.
     */
    override fun read(): Int {
        while (true) {
            // Drain currentBuffer first
            currentBuffer?.let { buf ->
                if (currentPosition < buf.size) {
                    return buf[currentPosition++].toInt() and 0xFF
                }
                currentBuffer = null
                currentPosition = 0
            }

            if (closed.get() && queue.isEmpty()) return -1

            val next = queue.poll(100, TimeUnit.MILLISECONDS) ?: continue

            if (next === POISON_PILL) return -1
            if (next.isNotEmpty()) {
                currentBuffer = next
                currentPosition = 0
            }
            // empty (but not poison) — loop again
        }
    }

    /**
     * Bulk read. Blocks until at least 1 byte is available, then returns
     * as many bytes from the CURRENT chunk as fit in [len].
     *
     * Critically: does NOT loop back to the queue for a second chunk.
     * Returning a partial buffer is perfectly valid per InputStream contract
     * and prevents NanoHTTPD from assembling malformed chunked-transfer frames.
     */
    override fun read(b: ByteArray, off: Int, len: Int): Int {
        if (len == 0) return 0

        // Step 1 — ensure we have a live buffer (blocks here if queue is empty)
        while (currentBuffer == null || currentPosition >= currentBuffer!!.size) {
            currentBuffer = null
            currentPosition = 0

            if (closed.get() && queue.isEmpty()) return -1

            val next = queue.poll(100, TimeUnit.MILLISECONDS) ?: continue

            if (next === POISON_PILL) return -1
            if (next.isNotEmpty()) {
                currentBuffer = next
                currentPosition = 0
            }
            // empty array (not poison) — loop
        }

        // Step 2 — copy as much of the current chunk as fits, then return
        val buf = currentBuffer!!
        val available = buf.size - currentPosition
        val toCopy = minOf(available, len)

        System.arraycopy(buf, currentPosition, b, off, toCopy)
        currentPosition += toCopy

        if (currentPosition >= buf.size) {
            currentBuffer = null
            currentPosition = 0
        }

        Logger.v(TAG, "Read chunk from queue: $toCopy bytes")
        return toCopy
    }

    override fun available(): Int =
        (currentBuffer?.size?.minus(currentPosition) ?: 0) +
                queue.sumOf { if (it === POISON_PILL) 0 else it.size }

    override fun close() {
        closeStream()
    }
}