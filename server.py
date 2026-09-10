#!/usr/bin/env python3
"""
ZenLock - Phone Locker Python Server
Zero-dependency HTTP server that serves the Phone Locker app,
displays your phone-ready local Wi-Fi URL, and logs focus sessions.
"""

import http.server
import socketserver
import socket
import json
import os
import sys
from datetime import datetime

PORT = 8000
DIRECTORY = os.path.dirname(os.path.abspath(__file__))
LOG_FILE = os.path.join(DIRECTORY, "focus_sessions.json")

def get_local_ip():
    """Finds the primary local IP address of the machine connected to Wi-Fi/LAN."""
    s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    try:
        # Doesn't even need to be reachable, just triggers routing table lookup
        s.connect(('8.8.8.8', 1))
        ip = s.getsockname()[0]
    except Exception:
        ip = '127.0.0.1'
    finally:
        s.close()
    return ip

class ZenLockHandler(http.server.SimpleHTTPRequestHandler):
    def __init__(self, *args, **kwargs):
        super().__init__(*args, directory=DIRECTORY, **kwargs)

    def end_headers(self):
        # Enable CORS and caching headers suitable for mobile PWA
        self.send_header('Access-Control-Allow-Origin', '*')
        self.send_header('Cache-Control', 'no-cache, must-revalidate')
        super().end_headers()

    def do_POST(self):
        """Handle API events like session logging from the phone."""
        if self.path == '/api/session':
            content_length = int(self.headers.get('Content-Length', 0))
            post_data = self.rfile.read(content_length)
            
            try:
                data = json.loads(post_data.decode('utf-8'))
                data['server_received_at'] = datetime.now().strftime("%Y-%m-%d %H:%M:%S")
                
                # Append to focus_sessions.json
                sessions = []
                if os.path.exists(LOG_FILE):
                    try:
                        with open(LOG_FILE, 'r', encoding='utf-8') as f:
                            sessions = json.load(f)
                    except Exception:
                        sessions = []
                
                sessions.append(data)
                with open(LOG_FILE, 'w', encoding='utf-8') as f:
                    json.dump(sessions, f, indent=2)

                event_type = data.get('event', 'unknown').upper()
                minutes = data.get('minutes', '?')
                print(f" [PHONE EVENT] {data['server_received_at']} -> Session {event_type} ({minutes} mins)")

                response = {"status": "success", "message": "Session recorded"}
                self.send_response(200)
                self.send_header('Content-Type', 'application/json')
                self.end_headers()
                self.wfile.write(json.dumps(response).encode('utf-8'))
                return
            except Exception as e:
                self.send_response(400)
                self.end_headers()
                self.wfile.write(json.dumps({"status": "error", "message": str(e)}).encode('utf-8'))
                return

        self.send_response(404)
        self.end_headers()

    def log_message(self, format, *args):
        # Clean terminal output: filter out noisy static GET requests
        if args and len(args) > 0 and 'POST' in str(args[0]):
            super().log_message(format, *args)

def main():
    local_ip = get_local_ip()
    phone_url = f"http://{local_ip}:{PORT}"
    localhost_url = f"http://localhost:{PORT}"

    print("\n" + "=" * 60)
    print("   ZENLOCK - PHONE LOCKER & FOCUS CHAMBER SERVER")
    print("=" * 60)
    print(" Server is running with ZERO external dependencies!\n")
    print(" HOW TO OPEN ON YOUR PHONE:")
    print(" 1. Make sure your phone is connected to the same Wi-Fi network.")
    print(" 2. Open your phone's browser (Chrome on Android, Safari on iPhone).")
    print(f" 3. Type this URL:  \033[1;36m{phone_url}\033[0m")
    print(" 4. Optional: Tap browser menu -> 'Add to Home Screen' for native fullscreen app mode!\n")
    print(" On this computer:")
    print(f" Local URL: \033[1;32m{localhost_url}\033[0m")
    print("=" * 60)
    print(" Press Ctrl+C at any time to stop the server.\n")

    # Allow immediate reuse of port
    socketserver.TCPServer.allow_reuse_address = True
    with socketserver.TCPServer(("0.0.0.0", PORT), ZenLockHandler) as httpd:
        try:
            httpd.serve_forever()
        except KeyboardInterrupt:
            print("\n Stopping ZenLock server. Have a productive day!")
            sys.exit(0)

if __name__ == "__main__":
    main()
