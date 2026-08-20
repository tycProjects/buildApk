#!/usr/bin/env python3

PORT = 8000

import http.server

import sys
from http.server import SimpleHTTPRequestHandler, ThreadingHTTPServer

import argparse
import webbrowser

class WasmHandler(SimpleHTTPRequestHandler):
    def end_headers(self):        
        # Include additional response headers to allow for use of the 
        # SharedArrayBuffer in Firefox.
        # https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/SharedArrayBuffer/Planned_changes
        self.send_header("Cross-Origin-Opener-Policy", "same-origin")
        self.send_header("Cross-Origin-Embedder-Policy", "require-corp")

        # Emulate csp in publishing sites
        # https://developers.google.com/web/fundamentals/security/csp?utm_source=devtools#eval_too
        # self.send_header("Content-Security-Policy", "script-src 'unsafe-eval' 'self'")

        SimpleHTTPRequestHandler.end_headers(self)

# Media type update needed for some browsers.
WasmHandler.extensions_map['.wasm'] = 'application/wasm'
WasmHandler.extensions_map['.js'] = 'application/javascript'

if __name__ == '__main__':

    parser = argparse.ArgumentParser(description='Light.vn Server')
    parser.add_argument('-o', '--open', action='store_true')

    args = parser.parse_args()
    #print(args.open)

    # webpage needs to open first before server start
    if args.open:
        # note: using https:// causes bad requests
        webbrowser.open_new_tab("http://localhost:{}".format(PORT))

    # ThreadingHTTPServer: needed to accept ctrl+c when busy
    with ThreadingHTTPServer(("", PORT), WasmHandler) as httpd:
        print("Listening on port {}. Press Ctrl+C to stop.".format(PORT))
        httpd.serve_forever()

