package com.walhalla.bluetoothhiddevice

import com.walhalla.bluetoothhiddevice.presets.PresetAction
import com.walhalla.bluetoothhiddevice.presets.PresetActionCodec
import com.walhalla.bluetoothhiddevice.presets.PresetActionEntity
import com.walhalla.bluetoothhiddevice.presets.PresetEntity
import com.walhalla.bluetoothhiddevice.presets.PresetExecutor
import com.walhalla.bluetoothhiddevice.presets.PresetRepository
import com.walhalla.bluetoothhiddevice.presets.PresetShortcutDraft
import com.walhalla.bluetoothhiddevice.presets.PresetShortcutParser
import fi.iki.elonen.NanoHTTPD
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest

/**
 * Tiny LAN web server that lives in [HidForegroundService].
 * GET / serves a single static page; everything under /api/ needs the token
 * (header `X-Token` or `Authorization: Bearer <token>`).
 *
 * Presets are read from and written to the same Room database the app uses.
 * Sensitive presets and presets that need confirmation on the phone are listed (without their
 * content) but cannot be run, shown, edited or exported from the web.
 */
class HidWebServer(
    port: Int,
    private val token: String,
    private val hid: HidDeviceManager,
    private val presets: PresetRepository,
    private val executor: PresetExecutor
) : NanoHTTPD(port) {

    private val presetActionPath = Regex("^/api/presets/(\\d+)/(run|duplicate|delete|update)$")
    private val categoryActionPath = Regex("^/api/categories/(\\d+)/(delete|color|reorder)$")

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
        return try {
            route(session, uri)
        } catch (e: Exception) {
            json(Response.Status.INTERNAL_ERROR, JSONObject().put("error", e.message ?: e.javaClass.simpleName).toString())
        }
    }

    private fun route(session: IHTTPSession, uri: String): Response {
        val post = session.method == Method.POST
        val get = session.method == Method.GET
        val presetMatch = presetActionPath.matchEntire(uri)
        val categoryMatch = categoryActionPath.matchEntire(uri)
        return when {
            uri == "/api/status" && get -> statusResponse()

            uri == "/api/reconnect" && post -> {
                val result = hid.reconnectLast()
                json(Response.Status.OK, JSONObject().put("ok", true).put("result", result).toString())
            }

            uri == "/api/disconnect" && post -> {
                hid.disconnectCurrent()
                json(Response.Status.OK, """{"ok":true}""")
            }

            uri == "/api/release-all" && post -> {
                hid.releaseAll()
                json(Response.Status.OK, """{"ok":true}""")
            }

            uri == "/api/type" && post -> {
                session.parseBody(HashMap())
                val text = session.parameters["text"]?.firstOrNull().orEmpty()
                if (!hid.isConnected()) {
                    json(Response.Status.CONFLICT, """{"error":"host not connected"}""")
                } else {
                    if (text.isNotEmpty()) hid.sendString(text)
                    json(Response.Status.OK, """{"ok":true,"chars":${text.length}}""")
                }
            }

            uri == "/api/presets" && get ->
                json(Response.Status.OK, runBlocking { buildPresetList() }.toString())

            uri == "/api/presets/add" && post -> {
                session.parseBody(HashMap())
                runBlocking { addPreset(session) }
            }

            presetMatch != null && post -> {
                session.parseBody(HashMap())
                runBlocking { presetAction(presetMatch.groupValues[1].toLong(), presetMatch.groupValues[2], session) }
            }

            uri == "/api/categories/add" && post -> {
                session.parseBody(HashMap())
                val title = param(session, "title")?.trim().orEmpty()
                if (title.isEmpty()) {
                    error(Response.Status.BAD_REQUEST, "title required")
                } else {
                    runBlocking { presets.addCategory(title) }
                    json(Response.Status.OK, """{"ok":true}""")
                }
            }

            categoryMatch != null && post -> {
                session.parseBody(HashMap())
                runBlocking { categoryAction(categoryMatch.groupValues[1].toLong(), categoryMatch.groupValues[2], session) }
            }

            uri == "/api/export" && get -> {
                val body = runBlocking { presets.exportToJson(includeSensitive = false) }
                newFixedLengthResponse(Response.Status.OK, "application/json; charset=utf-8", body).also {
                    it.addHeader("Content-Disposition", "attachment; filename=\"presets.json\"")
                }
            }

            uri == "/api/import" && post -> {
                val files = HashMap<String, String>()
                session.parseBody(files)
                val body = files["postData"].orEmpty()
                if (body.isBlank()) {
                    error(Response.Status.BAD_REQUEST, "empty body")
                } else try {
                    runBlocking { presets.importFromJson(body) }
                    json(Response.Status.OK, """{"ok":true}""")
                } catch (e: Exception) {
                    error(Response.Status.BAD_REQUEST, "import failed: " + (e.message ?: e.javaClass.simpleName))
                }
            }

            else -> json(Response.Status.NOT_FOUND, """{"error":"not found"}""")
        }
    }

    private fun statusResponse(): Response {
        val body = JSONObject()
            .put("connected", hid.isConnected())
            .put("status", hid.lastStatus)
            .put("device", hid.connectedName() ?: JSONObject.NULL)
            .put("address", hid.connectedAddress() ?: JSONObject.NULL)
            .put("hasLast", hid.lastDeviceAddress() != null)
        return json(Response.Status.OK, body.toString())
    }

    private fun isLocked(preset: PresetEntity, actions: List<PresetActionEntity>): Boolean =
        preset.isSensitive || preset.requiresConfirmation || actions.any {
            it.type == PresetActionCodec.TYPE_TYPE_SENSITIVE_TEXT || it.type == PresetActionCodec.TYPE_CREDENTIAL
        }

    /** Web-friendly steps (type and text) for every action, or null when one of them is not supported. */
    private fun stepsOf(actions: List<PresetActionEntity>): JSONArray? {
        if (actions.isEmpty()) return null
        val result = JSONArray()
        for (action in actions.sortedBy { it.sortOrder }) {
            val payload = JSONObject(action.payloadJson)
            val step = when (action.type) {
                PresetActionCodec.TYPE_TYPE_TEXT ->
                    JSONObject().put("type", PresetActionCodec.TYPE_TYPE_TEXT).put("value", payload.optString("text"))
                PresetActionCodec.TYPE_RUN_WINDOWS_COMMAND ->
                    JSONObject().put("type", action.type).put("value", payload.optString("command"))
                PresetActionCodec.TYPE_DELAY ->
                    JSONObject().put("type", action.type).put("value", payload.optLong("millis").toString())
                PresetActionCodec.TYPE_KEY_COMBO, PresetActionCodec.TYPE_KEY_PRESS -> {
                    val shortcut = runCatching {
                        PresetShortcutDraft.fromAction(PresetActionCodec.fromEntity(action))?.toShortcutString()
                    }.getOrNull() ?: return null
                    JSONObject().put("type", PresetActionCodec.TYPE_KEYBOARD_SHORTCUT).put("value", shortcut)
                }
                else -> return null
            }
            result.put(step)
        }
        return result
    }

    private fun colorToHex(argb: Int): String =
        if (argb == 0) "" else String.format("#%06X", argb and 0xFFFFFF)

    private suspend fun buildPresetList(): JSONObject {
        val categories = presets.categories.first()
        val allPresets = presets.allPresets.first()
        val actionsByPreset = presets.allActions.first().groupBy { it.presetId }
        val presetsByCategory = allPresets.groupBy { it.categoryId }

        val result = JSONArray()
        for (category in categories) {
            val items = JSONArray()
            for (preset in presetsByCategory[category.id].orEmpty().sortedBy { it.sortOrder }) {
                val actions = actionsByPreset[preset.id].orEmpty()
                val locked = isLocked(preset, actions)
                val steps = if (locked) null else stepsOf(actions)
                items.put(
                    JSONObject()
                        .put("id", preset.id)
                        .put("title", preset.title)
                        .put("description", if (locked) "" else preset.description)
                        .put("builtIn", preset.isBuiltIn)
                        .put("locked", locked)
                        .put("actions", actions.size)
                        .put("steps", steps ?: JSONObject.NULL)
                )
            }
            result.put(
                JSONObject()
                    .put("id", category.id)
                    .put("title", category.title)
                    .put("builtIn", category.isBuiltIn)
                    .put("color", colorToHex(category.colorArgb))
                    .put("presets", items)
            )
        }
        return JSONObject().put("connected", hid.isConnected()).put("categories", result)
    }

    private fun param(session: IHTTPSession, name: String): String? =
        session.parameters[name]?.firstOrNull()

    private fun error(status: Response.IStatus, message: String): Response =
        json(status, JSONObject().put("error", message).toString())

    /** Steps from the web form: JSON array of {type, value}. Throws IllegalArgumentException on bad input. */
    private fun parseActions(raw: String?): List<PresetAction> {
        val array = JSONArray(raw ?: throw IllegalArgumentException("actions required"))
        require(array.length() in 1..50) { "1 to 50 steps required" }
        return (0 until array.length()).map { index ->
            val step = array.getJSONObject(index)
            val value = step.optString("value")
            val n = index + 1
            when (step.optString("type")) {
                PresetActionCodec.TYPE_TYPE_TEXT -> {
                    require(value.isNotEmpty()) { "step $n: text is empty" }
                    PresetAction.TypeText(value)
                }
                PresetActionCodec.TYPE_RUN_WINDOWS_COMMAND -> {
                    require(value.isNotBlank()) { "step $n: command is empty" }
                    PresetAction.RunWindowsCommand(value)
                }
                PresetActionCodec.TYPE_KEYBOARD_SHORTCUT -> {
                    require(value.isNotBlank()) { "step $n: shortcut is empty" }
                    PresetShortcutParser.parse(value)
                }
                PresetActionCodec.TYPE_DELAY -> {
                    val millis = value.trim().toLongOrNull()
                        ?: throw IllegalArgumentException("step $n: delay must be a number of milliseconds")
                    PresetAction.Delay(millis.coerceIn(0L, 60_000L))
                }
                else -> throw IllegalArgumentException("step $n: unsupported type")
            }
        }
    }

    private suspend fun addPreset(session: IHTTPSession): Response {
        val categoryId = param(session, "categoryId")?.toLongOrNull()
            ?: return error(Response.Status.BAD_REQUEST, "categoryId required")
        if (presets.categories.first().none { it.id == categoryId }) {
            return error(Response.Status.NOT_FOUND, "category not found")
        }
        val title = param(session, "title")?.trim().orEmpty()
        if (title.isEmpty()) return error(Response.Status.BAD_REQUEST, "title required")
        val description = param(session, "description")?.trim().orEmpty().ifEmpty { title }
        return try {
            presets.addPresetWithActions(categoryId, title, description, parseActions(param(session, "actions")))
            json(Response.Status.OK, """{"ok":true}""")
        } catch (e: Exception) {
            error(Response.Status.BAD_REQUEST, e.message ?: "invalid steps")
        }
    }

    private suspend fun presetAction(id: Long, action: String, session: IHTTPSession): Response {
        val source = presets.getPresetWithActions(id)
            ?: return error(Response.Status.NOT_FOUND, "preset not found")
        val locked = isLocked(source.preset, source.actions)

        return when (action) {
            "run" -> {
                if (locked) return error(Response.Status.FORBIDDEN, "this preset must be confirmed on the phone")
                if (!hid.isConnected()) return error(Response.Status.CONFLICT, "host not connected")
                val ok = executor.execute(source.actions.sortedBy { it.sortOrder }.map { PresetActionCodec.fromEntity(it) })
                if (ok) json(Response.Status.OK, """{"ok":true}""")
                else error(Response.Status.CONFLICT, "preset failed: no HID connection or unsupported key")
            }

            "duplicate" -> {
                if (locked) return error(Response.Status.FORBIDDEN, "locked preset")
                presets.duplicatePreset(id)
                json(Response.Status.OK, """{"ok":true}""")
            }

            "delete" -> {
                if (source.preset.isBuiltIn) return error(Response.Status.FORBIDDEN, "built-in preset cannot be deleted")
                presets.deletePreset(id)
                json(Response.Status.OK, """{"ok":true}""")
            }

            else -> {
                if (locked) return error(Response.Status.FORBIDDEN, "locked preset")
                if (stepsOf(source.actions) == null) {
                    return error(Response.Status.CONFLICT, "this preset has actions that can be edited only on the phone")
                }
                val title = param(session, "title")?.trim().orEmpty().ifEmpty { source.preset.title }
                val description = param(session, "description")?.trim().orEmpty().ifEmpty { title }
                try {
                    presets.replacePresetActions(id, title, description, parseActions(param(session, "actions")))
                    json(Response.Status.OK, """{"ok":true}""")
                } catch (e: Exception) {
                    error(Response.Status.BAD_REQUEST, e.message ?: "invalid steps")
                }
            }
        }
    }

    private suspend fun categoryAction(id: Long, action: String, session: IHTTPSession): Response {
        if (presets.categories.first().none { it.id == id }) {
            return error(Response.Status.NOT_FOUND, "category not found")
        }
        return when (action) {
            "delete" ->
                if (presets.deleteCustomCategory(id)) json(Response.Status.OK, """{"ok":true}""")
                else error(Response.Status.FORBIDDEN, "built-in category cannot be deleted")

            "color" -> {
                val hex = param(session, "color").orEmpty().trim().removePrefix("#")
                val argb = when {
                    hex.isEmpty() -> 0
                    hex.length == 6 && hex.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' } ->
                        (0xFF000000L or hex.toLong(16)).toInt()
                    else -> return error(Response.Status.BAD_REQUEST, "color must be #RRGGBB")
                }
                presets.setCategoryColor(id, argb)
                json(Response.Status.OK, """{"ok":true}""")
            }

            else -> {
                val ids = param(session, "ids").orEmpty().split(",").mapNotNull { it.trim().toLongOrNull() }
                presets.reorderPresets(id, ids)
                json(Response.Status.OK, """{"ok":true}""")
            }
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
<style>
body{font-family:sans-serif;max-width:1100px;margin:16px auto;padding:0 12px}
.layout{display:flex;gap:24px;align-items:flex-start}
main{flex:1 1 0;min-width:0}
aside{flex:0 0 360px;position:sticky;top:16px;max-height:calc(100vh - 32px);overflow:auto;border:1px solid #ccc;border-radius:8px;padding:0 12px 12px}
@media(max-width:800px){.layout{flex-direction:column}aside{position:static;flex:none;width:100%;max-height:none}}
input,textarea,select,button{box-sizing:border-box;font-size:16px;margin:4px 0;padding:8px}
input,textarea,select{width:100%}
input[type=color]{width:40px;padding:0;height:32px;margin:0 6px}
.card{border:1px solid #ccc;border-radius:8px;padding:8px;margin:6px 0;border-left-width:6px;cursor:default}
.card.sel{outline:2px solid #36c}
.card.over{background:#eef4ff}
.card small{color:#666;display:block}
.card code{display:block;background:#f3f3f3;padding:4px;margin:4px 0;white-space:pre-wrap;word-break:break-all}
.row button{margin-right:4px}
.lock{color:#a60}
.tabs{display:flex;flex-wrap:wrap;gap:4px;margin:8px 0}
.tab{margin:0;border:1px solid #ccc;border-bottom:3px solid transparent;background:#f7f7f7;border-radius:6px 6px 0 0;padding:6px 10px;font-size:14px;cursor:pointer}
.tab.act{background:#fff;border-color:#36c;font-weight:bold}
.cat{display:flex;align-items:center;margin-top:12px}
.cat h4{margin:0;flex:1}
#out{background:#f3f3f3;padding:6px;min-height:1.5em;white-space:pre-wrap}
.dot{display:inline-block;width:12px;height:12px;border-radius:50%;background:#999;margin-right:6px;vertical-align:middle}
.dot.ok{background:#2a2}.dot.off{background:#d90}.dot.bad{background:#d22}
.step{border:1px solid #ddd;border-radius:6px;padding:4px 6px;margin:6px 0}
.step button{padding:4px 8px;margin-right:2px}
.bar{display:flex;gap:8px;align-items:center;flex-wrap:wrap}
.bar input{flex:1 1 200px;width:auto}
kbd{background:#eee;border:1px solid #ccc;border-radius:3px;padding:0 4px}
</style></head>
<body><h2>Bluetooth HID remote</h2>
<div class="layout"><main>
<div class="bar"><input id="token" placeholder="Token (shown in the app)"><button onclick="connectClick()">Connect</button></div>
<div class="bar"><span><span id="dot" class="dot"></span><span id="conn">not connected to the app</span></span>
<button id="breconnect" onclick="reconnect()">Reconnect to host</button>
<button id="bdisconnect" onclick="disconnectHost()">Disconnect</button></div>
<pre id="out"></pre>
<details><summary>Quick type</summary>
<textarea id="text" rows="3" placeholder="Text to type on the host"></textarea>
<button onclick="send()">Type on host</button>
<button onclick="rel()">Release all keys</button>
</details>
<h3>Presets</h3>
<div class="bar"><button onclick="exportJson()">Export JSON</button>
<button onclick="el('importFile').click()">Import JSON</button>
<input id="importFile" type="file" accept=".json,application/json" style="display:none" onchange="importPicked(this)"></div>
<small>Keys: <kbd>/</kbd> filter, <kbd>&uarr;</kbd><kbd>&darr;</kbd> select, <kbd>&larr;</kbd><kbd>&rarr;</kbd> tabs, <kbd>Enter</kbd> run, <kbd>Esc</kbd> cancel. Sensitive presets are exported only from the phone.</small>
<input id="filter" placeholder="Filter by title ( / )" oninput="render()" onkeydown="filterKey(event)">
<div id="tabs" class="tabs"></div>
<div id="list"></div>
</main>
<aside>
<h3 id="formTitle">Add preset</h3>
<select id="fcat"></select>
<input id="ftitle" placeholder="Title">
<input id="fdesc" placeholder="Description (optional)">
<div id="steps"></div>
<button onclick="addStep()">+ Step</button>
<div>
<button id="fsave" onclick="save()">Add</button>
<button id="fcancel" onclick="cancelEdit()" style="display:none">Cancel</button>
</div>
<details><summary>New category</summary>
<input id="newcat" placeholder="Category title">
<button onclick="addCategory()">Add category</button>
</details>
</aside></div>
<script>
function el(i){return document.getElementById(i)}
var t=el('token');t.value=localStorage.getItem('hidToken')||'';
var activeCat=localStorage.getItem('hidTab')||'all';
var data={categories:[]};var editId=null;var selId=null;var dragId=null;
var TYPES=[['TypeText','Type text'],['KeyboardShortcut','Shortcut, e.g. Ctrl+Shift+Esc'],['RunWindowsCommand','Run command (Win+R)'],['Delay','Delay (ms)']];
var steps=[{type:'TypeText',value:''}];
if(location.hash.length>1){t.value=location.hash.substring(1);localStorage.setItem('hidToken',t.value);history.replaceState(null,'','/');}
function api(m,p,b,ct){localStorage.setItem('hidToken',t.value);var h={'X-Token':t.value};if(ct)h['Content-Type']=ct;
 return fetch(p,{method:m,headers:h,body:b}).then(function(r){
  return r.json().then(function(j){j._s=r.status;return j},function(){return {_s:r.status}})})}
function msg(j){el('out').textContent=j.error?('Error: '+j.error):(j.result?j.result:'OK')}
function connectClick(){load();poll()}
function setConn(cls,text){el('dot').className='dot '+cls;el('conn').textContent=text}
function poll(){if(!t.value||document.hidden)return;
 fetch('/api/status',{headers:{'X-Token':t.value}}).then(function(r){
  if(r.status===401){setConn('bad','Wrong token');return}
  return r.json().then(function(j){
   setConn(j.connected?'ok':'off',j.connected?('Connected: '+(j.device||j.address||'host')):(j.status||'Not connected'));
   el('breconnect').disabled=j.connected||!j.hasLast;el('bdisconnect').disabled=!j.connected})
 }).catch(function(){setConn('bad','Phone unreachable')})}
setInterval(poll,2000);
function reconnect(){api('POST','/api/reconnect','').then(msg)}
function disconnectHost(){api('POST','/api/disconnect','').then(msg)}
function load(){return api('GET','/api/presets').then(function(j){
 if(j.error){msg(j);return}
 data=j;el('out').textContent='';
 var s=el('fcat'),keep=s.value;s.innerHTML='';
 j.categories.forEach(function(c){var o=document.createElement('option');o.value=c.id;o.textContent=c.title;s.appendChild(o)});
 if(keep)s.value=keep;else if(activeCat!=='all')s.value=activeCat;render()})}
function btn(label,fn,disabled,title){var b=document.createElement('button');b.textContent=label;b.disabled=!!disabled;b.onclick=function(e){e.stopPropagation();fn()};if(title)b.title=title;return b}
function visible(c){var f=el('filter').value.toLowerCase();return c.presets.filter(function(p){return !f||p.title.toLowerCase().indexOf(f)>=0})}
function sendOrder(c,ids){api('POST','/api/categories/'+c.id+'/reorder',new URLSearchParams({ids:ids.join(',')})).then(function(j){if(j.error)msg(j);load()})}
function movePreset(c,p,d){var ids=c.presets.map(function(x){return x.id});var i=ids.indexOf(p.id),k=i+d;if(k<0||k>=ids.length)return;ids.splice(i,1);ids.splice(k,0,p.id);sendOrder(c,ids)}
function render(){renderTabs();var box=el('list');box.innerHTML='';var filtering=!!el('filter').value;
 shownCats().forEach(function(c){
  var items=visible(c);
  if(!items.length&&filtering)return;
  var hd=document.createElement('div');hd.className='cat';
  var h=document.createElement('h4');h.textContent=c.title;hd.appendChild(h);
  var col=document.createElement('input');col.type='color';col.value=c.color||'#888888';col.title='Category color';
  col.onchange=function(){api('POST','/api/categories/'+c.id+'/color',new URLSearchParams({color:col.value})).then(function(j){msg(j);load()})};
  hd.appendChild(col);
  if(c.color)hd.appendChild(btn('Reset color',function(){api('POST','/api/categories/'+c.id+'/color',new URLSearchParams({color:''})).then(function(j){msg(j);load()})}));
  if(!c.builtIn)hd.appendChild(btn('Delete category',function(){if(confirm('Delete category "'+c.title+'" with all its presets?'))api('POST','/api/categories/'+c.id+'/delete','').then(function(j){msg(j);load()})}));
  box.appendChild(hd);
  items.forEach(function(p){
   var d=document.createElement('div');d.className='card'+(p.id===selId?' sel':'');d.setAttribute('data-id',p.id);
   d.style.borderLeftColor=c.color||'#ccc';
   d.onclick=function(){select(p.id)};
   if(!filtering){d.draggable=true;
    d.ondragstart=function(e){dragId=p.id;e.dataTransfer.effectAllowed='move';try{e.dataTransfer.setData('text/plain',String(p.id))}catch(x){}};
    d.ondragover=function(e){if(dragId!==null&&c.presets.some(function(x){return x.id===dragId})){e.preventDefault();d.classList.add('over')}};
    d.ondragleave=function(){d.classList.remove('over')};
    d.ondrop=function(e){e.preventDefault();d.classList.remove('over');if(dragId===null||dragId===p.id)return;
     var ids=c.presets.map(function(x){return x.id});if(ids.indexOf(dragId)<0)return;
     var from=ids.indexOf(dragId);ids.splice(from,1);var to=ids.indexOf(p.id);if(from<=to)to+=1;ids.splice(to,0,dragId);dragId=null;sendOrder(c,ids)};
    d.ondragend=function(){dragId=null}}
   var b=document.createElement('b');b.textContent=p.title;d.appendChild(b);
   if(p.locked){var l=document.createElement('small');l.className='lock';l.textContent='Locked: run it on the phone';d.appendChild(l)}
   else{
    if(p.description&&p.description!==p.title){var s=document.createElement('small');s.textContent=p.description;d.appendChild(s)}
    if(p.steps){var cd=document.createElement('code');cd.textContent=p.steps.map(function(s){return stepLabel(s)}).join('\n');d.appendChild(cd)}
    else{var s2=document.createElement('small');s2.textContent=p.actions+' actions (edit on the phone)';d.appendChild(s2)}
   }
   var r=document.createElement('div');r.className='row';
   r.appendChild(btn('Run',function(){runPreset(p)},p.locked));
   r.appendChild(btn('Edit',function(){startEdit(p,c)},!p.steps));
   r.appendChild(btn('Copy',function(){api('POST','/api/presets/'+p.id+'/duplicate','').then(function(j){msg(j);load()})},p.locked));
   r.appendChild(btn('Delete',function(){if(confirm('Delete "'+p.title+'"?'))api('POST','/api/presets/'+p.id+'/delete','').then(function(j){msg(j);load()})},p.builtIn));
   if(!filtering){r.appendChild(btn('\u2191',function(){movePreset(c,p,-1)},false,'Move up'));r.appendChild(btn('\u2193',function(){movePreset(c,p,1)},false,'Move down'))}
   d.appendChild(r);box.appendChild(d)})})}
function stepLabel(s){var n={TypeText:'Type',KeyboardShortcut:'Keys',RunWindowsCommand:'Run',Delay:'Wait'}[s.type]||s.type;return n+': '+s.value+(s.type==='Delay'?' ms':'')}
function isFiltering(){return !!el('filter').value}
function shownCats(){if(isFiltering()||activeCat==='all')return data.categories;var c=data.categories.filter(function(x){return String(x.id)===activeCat});return c.length?c:data.categories}
function tabIds(){return ['all'].concat(data.categories.map(function(c){return String(c.id)}))}
function setTab(id){activeCat=String(id);localStorage.setItem('hidTab',activeCat);if(editId===null&&activeCat!=='all')el('fcat').value=activeCat;render()}
function stepTab(d){var ids=tabIds();var i=ids.indexOf(activeCat);if(i<0)i=0;setTab(ids[Math.max(0,Math.min(ids.length-1,i+d))])}
function renderTabs(){var box=el('tabs');box.innerHTML='';if(!data.categories.length)return;
 if(tabIds().indexOf(activeCat)<0)activeCat='all';
 function tab(id,label,count,color){var b=document.createElement('button');b.className='tab'+(activeCat===id?' act':'');b.textContent=label+' ('+count+')';b.style.borderBottomColor=color||'transparent';b.onclick=function(){setTab(id)};box.appendChild(b)}
 var total=0;data.categories.forEach(function(c){total+=c.presets.length});
 tab('all','All',total,'');
 data.categories.forEach(function(c){tab(String(c.id),c.title,c.presets.length,c.color)})}
function runPreset(p){api('POST','/api/presets/'+p.id+'/run','').then(msg)}
function select(id){selId=id;var cards=document.querySelectorAll('.card');for(var i=0;i<cards.length;i++){cards[i].classList.toggle('sel',cards[i].getAttribute('data-id')===String(id))}}
function visibleIds(){var ids=[];shownCats().forEach(function(c){visible(c).forEach(function(p){ids.push(p.id)})});return ids}
function moveSel(d){var ids=visibleIds();if(!ids.length)return;var i=ids.indexOf(selId);i=i<0?(d>0?0:ids.length-1):Math.max(0,Math.min(ids.length-1,i+d));select(ids[i]);
 var c=document.querySelector('.card.sel');if(c)c.scrollIntoView({block:'nearest'})}
function runSelected(){if(selId===null)return;data.categories.forEach(function(c){c.presets.forEach(function(p){if(p.id===selId)runPreset(p)})})}
function filterKey(e){if(e.key==='Enter'||e.key==='ArrowDown'){e.preventDefault();var ids=visibleIds();if(ids.length){select(ids[0]);var c=document.querySelector('.card.sel');if(c)c.scrollIntoView({block:'nearest'})}e.target.blur()}}
document.addEventListener('keydown',function(e){
 var tag=(e.target.tagName||'').toLowerCase();var typing=(tag==='input'||tag==='textarea'||tag==='select');
 if(e.key==='Escape'){if(editId!==null)cancelEdit();if(typing)e.target.blur();return}
 if(typing||e.ctrlKey||e.metaKey||e.altKey)return;
 if(e.key==='/'){e.preventDefault();el('filter').focus();el('filter').select()}
 else if(e.key==='ArrowLeft'||e.key==='ArrowRight'){e.preventDefault();stepTab(e.key==='ArrowRight'?1:-1)}
 else if(e.key==='Enter'&&tag!=='button'){e.preventDefault();runSelected()}
 else if(e.key==='ArrowDown'||e.key==='ArrowUp'){e.preventDefault();moveSel(e.key==='ArrowDown'?1:-1)}
});
function renderSteps(){var box=el('steps');box.innerHTML='';
 steps.forEach(function(s,i){
  var row=document.createElement('div');row.className='step';
  var sel=document.createElement('select');
  TYPES.forEach(function(ty){var o=document.createElement('option');o.value=ty[0];o.textContent=ty[1];sel.appendChild(o)});
  sel.value=s.type;sel.onchange=function(){s.type=sel.value;if(s.type==='Delay'&&!/^[0-9]+$/.test(s.value))s.value='500';renderSteps()};
  row.appendChild(sel);
  var inp=document.createElement(s.type==='TypeText'?'textarea':'input');if(s.type==='TypeText')inp.rows=2;
  inp.placeholder=s.type==='Delay'?'milliseconds':(s.type==='KeyboardShortcut'?'Ctrl+Shift+Esc':(s.type==='RunWindowsCommand'?'cmd, notepad, calc':'text'));
  inp.value=s.value;inp.oninput=function(){s.value=inp.value};row.appendChild(inp);
  var bar=document.createElement('div');
  bar.appendChild(btn('\u2191',function(){moveStep(i,-1)},i===0));bar.appendChild(btn('\u2193',function(){moveStep(i,1)},i===steps.length-1));
  bar.appendChild(btn('\u2715',function(){steps.splice(i,1);if(!steps.length)steps.push({type:'TypeText',value:''});renderSteps()}));
  row.appendChild(bar);box.appendChild(row)})}
function moveStep(i,d){var s=steps.splice(i,1)[0];steps.splice(i+d,0,s);renderSteps()}
function addStep(){steps.push({type:'TypeText',value:''});renderSteps()}
function startEdit(p,c){editId=p.id;el('formTitle').textContent='Edit preset';el('fcat').value=c.id;el('fcat').disabled=true;
 el('ftitle').value=p.title;el('fdesc').value=p.description||'';
 steps=p.steps.map(function(s){return {type:s.type,value:s.value}});renderSteps();
 el('fsave').textContent='Save changes';el('fcancel').style.display='';el('ftitle').focus()}
function cancelEdit(){editId=null;el('formTitle').textContent='Add preset';el('fcat').disabled=false;
 el('ftitle').value='';el('fdesc').value='';steps=[{type:'TypeText',value:''}];renderSteps();el('fsave').textContent='Add';el('fcancel').style.display='none'}
function save(){var body=new URLSearchParams({categoryId:el('fcat').value,title:el('ftitle').value,description:el('fdesc').value,actions:JSON.stringify(steps)});
 var url=editId===null?'/api/presets/add':'/api/presets/'+editId+'/update';
 api('POST',url,body).then(function(j){msg(j);if(!j.error){cancelEdit();load()}})}
function addCategory(){var v=el('newcat').value;if(!v.trim())return;api('POST','/api/categories/add',new URLSearchParams({title:v})).then(function(j){msg(j);if(!j.error){el('newcat').value='';load()}})}
function exportJson(){fetch('/api/export',{headers:{'X-Token':t.value}}).then(function(r){if(!r.ok)throw new Error('HTTP '+r.status);return r.blob()}).then(function(b){
 var a=document.createElement('a');a.href=URL.createObjectURL(b);a.download='presets.json';document.body.appendChild(a);a.click();a.remove();setTimeout(function(){URL.revokeObjectURL(a.href)},1000)
 }).catch(function(e){el('out').textContent='Error: '+e.message})}
function importPicked(inp){var f=inp.files[0];inp.value='';if(!f)return;var fr=new FileReader();
 fr.onload=function(){api('POST','/api/import',fr.result,'application/json').then(function(j){msg(j);load()})};fr.readAsText(f)}
function rel(){api('POST','/api/release-all','').then(msg)}
function send(){api('POST','/api/type',new URLSearchParams({text:el('text').value})).then(msg)}
renderSteps();
if(t.value){load();poll()}
</script></body></html>"""
    }
}