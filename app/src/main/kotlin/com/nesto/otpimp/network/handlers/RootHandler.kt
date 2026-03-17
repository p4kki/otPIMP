package com.nesto.otpimp.network.handlers

import com.nesto.otpimp.util.Constants
import fi.iki.elonen.NanoHTTPD
import fi.iki.elonen.NanoHTTPD.Response.Status

class RootHandler {
    
    fun handle(): NanoHTTPD.Response {
        val html = """
            <!DOCTYPE html>
            <html lang="en">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>OTP Forwarder</title>
                <style>
                    * { box-sizing: border-box; margin: 0; padding: 0; }
                    body { 
                        font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
                        background: #1a1a2e; color: #eee; padding: 20px;
                        min-height: 100vh;
                    }
                    .container { max-width: 800px; margin: 0 auto; }
                    h1 { color: #00d9ff; margin-bottom: 10px; }
                    .status { 
                        display: inline-flex; align-items: center; gap: 8px;
                        padding: 8px 16px; border-radius: 20px;
                        background: #16213e; margin-bottom: 20px;
                    }
                    .status.connected { color: #00ff88; }
                    .status.disconnected { color: #ff4757; }
                    .dot { 
                        width: 10px; height: 10px; border-radius: 50%;
                        background: currentColor; animation: pulse 2s infinite;
                    }
                    @keyframes pulse { 0%, 100% { opacity: 1; } 50% { opacity: 0.5; } }
                    .card {
                        background: #16213e; border-radius: 12px;
                        padding: 20px; margin-bottom: 16px;
                        border-left: 4px solid #00d9ff;
                    }
                    .card.otp { border-left-color: #00ff88; }
                    .card h3 { color: #00d9ff; margin-bottom: 8px; font-size: 14px; text-transform: uppercase; }
                    .otp-code { font-size: 32px; font-weight: bold; color: #00ff88; letter-spacing: 4px; }
                    .meta { color: #888; font-size: 13px; margin-top: 8px; }
                    .employee { color: #ffd93d; font-weight: 500; }
                    #messages { max-height: 60vh; overflow-y: auto; }
                    .empty { text-align: center; color: #666; padding: 40px; }
                    pre { background: #0f0f23; padding: 12px; border-radius: 8px; overflow-x: auto; font-size: 12px; }
                </style>
            </head>
            <body>
                <div class="container">
                    <h1>📱 OTP Forwarder</h1>
                    <div class="status disconnected" id="status">
                        <span class="dot"></span>
                        <span id="statusText">Connecting...</span>
                    </div>
                    
                    <div class="card">
                        <h3>API Endpoints</h3>
                        <pre>GET /stream    - SSE stream of OTP messages
GET /health    - Server health and stats
GET /employees - List of known employees</pre>
                    </div>
                    
                    <h2 style="margin: 20px 0 10px; color: #888; font-size: 14px;">RECENT MESSAGES</h2>
                    <div id="messages">
                        <div class="empty">Waiting for OTP messages...</div>
                    </div>
                </div>
                
                <script>
                    const messagesEl = document.getElementById('messages');
                    const statusEl = document.getElementById('status');
                    const statusText = document.getElementById('statusText');
                    let messageCount = 0;
                    
                    function connect() {
                        const es = new EventSource('/stream');
                        
                        es.onopen = () => {
                            statusEl.className = 'status connected';
                            statusText.textContent = 'Connected';
                        };
                        
                        es.addEventListener('otp', (e) => {
                            const data = JSON.parse(e.data);
                            addMessage(data);
                        });
                        
                        es.onerror = () => {
                            statusEl.className = 'status disconnected';
                            statusText.textContent = 'Disconnected - Reconnecting...';
                            es.close();
                            setTimeout(connect, 3000);
                        };
                    }
                    
                    function addMessage(data) {
                        if (messageCount === 0) {
                            messagesEl.innerHTML = '';
                        }
                        messageCount++;
                        
                        const card = document.createElement('div');
                        card.className = 'card otp';
                        card.innerHTML = `
                            <h3>OTP Received</h3>
                            <div class="otp-code">${'$'}{data.otp_code || '----'}</div>
                            <div class="meta">
                                ${'$'}{data.employee_name ? `<span class="employee">${'$'}{data.employee_name}</span> • ` : ''}
                                From: ${'$'}{data.sender} • 
                                ${'$'}{new Date(data.received_at).toLocaleTimeString()}
                            </div>
                        `;
                        messagesEl.insertBefore(card, messagesEl.firstChild);
                        
                        // Keep only last 50 messages
                        while (messagesEl.children.length > 50) {
                            messagesEl.removeChild(messagesEl.lastChild);
                        }
                    }
                    
                    connect();
                </script>
            </body>
            </html>
        """.trimIndent()
        
        return NanoHTTPD.newFixedLengthResponse(
            Status.OK,
            Constants.MimeTypes.HTML,
            html
        )
    }
}