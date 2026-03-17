package com.nesto.otpimp.network

import com.nesto.otpimp.util.Logger
import java.io.InputStream
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class SseOutputStream() : InputStream() {
    
    private val queue = LinkedBlockingQueue<ByteArray>()
    private val closed = AtomicBoolean(false)
    
    private var currentBuffer: ByteArray? = null
    private var currentPosition = 0
    
    companion object {
        private const val TAG = "SseOutputStream"
        private val POISON_PILL = ByteArray(0)
    }
    
    fun write(data: String) {
        if (!closed.get()) {
            queue.offer(data.toByteArray(Charsets.UTF_8))
        }
    }
    
    fun closeStream() {
        closed.set(true)
        queue.offer(POISON_PILL)
    }
    
    override fun read(): Int {
        Logger.v(TAG, "read() called, queue=${queue.size}, closed=${closed.get()}")
        while (true) {
            currentBuffer?.let { buffer ->
                if (currentPosition < buffer.size) {
                    return buffer[currentPosition++].toInt() and 0xFF
                }
                currentBuffer = null
                currentPosition = 0
            }
            
            if (closed.get() && queue.isEmpty()) {
                return -1
            }
            
            val next = queue.poll(100, TimeUnit.MILLISECONDS)
            
            when {
                next == null -> continue
                next === POISON_PILL -> return -1
                next.isNotEmpty() -> {
                    currentBuffer = next
                    currentPosition = 0
                }
            }
        }
    }
    
    override fun read(b: ByteArray, off: Int, len: Int): Int {
        if (len == 0) return 0
        
        val firstByte = read()
        if (firstByte == -1) return -1
        
        b[off] = firstByte.toByte()
        var bytesRead = 1
        
        while (bytesRead < len) {
            currentBuffer?.let { buffer ->
                val remaining = buffer.size - currentPosition
                val toCopy = minOf(remaining, len - bytesRead)
                System.arraycopy(buffer, currentPosition, b, off + bytesRead, toCopy)
                currentPosition += toCopy
                bytesRead += toCopy
                
                if (currentPosition >= buffer.size) {
                    currentBuffer = null
                    currentPosition = 0
                }
                return@let
            }
            
            val next = queue.poll() ?: break
            if (next === POISON_PILL) {
                queue.offer(POISON_PILL)
                break
            }
            if (next.isNotEmpty()) {
                currentBuffer = next
                currentPosition = 0
            }
        }
        
        return bytesRead
    }
    
    override fun available(): Int {
        return currentBuffer?.let { it.size - currentPosition } ?: 0
    }
    
    override fun close() {
        closeStream()
    }
}
