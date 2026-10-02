package com.walhalla.bluetoothhiddevice

import fi.iki.elonen.NanoHTTPD
import java.security.MessageDigest

/**
 * Tiny LAN web server that lives in [HidForegroundService].
 * GET / serves a single static page; everything under /api/ needs the token
 * (header `X-Token` or `Authorization: Bearer <token>`).
 */
class HidWebServer(
    port: Int,
    private val token: String,
    private val hid: HidDeviceManager
) : NanoHTTPD(port) {

    override fun serve(session: IHTTPSession): Response {
        val uri = session.uri
        if (uri == "/" || uri == "/index.html") {
            return newFixedLengthResponse(Response.Status.OK, "text/html; charset=utf-8", INDEX_HTML)
        }
        if (!uri.startsWith("/api/")) {
            return json(Response.Status.NOT_FOUND, """{"error":"not found"}""")
        }
        if (!isAuthorized(session)) {
            return json(Response.Status.UNAUTHORIZED, """{"error":"unauthorized"}""")
        }
        return when {
            uri == "/api/status" && session.method == Method.GET ->
                json(Response.Status.OK, """{"connected":${hid.isConnected()}}""")

            uri == "/api/release-all" && session.method == Method.POST -> {
                hid.releaseAll()
                json(Response.Status.OK, """{"ok":true}""")
            }

            uri == "/api/type" && session.method == Method.POST -> {
                session.parseBody(HashMap())
                val text = session.parameters["text"]?.firstOrNull().orEmpty()
                if (!hid.isConnected()) {
                    json(Response.Status.CONFLICT, """{"error":"host not connected"}""")
                } else {
                    if (text.isNotEmpty()) hid.sendString(text)
                    json(Response.Status.OK, """{"ok":true,"chars":${text.length}}""")
                }
            }

            else -> json(Response.Status.NOT_FOUND, """{"error":"not found"}""")
        }
    }

    private fun isAuthorized(session: IHTTPSession): Boolean {
        val headers = session.headers
        val given = headers["x-token"]
            ?: headers["authorization"]?.removePrefix("Bearer ")?.trim()
            ?: return false
        return MessageDigest.isEqual(given.toByteArray(), token.toByteArray())
    }

    private fun json(status: Response.IStatus, body: String): Response =
        newFixedLengthResponse(status, "application/json; charset=utf-8", body)

    private companion object {
        const val INDEX_HTML = """<!doctype html>
<html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>HID remote</title>
<style>body{font-family:sans-serif;max-width:560px;margin:24px auto;padding:0 12px}
input,textarea,button{width:100%;box-sizing:border-box;font-size:16px;margin:6px 0;padding:8px}</style></head>
<body><h2>Bluetooth HID remote</h2>
<input id="token" placeholder="Token (shown in the app)">
<button onclick="status()">Check status</button>
<textarea id="text" rows="4" placeholder="Text to type on the host"></textarea>
<button onclick="send()">Type on host</button>
<button onclick="rel()">Release all keys</button>
<pre id="out"></pre>
<script>
var t=document.getElementById('token');t.value=localStorage.getItem('hidToken')||'';
if(location.hash.length>1){t.value=location.hash.substring(1);localStorage.setItem('hidToken',t.value);history.replaceState(null,'','/');status();}
function call(m,p,b){localStorage.setItem('hidToken',t.value);
 return fetch(p,{method:m,headers:{'X-Token':t.value},body:b}).then(function(r){return r.text()}).then(function(x){document.getElementById('out').textContent=x})}
function status(){call('GET','/api/status')}
function rel(){call('POST','/api/release-all','')}
function send(){call('POST','/api/type',new URLSearchParams({text:document.getElementById('text').value}))}
</script></body></html>"""
    }
}